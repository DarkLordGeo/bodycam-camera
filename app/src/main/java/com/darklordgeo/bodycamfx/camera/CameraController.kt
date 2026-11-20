package com.darklordgeo.bodycamfx.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraEffect
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.MirrorMode
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.lifecycle.LifecycleOwner
import com.darklordgeo.bodycamfx.filters.FilterPreset
import com.darklordgeo.bodycamfx.filters.PresetCapability
import com.darklordgeo.bodycamfx.filters.QualityTier
import com.darklordgeo.bodycamfx.media.MediaStorage
import com.darklordgeo.bodycamfx.render.DeviceCapabilities
import com.darklordgeo.bodycamfx.render.GlFilterProcessor
import com.darklordgeo.bodycamfx.render.HudConfig
import com.darklordgeo.bodycamfx.render.QualityMode
import com.darklordgeo.bodycamfx.render.RenderState
import com.darklordgeo.bodycamfx.settings.AppSettings
import com.darklordgeo.bodycamfx.settings.CaptureResolution
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.Segmenter
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

enum class CaptureMode { PHOTO, VIDEO }

data class CameraUiState(
    val ready: Boolean = false,
    val frontFacing: Boolean = false,
    val hasFrontCamera: Boolean = true,
    val hasFlash: Boolean = false,
    val torchOn: Boolean = false,
    val zoomRatio: Float = 1f,
    val minZoom: Float = 1f,
    val maxZoom: Float = 1f,
    val exposureIndex: Int = 0,
    val exposureMin: Int = 0,
    val exposureMax: Int = 0,
    val exposureStep: Float = 0f,
    val mode: CaptureMode = CaptureMode.PHOTO,
    val recording: Boolean = false,
    val recordingMs: Long = 0,
    val capturing: Boolean = false,
    val lastMediaUri: Uri? = null,
    val lastThumb: Bitmap? = null,
    val supportedResolutions: List<CaptureResolution> = CaptureResolution.entries,
    val activeResolution: CaptureResolution = CaptureResolution.FHD,
    val gpu: DeviceCapabilities? = null,
    val tier: QualityTier = QualityTier.BALANCED,
    val fps: Float = 0f,
    val message: String? = null,
    val focusPoint: Pair<Float, Float>? = null,
    val focusSeq: Int = 0,
    val outlineActive: Boolean = false,
    val streamSize: String = "",
)

/** CameraX effect wrapper: routes Preview + VideoCapture through the GL processor. */
private class FilterEffect(processor: GlFilterProcessor) : CameraEffect(
    CameraEffect.PREVIEW or CameraEffect.VIDEO_CAPTURE,
    processor.executor,
    processor,
    Consumer<Throwable> { Log.e("FilterEffect", "Effect error", it) },
)

/**
 * Owns CameraX binding, recording and capture. UI state is exposed as a StateFlow; rendering
 * parameters are pushed to [GlFilterProcessor.renderState].
 */
