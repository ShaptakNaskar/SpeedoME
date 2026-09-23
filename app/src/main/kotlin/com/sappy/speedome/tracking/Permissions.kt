package com.sappy.speedome.tracking

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat

/** What SpeedoME is allowed to do right now (docs/plan.md §8, permissions 1–3). */
data class PermissionState(
    val fineLocation: Boolean,
    val coarseOnly: Boolean,
    val locationServicesOn: Boolean,
    val notifications: Boolean,
    val activityRecognition: Boolean,
) {
    val canTrack: Boolean get() = fineLocation && locationServicesOn

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
            )
        }
    }
}
