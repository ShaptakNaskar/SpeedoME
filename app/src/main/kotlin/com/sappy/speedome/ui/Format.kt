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
}
