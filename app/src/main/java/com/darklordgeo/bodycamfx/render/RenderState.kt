package com.darklordgeo.bodycamfx.render

import com.darklordgeo.bodycamfx.filters.FilterPreset

/**
 * Optional on-frame HUD. Off by default. It is a stylistic overlay only: it never shows agency
 * names, badge or serial numbers, and the label defaults to a neutral "FX CAM" so footage is not
 * presented as genuine law-enforcement evidence.
 */
data class HudConfig(
    val enabled: Boolean = false,
    val showTimestamp: Boolean = true,
    val showRecIndicator: Boolean = true,
    val label: String = "FX CAM 01",
)

/** Immutable snapshot the GL thread reads each frame (published through a @Volatile field). */
class RenderState(
    val preset: FilterPreset,
    /** Effective uniform values (intensity already applied), keyed by param key. */
    val values: Map<String, Float>,
    val hud: HudConfig,
    val recording: Boolean,
    val recordingStartedAtMs: Long,
) {
    companion object {
        fun of(preset: FilterPreset, raw: Map<String, Float>, intensity: Float, hud: HudConfig,
               recording: Boolean = false, recordingStartedAtMs: Long = 0L): RenderState {
            val eff = preset.params.associate { p ->
                p.key to p.effective(raw[p.key] ?: p.default, intensity)
            }
            return RenderState(preset, eff, hud, recording, recordingStartedAtMs)
        }
    }
}
