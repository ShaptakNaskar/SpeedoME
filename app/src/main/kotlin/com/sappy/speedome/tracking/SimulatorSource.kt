package com.sappy.speedome.tracking

import android.os.SystemClock
import com.sappy.speedome.engine.EngineEvent
import com.sappy.speedome.engine.sim.SimInput
import com.sappy.speedome.engine.sim.SimPreset
import com.sappy.speedome.engine.sim.SimRig
import com.sappy.speedome.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * Developer simulator: drives the shared [SimRig] in real time on the app's clock and feeds its
 * fixes and step events into the [TrackingEngine], exactly where real GPS will plug in (M3).
 */
class SimulatorSource(
    private val scope: CoroutineScope,
    private val tracking: TrackingEngine,
    private val settings: StateFlow<AppSettings>,
) {
    data class Controls(
        val preset: SimPreset? = SimPreset.CITY,
        val signal: Boolean = true,
        val doppler: Boolean = true,
        val hz: Double = 1.0,
        val cruiseKmh: Double? = null,
        val throttle: Boolean = false,
        val brake: Boolean = false,
    )

    data class Truth(val running: Boolean = false, val speedMps: Double = 0.0, val odometerM: Double = 0.0)

    private val _controls = MutableStateFlow(Controls())
    val controls: StateFlow<Controls> = _controls.asStateFlow()
    private val _truth = MutableStateFlow(Truth())
    val truth: StateFlow<Truth> = _truth.asStateFlow()

    private var job: Job? = null
    private var rig: SimRig? = null
    private var spikePending = false
    private var lastTruth = 0L

    fun start() {
        if (job != null) return
        val r = SimRig(
            seed = (System.currentTimeMillis() % 100_000).toInt(),
            startUtcMillis = System.currentTimeMillis(),
            startNanos = SystemClock.elapsedRealtimeNanos(),
            emitTicks = false,
        )
        rig = r
        job = scope.launch {
            while (isActive) {
                val events = ArrayList<EngineEvent>()
                synchronized(this@SimulatorSource) {
                    apply(r, _controls.value)
                    val target = SystemClock.elapsedRealtimeNanos()
                    var steps = 0
                    while (r.nowNanos < target && steps++ < 250) {
                        val dt = min(0.02, (target - r.nowNanos) / 1e9)
                        if (dt < 1e-4) break
                        events += r.advance(dt)
                    }
                }
                events.forEach(tracking::submit)
                val now = SystemClock.elapsedRealtime()
                if (now - lastTruth >= 250) { // the readout only needs a few updates a second
                    lastTruth = now
                    _truth.value = Truth(true, r.vehicle.v, r.vehicle.odometerM)
                }
                delay(20)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        rig = null
        _truth.value = Truth()
    }

    fun update(transform: (Controls) -> Controls) {
        _controls.value = transform(_controls.value)
    }

    /** The next fix will read ~200 km/h: watch the filter throw it away. */
    fun spike() = synchronized(this) { spikePending = true }

    private fun apply(r: SimRig, c: Controls) {
        if (r.preset != c.preset) r.preset = c.preset
        if (c.preset == null) r.mode = settings.value.mode
        r.cruiseMps = c.cruiseKmh?.let { it / 3.6 }
        r.manual = SimInput(throttle = c.throttle, brake = c.brake)
        r.gnss.signal = c.signal
        r.gnss.doppler = c.doppler
        r.gnss.hz = c.hz
        if (spikePending) {
            r.gnss.injectSpike()
            spikePending = false
        }
    }
}
