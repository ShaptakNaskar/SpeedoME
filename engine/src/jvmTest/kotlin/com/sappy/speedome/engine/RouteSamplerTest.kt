package com.sappy.speedome.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteSamplerTest {
    @Test
    fun `10 Hz fixes become about one route point a second, and none while parked`() {
        val h = Harness()
        h.rig.gnss.hz = 10.0
        h.rig.cruiseMps = kmhToMps(60.0)
        val sampler = RouteSampler()
        var taken = 0
        fun drive(seconds: Double) = repeat((seconds * 10).toInt()) { h.run(0.1); if (sampler.take(h.state) != null) taken++ }

        drive(120.0)
        assertTrue("taken $taken in 120 s", taken in 110..130) // not the 1,200 fixes

        h.rig.cruiseMps = 0.0
        drive(30.0) // come to a stop
        val atStop = taken
        drive(60.0)
        assertTrue("parked added ${taken - atStop}", taken - atStop <= 2)
    }

    @Test
    fun `thinning keeps the start, the end and segment boundaries`() {
        data class P(val i: Int, val seg: Int)
        val route = (0 until 11).map { P(it, if (it < 6) 0 else 1) }
        val thin = route.thinned { it.seg }
        assertEquals(listOf(0, 2, 4, 5, 6, 8, 10), thin.map { it.i })
    }
}
