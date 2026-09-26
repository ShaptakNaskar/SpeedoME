package com.sappy.speedome.ui.trips

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.sappy.speedome.data.PointEntity
import com.sappy.speedome.ui.theme.SpeedoColors
import kotlin.math.cos
import kotlin.math.max

/** Blue → green → amber → red, as on the Map theme (docs/plan.md §9). */
private val SPEED_STOPS = listOf(
    0f to Color(0xFF3B82F6),
    0.35f to Color(0xFF22C55E),
    0.65f to Color(0xFFF59E0B),
    1f to Color(0xFFEF4444),
)

fun speedColor(fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    for (i in 1 until SPEED_STOPS.size) {
        val (f1, c1) = SPEED_STOPS[i]
        if (f <= f1) {
            val (f0, c0) = SPEED_STOPS[i - 1]
            return lerp(c0, c1, (f - f0) / (f1 - f0))
        }
    }
    return SPEED_STOPS.last().second
}

private class Projection(lats: List<Double>, lons: List<Double>, w: Float, h: Float, pad: Float) {
    private val lat0 = (lats.min() + lats.max()) / 2
    private val lon0 = (lons.min() + lons.max()) / 2
    private val k = cos(Math.toRadians(lat0))
    private val spanX = max((lons.max() - lons.min()) * k, 1e-6)
    private val spanY = max(lats.max() - lats.min(), 1e-6)
    private val scale = minOf((w - 2 * pad) / spanX, (h - 2 * pad) / spanY)
    private val cx = w / 2
    private val cy = h / 2

    fun at(lat: Double, lon: Double) = Offset((cx + (lon - lon0) * k * scale).toFloat(), (cy - (lat - lat0) * scale).toFloat())
}

/** The recorded route coloured by speed; segments (pauses, resume gaps) are joined with a dashed line. */
@Composable
fun RouteView(points: List<PointEntity>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRoundRect(SpeedoColors.Raised, cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()))
        if (points.size < 2) return@Canvas
        val proj = Projection(points.map { it.lat }, points.map { it.lon }, size.width, size.height, 24.dp.toPx())
        val maxSpeed = maxSpeedOf(points)
        val width = 4.dp.toPx()
        val dash = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx()))
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val pa = proj.at(a.lat, a.lon)
            val pb = proj.at(b.lat, b.lon)
            if (a.segment != b.segment) {
                drawLine(Color.White.copy(alpha = 0.45f), pa, pb, strokeWidth = 2.dp.toPx(), pathEffect = dash)
            } else {
                drawLine(speedColor(b.speedMps / maxSpeed), pa, pb, strokeWidth = width, cap = StrokeCap.Round)
            }
        }
        endpoint(proj.at(points.first().lat, points.first().lon), Color(0xFF3ECF7A))
        endpoint(proj.at(points.last().lat, points.last().lon), Color.White)
    }
}

private fun DrawScope.endpoint(p: Offset, color: Color) {
    drawCircle(Color.Black, radius = 7.dp.toPx(), center = p)
    drawCircle(color, radius = 5.dp.toPx(), center = p)
}

/** List thumbnail from the stored "lat,lon;lat,lon" string. */
@Composable
fun RouteThumbnail(encoded: String?, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRoundRect(SpeedoColors.Raised, cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()))
        val pts = encoded?.split(';')?.mapNotNull { p ->
            val (a, b) = p.split(',').takeIf { it.size == 2 } ?: return@mapNotNull null
            val lat = a.toDoubleOrNull() ?: return@mapNotNull null
            val lon = b.toDoubleOrNull() ?: return@mapNotNull null
            lat to lon
        }.orEmpty()
        if (pts.size < 2) return@Canvas
        val proj = Projection(pts.map { it.first }, pts.map { it.second }, size.width, size.height, 8.dp.toPx())
        val path = Path()
        pts.forEachIndexed { i, (lat, lon) ->
            val o = proj.at(lat, lon)
            if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
        }
        drawPath(path, SpeedoColors.Accent, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** Speed over time; breaks the line across gaps longer than 5 s. */
@Composable
fun SpeedGraph(points: List<PointEntity>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRoundRect(SpeedoColors.Raised, cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()))
        if (points.size < 2) return@Canvas
        val pad = 14.dp.toPx()
        val t0 = points.first().t
        val span = max(points.last().t - t0, 1L).toFloat()
        val vMax = max(points.maxOf { it.speedMps }, 0.5f) * 1.1f
        fun x(t: Long) = pad + (t - t0) / span * (size.width - 2 * pad)
        fun y(v: Float) = size.height - pad - v / vMax * (size.height - 2 * pad)
        val line = Path()
        val area = Path()
        var open = false
        var startX = 0f
        var lastX = 0f
        for (i in points.indices) {
            val p = points[i]
            val gap = i > 0 && p.t - points[i - 1].t > 5_000
            if (gap && open) {
                area.lineTo(lastX, size.height - pad)
                area.lineTo(startX, size.height - pad)
                area.close()
                open = false
            }
            val px = x(p.t)
            val py = y(p.speedMps)
            if (!open) {
                line.moveTo(px, py)
                area.moveTo(px, size.height - pad)
                area.lineTo(px, py)
                startX = px
                open = true
            } else {
                line.lineTo(px, py)
                area.lineTo(px, py)
            }
            lastX = px
        }
        if (open) {
            area.lineTo(lastX, size.height - pad)
            area.lineTo(startX, size.height - pad)
            area.close()
        }
        drawPath(area, SpeedoColors.Accent.copy(alpha = 0.18f))
        drawPath(line, SpeedoColors.Accent, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}
