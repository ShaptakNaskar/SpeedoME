package com.sappy.speedome.engine

/** One point of the speed graph: the displayed speed at the engine's clock [tNanos]. */
data class SpeedSample(val tNanos: Long, val mps: Float)

/** How far back the speed graph reaches: its longest window, two hours. */
const val SPEED_HISTORY_NANOS = 2 * 3600 * 1_000_000_000L

/**
 * This history with [s]'s speed appended, at most once a second (the engine ticks at 1 Hz, so a
 * sample arrives every second the meter runs), dropping samples older than two hours. Time going
 * backwards (a restored snapshot on another clock) starts over.
 */
fun List<SpeedSample>.appended(s: EngineState): List<SpeedSample> {
    val t = s.lastEventNanos ?: return this
    val last = lastOrNull()
    if (last != null && t < last.tNanos) return listOf(SpeedSample(t, s.filter.output.toFloat()))
    if (last != null && t - last.tNanos < 1_000_000_000L) return this
    val cut = t - SPEED_HISTORY_NANOS
    return dropWhile { it.tNanos < cut } + SpeedSample(t, s.filter.output.toFloat())
}
