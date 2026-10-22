package com.sappy.speedome.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitsTest {
    @Test
    fun `one hundred km per hour round-trips through m per s`() {
        val mps = Units.kmhToMps(100.0)
        assertEquals(27.7778, mps, 1e-4)
        assertEquals(100.0, Units.mpsToKmh(mps), 1e-9)
    }

    @Test
    fun `m per s converts to mph`() {
        assertEquals(60.0, Units.mpsToMph(26.8224), 1e-3)
    }
}
