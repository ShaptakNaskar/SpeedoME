package com.sappy.speedome.engine

import kotlinx.serialization.Serializable
import kotlin.math.max

/** How the dial shrinks back after you slow down (docs/plan.md §6). */
enum class ShrinkPolicy { WITH_DELAY, ONLY_GROW, IMMEDIATE, OFF }

data class AutoRangeSettings(
    val policy: ShrinkPolicy = ShrinkPolicy.WITH_DELAY,
    /** Dial maximum when [policy] is [ShrinkPolicy.OFF] (drive mode). */
    val fixedKmh: Int = 120,
)

@Serializable
data class RangeState(
    val index: Int = 0,
    val belowS: Double = 0.0,
    val lastNanos: Long? = null,
    val mode: Mode = Mode.DRIVE,
    val maxKmh: Int = AutoRange.DRIVE.first(),
)

/**
 * Dial range ladder. Grows at 90 % of the dial to the smallest range that fits where you'll be in
 * about 2 s; with the default policy it shrinks one step after 15 s below 80 % of the smaller range.
 */
object AutoRange {
    val DRIVE = listOf(20, 40, 60, 80, 120, 160, 200, 260, 320, 500, 1000)
    val STEP = listOf(10, 20)
    const val GROW_AT = 0.9
    const val SHRINK_BELOW = 0.8
    const val SHRINK_AFTER_S = 15.0
    const val LOOKAHEAD_S = 2.0

    fun ladder(mode: Mode) = if (mode == Mode.STEP) STEP else DRIVE

    fun initial(mode: Mode, settings: AutoRangeSettings) = RangeState(mode = mode, maxKmh = fixedOr(mode, settings, ladder(mode).first()))

    fun update(r: RangeState, speedKmh: Double, accelKmhS: Double, tNanos: Long, mode: Mode, settings: AutoRangeSettings): RangeState {
        val ladder = ladder(mode)
        val base = if (r.mode != mode) initial(mode, settings) else r
        val dt = base.lastNanos?.let { ((tNanos - it) / 1e9).coerceIn(0.0, 5.0) } ?: 0.0
        var idx = base.index.coerceIn(0, ladder.lastIndex)
        var below = base.belowS
        when (settings.policy) {
            ShrinkPolicy.OFF -> return base.copy(lastNanos = tNanos, mode = mode, maxKmh = fixedOr(mode, settings, ladder.last()))
            ShrinkPolicy.IMMEDIATE -> {
                idx = ladder.indexOfFirst { speedKmh < GROW_AT * it }.let { if (it < 0) ladder.lastIndex else it }
                below = 0.0
            }
            ShrinkPolicy.WITH_DELAY, ShrinkPolicy.ONLY_GROW -> {
                if (speedKmh >= GROW_AT * ladder[idx] && idx < ladder.lastIndex) {
                    val ahead = speedKmh + max(0.0, accelKmhS) * LOOKAHEAD_S
                    var i = idx + 1
                    while (i < ladder.lastIndex && ahead >= GROW_AT * ladder[i]) i++
                    idx = i
                    below = 0.0
                } else if (settings.policy == ShrinkPolicy.WITH_DELAY && idx > 0) {
                    if (speedKmh < SHRINK_BELOW * ladder[idx - 1]) {
                        below += dt
                        if (below >= SHRINK_AFTER_S) {
                            idx--
                            below = 0.0
                        }
                    } else {
                        below = 0.0
                    }
                }
            }
        }
        return RangeState(index = idx, belowS = below, lastNanos = tNanos, mode = mode, maxKmh = ladder[idx])
    }

    private fun fixedOr(mode: Mode, settings: AutoRangeSettings, fallback: Int): Int = when {
        settings.policy != ShrinkPolicy.OFF -> fallback
        mode == Mode.STEP -> STEP.last()
        else -> settings.fixedKmh
    }
}
