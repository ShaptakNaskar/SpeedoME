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
    )
}
