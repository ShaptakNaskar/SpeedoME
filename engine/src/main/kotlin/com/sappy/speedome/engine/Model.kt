package com.sappy.speedome.engine

enum class Mode { DRIVE, STEP }

/** Where the current speed estimate came from (docs/plan.md §5). */
enum class SpeedSource { NONE, DOPPLER, DOPPLER_NO_ACCURACY, POSITION, STEPS }

/** Drives the GPS dot: green / amber / red. */
enum class GpsQuality { GOOD, FALLBACK, NONE }

enum class SessionKind { LIVE, TRIP }

/** User settings the engine needs. */
data class EngineSettings(
    val mode: Mode = Mode.DRIVE,
    val autoRange: AutoRangeSettings = AutoRangeSettings(),
)

/** Filter and bookkeeping constants, validated in docs/theme-lab.html. */
object Tuning {
    const val G = 9.81

    // Target arrival (docs/plan.md §6): trend = average over the last 180 s, stops included.
    const val TREND_WINDOW_S = 180.0

    /** Gap bridges implying more than 360 km/h are rejected as position jumps. */
    const val BRIDGE_MAX_MPS = 100.0
    const val TREND_MIN_S = 15.0
    const val TREND_MIN_MPS = 0.3

    // Kalman filter on [speed, acceleration]
    const val JERK_Q = 6.0
    const val ACCEL_DECAY_S = 3.0
    const val GATE_SIGMA = 4.0
    const val MAX_ACCEL = 1.5 * G
    const val RESET_STREAK = 3
    const val RESET_MIN_SPREAD = 2.0

    // Output snaps to 0 below ENTER and releases above EXIT (m/s)
    const val ZERO_ENTER = 0.45
    const val ZERO_EXIT = 0.8
    /** Consecutive updates (filter and reading both above EXIT) needed to leave zero. */
    const val ZERO_RELEASE_UPDATES = 2

    // Position-derived fallback speed
    const val POS_WINDOW_S = 3.2
    const val POS_MIN_SPAN_S = 1.5
    const val POS_STILL_FACTOR = 0.6
    const val DEFAULT_HACC = 10.0

    // Distance, gaps and moving time
    const val GAP_S = 3.2
    const val BRIDGE_MAX_HACC = 20.0
    const val MOVING_DRIVE_MPS = 2.0 / 3.6
    const val STEP_MOVING_WINDOW_S = 5.0

    // Display and quality
    const val PREDICT_MAX_S = 1.5
    const val NO_FIX_S = 3.0
    const val FALLBACK_HACC = 10.0

    // Steps
    const val CADENCE_WINDOW_S = 10.0
    const val RUN_CADENCE_SPM = 140.0
    const val STRIDE_LEARN_RATE = 0.06
    const val STILL_AFTER_S = 3.0
}
