package com.sappy.speedome

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.sappy.speedome.data.SpeedoDatabase
import com.sappy.speedome.tracking.Motion
import kotlinx.coroutines.flow.StateFlow

/**
 * What the shared app needs from the device it runs on. Android ([AndroidPlatform]), iOS and the
 * Linux desktop preview each provide one; [AppContainer] wires the rest.
 */
interface Platform {
    fun settingsStore(): DataStore<Preferences>
    fun database(): SpeedoDatabase
    val motion: MotionSensors

    /** Starts and stops GPS and step sensors as the container's state asks (called once, during wiring). */
    fun sources(app: AppContainer): LocationSources

    /** Buzzes for the speed limit, or null without a vibrator. */
    val vibrator: LimitVibrator?

    /** Keeps a recording trip running when the app leaves the screen (Android: the location service). */
    fun ensureRecording(app: AppContainer)

    /** Developer raw GPS log (Android only). */
    fun setRawLogging(enabled: Boolean) {}
}

/** Compass heading, yaw rate and horizontal acceleration for the Nerd page, Speed Tape and the info strip. */
interface MotionSensors {
    val motion: StateFlow<Motion>

    /** Screens that show motion data hold the sensors while on screen; they run while anyone holds them. */
    fun acquire()
    fun release()
    fun setVisible(visible: Boolean)
}

/** GPS and step sensors, run by the platform while tracking is active. */
interface LocationSources {
    /** NMEA is parsed only while the Nerd page is on screen. */
    fun setNmeaEnabled(on: Boolean)

    /** Call after the user grants or revokes a permission. */
    fun permissionsChanged()
    fun setVisible(visible: Boolean)

    /** A developer file replay stands in for real GPS, like the simulator. */
    fun setReplaying(on: Boolean)
}

/** The speed-limit buzz: one long buzz if [hit], then pulses at [hz] until replaced (0 and !hit = stop). */
fun interface LimitVibrator {
    fun play(hit: Boolean, hz: Int)
}

/** The engine's clock: monotonic nanoseconds that keep counting while the phone sleeps. */
expect fun monotonicNanos(): Long

/** Wall-clock UTC milliseconds. */
fun utcMillis(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()

/** [value] with [places] decimals in the user's locale (a decimal comma in much of Europe). */
expect fun localDecimal(value: Double, places: Int): String

/** The user's region (US, GB, …), for the default units. */
expect fun regionCode(): String?

/** Ends the process (developer resume tests). Only Android does it; elsewhere it's ignored. */
expect fun killProcess()

/** Local wall-clock time, "14:32" or "2:32 PM" per the user's locale. */
expect fun formatLocalTime(utcMillis: Long): String

/** Local date and time, e.g. "Sep 26, 2026, 5:59 PM". */
expect fun formatLocalDateTime(utcMillis: Long): String

/** The local hour (0–23) and minute of [utcMillis]. */
expect fun localHourMinute(utcMillis: Long): Pair<Int, Int>

/** The next time the local clock reads [hour]:[minute] (today if still ahead, else tomorrow), in UTC millis. */
expect fun nextLocalOccurrence(hour: Int, minute: Int): Long

/** Whether the user's clock shows 24-hour time. */
@androidx.compose.runtime.Composable
expect fun is24HourClock(): Boolean

/** Local "yyyy-MM-dd-HHmm", for file names. */
expect fun localFileStamp(utcMillis: Long): String
