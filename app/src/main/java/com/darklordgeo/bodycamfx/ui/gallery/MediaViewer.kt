package com.darklordgeo.bodycamfx.ui.gallery

import android.content.Intent
import android.graphics.ImageDecoder
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.darklordgeo.bodycamfx.media.MediaItem
import com.darklordgeo.bodycamfx.ui.components.AppIcons
import com.darklordgeo.bodycamfx.ui.components.GlassIconButton
import com.darklordgeo.bodycamfx.ui.components.GlassSurface
import com.darklordgeo.bodycamfx.ui.theme.MonoStyle
import com.darklordgeo.bodycamfx.ui.theme.Palette
import com.darklordgeo.bodycamfx.ui.theme.Radius
import com.darklordgeo.bodycamfx.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

@Composable
fun MediaViewer(
    items: List<MediaItem>,
    startId: Long,
    startIsVideo: Boolean,
    onClose: () -> Unit,
    onDelete: (MediaItem) -> Unit,
) {
    BackHandler(onBack = onClose)
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val startIndex = items.indexOfFirst { it.id == startId && it.isVideo == startIsVideo }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = startIndex) { items.size }
    var controls by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val current = items.getOrNull(pager.currentPage.coerceIn(0, items.lastIndex))

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !zoomed,
            key = { "${items[it].isVideo}-${items[it].id}" },
        ) { page ->
            val item = items[page]
            val active = page == pager.currentPage
            if (item.isVideo) {
                VideoPage(item, active, controls, onToggleControls = { controls = !controls })
            } else {
                ZoomableImage(item, onZoomChanged = { if (active) zoomed = it }, onTap = { controls = !controls })
            }
        }

        AnimatedVisibility(controls, Modifier.align(Alignment.TopCenter), enter = fadeIn(), exit = fadeOut()) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(Space.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(AppIcons.Back, "Close viewer", onClose)
                Spacer(Modifier.width(Space.m))
                current?.let {
                    GlassSurface(shape = Radius.Pill) {
                        Column(Modifier.padding(horizontal = Space.l, vertical = 6.dp)) {
                            Text(
                                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
                                    .format(Date(it.dateTakenMs)),
                                style = MaterialTheme.typography.bodySmall, color = Palette.Text,
                            )
                            Text(
                                "${pager.currentPage + 1} of ${items.size}",
                                style = MaterialTheme.typography.labelSmall, color = Palette.TextMuted,
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(controls, Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
            Row(
                Modifier.navigationBarsPadding().padding(Space.l),
                horizontalArrangement = Arrangement.spacedBy(Space.xl),
            ) {
                GlassIconButton(AppIcons.Share, "Share", {
                    current?.let { share(context, it) }
                }, size = 52.dp)
                GlassIconButton(AppIcons.Delete, if (current?.isVideo == true) "Delete video" else "Delete photo", {
                    current?.let(onDelete)
                }, size = 52.dp)
            }
        }
    }
}

private fun share(context: android.content.Context, item: MediaItem) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = if (item.isVideo) "video/mp4" else "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, item.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, null))
}

@Composable
private fun ZoomableImage(item: MediaItem, onZoomChanged: (Boolean) -> Unit, onTap: () -> Unit) {
    val context = LocalContext.current
    val image by produceState<Result<ImageBitmap>?>(null, item.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val src = ImageDecoder.createSource(context.contentResolver, item.uri)
                ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                    val longest = maxOf(info.size.width, info.size.height)
                    if (longest > 3000) decoder.setTargetSampleSize(ceil(longest / 3000.0).toInt())
                }.asImageBitmap()
            }
        }
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    fun clamp(o: Offset, s: Float): Offset {
        val maxX = size.width * (s - 1f) / 2f
        val maxY = size.height * (s - 1f) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(item.id) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        // Only take over the gesture when pinching or already zoomed in,
                        // so single-finger swipes still page between items.
                        if (event.changes.size > 1 || scale > 1.01f) {
                            val newScale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                            offset = if (newScale > 1.01f) clamp(offset + event.calculatePan(), newScale) else Offset.Zero
                            scale = newScale
                            onZoomChanged(scale > 1.01f)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(item.id) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1.01f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                        onZoomChanged(scale > 1.01f)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        when (val r = image) {
            null -> CircularProgressIndicator(color = Palette.TextMuted, strokeWidth = 2.dp)
            else -> r.fold(
                onSuccess = { bmp ->
                    Image(
                        bmp, contentDescription = "Photo",
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            scaleX = scale; scaleY = scale
                            translationX = offset.x; translationY = offset.y
                        },
                        contentScale = ContentScale.Fit,
                    )
                },
                onFailure = { Unavailable() },
            )
        }
    }
}

@Composable
private fun Unavailable() {
    Text("This item is no longer available.", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
}

@Composable
private fun VideoPage(item: MediaItem, active: Boolean, controlsVisible: Boolean, onToggleControls: () -> Unit) {
    val context = LocalContext.current
    var playing by remember { mutableStateOf(false) }
    var duration by remember { mutableIntStateOf(0) }
    var position by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    val video = remember(item.uri) {
        VideoView(context).apply {
            setVideoURI(item.uri)
            setOnPreparedListener { mp ->
                duration = mp.duration
                mp.isLooping = false
            }
            setOnCompletionListener { playing = false }
            setOnErrorListener { _, _, _ -> failed = true; true }
        }
    }
    DisposableEffect(video) { onDispose { video.stopPlayback() } }
    LaunchedEffect(active) {
        if (active) { video.start(); playing = true } else { video.pause(); playing = false }
    }
    LaunchedEffect(playing) {
        while (playing) { position = video.currentPosition; delay(200) }
    }

    Box(
        Modifier.fillMaxSize().pointerInput(item.id) { detectTapGestures(onTap = { onToggleControls() }) },
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { ctx ->
                FrameLayout(ctx).apply {
                    (video.parent as? android.view.ViewGroup)?.removeView(video)
                    addView(video, FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER,
                    ))
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (failed) Unavailable()

        AnimatedVisibility(controlsVisible && !failed, enter = fadeIn(), exit = fadeOut()) {
            GlassIconButton(
                if (playing) AppIcons.Pause else AppIcons.Play,
                if (playing) "Pause" else "Play",
                {
                    if (playing) video.pause() else video.start()
                    playing = !playing
                },
                size = 64.dp,
            )
        }
        AnimatedVisibility(
            controlsVisible && !failed && duration > 0,
            Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(), exit = fadeOut(),
        ) {
            GlassSurface(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(start = Space.l, end = Space.l, bottom = 96.dp),
                shape = Radius.Pill,
            ) {
                Row(Modifier.padding(horizontal = Space.l), verticalAlignment = Alignment.CenterVertically) {
                    Text(formatDuration(position.toLong()), style = MonoStyle, color = Palette.Text)
                    Slider(
                        value = position.toFloat().coerceIn(0f, duration.toFloat()),
                        onValueChange = { position = it.toInt(); video.seekTo(it.toInt()) },
                        valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White, activeTrackColor = Color.White,
                            inactiveTrackColor = Color(0x40FFFFFF),
                        ),
                        modifier = Modifier.weight(1f).padding(horizontal = Space.s),
                    )
                    Text(formatDuration(duration.toLong()), style = MonoStyle, color = Palette.TextMuted)
                }
            }
        }
    }
}
