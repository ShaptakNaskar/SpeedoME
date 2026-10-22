package com.sappy.speedome.engine

/**
 * Everything the engine reacts to. [tNanos] is a monotonic clock (Android's elapsedRealtimeNanos);
 * wall-clock time, where needed, travels separately as UTC milliseconds.
 */
sealed interface EngineEvent {
    val tNanos: Long
}

/** One GNSS fix. A null field means the chip did not provide it. Speeds in m/s, distances in metres. */
data class FixEvent(
    override val tNanos: Long,
    val utcMillis: Long,
    val lat: Double,
    val lon: Double,
    val hAcc: Float? = null,
    val altM: Double? = null,
    val vAcc: Float? = null,
    val speed: Float? = null,
    val speedAcc: Float? = null,
    val bearing: Float? = null,
    val bearingAcc: Float? = null,
    val isMock: Boolean = false,
) : EngineEvent

/** Hardware step counter reading: total steps since the phone booted. */
data class StepCountEvent(override val tNanos: Long, val counterTotal: Long) : EngineEvent

/** A single step from the low-latency step detector; drives the live cadence. */
data class StepDetectedEvent(override val tNanos: Long) : EngineEvent

/** Roughly 1 Hz housekeeping: advances time without fixes and feeds step-based speed. */
data class TickEvent(override val tNanos: Long, val utcMillis: Long) : EngineEvent

data class CommandEvent(override val tNanos: Long, val utcMillis: Long, val command: Command) : EngineEvent

sealed interface Command {
    /** Zero the live meter. */
    data object Reset : Command

    /** Begin a recorded trip at zero. */
    data object StartTrip : Command

    data object Pause : Command

    data object Resume : Command

    /** End the recorded trip (the app saves it first); a fresh live meter begins. */
    data object StopTrip : Command

    /** Aim for [distanceM] more metres from now, optionally arriving by [arriveByUtc] (docs/plan.md §6). */
    data class SetTarget(val distanceM: Double, val arriveByUtc: Long? = null) : Command

    data object ClearTarget : Command

    /**
     * Tracking stopped with no trip recording (the app was closed): the live meter ends and every
     * trace of it (filter, last fix, target) is dropped, so the next open starts clean. A recording
     * trip ignores it.
     */
    data object Standby : Command
}
