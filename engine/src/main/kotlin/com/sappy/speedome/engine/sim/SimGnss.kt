package com.sappy.speedome.engine.sim

import com.sappy.speedome.engine.FixEvent
import com.sappy.speedome.engine.Geo
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/** Standard normal sample (Box–Muller). */
fun Random.gauss(): Double {
    var u = 0.0
    while (u == 0.0) u = nextDouble()
    return sqrt(-2 * ln(u)) * cos(2 * Math.PI * nextDouble())
}

/**
 * A pretend GNSS receiver: correlated (Gauss–Markov) position error, Doppler speed noise,
 * and switchable faults — signal loss, missing speed field, and one-shot 200 km/h spikes.
 */
class SimGnss(private val rnd: Random) {
    var hz = 1.0
    var doppler = true
    var signal = true

    private var errEast = 0.0
    private var errNorth = 0.0
    private var nextS = 0.0
    private var spikeNext = false

    /** The next fix will read ~200 km/h and jump ~60 m. */
    fun injectSpike() {
        spikeNext = true
    }

    /** Advances receiver state by [dt]; returns a fix when one is due at [nowS]. */
    fun step(dt: Double, nowS: Double, vehicle: SimVehicle, tNanos: Long, utcMillis: Long): FixEvent? {
        val tau = 25.0
        val sigma = 2.4
        val k = sqrt(2 * dt / tau)
        errEast += -errEast / tau * dt + sigma * k * rnd.gauss()
        errNorth += -errNorth / tau * dt + sigma * k * rnd.gauss()
        if (nowS < nextS) return null
        nextS = nowS + 1 / hz
        if (!signal) return null
        var east = vehicle.eastM + errEast
        var north = vehicle.northM + errNorth
        var speed: Double? = null
        var speedAcc: Double? = null
        if (doppler) {
            speed = abs(vehicle.v + rnd.gauss() * 0.22)
            speedAcc = 0.35 + rnd.nextDouble() * 0.25
        }
        if (spikeNext) {
            spikeNext = false
            if (speed != null) speed = (200 + rnd.nextDouble() * 20) / 3.6
            east += 55
            north += 25
        }
        val (lat, lon) = Geo.offset(vehicle.startLat, vehicle.startLon, east, north)
        return FixEvent(
            tNanos = tNanos,
            utcMillis = utcMillis,
            lat = lat,
            lon = lon,
            hAcc = (3.2 + abs(rnd.gauss()) * 1.3).toFloat(),
            altM = vehicle.altM + rnd.gauss() * 2,
            vAcc = (4 + rnd.nextDouble() * 2).toFloat(),
            speed = speed?.toFloat(),
            speedAcc = speedAcc?.toFloat(),
            bearing = Math.toDegrees(vehicle.heading).toFloat(),
            bearingAcc = 5f,
            isMock = true,
        )
    }
}
