package com.sappy.speedome.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.createBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sappy.speedome.LocalAppContainer
import com.sappy.speedome.gauges.warned
import com.sappy.speedome.tracking.RoutePoint
import com.sappy.speedome.ui.theme.SpeedoColors
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.gestures.MoveGestureDetector
import org.maplibre.android.gestures.RotateGestureDetector
import org.maplibre.android.gestures.StandardScaleGestureDetector
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import kotlin.math.abs

/** Live map: route coloured by speed, a heading puck, follow camera, speed card (docs/plan.md §9.5). */
@Composable
fun MapPage(driver: GaugeDriver, northUp: Boolean, modifier: Modifier = Modifier) {
    val app = LocalAppContainer.current
    val route by app.liveRoute.points.collectAsStateWithLifecycle()
    val view = rememberTrackView(app.tracking.state, 500)
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var following by remember { mutableStateOf(true) }
    // Pinch-zoom keeps following and remembers how far in/out you like it relative to the
    // speed-based zoom; panning or rotating stops following until RE-CENTRE.
    var zoomOffset by remember { mutableDoubleStateOf(0.0) }
    var scaling by remember { mutableStateOf(false) }

    val mapView = rememberMapView { m ->
        m.setMinZoomPreference(3.0)
        m.addOnMoveListener(object : MapLibreMap.OnMoveListener {
            override fun onMoveBegin(d: MoveGestureDetector) {
                if (d.pointersCount < 2) following = false // a two-finger drag is part of a pinch
            }
            override fun onMove(d: MoveGestureDetector) = Unit
            override fun onMoveEnd(d: MoveGestureDetector) = Unit
        })
        m.addOnRotateListener(object : MapLibreMap.OnRotateListener {
            override fun onRotateBegin(d: RotateGestureDetector) {
                following = false
            }
            override fun onRotate(d: RotateGestureDetector) = Unit
            override fun onRotateEnd(d: RotateGestureDetector) = Unit
        })
        m.addOnScaleListener(object : MapLibreMap.OnScaleListener {
            override fun onScaleBegin(d: StandardScaleGestureDetector) {
                scaling = true
            }
            override fun onScale(d: StandardScaleGestureDetector) = Unit
            override fun onScaleEnd(d: StandardScaleGestureDetector) {
                scaling = false
                zoomOffset = (m.cameraPosition.zoom - zoomFor(lastKmh)).coerceIn(-8.0, 4.0)
            }
        })
        val density = resources.displayMetrics.density
        m.loadDarkStyle { s ->
            s.addRouteLayers()
            s.addPuck(density)
            map = m
            style = s
        }
    }

    val rangeKmh = driver.frame.rangeKmh
    LaunchedEffect(style, route, (rangeKmh / 10).toInt()) {
        // The dial range is in display units; the route colours scale to it in m/s.
        style?.getSourceAs<GeoJsonSource>("route")?.setGeoJson(routeFeatures(route, (rangeKmh / Fmt.speed(1.0)).toFloat()))
    }
    val fix = view.lastFix
    val moving = view.speedMps > 1.5
    // Course from the route itself first: some receivers (and the emulator) report a stale 0° bearing.
    val heading = (courseOf(route) ?: fix?.bearing)?.takeIf { moving }?.toFloat()
    LaunchedEffect(style, fix?.tNanos) {
        val s = style ?: return@LaunchedEffect
        fix ?: return@LaunchedEffect
        val puck = Feature.fromGeometry(Point.fromLngLat(fix.lon, fix.lat)).apply {
            addNumberProperty("bearing", heading ?: 0f)
            addBooleanProperty("cone", heading != null)
        }
        s.getSourceAs<GeoJsonSource>("puck")?.setGeoJson(puck)
    }
    lastKmh = view.speedMps * 3.6
    LaunchedEffect(map, fix?.tNanos, following, northUp, zoomOffset) {
        val m = map ?: return@LaunchedEffect
        fix ?: return@LaunchedEffect
        if (!following || scaling) return@LaunchedEffect
        val bearing = if (northUp) 0.0 else heading?.toDouble() ?: m.cameraPosition.bearing
        val camera = CameraPosition.Builder()
            .target(LatLng(fix.lat, fix.lon))
            .zoom(zoomFor(view.speedMps * 3.6) + zoomOffset)
            .bearing(bearing)
            .padding(0.0, if (northUp) 0.0 else mapView.height * .35, 0.0, 0.0) // course-up: see more road ahead
            .build()
        m.easeCamera(CameraUpdateFactory.newCameraPosition(camera), 900, false)
    }

    Box(modifier.background(SpeedoColors.Background)) {
        AndroidView({ mapView }, Modifier.fillMaxSize())
        SpeedCard(driver, view, Modifier.align(Alignment.BottomStart).padding(12.dp))
        if (!following) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(12.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xE6101312))
                    .clickable(role = Role.Button) { following = true }
                    .semantics { contentDescription = "Re-centre map" }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) { Text("RE-CENTRE", color = SpeedoColors.Accent, fontSize = 12.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold) }
        }
        if (fix == null) {
            Text(
                "Waiting for position", color = SpeedoColors.Muted, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
            )
        }
    }
}

