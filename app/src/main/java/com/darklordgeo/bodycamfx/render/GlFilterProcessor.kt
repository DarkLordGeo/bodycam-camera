package com.darklordgeo.bodycamfx.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.core.SurfaceOutput
import androidx.camera.core.SurfaceProcessor
import androidx.camera.core.SurfaceRequest
import com.darklordgeo.bodycamfx.filters.QualityTier
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * CameraX [SurfaceProcessor] that owns the GL thread. CameraX hands it one camera input surface
 * and one output surface per target use case (Preview, VideoCapture). Every camera frame is
 * processed once (pass 1) and blitted to all outputs (pass 2), so the encoder records exactly
 * the frames the viewfinder shows. Photos are rendered from the same frame at full resolution.
 */
class GlFilterProcessor(context: Context, private val listener: Listener) : SurfaceProcessor {

    interface Listener {
        fun onGpuReady(caps: DeviceCapabilities, tier: QualityTier)
        fun onTierChanged(tier: QualityTier)
        fun onFps(fps: Float)
        fun onError(message: String)
    }

    private val appContext = context.applicationContext
    private val thread = HandlerThread("BodycamFX-GL").apply { start() }
    private val handler = Handler(thread.looper)
    val executor: Executor = Executor { r -> handler.post(r) }
    private val photoExecutor = Executors.newSingleThreadExecutor()

    private var egl: EglCore? = null
    private var renderer: FrameRenderer? = null
    private var caps: DeviceCapabilities? = null
    private var released = false

    @Volatile var renderState: RenderState? = null
    @Volatile var qualityMode: QualityMode = QualityMode.AUTO
        set(value) { field = value; handler.post { applyTier(resolveTier(), notify = true) } }
    /** Physical device rotation (0/90/180/270, clockwise), from OrientationEventListener. */
    @Volatile var deviceOrientation: Int = 0
    @Volatile var sensorRotation: Int = 90
    @Volatile var frontFacing: Boolean = false
    /** While recording the HUD orientation is frozen to the orientation the video started in. */
    @Volatile var lockedRotation: Int? = null
    /** Front-camera photos/videos are saved mirrored (like the viewfinder) when true. */
    @Volatile var mirrorSelfies: Boolean = true

    private val mirrorOutput: Boolean get() = frontFacing && mirrorSelfies

    private class ThumbRequest(val states: List<RenderState>, val sizePx: Int, val callback: (Map<String, Bitmap>) -> Unit)
    private var pendingThumbs: ThumbRequest? = null

    private var tier = QualityTier.BALANCED
    private var autoTier = QualityTier.BALANCED

    private class Input(val texture: Int, val surfaceTexture: SurfaceTexture, val surface: Surface, val size: Size)
    private class Output(val eglSurface: EGLSurface, val size: Size)

    private var input: Input? = null
    private val outputs = LinkedHashMap<SurfaceOutput, Output>()

    private val stMatrix = FloatArray(16)
    private val invSt = FloatArray(16)
    private val updated = FloatArray(16)
    private val blitMatrix = FloatArray(16)
    private val startNs = SystemClock.elapsedRealtimeNanos()
    private var frameIndex = 0

    // perf stats
    private var statWindowStart = 0L
    private var statFrames = 0
    private var slowStreak = 0
    private var frameCostEmaMs = 0f

    // person mask
    private var maskTex = 0
    private var maskW = 0
    private var maskH = 0
    private var maskUpdatedAt = 0L
    @Volatile var outlineEnabled: Boolean = false

