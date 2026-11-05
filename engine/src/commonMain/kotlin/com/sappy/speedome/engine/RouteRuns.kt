package com.sappy.speedome.engine

/** A point of a route drawn on a map. */
data class RoutePoint(val lat: Double, val lon: Double, val speedMps: Float, val segment: Int)

/**
 * One line of a drawn route: points [from]..[to] (indices, inclusive) in colour [bucket] (null on a
 * one-colour route), or a straight [gap] line where the segment changes (pauses, resume gaps).
 */
data class RouteRun(val from: Int, val to: Int, val bucket: Int?, val gap: Boolean = false)

/**
 * Splits a route into lines for a map. With [rangeMps] the colour follows speed: one of [buckets]
 * colours over 0..rangeMps, from each line's mean speed. A line changes colour only once it is
 * [minRunM] long. Map renderers drop lines shorter than a fraction of a pixel, so on a zoomed-out map
 * a line per few seconds of driving would leave holes, and thousands of tiny lines make panning slow.
 * Without [rangeMps], each segment is a single line.
 */
fun routeRuns(points: List<RoutePoint>, rangeMps: Float?, buckets: Int, minRunM: Double = 0.0): List<RouteRun> {
    val out = ArrayList<RouteRun>()
    if (points.size < 2) return out
    fun bucketOf(mps: Double) = rangeMps?.let { ((mps / it.coerceAtLeast(1f)).coerceIn(0.0, .999) * buckets).toInt() }
    var start = 0
    var length = 0.0
    var speedSum = 0.0
    var n = 0
    fun flush(end: Int) {
        if (end > start) out += RouteRun(start, end, bucketOf(speedSum / n))
        length = 0.0
        speedSum = 0.0
        n = 0
    }
    for (i in 1 until points.size) {
        val a = points[i - 1]
        val b = points[i]
        if (a.segment != b.segment) {
            flush(i - 1)
            out += RouteRun(i - 1, i, null, gap = true)
            start = i
            continue
        }
        if (n > 0 && length >= minRunM && bucketOf(b.speedMps.toDouble()) != bucketOf(speedSum / n)) {
            flush(i - 1)
            start = i - 1
        }
        length += Geo.distanceM(a.lat, a.lon, b.lat, b.lon)
        speedSum += b.speedMps
        n++
    }
    flush(points.lastIndex)
    return out
}
