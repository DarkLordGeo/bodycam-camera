package com.darklordgeo.bodycamfx.ui.camera

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darklordgeo.bodycamfx.camera.CaptureMode
import com.darklordgeo.bodycamfx.filters.FilterPreset
import com.darklordgeo.bodycamfx.filters.FilterState
import com.darklordgeo.bodycamfx.ui.components.GlassButton
import com.darklordgeo.bodycamfx.ui.components.GlassSurface
import com.darklordgeo.bodycamfx.ui.components.rememberHaptic
import com.darklordgeo.bodycamfx.ui.theme.Glass
import com.darklordgeo.bodycamfx.ui.theme.MonoStyle
import com.darklordgeo.bodycamfx.ui.theme.Palette
import com.darklordgeo.bodycamfx.ui.theme.Radius
import com.darklordgeo.bodycamfx.ui.theme.Space
import java.util.Locale

@Composable
fun ShutterButton(mode: CaptureMode, recording: Boolean, busy: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, label = "shutterScale")
    val inner by animateDpAsState(if (recording) 28.dp else 62.dp, label = "inner")
    val corner by animateDpAsState(if (recording) 8.dp else 31.dp, label = "corner")
    val color by animateColorAsState(if (mode == CaptureMode.VIDEO) Palette.Record else Color.White, label = "color")
    val haptic = rememberHaptic()
    val label = when {
        recording -> "Stop recording"
        mode == CaptureMode.VIDEO -> "Start recording"
        else -> "Take photo"
    }
    Box(
        modifier
            .size(78.dp)
            .scale(scale)
            .clip(CircleShape)
            .clickable(interaction, null, enabled = !busy, role = Role.Button, onClickLabel = label) { haptic(); onClick() }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxSize().border(4.dp, Color.White.copy(alpha = if (busy) 0.5f else 0.95f), CircleShape))
        Box(
            Modifier
                .size(inner)
                .clip(RoundedCornerShape(corner))
                .background(color.copy(alpha = if (busy) 0.6f else 1f)),
        )
    }
}

@Composable
fun RecordingBadge(ms: Long, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "rec")
    val a by t.animateFloat(1f, 0.25f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "blink")
    val s = ms / 1000
    GlassSurface(modifier, shape = Radius.Pill) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).alpha(a).clip(CircleShape).background(Palette.Record))
            Spacer(Modifier.width(8.dp))
            Text(
                String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60),
                style = MonoStyle, color = Palette.Text,
                modifier = Modifier.semantics { contentDescription = "Recording, $s seconds" },
            )
        }
    }
}

@Composable
fun ModeSwitch(mode: CaptureMode, enabled: Boolean, onMode: (CaptureMode) -> Unit, modifier: Modifier = Modifier) {
    val haptic = rememberHaptic()
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalAlignment = Alignment.CenterVertically) {
        CaptureMode.entries.forEach { m ->
            val sel = m == mode
            Box(
                Modifier
                    .clip(Radius.Pill)
                    .background(if (sel) Glass.Fill else Color.Transparent)
                    .clickable(enabled = enabled, role = Role.Tab, onClickLabel = m.name) { haptic(); onMode(m) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .semantics { selected = sel },
            ) {
                Text(
                    m.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (sel) Palette.Text else Palette.TextMuted,
                )
            }
        }
    }
}

@Composable
fun ZoomChips(zoom: Float, min: Float, max: Float, onZoom: (Float) -> Unit, modifier: Modifier = Modifier) {
    val stops = buildList {
        if (min < 0.95f) add(min)
        add(1f)
        if (max >= 2f) add(2f)
        if (max >= 5f) add(5f)
    }
    if (stops.size < 2) return
    GlassSurface(modifier, shape = Radius.Pill) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            stops.forEach { s ->
                val active = kotlin.math.abs(zoom - s) < 0.05f ||
                    (s == stops.lastOrNull { it <= zoom + 0.01f } && zoom > s + 0.05f)
                val label = if (active && kotlin.math.abs(zoom - s) >= 0.05f) fmtZoom(zoom) else fmtZoom(s)
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (active) Glass.Pressed else Color.Transparent)
                        .clickable(role = Role.Button, onClickLabel = "Zoom ${fmtZoom(s)}") { onZoom(s) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (active) Palette.Text else Palette.TextMuted)
                }
            }
        }
    }
}

