package com.sappy.speedome.tracking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.sappy.speedome.MainActivity
import com.sappy.speedome.R
import com.sappy.speedome.engine.SessionKind
import com.sappy.speedome.engine.TrackView
import com.sappy.speedome.ui.Fmt
import kotlin.math.roundToInt

/** The silent, ongoing tracking notification with live speed and trip controls (docs/plan.md §8). */
object TrackingNotification {
    const val CHANNEL_ID = "tracking"
    const val ID = 1

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Tracking", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Live speed and trip controls while SpeedoME is tracking"
                setShowBadge(false)
            },
        )
    }

    fun build(context: Context, v: TrackView): Notification {
        ensureChannel(context)
        val title = if (v.lastFix == null) {
            "Waiting for GPS…"
        } else {
            "${Fmt.speed(v.speedMps).roundToInt()} ${Fmt.speedUnit} · ${Fmt.dist(v.distanceM)} ${Fmt.distUnit}"
        }
        val state = when {
            v.sessionKind == SessionKind.TRIP && v.paused -> "Paused"
            v.sessionKind == SessionKind.TRIP -> "Recording"
            else -> "Live meter"
        }
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_speedome)
            .setContentTitle(title)
            .setContentText("$state · ${Fmt.duration(v.elapsedS)}")
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        v.target?.let { t ->
            // Target progress: the Android 16 progress-centric style, a plain bar below that (docs/plan.md §6).
            b.setSubText(Fmt.target(t).joinToString(" · "))
            val permille = (t.progress * 1000).roundToInt()
            if (Build.VERSION.SDK_INT >= 36) {
                b.setStyle(
                    NotificationCompat.ProgressStyle()
                        .setProgressSegments(listOf(NotificationCompat.ProgressStyle.Segment(1000).setColor(0xFFE8A33D.toInt())))
                        .setProgress(permille),
                )
            } else {
                b.setProgress(1000, permille, false)
            }
        }
        when {
            v.sessionKind == SessionKind.LIVE -> {
                b.addAction(0, "Record", action(context, TrackingService.ACTION_START_TRIP))
                b.addAction(0, "Stop tracking", action(context, TrackingService.ACTION_STOP_TRACKING))
            }
            v.paused -> {
                b.addAction(0, "Resume", action(context, TrackingService.ACTION_RESUME))
                b.addAction(0, "Stop", action(context, TrackingService.ACTION_STOP_TRIP))
            }
            else -> {
                b.addAction(0, "Pause", action(context, TrackingService.ACTION_PAUSE))
                b.addAction(0, "Stop", action(context, TrackingService.ACTION_STOP_TRIP))
            }
        }
        return b.build()
    }

    private fun action(context: Context, action: String): PendingIntent = PendingIntent.getService(
        context, action.hashCode(),
        Intent(context, TrackingService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
