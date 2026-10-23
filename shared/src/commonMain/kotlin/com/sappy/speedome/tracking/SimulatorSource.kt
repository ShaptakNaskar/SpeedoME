package com.sappy.speedome.tracking

import com.sappy.speedome.gauges.fmt
import kotlin.concurrent.Volatile
import kotlin.math.PI
import kotlin.math.abs
import com.sappy.speedome.utcMillis
import com.sappy.speedome.monotonicNanos
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
    private val gnss: GnssRepository,
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
    @Volatile
    private var spikePending = false // set from the UI, read by the simulator loop
    private var lastTruth = 0L
    private var lastSky = 0L

    fun start() {
        if (job != null) return
        val r = SimRig(
            seed = (utcMillis() % 100_000).toInt(),
            startUtcMillis = utcMillis(),
            startNanos = monotonicNanos(),
            emitTicks = false,
        )
        rig = r
        job = scope.launch {
            while (isActive) {
                val events = ArrayList<EngineEvent>()
                apply(r, _controls.value)
                val target = monotonicNanos()
                var steps = 0
                while (r.nowNanos < target && steps++ < 250) {
                    val dt = min(0.02, (target - r.nowNanos) / 1e9)
                    if (dt < 1e-4) break
                    events += r.advance(dt)
                }
                events.forEach(tracking::submit)
                val now = monotonicNanos() / 1_000_000
                if (now - lastSky >= 1000) {
                    lastSky = now
                    publishSky(r)
                }
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
        gnss.clearSatellites()
        job = null
        rig = null
        _truth.value = Truth()
    }

    fun update(transform: (Controls) -> Controls) {
        _controls.value = transform(_controls.value)
    }

    /** The next fix will read ~200 km/h: watch the filter throw it away. */
    fun spike() {
        spikePending = true
    }

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

    private val sky = kotlin.random.Random(5).let { rnd ->
        listOf(Constellation.GPS to 9, Constellation.GLONASS to 7, Constellation.GALILEO to 7, Constellation.BEIDOU to 8, Constellation.IRNSS to 4)
            .flatMap { (c, n) -> List(n) { Triple(c, 1 + rnd.nextInt(32), floatArrayOf(rnd.nextFloat() * 360, 8 + rnd.nextFloat() * 80, rnd.nextFloat() * 6)) } }
    }

    /** Pretend satellites and NMEA so the Nerd page works with the simulator. */
    private fun publishSky(r: SimRig) {
        val c = _controls.value
        gnss.onHardware("SpeedoME simulator")
        gnss.onSatellites(sky.map { (con, id, p) ->
            val cn0 = if (c.signal) 30 + p[1] * .18f + p[2] else 10f
            Satellite(con, id, cn0, p[0], p[1], c.signal && cn0 > 30 && p[1] > 12, if (id % 3 == 0) 1_176_450_000f else 1_575_420_000f)
        })
        if (!c.signal) return
        // UTC from the ISO form: yyyy-MM-ddTHH:mm:ss…
        val iso = kotlin.time.Clock.System.now().toString()
        val hms = iso.substring(11, 13) + iso.substring(14, 16) + iso.substring(17, 19) + ".00"
        val date = iso.substring(8, 10) + iso.substring(5, 7) + iso.substring(2, 4)
        val lat = r.vehicle.lat
        val lon = r.vehicle.lon
        fun dm(v: Double, w: Int) = abs(v).toInt().toString().padStart(w, '0') + fmt((abs(v) % 1) * 60, 4).padStart(7, '0')
        val body = "GNRMC,$hms,A,${dm(lat, 2)},${if (lat >= 0) "N" else "S"},${dm(lon, 3)},${if (lon >= 0) "E" else "W"}," +
            "${fmt(r.vehicle.v * 1.943844, 2)},${fmt(r.vehicle.heading * 180 / PI, 1)},$date,,,A"
        gnss.onNmea("$" + body + "*" + com.sappy.speedome.engine.replay.Nmea.checksum(body))
    }
}
