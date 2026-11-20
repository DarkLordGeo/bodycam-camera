package com.darklordgeo.bodycamfx.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Design tokens. Every colour, radius, blur and spacing value in the UI comes from here. */
object Palette {
    val Background = Color(0xFF070808)
    val Surface = Color(0xFF111315)
    val SurfaceRaised = Color(0xFF181B1E)
    val Text = Color(0xFFF2F1EE)
    val TextMuted = Color(0xA6F2F1EE)
    val TextFaint = Color(0x66F2F1EE)
    val Record = Color(0xFFE5372B)
    val Accent = Color(0xFFF2F1EE)
    val Danger = Color(0xFFFF6B5E)
}

object Glass {
    /** Fill over live camera footage; dark enough for white text on a sunlit scene. */
    val Fill = Color(0x80101214)
    val FillStrong = Color(0xD9121416)
    val Border = Color(0x33FFFFFF)
    val Highlight = Color(0x14FFFFFF)
    val Pressed = Color(0x29FFFFFF)
    val Scrim = Color(0x99000000)
    val BlurRadius = 22.dp
    val BorderWidth = 0.75.dp
}

object Radius {
    val Panel = RoundedCornerShape(24.dp)
    val Card = RoundedCornerShape(20.dp)
    val Control = RoundedCornerShape(16.dp)
    val Thumb = RoundedCornerShape(10.dp)
    val Pill = CircleShape
}

object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val TouchTarget = 48.dp
}

private val AppTypography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.15.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp),
    labelMedium = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp),
    labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp),
)

val MonoStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

@Composable
fun BodycamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Accent,
            onPrimary = Color.Black,
            secondary = Palette.TextMuted,
            background = Palette.Background,
            onBackground = Palette.Text,
            surface = Palette.Surface,
            onSurface = Palette.Text,
            surfaceVariant = Palette.SurfaceRaised,
            onSurfaceVariant = Palette.TextMuted,
            error = Palette.Danger,
        ),
        typography = AppTypography,
        content = content,
    )
}
