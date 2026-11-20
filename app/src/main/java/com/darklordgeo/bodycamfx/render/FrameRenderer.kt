package com.darklordgeo.bodycamfx.render

import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.util.Log
import com.darklordgeo.bodycamfx.filters.FilterPreset
import com.darklordgeo.bodycamfx.filters.PresetThumbnail
import com.darklordgeo.bodycamfx.filters.QualityTier
import com.darklordgeo.bodycamfx.filters.ShaderComposer

/**
 * Two-pass renderer.
 *
 *  Pass 1 (once per camera frame): camera OES texture -> composed preset shader -> RGBA target,
 *          in the camera buffer's own orientation, then the HUD. Ping-pong targets give the
 *          temporal effects the previous frame.
 *  Pass 2 (once per output surface): cheap textured blit using that output's CameraX transform.
 *
 * The expensive look is therefore computed once and shared by preview, video and photo.
 */
class FrameRenderer(private val onShaderError: (String) -> Unit) {

    private class FilterProgram(
        val program: Int,
        val paramLocs: List<Pair<String, Int>>,
        val locInput: Int,
        val locHistory: Int,
        val locSt: Int,
        val locRes: Int,
        val locTime: Int,
        val locFrame: Int,
        val locMask: Int,
        val locMaskRotation: Int,
        val locMaskValid: Int,
        val usesHistory: Boolean,
    )

    private val programs = HashMap<String, FilterProgram>()
    private val failed = HashSet<String>()
    private val passthrough = FilterPreset(
        id = "passthrough", name = "", description = "",
        thumbnail = PresetThumbnail(0, 0, ""), modules = emptyList(),
    )

    private val blitProgram = GlUtil.linkProgram(ShaderComposer.VERTEX_SHADER, BLIT_FRAGMENT)
    private val blitLocTex = GLES30.glGetUniformLocation(blitProgram, "uTex")
    private val blitLocMatrix = GLES30.glGetUniformLocation(blitProgram, "uMatrix")

    val hud = HudOverlay()

    private var targets = arrayOfNulls<RenderTarget>(2)

    /** Person segmentation mask (R8), uploaded by the processor. 0 = none. */
    var maskTexture = 0
    var maskRotation = 0f
    var maskValid = 0f
    private var current = 0

    /** Texture of the most recent processed frame (0 if none yet). */
    val latestTexture: Int get() = targets[current]?.texture ?: 0

    private fun programFor(preset: FilterPreset, tier: QualityTier): FilterProgram {
        val key = "${preset.id}@${tier.name}"
        programs[key]?.let { return it }
        if (key in failed) return programFor(passthrough, tier)
        return try {
            val composed = ShaderComposer.compose(preset, tier, externalTexture = true)
            val p = GlUtil.linkProgram(ShaderComposer.VERTEX_SHADER, composed.fragmentSource)
            FilterProgram(
                program = p,
                paramLocs = composed.paramKeys.map { it to GLES30.glGetUniformLocation(p, it) },
                locInput = GLES30.glGetUniformLocation(p, "uInput"),
                locHistory = GLES30.glGetUniformLocation(p, "uHistory"),
                locSt = GLES30.glGetUniformLocation(p, "uStMatrix"),
                locRes = GLES30.glGetUniformLocation(p, "uResolution"),
                locTime = GLES30.glGetUniformLocation(p, "uTime"),
                locFrame = GLES30.glGetUniformLocation(p, "uFrame"),
                locMask = GLES30.glGetUniformLocation(p, "uPersonMask"),
                locMaskRotation = GLES30.glGetUniformLocation(p, "uMaskRotation"),
                locMaskValid = GLES30.glGetUniformLocation(p, "uMaskValid"),
                usesHistory = composed.usesHistory,
            ).also { programs[key] = it }
        } catch (t: Throwable) {
            Log.e(TAG, "Shader for $key failed", t)
            failed += key
            onShaderError("Filter ${preset.name} failed to compile on this GPU; showing the plain image.")
            if (preset === passthrough) throw t else programFor(passthrough, tier)
        }
    }

