package com.darklordgeo.bodycamfx.filters.presets

import com.darklordgeo.bodycamfx.filters.Effects
import com.darklordgeo.bodycamfx.filters.FilterPreset
import com.darklordgeo.bodycamfx.filters.PresetCapability
import com.darklordgeo.bodycamfx.filters.PresetThumbnail

/**
 * BODYCAM: a wide, chest-mounted small-sensor camera. The look comes from many small, physically
 * motivated imperfections stacked together rather than one strong effect:
 *
 *  - ultra-wide lens (uses the device's ultra-wide camera when available) with barrel + fisheye
 *    curvature, slight lateral chromatic aberration toward the edges, and a soft optical vignette
 *  - aggressive in-camera sharpening with faint halos
 *  - slightly cool / green white balance and muted saturation, typical of fixed-WB small sensors
 *  - firm contrast, crushed blacks and a soft highlight shoulder
 *  - shadow-heavy luminance + chroma noise and a lifted black level in low light
 *  - fine digital grain and slow auto-exposure breathing
 *
 * See docs/BODYCAM_PARAMETERS.md for what each parameter does and its useful range.
 */
object BodycamPreset {
    const val ID = "bodycam"

    val preset = FilterPreset(
        id = ID,
        name = "BODYCAM",
        description = "Wide-angle body-mounted camera: fisheye lens, sensor noise, cool muted grade.",
        thumbnail = PresetThumbnail(0xFF3A4440, 0xFF0D1110, "BC"),
        modules = listOf(
            Effects.lens,
            Effects.chromatic,
            Effects.compression,
            Effects.sharpen,
            Effects.bloom,
            Effects.exposure,
            Effects.whiteBalance,
            Effects.toneColor,
            Effects.splitTone,
            Effects.sensorNoise,
            Effects.grain,
            Effects.vignette,
            Effects.motionBlur,
        ),
        defaults = mapOf(
            "uDistortion" to 0.18f,
            "uFisheye" to 0.34f,
            "uLensFill" to 0.45f,
            "uFov" to 1f,
            "uChromatic" to 0.45f,
            "uSharpen" to 0.75f,
            "uExposure" to 0.05f,
            "uExposureDrift" to 0.40f,
            "uTemperature" to -0.18f,
            "uTint" to 0.18f,
            "uContrast" to 1.24f,
            "uBlackPoint" to 0.02f,
            "uShoulder" to 0.25f,
            "uSaturation" to 0.66f,
            "uShadowHue" to 0.48f,
            "uShadowAmount" to 0.45f,
            "uHighlightHue" to 0.11f,
            "uHighlightAmount" to 0.15f,
            "uSensorNoise" to 0.48f,
            "uLowLight" to 0.60f,
            "uGrain" to 0.40f,
            "uGrainSize" to 1.3f,
            "uVignette" to 0.55f,
            "uMotionBlur" to 0f,
            "uBloom" to 0.45f,
            "uCompression" to 0.35f,
        ),
        defaultIntensity = 1f,
        capabilities = setOf(
            PresetCapability.PREVIEW,
            PresetCapability.PHOTO,
            PresetCapability.VIDEO,
            PresetCapability.OVERLAY,
            PresetCapability.TEMPORAL,
            PresetCapability.PREFERS_WIDE_LENS,
        ),
    )
}
