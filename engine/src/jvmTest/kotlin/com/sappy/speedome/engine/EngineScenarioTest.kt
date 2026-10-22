package com.sappy.speedome.engine

import com.sappy.speedome.engine.sim.SimPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class EngineScenarioTest {
    @Test
    fun `steady cruise tracks the true speed`() {
        val h = Harness()
        h.rig.cruiseMps = kmhToMps(60.0)
        h.run(60.0)
        val late = h.samplesAfter(30.0)
        val worst = late.maxOf { abs(it.outMps - it.trueMps) }.kmh()
        val mean = late.map { abs(it.outMps - it.trueMps) }.average().kmh()
        assertTrue("worst $worst km/h, mean $mean km/h", worst < 2.0 && mean < 0.7)
        assertEquals(0, h.state.filter.rejectTotal)
    }

    @Test
    fun `a 200 km per h spike is rejected and never becomes the max`() {
        val h = Harness()
        h.rig.cruiseMps = kmhToMps(60.0)
        h.run(40.0)
        h.rig.gnss.injectSpike()
        h.run(20.0)
        assertTrue("max ${h.view.maxMps.kmh()}", h.view.maxMps.kmh() < 66)
        assertTrue(h.state.filter.rejectTotal >= 1)
    }

    @Test
    fun `a 300 km per h train is real speed, not a glitch`() {
        val h = Harness()
        h.rig.preset = SimPreset.TRAIN
        h.run(260.0)
        assertTrue("max ${h.view.maxMps.kmh()}", h.view.maxMps.kmh() > 250)
        assertTrue("rejects ${h.state.filter.rejectTotal}", h.state.filter.rejectTotal <= 1)
    }

    @Test
    fun `a chip without a speed field still gives usable speed`() {
        val h = Harness()
        h.rig.gnss.doppler = false
        h.rig.cruiseMps = kmhToMps(60.0)
        h.run(60.0)
        val late = h.samplesAfter(30.0)
        val worst = late.maxOf { abs(it.outMps - it.trueMps) }.kmh()
        val mean = late.map { abs(it.outMps - it.trueMps) }.average().kmh()
        assertTrue("worst $worst mean $mean", worst < 6 && mean < 2.5)
        assertEquals(SpeedSource.POSITION, h.view.source)
        h.rig.cruiseMps = 0.0
        h.run(40.0)
        assertEquals(0.0, h.view.speedMps, 0.0)
    }

    @Test
    fun `a parked phone reads zero and the distance does not creep`() {
        val h = Harness()
        h.run(600.0)
        val moving = h.samplesAfter(5.0).filter { it.outMps != 0.0 }
        assertTrue("non-zero readings while parked: ${moving.take(5)}", moving.isEmpty())
        assertTrue("creep ${h.state.stats.distanceM} m", h.state.stats.distanceM < 5)
        assertEquals(0.0, h.state.stats.movingS, 0.0)
    }

    @Test
    fun `a tunnel gap is bridged with a straight line`() {
        val h = Harness()
        h.rig.cruiseMps = kmhToMps(60.0)
        h.run(60.0)
        h.rig.gnss.signal = false
        h.run(20.0)
        assertEquals(GpsQuality.NONE, h.view.quality)
        h.rig.gnss.signal = true
        h.run(30.0)
        val err = abs(h.state.stats.distanceM - h.rig.vehicle.odometerM) / h.rig.vehicle.odometerM
        assertTrue("distance error ${err * 100}%", err < 0.02)
        assertEquals(1, h.state.stats.gaps)
    }

    @Test
    fun `distance matches the truth over ten minutes of city driving`() {
        val h = Harness(seed = 11)
        h.rig.preset = SimPreset.CITY
        h.run(600.0)
        val err = abs(h.state.stats.distanceM - h.rig.vehicle.odometerM) / h.rig.vehicle.odometerM
        assertTrue("distance error ${err * 100}% of ${h.rig.vehicle.odometerM} m", err < 0.02)
        assertTrue(h.state.stats.movingS < h.state.stats.elapsedS)
    }

    @Test
    fun `ten fixes per second work`() {
        val h = Harness()
        h.rig.gnss.hz = 10.0
        h.rig.cruiseMps = kmhToMps(80.0)
        h.run(40.0)
        assertTrue(h.samples.size > 350)
        val late = h.samplesAfter(20.0)
        val worst = late.maxOf { abs(it.outMps - it.trueMps) }.kmh()
        val mean = late.map { abs(it.outMps - it.trueMps) }.average().kmh()
        assertTrue("worst $worst mean $mean", worst < 2.0 && mean < 0.7)
    }

    @Test
    fun `paused time and distance are not counted`() {
        val h = Harness()
        h.rig.cruiseMps = kmhToMps(50.0)
        h.run(30.0)
        val atPause = h.rig.vehicle.odometerM
        val distAtPause = h.state.stats.distanceM
        h.command(Command.Pause)
        h.run(60.0)
        assertEquals(distAtPause, h.state.stats.distanceM, 0.0)
        val atResume = h.rig.vehicle.odometerM
        h.command(Command.Resume)
        h.run(30.0)
        val expected = distAtPause + (h.rig.vehicle.odometerM - atResume)
        assertEquals(expected, h.state.stats.distanceM, expected * 0.03)
        assertEquals(60.0, h.state.stats.elapsedS, 1.5)
        assertTrue(atResume > atPause)
        assertEquals(1, h.view.segment)
    }

    @Test
    fun `starting and stopping a trip zeroes the meter`() {
        val h = Harness()
        h.rig.cruiseMps = kmhToMps(40.0)
        h.run(20.0)
        assertTrue(h.state.stats.distanceM > 100)
        h.command(Command.StartTrip)
        assertEquals(SessionKind.TRIP, h.view.sessionKind)
        assertEquals(0.0, h.state.stats.distanceM, 0.0)
        h.run(10.0)
        assertTrue(h.state.stats.distanceM > 80)
        h.command(Command.StopTrip)
        assertEquals(SessionKind.LIVE, h.view.sessionKind)
        assertEquals(0.0, h.state.stats.distanceM, 0.0)
        assertTrue("speed display survives", h.view.speedMps > 10)
    }

    @Test
    fun `standby forgets the live meter but never touches a trip`() {
        val h = Harness()
        h.rig.cruiseMps = kmhToMps(50.0)
        h.run(20.0)
        h.command(Command.SetTarget(5_000.0))
        val stride = h.state.steps.strideWalkM
        h.command(Command.Standby)
        assertEquals(SessionKind.LIVE, h.view.sessionKind)
        assertEquals(0.0, h.view.speedMps, 0.0)
        assertEquals(null, h.view.lastFix)
        assertEquals(0.0, h.view.distanceM, 0.0)
        assertEquals(null, h.view.target)
        assertEquals(AutoRange.DRIVE.first(), h.view.rangeKmh)
        assertEquals(stride, h.state.steps.strideWalkM, 0.0)

        h.run(10.0) // the app is open again: a clean live meter picks up
        assertTrue(h.view.speedMps > 10)
        h.command(Command.StartTrip)
        h.run(5.0)
        val trip = h.state
        h.command(Command.Standby)
        assertEquals(trip, h.state)
    }

    @Test
    fun `the odometer rolls smoothly between 1 Hz fixes`() {
        val h = Harness()
        h.rig.cruiseMps = kmhToMps(100.0)
        h.run(30.0)
        val frame = 0.02
        var shown = h.view.displayDistanceM(h.rig.nowNanos)
        var stored = h.view.distanceM
        var worstShown = 0.0
        var worstStored = 0.0
        repeat(500) { // 10 s of 50 Hz frames
            h.run(frame)
            val v = h.view
            val now = v.displayDistanceM(h.rig.nowNanos)
            worstShown = maxOf(worstShown, abs(now - shown - v.speedMps * frame))
            worstStored = maxOf(worstStored, v.distanceM - stored)
            shown = now
            stored = v.distanceM
        }
        assertTrue("stored distance steps once per fix: $worstStored m", worstStored > 20)
        assertTrue("shown distance never jumps: $worstShown m", worstShown < 0.5)
    }

    @Test
    fun `display target extrapolates only a little`() {
        val h = Harness()
        h.rig.manual = com.sappy.speedome.engine.sim.SimInput(throttle = true)
        h.run(6.0)
        val v = h.view
        val atFix = v.displayTargetMps(v.filterNanos)
        val later = v.displayTargetMps(v.filterNanos + 900_000_000L)
        val muchLater = v.displayTargetMps(v.filterNanos + 10_000_000_000L)
        assertTrue(later > atFix)
        assertEquals(v.speedMps + v.accelMps2 * Tuning.PREDICT_MAX_S, muchLater, 1e-9)
        assertEquals(v.speedMps, v.displayTargetMps(v.filterNanos + 900_000_000L, predict = false), 0.0)
    }
}
