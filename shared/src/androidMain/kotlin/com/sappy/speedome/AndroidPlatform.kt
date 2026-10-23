package com.sappy.speedome

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import androidx.room.RoomDatabase.JournalMode
import com.sappy.speedome.data.SpeedoDatabase
import com.sappy.speedome.engine.SessionKind
import com.sappy.speedome.tracking.AndroidLimitVibrator
import com.sappy.speedome.tracking.MotionSource
import com.sappy.speedome.tracking.PermissionState
import com.sappy.speedome.tracking.RawLogger
import com.sappy.speedome.tracking.SourceManager
import com.sappy.speedome.tracking.TrackingService

/** The Application implements this so services can reach the container. */
interface ContainerHolder {
    val container: AppContainer
}

private val Context.settingsStore by preferencesDataStore(name = "settings")

/** Android's sensors, storage and the recording service. */
class AndroidPlatform(private val app: Application) : Platform {
    private val rawLogger = RawLogger(app)

    override fun settingsStore(): DataStore<Preferences> = app.settingsStore

    override fun database(): SpeedoDatabase =
        Room.databaseBuilder<SpeedoDatabase>(app, app.getDatabasePath(SpeedoDatabase.FILE).absolutePath)
            .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
            .build()

    override val motion = MotionSource(app)

    override fun sources(app: AppContainer) =
        SourceManager(this.app, app.appScope, app.settings.state, app.tracking, app.gnss, rawLogger, app.trackingActive)

    override val vibrator = AndroidLimitVibrator.create(app)

    /** Starts the recording service if a trip is active, the app is on screen and location is allowed. */
    override fun ensureRecording(app: AppContainer) {
        val trip = app.tracking.state.value.session.kind == SessionKind.TRIP
        if (app.visible.value && trip && PermissionState.of(this.app).canTrack) TrackingService.start(this.app)
    }

    override fun setRawLogging(enabled: Boolean) = rawLogger.setEnabled(enabled)
}
