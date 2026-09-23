package com.sappy.speedome.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedFilterTest {
    private val second = 1_000_000_000L

    private fun feed(f: SpeedFilter, z: Double, times: Int, startS: Long, r: Double = 0.09): SpeedFilter =
        (0 until times).fold(f) { acc, i -> acc.update(z, r, (startS + i) * second).filter }

    @Test
    fun `converges on consistent readings`() {
        val f = feed(SpeedFilter(), 10.0, 20, 1)
        assertEquals(10.0, f.output, 0.05)
        assertEquals(0, f.rejectTotal)
    }

    @Test
    fun `rejects a single impossible reading`() {
        val settled = feed(SpeedFilter(), 10.0, 20, 1)
        val u = settled.update(55.6, 0.09, 21 * second) // a 200 km/h glitch
        assertFalse(u.accepted)
        assertEquals(10.0, u.filter.output, 0.3)
        assertEquals(1, u.filter.rejectTotal)
    }

    @Test
    fun `accepts a real jump after three consistent rejects`() {
        val parked = feed(SpeedFilter(), 0.0, 10, 1)
        var f = parked
        val accepted = (0 until 3).map { i ->
            val u = f.update(30.0, 0.09, (11 + i) * second)
            f = u.filter
            u.accepted
        }
        assertEquals(listOf(false, false, true), accepted)
        assertEquals(30.0, f.output, 1e-9)
    }

    @Test
    fun `zero clamp has hysteresis`() {
        var f = feed(SpeedFilter(), 0.3, 10, 1)
        assertEquals(0.0, f.output, 0.0)
        f = feed(f, 0.6, 10, 11) // below the 0.8 release threshold: still 0
        assertEquals(0.0, f.output, 0.0)
        f = feed(f, 1.0, 10, 21) // released
        assertTrue(f.output > 0.9)
        f = feed(f, 0.6, 10, 31) // above the 0.45 engage threshold: stays live
        assertEquals(0.6, f.output, 0.05)
        f = feed(f, 0.3, 10, 41)
        assertEquals(0.0, f.output, 0.0)
    }

    @Test
    fun `time never runs backwards`() {
        val f = feed(SpeedFilter(), 5.0, 5, 10)
        val u = f.update(5.0, 0.09, 12 * second) // older than the filter's last update
        assertEquals(14 * second, u.filter.tNanos)
    }
}
