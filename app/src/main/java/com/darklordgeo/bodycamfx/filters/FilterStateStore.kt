package com.darklordgeo.bodycamfx.filters

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The user's filter choices: which preset is active, its intensity, and per-preset parameter edits. */
data class FilterState(
    val selectedId: String,
    val intensity: Float,
    /** presetId -> (paramKey -> value). Only edited params are stored. */
    val overrides: Map<String, Map<String, Float>>,
) {
    val preset: FilterPreset get() = FilterRegistry[selectedId] ?: FilterRegistry.default

    fun valuesFor(preset: FilterPreset): Map<String, Float> {
        val o = overrides[preset.id].orEmpty()
        return preset.params.associate { it.key to (o[it.key] ?: it.default) }
    }
}

/** Persists parameter edits per preset. Selection + intensity start from the user's defaults each launch. */
class FilterStateStore(context: Context, defaultId: String, defaultIntensity: Float) {
    private val prefs = context.getSharedPreferences("filters", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(
        FilterState(
            selectedId = FilterRegistry[defaultId]?.takeIf { it.available }?.id ?: FilterRegistry.default.id,
            intensity = defaultIntensity.coerceIn(0f, 1f),
            overrides = loadOverrides(),
        ),
    )
    val state: StateFlow<FilterState> = _state.asStateFlow()

    private fun loadOverrides(): Map<String, Map<String, Float>> {
        val out = HashMap<String, MutableMap<String, Float>>()
        for ((k, v) in prefs.all) {
            val parts = k.split('.')
            if (parts.size != 3 || parts[0] != "p" || v !is Float) continue
            val preset = FilterRegistry[parts[1]] ?: continue
            val spec = preset.param(parts[2]) ?: continue
            out.getOrPut(preset.id) { HashMap() }[spec.key] = spec.clamp(v)
        }
        return out
    }

    fun select(id: String) {
        val p = FilterRegistry[id] ?: return
        if (!p.available) return
        _state.value = _state.value.copy(selectedId = id)
    }

    fun setIntensity(v: Float) {
        _state.value = _state.value.copy(intensity = v.coerceIn(0f, 1f))
    }

    fun setParam(presetId: String, key: String, value: Float) {
        val spec = FilterRegistry[presetId]?.param(key) ?: return
        val v = spec.clamp(value)
        val cur = _state.value
        val forPreset = cur.overrides[presetId].orEmpty() + (key to v)
        _state.value = cur.copy(overrides = cur.overrides + (presetId to forPreset))
        prefs.edit().putFloat("p.$presetId.$key", v).apply()
    }

    fun resetPreset(presetId: String) {
        val cur = _state.value
        _state.value = cur.copy(overrides = cur.overrides - presetId)
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith("p.$presetId.") }.forEach { editor.remove(it) }
        editor.apply()
    }
}
