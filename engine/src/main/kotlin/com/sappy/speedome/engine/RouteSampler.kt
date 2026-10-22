package com.sappy.speedome.engine

import kotlin.math.max

/**
 * Picks which fixes become route points: at most one a second, none while paused or poorly located
 * (hAcc ≥ 20 m), and none while parked in place, so stops don't draw GPS-jitter squiggles. The trip
 * recorder and the live map both use it, so the live line matches the stored trip whatever rate the
 * phone delivers fixes at.
 */
class RouteSampler {
    private var seen = Long.MIN_VALUE
    private var lastT: Long? = null
    private var lastLat = 0.0
    private var lastLon = 0.0

    /** Starts a new route. [seenFix] is a fix already accounted for (a restored snapshot's), never taken. */
    fun reset(seenFix: Long? = null) {
        seen = seenFix ?: Long.MIN_VALUE
        lastT = null
    }

    /** The newest fix in [s] if it should become a route point, else null. */
    fun take(s: EngineState): LastFix? {
        val f = s.lastFix ?: return null
        if (f.tNanos == seen) return null
        seen = f.tNanos
        val since = lastT?.let { f.tNanos - it }
        if (s.session.paused || f.hAcc >= 20 || (since != null && since < MIN_GAP_NANOS)) return null
        val parkedHere = s.filter.output == 0.0 && lastT != null &&
            Geo.distanceM(lastLat, lastLon, f.lat, f.lon) < max(f.hAcc, 8.0)
        if (parkedHere) return null
        lastT = f.tNanos
        lastLat = f.lat
        lastLon = f.lon
        return f
    }

    private companion object {
        const val MIN_GAP_NANOS = 950_000_000L
    }
}

/**
 * Every other point, keeping the last one and both ends of every segment, so a route over its point
 * cap loses detail evenly instead of losing its start.
 */
fun <T> List<T>.thinned(segment: (T) -> Int): List<T> = filterIndexed { i, p ->
    i % 2 == 0 || i == lastIndex || segment(p) != segment(this[i - 1]) || segment(p) != segment(this[i + 1])
}
