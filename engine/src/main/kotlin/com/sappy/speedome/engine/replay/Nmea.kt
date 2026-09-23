package com.sappy.speedome.engine.replay

import com.sappy.speedome.engine.FixEvent
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** Reads fixes from NMEA 0183 logs: RMC for position/speed/course, GGA for altitude and HDOP. */
object Nmea {
    private const val KNOT_MPS = 0.514444

    fun checksum(body: String): String = body.fold(0) { acc, ch -> acc xor ch.code }.toString(16).uppercase().padStart(2, '0')

    /** True when the sentence carries no checksum or a correct one. */
    fun checksumOk(sentence: String): Boolean {
        val s = sentence.trim().removePrefix("$")
        val star = s.lastIndexOf('*')
        if (star < 0) return true
        return checksum(s.substring(0, star)).equals(s.substring(star + 1).take(2), ignoreCase = true)
    }

    fun parse(text: String): List<FixEvent> {
        val sentences = text.lineSequence().map { it.trim() }.filter { it.startsWith("$") && checksumOk(it) }
            .map { it.removePrefix("$").substringBefore('*').split(',') }.toList()
        val gga = sentences.filter { it[0].endsWith("GGA") && it.size > 9 }.associate { f ->
            f[1] to (f[9].toDoubleOrNull() to f[8].toFloatOrNull())
        }
        val out = ArrayList<FixEvent>()
        var t0: Long? = null
        for (f in sentences) {
            if (!f[0].endsWith("RMC") || f.size < 10 || f[2] != "A") continue
            val utc = utcMillis(f[1], f[9]) ?: continue
            val lat = coord(f[3], f[4], 2) ?: continue
            val lon = coord(f[5], f[6], 3) ?: continue
            val start = t0 ?: utc.also { t0 = it }
            val (alt, hdop) = gga[f[1]] ?: (null to null)
            out += FixEvent(
                tNanos = (utc - start) * 1_000_000L + 1,
                utcMillis = utc,
                lat = lat,
                lon = lon,
                hAcc = hdop?.let { it * 4f },
                altM = alt,
                speed = f[7].toDoubleOrNull()?.let { (it * KNOT_MPS).toFloat() },
                bearing = f[8].toFloatOrNull(),
            )
        }
        return out
    }

    private fun coord(value: String, hemisphere: String, degDigits: Int): Double? {
        if (value.length <= degDigits) return null
        val deg = value.substring(0, degDigits).toDoubleOrNull() ?: return null
        val min = value.substring(degDigits).toDoubleOrNull() ?: return null
        val v = deg + min / 60
        return if (hemisphere == "S" || hemisphere == "W") -v else v
    }

    private fun utcMillis(time: String, date: String): Long? = runCatching {
        val hh = time.substring(0, 2).toInt()
        val mm = time.substring(2, 4).toInt()
        val ss = time.substring(4).toDouble()
        val t = LocalTime.of(hh, mm, ss.toInt(), ((ss - ss.toInt()) * 1e9).toInt())
        val d = LocalDate.of(2000 + date.substring(4, 6).toInt(), date.substring(2, 4).toInt(), date.substring(0, 2).toInt())
        d.atTime(t).toInstant(ZoneOffset.UTC).toEpochMilli()
    }.getOrNull()
}
