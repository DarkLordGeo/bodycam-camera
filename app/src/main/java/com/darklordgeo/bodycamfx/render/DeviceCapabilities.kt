package com.darklordgeo.bodycamfx.render

import android.app.ActivityManager
import android.content.Context
import android.opengl.GLES30
import com.darklordgeo.bodycamfx.filters.QualityTier

enum class QualityMode { AUTO, HIGH, BALANCED, LOW }

/** GPU + memory profile, probed once on the GL thread. */
data class DeviceCapabilities(
    val glRenderer: String,
    val glVersion: String,
    val maxTextureSize: Int,
    val totalRamMb: Long,
    val recommendedTier: QualityTier,
) {
    /** Longest side the effect pass renders at for a tier. Outputs are scaled from this. */
    fun maxProcessingSide(tier: QualityTier): Int = minOf(
        maxTextureSize,
        when (tier) {
            QualityTier.HIGH -> 3840
            QualityTier.BALANCED -> 1920
            QualityTier.LOW -> 1280
        },
    )

    companion object {
        private val weakGpuHints = listOf("Mali-G52", "Mali-G51", "Mali-G31", "Mali-T", "Adreno (TM) 5", "Adreno (TM) 4", "PowerVR")

        /** Must be called with a current GL context. */
        fun probe(context: Context): DeviceCapabilities {
            val renderer = GLES30.glGetString(GLES30.GL_RENDERER) ?: "unknown"
            val version = GLES30.glGetString(GLES30.GL_VERSION) ?: "unknown"
            val maxTex = IntArray(1).also { GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, it, 0) }[0]

            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            val ramMb = mem.totalMem / (1024 * 1024)

            var tier = when {
                am.isLowRamDevice || ramMb < 3500 -> QualityTier.LOW
                ramMb < 5500 -> QualityTier.BALANCED
                else -> QualityTier.HIGH
            }
            if (tier == QualityTier.HIGH && weakGpuHints.any { renderer.contains(it) }) tier = QualityTier.BALANCED
            return DeviceCapabilities(renderer, version, if (maxTex > 0) maxTex else 4096, ramMb, tier)
        }
    }
}
