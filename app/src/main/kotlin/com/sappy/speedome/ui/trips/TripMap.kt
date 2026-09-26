package com.sappy.speedome.ui.trips

import android.view.Gravity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import com.sappy.speedome.data.PointEntity
import com.sappy.speedome.tracking.RoutePoint
import com.sappy.speedome.ui.Fmt
import com.sappy.speedome.ui.TabIcons
import com.sappy.speedome.ui.addRouteLayers
import com.sappy.speedome.ui.loadDarkStyle
import com.sappy.speedome.ui.pt
import com.sappy.speedome.ui.rememberMapView
import com.sappy.speedome.ui.routeFeatures
import com.sappy.speedome.ui.theme.SpeedoColors
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import kotlin.math.max

/** The fastest point of a trip, for the colour scale (at least 1 m/s so a stationary trip still draws). */
fun maxSpeedOf(points: List<PointEntity>): Float = max(points.maxOfOrNull { it.speedMps } ?: 0f, 1f)

/**
 * A trip's route on the dark map (docs/plan.md §7), coloured by speed from 0 to the trip's top speed,
 * with the start (green) and finish (white) marked. Pan and zoom freely; the corner button frames the
 * whole route again. Until the map style has loaded, or if it can't (offline on first use), the plain
 * route drawing shows instead.
 */
@Composable
fun TripMap(points: List<PointEntity>, modifier: Modifier = Modifier) {
    val route = remember(points) { points.map { RoutePoint(it.lat, it.lon, it.speedMps, it.segment) } }
    val maxMps = remember(points) { maxSpeedOf(points) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var drawn by remember { mutableStateOf(false) }
    var moved by remember { mutableStateOf(false) }
    val padding = with(LocalDensity.current) { 36.dp.roundToPx() }
    val mapView = rememberMapView { m ->
        m.uiSettings.isRotateGesturesEnabled = false
        m.uiSettings.attributionGravity = Gravity.BOTTOM or Gravity.START
        m.setMaxZoomPreference(18.0)
        m.addOnCameraMoveStartedListener { reason ->
            if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) moved = true
        }
        m.loadDarkStyle { s ->
            s.addRouteLayers()
            s.addEnds()
            map = m
            style = s
        }
    }
    LaunchedEffect(style, route) {
        val s = style ?: return@LaunchedEffect
        s.getSourceAs<GeoJsonSource>("route")?.setGeoJson(routeFeatures(route, maxMps))
        s.getSourceAs<GeoJsonSource>("ends")?.setGeoJson(endsOf(route))
        mapView.doOnLayout { map?.let { fit(it, route, padding) } }
        moved = false
        drawn = route.isNotEmpty()
    }
    Box(modifier.clip(RoundedCornerShape(16.dp)).semantics { contentDescription = "Map of the route" }) {
        RouteView(points, Modifier.fillMaxSize())
        AndroidView({ mapView }, Modifier.fillMaxSize().alpha(if (drawn) 1f else 0f))
        if (drawn && moved) {
            Box(
                Modifier.align(Alignment.BottomEnd).padding(8.dp).size(48.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xE6101312))
                    .clickable(role = Role.Button) {
                        map?.let { fit(it, route, padding, animate = true) }
                        moved = false
                    }
                    .semantics { contentDescription = "Show the whole route" },
                contentAlignment = Alignment.Center,
            ) { Icon(TabIcons.Fit, contentDescription = null, tint = SpeedoColors.Accent, modifier = Modifier.size(22.dp)) }
        }
    }
}

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

/** Frames the whole route (a single point gets a street-level view). */
private fun fit(m: MapLibreMap, route: List<RoutePoint>, paddingPx: Int, animate: Boolean = false) {
    val first = route.firstOrNull() ?: return
    val update = if (route.all { it.lat == first.lat && it.lon == first.lon }) {
        CameraUpdateFactory.newLatLngZoom(LatLng(first.lat, first.lon), 16.0)
    } else {
        CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(route.map { LatLng(it.lat, it.lon) }).build(), paddingPx)
    }
    if (animate) m.easeCamera(update, 600) else m.moveCamera(update)
}

/** Start and finish dots, as on the plain route drawing. */
private fun Style.addEnds() {
    addSource(GeoJsonSource("ends"))
    addLayer(
        CircleLayer("route-ends", "ends").withProperties(
            PropertyFactory.circleRadius(6f),
            PropertyFactory.circleColor(Expression.switchCase(Expression.get("finish"), Expression.color(0xFFFFFFFF.toInt()), Expression.color(0xFF3ECF7A.toInt()))),
            PropertyFactory.circleStrokeColor(Expression.color(0xFF000000.toInt())),
            PropertyFactory.circleStrokeWidth(2f),
        ),
    )
}

private fun endsOf(route: List<RoutePoint>): FeatureCollection {
    if (route.isEmpty()) return FeatureCollection.fromFeatures(emptyList())
    return FeatureCollection.fromFeatures(
        listOf(
            Feature.fromGeometry(route.first().pt()).apply { addBooleanProperty("finish", false) },
            Feature.fromGeometry(route.last().pt()).apply { addBooleanProperty("finish", true) },
        ),
    )
}
