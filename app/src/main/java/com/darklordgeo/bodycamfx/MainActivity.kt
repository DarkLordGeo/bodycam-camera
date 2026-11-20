package com.darklordgeo.bodycamfx

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.darklordgeo.bodycamfx.camera.CameraController
import com.darklordgeo.bodycamfx.filters.FilterStateStore
import com.darklordgeo.bodycamfx.media.MediaRepository
import com.darklordgeo.bodycamfx.media.MediaStorage
import com.darklordgeo.bodycamfx.settings.SettingsRepository
import com.darklordgeo.bodycamfx.ui.AppRoot
import com.darklordgeo.bodycamfx.ui.theme.BodycamTheme
import kotlinx.coroutines.launch

/**
 * Wires the independent modules together:
 *   settings · filter state · media storage/gallery · camera controller (which owns the GL pipeline)
 */
class MainActivity : ComponentActivity() {

    private lateinit var settingsRepo: SettingsRepository
    private lateinit var filterStore: FilterStateStore
    private lateinit var mediaRepo: MediaRepository
    private lateinit var controller: CameraController

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        settingsRepo = SettingsRepository(this)
        val initial = settingsRepo.settings.value
        filterStore = FilterStateStore(this, initial.defaultFilterId, initial.defaultIntensity)
        mediaRepo = MediaRepository(this)
        controller = CameraController(this, MediaStorage(this), lifecycleScope)

        lifecycleScope.launch { settingsRepo.settings.collect { controller.applySettings(it) } }

        setContent {
            BodycamTheme {
                AppRoot(controller, filterStore, settingsRepo, mediaRepo)
            }
        }
    }

    override fun onStop() {
        // Finalise an in-progress recording cleanly before the camera is taken away.
        controller.stopRecording()
        super.onStop()
    }

    override fun onDestroy() {
        controller.release()
        super.onDestroy()
    }
}
