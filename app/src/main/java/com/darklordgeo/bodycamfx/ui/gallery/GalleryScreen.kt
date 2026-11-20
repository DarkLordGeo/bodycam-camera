package com.darklordgeo.bodycamfx.ui.gallery

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darklordgeo.bodycamfx.media.MediaItem
import com.darklordgeo.bodycamfx.media.MediaRepository
import com.darklordgeo.bodycamfx.ui.components.AppIcons
import com.darklordgeo.bodycamfx.ui.components.GlassButton
import com.darklordgeo.bodycamfx.ui.components.GlassSurface
import com.darklordgeo.bodycamfx.ui.components.glassBackdrop
import com.darklordgeo.bodycamfx.ui.theme.MonoStyle
import com.darklordgeo.bodycamfx.ui.theme.Palette
import com.darklordgeo.bodycamfx.ui.theme.Radius
import com.darklordgeo.bodycamfx.ui.theme.Space
import java.text.DateFormat
import java.util.Date
import java.util.Locale

enum class GalleryFilter(val label: String) { ALL("All"), PHOTOS("Photos"), VIDEOS("Videos") }

fun List<MediaItem>.filtered(f: GalleryFilter) = when (f) {
    GalleryFilter.ALL -> this
    GalleryFilter.PHOTOS -> filter { !it.isVideo }
    GalleryFilter.VIDEOS -> filter { it.isVideo }
}

@Composable
fun GalleryScreen(
    items: List<MediaItem>,
    filter: GalleryFilter,
    onFilter: (GalleryFilter) -> Unit,
    repository: MediaRepository,
    onOpen: (MediaItem) -> Unit,
    onOpenCamera: () -> Unit,
    bottomInset: Dp,
) {
    val shown = items.filtered(filter)
    val topPad = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 96.dp
    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        if (shown.isEmpty()) {
            EmptyGallery(filter, onOpenCamera, Modifier.align(Alignment.Center))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 108.dp),
                modifier = Modifier.fillMaxSize().glassBackdrop(),
                contentPadding = PaddingValues(start = 3.dp, end = 3.dp, top = topPad, bottom = bottomInset + Space.l),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                itemsIndexed(shown, key = { _, it -> "${it.isVideo}-${it.id}" }) { _, item ->
                    GridThumb(item, repository) { onOpen(item) }
                }
            }
        }

        GlassSurface(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = Space.s, vertical = Space.s),
            shape = Radius.Card,
        ) {
            Column(Modifier.padding(horizontal = Space.l, vertical = Space.m)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Gallery", style = MaterialTheme.typography.titleLarge, color = Palette.Text, modifier = Modifier.weight(1f))
                    Text(
                        "${items.count { !it.isVideo }} photos · ${items.count { it.isVideo }} videos",
                        style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted,
                    )
                }
                Spacer(Modifier.height(Space.s))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    GalleryFilter.entries.forEach { f ->
                        GlassButton(f.label, { onFilter(f) }, selected = f == filter)
                    }
                }
            }
        }
    }
}

@Composable
private fun GridThumb(item: MediaItem, repository: MediaRepository, onClick: () -> Unit) {
    val px = with(LocalDensity.current) { 160.dp.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(null, item.id, item.isVideo) {
        value = repository.thumbnail(item, px)?.asImageBitmap()
    }
    val date = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(Date(item.dateTakenMs))
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(Radius.Thumb)
            .background(Palette.SurfaceRaised)
            .clickable(onClickLabel = "Open") { onClick() }
            .semantics { contentDescription = "${if (item.isVideo) "Video" else "Photo"}, $date" },
    ) {
        bitmap?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        if (item.isVideo) {
            GlassSurface(Modifier.align(Alignment.BottomEnd).padding(5.dp), shape = Radius.Pill, strong = true, allowBlur = false) {
                Row(Modifier.padding(horizontal = 7.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.Play, null, tint = Palette.Text, modifier = Modifier.size(10.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(formatDuration(item.durationMs), style = MonoStyle.copy(fontSize = 10.sp), color = Palette.Text)
                }
            }
        }
    }
}

fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
    else String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}

@Composable
private fun EmptyGallery(filter: GalleryFilter, onOpenCamera: () -> Unit, modifier: Modifier) {
    Column(modifier.padding(Space.xl), horizontalAlignment = Alignment.CenterHorizontally) {
        GlassSurface(Modifier.size(84.dp), shape = Radius.Pill, strong = true) {
            Icon(
                if (filter == GalleryFilter.VIDEOS) AppIcons.Video else AppIcons.Camera, null,
                tint = Palette.TextMuted, modifier = Modifier.align(Alignment.Center).size(34.dp),
            )
        }
        Spacer(Modifier.height(Space.l))
        Text(
            when (filter) {
                GalleryFilter.ALL -> "No captures yet"
                GalleryFilter.PHOTOS -> "No photos yet"
                GalleryFilter.VIDEOS -> "No videos yet"
            },
            style = MaterialTheme.typography.titleMedium, color = Palette.Text,
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            "Photos and videos you take appear here and in your phone's gallery.",
            style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted,
        )
        Spacer(Modifier.height(Space.l))
        GlassButton("Open camera", onOpenCamera, selected = true)
    }
}
