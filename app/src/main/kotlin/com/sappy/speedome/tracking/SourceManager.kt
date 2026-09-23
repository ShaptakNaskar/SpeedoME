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
 * Decides which sensors run. Real GPS and the step sensors run only while the tracking service
 * is active; the simulator replaces real GPS when developer options enable it.
 */
class SourceManager(
    context: Context,
    scope: CoroutineScope,
    settings: StateFlow<AppSettings>,
    tracking: TrackingEngine,
    gnss: GnssRepository,
) {
    private val appContext = context.applicationContext
    private val gps = GpsSource(appContext, tracking, gnss)
    private val steps = StepSource(appContext, tracking)
    private val serviceActive = MutableStateFlow(false)
    private val permissionEpoch = MutableStateFlow(0)

    private data class Wanted(val active: Boolean, val simulated: Boolean, val mode: Mode, val epoch: Int)

    val stepSensorsAvailable: Boolean get() = steps.available

    init {
        scope.launch(Dispatchers.Main) {
            combine(serviceActive, settings, permissionEpoch) { active, s, epoch ->
                Wanted(active, s.devMode && s.simulator, s.mode, epoch)
            }.distinctUntilChanged().collect(::apply)
        }
    }

    fun setServiceActive(active: Boolean) {
        serviceActive.value = active
    }

    /** Call after the user grants or revokes a permission so sources re-evaluate. */
    fun permissionsChanged() {
        permissionEpoch.value++
    }

    fun setNmeaEnabled(on: Boolean) = gps.setNmeaEnabled(on)

    private fun apply(w: Wanted) {
        val p = PermissionState.of(appContext)
        if (w.active && !w.simulated && p.fineLocation) gps.start() else gps.stop()
        if (w.active && !w.simulated && w.mode == Mode.STEP && p.activityRecognition) steps.start() else steps.stop()
    }
}