private fun fmtZoom(z: Float): String =
    if (z < 1f || z % 1f > 0.05f) String.format(Locale.US, "%.1fx", z) else "${z.toInt()}x"

/**
 * Instagram-style carousel. Thumbnails are real processed previews of the live camera frame
 * (rendered by the GL pipeline), falling back to a gradient until the first preview arrives.
 */
@Composable
fun GlassFilterSelector(
    presets: List<FilterPreset>,
    state: FilterState,
    thumbnails: Map<String, ImageBitmap>,
    onSelect: (FilterPreset) -> Unit,
    onAdjust: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val selectedIndex = presets.indexOfFirst { it.id == state.selectedId }.coerceAtLeast(0)
    val screenW = LocalConfiguration.current.screenWidthDp.dp
    val itemW = 72.dp
    LaunchedEffect(selectedIndex) { listState.animateScrollToItem(selectedIndex) }
    val haptic = rememberHaptic()
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(horizontal = (screenW - itemW) / 2),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        itemsIndexed(presets, key = { _, p -> p.id }) { _, p ->
            val selected = p.id == state.selectedId
            val ring by animateDpAsState(if (selected) 2.5.dp else 0.dp, label = "ring")
            Column(
                Modifier
                    .width(itemW)
                    .clip(Radius.Control)
                    .clickable(
                        enabled = p.available,
                        role = Role.Button,
                        onClickLabel = if (selected) "Adjust ${p.name}" else "Use ${p.name}",
                    ) {
                        haptic()
                        if (selected) onAdjust() else onSelect(p)
                    }
                    .semantics {
                        this.selected = selected
                        stateDescription = if (p.available) p.description else "Coming soon"
                    }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .border(ring, Color.White, CircleShape)
                        .padding(if (selected) 4.dp else 0.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(listOf(Color(p.thumbnail.topColor), Color(p.thumbnail.bottomColor))),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    val thumb = thumbnails[p.id]
                    if (thumb != null && p.available) {
                        Image(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Text(p.thumbnail.glyph, style = MaterialTheme.typography.labelLarge, color = Palette.Text.copy(alpha = 0.85f))
                    }
                    if (!p.available) {
                        Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                            Text("SOON", fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Palette.TextMuted)
                        }
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    p.name,
                    fontSize = 10.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    letterSpacing = 0.6.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = when {
                        selected -> Palette.Text
                        p.available -> Palette.TextMuted
                        else -> Palette.TextFaint
                    },
                )
            }
        }
    }
}

@Composable
fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    valueText: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = Palette.Text, modifier = Modifier.weight(1f))
            Text(valueText, style = MonoStyle.copy(fontSize = 11.sp), color = Palette.TextMuted)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color(0x40FFFFFF),
            ),
            modifier = Modifier.height(32.dp).semantics { contentDescription = label },
        )
    }
}

/** Contents of the adjustment sheet: intensity + every parameter of the active preset, grouped. */
@Composable
fun AdjustPanel(
    preset: FilterPreset,
    state: FilterState,
    onIntensity: (Float) -> Unit,
    onParam: (String, Float) -> Unit,
    onReset: () -> Unit,
) {
    val values = state.valuesFor(preset)
    val maxH = (LocalConfiguration.current.screenHeightDp * 0.5f).dp
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(preset.name, style = MaterialTheme.typography.titleMedium, color = Palette.Text)
            Text(preset.description, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted, maxLines = 2)
        }
        GlassButton("Reset", onReset)
    }
    Spacer(Modifier.height(Space.m))
    LabeledSlider("Intensity", state.intensity, 0f..1f, onIntensity, "${(state.intensity * 100).toInt()}%")
    Column(
        Modifier
            .fillMaxWidth()
            .height(maxH.coerceAtMost(420.dp))
            .verticalScroll(rememberScrollState()),
    ) {
        preset.params.groupBy { it.group }.forEach { (group, params) ->
            Spacer(Modifier.height(Space.m))
            Text(group.uppercase(Locale.US), style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
            params.forEach { p ->
                val v = values[p.key] ?: p.default
                LabeledSlider(p.label, v, p.min..p.max, { onParam(p.key, it) }, String.format(Locale.US, "%.2f", v))
            }
        }
        Spacer(Modifier.height(Space.s))
    }
}
