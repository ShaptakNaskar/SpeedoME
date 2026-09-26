package com.sappy.speedome.engine

import kotlin.math.max

/** Read-only projection of [EngineState] for screens and the notification. SI units throughout. */
data class TrackView(
    val speedMps: Double,
    val accelMps2: Double,
    val filterNanos: Long,
    val zero: Boolean,
    val source: SpeedSource,
    val quality: GpsQuality,
    val rawSpeedMps: Double?,
    val rejectTotal: Int,
    val distanceM: Double,
    /** When [distanceM] was last integrated (monotonic ns); null before the first measurement. */
    val distanceNanos: Long?,
    val movingS: Double,
    val elapsedS: Double,
    val maxMps: Double,
    val avgMovingMps: Double,
    val avgOverallMps: Double,
    val steps: Long,
    val cadenceSpm: Double,
    val paceSecPerKm: Double?,
    val sessionKind: SessionKind,
    val paused: Boolean,
    val segment: Int,
    val gaps: Int,
    val lastFix: LastFix?,
    /** Current dial maximum from auto-range (km/h). */
    val rangeKmh: Int,
    val target: TargetView? = null,
) {
    /**
     * What the needle chases at [nowNanos]: the filtered speed, extrapolated with the filter's
     * acceleration for up to [Tuning.PREDICT_MAX_S] so the needle glides between fixes (D25).
     */
    fun displayTargetMps(nowNanos: Long, predict: Boolean = true): Double {
        if (zero) return 0.0
        if (!predict) return speedMps
        val h = ((nowNanos - filterNanos) / 1e9).coerceIn(0.0, Tuning.PREDICT_MAX_S)
        return max(0.0, speedMps + accelMps2 * h)
    }

    /**
     * Distance at [nowNanos] for a rolling odometer: [distanceM] plus the ground covered since it was
     * last integrated, on the same prediction as the needle (speed + acceleration, at most
     * [Tuning.PREDICT_MAX_S], never below zero speed). The next fix lands on about the same value, so
     * a drum rolls continuously instead of stepping once per fix.
     */
    fun displayDistanceM(nowNanos: Long): Double {
        val since = distanceNanos ?: return distanceM
        if (paused || zero) return distanceM
        var h = ((nowNanos - since) / 1e9).coerceIn(0.0, Tuning.PREDICT_MAX_S)
        if (accelMps2 < 0) h = minOf(h, speedMps / -accelMps2) // braking to a stop within the window
        return distanceM + max(0.0, speedMps * h + accelMps2 * h * h / 2)
    }

    companion object {
        val EMPTY = EngineState().view(0)
    }
}

fun EngineState.view(nowNanos: Long): TrackView {
    val speed = filter.output
    val cadence = cadenceSpm(nowNanos)
    return TrackView(
        speedMps = speed,
        accelMps2 = if (filter.zero) 0.0 else filter.a,
        filterNanos = filter.tNanos,
        zero = filter.zero,
        source = lastSource,
        quality = quality(nowNanos),
        rawSpeedMps = lastFix?.rawSpeed,
        rejectTotal = filter.rejectTotal,
        distanceM = stats.distanceM,
        distanceNanos = lastMeasureNanos,
        movingS = stats.movingS,
        elapsedS = stats.elapsedS,
        maxMps = stats.maxMps,
        avgMovingMps = if (stats.movingS > 1) stats.distanceM / stats.movingS else 0.0,
        avgOverallMps = if (stats.elapsedS > 1) stats.distanceM / stats.elapsedS else 0.0,
        steps = stats.steps,
        cadenceSpm = cadence,
        paceSecPerKm = if (speed > 0.3) 1000.0 / speed else null,
        sessionKind = session.kind,
        paused = session.paused,
        segment = session.segment,
        gaps = stats.gaps,
        lastFix = lastFix,
        rangeKmh = range.maxKmh,
        target = targetView(nowNanos),
    )
}