@Composable
private fun SpeedCard(driver: GaugeDriver, view: com.sappy.speedome.engine.TrackView, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xE6090B0B)).padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${driver.frame.readout}", color = driver.frame.warned(Color.White), fontSize = 40.sp, fontWeight = FontWeight.Bold, lineHeight = 40.sp)
            Text(Fmt.speedUnit, color = SpeedoColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
        }
        Text(
            "${Fmt.dist(view.distanceM)} ${Fmt.distUnit} · avg ${Fmt.decimal(Fmt.speed(view.avgOverallMps), 0)} · max ${Fmt.decimal(Fmt.speed(view.maxMps), 0)}",
            color = SpeedoColors.Text, fontSize = 12.sp,
        )
    }
}

/** Course over ground from the last two route points at least ~5 m apart, in degrees from north. */
private fun courseOf(route: List<RoutePoint>): Double? {
    val b = route.lastOrNull() ?: return null
    val a = route.asReversed().firstOrNull { abs(it.lat - b.lat) + abs(it.lon - b.lon) > 5e-5 } ?: return null
    val k = kotlin.math.cos(Math.toRadians(b.lat))
    return (Math.toDegrees(kotlin.math.atan2((b.lon - a.lon) * k, b.lat - a.lat)) + 360) % 360
}

/** Latest speed for the gesture callbacks (they run outside composition). */
@Volatile
private var lastKmh = 0.0

/** Closer in when slow, further out on the motorway. */
private fun zoomFor(kmh: Double): Double = when {
    kmh < 8 -> 17.0
    kmh > 120 -> 13.6
    else -> 17.0 - (kmh - 8) / 112 * 3.4
}

/** The position puck, above everything; [puckBitmap] draws its two images. */
private fun Style.addPuck(density: Float) {
    addSource(GeoJsonSource("puck"))
    addImage("puck", puckBitmap(false, density))
    addImage("puck-cone", puckBitmap(true, density))
    addLayer(
        SymbolLayer("puck", "puck").withProperties(
            PropertyFactory.iconImage(Expression.switchCase(Expression.get("cone"), Expression.literal("puck-cone"), Expression.literal("puck"))),
            PropertyFactory.iconRotate(Expression.get("bearing")),
            PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
        ),
    )
}

/** White dot with an amber ring; the cone variant adds a translucent heading wedge pointing up. */
private fun puckBitmap(cone: Boolean, density: Float): Bitmap {
    val size = (64 * density).toInt()
    val r = 6 * density
    val bmp = createBitmap(size, size)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val mid = size / 2f
    if (cone) {
        p.shader = android.graphics.RadialGradient(mid, mid, mid, 0x99E8A33D.toInt(), 0x00E8A33D, android.graphics.Shader.TileMode.CLAMP)
        val wedge = android.graphics.Path().apply {
            moveTo(mid, mid); lineTo(mid - mid * .62f, 0f); lineTo(mid + mid * .62f, 0f); close()
        }
        c.drawPath(wedge, p)
        p.shader = null
    }
    p.color = 0xFF000000.toInt()
    c.drawCircle(mid, mid, r * 1.25f, p)
    p.color = SpeedoColors.Accent.toArgb()
    c.drawCircle(mid, mid, r * 1.1f, p)
    p.color = 0xFFFFFFFF.toInt()
    c.drawCircle(mid, mid, r * .72f, p)
    return bmp
}
