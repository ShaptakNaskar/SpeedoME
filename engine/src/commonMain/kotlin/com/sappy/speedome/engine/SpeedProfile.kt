package com.sappy.speedome.engine

import kotlin.math.max
import kotlin.math.min

/**
 * A trip's speed for a graph: [bins] equal slices of the time from the first to the last fix, each
 * the average speed (m/s) over its slice. Time between fixes more than [maxGapMs] apart (parked, or
 * no GPS) counts as standing still, so stops show as dips; a long trip becomes a few hundred values
 * instead of thousands of one-second spikes.
 */
fun speedProfile(timesMs: LongArray, speedsMps: FloatArray, bins: Int, maxGapMs: Long = 5_000): FloatArray {
    val out = FloatArray(bins.coerceAtLeast(0))
    if (timesMs.size < 2 || bins < 1) return out
    val t0 = timesMs.first()
    val width = max(timesMs.last() - t0, 1L).toDouble() / bins
    for (i in 1 until timesMs.size) {
        val a = (timesMs[i - 1] - t0).toDouble()
        val b = (timesMs[i] - t0).toDouble()
        if (b <= a || b - a > maxGapMs) continue
        val v = (speedsMps[i - 1] + speedsMps[i]) / 2.0
        for (k in (a / width).toInt().coerceAtMost(bins - 1)..(b / width).toInt().coerceAtMost(bins - 1)) {
            val overlap = min(b, (k + 1) * width) - max(a, k * width)
            if (overlap > 0) out[k] += (v * overlap / width).toFloat()
        }
    }
    return out
}
