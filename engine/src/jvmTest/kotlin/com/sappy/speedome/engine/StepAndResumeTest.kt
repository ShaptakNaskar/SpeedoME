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
    fun `walking keeps one speed source instead of alternating`() {
        val h = Harness()
        h.rig.preset = SimPreset.WALK
        h.run(60.0)
        val withDoppler = (1..30).map { h.run(1.0); h.view.source }
        assertTrue("sources $withDoppler", withDoppler.all { it == SpeedSource.DOPPLER })

        h.rig.gnss.doppler = false // position-only chip: steps should lead, not position differencing
        h.run(10.0)
        val noDoppler = (1..30).map { h.run(1.0); h.view.source }
        assertTrue("sources $noDoppler", noDoppler.all { it == SpeedSource.STEPS })
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
    fun `detector steps count live and a late batched counter report corrects them`() {
        val cfg = EngineSettings(mode = Mode.STEP)
        var s = Engine.reduce(EngineState(), StepCountEvent(1_000_000_000L, 10_000), cfg) // baseline at registration
        // 40 detector steps; the counter (batched, like many Qualcomm phones) stays silent meanwhile.
        for (i in 1..40) s = Engine.reduce(s, StepDetectedEvent(1_000_000_000L + i * 500_000_000L), cfg)
        assertEquals(40L, s.stats.steps)
        // Minutes later the counter reports 43 steps: the exact figure wins.
        s = Engine.reduce(s, StepCountEvent(30_000_000_000L, 10_043), cfg)
        assertEquals(43L, s.stats.steps)
        // Detector steps keep counting on top of the corrected total.
        s = Engine.reduce(s, StepDetectedEvent(31_000_000_000L), cfg)
        assertEquals(44L, s.stats.steps)
    }

    @Test
    fun `steps taken while paused are not counted after the counter catches up`() {
        val cfg = EngineSettings(mode = Mode.STEP)
        var s = Engine.reduce(EngineState(), StepCountEvent(1_000_000_000L, 500), cfg)
        for (i in 1..10) s = Engine.reduce(s, StepDetectedEvent(1_000_000_000L + i * 500_000_000L), cfg)
        s = Engine.reduce(s, CommandEvent(7_000_000_000L, 7_000L, Command.Pause), cfg)
        for (i in 1..20) s = Engine.reduce(s, StepDetectedEvent(7_000_000_000L + i * 500_000_000L), cfg)
        s = Engine.reduce(s, CommandEvent(20_000_000_000L, 20_000L, Command.Resume), cfg)
        s = Engine.reduce(s, StepCountEvent(21_000_000_000L, 530), cfg) // covers the paused steps too: new baseline
        assertEquals(10L, s.stats.steps)
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
    fun `a resume gap that teleports is counted as a gap without distance`() {
        val cfg = EngineSettings()
        val before = listOf(
            FixEvent(1_000_000_000L, 1_000L, 35.68, 139.76, hAcc = 4f, speed = 0f, speedAcc = 0.3f),
            FixEvent(2_000_000_000L, 2_000L, 35.68, 139.76, hAcc = 4f, speed = 0f, speedAcc = 0.3f),
        )
        val s1 = Engine.reduceAll(EngineState(), before, cfg)
        val resumed = s1.resumedAfter(gapMillis = 120_000, nowNanos = 200_000_000_000L)
        // Two minutes later the next fix is in Berlin (a mock source took over): 8900 km in 2 min.
        val s2 = Engine.reduce(resumed, FixEvent(201_000_000_000L, 122_000L, 52.5163, 13.3777, hAcc = 4f, speed = 0f, speedAcc = 0.3f), cfg)
        assertEquals(1, s2.stats.gaps)
        assertTrue("distance ${s2.stats.distanceM}", s2.stats.distanceM < 1.0)
        // Tracking continues normally from the new position.
        val s3 = Engine.reduce(s2, FixEvent(202_000_000_000L, 123_000L, 52.51639, 13.3777, hAcc = 4f, speed = 10f, speedAcc = 0.3f), cfg)
        assertTrue(s3.stats.distanceM < 20.0)
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
