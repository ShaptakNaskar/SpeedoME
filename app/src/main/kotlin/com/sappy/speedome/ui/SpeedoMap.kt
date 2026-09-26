package com.sappy.speedome.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sappy.speedome.tracking.RoutePoint
import com.sappy.speedome.ui.theme.SpeedoColors
import com.sappy.speedome.ui.trips.speedColor
import org.maplibre.android.MapLibre
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

/** Configurable per the risk table in docs/plan.md §13 (OpenFreeMap has no SLA). */
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/dark"
private const val CACHE_BYTES = 150L * 1024 * 1024
private const val COLOR_BUCKETS = 24

/**
 * A MapLibre map for Compose, shared by the Map theme and trip detail. It uses TextureView mode, so
 * it composites inside Compose (Crossfade alpha, clipping, scrolling columns), follows the screen's
 * lifecycle and is destroyed with the composition. [onMap] runs once, when the map object is ready.
 */
@Composable
fun rememberMapView(onMap: MapView.(MapLibreMap) -> Unit): MapView {
    val context = LocalContext.current
    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        OfflineManager.getInstance(context.applicationContext).setMaximumAmbientCacheSize(CACHE_BYTES, null)
        GestureMapView(context, MapLibreMapOptions.createFromAttributes(context).textureMode(true)).apply {
            onCreate(Bundle())
            getMapAsync { m ->
                m.uiSettings.apply {
                    isLogoEnabled = false
                    isCompassEnabled = false
                    isTiltGesturesEnabled = false
                    attributionGravity = Gravity.BOTTOM or Gravity.END
                    setAttributionTintColor(SpeedoColors.Muted.toArgb())
                }
                onMap(m)
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
    return mapView
}

/**
 * Keeps a gesture that starts on the map inside the map, so a drag or pinch doesn't also scroll (or
 * overscroll-stretch) the page around it. Only ever built in code, never inflated from XML.
 */
@SuppressLint("ViewConstructor")
private class GestureMapView(context: Context, options: MapLibreMapOptions) : MapView(context, options) {
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) parent?.requestDisallowInterceptTouchEvent(true)
        return super.dispatchTouchEvent(ev)
    }
}

/** Loads SpeedoME's dark map style, then calls [onLoaded]. */
fun MapLibreMap.loadDarkStyle(onLoaded: (Style) -> Unit) {
    setStyle(Style.Builder().fromUri(STYLE_URL)) { s ->
        s.restyle()
        onLoaded(s)
    }
}

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

/** A GeoJSON source "route" for [routeFeatures], drawn above roads and rail but below road and place labels. */
fun Style.addRouteLayers() {
    addSource(GeoJsonSource("route"))
    // water_name comes first in the style, too low; the road names are the right place.
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
}

/**
 * The route coloured by speed over 0–[rangeMps]. Consecutive points are merged into LineStrings while
 * their colour bucket stays the same, so a long drive is a few hundred features rather than one per
 * fix. Segment changes (pauses, resume gaps) become dashed straight lines.
 */
fun routeFeatures(points: List<RoutePoint>, rangeMps: Float): FeatureCollection {
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

fun RoutePoint.pt(): Point = Point.fromLngLat(lon, lat)

fun colorHex(c: Color): String = String.format(java.util.Locale.ROOT, "#%06X", c.toArgb() and 0xFFFFFF)
