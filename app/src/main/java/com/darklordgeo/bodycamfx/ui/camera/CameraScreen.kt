package com.darklordgeo.bodycamfx.ui.camera

import android.view.OrientationEventListener
import android.view.WindowManager
import android.app.Activity
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darklordgeo.bodycamfx.camera.CameraController
import com.darklordgeo.bodycamfx.camera.CaptureMode
import com.darklordgeo.bodycamfx.filters.FilterRegistry
import com.darklordgeo.bodycamfx.filters.FilterStateStore
import com.darklordgeo.bodycamfx.settings.AppSettings
import com.darklordgeo.bodycamfx.settings.CaptureResolution
import com.darklordgeo.bodycamfx.ui.components.AppIcons
import com.darklordgeo.bodycamfx.ui.components.GlassBottomSheet
import com.darklordgeo.bodycamfx.ui.components.GlassButton
import com.darklordgeo.bodycamfx.ui.components.GlassIconButton
import com.darklordgeo.bodycamfx.ui.components.GlassSurface
import com.darklordgeo.bodycamfx.ui.components.GlassToast
import com.darklordgeo.bodycamfx.ui.components.glassBackdrop
import com.darklordgeo.bodycamfx.ui.theme.Palette
import com.darklordgeo.bodycamfx.ui.theme.Radius
import com.darklordgeo.bodycamfx.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun CameraScreen(
    controller: CameraController,
    filterStore: FilterStateStore,
    settings: AppSettings,
    hasCameraPermission: Boolean,
    onRequestPermission: () -> Unit,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
    bottomInset: Dp,
) {
    val ui by controller.state.collectAsStateWithLifecycle()
    val fs by filterStore.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    val previewView = remember {
        PreviewView(context).apply {
            // TextureView-backed so the preview can sit under (and be blurred by) glass surfaces.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FIT_CENTER
            setBackgroundColor(android.graphics.Color.BLACK)
        }
    }

    // Bind while this screen is visible; unbinding on leave saves battery and releases the camera.
    DisposableEffect(hasCameraPermission) {
        if (hasCameraPermission) controller.attach(lifecycleOwner, previewView.surfaceProvider)
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            controller.detach()
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Physical orientation: drives media orientation, HUD orientation and icon rotation.
    var orientation by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(o: Int) {
                if (o == ORIENTATION_UNKNOWN) return
                val snapped = ((o + 45) / 90 * 90) % 360
                if (snapped != orientation) {
                    orientation = snapped
                    controller.setDeviceOrientation(snapped)
                }
            }
        }
        listener.enable()
        onDispose { listener.disable() }
    }
    val iconRotation by animateFloatAsState(
        targetValue = when (orientation) { 90 -> -90f; 270 -> 90f; 180 -> 180f; else -> 0f },
        label = "iconRot",
    )

    LaunchedEffect(fs) { controller.setFilter(fs.preset, fs.valuesFor(fs.preset), fs.intensity) }

    // Live processed thumbnails for the carousel.
    var thumbs by remember { mutableStateOf<Map<String, ImageBitmap>>(emptyMap()) }
    val latestFs by rememberUpdatedState(fs)
    LaunchedEffect(ui.ready) {
        while (ui.ready) {
            val states = FilterRegistry.available.map { controller.renderStateFor(it, latestFs.valuesFor(it)) }
            controller.processor.requestThumbnails(states, 144) { bitmaps ->
                scope.launch(Dispatchers.Main) { thumbs = bitmaps.mapValues { it.value.asImageBitmap() } }
            }
            delay(if (thumbs.isEmpty()) 600 else 2500)
        }
    }

    var showAdjust by remember { mutableStateOf(false) }
    var showQuick by remember { mutableStateOf(false) }
    var showEv by remember { mutableStateOf(false) }
    var flash by remember { mutableStateOf(false) }

    LaunchedEffect(ui.focusSeq) {
        if (ui.focusSeq > 0) { showEv = true; delay(4000); showEv = false }
    }
    LaunchedEffect(ui.message) {
        if (ui.message != null) { delay(3200); controller.consumeMessage() }
    }
    LaunchedEffect(ui.capturing) {
        if (ui.capturing) { flash = true; delay(120); flash = false }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize().glassBackdrop())

        // Gesture layer: pinch to zoom, tap to focus/meter, double tap to flip camera.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInputZoomFocus(
                    onZoom = { controller.zoomBy(it) },
                    onTap = { x, y -> controller.focus(previewView.meteringPointFactory, x, y) },
                    onDoubleTap = { controller.switchCamera() },
                ),
        )

        FocusRing(ui.focusPoint, ui.focusSeq)

        AnimatedVisibility(flash, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.55f)))
        }

        if (!hasCameraPermission) {
            PermissionPrompt(onRequestPermission, Modifier.align(Alignment.Center))
        }

        // ---------------------------------------------------------------- top bar
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = Space.l, vertical = Space.s),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(
                    icon = if (ui.torchOn) AppIcons.FlashOn else AppIcons.FlashOff,
                    contentDescription = if (ui.torchOn) "Turn flashlight off" else "Turn flashlight on",
                    onClick = {
                        val on = !ui.torchOn
                        controller.setTorch(on)
                        onUpdateSettings { it.copy(lastTorch = on) }
                    },
                    enabled = ui.hasFlash,
                    active = ui.torchOn,
                    iconRotation = iconRotation,
                )
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (ui.recording) {
                        RecordingBadge(ui.recordingMs)
                    } else {
                        Text(
                            buildBrand(),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 3.sp,
                            color = Palette.Text,
                        )
                    }
                }
                GlassIconButton(
                    icon = AppIcons.PersonOutline,
                    contentDescription = if (settings.outline.enabled) "Turn people outline off" else "Turn people outline on",
                    onClick = { onUpdateSettings { it.copy(outline = it.outline.copy(enabled = !it.outline.enabled)) } },
                    enabled = !ui.recording,
                    active = settings.outline.enabled,
                    iconRotation = iconRotation,
                )
                Spacer(Modifier.width(Space.s))
                GlassIconButton(
                    icon = AppIcons.Tune,
                    contentDescription = "Camera settings",
                    onClick = { showQuick = true },
                    enabled = !ui.recording,
                    iconRotation = iconRotation,
                )
            }
            AnimatedVisibility(showEv && ui.exposureMax > ui.exposureMin) {
                GlassSurface(Modifier.padding(top = Space.s).widthIn(max = 320.dp), shape = Radius.Pill) {
                    Box(Modifier.padding(horizontal = Space.l)) {
                        LabeledSlider(
                            label = "Exposure",
                            value = ui.exposureIndex.toFloat(),
                            range = ui.exposureMin.toFloat()..ui.exposureMax.toFloat(),
                            onChange = { controller.setExposure(it.roundToInt()) },
                            valueText = String.format(java.util.Locale.US, "%+.1f EV", ui.exposureIndex * ui.exposureStep),
                        )
                    }
                }
            }
            GlassToast(ui.message, Modifier.padding(top = Space.s))
        }

        // ---------------------------------------------------------------- bottom controls
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = bottomInset),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!ui.recording) {
                ZoomChips(ui.zoomRatio, ui.minZoom, ui.maxZoom, { controller.setZoom(it) })
                Spacer(Modifier.height(Space.s))
                GlassFilterSelector(
                    presets = FilterRegistry.all,
                    state = fs,
                    thumbnails = thumbs,
                    onSelect = { filterStore.select(it.id) },
                    onAdjust = { showAdjust = true },
                )
                ModeSwitch(ui.mode, enabled = true, onMode = { controller.setMode(it) })
            }
            Spacer(Modifier.height(Space.s))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RecentThumb(ui.lastThumb, onOpenGallery, Modifier.rotate(iconRotation), enabled = !ui.recording)
                ShutterButton(
                    mode = ui.mode,
                    recording = ui.recording,
                    busy = ui.capturing || !ui.ready,
                    onClick = {
                        when {
                            ui.mode == CaptureMode.PHOTO -> controller.takePhoto()
                            ui.recording -> controller.stopRecording()
                            else -> controller.startRecording()
                        }
                    },
                )
                GlassIconButton(
                    icon = AppIcons.FlipCamera,
                    contentDescription = if (ui.frontFacing) "Switch to rear camera" else "Switch to front camera",
                    onClick = { controller.switchCamera() },
                    enabled = !ui.recording && ui.hasFrontCamera,
                    size = 50.dp,
                    iconRotation = iconRotation,
                )
            }
        }

        GlassBottomSheet(visible = showAdjust, onDismiss = { showAdjust = false }) {
            AdjustPanel(
                preset = fs.preset,
                state = fs,
                onIntensity = filterStore::setIntensity,
                onParam = { k, v -> filterStore.setParam(fs.preset.id, k, v) },
                onReset = { filterStore.resetPreset(fs.preset.id); filterStore.setIntensity(settings.defaultIntensity) },
            )
        }

        GlassBottomSheet(visible = showQuick, onDismiss = { showQuick = false }) {
            QuickSettings(ui.supportedResolutions, ui.activeResolution, settings, onUpdateSettings) {
                showQuick = false
                onOpenSettings()
            }
        }
    }
}

