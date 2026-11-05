package com.sappy.speedome.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedProfileTest {
    @Test
    fun averagesEachSliceAndShowsStopsAsZero() {
        // 100 s at 20 m/s, parked 100 s (no fixes), 100 s at 10 m/s; 1 Hz.
        val times = (0..100).map { it * 1000L } + (200..300).map { it * 1000L }
        val speeds = times.map { if (it <= 100_000) 20f else 10f }
        val bins = speedProfile(times.toLongArray(), speeds.toFloatArray(), bins = 30)
        assertEquals(30, bins.size)
        bins.slice(0..8).forEach { assertEquals(20f, it, 1e-3f) }
        bins.slice(11..18).forEach { assertEquals(0f, it, 1e-3f) }
        bins.slice(21..29).forEach { assertEquals(10f, it, 1e-3f) }
        // The area under the graph is the distance driven: 2000 m + 1000 m.
        assertEquals(3000.0, bins.sumOf { it * 10.0 }, 1.0)
    }

    @Test
    fun finerThanTheFixesStillHasNoHoles() {
        val times = LongArray(11) { it * 1000L }
        val bins = speedProfile(times, FloatArray(11) { 5f }, bins = 100)
        assertTrue(bins.all { kotlin.math.abs(it - 5f) < 1e-3f })
    }
}
