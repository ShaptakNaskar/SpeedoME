package com.sappy.speedome.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SpeedHistoryTest {
    @Test
    fun `one sample a second that follows the speed, starting over with each session`() {
        val h = Harness()
        h.rig.gnss.hz = 10.0
        h.rig.cruiseMps = kmhToMps(80.0)
        var history = SpeedHistory()
        fun drive(seconds: Double) = repeat((seconds * 10).toInt()) { h.run(0.1); history = history.appended(h.state) }

        drive(60.0)
        assertTrue("samples ${history.samples.size} in 60 s", history.samples.size in 58..61)
        assertTrue("last ${history.samples.last().mps}", abs(history.samples.last().mps - kmhToMps(80.0)) < 1.5)

        h.command(Command.Reset)
        drive(2.5)
        assertTrue("after Reset ${history.samples.size}", history.samples.size in 1..3)
    }

    @Test
    fun `samples older than two hours drop off`() {
        val old = List(8000) { SpeedSample(it * 1_000_000_000L, 1f) }
        val later = old.appended(EngineState(lastEventNanos = 8000 * 1_000_000_000L))
        assertEquals(SPEED_HISTORY_NANOS, later.last().tNanos - later.first().tNanos)
    }
}
