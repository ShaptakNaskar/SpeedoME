package com.sappy.speedome.ui

import com.sappy.speedome.engine.EngineSettings
import com.sappy.speedome.engine.TargetView
import com.sappy.speedome.gauges.SpeedUnit
import com.sappy.speedome.formatLocalTime
import com.sappy.speedome.localDecimal
import kotlin.concurrent.Volatile

/** Display formatting. The engine is SI; these convert at the edge, in the user's units. */
object Fmt {
    /** Follows the Units setting (set by AppContainer); read from any thread. */
    @Volatile
    var units: SpeedUnit = SpeedUnit.KMH

    val speedUnit: String get() = units.label
    val distUnit: String get() = units.distanceLabel

    fun speed(mps: Double): Double = mps * if (units == SpeedUnit.MPH) EngineSettings.MPH_PER_MPS else EngineSettings.KMH_PER_MPS

    /** Distance in km or miles: one decimal below 100. */
    fun dist(metres: Double): String {
        val v = metres / units.metresPerDistance
        return localDecimal(v, if (v < 99.95) 1 else 0)
    }

    fun duration(seconds: Double): String {
        val s = seconds.toLong().coerceAtLeast(0)
        val h = s / 3600
        val m = s % 3600 / 60
        val sec = s % 60
        return if (h > 0) "$h:${two(m)}:${two(sec)}" else "$m:${two(sec)}"
    }

    fun decimal(value: Double, places: Int): String = localDecimal(value, places)

    private fun two(v: Long) = v.toString().padStart(2, '0')

    fun pace(secPerKm: Double?): String = secPerKm?.let {
        val s = (it * units.metresPerDistance / 1000).toLong()
        "${s / 60}:${two(s % 60)} /$distUnit"
    } ?: "–"

    /** "12 min", "2 h 14 min", "45 s" — for gaps. */
    fun gap(millis: Long): String {
        val s = millis / 1000
        return when {
            s < 60 -> "$s s"
            s < 3600 -> "${s / 60} min"
            else -> "${s / 3600} h ${s % 3600 / 60} min"
        }
    }

    /** Local wall-clock time, "14:32" or "2:32 PM" per locale. */
    fun clock(utcMillis: Long): String =
        formatLocalTime(utcMillis)

    /** "3 min ahead" / "5 min behind" / "on time". */
    fun ahead(seconds: Long): String {
        val m = kotlin.math.floor(kotlin.math.abs(seconds) / 60.0 + .5).toLong()
        return when {
            m == 0L -> "on time"
            seconds > 0 -> "${gap(m * 60_000)} ahead"
            else -> "${gap(m * 60_000)} behind"
        }
    }

    /** The target summary used by the strip under the gauge and the notification. */
    fun target(t: TargetView): List<String> = when {
        t.arrived -> listOf("ARRIVED", "+${dist(t.pastM)} $distUnit past")
        else -> buildList {
            add("${dist(t.remainingM)} $distUnit left")
            add(t.etaUtc?.let { "ETA ${clock(it)}" } ?: "ETA —")
            if (t.late) {
                add("LATE")
            } else {
                t.neededMps?.let { add("need ${speed(it).toInt()} $speedUnit") }
                t.aheadS?.let { add(ahead(it)) }
            }
        }
    }

    /** "12 min ago", "2 h 14 min ago", "3 days ago". */
    fun ago(millis: Long): String {
        val days = millis / 86_400_000
        return if (days >= 1) "$days day${if (days > 1) "s" else ""} ago" else "${gap(millis)} ago"
    }
}
