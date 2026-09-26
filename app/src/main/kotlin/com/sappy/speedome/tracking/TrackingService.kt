package com.sappy.speedome.tracking

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.sappy.speedome.AppContainer
import com.sappy.speedome.SpeedoApplication
import com.sappy.speedome.engine.Command
import com.sappy.speedome.engine.SessionKind
import com.sappy.speedome.engine.view
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service of type `location` that keeps a recording trip going with the screen off or
 * another app in front (docs/plan.md §8). It runs only while a trip is recording: it is started from
 * the visible app when recording begins, and stops itself when the trip ends. The live meter never
 * uses it, so closing the app without recording leaves nothing running.
 */
class TrackingService : Service() {
    private lateinit var container: AppContainer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var foreground = false

    override fun onCreate() {
        super.onCreate()
        container = (application as SpeedoApplication).container
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null && !PermissionState.of(this).canSelfRestart) {
            // A sticky restart after the process was killed. Without "Allow all the time" and the
            // battery exemption the background start can't get location, so the trip resumes when
            // the app is next opened instead (the recorder has the snapshot either way).
            Log.i(TAG, "sticky restart skipped: background location or battery exemption missing")
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent == null) Log.i(TAG, "sticky restart: resuming the trip in the background")
        if (!foreground) {
            if (!goForeground()) {
                stopSelf()
                return START_NOT_STICKY
            }
            foreground = true
            container.recordingService.value = true
            scope.launch {
                while (isActive) {
                    delay(1000)
                    notifyNow()
                }
            }
            scope.launch {
                // After a sticky restart the recorder first restores the trip; then this service lives
                // exactly as long as the trip does.
                container.recorder.ready.first { it }
                container.tracking.awaitIdle()
                container.tracking.state.first { it.session.kind != SessionKind.TRIP }
                Log.i(TAG, "no trip recording: stopping")
                stopTracking()
            }
        }
        when (intent?.action) {
            ACTION_PAUSE -> container.tracking.command(Command.Pause)
            ACTION_RESUME -> container.tracking.command(Command.Resume)
            ACTION_STOP_TRIP -> container.tracking.command(Command.StopTrip)
        }
        return START_STICKY
    }

    private fun currentView() = container.tracking.state.value.view(SystemClock.elapsedRealtimeNanos())

    private fun goForeground(): Boolean = runCatching {
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, TrackingNotification.ID, TrackingNotification.build(this, currentView()), type)
    }.isSuccess

    private fun notifyNow() {
        val nm = getSystemService(NotificationManager::class.java)
        runCatching { nm.notify(TrackingNotification.ID, TrackingNotification.build(this, currentView())) }
    }

    private fun stopTracking() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (foreground) container.recordingService.value = false
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_PAUSE = "com.sappy.speedome.action.PAUSE"
        const val ACTION_RESUME = "com.sappy.speedome.action.RESUME"
        const val ACTION_STOP_TRIP = "com.sappy.speedome.action.STOP_TRIP"
        private const val TAG = "SpeedoService"

        /** Starts (or keeps) recording in the background. Call while the app is visible and location is granted. */
        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, TrackingService::class.java)) }
        }
    }
}
