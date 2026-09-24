package com.sappy.speedome.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoRangeTest {
    private val sec = 1_000_000_000L
    private val delay = AutoRangeSettings(ShrinkPolicy.WITH_DELAY)

    /** Feeds a constant speed once per second for [seconds]. */
    private fun hold(
        start: RangeState,
        kmh: Double,
        seconds: Int,
        fromS: Long,
        settings: AutoRangeSettings = delay,
        mode: Mode = Mode.DRIVE,
        accel: Double = 0.0,
    ): RangeState = (0 until seconds).fold(start) { r, i -> AutoRange.update(r, kmh, accel, (fromS + i) * sec, mode, settings) }

    @Test
    fun `grows at 90 percent of the dial`() {
        val r0 = AutoRange.initial(Mode.DRIVE, delay)
        assertEquals(20, hold(r0, 17.9, 1, 0).maxKmh)
        assertEquals(40, hold(r0, 18.0, 1, 0).maxKmh)
    }

    @Test
    fun `hard acceleration skips a step`() {
        val r = AutoRange.update(AutoRange.initial(Mode.DRIVE, delay), 19.0, 15.0, 0, Mode.DRIVE, delay)
        assertEquals(60, r.maxKmh) // 19 + 15 x 2 s = 49 km/h fits 0–60 but not 0–40
    }

    @Test
    fun `shrinks one step after 15 s well below the smaller range`() {
        var r = hold(AutoRange.initial(Mode.DRIVE, delay), 75.0, 3, 0) // t = 0..2 s
        assertEquals(120, r.maxKmh) // 75 >= 72 (90 % of 80), so it jumps straight to 0–120
        r = hold(r, 60.0, 14, 3) // t = 3..16: below 80 % of 80 = 64 for 14 s
        assertEquals(120, r.maxKmh)
        r = hold(r, 60.0, 1, 17) // 15 s
        assertEquals(80, r.maxKmh)
        r = hold(r, 60.0, 30, 18) // 60 is not below 80 % of 60 = 48: stays
        assertEquals(80, r.maxKmh)
    }

    @Test
    fun `a brief dip does not shrink the dial`() {
        var r = hold(AutoRange.initial(Mode.DRIVE, delay), 100.0, 2, 0)
        assertEquals(120, r.maxKmh)
        r = hold(r, 30.0, 10, 2)
        r = hold(r, 70.0, 1, 12) // back above 64 resets the countdown
        r = hold(r, 30.0, 10, 13)
        assertEquals(120, r.maxKmh)
    }

    @Test
    fun `only grow keeps the biggest dial`() {
        val s = AutoRangeSettings(ShrinkPolicy.ONLY_GROW)
        var r = hold(AutoRange.initial(Mode.DRIVE, s), 150.0, 2, 0, s)
        assertEquals(200, r.maxKmh)
        r = hold(r, 10.0, 120, 2, s)
        assertEquals(200, r.maxKmh)
    }

    @Test
    fun `immediate always uses the smallest fitting dial`() {
        val s = AutoRangeSettings(ShrinkPolicy.IMMEDIATE)
        val r0 = AutoRange.initial(Mode.DRIVE, s)
        assertEquals(80, hold(r0, 55.0, 1, 0, s).maxKmh)
        assertEquals(60, hold(r0, 53.0, 1, 0, s).maxKmh)
        assertEquals(1000, hold(r0, 950.0, 1, 0, s).maxKmh)
    }

    @Test
    fun `off pins a fixed dial`() {
        val s = AutoRangeSettings(ShrinkPolicy.OFF, fixedKmh = 160)
        assertEquals(160, hold(AutoRange.initial(Mode.DRIVE, s), 250.0, 3, 0, s).maxKmh)
        assertEquals(20, hold(AutoRange.initial(Mode.STEP, s), 3.0, 3, 0, s, Mode.STEP).maxKmh)
    }

    @Test
    fun `step mode uses 0-10 growing to 0-20 and never beyond`() {
        val r0 = AutoRange.initial(Mode.STEP, delay)
        assertEquals(10, r0.maxKmh)
        assertEquals(10, hold(r0, 5.0, 3, 0, mode = Mode.STEP).maxKmh)
        assertEquals(20, hold(r0, 11.0, 3, 0, mode = Mode.STEP).maxKmh)
        assertEquals(20, hold(r0, 40.0, 3, 0, mode = Mode.STEP).maxKmh)
    }

    @Test
    fun `the engine tracks the range and a new session starts small`() {
        val h = Harness()
        h.rig.cruiseMps = kmhToMps(100.0)
        h.run(40.0)
        assertEquals(120, h.view.rangeKmh)
        h.command(Command.Reset)
        assertEquals(20, h.view.rangeKmh)
        h.run(3.0)
        assertEquals(120, h.view.rangeKmh)
    }
}
