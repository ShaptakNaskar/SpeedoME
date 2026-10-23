package com.sappy.speedome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sappy.speedome.LocalAppContainer
import com.sappy.speedome.tracking.RoutePoint
import com.sappy.speedome.ui.theme.SpeedoColors
import com.sappy.speedome.ui.trips.speedColor
import org.maplibre.compose.camera.CameraAnimation
import org.maplibre.compose.camera.CameraMoveReason
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.convertToColor
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.interpolate
import org.maplibre.compose.expressions.dsl.linear
import org.maplibre.compose.expressions.dsl.zoom
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.DpPadding
import org.maplibre.spatialk.geojson.Position
import kotlin.time.Duration.Companion.milliseconds

// The Map theme and trip maps on iOS, with maplibre-compose and the same OpenFreeMap dark style as
// Android (docs/plan.md §9.5). The route is coloured by speed in buckets like Android's.

internal const val STYLE_URL = "https://tiles.openfreemap.org/styles/dark"
private const val COLOR_BUCKETS = 24

private fun hex(c: Color): String {
    fun h(v: Float) = (v * 255 + .5f).toInt().coerceIn(0, 255).toString(16).padStart(2, '0')
    return "#" + h(c.red) + h(c.green) + h(c.blue)
}

private fun coord(p: RoutePoint) = "[${p.lon},${p.lat}]"

private fun lineFeature(coords: List<String>, color: String?) =
    """{"type":"Feature","properties":{${if (color != null) "\"color\":\"$color\"" else ""}},""" +
        """"geometry":{"type":"LineString","coordinates":[${coords.joinToString(",")}]}}"""

private fun collection(features: List<String>) = """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""

/**
 * The route as GeoJSON: speed-coloured lines (consecutive points merged while their colour bucket
 * holds) and straight gap lines where the segment changes (pauses, resume gaps).
 */
internal fun routeGeoJson(points: List<RoutePoint>, rangeMps: Float): Pair<String, String> {
    val lines = ArrayList<String>()
    val gaps = ArrayList<String>()
    if (points.size >= 2) {
        var run = arrayListOf(coord(points[0]))
        var bucket = -1
        fun flush() {
            if (run.size >= 2 && bucket >= 0) lines += lineFeature(run, hex(speedColor((bucket + .5f) / COLOR_BUCKETS)))
        }
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            if (a.segment != b.segment) {
                flush()
                gaps += lineFeature(listOf(coord(a), coord(b)), null)
                run = arrayListOf(coord(b))
                bucket = -1
                continue
            }
            val bk = ((b.speedMps / rangeMps.coerceAtLeast(1f)).coerceIn(0f, .999f) * COLOR_BUCKETS).toInt()
            if (bucket >= 0 && bk != bucket) {
                flush()
                run = arrayListOf(coord(a))
            }
            bucket = bk
            run += coord(b)
        }
        flush()
    }
    return collection(lines) to collection(gaps)
}

internal fun pointGeoJson(points: List<Pair<Double, Double>>) = collection(
    points.map { (lat, lon) -> """{"type":"Feature","properties":{},"geometry":{"type":"Point","coordinates":[$lon,$lat]}}""" },
)

/** The speed-coloured route and its dashed gaps, above the base style. */
@Composable
internal fun RouteLayers(lines: String, gaps: String) {
    val lineSource = rememberGeoJsonSource(GeoJsonData.JsonString(lines))
    val gapSource = rememberGeoJsonSource(GeoJsonData.JsonString(gaps))
    LineLayer(
        id = "route-line", source = lineSource,
        color = feature["color"].convertToColor(),
        width = interpolate(linear(), zoom(), 10 to const(3.dp), 17 to const(7.dp)),
        cap = const(LineCap.Round), join = const(LineJoin.Round),
    )
    LineLayer(
        id = "route-gap", source = gapSource,
        color = const(Color.White), opacity = const(.45f), width = const(2.dp),
        dasharray = const(listOf(2, 2)),
    )
}

/** A dot with a dark rim: the position puck (amber), or a trip's start (green) and finish (white). */
@Composable
internal fun Dot(id: String, json: String, fill: Color, radius: Float) {
    val source = rememberGeoJsonSource(GeoJsonData.JsonString(json))
    CircleLayer(id = id, source = source, color = const(fill), radius = const(radius.dp), strokeColor = const(Color.Black), strokeWidth = const(2.dp))
}

@Composable
actual fun MapPage(driver: GaugeDriver, northUp: Boolean, modifier: Modifier) {
    val app = LocalAppContainer.current
    val route by app.liveRoute.points.collectAsStateWithLifecycle()
    val view = rememberTrackView(app.tracking.state, 500)
    val fix = view.lastFix
    val rangeBucket = (driver.frame.rangeKmh / 10).toInt()
    val (lines, gaps) = remember(route, rangeBucket) { routeGeoJson(route, (driver.frame.rangeKmh / Fmt.speed(1.0)).toFloat()) }
    val puck = remember(fix?.lat, fix?.lon) { pointGeoJson(listOfNotNull(fix?.let { it.lat to it.lon })) }
    val moving = view.speedMps > 1.5
    // Course from the route itself first: some receivers report a stale bearing.
    val heading = (courseOf(route) ?: fix?.bearing)?.takeIf { moving }
    var following by remember { mutableStateOf(true) }

    val state = rememberMapState(
        baseStyle = BaseStyle.Uri(STYLE_URL),
        initialCameraPosition = CameraPosition(center = Position(fix?.lon ?: 0.0, fix?.lat ?: 0.0), zoom = if (fix != null) 16.0 else 2.0),
    ) {
        RouteLayers(lines, gaps)
        Dot("puck", puck, SpeedoColors.Accent, 7f)
    }
    // Panning or rotating the map stops following until RE-CENTRE.
    LaunchedEffect(state) {
        snapshotFlow { state.isCameraMoving && state.cameraMoveReason == CameraMoveReason.Gesture }.collect { if (it) following = false }
    }

    BoxWithConstraints(modifier.background(SpeedoColors.Background)) {
        val lookAhead = if (northUp) 0.dp else maxHeight * .35f // course-up: see more road ahead
        LaunchedEffect(fix?.tNanos, following, northUp) {
            val f = fix ?: return@LaunchedEffect
            if (!following) return@LaunchedEffect
            state.animateCamera(
                CameraUpdate(
                    center = Position(f.lon, f.lat),
                    zoom = zoomFor(view.speedMps * 3.6),
                    bearing = if (northUp) 0.0 else heading ?: state.cameraPosition.bearing,
                    padding = DpPadding(top = lookAhead),
                ),
                CameraAnimation.Ease { duration = 900.milliseconds },
            )
        }
        MaplibreMap(Modifier.fillMaxSize(), state)
        SpeedCard(driver, view, Modifier.align(Alignment.BottomStart).padding(12.dp))
        if (!following) MapButton("RE-CENTRE", "Re-centre map", Modifier.align(Alignment.TopEnd)) { following = true }
        if (fix == null) {
            Text("Waiting for position", color = SpeedoColors.Muted, fontSize = 13.sp, modifier = Modifier.align(Alignment.TopStart).padding(14.dp))
        }
    }
}

@Composable
internal fun MapButton(label: String, description: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.padding(12.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xE6101312))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(label, color = SpeedoColors.Accent, fontSize = 12.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold) }
}
