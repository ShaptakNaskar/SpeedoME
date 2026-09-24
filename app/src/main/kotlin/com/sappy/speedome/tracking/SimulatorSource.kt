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
    private var spikePending = false
    private var lastTruth = 0L
    private var lastSky = 0L

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

    private val sky = kotlin.random.Random(5).let { rnd ->
        listOf(android.location.GnssStatus.CONSTELLATION_GPS to 9, android.location.GnssStatus.CONSTELLATION_GLONASS to 7,
            android.location.GnssStatus.CONSTELLATION_GALILEO to 7, android.location.GnssStatus.CONSTELLATION_BEIDOU to 8,
            7 /* IRNSS (NavIC), API 29 */ to 4)
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
        val t = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
        val hms = "%02d%02d%02d.00".format(java.util.Locale.ROOT, t.hour, t.minute, t.second)
        val lat = r.vehicle.lat
        val lon = r.vehicle.lon
        fun dm(v: Double, w: Int) = "%0${w}d%07.4f".format(java.util.Locale.ROOT, kotlin.math.abs(v).toInt(), (kotlin.math.abs(v) % 1) * 60)
        val body = "GNRMC,$hms,A,${dm(lat, 2)},${if (lat >= 0) "N" else "S"},${dm(lon, 3)},${if (lon >= 0) "E" else "W"},%.2f,%.1f,%02d%02d%02d,,,A"
            .format(java.util.Locale.ROOT, r.vehicle.v * 1.943844, Math.toDegrees(r.vehicle.heading), t.dayOfMonth, t.monthValue, t.year % 100)
        gnss.onNmea("$" + body + "*" + com.sappy.speedome.engine.replay.Nmea.checksum(body))
    }
}
