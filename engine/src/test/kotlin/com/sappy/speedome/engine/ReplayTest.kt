package com.sappy.speedome.engine

import com.sappy.speedome.engine.replay.Gpx
import com.sappy.speedome.engine.replay.Nmea
import com.sappy.speedome.engine.replay.RawLog
import com.sappy.speedome.engine.replay.Replay
import com.sappy.speedome.engine.sim.SimRig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ReplayTest {
    private fun simFixes(seconds: Double, kmh: Double = 50.0): Pair<List<FixEvent>, Double> {
        val rig = SimRig(seed = 3)
        rig.cruiseMps = kmhToMps(kmh)
        val fixes = mutableListOf<FixEvent>()
        rig.run(seconds) { if (it is FixEvent) fixes += it }
        return fixes to rig.vehicle.odometerM
    }

    @Test
    fun `raw log round-trips every field`() {
        val (fixes, _) = simFixes(20.0)
        val parsed = RawLog.parse(RawLog.write(fixes))
        assertEquals(fixes, parsed)
    }

    @Test
    fun `raw log skips malformed lines`() {
        val text = RawLog.HEADER + "\n1,2,35.0,139.0,,,,,,,,0\ngarbage\n# comment\n"
        assertEquals(1, RawLog.parse(text).size)
    }

    @Test
    fun `replaying a recorded drive reproduces the distance`() {
        val (fixes, odometer) = simFixes(120.0)
        val result = Replay.run(fixes)
        assertEquals(odometer, result.state.stats.distanceM, odometer * 0.02)
        assertEquals(fixes.size, result.rows.size)
    }

    @Test
    fun `gpx track points are read with time, elevation and speed`() {
        val gpx = """
            <gpx version="1.1" xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v2">
             <trk><trkseg>
              <trkpt lat="35.6812" lon="139.7671"><ele>40.5</ele><time>2026-09-24T01:00:00Z</time>
                <extensions><gpxtpx:TrackPointExtension><gpxtpx:speed>13.9</gpxtpx:speed></gpxtpx:TrackPointExtension></extensions></trkpt>
              <trkpt lat='35.6813' lon='139.7672'><time>2026-09-24T01:00:01Z</time></trkpt>
              <trkpt lat="35.6814" lon="139.7673"/>
             </trkseg></trk>
            </gpx>
        """.trimIndent()
        val fixes = Gpx.parse(gpx)
        assertEquals(2, fixes.size)
        assertEquals(40.5, fixes[0].altM!!, 1e-9)
        assertEquals(13.9f, fixes[0].speed!!, 1e-6f)
        assertEquals(1_000_000_000L, fixes[1].tNanos - fixes[0].tNanos)
    }

    @Test
    fun `nmea rmc and gga combine into fixes and bad checksums are dropped`() {
        fun s(body: String) = "$" + body + "*" + Nmea.checksum(body)
        val log = listOf(
            s("GNGGA,010203.00,3540.8720,N,13946.0260,E,1,14,0.8,41.2,M,39.4,M,,"),
            s("GNRMC,010203.00,A,3540.8720,N,13946.0260,E,32.40,51.0,240926,,,A"),
            "\$GNRMC,010204.00,A,3540.8800,N,13946.0300,E,32.40,51.0,240926,,,A*00",
            s("GNRMC,010205.00,V,,,,,,,240926,,,N"),
        ).joinToString("\n")
        val fixes = Nmea.parse(log)
        assertEquals(1, fixes.size)
        val f = fixes[0]
        assertEquals(35.6812, f.lat, 1e-4)
        assertEquals(139.7671, f.lon, 1e-4)
        assertEquals(32.40 * 0.514444, f.speed!!.toDouble(), 1e-3)
        assertEquals(41.2, f.altM!!, 1e-9)
        assertTrue(abs(f.hAcc!! - 3.2f) < 1e-4)
    }
}
