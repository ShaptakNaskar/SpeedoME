package com.sappy.speedome.engine

import kotlinx.serialization.Serializable

@Serializable
data class PosSample(val tNanos: Long, val lat: Double, val lon: Double, val hAcc: Double)

/** The most recent fix as the engine saw it; feeds the GPS dot, nerd readouts and gap bridging. */
@Serializable
data class LastFix(
    val tNanos: Long,
    val utcMillis: Long,
    val lat: Double,
    val lon: Double,
    val hAcc: Double,
    val altM: Double? = null,
    val vAcc: Double? = null,
    val bearing: Double? = null,
    val rawSpeed: Double? = null,
    val speedAcc: Double? = null,
    val source: SpeedSource = SpeedSource.NONE,
    val isMock: Boolean = false,
)

@Serializable
data class Session(
    val kind: SessionKind = SessionKind.LIVE,
    val paused: Boolean = false,
    val startedUtc: Long = 0,
    val segment: Int = 0,
)

@Serializable
data class Stats(
    val distanceM: Double = 0.0,
    val movingS: Double = 0.0,
    val elapsedS: Double = 0.0,
    val maxMps: Double = 0.0,
    val steps: Long = 0,
    val gaps: Int = 0,
)

@Serializable
data class CounterSample(val tNanos: Long, val total: Long)

@Serializable
data class StepState(
    val lastCounter: Long? = null,
    val detectorTimes: List<Long> = emptyList(),
    val counterSamples: List<CounterSample> = emptyList(),
    val lastStepNanos: Long? = null,
    val strideWalkM: Double = 0.74,
    val strideRunM: Double = 1.05,
)

/**
 * The engine's complete, serializable state. It doubles as the resume snapshot (docs/plan.md §7):
 * restoring it and calling [resumedAfter] continues a session exactly where it stopped.
 */
@Serializable
data class EngineState(
    val filter: SpeedFilter = SpeedFilter(),
    val recent: List<PosSample> = emptyList(),
    val lastFix: LastFix? = null,
    val lastGood: PosSample? = null,
    val lastMeasureNanos: Long? = null,
    val lastMeasureOut: Double = 0.0,
    val lastEventNanos: Long? = null,
    val lastSource: SpeedSource = SpeedSource.NONE,
    val session: Session = Session(),
    val stats: Stats = Stats(),
    val steps: StepState = StepState(),
    val range: RangeState = RangeState(),
)

/** GPS dot colour at [nowNanos]. */
fun EngineState.quality(nowNanos: Long): GpsQuality {
    val f = lastFix ?: return GpsQuality.NONE
    if ((nowNanos - f.tNanos) / 1e9 > Tuning.NO_FIX_S) return GpsQuality.NONE
    return if (f.source == SpeedSource.POSITION || f.hAcc > Tuning.FALLBACK_HACC) GpsQuality.FALLBACK else GpsQuality.GOOD
}

/** Steps per minute from the step detector, falling back to the step counter. 0 when not walking. */
fun EngineState.cadenceSpm(nowNanos: Long): Double {
    val window = Tuning.CADENCE_WINDOW_S
    val det = steps.detectorTimes.filter { it <= nowNanos && (nowNanos - it) / 1e9 <= window }
    if (det.size >= 2 && (nowNanos - det.last()) / 1e9 <= 2.5) {
        val span = (det.last() - det.first()) / 1e9
        if (span >= 1.0) return (det.size - 1) * 60.0 / span
    }
    val cs = steps.counterSamples.filter { it.tNanos <= nowNanos && (nowNanos - it.tNanos) / 1e9 <= window }
    val recentStep = steps.lastStepNanos?.let { (nowNanos - it) / 1e9 <= 3.0 } ?: false
    if (cs.size >= 2 && recentStep) {
        val span = (cs.last().tNanos - cs.first().tNanos) / 1e9
        if (span >= 2.0) return (cs.last().total - cs.first().total) * 60.0 / span
    }
    return 0.0
}

fun EngineState.strideFor(cadenceSpm: Double): Double =
    if (cadenceSpm < Tuning.RUN_CADENCE_SPM) steps.strideWalkM else steps.strideRunM

/**
 * Prepares a restored snapshot to continue after the process was dead for [gapMillis].
 * Works across reboots (the monotonic clock restarts) by re-basing stored times onto [nowNanos].
 * The gap is added to elapsed time; the next good fix bridges it with a straight line.
 */
fun EngineState.resumedAfter(gapMillis: Long, nowNanos: Long): EngineState {
    val then = nowNanos - gapMillis * 1_000_000L
    return copy(
        filter = SpeedFilter(rejectTotal = filter.rejectTotal),
        recent = emptyList(),
        lastFix = lastFix?.copy(tNanos = then),
        lastGood = lastGood?.copy(tNanos = then),
        lastMeasureNanos = lastMeasureNanos?.let { then },
        lastMeasureOut = 0.0,
        lastEventNanos = nowNanos,
        session = session.copy(segment = session.segment + 1), // the route resumes as a new, dashed-joined segment
        stats = if (session.paused) stats else stats.copy(elapsedS = stats.elapsedS + gapMillis / 1000.0),
        steps = steps.copy(detectorTimes = emptyList(), counterSamples = emptyList(), lastStepNanos = null),
    )
}
