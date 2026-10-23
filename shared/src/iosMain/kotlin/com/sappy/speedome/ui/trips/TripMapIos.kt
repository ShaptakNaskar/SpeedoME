package com.sappy.speedome.ui.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sappy.speedome.data.PointEntity
import com.sappy.speedome.tracking.RoutePoint
import com.sappy.speedome.ui.Dot
import com.sappy.speedome.ui.MapButton
import com.sappy.speedome.ui.RouteLayers
import com.sappy.speedome.ui.STYLE_URL
import com.sappy.speedome.ui.pointGeoJson
import com.sappy.speedome.ui.routeGeoJson
import com.sappy.speedome.ui.theme.SpeedoColors
import org.maplibre.compose.camera.CameraFit
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.DpPadding
import org.maplibre.spatialk.geojson.BoundingBox
import kotlin.math.abs

/** The route's bounds; a single point gets a small box around it. */
private fun boundsOf(route: List<RoutePoint>): BoundingBox {
    var w = route.minOf { it.lon }
    var e = route.maxOf { it.lon }
    var s = route.minOf { it.lat }
    var n = route.maxOf { it.lat }
    if (abs(e - w) < 1e-4) { w -= 5e-4; e += 5e-4 }
    if (abs(n - s) < 1e-4) { s -= 5e-4; n += 5e-4 }
    return BoundingBox(west = w, south = s, east = e, north = n)
}

/** A trip's route on the map, coloured by speed, with start (green) and finish (white); FIT frames it again. */
@Composable
actual fun TripMap(points: List<PointEntity>, modifier: Modifier) {
    val route = remember(points) { points.map { RoutePoint(it.lat, it.lon, it.speedMps, it.segment) } }
    if (route.isEmpty()) {
        Box(modifier.clip(RoundedCornerShape(16.dp)).background(SpeedoColors.Raised))
        return
    }
    val (lines, gaps) = remember(route) { routeGeoJson(route, maxSpeedOf(points)) }
    val ends = remember(route) {
        pointGeoJson(listOf(route.first().lat to route.first().lon)) to pointGeoJson(listOf(route.last().lat to route.last().lon))
    }
    val state = rememberMapState(baseStyle = BaseStyle.Uri(STYLE_URL)) {
        RouteLayers(lines, gaps)
        Dot("start", ends.first, Color(0xFF3ECF7A), 6f)
        Dot("finish", ends.second, Color.White, 6f)
    }
    var fitTick by remember { mutableStateOf(0) }
    LaunchedEffect(route, fitTick) {
        state.animateCameraToBounds(boundsOf(route), CameraFit(fitPadding = DpPadding(28.dp, 28.dp, 28.dp, 28.dp)))
    }
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(SpeedoColors.Raised)) {
        MaplibreMap(Modifier.fillMaxSize(), state)
        MapButton("FIT", "Show the whole route", Modifier.align(Alignment.TopEnd)) { fitTick++ }
    }
}
