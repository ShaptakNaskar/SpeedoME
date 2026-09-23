package com.sappy.speedome.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.sappy.speedome.LocalAppContainer
import com.sappy.speedome.engine.Mode
import com.sappy.speedome.tracking.PermissionState
import com.sappy.speedome.tracking.TrackingService
import com.sappy.speedome.ui.theme.SpeedoColors

/** Live permission state; refreshed on every resume so changes made in system settings show up. */
@Stable
class PermissionHolder(private val context: Context, private val onChanged: () -> Unit) {
    var state by mutableStateOf(PermissionState.of(context))
        private set

    fun refresh() {
        state = PermissionState.of(context)
        onChanged()
    }
}

@Composable
fun rememberPermissions(): PermissionHolder {
    val context = LocalContext.current
    val app = LocalAppContainer.current
    val holder = remember {
        PermissionHolder(context) {
            app.sources.permissionsChanged()
            if (PermissionState.of(context).canTrack) TrackingService.start(context)
        }
    }
    LifecycleResumeEffect(Unit) {
        holder.refresh()
        onPauseOrDispose { }
    }
    return holder
}

/**
 * Asks for each permission the first time it's needed, with one line of why (docs/plan.md §8).
 * After a refusal the button switches to opening SpeedoME's system settings.
 */
@Composable
fun PermissionCards(permissions: PermissionHolder, mode: Mode) {
    val context = LocalContext.current
    val state = permissions.state
    var asked by rememberSaveable { mutableStateOf(emptyList<String>()) }

    fun refresh() = permissions.refresh()

    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }
    val location = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        refresh()
        val fine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fine && Build.VERSION.SDK_INT >= 33 && !permissions.state.notifications) {
            asked = asked + NOTIFY
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val activity = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            !state.fineLocation && !state.coarseOnly -> Card(
                "Location needed",
                "SpeedoME measures your speed with the phone's GPS. Your location never leaves the phone.",
                if (LOCATION in asked) "Open settings" else "Allow location",
            ) {
                if (LOCATION in asked) {
                    openAppSettings(context)
                } else {
                    asked = asked + LOCATION
                    location.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            }
            state.coarseOnly -> Card(
                "Precise location is off",
                "Speed needs precise location. Turn on \"Use precise location\" for SpeedoME.",
                "Open settings",
            ) { openAppSettings(context) }
            !state.locationServicesOn -> Card("Location is turned off", "Turn on location to see your speed.", "Turn on") {
                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
        }
        if (state.fineLocation && !state.notifications) {
            Card(
                "Show speed in the notification",
                "Tracking works without it, but you won't see live speed outside the app.",
                if (NOTIFY in asked) "Open settings" else "Allow",
            ) {
                if (NOTIFY in asked || Build.VERSION.SDK_INT < 33) {
                    openAppSettings(context)
                } else {
                    asked = asked + NOTIFY
                    notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
        if (mode == Mode.STEP && !state.activityRecognition) {
            Card(
                "Step counting is off",
                "Step mode needs the Physical activity permission to count your steps.",
                if (ACTIVITY in asked) "Open settings" else "Allow",
            ) {
                if (ACTIVITY in asked || Build.VERSION.SDK_INT < 29) {
                    openAppSettings(context)
                } else {
                    asked = asked + ACTIVITY
                    activity.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                }
            }
        }
    }
}

private const val LOCATION = "location"
private const val NOTIFY = "notifications"
private const val ACTIVITY = "activity"

private fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
    )
}

@Composable
private fun Card(title: String, body: String, button: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SpeedoColors.Raised).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, color = SpeedoColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(body, color = SpeedoColors.Muted, fontSize = 14.sp)
        FilledTonalButton(onClick = onClick) { Text(button) }
    }
}
