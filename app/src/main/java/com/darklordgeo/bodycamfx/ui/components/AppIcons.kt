package com.darklordgeo.bodycamfx.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Small hand-drawn icon set (keeps the APK free of the full Material icon library). */
object AppIcons {
    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    private fun ImageVector.Builder.stroke(width: Float = 1.8f, pathBuilder: PathBuilder.() -> Unit) =
        path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = pathBuilder,
        )

    private fun ImageVector.Builder.fill(pathBuilder: PathBuilder.() -> Unit) =
        path(fill = SolidColor(Color.White), pathBuilder = pathBuilder)

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcToRelative(r, r, 0f, true, true, r * 2, 0f)
        arcToRelative(r, r, 0f, true, true, -r * 2, 0f)
        close()
    }

    private fun PathBuilder.roundRect(l: Float, t: Float, r: Float, b: Float, rad: Float) {
        moveTo(l + rad, t)
        lineTo(r - rad, t)
        arcTo(rad, rad, 0f, false, true, r, t + rad)
        lineTo(r, b - rad)
        arcTo(rad, rad, 0f, false, true, r - rad, b)
        lineTo(l + rad, b)
        arcTo(rad, rad, 0f, false, true, l, b - rad)
        lineTo(l, t + rad)
        arcTo(rad, rad, 0f, false, true, l + rad, t)
        close()
    }

    val FlashOn: ImageVector = icon("FlashOn") {
        fill {
            moveTo(13.5f, 2f); lineTo(5f, 13.5f); lineTo(11f, 13.5f); lineTo(10f, 22f)
            lineTo(19f, 10f); lineTo(13f, 10f); close()
        }
    }

    val FlashOff: ImageVector = icon("FlashOff") {
        stroke {
            moveTo(13.5f, 2.5f); lineTo(5.5f, 13.5f); lineTo(11f, 13.5f); lineTo(10f, 21.5f)
            lineTo(18.5f, 10.5f); lineTo(13f, 10.5f); close()
        }
        stroke(2f) { moveTo(4f, 4f); lineTo(20f, 20f) }
    }

    val FlipCamera: ImageVector = icon("FlipCamera") {
        stroke {
            moveTo(4.5f, 11f); arcTo(7.5f, 7.5f, 0f, false, true, 18.6f, 7.6f)
            moveTo(19f, 3.8f); lineTo(19f, 8f); lineTo(14.8f, 8f)
            moveTo(19.5f, 13f); arcTo(7.5f, 7.5f, 0f, false, true, 5.4f, 16.4f)
            moveTo(5f, 20.2f); lineTo(5f, 16f); lineTo(9.2f, 16f)
        }
    }

    val Tune: ImageVector = icon("Tune") {
        stroke { moveTo(4f, 7f); lineTo(20f, 7f); moveTo(4f, 17f); lineTo(20f, 17f) }
        fill { circle(9f, 7f, 2.6f) }
        fill { circle(15f, 17f, 2.6f) }
    }

    val Camera: ImageVector = icon("Camera") {
        stroke { roundRect(3f, 6.5f, 21f, 19f, 3f) }
        stroke { moveTo(8.5f, 6.5f); lineTo(10f, 4f); lineTo(14f, 4f); lineTo(15.5f, 6.5f) }
        stroke { circle(12f, 12.8f, 3.6f) }
    }

    val Gallery: ImageVector = icon("Gallery") {
        stroke { roundRect(3.5f, 3.5f, 10.5f, 10.5f, 2f) }
        stroke { roundRect(13.5f, 3.5f, 20.5f, 10.5f, 2f) }
        stroke { roundRect(3.5f, 13.5f, 10.5f, 20.5f, 2f) }
        stroke { roundRect(13.5f, 13.5f, 20.5f, 20.5f, 2f) }
    }

    val Settings: ImageVector = icon("Settings") {
        stroke { circle(12f, 12f, 3f) }
        stroke {
            // eight short spokes around a ring
            val pts = listOf(
                12f to 2.5f, 12f to 5.5f, 12f to 18.5f, 12f to 21.5f, 2.5f to 12f, 5.5f to 12f,
                18.5f to 12f, 21.5f to 12f, 5.3f to 5.3f, 7.4f to 7.4f, 16.6f to 16.6f, 18.7f to 18.7f,
                18.7f to 5.3f, 16.6f to 7.4f, 7.4f to 16.6f, 5.3f to 18.7f,
            )
            for (i in pts.indices step 2) { moveTo(pts[i].first, pts[i].second); lineTo(pts[i + 1].first, pts[i + 1].second) }
        }
        stroke { circle(12f, 12f, 6.5f) }
    }

    val Play: ImageVector = icon("Play") {
        fill { moveTo(8f, 5f); lineTo(19f, 12f); lineTo(8f, 19f); close() }
    }

    val Pause: ImageVector = icon("Pause") {
        fill { roundRect(6.5f, 5f, 10f, 19f, 1f) }
        fill { roundRect(14f, 5f, 17.5f, 19f, 1f) }
    }

    val Close: ImageVector = icon("Close") {
        stroke(2f) { moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f) }
    }

    val Delete: ImageVector = icon("Delete") {
        stroke { moveTo(4.5f, 6.5f); lineTo(19.5f, 6.5f) }
        stroke { moveTo(9.5f, 6.5f); lineTo(10f, 4f); lineTo(14f, 4f); lineTo(14.5f, 6.5f) }
        stroke { moveTo(6.5f, 6.5f); lineTo(7.5f, 20f); lineTo(16.5f, 20f); lineTo(17.5f, 6.5f) }
        stroke { moveTo(10.2f, 10f); lineTo(10.4f, 16.5f); moveTo(13.8f, 10f); lineTo(13.6f, 16.5f) }
    }

    val Share: ImageVector = icon("Share") {
        stroke { moveTo(12f, 3.5f); lineTo(12f, 15f); moveTo(7.5f, 8f); lineTo(12f, 3.5f); lineTo(16.5f, 8f) }
        stroke { moveTo(5f, 12.5f); lineTo(5f, 19f); lineTo(19f, 19f); lineTo(19f, 12.5f) }
    }

    val Back: ImageVector = icon("Back") {
        stroke(2f) { moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f) }
    }

    /** Person with an outline ring: People Outline mode. */
    val PersonOutline: ImageVector = icon("PersonOutline") {
        stroke { circle(12f, 8.2f, 3.4f) }
        stroke { moveTo(5.5f, 20f); arcTo(6.5f, 6.5f, 0f, false, true, 18.5f, 20f) }
        stroke(1.2f) { moveTo(2.5f, 6f); lineTo(2.5f, 2.5f); lineTo(6f, 2.5f); moveTo(18f, 2.5f); lineTo(21.5f, 2.5f); lineTo(21.5f, 6f) }
    }

    val Video: ImageVector = icon("Video") {
        stroke { roundRect(3f, 6.5f, 15.5f, 17.5f, 2.5f) }
        stroke { moveTo(15.5f, 10.5f); lineTo(21f, 7.5f); lineTo(21f, 16.5f); lineTo(15.5f, 13.5f) }
    }
}
