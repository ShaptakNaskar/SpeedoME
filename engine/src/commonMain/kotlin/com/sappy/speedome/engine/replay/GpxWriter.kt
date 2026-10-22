package com.sappy.speedome.engine.replay

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.time.Instant

/** A stored route point for export. [segment] changes start a new `<trkseg>` (pauses, resume gaps). */
data class GpxPoint(
    val utcMillis: Long,
    val lat: Double,
    val lon: Double,
    val eleM: Double? = null,
    val speedMps: Double? = null,
    val segment: Int = 0,
)

/**
 * Writes GPX 1.1 with time, elevation and speed (Garmin TrackPointExtension v2), which Strava,
 * OsmAnd and Google Earth read. Round-trips through [Gpx.parse].
 */
object GpxWriter {
    fun write(name: String, points: List<GpxPoint>, creator: String = "SpeedoME"): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append("""<gpx version="1.1" creator="${esc(creator)}" xmlns="http://www.topografix.com/GPX/1/1" """)
        append("""xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v2">""").append('\n')
        append("  <trk><name>").append(esc(name)).append("</name>\n")
        var segment: Int? = null
        for (p in points) {
            if (p.segment != segment) {
                if (segment != null) append("  </trkseg>\n")
                append("  <trkseg>\n")
                segment = p.segment
            }
            append("    <trkpt lat=\"").append(num(p.lat, 7)).append("\" lon=\"").append(num(p.lon, 7)).append("\">")
            p.eleM?.let { append("<ele>").append(num(it, 1)).append("</ele>") }
            append("<time>").append(Instant.fromEpochMilliseconds(p.utcMillis)).append("</time>")
            p.speedMps?.let {
                append("<extensions><gpxtpx:TrackPointExtension><gpxtpx:speed>").append(num(it, 2))
                append("</gpxtpx:speed></gpxtpx:TrackPointExtension></extensions>")
            }
            append("</trkpt>\n")
        }
        if (segment != null) append("  </trkseg>\n")
        append("  </trk>\n</gpx>\n")
    }

    /** [v] with exactly [places] decimals and a dot, whatever the locale (String.format is JVM-only). */
    private fun num(v: Double, places: Int): String {
        val scale = 10.0.pow(places).toLong()
        val n = (abs(v) * scale).roundToLong()
        val s = "${n / scale}." + (n % scale).toString().padStart(places, '0')
        return if (v < 0 && n != 0L) "-$s" else s
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
