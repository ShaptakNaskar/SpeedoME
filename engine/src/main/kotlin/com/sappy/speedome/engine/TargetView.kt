package com.sappy.speedome.engine

/**
 * Target progress and arrival at one instant (docs/plan.md §6). Times are UTC milliseconds.
 * [aheadS] is positive when the estimate beats the arrive-by time.
 */
data class TargetView(
    val targetM: Double,
    val coveredM: Double,
    val remainingM: Double,
    val progress: Float,
    val arrived: Boolean,
    val pastM: Double,
    val trendMps: Double?,
    val etaUtc: Long?,
    val arriveByUtc: Long?,
    val neededMps: Double?,
    val aheadS: Long?,
    val late: Boolean,
)

/**
 * The trend speed: distance over the trend window (stops included) once it spans [Tuning.TREND_MIN_S];
 * before that, the current filtered speed.
 */
fun EngineState.trendMps(): Double {
    val first = trend.firstOrNull()
    val last = trend.lastOrNull()
    if (first != null && last != null) {
        val span = (last.tNanos - first.tNanos) / 1e9
        if (span >= Tuning.TREND_MIN_S) return (last.distanceM - first.distanceM) / span
    }
    return filter.output
}

fun EngineState.targetView(nowNanos: Long): TargetView? {
    val t = target ?: return null
    val covered = (stats.distanceM - t.startDistanceM).coerceAtLeast(0.0)
    val remaining = t.distanceM - covered
    val arrived = remaining <= 0
    val nowUtc = clock?.utcAt(nowNanos)
    val trend = trendMps()
    val eta = if (arrived || nowUtc == null || trend < Tuning.TREND_MIN_MPS) null else nowUtc + (remaining / trend * 1000).toLong()
    val by = t.arriveByUtc
    val leftS = if (by != null && nowUtc != null) (by - nowUtc) / 1000.0 else null
    return TargetView(
        targetM = t.distanceM,
        coveredM = covered,
        remainingM = remaining.coerceAtLeast(0.0),
        progress = if (t.distanceM > 0) (covered / t.distanceM).toFloat().coerceIn(0f, 1f) else 1f,
        arrived = arrived,
        pastM = if (arrived) -remaining else 0.0,
        trendMps = trend.takeIf { it >= Tuning.TREND_MIN_MPS },
        etaUtc = eta,
        arriveByUtc = by,
        neededMps = if (!arrived && leftS != null && leftS > 0) remaining / leftS else null,
        aheadS = if (!arrived && by != null && eta != null) (by - eta) / 1000 else null,
        late = !arrived && leftS != null && leftS <= 0,
    )
}