class CameraController(
    private val context: Context,
    private val storage: MediaStorage,
    private val scope: CoroutineScope,
) : GlFilterProcessor.Listener {

    private val _state = MutableStateFlow(CameraUiState())
    val state: StateFlow<CameraUiState> = _state.asStateFlow()

    val processor = GlFilterProcessor(context, this)
    private val effect = FilterEffect(processor)
    private val mainExecutor = ContextCompat.getMainExecutor(context)

    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var owner: LifecycleOwner? = null
    private var surfaceProvider: Preview.SurfaceProvider? = null

    private var settings = AppSettings()
    private var preset: FilterPreset? = null
    private var filterValues: Map<String, Float> = emptyMap()
    private var intensity = 1f
    private var hud = HudConfig()
    private var recordingStartedAt = 0L
    private var deviceOrientation = 0
    private var pendingWideZoom = false
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private var segmenter: Segmenter? = null
    private var analysisBusy = false
    private var zoomLiveData: androidx.lifecycle.LiveData<androidx.camera.core.ZoomState>? = null

    // ------------------------------------------------------------------ binding

    fun attach(owner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) {
        this.owner = owner
        this.surfaceProvider = surfaceProvider
        scope.launch { bind() }
    }

    fun detach() {
        stopRecording()
        provider?.unbindAll()
        owner?.let { zoomLiveData?.removeObservers(it) }
        zoomLiveData = null
        camera = null
        owner = null
        surfaceProvider = null
        _state.update { it.copy(ready = false) }
    }

    private fun hasPermission(p: String) =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    private suspend fun bind() {
        val owner = owner ?: return
        val sp = surfaceProvider ?: return
        if (!hasPermission(Manifest.permission.CAMERA)) return
        try {
            val p = provider ?: ProcessCameraProvider.getInstance(context).await().also { provider = it }
            val front = _state.value.frontFacing
            val hasFront = p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
            val selector = if (front && hasFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

            val ratio = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                .build()
            val preview = Preview.Builder().setResolutionSelector(ratio).build().also { it.setSurfaceProvider(sp) }

            // Choose the best supported quality not above the requested one.
            val cameraInfo = p.availableCameraInfos.firstOrNull { selector.filter(listOf(it)).isNotEmpty() }
            val supported = cameraInfo?.let { info ->
                val caps = Recorder.getVideoCapabilities(info)
                CaptureResolution.entries.filter { caps.isQualitySupported(it.quality(), androidx.camera.core.DynamicRange.SDR) }
            }?.ifEmpty { null } ?: listOf(CaptureResolution.HD, CaptureResolution.FHD)
            val wanted = settings.resolution
            val active = supported.lastOrNull { it.ordinal <= wanted.ordinal } ?: supported.first()

            val recorderBuilder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(active.quality(), FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
                .setAspectRatio(androidx.camera.core.AspectRatio.RATIO_16_9)
            settings.bitrate.mbps?.get(active)?.let { recorderBuilder.setTargetVideoEncodingBitRate(it * 1_000_000) }
            val vc = VideoCapture.Builder(recorderBuilder.build())
                .setMirrorMode(if (settings.mirrorSelfies) MirrorMode.MIRROR_MODE_ON_FRONT_ONLY else MirrorMode.MIRROR_MODE_OFF)
                .build()

            fun group(withAnalysis: Boolean) = UseCaseGroup.Builder()
                .addUseCase(preview)
                .addUseCase(vc)
                .apply { if (withAnalysis) addUseCase(buildAnalysis()) }
                .addEffect(effect)
                .build()

            p.unbindAll()
            val wantOutline = settings.outline.enabled
            var outlineBound = wantOutline
            val cam = try {
                p.bindToLifecycle(owner, selector, group(wantOutline))
            } catch (t: Throwable) {
                if (!wantOutline) throw t
                // Some devices can't add a 3rd stream next to preview+video; keep the camera working.
                Log.w(TAG, "Binding with person analysis failed; retrying without", t)
                outlineBound = false
                p.unbindAll()
                p.bindToLifecycle(owner, selector, group(false)).also {
                    _state.update { s -> s.copy(message = "People outline isn't supported with this camera") }
                }
            }
            camera = cam
            videoCapture = vc

            val isFront = selector == CameraSelector.DEFAULT_FRONT_CAMERA
            processor.frontFacing = isFront
            processor.sensorRotation = cam.cameraInfo.sensorRotationDegrees
            processor.mirrorSelfies = settings.mirrorSelfies

            val exp = cam.cameraInfo.exposureState
            _state.update {
                it.copy(
                    ready = true,
                    frontFacing = isFront,
                    hasFrontCamera = hasFront,
                    hasFlash = cam.cameraInfo.hasFlashUnit(),
                    torchOn = false,
                    exposureIndex = exp.exposureCompensationIndex,
                    exposureMin = exp.exposureCompensationRange.lower,
                    exposureMax = exp.exposureCompensationRange.upper,
                    exposureStep = exp.exposureCompensationStep.toFloat(),
                    supportedResolutions = supported,
                    activeResolution = active,
                    outlineActive = outlineBound,
                )
            }
            processor.outlineEnabled = outlineBound
            publishRenderState()
            zoomLiveData?.removeObservers(owner)
            zoomLiveData = cam.cameraInfo.zoomState
            cam.cameraInfo.zoomState.observe(owner) { z ->
                _state.update { it.copy(zoomRatio = z.zoomRatio, minZoom = z.minZoomRatio, maxZoom = z.maxZoomRatio) }
                if (pendingWideZoom) {
                    pendingWideZoom = false
                    applyWideLens()
                }
            }
            pendingWideZoom = true
            if (settings.rememberTorch && settings.lastTorch && cam.cameraInfo.hasFlashUnit()) setTorch(true)
        } catch (t: Throwable) {
            Log.e(TAG, "Camera bind failed", t)
            _state.update { it.copy(ready = false, message = "Camera unavailable: ${t.message}") }
        }
    }

    private fun buildAnalysis(): ImageAnalysis {
        val selector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(
                ResolutionStrategy(android.util.Size(640, 360), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
            )
            .build()
        return ImageAnalysis.Builder()
            .setResolutionSelector(selector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(analysisExecutor) { proxy -> analyze(proxy) } }
    }

    private fun segmenter(): Segmenter = segmenter ?: Segmentation.getClient(
        SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.STREAM_MODE)
            .enableRawSizeMask()
            .build(),
    ).also { segmenter = it }

    /** Runs person segmentation on a low-res analysis frame and hands the mask to the GL thread. */
    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun analyze(proxy: ImageProxy) {
        val media = proxy.image
        if (media == null || analysisBusy || !settings.outline.enabled) {
            proxy.close()
            return
        }
        analysisBusy = true
        val rotation = proxy.imageInfo.rotationDegrees
        try {
            segmenter().process(InputImage.fromMediaImage(media, rotation))
                .addOnSuccessListener(analysisExecutor) { mask ->
                    val w = mask.width
                    val h = mask.height
                    val src = mask.buffer
                    src.rewind()
                    val floats = src.asFloatBuffer()
                    val out = ByteBuffer.allocateDirect(w * h)
                    for (i in 0 until w * h) {
                        out.put((floats.get(i).coerceIn(0f, 1f) * 255f).toInt().toByte())
                    }
                    out.rewind()
                    processor.updatePersonMask(out, w, h, rotation)
                }
                .addOnFailureListener(analysisExecutor) { Log.w(TAG, "Segmentation failed", it) }
                .addOnCompleteListener(analysisExecutor) {
                    analysisBusy = false
                    proxy.close()
                }
        } catch (t: Throwable) {
            analysisBusy = false
            proxy.close()
        }
    }

    private fun CaptureResolution.quality(): Quality = when (this) {
        CaptureResolution.HD -> Quality.HD
        CaptureResolution.FHD -> Quality.FHD
        CaptureResolution.UHD -> Quality.UHD
    }

    private fun rebind() {
        if (owner != null && !_state.value.recording) scope.launch { bind() }
    }

    // ------------------------------------------------------------------ settings / filters

    fun applySettings(new: AppSettings) {
        val old = settings
        settings = new
        processor.qualityMode = new.qualityMode
        processor.mirrorSelfies = new.mirrorSelfies
        if (new.hud != hud) hud = new.hud
        publishRenderState()
        if (old.resolution != new.resolution || old.bitrate != new.bitrate || old.mirrorSelfies != new.mirrorSelfies ||
            old.outline.enabled != new.outline.enabled
        ) rebind()
    }

    fun setFilter(preset: FilterPreset, values: Map<String, Float>, intensity: Float) {
        val changed = this.preset?.id != preset.id
        this.preset = preset
        this.filterValues = values
        this.intensity = intensity
        publishRenderState()
        if (changed) applyWideLens()
    }

    private fun applyWideLens() {
        val p = preset ?: return
        val cam = camera ?: return
        val z = _state.value
        if (settings.useUltraWide && PresetCapability.PREFERS_WIDE_LENS in p.capabilities && z.minZoom < 1f) {
            cam.cameraControl.setZoomRatio(z.minZoom)
        }
    }

    private fun publishRenderState() {
        val p = preset ?: return
        val overlay = if (PresetCapability.OVERLAY in p.capabilities) hud else hud.copy(enabled = false)
        val values = if (_state.value.frontFacing) frontLensBoost(p, filterValues) else filterValues
        processor.renderState = RenderState.of(
            p, values, intensity, overlay,
            recording = _state.value.recording, recordingStartedAtMs = recordingStartedAt,
            globals = settings.outline.copy(enabled = settings.outline.enabled && _state.value.outlineActive).uniforms(),
        )
    }

    /**
     * Front cameras are far narrower than a body camera, so the same lens settings look tame on a
     * selfie. Wide-lens presets get extra curvature and grit there (the sliders still show the
     * user's base values).
     */
    private fun frontLensBoost(p: FilterPreset, v: Map<String, Float>): Map<String, Float> {
        if (PresetCapability.PREFERS_WIDE_LENS !in p.capabilities) return v
        fun boost(key: String, f: (Float) -> Float): Pair<String, Float>? {
            val spec = p.param(key) ?: return null
            return key to spec.clamp(f(v[key] ?: spec.default))
        }
        val boosted = listOfNotNull(
            boost("uDistortion") { it * 1.6f + 0.06f },
            boost("uFisheye") { it * 1.5f + 0.08f },
            boost("uLensFill") { it * 0.8f },
            boost("uSensorNoise") { it * 1.25f },
            boost("uGrain") { it * 1.2f },
            boost("uSharpen") { it + 0.1f },
        ).toMap()
        return v + boosted
    }

    fun renderStateFor(p: FilterPreset, values: Map<String, Float>): RenderState =
        RenderState.of(p, values, 1f, HudConfig(enabled = false))

    fun setDeviceOrientation(degrees: Int) {
        deviceOrientation = degrees
        processor.deviceOrientation = degrees
    }

    fun setMode(mode: CaptureMode) {
        if (_state.value.recording) return
        _state.update { it.copy(mode = mode) }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    // ------------------------------------------------------------------ controls

    fun switchCamera() {
        if (_state.value.recording) return
        _state.update { it.copy(frontFacing = !it.frontFacing, torchOn = false) }
        rebind()
    }

    fun setTorch(on: Boolean) {
        val cam = camera ?: return
        if (!cam.cameraInfo.hasFlashUnit()) return
        cam.cameraControl.enableTorch(on)
        _state.update { it.copy(torchOn = on) }
    }

    fun setZoom(ratio: Float) {
        val s = _state.value
        camera?.cameraControl?.setZoomRatio(ratio.coerceIn(s.minZoom, s.maxZoom))
    }

    fun zoomBy(scale: Float) = setZoom(_state.value.zoomRatio * scale)

    fun focus(factory: MeteringPointFactory, x: Float, y: Float) {
        val cam = camera ?: return
        val point = factory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(5, TimeUnit.SECONDS)
            .build()
        cam.cameraControl.startFocusAndMetering(action)
        _state.update { it.copy(focusPoint = x to y, focusSeq = it.focusSeq + 1) }
    }

    fun setExposure(index: Int) {
        val cam = camera ?: return
        val s = _state.value
        val i = index.coerceIn(s.exposureMin, s.exposureMax)
        cam.cameraControl.setExposureCompensationIndex(i)
        _state.update { it.copy(exposureIndex = i) }
    }

    // ------------------------------------------------------------------ capture

    fun takePhoto() {
        val s = _state.value
        if (!s.ready || s.capturing) return
        _state.update { it.copy(capturing = true) }
        val selfie = s.frontFacing
        processor.capturePhoto { bmp ->
            scope.launch {
                if (bmp == null) {
                    _state.update { it.copy(capturing = false, message = "Photo capture failed") }
                    return@launch
                }
                val thumb = Bitmap.createScaledBitmap(bmp, 160, 160 * bmp.height / bmp.width.coerceAtLeast(1), true)
                try {
                    val uri = storage.savePhoto(bmp, selfie)
                    _state.update { it.copy(capturing = false, lastMediaUri = uri, lastThumb = thumb) }
                } catch (t: Throwable) {
                    Log.e(TAG, "Save failed", t)
                    _state.update { it.copy(capturing = false, message = "Could not save photo: ${t.message}") }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startRecording() {
        val vc = videoCapture ?: return
        if (recording != null || !_state.value.ready) return
        vc.targetRotation = surfaceRotation(deviceOrientation)
        processor.lockedRotation = processor.finalRotation()
        var pending = vc.output.prepareRecording(context, storage.videoOutputOptions())
        if (settings.recordAudio && hasPermission(Manifest.permission.RECORD_AUDIO)) pending = pending.withAudioEnabled()
        recordingStartedAt = System.currentTimeMillis()
        recording = pending.start(mainExecutor) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    recordingStartedAt = System.currentTimeMillis()
                    _state.update { it.copy(recording = true, recordingMs = 0) }
                    publishRenderState()
                }
                is VideoRecordEvent.Status ->
                    _state.update { it.copy(recordingMs = event.recordingStats.recordedDurationNanos / 1_000_000) }
                is VideoRecordEvent.Finalize -> {
                    recording = null
                    processor.lockedRotation = null
                    val uri = event.outputResults.outputUri
                    val saved = uri != Uri.EMPTY && (!event.hasError() || event.error == VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE ||
                        event.error == VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED ||
                        event.error == VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED)
                    _state.update {
                        it.copy(
                            recording = false,
                            recordingMs = 0,
                            lastMediaUri = if (saved) uri else it.lastMediaUri,
                            message = if (saved) null else "Recording failed (error ${event.error})",
                        )
                    }
                    publishRenderState()
                    if (saved) scope.launch { loadVideoThumb(uri) }
                }
                else -> Unit
            }
        }
    }

    private suspend fun loadVideoThumb(uri: Uri) {
        val bmp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { context.contentResolver.loadThumbnail(uri, android.util.Size(160, 160), null) }.getOrNull()
        }
        if (bmp != null) _state.update { it.copy(lastThumb = bmp) }
    }

    fun stopRecording() {
        recording?.stop()
    }

    fun loadLatestThumb(uri: Uri?) {
        if (uri == null) { _state.update { it.copy(lastMediaUri = null, lastThumb = null) }; return }
        if (uri == _state.value.lastMediaUri && _state.value.lastThumb != null) return
        _state.update { it.copy(lastMediaUri = uri) }
        scope.launch { loadVideoThumb(uri) }
    }

    fun release() {
        stopRecording()
        provider?.unbindAll()
        segmenter?.close()
        analysisExecutor.shutdown()
        processor.release()
    }

    private fun surfaceRotation(deg: Int) = when (deg) {
        90 -> Surface.ROTATION_270
        180 -> Surface.ROTATION_180
        270 -> Surface.ROTATION_90
        else -> Surface.ROTATION_0
    }

    // ------------------------------------------------------------------ processor callbacks (GL thread)

    override fun onGpuReady(caps: DeviceCapabilities, tier: QualityTier) =
        _state.update { it.copy(gpu = caps, tier = tier) }

    override fun onTierChanged(tier: QualityTier) = _state.update { it.copy(tier = tier) }

    override fun onFps(fps: Float) = _state.update { it.copy(fps = fps) }

    override fun onStreamSize(width: Int, height: Int) = _state.update { it.copy(streamSize = "${width}×$height") }

    override fun onError(message: String) = _state.update { it.copy(message = message) }

    private companion object {
        const val TAG = "CameraController"
    }
}
