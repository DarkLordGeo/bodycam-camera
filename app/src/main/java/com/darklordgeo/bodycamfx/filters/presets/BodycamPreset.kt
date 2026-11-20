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
            Effects.sharpen,
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
            "uDistortion" to 0.27f,
            "uFisheye" to 0.14f,
            "uFov" to 0.97f,
            "uChromatic" to 0.30f,
            "uSharpen" to 0.55f,
            "uExposure" to 0.05f,
            "uExposureDrift" to 0.30f,
            "uTemperature" to -0.12f,
            "uTint" to 0.14f,
            "uContrast" to 1.16f,
            "uBlackPoint" to 0.015f,
            "uShoulder" to 0.40f,
            "uSaturation" to 0.76f,
            "uShadowHue" to 0.48f,
            "uShadowAmount" to 0.35f,
            "uHighlightHue" to 0.11f,
            "uHighlightAmount" to 0.20f,
            "uSensorNoise" to 0.35f,
            "uLowLight" to 0.50f,
            "uGrain" to 0.30f,
            "uGrainSize" to 1.3f,
            "uVignette" to 0.35f,
            "uMotionBlur" to 0f,
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
