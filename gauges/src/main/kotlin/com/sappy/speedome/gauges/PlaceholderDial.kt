package com.sappy.speedome.gauges

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** Stand-in dial for the skeleton build; the real gauge toolkit lands in M5 (docs/plan.md §9). */
@Composable
fun PlaceholderDial(
    modifier: Modifier = Modifier,
    accent: Color = Color(0xFFE8A33D),
) {
    Canvas(modifier.aspectRatio(1f)) {
        val r = size.minDimension * 0.4f
        val c = center
        drawArc(
            color = Color(0xFF16181A),
            startAngle = 135f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(c.x - r, c.y - r),
            size = Size(2 * r, 2 * r),
            style = Stroke(width = r * 0.07f, cap = StrokeCap.Round),
        )
        for (i in 0..10) {
            val a = Math.toRadians(135.0 + 27.0 * i)
            val dx = cos(a).toFloat()
            val dy = sin(a).toFloat()
            drawLine(
                color = Color.White.copy(alpha = 0.35f),
                start = Offset(c.x + dx * r * 1.14f, c.y + dy * r * 1.14f),
                end = Offset(c.x + dx * r * 1.22f, c.y + dy * r * 1.22f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        val a0 = Math.toRadians(135.0)
        drawLine(
            color = accent,
            start = c,
            end = Offset(c.x + cos(a0).toFloat() * r * 0.85f, c.y + sin(a0).toFloat() * r * 0.85f),
            strokeWidth = r * 0.035f,
            cap = StrokeCap.Round,
        )
        drawCircle(color = Color(0xFF0D0E0F), radius = r * 0.09f, center = c)
        drawCircle(color = accent.copy(alpha = 0.6f), radius = r * 0.09f, center = c, style = Stroke(1.5.dp.toPx()))
    }
}

@Preview
@Composable
private fun PlaceholderDialPreview() {
    PlaceholderDial(Modifier.size(240.dp).background(Color.Black))
}