private fun buildBrand() = "BODYCAM"

@Composable
private fun RecentThumb(thumb: android.graphics.Bitmap?, onClick: () -> Unit, modifier: Modifier, enabled: Boolean) {
    GlassSurface(
        modifier
            .size(50.dp)
            .clickable(enabled = enabled, onClickLabel = "Open gallery") { onClick() }
            .semantics { contentDescription = "Recent capture. Opens gallery" },
        shape = Radius.Control,
    ) {
        if (thumb != null) {
            val img = remember(thumb) { thumb.asImageBitmap() }
            Image(img, null, Modifier.fillMaxSize().clip(Radius.Control), contentScale = ContentScale.Crop)
        } else {
            androidx.compose.material3.Icon(
                AppIcons.Gallery, null, tint = Palette.TextMuted,
                modifier = Modifier.align(Alignment.Center).size(22.dp),
            )
        }
    }
}

@Composable
private fun FocusRing(point: Pair<Float, Float>?, seq: Int) {
    if (point == null || seq == 0) return
    var visible by remember(seq) { mutableStateOf(true) }
    LaunchedEffect(seq) { delay(1200); visible = false }
    val density = LocalDensity.current
    val size = 72.dp
    val half = with(density) { (size / 2).roundToPx() }
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        val scale by animateFloatAsState(if (visible) 1f else 1.3f, label = "focusScale")
        Box(
            Modifier
                .offset { IntOffset(point.first.roundToInt() - half, point.second.roundToInt() - half) }
                .size(size * scale)
                .border(1.5.dp, Color.White.copy(alpha = 0.9f), Radius.Control),
        )
    }
}

