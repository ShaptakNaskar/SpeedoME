package com.sappy.speedome.ui

import com.sappy.speedome.engine.TargetView
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Display formatting. The engine is SI; these convert at the edge. */
object Fmt {
    fun kmh(mps: Double): Double = mps * 3.6

    fun km(metres: Double): String =
        String.format(Locale.getDefault(), if (metres < 99_500) "%.1f" else "%.0f", metres / 1000)

    fun duration(seconds: Double): String {
        val s = seconds.toLong().coerceAtLeast(0)
        val h = s / 3600
        val m = s % 3600 / 60
        val sec = s % 60
        return if (h > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, sec)
        } else {
            String.format(Locale.getDefault(), "%d:%02d", m, sec)
        }
    }

    fun decimal(value: Double, places: Int): String = String.format(Locale.getDefault(), "%.${places}f", value)

    fun pace(secPerKm: Double?): String = secPerKm?.let {
        val s = it.toLong()
        String.format(Locale.getDefault(), "%d:%02d /km", s / 60, s % 60)
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
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(Instant.ofEpochMilli(utcMillis).atZone(ZoneId.systemDefault()))

    /** "3 min ahead" / "5 min behind" / "on time". */
    fun ahead(seconds: Long): String {
        val m = Math.round(kotlin.math.abs(seconds) / 60.0)
        return when {
            m == 0L -> "on time"
            seconds > 0 -> "${gap(m * 60_000)} ahead"
            else -> "${gap(m * 60_000)} behind"
        }
    }

    /** The target summary used by the strip under the gauge and the notification. */
    fun target(t: TargetView): List<String> = when {
        t.arrived -> listOf("ARRIVED", "+${km(t.pastM)} km past")
        else -> buildList {
            add("${km(t.remainingM)} km left")
            add(t.etaUtc?.let { "ETA ${clock(it)}" } ?: "ETA —")
            if (t.late) {
                add("LATE")
            } else {
                t.neededMps?.let { add("need ${kmh(it).toInt()} km/h") }
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
