package com.sappy.speedome

import android.app.Application
import androidx.compose.runtime.staticCompositionLocalOf
import com.sappy.speedome.settings.SettingsRepository
import com.sappy.speedome.tracking.GnssRepository
import com.sappy.speedome.tracking.MotionSource
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
    val settings = SettingsRepository(app, appScope)
    val tracking = TrackingEngine(appScope, settings.state)
    val simulator = SimulatorSource(appScope, tracking, settings.state)
    val gnss = GnssRepository()
    val motion = MotionSource(app)
    val sources = SourceManager(app, appScope, settings.state, tracking, gnss)

    init {
        appScope.launch {
            settings.state.map { it.devMode && it.simulator }.distinctUntilChanged().collect { on ->
                if (on) simulator.start() else simulator.stop()
            }
        }
    }
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
