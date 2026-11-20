package com.darklordgeo.bodycamfx.settings

import android.content.Context
import android.content.SharedPreferences
import com.darklordgeo.bodycamfx.filters.FilterRegistry
import com.darklordgeo.bodycamfx.render.HudConfig
import com.darklordgeo.bodycamfx.render.QualityMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Capture stream resolution; photos and videos both come from this stream. */
enum class CaptureResolution(val label: String) { HD("720p"), FHD("1080p"), UHD("4K") }

/** Video encoder bitrate target. STANDARD lets CameraX choose. Values in Mbps per resolution. */
enum class VideoBitrate(val label: String, val mbps: Map<CaptureResolution, Int>?) {
    STANDARD("Standard", null),
    HIGH("High", mapOf(CaptureResolution.HD to 12, CaptureResolution.FHD to 20, CaptureResolution.UHD to 50)),
    MAX("Maximum", mapOf(CaptureResolution.HD to 16, CaptureResolution.FHD to 30, CaptureResolution.UHD to 80)),
}

data class AppSettings(
    val resolution: CaptureResolution = CaptureResolution.FHD,
    val bitrate: VideoBitrate = VideoBitrate.STANDARD,
    val recordAudio: Boolean = true,
    val defaultFilterId: String = "bodycam",
    val defaultIntensity: Float = 1f,
    val mirrorSelfies: Boolean = true,
    val rememberTorch: Boolean = false,
    val lastTorch: Boolean = false,
    val haptics: Boolean = true,
    val glassBlur: Boolean = true,
    val thumbnailOpensInApp: Boolean = true,
    val useUltraWide: Boolean = true,
    val qualityMode: QualityMode = QualityMode.AUTO,
    val hud: HudConfig = HudConfig(),
)

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        save(next)
    }

    private inline fun <reified T : Enum<T>> SharedPreferences.enum(key: String, default: T): T =
        getString(key, null)?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    private fun load(): AppSettings {
        val d = AppSettings()
        val filter = prefs.getString("defaultFilter", d.defaultFilterId)!!
        return AppSettings(
            resolution = prefs.enum("resolution", d.resolution),
            bitrate = prefs.enum("bitrate", d.bitrate),
            recordAudio = prefs.getBoolean("recordAudio", d.recordAudio),
            defaultFilterId = if (FilterRegistry[filter]?.available == true) filter else d.defaultFilterId,
            defaultIntensity = prefs.getFloat("defaultIntensity", d.defaultIntensity),
            mirrorSelfies = prefs.getBoolean("mirrorSelfies", d.mirrorSelfies),
            rememberTorch = prefs.getBoolean("rememberTorch", d.rememberTorch),
            lastTorch = prefs.getBoolean("lastTorch", d.lastTorch),
            haptics = prefs.getBoolean("haptics", d.haptics),
            glassBlur = prefs.getBoolean("glassBlur", d.glassBlur),
            thumbnailOpensInApp = prefs.getBoolean("thumbInApp", d.thumbnailOpensInApp),
            useUltraWide = prefs.getBoolean("ultraWide", d.useUltraWide),
            qualityMode = prefs.enum("qualityMode", d.qualityMode),
            hud = HudConfig(
                enabled = prefs.getBoolean("hudEnabled", d.hud.enabled),
                showTimestamp = prefs.getBoolean("hudTimestamp", d.hud.showTimestamp),
                showRecIndicator = prefs.getBoolean("hudRec", d.hud.showRecIndicator),
                label = prefs.getString("hudLabel", d.hud.label) ?: d.hud.label,
            ),
        )
    }

    private fun save(s: AppSettings) {
        prefs.edit()
            .putString("resolution", s.resolution.name)
            .putString("bitrate", s.bitrate.name)
            .putBoolean("recordAudio", s.recordAudio)
            .putString("defaultFilter", s.defaultFilterId)
            .putFloat("defaultIntensity", s.defaultIntensity)
            .putBoolean("mirrorSelfies", s.mirrorSelfies)
            .putBoolean("rememberTorch", s.rememberTorch)
            .putBoolean("lastTorch", s.lastTorch)
            .putBoolean("haptics", s.haptics)
            .putBoolean("glassBlur", s.glassBlur)
            .putBoolean("thumbInApp", s.thumbnailOpensInApp)
            .putBoolean("ultraWide", s.useUltraWide)
            .putString("qualityMode", s.qualityMode.name)
            .putBoolean("hudEnabled", s.hud.enabled)
            .putBoolean("hudTimestamp", s.hud.showTimestamp)
            .putBoolean("hudRec", s.hud.showRecIndicator)
            .putString("hudLabel", s.hud.label)
            .apply()
    }
}
