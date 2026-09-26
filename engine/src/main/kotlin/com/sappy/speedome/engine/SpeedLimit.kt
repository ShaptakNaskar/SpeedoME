package com.sappy.speedome.engine

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Speed-limit warnings (docs/plan.md §6): gauges fade to red over the last 10 % below the limit, and
 * the phone buzzes once on reaching it, then pulses faster the further over you go. Pure maths; the
 * app does the drawing and the buzzing.
 */
object SpeedLimit {
    /** The warning fade starts at 90 % of the limit. */
    const val WARN_FROM = 0.9

    /**
     * With a limit set, every dial and bar is fixed at this multiple of it ([scaleMax]) instead of
     * auto-ranging, so the red zone is always in view; needles and bars stop at the end, digits don't.
     */
    const val SCALE_MAX = 1.25

    /** % over the limit → pulse rate: up to 5 % over pulses at 1 Hz, to 10 % at 2 Hz, to 20 % at 3 Hz. */
    private val BANDS = listOf(5.0 to 1, 10.0 to 2, 20.0 to 3)

    /** Beyond the last band. */
    const val MAX_HZ = 4

    /** The "reached" buzz re-arms only after dropping this far under, so GPS jitter at the limit can't repeat it. */
    const val REARM_BELOW = 0.97

    /** Pulsing continues down to here, so hovering right at the limit doesn't stutter on and off. */
    const val PULSE_UNTIL = 0.99

    /** Slowing down, a band is kept until the speed is this many percentage points below its lower edge. */
    const val BAND_HYSTERESIS = 1.0

    /** 0 below [WARN_FROM] of [limit], rising linearly to 1 at the limit and staying there above it. */
    fun warn(speed: Double, limit: Double): Float =
        if (limit <= 0) 0f else ((speed / limit - WARN_FROM) / (1 - WARN_FROM)).coerceIn(0.0, 1.0).toFloat()

    /** The fixed scale for [limit]: [SCALE_MAX] times it, rounded up to a multiple of 5 (50 → 65, 55 → 70, 100 → 125). */
    fun scaleMax(limit: Double): Int = (ceil(limit * SCALE_MAX / 5 - 1e-6) * 5).toInt()

    /** Pulse rate (Hz) at [percentOver] per cent above the limit. */
    fun hzFor(percentOver: Double): Int = BANDS.firstOrNull { percentOver <= it.first }?.second ?: MAX_HZ
}

/**
 * Vibration state for a speed limit. [hit] is true only on the update where the limit was reached;
 * [hz] is the pulse rate while over it (0 = quiet).
 */
data class LimitAlarm(val armed: Boolean = true, val hz: Int = 0, val hit: Boolean = false) {
    /**
     * The next state for [speed] against [limit], in the same units. No limit starts over. No speed (no
     * fix) goes quiet but stays un-armed: a GPS gap isn't evidence of having slowed down.
     */
    fun update(speed: Double?, limit: Double?): LimitAlarm {
        if (limit == null || limit <= 0) return LimitAlarm()
        if (speed == null) return copy(hz = 0, hit = false)
        val ratio = speed / limit
        val hit = armed && ratio >= 1.0
        val armed = when {
            hit -> false
            ratio < SpeedLimit.REARM_BELOW -> true
            else -> this.armed
        }
        val hz = when {
            ratio >= 1.0 -> {
                val pct = (ratio - 1) * 100
                val up = SpeedLimit.hzFor(pct)
                if (up >= hz) up else max(up, min(hz, SpeedLimit.hzFor(pct + SpeedLimit.BAND_HYSTERESIS)))
            }
            hz > 0 && ratio >= SpeedLimit.PULSE_UNTIL -> 1
            else -> 0
        }
        return LimitAlarm(armed, hz, hit)
    }
}
