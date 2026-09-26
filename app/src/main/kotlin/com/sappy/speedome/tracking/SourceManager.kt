package com.sappy.speedome.tracking

import android.content.Context
import com.sappy.speedome.engine.Mode
import com.sappy.speedome.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Decides which sensors run. Real GPS and the step sensors run only while tracking is [active] (the
 * app is on screen, or a trip records in the background); the simulator replaces real GPS when
 * developer options enable it.
 */
class SourceManager(
    context: Context,
    scope: CoroutineScope,
    settings: StateFlow<AppSettings>,
    tracking: TrackingEngine,
    gnss: GnssRepository,
    raw: RawLogger,
    active: StateFlow<Boolean>,
) {
    private val appContext = context.applicationContext
    private val gps = GpsSource(appContext, tracking, gnss, raw)
    private val steps = StepSource(appContext, tracking)
    private val permissionEpoch = MutableStateFlow(0)
    private val replaying = MutableStateFlow(false)

    private data class Wanted(val active: Boolean, val simulated: Boolean, val mode: Mode, val epoch: Int)

    val stepSensorsAvailable: Boolean get() = steps.available

    init {
        scope.launch(Dispatchers.Main) {
            combine(active, settings, permissionEpoch, replaying) { on, s, epoch, replay ->
                Wanted(on, (s.devMode && s.simulator) || replay, s.mode, epoch)
            }.distinctUntilChanged().collect(::apply)
        }
    }

    /** A developer file replay stands in for real GPS, like the simulator. */
    fun setReplaying(on: Boolean) {
        replaying.value = on
    }

    /** Call after the user grants or revokes a permission so sources re-evaluate. */
    fun permissionsChanged() {
        permissionEpoch.value++
    }

    private var nmeaWanted = false
    private var visible = true

    /** NMEA is parsed only while the nerd page is on screen and the app is visible. */
    @Synchronized
    fun setNmeaEnabled(on: Boolean) {
        nmeaWanted = on
        gps.setNmeaEnabled(nmeaWanted && visible)
    }

    @Synchronized
    fun setVisible(visible: Boolean) {
        this.visible = visible
        gps.setNmeaEnabled(nmeaWanted && visible)
    }

    private fun apply(w: Wanted) {
        val p = PermissionState.of(appContext)
        if (w.active && !w.simulated && p.fineLocation) gps.start() else gps.stop()
        if (w.active && !w.simulated && w.mode == Mode.STEP && p.activityRecognition) steps.start() else steps.stop()
    }
}
