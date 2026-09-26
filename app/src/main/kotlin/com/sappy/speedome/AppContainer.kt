package com.sappy.speedome

import android.app.Application
import androidx.compose.runtime.staticCompositionLocalOf
import com.sappy.speedome.data.SpeedoDatabase
import com.sappy.speedome.engine.Command
import com.sappy.speedome.engine.SessionKind
import com.sappy.speedome.settings.SettingsRepository
import com.sappy.speedome.tracking.GnssRepository
import com.sappy.speedome.tracking.MotionSource
import com.sappy.speedome.tracking.PermissionState
import com.sappy.speedome.tracking.SessionRecorder
import com.sappy.speedome.tracking.SimulatorSource
import com.sappy.speedome.tracking.SourceManager
import com.sappy.speedome.tracking.SpeedLimitAlert
import com.sappy.speedome.tracking.TrackingEngine
import com.sappy.speedome.tracking.TrackingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Hand-wired app singletons (D26: no DI framework). */
class AppContainer(private val app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** True while the activity is started (on screen). The live meter only runs then. */
    val visible = MutableStateFlow(false)

    /** True while [TrackingService] is in the foreground, which it only is while a trip records. */
    val recordingService = MutableStateFlow(false)

    /**
     * Sensors, the engine clock and the simulator run only while this is true: the app is on screen,
     * or a trip is recording in the background. Closing the app without recording ends the live meter
     * and leaves nothing running (docs/plan.md §7).
     */
    val trackingActive: StateFlow<Boolean> = combine(visible, recordingService) { v, r -> v || r }
        .stateIn(appScope, SharingStarted.Eagerly, false)

    val settings = SettingsRepository(app, appScope)
    val tracking = TrackingEngine(appScope, settings.state, trackingActive)
    val gnss = GnssRepository()
    val simulator = SimulatorSource(appScope, tracking, settings.state, gnss)
    val motion = MotionSource(app)
    val rawLogger = com.sappy.speedome.tracking.RawLogger(app)
    val sources = SourceManager(app, appScope, settings.state, tracking, gnss, rawLogger, trackingActive)
    val db = SpeedoDatabase.create(app)
    val liveRoute = com.sappy.speedome.tracking.LiveRoute(appScope, tracking.state)
    val replay = com.sappy.speedome.tracking.FileReplay(appScope, tracking)
    val recorder = SessionRecorder(appScope, db.trips(), tracking, settings.state)
    val limitAlert = SpeedLimitAlert(app, appScope, tracking.state, settings.state, trackingActive)

    init {
        recorder.start()
        appScope.launch(Dispatchers.Main) {
            visible.collect { v ->
                motion.setVisible(v)
                sources.setVisible(v)
            }
        }
        // A trip keeps recording in the background through the location service, which may only be
        // started while the app is on screen: start it whenever both are true.
        appScope.launch(Dispatchers.Main) {
            combine(visible, tracking.state.map { it.session.kind == SessionKind.TRIP }.distinctUntilChanged()) { v, trip -> v && trip }
                .distinctUntilChanged()
                .collect { if (it) ensureRecordingService() }
        }
        appScope.launch {
            var was = false
            trackingActive.collect { on ->
                if (was && !on) {
                    replay.stop()
                    tracking.command(Command.Standby) // the live meter ends with the app; a trip ignores this
                }
                was = on
            }
        }
        appScope.launch {
            settings.state.collect { com.sappy.speedome.ui.Fmt.units = it.units }
        }
        appScope.launch {
            replay.progress.map { it.running }.distinctUntilChanged().collect(sources::setReplaying)
        }
        appScope.launch {
            settings.state.map { it.devMode && it.rawLog }.distinctUntilChanged().collect(rawLogger::setEnabled)
        }
        appScope.launch {
            combine(settings.state.map { it.devMode && it.simulator }, trackingActive) { sim, on -> sim && on }
                .distinctUntilChanged()
                .collect { on -> if (on) simulator.start() else simulator.stop() }
        }
    }

    /** Starts the recording service if a trip is active and the app is on screen (call after permission changes too). */
    fun ensureRecordingService() {
        val trip = tracking.state.value.session.kind == SessionKind.TRIP
        if (visible.value && trip && PermissionState.of(app).canTrack) TrackingService.start(app)
    }
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
