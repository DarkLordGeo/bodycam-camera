package com.darklordgeo.bodycamfx.filters

/**
 * Where an effect runs inside the single composed fragment shader.
 *
 *  UV       – remaps the sampling coordinate (lens distortion, FOV).      `uv = fx(uv);`
 *  SAMPLE   – replaces how the camera texture is read (chromatic aberration).
 *             At most one SAMPLE module is used; the last one wins.        `color = fx(uv);`
 *  COLOR    – per-pixel colour operations, applied in list order.         `color = fx(color, uv);`
 *  TEMPORAL – blends with the previous processed frame (motion blur).     `color = fx(color);`
 */
enum class Stage { UV, SAMPLE, COLOR, TEMPORAL, OVERLAY }

/** Device rendering tiers. Modules declare the minimum tier they need. */
enum class QualityTier { LOW, BALANCED, HIGH }

/**
 * One user-adjustable float parameter. The key doubles as the GLSL uniform name, so it must be
 * a valid identifier and unique within a preset.
 *
 * [neutral] is the value at which the effect does nothing. The preset's global intensity
 * interpolates every parameter between [neutral] and its configured value.
 */
data class ParamSpec(
    val key: String,
    val label: String,
    val min: Float,
    val max: Float,
    val default: Float,
    val neutral: Float,
    val group: String = "Image",
) {
    init {
        require(key.matches(Regex("u[A-Z][A-Za-z0-9]*"))) { "Param key '$key' must look like uName" }
        require(min <= max) { "min > max for $key" }
    }

    fun clamp(v: Float) = v.coerceIn(min, max)
    fun effective(value: Float, intensity: Float) = neutral + (value - neutral) * intensity
}

/**
 * A reusable GPU effect. Modules carry their own GLSL and parameters; presets combine them.
 * All modules of a preset are fused into one shader pass (see [ShaderComposer]).
 *
 * GLSL available to every module (defined by the composer prelude):
 *   uniforms  uResolution (vec2, processing size in px), uTime (float, seconds), uFrame (float)
 *   helpers   vec3 sampleRaw(vec2 uv)  luma(vec3)  hash13(vec3)  valueNoise(vec2)  hue2rgb(float)
 *   in main   vUv (output-space uv), uv (source uv after UV stage), color (vec3)
 */
data class EffectModule(
    val id: String,
    val stage: Stage,
    val params: List<ParamSpec>,
    /** Function definitions. Must not declare the params' uniforms; the composer does that. */
    val glsl: String,
    /** One statement inserted into main(), e.g. `color = fx_vignette(color);` */
    val call: String,
    val minTier: QualityTier = QualityTier.LOW,
    /** Uses the history texture (previous frame). */
    val temporal: Boolean = stage == Stage.TEMPORAL,
)
