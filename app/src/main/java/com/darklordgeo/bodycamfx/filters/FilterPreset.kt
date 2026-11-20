package com.darklordgeo.bodycamfx.filters

/** What a preset supports or asks of the camera system. */
enum class PresetCapability {
    PREVIEW,
    PHOTO,
    VIDEO,
    /** Allows the optional HUD overlay. */
    OVERLAY,
    /** Uses the previous frame (motion blur / persistence). */
    TEMPORAL,
    /** Ask the camera for its widest zoom ratio (ultra-wide lens) when the preset is selected. */
    PREFERS_WIDE_LENS,
}

/** Gradient + glyph drawn as the carousel thumbnail. ARGB colours. */
data class PresetThumbnail(val topColor: Long, val bottomColor: Long, val glyph: String)

/**
 * An independent filter: a list of reusable effect modules plus this preset's own defaults.
 * Adding a filter = constructing one of these and registering it in [FilterRegistry].
 * Nothing in the camera, renderer, or UI needs to change.
 */
data class FilterPreset(
    val id: String,
    val name: String,
    val description: String,
    val thumbnail: PresetThumbnail,
    val modules: List<EffectModule>,
    /** Overrides of module parameter defaults, keyed by param key. */
    val defaults: Map<String, Float> = emptyMap(),
    val defaultIntensity: Float = 1f,
    val capabilities: Set<PresetCapability> = setOf(
        PresetCapability.PREVIEW, PresetCapability.PHOTO, PresetCapability.VIDEO, PresetCapability.OVERLAY,
    ),
    /** False = listed in the carousel as "coming soon" and not selectable. */
    val available: Boolean = true,
) {
    init {
        val keys = modules.flatMap { m -> m.params.map { it.key } }
        val unknown = defaults.keys - keys.toSet()
        require(unknown.isEmpty()) { "Preset '$id' overrides unknown params: $unknown" }
    }

    /** All adjustable parameters with this preset's defaults applied. */
    val params: List<ParamSpec> = modules.flatMap { it.params }.map { p ->
        defaults[p.key]?.let { p.copy(default = p.clamp(it)) } ?: p
    }

    fun param(key: String): ParamSpec? = params.firstOrNull { it.key == key }
}
