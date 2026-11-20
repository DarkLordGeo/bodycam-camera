package com.darklordgeo.bodycamfx.filters

import com.darklordgeo.bodycamfx.filters.presets.BodycamPreset
import com.darklordgeo.bodycamfx.filters.presets.CctvPreset
import com.darklordgeo.bodycamfx.filters.presets.NaturalPreset
import com.darklordgeo.bodycamfx.filters.presets.NightVisionPreset
import com.darklordgeo.bodycamfx.filters.presets.PlannedPresets

/**
 * The single place filters are registered. The carousel, settings persistence and renderer all
 * read from here, so a new preset only needs a `register(...)` line.
 */
object FilterRegistry {
    private val presets = LinkedHashMap<String, FilterPreset>()

    init {
        register(BodycamPreset.preset)
        register(NaturalPreset.preset)
        register(CctvPreset.preset)
        register(NightVisionPreset.preset)
        PlannedPresets.all.forEach(::register)
    }

    fun register(preset: FilterPreset) {
        require(preset.id.matches(Regex("[a-z0-9_]+"))) { "Preset id '${preset.id}' must be lower_snake_case" }
        require(preset.id !in presets) { "Preset '${preset.id}' registered twice" }
        presets[preset.id] = preset
    }

    val all: List<FilterPreset> get() = presets.values.toList()
    val available: List<FilterPreset> get() = presets.values.filter { it.available }
    val default: FilterPreset get() = presets.getValue(BodycamPreset.ID)

    operator fun get(id: String): FilterPreset? = presets[id]
}
