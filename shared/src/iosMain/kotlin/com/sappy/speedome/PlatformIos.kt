package com.sappy.speedome

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.countryCode
import platform.Foundation.currentLocale
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.timeIntervalSinceDate
import platform.posix.CLOCK_MONOTONIC
import platform.posix.clock_gettime_nsec_np

/** Darwin's CLOCK_MONOTONIC keeps counting while the phone sleeps, like Android's elapsedRealtime. */
@OptIn(ExperimentalForeignApi::class)
actual fun monotonicNanos(): Long = clock_gettime_nsec_np(CLOCK_MONOTONIC.toUInt()).toLong()

private val decimalFormatters = HashMap<Int, NSNumberFormatter>()

actual fun localDecimal(value: Double, places: Int): String {
    val f = decimalFormatters.getOrPut(places) {
        NSNumberFormatter().apply {
            numberStyle = NSNumberFormatterDecimalStyle
            usesGroupingSeparator = false
            minimumFractionDigits = places.toULong()
            maximumFractionDigits = places.toULong()
        }
    }
    return f.stringFromNumber(NSNumber(value)) ?: value.toString()
}

actual fun regionCode(): String? = NSLocale.currentLocale.countryCode

actual fun killProcess() {}

private fun nsDate(utcMillis: Long) = platform.Foundation.NSDate(timeIntervalSinceReferenceDate = utcMillis / 1000.0 - 978_307_200.0)

private fun formatter(date: platform.Foundation.NSDateFormatterStyle, time: platform.Foundation.NSDateFormatterStyle) =
    platform.Foundation.NSDateFormatter().apply {
        dateStyle = date
        timeStyle = time
    }

private val timeFormatter by lazy { formatter(platform.Foundation.NSDateFormatterNoStyle, platform.Foundation.NSDateFormatterShortStyle) }
private val dateTimeFormatter by lazy { formatter(platform.Foundation.NSDateFormatterMediumStyle, platform.Foundation.NSDateFormatterShortStyle) }

actual fun formatLocalTime(utcMillis: Long): String = timeFormatter.stringFromDate(nsDate(utcMillis))

actual fun formatLocalDateTime(utcMillis: Long): String = dateTimeFormatter.stringFromDate(nsDate(utcMillis))

actual fun localHourMinute(utcMillis: Long): Pair<Int, Int> {
    val cal = platform.Foundation.NSCalendar.currentCalendar
    val c = cal.components(platform.Foundation.NSCalendarUnitHour or platform.Foundation.NSCalendarUnitMinute, nsDate(utcMillis))
    return c.hour.toInt() to c.minute.toInt()
}

actual fun nextLocalOccurrence(hour: Int, minute: Int): Long {
    val cal = platform.Foundation.NSCalendar.currentCalendar
    val now = platform.Foundation.NSDate()
    val today = cal.dateBySettingHour(hour.toLong(), minute.toLong(), 0, now, 0u) ?: now
    val at = if (today.timeIntervalSinceDate(now) > 0) today else cal.dateByAddingUnit(platform.Foundation.NSCalendarUnitDay, 1, today, 0u) ?: today
    return (at.timeIntervalSince1970 * 1000).toLong()
}

/** iOS has no direct API: a short time format without "a" (AM/PM) means a 24-hour clock. */
@androidx.compose.runtime.Composable
actual fun is24HourClock(): Boolean =
    platform.Foundation.NSDateFormatter.dateFormatFromTemplate("j", 0u, platform.Foundation.NSLocale.currentLocale)?.contains("a") != true

private val stampFormatter by lazy { platform.Foundation.NSDateFormatter().apply { dateFormat = "yyyy-MM-dd-HHmm" } }

actual fun localFileStamp(utcMillis: Long): String = stampFormatter.stringFromDate(nsDate(utcMillis))
