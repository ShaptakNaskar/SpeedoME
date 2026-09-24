package com.sappy.speedome.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.view.Gravity
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.createBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sappy.speedome.LocalAppContainer
import com.sappy.speedome.tracking.RoutePoint
import com.sappy.speedome.ui.theme.SpeedoColors
import com.sappy.speedome.ui.trips.speedColor
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlin.math.abs

/** Configurable per the risk table in docs/plan.md §15 (OpenFreeMap has no SLA). */
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/dark"
private const val CACHE_BYTES = 150L * 1024 * 1024
private const val COLOR_BUCKETS = 24

/** Live map: route coloured by speed, a heading puck, follow camera, speed card (docs/plan.md §9.5). */
@Composable
fun MapPage(driver: GaugeDriver, northUp: Boolean, modifier: Modifier = Modifier) {
    val app = LocalAppContainer.current
    val context = LocalContext.current
    val route by app.liveRoute.points.collectAsStateWithLifecycle()
    val view = rememberTrackView(app.tracking.state, 500)
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var following by remember { mutableStateOf(true) }

    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        OfflineManager.getInstance(context.applicationContext).setMaximumAmbientCacheSize(CACHE_BYTES, null)
        // TextureView so the map composites inside Compose (Crossfade alpha, scrolling column).
        MapView(context, MapLibreMapOptions.createFromAttributes(context).textureMode(true)).apply {
            onCreate(Bundle())
            getMapAsync { m ->
                m.uiSettings.apply {
                    isLogoEnabled = false
                    isCompassEnabled = false
                    isTiltGesturesEnabled = false
                    attributionGravity = Gravity.BOTTOM or Gravity.END
                    setAttributionTintColor(SpeedoColors.Muted.toArgb())
                }
                m.addOnCameraMoveStartedListener { reason ->
                    if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) following = false
                }
                m.setStyle(Style.Builder().fromUri(STYLE_URL)) { s ->
                    s.restyle()
                    s.addRouteLayers(resources.displayMetrics.density)
                    map = m
                    style = s
                }
            }
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    val rangeKmh = driver.frame.rangeKmh
    LaunchedEffect(style, route, (rangeKmh / 10).toInt()) {
        style?.getSourceAs<GeoJsonSource>("route")?.setGeoJson(routeFeatures(route, rangeKmh / 3.6f))
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
    LaunchedEffect(map, fix?.tNanos, following, northUp) {
        val m = map ?: return@LaunchedEffect
        fix ?: return@LaunchedEffect
        if (!following) return@LaunchedEffect
        val bearing = if (northUp) 0.0 else heading?.toDouble() ?: m.cameraPosition.bearing
        val camera = CameraPosition.Builder()
            .target(LatLng(fix.lat, fix.lon))
            .zoom(zoomFor(view.speedMps * 3.6))
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
            Text("${driver.frame.readout}", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold, lineHeight = 40.sp)
            Text("km/h", color = SpeedoColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
        }
        Text(
            "${Fmt.decimal(view.distanceM / 1000, 2)} km · avg ${Fmt.decimal(view.avgOverallMps * 3.6, 0)} · max ${Fmt.decimal(view.maxMps * 3.6, 0)}",
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

/** Closer in when slow, further out on the motorway. */
private fun zoomFor(kmh: Double): Double = when {
    kmh < 8 -> 17.0
    kmh > 120 -> 13.6
    else -> 17.0 - (kmh - 8) / 112 * 3.4
}

/**
 * Consecutive points are merged into LineStrings while their speed colour bucket stays the same, so a
 * long drive is a few hundred features rather than one per fix. Segment changes become dashed gaps.
 */
private fun routeFeatures(points: List<RoutePoint>, rangeMps: Float): FeatureCollection {
    val out = ArrayList<Feature>()
    if (points.size < 2) return FeatureCollection.fromFeatures(out)
    var run = arrayListOf(points[0].pt())
    var bucket = -1
    fun flush() {
        if (run.size >= 2 && bucket >= 0) {
            out += Feature.fromGeometry(LineString.fromLngLats(run)).apply {
                addStringProperty("color", colorHex(speedColor((bucket + .5f) / COLOR_BUCKETS)))
                addBooleanProperty("gap", false)
            }
        }
    }
    for (i in 1 until points.size) {
        val a = points[i - 1]
        val b = points[i]
        if (a.segment != b.segment) {
            flush()
            out += Feature.fromGeometry(LineString.fromLngLats(listOf(a.pt(), b.pt()))).apply { addBooleanProperty("gap", true) }
            run = arrayListOf(b.pt())
            bucket = -1
            continue
        }
        val bk = ((b.speedMps / rangeMps.coerceAtLeast(1f)).coerceIn(0f, .999f) * COLOR_BUCKETS).toInt()
        if (bucket >= 0 && abs(bk - bucket) > 0) {
            flush()
            run = arrayListOf(a.pt())
        }
        bucket = bk
        run += b.pt()
    }
    flush()
    return FeatureCollection.fromFeatures(out)
}

private fun RoutePoint.pt() = Point.fromLngLat(lon, lat)

private fun colorHex(c: Color) = String.format(java.util.Locale.ROOT, "#%06X", c.toArgb() and 0xFFFFFF)

/** The OpenFreeMap dark style is near-black; lift roads a little and tint water so the map reads at a glance. */
private fun Style.restyle() {
    getLayerAs<BackgroundLayer>("background")?.setProperties(PropertyFactory.backgroundColor("#070909"))
    getLayerAs<FillLayer>("water")?.setProperties(PropertyFactory.fillColor("#0C1A22"))
    getLayerAs<LineLayer>("waterway")?.setProperties(PropertyFactory.lineColor("#0C1A22"))
    getLayerAs<FillLayer>("building")?.setProperties(PropertyFactory.fillColor("#101413"))
    getLayerAs<LineLayer>("highway_minor")?.setProperties(PropertyFactory.lineColor("#262C2B"))
    getLayerAs<LineLayer>("highway_major_inner")?.setProperties(PropertyFactory.lineColor("#2F3634"))
    getLayerAs<LineLayer>("highway_major_subtle")?.setProperties(PropertyFactory.lineColor("#2F3634"))
    getLayerAs<LineLayer>("highway_motorway_subtle")?.setProperties(PropertyFactory.lineColor("#39403E"))
    getLayerAs<SymbolLayer>("highway_name_other")?.setProperties(PropertyFactory.textColor("#7D8683"))
}

private fun Style.addRouteLayers(density: Float) {
    addSource(GeoJsonSource("route"))
    addSource(GeoJsonSource("puck"))
    addImage("puck", puckBitmap(false, density))
    addImage("puck-cone", puckBitmap(true, density))
    // Above roads and rail, below road and place labels (water_name comes first in the style, too low).
    val beforeLabels = (getLayer("highway_name_other") ?: layers.lastOrNull { it !is SymbolLayer }?.let { l -> layers.getOrNull(layers.indexOf(l) + 1) })?.id
    val routeLine = LineLayer("route-line", "route").withProperties(
        PropertyFactory.lineColor(Expression.get("color")),
        PropertyFactory.lineWidth(Expression.interpolate(Expression.linear(), Expression.zoom(), Expression.stop(10, 3f), Expression.stop(17, 7f))),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
    ).withFilter(Expression.eq(Expression.get("gap"), false))
    val gapLine = LineLayer("route-gap", "route").withProperties(
        PropertyFactory.lineColor("#FFFFFF"),
        PropertyFactory.lineOpacity(.45f),
        PropertyFactory.lineWidth(2f),
        PropertyFactory.lineDasharray(arrayOf(2f, 2f)),
    ).withFilter(Expression.eq(Expression.get("gap"), true))
    if (beforeLabels != null) {
        addLayerBelow(routeLine, beforeLabels)
        addLayerBelow(gapLine, beforeLabels)
    } else {
        addLayer(routeLine)
        addLayer(gapLine)
    }
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
