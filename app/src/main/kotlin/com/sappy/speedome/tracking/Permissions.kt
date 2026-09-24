package com.sappy.speedome.tracking

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat

/** What SpeedoME is allowed to do right now (docs/plan.md §8, permissions 1–3). */
data class PermissionState(
    val fineLocation: Boolean,
    val coarseOnly: Boolean,
    val locationServicesOn: Boolean,
    val notifications: Boolean,
    val activityRecognition: Boolean,
    /** "Allow all the time" (always true below Android 10, where foreground location covers it). */
    val backgroundLocation: Boolean,
    val batteryExempt: Boolean,
) {
    val canTrack: Boolean get() = fineLocation && locationServicesOn

    /** A killed service may restart itself in the background only with both upgrades (docs/plan.md §8). */
    val canSelfRestart: Boolean get() = canTrack && backgroundLocation && batteryExempt

    companion object {
        fun of(context: Context): PermissionState {
            fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
            val fine = granted(Manifest.permission.ACCESS_FINE_LOCATION)
            val coarse = granted(Manifest.permission.ACCESS_COARSE_LOCATION)
            val lm = context.getSystemService(LocationManager::class.java)
            val servicesOn = if (Build.VERSION.SDK_INT >= 28) lm.isLocationEnabled else lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
            return PermissionState(
                fineLocation = fine,
                coarseOnly = coarse && !fine,
                locationServicesOn = servicesOn,
                notifications = Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS),
                activityRecognition = Build.VERSION.SDK_INT < 29 || granted(Manifest.permission.ACTIVITY_RECOGNITION),
                backgroundLocation = fine && (Build.VERSION.SDK_INT < 29 || granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)),
                batteryExempt = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
            )
        }
    }
}
