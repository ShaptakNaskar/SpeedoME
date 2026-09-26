package com.sappy.speedome.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedLimitTest {
    @Test
    fun `the warning fades in over the last 10 percent`() {
        assertEquals(0f, SpeedLimit.warn(40.0, 50.0), 0f)
        assertEquals(0f, SpeedLimit.warn(45.0, 50.0), 1e-6f)
        assertEquals(.5f, SpeedLimit.warn(47.5, 50.0), 1e-6f)
        assertEquals(1f, SpeedLimit.warn(50.0, 50.0), 1e-6f)
        assertEquals(1f, SpeedLimit.warn(80.0, 50.0), 0f)
        assertEquals(0f, SpeedLimit.warn(80.0, 0.0), 0f)
    }

    @Test
    fun `the fixed scale is 125 percent of the limit, rounded up to a multiple of 5`() {
        assertEquals(65, SpeedLimit.scaleMax(50.0)) // 62.5
        assertEquals(70, SpeedLimit.scaleMax(55.0)) // 68.75
        assertEquals(125, SpeedLimit.scaleMax(100.0))
        assertEquals(75, SpeedLimit.scaleMax(60.0))
        assertEquals(40, SpeedLimit.scaleMax(30.0)) // 37.5
        assertEquals(40, SpeedLimit.scaleMax(31.1)) // 50 km/h shown in mph
        assertEquals(150, SpeedLimit.scaleMax(120.0))
    }

    @Test
    fun `pulse bands are 1 Hz to 5 percent over, then 2, 3 and 4 Hz`() {
        assertEquals(1, SpeedLimit.hzFor(0.0))
        assertEquals(1, SpeedLimit.hzFor(5.0))
        assertEquals(2, SpeedLimit.hzFor(5.1))
        assertEquals(2, SpeedLimit.hzFor(10.0))
        assertEquals(3, SpeedLimit.hzFor(15.0))
        assertEquals(3, SpeedLimit.hzFor(20.0))
        assertEquals(4, SpeedLimit.hzFor(25.0))
        assertEquals(4, SpeedLimit.hzFor(80.0))
    }

    /** Runs [speeds] through the alarm with a 50 limit; returns (hit, hz) per step. */
    private fun run(vararg speeds: Double?): List<Pair<Boolean, Int>> {
        var a = LimitAlarm()
        return speeds.map { v ->
            a = a.update(v, 50.0)
            a.hit to a.hz
        }
    }

    @Test
    fun `reaching the limit buzzes once, then the pulse follows how far over`() {
        val out = run(40.0, 49.0, 50.0, 51.0, 53.0, 56.0, 61.0, 70.0)
        assertEquals(listOf(false to 0, false to 0, true to 1, false to 1, false to 2, false to 3, false to 4, false to 4), out)
    }

    @Test
    fun `jitter at the limit neither repeats the buzz nor stutters`() {
        val out = run(50.2, 49.8, 50.1, 49.7, 50.3)
        assertEquals(1, out.count { it.first })
        assertTrue("keeps pulsing at 1 Hz: $out", out.all { it.second == 1 })
    }

    @Test
    fun `the buzz re-arms only after dropping clearly under`() {
        val out = run(51.0, 49.0, 50.5, 48.0, 50.5)
        assertEquals(listOf(true, false, false, false, true), out.map { it.first })
        assertEquals(0, out[1].second) // 98 % is under the limit: quiet
        assertEquals(0, out[3].second)
    }

    @Test
    fun `slowing down leaves a band only below its edge`() {
        val out = run(56.0, 54.9, 54.4, 52.4, 52.6)
        // 12 % over → 3 Hz; 9.8 % holds 3 Hz; 8.8 % drops to 2 Hz; 4.8 % holds 2 Hz (within a point of the 5 % edge).
        assertEquals(listOf(3, 3, 2, 2, 2), out.map { it.second })
    }

    @Test
    fun `losing the fix goes quiet without re-arming, turning the limit off starts over`() {
        var a = LimitAlarm().update(60.0, 50.0)
        assertTrue(a.hit)
        a = a.update(null, 50.0)
        assertEquals(0, a.hz)
        assertFalse(a.hit)
        a = a.update(60.0, 50.0)
        assertFalse("a GPS gap doesn't repeat the buzz", a.hit)
        assertEquals(3, a.hz) // 20 % over
        a = a.update(60.0, null)
        assertEquals(LimitAlarm(), a)
        a = a.update(60.0, 50.0)
        assertTrue("a new limit buzzes again", a.hit)
    }
}
