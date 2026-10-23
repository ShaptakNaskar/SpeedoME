package com.sappy.speedome

import java.util.Locale

actual fun monotonicNanos(): Long = System.nanoTime()

actual fun localDecimal(value: Double, places: Int): String = String.format(Locale.getDefault(), "%.${places}f", value)

actual fun regionCode(): String? = Locale.getDefault().country

actual fun killProcess() {}

actual fun formatLocalTime(utcMillis: Long): String =
    java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT)
        .format(java.time.Instant.ofEpochMilli(utcMillis).atZone(java.time.ZoneId.systemDefault()))

actual fun formatLocalDateTime(utcMillis: Long): String =
    java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.MEDIUM, java.time.format.FormatStyle.SHORT)
        .withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.ofEpochMilli(utcMillis))

actual fun localHourMinute(utcMillis: Long): Pair<Int, Int> =
    java.time.Instant.ofEpochMilli(utcMillis).atZone(java.time.ZoneId.systemDefault()).let { it.hour to it.minute }

actual fun nextLocalOccurrence(hour: Int, minute: Int): Long {
    val zone = java.time.ZoneId.systemDefault()
    val today = java.time.LocalDateTime.of(java.time.LocalDate.now(zone), java.time.LocalTime.of(hour, minute))
    val at = if (today.isAfter(java.time.LocalDateTime.now(zone))) today else today.plusDays(1)
    return at.atZone(zone).toInstant().toEpochMilli()
}

@androidx.compose.runtime.Composable
actual fun is24HourClock(): Boolean = true

actual fun localFileStamp(utcMillis: Long): String =
    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm").withZone(java.time.ZoneId.systemDefault())
        .format(java.time.Instant.ofEpochMilli(utcMillis))
