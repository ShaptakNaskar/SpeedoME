package com.sappy.speedome.debug

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.SystemClock
import android.util.Log

/**
 * Debug-build mock provider (docs/plan.md §11). Each broadcast becomes one fix on GPS_PROVIDER, so
 * the whole real pipeline (GpsSource → engine → gauges) runs exactly as with a real chip:
 *
 *     adb shell am broadcast -p com.sappy.SpeedoMe.debug -a speedome.MOCK_FIX \
 *         --ef lat 52.52 --ef lon 13.40 --ef kmh 48 [--ef acc 4] [--ef bearing 90] [--ef alt 35] [--ef sacc 0.3]
 *     adb shell am broadcast -p com.sappy.SpeedoMe.debug -a speedome.MOCK_STOP
 *
 * The app must be the selected mock location app once:
 * `adb shell appops set com.sappy.SpeedoMe.debug android:mock_location allow`.
 * Omitting `kmh` sends a fix without Doppler speed, to exercise the position-speed fallback.
 */
class MockFixReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val lm = context.getSystemService(LocationManager::class.java)
        runCatching {
            when (intent.action) {
                ACTION_FIX -> {
                    ensureProvider(lm)
                    lm.setTestProviderLocation(LocationManager.GPS_PROVIDER, intent.toLocation())
                }
                ACTION_STOP -> {
                    lm.removeTestProvider(LocationManager.GPS_PROVIDER)
                    installed = false
                }
            }
        }.onFailure { Log.w(TAG, "mock ${intent.action} failed (is this the mock location app?)", it) }
    }

    private fun ensureProvider(lm: LocationManager) {
        if (installed) return
        // The system keeps a test provider across our process restarts; re-adding it may throw.
        runCatching { addProvider(lm) }
        lm.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
        installed = true
    }

    @SuppressLint("WrongConstant") // lint misreads the ProviderProperties int defs on the builder
    private fun addProvider(lm: LocationManager) {
        if (Build.VERSION.SDK_INT >= 31) {
            lm.addTestProvider(
                LocationManager.GPS_PROVIDER,
                ProviderProperties.Builder().setHasSpeedSupport(true).setHasBearingSupport(true).setHasAltitudeSupport(true)
                    .setAccuracy(ProviderProperties.ACCURACY_FINE).setPowerUsage(ProviderProperties.POWER_USAGE_HIGH).build(),
            )
        } else {
            @Suppress("DEPRECATION")
            lm.addTestProvider(LocationManager.GPS_PROVIDER, false, true, false, false, true, true, true, 3, 1)
        }
    }

    private fun Intent.toLocation() = Location(LocationManager.GPS_PROVIDER).apply {
        latitude = getFloatExtra("lat", 0f).toDouble().let { d -> getStringExtra("latS")?.toDoubleOrNull() ?: d }
        longitude = getFloatExtra("lon", 0f).toDouble().let { d -> getStringExtra("lonS")?.toDoubleOrNull() ?: d }
        time = System.currentTimeMillis()
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        accuracy = getFloatExtra("acc", 4f)
        if (hasExtra("kmh")) {
            speed = getFloatExtra("kmh", 0f) / 3.6f
            speedAccuracyMetersPerSecond = getFloatExtra("sacc", .3f)
        }
        if (hasExtra("bearing")) bearing = getFloatExtra("bearing", 0f)
        if (hasExtra("alt")) altitude = getFloatExtra("alt", 0f).toDouble()
    }

    private companion object {
        const val TAG = "SpeedoMock"
        const val ACTION_FIX = "speedome.MOCK_FIX"
        const val ACTION_STOP = "speedome.MOCK_STOP"

        @Volatile
        var installed = false
    }
}
