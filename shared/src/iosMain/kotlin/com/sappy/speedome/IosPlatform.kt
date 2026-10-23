package com.sappy.speedome

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.sappy.speedome.data.SpeedoDatabase
import com.sappy.speedome.tracking.IosLimitVibrator
import com.sappy.speedome.tracking.IosLocation
import com.sappy.speedome.tracking.IosMotion
import com.sappy.speedome.tracking.IosSources
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/** iOS: files in the app's Documents folder, CoreLocation, CoreMotion and haptics. */
class IosPlatform : Platform {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Fixes go to the engine once the container exists ([sources] wires it). */
    private var onFix: (com.sappy.speedome.engine.FixEvent) -> Unit = {}
    val location = IosLocation { onFix(it) }

    @OptIn(ExperimentalForeignApi::class)
    private val documents: String = NSFileManager.defaultManager
        .URLForDirectory(NSDocumentDirectory, NSUserDomainMask, null, true, null)?.path ?: error("no Documents folder")

    override fun settingsStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.createWithPath(produceFile = { "$documents/settings.preferences_pb".toPath() })

    override fun database(): SpeedoDatabase =
        Room.databaseBuilder<SpeedoDatabase>(name = "$documents/${SpeedoDatabase.FILE}")
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

    override val motion = IosMotion()

    override fun sources(app: AppContainer): LocationSources {
        onFix = app.tracking::submit
        return IosSources(app, location, scope)
    }

    override val vibrator = IosLimitVibrator(scope)

    /** Background location follows the trip state in [IosSources]; nothing to start here. */
    override fun ensureRecording(app: AppContainer) {}
}
