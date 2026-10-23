package com.sappy.speedome.tracking

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.OnNmeaMessageListener
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import com.sappy.speedome.engine.FixEvent

/**
 * Raw GNSS straight from LocationManager's GPS provider at the fastest rate the chip offers
 * (minTime = 0, minDistance = 0). No Google Play Services involved (docs/plan.md §5).
 */
class GpsSource(
    context: Context,
    private val tracking: TrackingEngine,
    private val gnss: GnssRepository,
    private val raw: RawLogger,
) {
    private val appContext = context.applicationContext
    private val lm = appContext.getSystemService(LocationManager::class.java)
    private val thread = HandlerThread("speedome-gps").apply { start() }
    private val handler = Handler(thread.looper)
    private var running = false
    private var nmeaOn = false

    private val listener = LocationListener { loc ->
        val e = loc.toFixEvent()
        raw.fix(e)
        tracking.submit(e)
    }

    private val statusCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            raw.satellites(android.os.SystemClock.elapsedRealtimeNanos(), (0 until status.satelliteCount).count { status.usedInFix(it) }, status.satelliteCount)
            gnss.onSatellites(
                (0 until status.satelliteCount).map { i ->
                    Satellite(
                        constellation = status.getConstellationType(i),
                        svid = status.getSvid(i),
                        cn0DbHz = status.getCn0DbHz(i),
                        azimuthDeg = status.getAzimuthDegrees(i),
                        elevationDeg = status.getElevationDegrees(i),
                        usedInFix = status.usedInFix(i),
                        carrierHz = if (status.hasCarrierFrequencyHz(i)) status.getCarrierFrequencyHz(i) else null,
                    )
                },
            )
        }

        override fun onFirstFix(ttffMillis: Int) = gnss.onFirstFix(ttffMillis)

        override fun onStopped() = gnss.clearSatellites()
    }

    private val nmeaListener = OnNmeaMessageListener { message, _ -> gnss.onNmea(message) }

    /** Caller has checked fine-location permission ([PermissionState.canTrack]). */
    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        runCatching {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, listener, thread.looper)
            lm.registerGnssStatusCallback(statusCallback, handler)
            if (Build.VERSION.SDK_INT >= 28) gnss.onHardware(lm.gnssHardwareModelName)
            running = true
            if (nmeaOn) addNmea()
        }
    }

    fun stop() {
        if (!running) return
        lm.removeUpdates(listener)
        lm.unregisterGnssStatusCallback(statusCallback)
        lm.removeNmeaListener(nmeaListener)
        gnss.clearSatellites()
        running = false
    }

    /** NMEA is only parsed while the nerd page is on screen. */
    fun setNmeaEnabled(on: Boolean) {
        nmeaOn = on
        if (!running) return
        if (on) addNmea() else lm.removeNmeaListener(nmeaListener)
    }

    @SuppressLint("MissingPermission")
    private fun addNmea() {
        runCatching { lm.addNmeaListener(nmeaListener, handler) }
    }
}

fun Location.toFixEvent(): FixEvent = FixEvent(
    tNanos = elapsedRealtimeNanos,
    utcMillis = time,
    lat = latitude,
    lon = longitude,
    hAcc = if (hasAccuracy()) accuracy else null,
    altM = when {
        Build.VERSION.SDK_INT >= 34 && hasMslAltitude() -> mslAltitudeMeters
        hasAltitude() -> altitude
        else -> null
    },
    vAcc = if (hasVerticalAccuracy()) verticalAccuracyMeters else null,
    speed = if (hasSpeed()) speed else null,
    speedAcc = if (hasSpeedAccuracy()) speedAccuracyMetersPerSecond else null,
    bearing = if (hasBearing()) bearing else null,
    bearingAcc = if (hasBearingAccuracy()) bearingAccuracyDegrees else null,
    isMock = if (Build.VERSION.SDK_INT >= 31) isMock else @Suppress("DEPRECATION") isFromMockProvider,
)
