package com.sappy.speedome.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class TargetTest {
    private val cfg = EngineSettings()
    private val sec = 1_000_000_000L
    private val utc0 = 1_700_000_000_000L

    /** A deterministic trace: 1 Hz ticks with [distanceAt] forced into the stats (the filter is not under test). */
    private fun EngineState.at(tS: Int, distanceM: Double): EngineState =
        Engine.reduce(copy(stats = stats.copy(distanceM = distanceM)), TickEvent(tS * sec, utc0 + tS * 1000L), cfg)

    private fun EngineState.cmd(tS: Int, c: Command) = Engine.reduce(this, CommandEvent(tS * sec, utc0 + tS * 1000L, c), cfg)

    @Test
    fun `remaining, progress and arrival at a steady trend`() {
        var s = EngineState().at(0, 0.0).cmd(0, Command.SetTarget(10_000.0))
        for (t in 1..200) s = s.at(t, t * 20.0) // 20 m/s = 72 km/h
        val v = checkNotNull(s.targetView(200 * sec))
        assertEquals(4_000.0, v.coveredM, 1e-6)
        assertEquals(6_000.0, v.remainingM, 1e-6)
        assertEquals(.4f, v.progress, 1e-6f)
        assertEquals(20.0, checkNotNull(v.trendMps), 1e-6)
        // 6 km at 20 m/s = 300 s after now.
        assertEquals(utc0 + 200_000 + 300_000, v.etaUtc)
        assertFalse(v.arrived)
    }

    @Test
    fun `trend averages the last 180 s including a stop`() {
        var s = EngineState().at(0, 0.0).cmd(0, Command.SetTarget(50_000.0))
        var d = 0.0
        for (t in 1..300) {
            if (t <= 210 || t > 300) d += 20.0 // moving, then parked for the last 90 s
            s = s.at(t, d)
        }
        // Window 120..300: 90 s at 20 m/s + 90 s stopped = 10 m/s.
        assertEquals(10.0, s.trendMps(), .2)
    }

    @Test
    fun `short history uses current speed and a slow trend shows no estimate`() {
        var s = EngineState().at(0, 0.0).cmd(0, Command.SetTarget(1_000.0))
        for (t in 1..5) s = s.at(t, t * .1) // 0.1 m/s crawl, filter speed 0
        val v = checkNotNull(s.targetView(5 * sec))
        assertNull(v.etaUtc)
        assertNull(v.trendMps)
    }

    @Test
    fun `arrive-by gives needed average, ahead or behind, and LATE`() {
        var s = EngineState().at(0, 0.0).cmd(0, Command.SetTarget(12_000.0, arriveByUtc = utc0 + 600_000))
        for (t in 1..100) s = s.at(t, t * 20.0)
        val v = checkNotNull(s.targetView(100 * sec))
        // 10 km left, 500 s left → 20 m/s needed; ETA 10000/20 = 500 s → exactly on time.
        assertEquals(20.0, checkNotNull(v.neededMps), 1e-6)
        assertEquals(0L, v.aheadS)
        assertFalse(v.late)

        for (t in 101..700) s = s.at(t, 2_000.0 + (t - 100) * 10.0) // slows to 10 m/s, deadline passes at 600 s
        val late = checkNotNull(s.targetView(700 * sec))
        assertTrue(late.late)
        assertNull(late.neededMps)
    }

    @Test
    fun `behind schedule is negative`() {
        var s = EngineState().at(0, 0.0).cmd(0, Command.SetTarget(10_000.0, arriveByUtc = utc0 + 400_000))
        for (t in 1..100) s = s.at(t, t * 10.0)
        val v = checkNotNull(s.targetView(100 * sec))
        // 9 km at 10 m/s = 900 s; deadline in 300 s → 600 s behind.
        assertEquals(-600L, v.aheadS)
    }

    @Test
    fun `arrival reports distance past the target`() {
        var s = EngineState().at(0, 500.0).cmd(0, Command.SetTarget(1_000.0)) // counted from 500 m
        for (t in 1..80) s = s.at(t, 500.0 + t * 15.0)
        val v = checkNotNull(s.targetView(80 * sec))
        assertTrue(v.arrived)
        assertEquals(200.0, v.pastM, 1e-6)
        assertEquals(1f, v.progress)
        assertNull(v.etaUtc)
    }

    @Test
    fun `target survives a new session and counts from zero, and can be cleared`() {
        var s = EngineState().at(0, 0.0)
        for (t in 1..10) s = s.at(t, t * 10.0)
        s = s.cmd(10, Command.SetTarget(5_000.0)).cmd(11, Command.StartTrip)
        val v = checkNotNull(s.targetView(11 * sec))
        assertEquals(0.0, v.coveredM, 1e-6)
        assertEquals(5_000.0, v.remainingM, 1e-6)
        assertNull(s.cmd(12, Command.ClearTarget).targetView(12 * sec))
    }

    @Test
    fun `auto-range works in display units`() {
        fun rangeAt(units: Double): Int {
            val c = EngineSettings(unitsPerMps = units)
            var s = EngineState()
            // 100 km/h = 27.8 m/s = 62 mph.
            for (t in 0..30) s = Engine.reduce(s, FixEvent(t * sec, utc0 + t * 1000L, 0.0, t * 2.5e-4, 4f, speed = 27.78f, speedAcc = .3f), c)
            return s.range.maxKmh
        }
        assertEquals(120, rangeAt(EngineSettings.KMH_PER_MPS))
        assertEquals(80, rangeAt(EngineSettings.MPH_PER_MPS))
    }

    @Test
    fun `setting a target keeps the auto-range dial`() {
        var s = EngineState()
        for (t in 0..30) s = Engine.reduce(s, FixEvent(t * sec, utc0 + t * 1000L, 0.0, t * 3e-4, 4f, speed = 33f, speedAcc = .3f), cfg)
        val before = s.range
        s = s.cmd(31, Command.SetTarget(1_000.0))
        assertEquals(before, s.range)
    }

    @Test
    fun `target round-trips through the snapshot and resume clears the trend`() {
        var s = EngineState().at(0, 0.0).cmd(0, Command.SetTarget(3_000.0, utc0 + 999_000))
        for (t in 1..60) s = s.at(t, t * 12.0)
        assertEquals(s, Snapshot.decodeOrNull(Snapshot.encode(s)))
        val resumed = s.resumedAfter(60_000, 500 * sec)
        assertNotNull(resumed.target)
        assertTrue(resumed.trend.isEmpty())
        assertTrue(abs(checkNotNull(resumed.targetView(500 * sec)).coveredM - 720.0) < 1e-6)
    }
}
