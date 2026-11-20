package com.darklordgeo.bodycamfx.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darklordgeo.bodycamfx.camera.CameraUiState
import com.darklordgeo.bodycamfx.filters.FilterRegistry
import com.darklordgeo.bodycamfx.media.MediaStorage
import com.darklordgeo.bodycamfx.render.QualityMode
import com.darklordgeo.bodycamfx.settings.AppSettings
import com.darklordgeo.bodycamfx.settings.CaptureResolution
import com.darklordgeo.bodycamfx.settings.VideoBitrate
import com.darklordgeo.bodycamfx.ui.camera.LabeledSlider
import com.darklordgeo.bodycamfx.ui.camera.QuickToggle
import com.darklordgeo.bodycamfx.ui.components.GlassButton
import com.darklordgeo.bodycamfx.ui.components.GlassCard
import com.darklordgeo.bodycamfx.ui.theme.Glass
import com.darklordgeo.bodycamfx.ui.theme.Palette
import com.darklordgeo.bodycamfx.ui.theme.Radius
import com.darklordgeo.bodycamfx.ui.theme.Space

@Composable
fun SettingsScreen(
    settings: AppSettings,
    camera: CameraUiState,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    bottomInset: Dp,
) {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = Space.m)
                .padding(bottom = bottomInset + Space.l),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            Spacer(Modifier.height(Space.s))
            Text("Settings", style = MaterialTheme.typography.titleLarge, color = Palette.Text, modifier = Modifier.padding(start = Space.xs))

            Section("Capture") {
                Label("Camera resolution (photos and video)")
                Choices(CaptureResolution.entries, settings.resolution, { it.label }, enabled = { it in camera.supportedResolutions || !camera.ready }) {
                    onUpdate { s -> s.copy(resolution = it) }
                }
                Hint("Resolutions your camera can't record are disabled. Photos are saved at full stream resolution.")
                Spacer(Modifier.height(Space.m))
                Label("Video quality (bitrate)")
                Choices(VideoBitrate.entries, settings.bitrate, { it.label }) { onUpdate { s -> s.copy(bitrate = it) } }
                QuickToggle("Record audio", settings.recordAudio) { on -> onUpdate { it.copy(recordAudio = on) } }
            }

            Section("Filters") {
                Label("Default filter at launch")
                Choices(FilterRegistry.available, FilterRegistry[settings.defaultFilterId] ?: FilterRegistry.default, { it.name }) {
                    onUpdate { s -> s.copy(defaultFilterId = it.id) }
                }
                Spacer(Modifier.height(Space.s))
                LabeledSlider(
                    "Default filter intensity", settings.defaultIntensity, 0f..1f,
                    { v -> onUpdate { it.copy(defaultIntensity = v) } },
                    "${(settings.defaultIntensity * 100).toInt()}%",
                )
                QuickToggle("Use ultra-wide lens for BODYCAM", settings.useUltraWide) { on -> onUpdate { it.copy(useUltraWide = on) } }
                Spacer(Modifier.height(Space.s))
                Label("Processing quality")
                Choices(QualityMode.entries, settings.qualityMode, { it.name.lowercase().replaceFirstChar(Char::uppercase) }) {
                    onUpdate { s -> s.copy(qualityMode = it) }
                }
                Hint("Auto starts from your device's profile (currently ${camera.tier.name.lowercase()}) and steps down if frames run slow.")
            }

            Section("Front camera") {
                QuickToggle("Mirror selfies", settings.mirrorSelfies) { on -> onUpdate { it.copy(mirrorSelfies = on) } }
                Hint("On: saved selfies match the mirrored viewfinder. Off: saved as the camera sees them.")
            }

            Section("Recording HUD") {
                QuickToggle("Show HUD overlay", settings.hud.enabled) { on -> onUpdate { it.copy(hud = it.hud.copy(enabled = on)) } }
                QuickToggle("Timestamp", settings.hud.showTimestamp) { on -> onUpdate { it.copy(hud = it.hud.copy(showTimestamp = on)) } }
                QuickToggle("REC indicator while recording", settings.hud.showRecIndicator) { on ->
                    onUpdate { it.copy(hud = it.hud.copy(showRecIndicator = on)) }
                }
                Label("Label")
                HudLabelField(settings.hud.label) { t -> onUpdate { it.copy(hud = it.hud.copy(label = t)) } }
                Hint("The HUD is a stylistic effect and is burned into photos and videos. It isn't an official recording and shouldn't be presented as evidence.")
            }

            Section("Flashlight & feedback") {
                QuickToggle("Remember flashlight state", settings.rememberTorch) { on -> onUpdate { it.copy(rememberTorch = on) } }
                QuickToggle("Haptic feedback", settings.haptics) { on -> onUpdate { it.copy(haptics = on) } }
                QuickToggle("Background blur on glass panels", settings.glassBlur) { on -> onUpdate { it.copy(glassBlur = on) } }
                Hint("Blur needs Android 12+. Turn it off for longer battery life; panels fall back to tinted glass.")
            }

            Section("Gallery") {
                QuickToggle("Recent-capture thumbnail opens in-app gallery", settings.thumbnailOpensInApp) { on ->
                    onUpdate { it.copy(thumbnailOpensInApp = on) }
                }
                Hint("When off, the thumbnail opens the photo in your phone's default gallery app.")
            }

            Section("Storage") {
                Info("Photos", MediaStorage.PHOTO_DIR)
                Info("Selfies", MediaStorage.SELFIE_DIR)
                Info("Videos", MediaStorage.VIDEO_DIR)
                Hint("Saved to shared storage, so captures also appear in Google Photos and other gallery apps.")
            }

            Section("About") {
                Info("Version", version)
                camera.gpu?.let {
                    Info("GPU", it.glRenderer)
                    Info("Device tier", it.recommendedTier.name.lowercase())
                }
                if (camera.fps > 0f) Info("Preview", "${camera.fps.toInt()} fps")
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    GlassCard(Modifier.fillMaxWidth().widthIn(max = 640.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = Palette.TextFaint)
        Spacer(Modifier.height(Space.s))
        content()
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Palette.Text, modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun Info(label: String, value: String) {
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Palette.Text, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Choices(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    enabled: (T) -> Boolean = { true },
    onSelect: (T) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        options.forEach { o ->
            GlassButton(label(o), { onSelect(o) }, selected = o == selected, enabled = enabled(o))
        }
    }
}

@Composable
private fun HudLabelField(value: String, onChange: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    BasicTextField(
        value = text,
        onValueChange = { t -> text = t.take(24); onChange(text) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Palette.Text),
        cursorBrush = SolidColor(Palette.Text),
        modifier = Modifier
            .fillMaxWidth()
            .border(Glass.BorderWidth, Glass.Border, Radius.Control)
            .padding(horizontal = Space.m, vertical = Space.m),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (text.isEmpty()) Text("No label", color = Palette.TextFaint, style = MaterialTheme.typography.bodyMedium)
                inner()
            }
        },
    )
}
