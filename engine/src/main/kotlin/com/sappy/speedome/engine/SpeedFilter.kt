package com.sappy.speedome.engine

import com.sappy.speedome.engine.Tuning.ACCEL_DECAY_S
import com.sappy.speedome.engine.Tuning.GATE_SIGMA
import com.sappy.speedome.engine.Tuning.JERK_Q
import com.sappy.speedome.engine.Tuning.MAX_ACCEL
import com.sappy.speedome.engine.Tuning.RESET_MIN_SPREAD
import com.sappy.speedome.engine.Tuning.RESET_STREAK
import com.sappy.speedome.engine.Tuning.ZERO_ENTER
import com.sappy.speedome.engine.Tuning.ZERO_EXIT
import com.sappy.speedome.engine.Tuning.ZERO_RELEASE_UPDATES
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Kalman filter over [speed, acceleration] with an innovation gate, a physical acceleration gate,
 * reject-streak recovery and a hysteresis zero clamp. Immutable: every update returns a new filter.
 */
@Serializable
data class SpeedFilter(
    val v: Double = 0.0,
    val a: Double = 0.0,
    val p00: Double = 25.0,
    val p01: Double = 0.0,
    val p10: Double = 0.0,
    val p11: Double = 4.0,
    val tNanos: Long = 0,
    val initialized: Boolean = false,
    val rejectStreak: List<Double> = emptyList(),
    val rejectTotal: Int = 0,
    val zero: Boolean = true,
    val releaseCount: Int = 0,
) {
    /** Speed to show and integrate: exactly 0 while the zero clamp is engaged. */
    val output: Double get() = if (zero) 0.0 else v

    data class Update(val filter: SpeedFilter, val accepted: Boolean)

    fun predicted(dtS: Double): SpeedFilter {
        val e = exp(-dtS / ACCEL_DECAY_S)
        val f00 = p00 + dtS * p10
        val f01 = p01 + dtS * p11
        val f10 = e * p10
        val f11 = e * p11
        return copy(
            v = v + a * dtS,
            a = a * e,
            p00 = f00 + dtS * f01 + JERK_Q * dtS * dtS * dtS / 3,
            p01 = e * f01 + JERK_Q * dtS * dtS / 2,
            p10 = f10 + dtS * f11 + JERK_Q * dtS * dtS / 2,
            p11 = e * f11 + JERK_Q * dtS,
        )
    }

    /** Offers measurement [z] (m/s) with variance [r] taken at [t]. */
    fun update(z: Double, r: Double, t: Long): Update {
        if (!initialized) return Update(reset(z, r, t, rejectTotal), true)
        val dt = max(1e-3, (t - tNanos) / 1e9)
        val vPrev = v
        val p = predicted(dt).copy(tNanos = max(t, tNanos)) // time never runs backwards
        val y = z - p.v
        val s = p.p00 + r
        val sigmas = abs(y) / sqrt(s)
        val impliedAccel = abs(z - vPrev) / dt
        if (sigmas > GATE_SIGMA || impliedAccel > MAX_ACCEL) {
            val streak = (p.rejectStreak + z).takeLast(RESET_STREAK)
            val consistent = streak.size >= RESET_STREAK &&
                streak.max() - streak.min() <= max(RESET_MIN_SPREAD, 3 * sqrt(r))
            if (consistent) return Update(p.reset(z, r, p.tNanos, p.rejectTotal + 1), true)
            return Update(p.copy(rejectStreak = streak, rejectTotal = p.rejectTotal + 1).clampZero(null), false)
        }
        val k0 = p.p00 / s
        val k1 = p.p10 / s
        return Update(
            p.copy(
                v = max(0.0, p.v + k0 * y),
                a = p.a + k1 * y,
                p00 = (1 - k0) * p.p00,
                p01 = (1 - k0) * p.p01,
                p10 = p.p10 - k1 * p.p00,
                p11 = p.p11 - k1 * p.p01,
                rejectStreak = emptyList(),
            ).clampZero(z),
            true,
        )
    }

    /** A (re)initialisation is strong evidence, so it sets the zero clamp directly. */
    private fun reset(z: Double, r: Double, t: Long, rejects: Int) = copy(
        v = z, a = 0.0, p00 = r + 1, p01 = 0.0, p10 = 0.0, p11 = 4.0,
        tNanos = t, initialized = true, rejectStreak = emptyList(), rejectTotal = rejects,
        zero = z < ZERO_EXIT, releaseCount = 0,
    )

    /**
     * Hysteresis zero clamp: engages below [ZERO_ENTER]; releases only after [ZERO_RELEASE_UPDATES]
     * consecutive updates where both the estimate and the reading [z] exceed [ZERO_EXIT],
     * so a parked phone never flickers off zero on a noisy reading.
     */
    private fun clampZero(z: Double?): SpeedFilter = when {
        zero && v >= ZERO_EXIT && z != null && z >= ZERO_EXIT ->
            if (releaseCount + 1 >= ZERO_RELEASE_UPDATES) copy(zero = false, releaseCount = 0) else copy(releaseCount = releaseCount + 1)
        zero -> copy(releaseCount = 0)
        v < ZERO_ENTER -> copy(zero = true, releaseCount = 0)
        else -> this
    }
}
