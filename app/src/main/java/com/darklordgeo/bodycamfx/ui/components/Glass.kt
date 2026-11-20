package com.darklordgeo.bodycamfx.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.darklordgeo.bodycamfx.ui.theme.Glass
import com.darklordgeo.bodycamfx.ui.theme.Palette
import com.darklordgeo.bodycamfx.ui.theme.Radius
import com.darklordgeo.bodycamfx.ui.theme.Space
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/** Shared backdrop for real blur. Null (or [LocalGlassBlur] false) = translucent fallback. */
val LocalHaze = staticCompositionLocalOf<HazeState?> { null }
val LocalGlassBlur = staticCompositionLocalOf { true }
val LocalHapticsEnabled = staticCompositionLocalOf { true }

/** Marks content that glass surfaces should blur (the camera preview, gallery grid, ...). */
@Composable
fun Modifier.glassBackdrop(): Modifier {
    val haze = LocalHaze.current
    return if (haze != null) this.hazeSource(haze) else this
}

@Composable
fun rememberHaptic(): () -> Unit {
    val h = LocalHapticFeedback.current
    val enabled = LocalHapticsEnabled.current
    return remember(h, enabled) { { if (enabled) h.performHapticFeedback(HapticFeedbackType.TextHandleMove) } }
}

/**
 * The one glass primitive: backdrop blur where available (Android 12+, when enabled), always with a
 * translucent dark fill so text stays readable over bright footage, a soft top highlight and a
 * hairline border.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = Radius.Panel,
    strong: Boolean = false,
    /** Must be false for surfaces that sit inside a blur source (e.g. badges on grid items). */
    allowBlur: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val haze = LocalHaze.current
    val blur = allowBlur && LocalGlassBlur.current && haze != null
    val fill = if (strong) Glass.FillStrong else Glass.Fill
    val base = modifier.clip(shape)
    val withBlur = if (blur) {
        base.hazeEffect(
            state = haze!!,
            style = HazeStyle(
                backgroundColor = Palette.Background,
                tint = HazeTint(fill),
                blurRadius = Glass.BlurRadius,
                noiseFactor = 0f,
            ),
        )
    } else {
        base.background(fill)
    }
    Box(
        modifier = withBlur
            .background(Brush.verticalGradient(listOf(Glass.Highlight, Color.Transparent)))
            .border(Glass.BorderWidth, Glass.Border, shape),
        content = content,
    )
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    GlassSurface(modifier, Radius.Card, strong = true, allowBlur = false) {
        Column(Modifier.padding(Space.l), content = content)
    }
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    enabled: Boolean = true,
    active: Boolean = false,
    iconRotation: Float = 0f,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "press")
    val haptic = rememberHaptic()
    GlassSurface(
        modifier = modifier
            .size(maxOf(size, 40.dp))
            .scale(scale)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = contentDescription,
            ) { haptic(); onClick() },
        shape = Radius.Pill,
    ) {
        if (active) Box(Modifier.fillMaxSize().background(Glass.Pressed))
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) Palette.Text else Palette.TextFaint,
            modifier = Modifier
                .align(Alignment.Center)
                .size(size * 0.46f)
                .rotate(iconRotation),
        )
    }
}

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptic = rememberHaptic()
    GlassSurface(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clickable(interaction, null, enabled = enabled, role = Role.Button) { haptic(); onClick() },
        shape = Radius.Pill,
    ) {
        if (selected || pressed) {
            Box(Modifier.matchParentSize().background(if (selected) Palette.Text else Glass.Pressed))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = when {
                selected -> Color.Black
                danger -> Palette.Danger
                !enabled -> Palette.TextFaint
                else -> Palette.Text
            },
            modifier = Modifier.align(Alignment.Center).padding(horizontal = Space.l, vertical = 10.dp),
        )
    }
}

data class BarItem(val label: String, val icon: ImageVector)

@Composable
fun GlassBottomBar(items: List<BarItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val haptic = rememberHaptic()
    GlassSurface(modifier.widthIn(max = 420.dp), shape = Radius.Pill) {
        Row(
            Modifier.padding(horizontal = Space.s, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { i, item ->
                val isSel = i == selected
                Box(
                    Modifier
                        .clip(Radius.Pill)
                        .background(if (isSel) Glass.Pressed else Color.Transparent)
                        .clickable(role = Role.Tab, onClickLabel = item.label) { haptic(); onSelect(i) }
                        .heightIn(min = Space.TouchTarget)
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(item.icon, item.label, tint = if (isSel) Palette.Text else Palette.TextMuted, modifier = Modifier.size(20.dp))
                        if (isSel) {
                            Spacer(Modifier.width(6.dp))
                            Text(item.label, style = MaterialTheme.typography.labelMedium, color = Palette.Text)
                        }
                    }
                }
            }
        }
    }
}

/** Bottom sheet with scrim; content scrolls inside a capped height. */
@Composable
fun GlassBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Glass.Scrim.copy(alpha = 0.35f))
                    .clickable(remember { MutableInteractionSource() }, null, onClickLabel = "Close panel") { onDismiss() },
            )
        }
        AnimatedVisibility(
            visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            GlassSurface(
                modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .padding(horizontal = Space.s)
                    .navigationBarsPadding()
                    .padding(bottom = Space.s),
                shape = RoundedCornerShape(28.dp),
                strong = true,
            ) {
                Column(Modifier.padding(Space.l)) {
                    Box(
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(width = 36.dp, height = 4.dp)
                            .clip(Radius.Pill)
                            .background(Palette.TextFaint),
                    )
                    Spacer(Modifier.height(Space.m))
                    content()
                }
            }
        }
    }
}

/** Confirmation dialog. Dialog windows can't see the backdrop, so this always uses the strong fill. */
@Composable
fun GlassDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = false,
) {
    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalHaze provides null) {
            GlassSurface(Modifier.widthIn(max = 360.dp), shape = Radius.Card, strong = true) {
                Column(Modifier.padding(Space.xl), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.Text, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(Space.s))
                    Text(message, style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(Space.xl))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                        GlassButton("Cancel", onDismiss)
                        GlassButton(confirmLabel, onConfirm, danger = danger)
                    }
                }
            }
        }
    }
}

/** Small transient message pill. */
@Composable
fun GlassToast(text: String?, modifier: Modifier = Modifier) {
    AnimatedVisibility(text != null, modifier, enter = fadeIn(), exit = fadeOut()) {
        GlassSurface(shape = Radius.Pill, strong = true) {
            Text(
                text ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Text,
                modifier = Modifier.padding(horizontal = Space.l, vertical = 10.dp),
            )
        }
    }
}