@Composable
private fun PermissionPrompt(onRequest: () -> Unit, modifier: Modifier) {
    GlassSurface(modifier.padding(Space.xl).widthIn(max = 360.dp), shape = Radius.Card, strong = true) {
        Column(Modifier.padding(Space.xl), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Camera access needed", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
            Spacer(Modifier.height(Space.s))
            Text(
                "Bodycam FX processes the live camera feed on your phone's GPU. Nothing leaves the device.",
                style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted,
            )
            Spacer(Modifier.height(Space.l))
            GlassButton("Allow camera", onRequest, selected = true)
        }
    }
}

@Composable
private fun QuickSettings(
    supported: List<CaptureResolution>,
    active: CaptureResolution,
    settings: AppSettings,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onMore: () -> Unit,
) {
    Text("Camera", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
    Spacer(Modifier.height(Space.m))
    Text("RESOLUTION", style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
    Spacer(Modifier.height(Space.s))
    Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        supported.forEach { r ->
            GlassButton(r.label, { onUpdate { it.copy(resolution = r) } }, selected = r == active)
        }
    }
    Spacer(Modifier.height(Space.m))
    QuickToggle("People outline", settings.outline.enabled) { on -> onUpdate { it.copy(outline = it.outline.copy(enabled = on)) } }
    QuickToggle("Recording HUD overlay", settings.hud.enabled) { on -> onUpdate { it.copy(hud = it.hud.copy(enabled = on)) } }
    QuickToggle("Record audio", settings.recordAudio) { on -> onUpdate { it.copy(recordAudio = on) } }
    QuickToggle("Mirror selfies", settings.mirrorSelfies) { on -> onUpdate { it.copy(mirrorSelfies = on) } }
    Spacer(Modifier.height(Space.m))
    GlassButton("All settings", onMore, modifier = Modifier.fillMaxWidth())
}

@Composable
fun QuickToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Palette.Text, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = Color.White,
                uncheckedThumbColor = Palette.TextMuted,
                uncheckedTrackColor = Color(0x33FFFFFF),
                uncheckedBorderColor = Color(0x33FFFFFF),
            ),
        )
    }
}

/** Pinch-zoom + tap/double-tap on one layer. */
private fun Modifier.pointerInputZoomFocus(
    onZoom: (Float) -> Unit,
    onTap: (Float, Float) -> Unit,
    onDoubleTap: () -> Unit,
): Modifier = this
    .then(
        Modifier.pointerInput("zoom") {
            detectTransformGestures { _, _, zoom, _ -> if (zoom != 1f) onZoom(zoom) }
        },
    )
    .then(
        Modifier.pointerInput("tap") {
            detectTapGestures(onTap = { onTap(it.x, it.y) }, onDoubleTap = { onDoubleTap() })
        },
    )
