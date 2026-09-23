package com.sappy.speedome.tracking

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.sappy.speedome.AppContainer
import com.sappy.speedome.SpeedoApplication
import com.sappy.speedome.engine.Command
import com.sappy.speedome.engine.view
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service of type `location`. It is started while the Speed screen is visible, which is
 * what lets GPS keep flowing in the background under the while-in-use permission (docs/plan.md §8).
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
        if (intent?.action == ACTION_STOP_TRACKING) {
            container.tracking.command(Command.Reset)
            stopTracking()
            return START_NOT_STICKY
        }
        if (!foreground) {
            if (!goForeground()) {
                stopSelf()
                return START_NOT_STICKY
            }
            foreground = true
            container.sources.setServiceActive(true)
            scope.launch {
                while (isActive) {
                    delay(1000)
                    notifyNow()
                }
            }
        }
        when (intent?.action) {
            ACTION_START_TRIP -> container.tracking.command(Command.StartTrip)
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
        if (foreground) container.sources.setServiceActive(false)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START_TRIP = "com.sappy.speedome.action.START_TRIP"
        const val ACTION_PAUSE = "com.sappy.speedome.action.PAUSE"
        const val ACTION_RESUME = "com.sappy.speedome.action.RESUME"
        const val ACTION_STOP_TRIP = "com.sappy.speedome.action.STOP_TRIP"
        const val ACTION_STOP_TRACKING = "com.sappy.speedome.action.STOP_TRACKING"

        /** Starts (or keeps) tracking. Call while the app is visible and location is granted. */
        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, TrackingService::class.java)) }
        }
    }
}
