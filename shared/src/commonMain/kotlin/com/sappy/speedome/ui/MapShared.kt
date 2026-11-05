package com.sappy.speedome.ui

import com.sappy.speedome.engine.RoutePoint
import kotlin.math.abs
import kotlin.math.pow

// The live map's platform-neutral parts, shared by the Android (MapLibre Android) and iOS
// (maplibre-compose) map pages.

/** Course over ground from the last two route points at least ~5 m apart, in degrees from north. */
internal fun courseOf(route: List<RoutePoint>): Double? {
    val b = route.lastOrNull() ?: return null
    val a = route.asReversed().firstOrNull { abs(it.lat - b.lat) + abs(it.lon - b.lon) > 5e-5 } ?: return null
    val k = kotlin.math.cos(b.lat * kotlin.math.PI / 180)
    return (kotlin.math.atan2((b.lon - a.lon) * k, b.lat - a.lat) * 180 / kotlin.math.PI + 360) % 360
}

/** How many speed colours a route uses. */
internal const val COLOR_BUCKETS = 24

/** Ground metres per screen pixel (dp) at a map zoom level and latitude (MapLibre's 512-px tiles). */
internal fun metresPerPixel(zoom: Double, lat: Double): Double =
    40_075_016.7 * kotlin.math.cos(lat * kotlin.math.PI / 180) / (512 * 2.0.pow(zoom))

/** Closer in when slow, further out on the motorway. */
internal fun zoomFor(kmh: Double): Double = when {
    kmh < 8 -> 17.0
    kmh > 120 -> 13.6
    else -> 17.0 - (kmh - 8) / 112 * 3.4
}
