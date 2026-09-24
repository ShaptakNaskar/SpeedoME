package com.sappy.speedome

import android.app.Application
import androidx.compose.runtime.staticCompositionLocalOf
import com.sappy.speedome.data.SpeedoDatabase
import com.sappy.speedome.settings.SettingsRepository
import com.sappy.speedome.tracking.GnssRepository
import com.sappy.speedome.tracking.MotionSource
import com.sappy.speedome.tracking.SessionRecorder
import com.sappy.speedome.tracking.SimulatorSource
import com.sappy.speedome.tracking.SourceManager
import com.sappy.speedome.tracking.TrackingEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Hand-wired app singletons (D26: no DI framework). */
class AppContainer(app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** True while an activity is started (on screen); the live meter only auto-stops when false. */
    val visible = kotlinx.coroutines.flow.MutableStateFlow(false)
    val settings = SettingsRepository(app, appScope)
    val tracking = TrackingEngine(appScope, settings.state)
    val gnss = GnssRepository()
    val simulator = SimulatorSource(appScope, tracking, settings.state, gnss)
    val motion = MotionSource(app)
    val rawLogger = com.sappy.speedome.tracking.RawLogger(app)
    val sources = SourceManager(app, appScope, settings.state, tracking, gnss, rawLogger)
    val db = SpeedoDatabase.create(app)
    val liveRoute = com.sappy.speedome.tracking.LiveRoute(appScope, tracking.state)
    val recorder = SessionRecorder(appScope, db.trips(), tracking, settings.state)

    init {
        recorder.start()
        appScope.launch(Dispatchers.Main) {
            visible.collect { v ->
                motion.setVisible(v)
                sources.setVisible(v)
            }
        }
        appScope.launch {
            settings.state.collect { com.sappy.speedome.ui.Fmt.units = it.units }
        }
        appScope.launch {
            settings.state.map { it.devMode && it.rawLog }.distinctUntilChanged().collect(rawLogger::setEnabled)
        }
        appScope.launch {
            settings.state.map { it.devMode && it.simulator }.distinctUntilChanged().collect { on ->
                if (on) simulator.start() else simulator.stop()
            }
        }
    }
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
