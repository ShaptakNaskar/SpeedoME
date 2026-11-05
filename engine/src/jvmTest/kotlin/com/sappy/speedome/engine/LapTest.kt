package com.sappy.speedome.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LapTest {
    @Test
    fun `a section counts from its start, keeps its own max and leaves the trip alone`() {
        val h = Harness()
        h.command(Command.StartLap)
        assertNull("no section on the live meter", h.state.lap)

        h.command(Command.StartTrip)
        h.rig.cruiseMps = kmhToMps(90.0)
        h.run(60.0)
        h.rig.cruiseMps = kmhToMps(50.0)
        h.run(30.0) // slowed down before the section starts
        h.command(Command.StartLap)
        val before = h.state.stats
        h.run(60.0)

        val lap = checkNotNull(h.state.lapStats())
        val trip = h.state.stats
        assertEquals(trip.distanceM - before.distanceM, lap.distanceM, 1e-6)
        assertEquals(trip.elapsedS - before.elapsedS, lap.elapsedS, 1e-6)
        assertTrue("lap max ${lap.maxMps.kmh()}", lap.maxMps.kmh() < 60) // the 90 km/h before it doesn't count
        assertTrue("trip max ${trip.maxMps.kmh()}", trip.maxMps.kmh() > 85)
        assertEquals(lap.distanceM, h.view.lapDistanceM!!, 1e-6)

        h.command(Command.EndLap)
        assertNull(h.state.lap)
        assertEquals(trip.distanceM, h.state.stats.distanceM, 0.0) // ending a section does not touch the trip
        h.run(5.0)
        assertTrue(h.state.stats.distanceM > trip.distanceM)

        h.command(Command.StartLap)
        assertNotNull(h.state.lap)
        h.command(Command.StopTrip)
        assertNull("a new session drops the section", h.state.lap)
    }
}