    /** Called from the analysis thread with a w*h confidence mask (0..255), upright for [rotation]. */
    fun updatePersonMask(bytes: ByteBuffer, w: Int, h: Int, rotation: Int) {
        handler.post {
            if (released || egl == null) return@post
            egl?.makePbufferCurrent()
            if (maskTex == 0 || w != maskW || h != maskH) {
                GlUtil.deleteTexture(maskTex)
                val t = IntArray(1)
                GLES30.glGenTextures(1, t, 0)
                maskTex = t[0]
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, maskTex)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
                maskW = w
                maskH = h
            }
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, maskTex)
            GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
            bytes.rewind()
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_R8, w, h, 0, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, bytes)
            renderer?.maskTexture = maskTex
            renderer?.maskRotation = rotation.toFloat()
            maskUpdatedAt = SystemClock.elapsedRealtime()
        }
    }

    /** Fades the outline out if segmentation stops delivering (camera switch, outline off). */
    private fun updateMaskValidity() {
        val r = renderer ?: return
        val age = SystemClock.elapsedRealtime() - maskUpdatedAt
        r.maskValid = when {
            !outlineEnabled || maskTex == 0 -> 0f
            age < 400 -> 1f
            age < 1000 -> 1f - (age - 400) / 600f
            else -> 0f
        }
    }

    private class PhotoRequest(val callback: (Bitmap?) -> Unit)
    private var pendingPhoto: PhotoRequest? = null

    init {
        handler.post {
            try {
                val core = EglCore()
                egl = core
                val c = DeviceCapabilities.probe(appContext)
                caps = c
                autoTier = c.recommendedTier
                tier = resolveTier()
                renderer = FrameRenderer { listener.onError(it) }
                Log.i(TAG, "GPU ${c.glRenderer} (${c.glVersion}) maxTex=${c.maxTextureSize} ram=${c.totalRamMb}MB tier=$tier")
                listener.onGpuReady(c, tier)
            } catch (t: Throwable) {
                Log.e(TAG, "GL init failed", t)
                listener.onError("OpenGL ES 3 is unavailable: ${t.message}")
            }
        }
    }

    private fun resolveTier(): QualityTier = when (qualityMode) {
        QualityMode.AUTO -> autoTier
        QualityMode.HIGH -> QualityTier.HIGH
        QualityMode.BALANCED -> QualityTier.BALANCED
        QualityMode.LOW -> QualityTier.LOW
    }

    private fun applyTier(newTier: QualityTier, notify: Boolean) {
        if (newTier == tier) return
        tier = newTier
        slowStreak = 0
        if (notify) listener.onTierChanged(newTier)
    }

    // ---------------------------------------------------------------- SurfaceProcessor

    override fun onInputSurface(request: SurfaceRequest) {
        val core = egl
        if (released || core == null || renderer == null) {
            request.willNotProvideSurface()
            return
        }
        core.makePbufferCurrent()
        val tex = GlUtil.createOesTexture()
        val st = SurfaceTexture(tex)
        st.setDefaultBufferSize(request.resolution.width, request.resolution.height)
        val surface = Surface(st)
        val slot = Input(tex, st, surface, request.resolution)
        input = slot
        st.setOnFrameAvailableListener({ onFrame(slot) }, handler)
        request.provideSurface(surface, executor) {
            st.setOnFrameAvailableListener(null)
            st.release()
            surface.release()
            if (!released) {
                egl?.makePbufferCurrent()
                GlUtil.deleteTexture(tex)
            }
            if (input === slot) input = null
        }
    }

    override fun onOutputSurface(surfaceOutput: SurfaceOutput) {
        val core = egl
        if (released || core == null) {
            surfaceOutput.close()
            return
        }
        val surface = surfaceOutput.getSurface(executor) {
            outputs.remove(surfaceOutput)?.let { out ->
                egl?.makePbufferCurrent()
                egl?.releaseSurface(out.eglSurface)
            }
            surfaceOutput.close()
        }
        try {
            outputs[surfaceOutput] = Output(core.createWindowSurface(surface), surfaceOutput.size)
        } catch (t: Throwable) {
            Log.e(TAG, "Output surface failed", t)
            surfaceOutput.close()
        }
    }

    // ---------------------------------------------------------------- frames

    private fun liveRotation(): Int = lockedRotation ?: finalRotation()

    /** Clockwise rotation that makes the buffer upright for the current physical orientation. */
    fun finalRotation(): Int {
        val dev = if (frontFacing) -deviceOrientation else deviceOrientation
        return ((sensorRotation + dev) % 360 + 360) % 360
    }

    private fun processingSize(size: Size, t: QualityTier): Size {
        val maxSide = caps?.maxProcessingSide(t) ?: 1920
        val long = maxOf(size.width, size.height)
        if (long <= maxSide) return size
        val s = maxSide.toFloat() / long
        return Size((size.width * s).toInt() and 1.inv(), (size.height * s).toInt() and 1.inv())
    }

    private fun onFrame(slot: Input) {
        if (released || slot !== input) return
        val core = egl ?: return
        val r = renderer ?: return
        val state = renderState ?: return
        val t0 = SystemClock.elapsedRealtimeNanos()
        try {
            core.makePbufferCurrent()
            slot.surfaceTexture.updateTexImage()
            slot.surfaceTexture.getTransformMatrix(stMatrix)
            val ts = slot.surfaceTexture.timestamp
            val timeSec = ((t0 - startNs) / 1e9).toFloat() % 3600f
            val now = System.currentTimeMillis()
            frameIndex++

            updateMaskValidity()
            val proc = processingSize(slot.size, tier)
            val tex = r.processLive(state, tier, slot.texture, stMatrix, proc.width, proc.height,
                timeSec, frameIndex, liveRotation(), now, mirrorOutput)

            Matrix.invertM(invSt, 0, stMatrix, 0)
            for ((surfaceOutput, out) in outputs.entries.toList()) {
                surfaceOutput.updateTransformMatrix(updated, stMatrix)
                Matrix.multiplyMM(blitMatrix, 0, invSt, 0, updated, 0)
                core.makeCurrent(out.eglSurface)
                r.blit(tex, blitMatrix, out.size.width, out.size.height)
                core.setPresentationTime(out.eglSurface, ts)
                core.swap(out.eglSurface)
            }

            pendingPhoto?.let {
                pendingPhoto = null
                renderPhoto(slot, state, it, timeSec, now)
            }
            pendingThumbs?.let {
                pendingThumbs = null
                renderThumbnails(slot, it, timeSec, now)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Frame failed", t)
        }
        trackPerformance(t0)
    }

    private fun trackPerformance(t0: Long) {
        val now = SystemClock.elapsedRealtimeNanos()
        val costMs = (now - t0) / 1e6f
        frameCostEmaMs = if (frameCostEmaMs == 0f) costMs else frameCostEmaMs * 0.95f + costMs * 0.05f
        statFrames++
        if (statWindowStart == 0L) statWindowStart = now
        val window = now - statWindowStart
        if (window >= 1_000_000_000L) {
            listener.onFps(statFrames * 1e9f / window)
            statFrames = 0
            statWindowStart = now
            // Auto mode: step down a tier after ~3 s of frames costing more than a 30 fps budget.
            if (qualityMode == QualityMode.AUTO && frameCostEmaMs > 30f && autoTier != QualityTier.LOW) {
                if (++slowStreak >= 3) {
                    autoTier = QualityTier.entries[autoTier.ordinal - 1]
                    Log.w(TAG, "Frame cost ${frameCostEmaMs}ms; lowering quality to $autoTier")
                    applyTier(autoTier, notify = true)
                }
            } else {
                slowStreak = 0
            }
        }
    }

    // ---------------------------------------------------------------- photo

    /** Renders the next camera frame at full resolution with the active filter. */
    fun capturePhoto(callback: (Bitmap?) -> Unit) {
        handler.post {
            if (released || input == null) callback(null) else pendingPhoto = PhotoRequest(callback)
        }
    }

    /**
     * Renders each preset onto the current camera frame at thumbnail size, so the carousel shows
     * real processed previews. Costs N tiny passes, requested every couple of seconds by the UI.
     */
    fun requestThumbnails(states: List<RenderState>, sizePx: Int, callback: (Map<String, Bitmap>) -> Unit) {
        handler.post { if (!released) pendingThumbs = ThumbRequest(states, sizePx, callback) }
    }

    private fun renderThumbnails(slot: Input, req: ThumbRequest, timeSec: Float, now: Long) {
        val r = renderer ?: return
        val rotation = finalRotation()
        val aspect = slot.size.width.toFloat() / slot.size.height
        val w = if (aspect >= 1f) (req.sizePx * aspect).toInt() else req.sizePx
        val h = if (aspect >= 1f) req.sizePx else (req.sizePx / aspect).toInt()
        val result = LinkedHashMap<String, Bitmap>()
        val target = RenderTarget(w, h)
        val buf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
        try {
            for (s in req.states) {
                val noHud = RenderState(s.preset, s.values + ("uMotionBlur" to 0f), HudConfig(enabled = false), false, 0L)
                r.renderEffect(noHud, tier, slot.texture, stMatrix, target, 0, timeSec, frameIndex, rotation, now)
                buf.rewind()
                GLES30.glReadPixels(0, 0, w, h, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf)
                buf.rewind()
                val raw = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                raw.copyPixelsFromBuffer(buf)
                val m = android.graphics.Matrix().apply {
                    postScale(1f, -1f)
                    postRotate(rotation.toFloat())
                    if (frontFacing) postScale(-1f, 1f)
                }
                val side = req.sizePx
                val rotated = Bitmap.createBitmap(raw, 0, 0, w, h, m, true)
                raw.recycle()
                val x = (rotated.width - side).coerceAtLeast(0) / 2
                val y = (rotated.height - side).coerceAtLeast(0) / 2
                val square = Bitmap.createBitmap(rotated, x, y, minOf(side, rotated.width), minOf(side, rotated.height))
                if (square !== rotated) rotated.recycle()
                result[s.preset.id] = square
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Thumbnail render failed", t)
        } finally {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            target.release()
        }
        if (result.isNotEmpty()) req.callback(result)
    }

    private fun renderPhoto(slot: Input, state: RenderState, req: PhotoRequest, timeSec: Float, now: Long) {
        val r = renderer ?: return req.callback(null)
        val rotation = finalRotation()
        val mirror = mirrorOutput
        val maxSide = minOf(caps?.maxTextureSize ?: 4096, 4096)
        val size = slot.size.let {
            val long = maxOf(it.width, it.height)
            if (long <= maxSide) it else {
                val s = maxSide.toFloat() / long
                Size((it.width * s).toInt() and 1.inv(), (it.height * s).toInt() and 1.inv())
            }
        }
        var target: RenderTarget? = null
        try {
            target = RenderTarget(size.width, size.height)
            r.renderEffect(state, QualityTier.HIGH, slot.texture, stMatrix, target, r.latestTexture,
                timeSec, frameIndex, rotation, now, mirror)
            val buf = ByteBuffer.allocateDirect(size.width * size.height * 4).order(ByteOrder.nativeOrder())
            GLES30.glReadPixels(0, 0, size.width, size.height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            target.release()
            target = null
            photoExecutor.execute {
                try {
                    buf.rewind()
                    val raw = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
                    raw.copyPixelsFromBuffer(buf)
                    val m = android.graphics.Matrix().apply {
                        postScale(1f, -1f) // GL rows start at the bottom
                        postRotate(rotation.toFloat())
                        if (mirror) postScale(-1f, 1f)
                    }
                    val upright = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
                    if (upright !== raw) raw.recycle()
                    req.callback(upright)
                } catch (t: Throwable) {
                    Log.e(TAG, "Photo conversion failed", t)
                    req.callback(null)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Photo render failed", t)
            target?.release()
            req.callback(null)
        }
    }

    // ---------------------------------------------------------------- lifecycle

    fun release() {
        handler.post {
            if (released) return@post
            released = true
            pendingPhoto?.callback?.invoke(null)
            pendingPhoto = null
            try {
                egl?.makePbufferCurrent()
                outputs.forEach { (so, out) ->
                    egl?.releaseSurface(out.eglSurface)
                    so.close()
                }
                outputs.clear()
                input?.let {
                    it.surfaceTexture.setOnFrameAvailableListener(null)
                    GlUtil.deleteTexture(it.texture)
                }
                input = null
                GlUtil.deleteTexture(maskTex)
                maskTex = 0
                renderer?.release()
                egl?.release()
            } catch (t: Throwable) {
                Log.w(TAG, "Release error", t)
            }
            renderer = null
            egl = null
            thread.quitSafely()
            photoExecutor.shutdown()
        }
    }

    private companion object {
        const val TAG = "GlFilterProcessor"
    }
}
