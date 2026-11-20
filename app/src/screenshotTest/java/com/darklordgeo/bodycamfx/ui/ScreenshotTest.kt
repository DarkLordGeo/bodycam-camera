package com.darklordgeo.bodycamfx.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.darklordgeo.bodycamfx.camera.CameraUiState
import com.darklordgeo.bodycamfx.camera.CaptureMode
import com.darklordgeo.bodycamfx.filters.FilterRegistry
import com.darklordgeo.bodycamfx.filters.FilterState
import com.darklordgeo.bodycamfx.media.MediaRepository
import com.darklordgeo.bodycamfx.settings.AppSettings
import com.darklordgeo.bodycamfx.ui.camera.AdjustPanel
import com.darklordgeo.bodycamfx.ui.camera.GlassFilterSelector
import com.darklordgeo.bodycamfx.ui.camera.ModeSwitch
import com.darklordgeo.bodycamfx.ui.camera.RecordingBadge
import com.darklordgeo.bodycamfx.ui.camera.ShutterButton
import com.darklordgeo.bodycamfx.ui.camera.ZoomChips
import com.darklordgeo.bodycamfx.ui.components.AppIcons
import com.darklordgeo.bodycamfx.ui.components.BarItem
import com.darklordgeo.bodycamfx.ui.components.GlassBottomBar
import com.darklordgeo.bodycamfx.ui.components.GlassBottomSheet
import com.darklordgeo.bodycamfx.ui.components.GlassButton
import com.darklordgeo.bodycamfx.ui.components.GlassIconButton
import com.darklordgeo.bodycamfx.ui.components.GlassSurface
import com.darklordgeo.bodycamfx.ui.components.LocalGlassBlur
import com.darklordgeo.bodycamfx.ui.gallery.GalleryFilter
import com.darklordgeo.bodycamfx.ui.gallery.GalleryScreen
import com.darklordgeo.bodycamfx.ui.settings.SettingsScreen
import com.darklordgeo.bodycamfx.ui.theme.BodycamTheme
import com.darklordgeo.bodycamfx.ui.theme.Palette
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * JVM screenshots of the real composables (no camera hardware): the camera chrome is drawn over a
 * synthetic bright/dark "footage" gradient to check glass contrast.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi-night")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            BodycamTheme {
                CompositionLocalProvider(LocalGlassBlur provides false) { content() }
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Composable
    private fun FakeFootage() {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0B0D0C), Color(0xFF8FA3A6), Color(0xFFE9E4D6), Color(0xFF4A5247), Color(0xFF151816)),
                ),
            ),
        )
    }

    private val filterState = FilterState("bodycam", 1f, emptyMap())

    @Composable
    private fun CameraChrome(state: CameraUiState) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            FakeFootage()
            Row(Modifier.fillMaxWidth().padding(16.dp).padding(top = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(AppIcons.FlashOff, "Flash", {})
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (state.recording) RecordingBadge(state.recordingMs)
                    else androidx.compose.material3.Text("BODYCAM", color = Palette.Text)
                }
                GlassIconButton(AppIcons.Tune, "Settings", {})
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 92.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (!state.recording) {
                    ZoomChips(1f, 0.6f, 8f, {})
                    Spacer(Modifier.height(8.dp))
                    GlassFilterSelector(FilterRegistry.all, filterState, emptyMap(), {}, {})
                    ModeSwitch(state.mode, true, {})
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    GlassSurface(Modifier.padding(0.dp).height(50.dp).fillMaxWidth(0.13f)) {}
                    ShutterButton(state.mode, state.recording, false, {})
                    GlassIconButton(AppIcons.FlipCamera, "Flip", {}, size = 50.dp)
                }
            }
            if (!state.recording) {
                GlassBottomBar(
                    listOf(BarItem("Camera", AppIcons.Camera), BarItem("Gallery", AppIcons.Gallery), BarItem("Settings", AppIcons.Settings)),
                    0, {}, Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
                )
            }
        }
    }

    @Test fun cameraPhoto() = shot("01_camera_photo") { CameraChrome(CameraUiState(mode = CaptureMode.PHOTO)) }

    @Test fun cameraRecording() = shot("02_camera_recording") {
        CameraChrome(CameraUiState(mode = CaptureMode.VIDEO, recording = true, recordingMs = 83_000))
    }

    @Test fun adjustSheet() = shot("03_adjust_sheet") {
        Box(Modifier.fillMaxSize()) {
            FakeFootage()
            GlassBottomSheet(true, {}) {
                AdjustPanel(FilterRegistry.default, filterState, {}, { _, _ -> }, {})
            }
        }
    }

    @Test fun galleryEmpty() = shot("04_gallery_empty") {
        GalleryScreen(
            items = emptyList(), filter = GalleryFilter.ALL, onFilter = {},
            repository = MediaRepository(ApplicationProvider.getApplicationContext()),
            onOpen = {}, onOpenCamera = {}, bottomInset = 90.dp,
        )
    }

    @Test fun settings() = shot("05_settings") {
        SettingsScreen(AppSettings(), CameraUiState(), {}, 90.dp)
    }

    @Test fun dialogContent() = shot("06_delete_dialog") {
        Box(Modifier.fillMaxSize().background(Palette.Background), contentAlignment = Alignment.Center) {
            GlassSurface(strong = true) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.material3.Text("Delete this photo?", color = Palette.Text)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GlassButton("Cancel", {})
                        GlassButton("Delete", {}, danger = true)
                    }
                }
            }
        }
    }
}
