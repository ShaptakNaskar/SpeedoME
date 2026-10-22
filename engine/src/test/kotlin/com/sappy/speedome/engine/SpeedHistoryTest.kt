package com.sappy.speedome.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SpeedHistoryTest {
    @Test
    fun `one sample a second that follows the speed, capped at two hours`() {
        val h = Harness()
        h.rig.gnss.hz = 10.0
        h.rig.cruiseMps = kmhToMps(80.0)
        var history = emptyList<SpeedSample>()
        repeat(600) { h.run(0.1); history = history.appended(h.state) }
        assertTrue("samples ${history.size} in 60 s", history.size in 58..61)
        assertTrue("last ${history.last().mps}", abs(history.last().mps - kmhToMps(80.0)) < 1.5)

        val old = List(8000) { SpeedSample(it * 1_000_000_000L, 1f) }
        val later = old.appended(EngineState(lastEventNanos = 8000 * 1_000_000_000L))
        assertEquals(SPEED_HISTORY_NANOS, later.last().tNanos - later.first().tNanos)
    }
}
