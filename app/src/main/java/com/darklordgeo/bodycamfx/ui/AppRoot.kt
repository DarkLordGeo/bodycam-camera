package com.darklordgeo.bodycamfx.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darklordgeo.bodycamfx.camera.CameraController
import com.darklordgeo.bodycamfx.filters.FilterStateStore
import com.darklordgeo.bodycamfx.media.DeleteResult
import com.darklordgeo.bodycamfx.media.MediaItem
import com.darklordgeo.bodycamfx.media.MediaRepository
import com.darklordgeo.bodycamfx.settings.SettingsRepository
import com.darklordgeo.bodycamfx.ui.camera.CameraScreen
import com.darklordgeo.bodycamfx.ui.components.AppIcons
import com.darklordgeo.bodycamfx.ui.components.BarItem
import com.darklordgeo.bodycamfx.ui.components.GlassBottomBar
import com.darklordgeo.bodycamfx.ui.components.GlassDialog
import com.darklordgeo.bodycamfx.ui.components.GlassToast
import com.darklordgeo.bodycamfx.ui.components.LocalGlassBlur
import com.darklordgeo.bodycamfx.ui.components.LocalHapticsEnabled
import com.darklordgeo.bodycamfx.ui.components.LocalHaze
import com.darklordgeo.bodycamfx.ui.gallery.GalleryFilter
import com.darklordgeo.bodycamfx.ui.gallery.GalleryScreen
import com.darklordgeo.bodycamfx.ui.gallery.MediaViewer
import com.darklordgeo.bodycamfx.ui.gallery.filtered
import com.darklordgeo.bodycamfx.ui.settings.SettingsScreen
import com.darklordgeo.bodycamfx.ui.theme.Palette
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Destination(val label: String) { CAMERA("Camera"), GALLERY("Gallery"), SETTINGS("Settings") }

private data class ViewerTarget(val id: Long, val isVideo: Boolean)

@Composable
fun AppRoot(
    controller: CameraController,
    filterStore: FilterStateStore,
    settingsRepo: SettingsRepository,
    mediaRepo: MediaRepository,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by settingsRepo.settings.collectAsStateWithLifecycle()
    val cameraState by controller.state.collectAsStateWithLifecycle()
    val media by remember { mediaRepo.observe() }.collectAsStateWithLifecycle(initialValue = emptyList())

    var destination by rememberSaveable { mutableStateOf(Destination.CAMERA) }
    var galleryFilter by rememberSaveable { mutableStateOf(GalleryFilter.ALL) }
    var viewer by remember { mutableStateOf<ViewerTarget?>(null) }
    var pendingDelete by remember { mutableStateOf<MediaItem?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }

    // ---------------------------------------------------------------- permissions
    fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    var cameraGranted by remember { mutableStateOf(granted(Manifest.permission.CAMERA)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        cameraGranted = r[Manifest.permission.CAMERA] ?: granted(Manifest.permission.CAMERA)
        if (!cameraGranted) toast = "Camera permission is required to take photos and videos"
    }
    val requestPermissions = {
        permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }
    LaunchedEffect(Unit) { if (!cameraGranted) requestPermissions() }

    // ---------------------------------------------------------------- delete with consent fallback
    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        toast = if (r.resultCode == android.app.Activity.RESULT_OK) "Deleted" else "Not deleted"
    }

    LaunchedEffect(media.firstOrNull()?.id) { controller.loadLatestThumb(media.firstOrNull()?.uri) }
    LaunchedEffect(toast) { if (toast != null) { delay(2500); toast = null } }

    val haze = remember { HazeState() }
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val barSpace = navInset + 76.dp
    val showBar = viewer == null && !cameraState.recording

    BackHandler(enabled = destination != Destination.CAMERA && viewer == null) { destination = Destination.CAMERA }

    CompositionLocalProvider(
        LocalHaze provides haze,
        LocalGlassBlur provides (settings.glassBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S),
        LocalHapticsEnabled provides settings.haptics,
    ) {
        Box(Modifier.fillMaxSize().background(Palette.Background)) {
            Crossfade(destination, label = "destination") { dest ->
                when (dest) {
                    Destination.CAMERA -> CameraScreen(
                        controller = controller,
                        filterStore = filterStore,
                        settings = settings,
                        hasCameraPermission = cameraGranted,
                        onRequestPermission = {
                            if (granted(Manifest.permission.CAMERA)) cameraGranted = true else requestPermissions()
                        },
                        onUpdateSettings = settingsRepo::update,
                        onOpenGallery = {
                            val uri = cameraState.lastMediaUri
                            if (settings.thumbnailOpensInApp || uri == null) {
                                destination = Destination.GALLERY
                            } else {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                    )
                                }.onFailure { destination = Destination.GALLERY }
                            }
                        },
                        onOpenSettings = { destination = Destination.SETTINGS },
                        bottomInset = if (showBar) barSpace else navInset + 16.dp,
                    )
                    Destination.GALLERY -> GalleryScreen(
                        items = media,
                        filter = galleryFilter,
                        onFilter = { galleryFilter = it },
                        repository = mediaRepo,
                        onOpen = { viewer = ViewerTarget(it.id, it.isVideo) },
                        onOpenCamera = { destination = Destination.CAMERA },
                        bottomInset = barSpace,
                    )
                    Destination.SETTINGS -> SettingsScreen(
                        settings = settings,
                        camera = cameraState,
                        onUpdate = settingsRepo::update,
                        bottomInset = barSpace,
                    )
                }
            }

            AnimatedVisibility(
                showBar,
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                GlassBottomBar(
                    items = listOf(
                        BarItem("Camera", AppIcons.Camera),
                        BarItem("Gallery", AppIcons.Gallery),
                        BarItem("Settings", AppIcons.Settings),
                    ),
                    selected = destination.ordinal,
                    onSelect = { destination = Destination.entries[it] },
                )
            }

            viewer?.let { target ->
                CompositionLocalProvider(LocalHaze provides null) {
                    MediaViewer(
                        items = media.let { list ->
                            if (destination == Destination.GALLERY) list.filtered(galleryFilter) else list
                        },
                        startId = target.id,
                        startIsVideo = target.isVideo,
                        onClose = { viewer = null },
                        onDelete = { pendingDelete = it },
                    )
                }
            }

            GlassToast(toast, Modifier.align(Alignment.TopCenter).padding(top = 64.dp))

            pendingDelete?.let { item ->
                val kind = if (item.isVideo) "video" else "photo"
                GlassDialog(
                    title = "Delete this $kind?",
                    message = "It will be removed from this device, including Google Photos and other gallery apps. This can't be undone.",
                    confirmLabel = "Delete",
                    danger = true,
                    onDismiss = { pendingDelete = null },
                    onConfirm = {
                        pendingDelete = null
                        scope.launch {
                            when (val r = mediaRepo.delete(item)) {
                                DeleteResult.Deleted -> toast = "${kind.replaceFirstChar(Char::uppercase)} deleted"
                                DeleteResult.AlreadyGone -> toast = "Already removed"
                                is DeleteResult.NeedsConsent ->
                                    consentLauncher.launch(IntentSenderRequest.Builder(r.intentSender).build())
                                is DeleteResult.Failed -> toast = "Couldn't delete: ${r.message}"
                            }
                        }
                    },
                )
            }
        }
    }
}
