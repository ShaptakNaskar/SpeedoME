package com.sappy.speedome.engine

import com.sappy.speedome.engine.sim.SimInput
import com.sappy.speedome.engine.sim.SimPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class StepAndResumeTest {
    @Test
    fun `step mode counts steps, learns stride and falls back to steps without GPS`() {
        val h = Harness()
        h.rig.preset = SimPreset.WALK
        h.run(120.0)
        assertEquals(Mode.STEP, h.settings.mode)
        assertTrue("steps ${h.state.stats.steps}", h.state.stats.steps in 150..260)
        assertTrue("cadence ${h.view.cadenceSpm}", h.view.cadenceSpm in 95.0..130.0)

        h.rig.gnss.signal = false
        h.run(60.0)
        assertEquals(SpeedSource.STEPS, h.view.source)
        val err = abs(h.view.speedMps - h.rig.vehicle.v).kmh()
        assertTrue("step speed error $err km/h", err < 1.5)

        h.rig.preset = null
        h.rig.manual = SimInput()
        h.run(20.0)
        assertEquals(0.0, h.view.speedMps, 0.0)
    }

    @Test
    fun `step counter handles a reboot reset`() {
        val cfg = EngineSettings(mode = Mode.STEP)
        var s = EngineState()
        s = Engine.reduce(s, StepCountEvent(1_000_000_000L, 5_000), cfg) // baseline
        s = Engine.reduce(s, StepCountEvent(2_000_000_000L, 5_012), cfg)
        s = Engine.reduce(s, StepCountEvent(3_000_000_000L, 3), cfg) // counter restarted
        s = Engine.reduce(s, StepCountEvent(4_000_000_000L, 10), cfg)
        assertEquals(19L, s.stats.steps)
    }

    @Test
    fun `snapshot round-trips exactly`() {
        val h = Harness()
        h.rig.preset = SimPreset.CITY
        h.run(90.0)
        val text = Snapshot.encode(h.state)
        assertEquals(h.state, Snapshot.decodeOrNull(text))
        assertEquals(null, Snapshot.decodeOrNull("{not json"))
    }

    @Test
    fun `resuming after the app was killed bridges the gap`() {
        val h = Harness()
        h.rig.cruiseMps = kmhToMps(60.0)
        h.run(60.0)
        val saved = Snapshot.encode(h.state)
        h.runUnseen(20.0) // process dead; the car keeps going
        val restored = checkNotNull(Snapshot.decodeOrNull(saved))
        h.state = restored.resumedAfter(gapMillis = 20_000, nowNanos = h.rig.nowNanos)
        h.run(30.0)
        val err = abs(h.state.stats.distanceM - h.rig.vehicle.odometerM) / h.rig.vehicle.odometerM
        assertTrue("distance error ${err * 100}%", err < 0.02)
        assertEquals(1, h.state.stats.gaps)
        assertEquals(110.0, h.state.stats.elapsedS, 2.0)
        assertEquals(1, h.state.session.segment)
    }

    @Test
    fun `resuming after a reboot re-bases the clock`() {
        val cfg = EngineSettings()
        val s0 = EngineState()
        val before = listOf(
            FixEvent(500_000_000_000L, 1_000_000L, 35.0, 139.0, hAcc = 4f, speed = 10f, speedAcc = 0.4f),
            FixEvent(501_000_000_000L, 1_001_000L, 35.00009, 139.0, hAcc = 4f, speed = 10f, speedAcc = 0.4f),
        )
        val s1 = Engine.reduceAll(s0, before, cfg)
        // After a reboot the monotonic clock restarts near zero.
        val resumed = s1.resumedAfter(gapMillis = 30_000, nowNanos = 5_000_000_000L)
        val after = FixEvent(6_000_000_000L, 1_032_000L, 35.0045, 139.0, hAcc = 4f, speed = 9f, speedAcc = 0.4f)
        val s2 = Engine.reduce(resumed, after, cfg)
        assertEquals(1, s2.stats.gaps)
        assertTrue("bridged ${s2.stats.distanceM}", s2.stats.distanceM > 480)
        assertTrue(s2.stats.elapsedS >= 31.0)
    }
}
