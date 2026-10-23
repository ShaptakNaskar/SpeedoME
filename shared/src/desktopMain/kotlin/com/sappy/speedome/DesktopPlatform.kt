package com.sappy.speedome

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.sappy.speedome.data.SpeedoDatabase
import com.sappy.speedome.tracking.Motion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okio.Path.Companion.toPath
import java.io.File

/**
 * The Linux desktop preview of the iOS/Android screens: no sensors (turn on the simulator), an
 * in-memory database and a throwaway settings file, so previews never touch real data.
 */
class DesktopPlatform(private val dir: File = File(System.getProperty("java.io.tmpdir"), "speedome-preview-${ProcessHandle.current().pid()}")) : Platform {
    override fun settingsStore(): DataStore<Preferences> {
        dir.mkdirs()
        return PreferenceDataStoreFactory.createWithPath(produceFile = { File(dir, "settings.preferences_pb").absolutePath.toPath() })
    }

    override fun database(): SpeedoDatabase =
        Room.inMemoryDatabaseBuilder<SpeedoDatabase>().setDriver(BundledSQLiteDriver()).build()

    override val motion = object : MotionSensors {
        override val motion: StateFlow<Motion> = MutableStateFlow(Motion(headingDeg = 42f, headingAccuracy = 3))
        override fun acquire() {}
        override fun release() {}
        override fun setVisible(visible: Boolean) {}
    }

    override fun sources(app: AppContainer) = object : LocationSources {
        override fun setNmeaEnabled(on: Boolean) {}
        override fun permissionsChanged() {}
        override fun setVisible(visible: Boolean) {}
        override fun setReplaying(on: Boolean) {}
    }

    override val vibrator: LimitVibrator? = null

    override fun ensureRecording(app: AppContainer) {
        app.recordingService.value = app.tracking.state.value.session.kind == com.sappy.speedome.engine.SessionKind.TRIP
    }
}