    private fun ensureTargets(w: Int, h: Int) {
        val t = targets[0]
        if (t != null && t.width == w && t.height == h) return
        targets.forEach { it?.release() }
        targets = arrayOf(RenderTarget(w, h), RenderTarget(w, h))
        current = 0
    }

    /**
     * Pass 1 into [target]. [historyTex] is the previous processed frame (or 0).
     */
    fun renderEffect(
        state: RenderState,
        tier: QualityTier,
        oesTexture: Int,
        stMatrix: FloatArray,
        target: RenderTarget,
        historyTex: Int,
        timeSec: Float,
        frame: Int,
        hudRotation: Int,
        nowMs: Long,
        hudMirror: Boolean = false,
    ) {
        val fp = programFor(state.preset, tier)
        target.bind()
        GLES30.glUseProgram(fp.program)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture)
        GLES30.glUniform1i(fp.locInput, 0)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, if (fp.usesHistory) historyTex else 0)
        GLES30.glUniform1i(fp.locHistory, 1)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, maskTexture)
        GLES30.glUniform1i(fp.locMask, 2)
        GLES30.glUniform1f(fp.locMaskRotation, maskRotation)
        GLES30.glUniform1f(fp.locMaskValid, if (maskTexture != 0) maskValid else 0f)

        GLES30.glUniformMatrix4fv(fp.locSt, 1, false, stMatrix, 0)
        GLES30.glUniform2f(fp.locRes, target.width.toFloat(), target.height.toFloat())
        GLES30.glUniform1f(fp.locTime, timeSec)
        GLES30.glUniform1f(fp.locFrame, (frame % 4096).toFloat())
        for ((key, loc) in fp.paramLocs) {
            if (loc >= 0) GLES30.glUniform1f(loc, state.values[key] ?: 0f)
        }
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)

        hud.draw(state, target.width, target.height, hudRotation, nowMs, hudMirror)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
    }

    /** Live path: renders into the next ping-pong target and returns its texture. */
    fun processLive(
        state: RenderState, tier: QualityTier, oesTexture: Int, stMatrix: FloatArray,
        width: Int, height: Int, timeSec: Float, frame: Int, hudRotation: Int, nowMs: Long, hudMirror: Boolean,
    ): Int {
        ensureTargets(width, height)
        val prev = targets[current]!!
        current = 1 - current
        val dst = targets[current]!!
        renderEffect(state, tier, oesTexture, stMatrix, dst, prev.texture, timeSec, frame, hudRotation, nowMs, hudMirror)
        return dst.texture
    }

    /** Pass 2: draw [texture] into the currently bound surface of [w]x[h] using [matrix]. */
    fun blit(texture: Int, matrix: FloatArray, w: Int, h: Int) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, w, h)
        GLES30.glUseProgram(blitProgram)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        GLES30.glUniform1i(blitLocTex, 0)
        GLES30.glUniformMatrix4fv(blitLocMatrix, 1, false, matrix, 0)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
    }

    /** Drops compiled programs (e.g. after the tier changes they are recompiled lazily). */
    fun clearPrograms() {
        programs.values.forEach { GLES30.glDeleteProgram(it.program) }
        programs.clear()
        failed.clear()
    }

    fun release() {
        clearPrograms()
        targets.forEach { it?.release() }
        targets = arrayOfNulls(2)
        GLES30.glDeleteProgram(blitProgram)
        hud.release()
    }

    private companion object {
        const val TAG = "FrameRenderer"
        const val BLIT_FRAGMENT = """#version 300 es
precision mediump float;
uniform sampler2D uTex;
uniform mat4 uMatrix;
in vec2 vUv;
out vec4 o;
void main() { o = texture(uTex, (uMatrix * vec4(vUv, 0.0, 1.0)).xy); }
"""
    }
}
