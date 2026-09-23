package com.sappy.speedome.engine

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    private const val EARTH_RADIUS_M = 6_371_008.8
    private const val DEG = Math.PI / 180

    /** Great-circle (haversine) distance in metres. */
    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = (lat2 - lat1) * DEG
        val dLon = (lon2 - lon1) * DEG
        val h = sin(dLat / 2).let { it * it } + cos(lat1 * DEG) * cos(lat2 * DEG) * sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(h)))
    }

    /** Moves a point by [eastM]/[northM] metres (small offsets; equirectangular). */
    fun offset(lat: Double, lon: Double, eastM: Double, northM: Double): Pair<Double, Double> {
        val dLat = northM / EARTH_RADIUS_M / DEG
        val dLon = eastM / (EARTH_RADIUS_M * cos(lat * DEG)) / DEG
        return lat + dLat to lon + dLon
    }
}
