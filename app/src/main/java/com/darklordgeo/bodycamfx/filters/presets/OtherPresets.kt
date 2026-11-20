package com.darklordgeo.bodycamfx.filters.presets

import com.darklordgeo.bodycamfx.filters.Effects
import com.darklordgeo.bodycamfx.filters.FilterPreset
import com.darklordgeo.bodycamfx.filters.PresetCapability
import com.darklordgeo.bodycamfx.filters.PresetThumbnail

/** Ungraded camera output with basic exposure / colour controls. */
object NaturalPreset {
    val preset = FilterPreset(
        id = "natural",
        name = "NATURAL",
        description = "Clean camera image with basic exposure and colour controls.",
        thumbnail = PresetThumbnail(0xFF6B7A86, 0xFF2A3036, "N"),
        modules = listOf(Effects.exposure, Effects.whiteBalance, Effects.toneColor),
        defaults = mapOf("uExposureDrift" to 0f),
    )
}

/**
 * CCTV – first-pass preset that exists to show the registry in action: it is pure configuration
 * of the shared effect library and needed no new code.
 */
object CctvPreset {
    val preset = FilterPreset(
        id = "cctv",
        name = "CCTV",
        description = "Fixed security camera: washed monochrome-ish colour, heavy noise, soft lens.",
        thumbnail = PresetThumbnail(0xFF5D6360, 0xFF1C1E1D, "CC"),
        modules = listOf(
            Effects.lens, Effects.exposure, Effects.whiteBalance, Effects.toneColor,
            Effects.monochrome, Effects.sensorNoise, Effects.grain, Effects.vignette,
        ),
        defaults = mapOf(
            "uDistortion" to 0.22f, "uFisheye" to 0.05f, "uLensFill" to 1f,
            "uExposureDrift" to 0.1f, "uContrast" to 1.25f, "uBlackPoint" to 0.04f, "uShoulder" to 0.1f,
            "uSaturation" to 0.4f, "uMonoAmount" to 0.55f, "uMonoHue" to 0.45f, "uMonoTint" to 0.15f,
            "uSensorNoise" to 0.55f, "uLowLight" to 0.6f, "uGrain" to 0.35f, "uGrainSize" to 1.8f,
            "uVignette" to 0.25f,
        ),
    )
}

/** NIGHT VISION – first-pass preset built only from shared modules. */
object NightVisionPreset {
    val preset = FilterPreset(
        id = "night_vision",
        name = "NIGHT VISION",
        description = "Green phosphor image-intensifier look with gain noise and a dark tube vignette.",
        thumbnail = PresetThumbnail(0xFF3F8A3A, 0xFF07170A, "NV"),
        modules = listOf(
            Effects.lens, Effects.exposure, Effects.toneColor, Effects.monochrome,
            Effects.sensorNoise, Effects.grain, Effects.vignette, Effects.motionBlur,
        ),
        defaults = mapOf(
            "uDistortion" to 0.15f, "uFisheye" to 0.05f, "uLensFill" to 0.2f,
            "uExposure" to 1.0f, "uExposureDrift" to 0.15f, "uContrast" to 1.2f, "uShoulder" to 0.6f,
            "uMonoAmount" to 1f, "uMonoHue" to 0.33f, "uMonoTint" to 0.9f,
            "uSensorNoise" to 0.7f, "uLowLight" to 0.2f, "uGrain" to 0.5f, "uGrainSize" to 1.6f,
            "uVignette" to 0.85f, "uMotionBlur" to 0.25f,
        ),
        capabilities = setOf(
            PresetCapability.PREVIEW, PresetCapability.PHOTO, PresetCapability.VIDEO,
            PresetCapability.OVERLAY, PresetCapability.TEMPORAL,
        ),
    )
}

/** Planned looks. Listed in the carousel as "soon"; each becomes real by adding modules + defaults. */
object PlannedPresets {
    private fun planned(id: String, name: String, desc: String, top: Long, bottom: Long, glyph: String) =
        FilterPreset(
            id = id, name = name, description = desc,
            thumbnail = PresetThumbnail(top, bottom, glyph),
            modules = emptyList(), available = false,
        )

    val all = listOf(
        planned("vhs", "VHS", "Analog tape: chroma bleed, tracking noise, soft luma.", 0xFF6A3E7A, 0xFF1A1024, "VHS"),
        planned("thermal", "THERMAL", "False-colour thermal imaging palette.", 0xFFE0702A, 0xFF2A0B4A, "TH"),
        planned("analog_film", "ANALOG FILM", "Film stock grain, halation and print grade.", 0xFFB58A55, 0xFF2E2418, "AF"),
        planned("camcorder", "CAMCORDER", "Early digital camcorder: interlacing, oversharpening.", 0xFF4F7FA0, 0xFF102030, "DV"),
        planned("cinematic", "CINEMATIC", "Teal-orange grade with soft highlight roll-off.", 0xFF2E7F86, 0xFF3A1E0E, "CI"),
        planned("retro_digital", "RETRO DIGITAL", "2000s compact camera: low dynamic range, JPEG blocks.", 0xFF9AA0A8, 0xFF30343A, "RD"),
    )
}
