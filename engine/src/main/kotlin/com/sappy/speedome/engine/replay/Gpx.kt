package com.sappy.speedome.engine.replay

import com.sappy.speedome.engine.FixEvent
import java.time.Instant

/** Reads track points from GPX 1.0/1.1 files (speed from `<speed>` or Garmin `<gpxtpx:speed>`). */
object Gpx {
    private val trkpt = Regex("""<trkpt\b([^>]*?)(?:/>|>(.*?)</trkpt>)""", RegexOption.DOT_MATCHES_ALL)
    private val attr = Regex("""\b(lat|lon)\s*=\s*["']([^"']+)["']""")

    private fun tag(body: String, name: String): String? =
        Regex("""<(?:\w+:)?$name>\s*([^<]+?)\s*</(?:\w+:)?$name>""").find(body)?.groupValues?.get(1)

    /** Points without a timestamp are skipped. Monotonic times start at 1 ns for the first point. */
    fun parse(text: String): List<FixEvent> {
        data class Pt(val utc: Long, val lat: Double, val lon: Double, val ele: Double?, val speed: Float?, val hdop: Float?)
        val pts = trkpt.findAll(text).mapNotNull { m ->
            val attrs = attr.findAll(m.groupValues[1]).associate { it.groupValues[1] to it.groupValues[2].toDoubleOrNull() }
            val lat = attrs["lat"] ?: return@mapNotNull null
            val lon = attrs["lon"] ?: return@mapNotNull null
            val body = m.groupValues[2]
            val utc = tag(body, "time")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: return@mapNotNull null
            Pt(utc, lat, lon, tag(body, "ele")?.toDoubleOrNull(), tag(body, "speed")?.toFloatOrNull(), tag(body, "hdop")?.toFloatOrNull())
        }.toList()
        if (pts.isEmpty()) return emptyList()
        val t0 = pts.first().utc
        return pts.map {
            FixEvent(
                tNanos = (it.utc - t0) * 1_000_000L + 1,
                utcMillis = it.utc,
                lat = it.lat,
                lon = it.lon,
                hAcc = it.hdop?.let { h -> h * 4f },
                altM = it.ele,
                speed = it.speed,
            )
        }
    }
}
