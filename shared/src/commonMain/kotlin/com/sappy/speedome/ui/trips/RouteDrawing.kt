package com.sappy.speedome.ui.trips

import com.sappy.speedome.ui.Fmt
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.runtime.remember
import com.sappy.speedome.engine.RoutePoint
import com.sappy.speedome.engine.routeRuns
import com.sappy.speedome.engine.speedProfile
import com.sappy.speedome.ui.COLOR_BUCKETS
import kotlin.math.pow
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
    private val k = cos(lat0 * kotlin.math.PI / 180)
    private val spanX = max((lons.max() - lons.min()) * k, 1e-6)
    private val spanY = max(lats.max() - lats.min(), 1e-6)
    private val scale = minOf((w - 2 * pad) / spanX, (h - 2 * pad) / spanY)
    val metresPerPx get() = 111_195.0 / scale
    private val cx = w / 2
    private val cy = h / 2

    fun at(lat: Double, lon: Double) = Offset((cx + (lon - lon0) * k * scale).toFloat(), (cy - (lat - lat0) * scale).toFloat())
}

/** The recorded route coloured by speed; segments (pauses, resume gaps) are joined with a dashed line. */
@Composable
fun RouteView(points: List<PointEntity>, modifier: Modifier = Modifier) {
    val route = remember(points) { points.map { RoutePoint(it.lat, it.lon, it.speedMps, it.segment) } }
    Canvas(modifier) {
        drawRoundRect(SpeedoColors.Raised, cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()))
        if (points.size < 2) return@Canvas
        val proj = Projection(points.map { it.lat }, points.map { it.lon }, size.width, size.height, 24.dp.toPx())
        val dash = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx()))
        val stroke = Stroke(4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        // Colour changes at most every 2 px and vertices every 1.5 px: a long trip stays a few hundred paths.
        for (r in routeRuns(route, maxSpeedOf(points), COLOR_BUCKETS, 2 * proj.metresPerPx)) {
            val a = proj.at(route[r.from].lat, route[r.from].lon)
            if (r.gap) {
                drawLine(Color.White.copy(alpha = 0.45f), a, proj.at(route[r.to].lat, route[r.to].lon), strokeWidth = 2.dp.toPx(), pathEffect = dash)
                continue
            }
            var last = a
            val path = Path().apply { moveTo(a.x, a.y) }
            for (i in r.from + 1..r.to) {
                val o = proj.at(route[i].lat, route[i].lon)
                if ((o - last).getDistance() >= 1.5f || i == r.to) path.lineTo(o.x, o.y).also { last = o }
            }
            drawPath(path, speedColor((r.bucket!! + .5f) / COLOR_BUCKETS), style = stroke)
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

/**
 * Speed over time: the average speed of each of [GRAPH_BINS] slices of the trip (stops dip to zero),
 * as a smooth line coloured like the route, with speed gridlines and the start and end times.
 */
@Composable
fun SpeedGraph(points: List<PointEntity>, modifier: Modifier = Modifier) {
    val profile = remember(points) {
        speedProfile(LongArray(points.size) { points[it].t }, FloatArray(points.size) { points[it].speedMps }, GRAPH_BINS)
    }
    val tripMax = maxSpeedOf(points)
    val labels = rememberTextMeasurer()
    val labelStyle = TextStyle(color = SpeedoColors.Muted, fontSize = 10.sp)
    Canvas(modifier) {
        drawRoundRect(SpeedoColors.Raised, cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()))
        if (points.size < 2) return@Canvas
        val left = 34.dp.toPx()
        val right = size.width - 14.dp.toPx()
        val top = 14.dp.toPx()
        val bottom = size.height - 24.dp.toPx()
        val vMax = max(profile.max(), .5f) * 1.12f
        fun y(mps: Float) = bottom - mps / vMax * (bottom - top)

        // Gridlines every round step of the display unit.
        val step = niceGridStep(Fmt.speed(vMax.toDouble()))
        var g = 0.0
        while (g <= Fmt.speed(vMax.toDouble())) {
            val gy = y((g / Fmt.speed(1.0)).toFloat())
            drawLine(Color.White.copy(alpha = if (g == 0.0) .14f else .06f), Offset(left, gy), Offset(right, gy), 1.dp.toPx())
            val text = labels.measure(Fmt.decimal(g, 0), labelStyle)
            drawText(text, topLeft = Offset(left - 8.dp.toPx() - text.size.width, gy - text.size.height / 2f))
            g += step
        }

        // A smooth line through the slice midpoints (quadratic curves never overshoot below zero).
        val pts = profile.mapIndexed { i, v -> Offset(left + (i + .5f) / profile.size * (right - left), y(v)) }
        fun Path.curve() {
            moveTo(left, pts.first().y)
            lineTo(pts.first().x, pts.first().y)
            for (i in 1 until pts.size) {
                val a = pts[i - 1]
                val b = pts[i]
                quadraticTo(a.x, a.y, (a.x + b.x) / 2, (a.y + b.y) / 2)
            }
            lineTo(pts.last().x, pts.last().y)
            lineTo(right, pts.last().y)
        }
        val line = Path().apply { curve() }
        // Coloured by height on the route's speed scale (0 to the trip's top speed).
        val colours = (0..6).map { speedColor((1 - it / 6f) * vMax / tripMax) }
        val area = Path().apply {
            curve()
            lineTo(right, bottom)
            lineTo(left, bottom)
            close()
        }
        drawPath(area, Brush.verticalGradient(colours.map { it.copy(alpha = .22f) }, startY = top, endY = bottom))
        drawPath(line, Brush.verticalGradient(colours, startY = top, endY = bottom), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Start and end times under the graph.
        val start = labels.measure(Fmt.clock(points.first().t), labelStyle)
        val end = labels.measure(Fmt.clock(points.last().t), labelStyle)
        drawText(start, topLeft = Offset(left, bottom + 6.dp.toPx()))
        drawText(end, topLeft = Offset(right - end.size.width, bottom + 6.dp.toPx()))
    }
}

private const val GRAPH_BINS = 180

/** 1, 2 or 5 × 10ⁿ, giving two to four gridlines up to [max]. */
private fun niceGridStep(max: Double): Double {
    val raw = max / 3
    val mag = 10.0.pow(kotlin.math.floor(kotlin.math.log10(raw.coerceAtLeast(1e-6))))
    return listOf(1.0, 2.0, 5.0, 10.0).map { it * mag }.first { it >= raw }
}

/** The fastest point of a trip, for the colour scale (at least 1 m/s so a stationary trip still draws). */
fun maxSpeedOf(points: List<PointEntity>): Float = max(points.maxOfOrNull { it.speedMps } ?: 0f, 1f)

/** The route's colour scale under the map: 0 up to the trip's top speed. */
@Composable
fun SpeedLegend(maxMps: Float, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("0", color = SpeedoColors.Muted, fontSize = 11.sp)
        Box(
            Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                .background(Brush.horizontalGradient((0..8).map { speedColor(it / 8f) })),
        )
        Text("${Fmt.decimal(Fmt.speed(maxMps.toDouble()), 0)} ${Fmt.speedUnit}", color = SpeedoColors.Muted, fontSize = 11.sp)
    }
}

/** A finished trip's route on the map (MapLibre on Android and iOS; the plain drawing on the desktop preview). */
@Composable
expect fun TripMap(points: List<PointEntity>, modifier: Modifier = Modifier)
