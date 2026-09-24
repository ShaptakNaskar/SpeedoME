package com.sappy.speedome.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Hand-drawn 24dp tab icons, so the app needs no icon library. Icon() tints them. */
object TabIcons {
    val Speed: ImageVector = icon("speed") {
        stroke { moveTo(6.34f, 18.66f); arcTo(8f, 8f, 0f, true, true, 17.66f, 18.66f) }
        stroke { moveTo(12f, 13f); lineTo(15.5f, 8.5f) }
    }

    val Trips: ImageVector = icon("trips") {
        stroke { moveTo(6f, 18f); curveTo(6f, 12f, 12f, 15f, 12f, 11f); curveTo(12f, 7f, 18f, 9f, 18f, 6f) }
        fill { dot(6f, 18f, 2.2f) }
        fill { dot(18f, 6f, 2.2f) }
    }

    val Settings: ImageVector = icon("settings") {
        stroke { moveTo(4f, 7f); lineTo(20f, 7f); moveTo(4f, 12f); lineTo(20f, 12f); moveTo(4f, 17f); lineTo(20f, 17f) }
        fill { dot(9f, 7f, 2.4f); dot(15f, 12f, 2.4f); dot(7.5f, 17f, 2.4f) }
    }

    val Target: ImageVector = icon("target") {
        stroke { dot(12f, 12f, 8.5f) }
        stroke { dot(12f, 12f, 4.5f) }
        fill { dot(12f, 12f, 1.6f) }
    }

    val Car: ImageVector = icon("car") {
        stroke { moveTo(4f, 16f); lineTo(4f, 12.5f); lineTo(6.5f, 7.5f); lineTo(17.5f, 7.5f); lineTo(20f, 12.5f); lineTo(20f, 16f); close() }
        stroke { moveTo(5f, 12.5f); lineTo(19f, 12.5f) }
        fill { dot(7.5f, 17.5f, 2f); dot(16.5f, 17.5f, 2f) }
    }

    val Walk: ImageVector = icon("walk") {
        fill { dot(13.5f, 4.5f, 2f) }
        stroke { moveTo(12.5f, 8.5f); lineTo(10.5f, 14f); lineTo(13.5f, 17f); lineTo(13f, 21f) }
        stroke { moveTo(10.5f, 14f); lineTo(8f, 21f) }
        stroke { moveTo(7.5f, 12f); lineTo(11.5f, 9f); lineTo(15.5f, 12.5f) }
    }

    private class Scope(val builder: ImageVector.Builder) {
        fun stroke(block: PathBuilder.() -> Unit) {
            builder.path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, pathBuilder = block)
        }

        fun fill(block: PathBuilder.() -> Unit) {
            builder.path(fill = SolidColor(Color.Black), pathBuilder = block)
        }
    }

    private fun PathBuilder.dot(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, false, true, cx + r, cy)
        arcTo(r, r, 0f, false, true, cx - r, cy)
        close()
    }

    private fun icon(name: String, block: Scope.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).also { Scope(it).block() }.build()
}
