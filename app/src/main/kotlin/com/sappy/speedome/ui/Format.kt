package com.sappy.speedome.ui

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

    /** "12 min ago", "2 h 14 min ago", "3 days ago". */
    fun ago(millis: Long): String {
        val days = millis / 86_400_000
        return if (days >= 1) "$days day${if (days > 1) "s" else ""} ago" else "${gap(millis)} ago"
    }
}
