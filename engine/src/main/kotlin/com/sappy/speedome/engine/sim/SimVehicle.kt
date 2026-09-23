package com.sappy.speedome.engine.sim

import com.sappy.speedome.engine.Geo
import com.sappy.speedome.engine.Mode
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Pedal and steering input. When [targetMps] is set an autopilot chases that speed instead. */
data class SimInput(
    val throttle: Boolean = false,
    val brake: Boolean = false,
    val hardBrake: Boolean = false,
    val sport: Boolean = false,
    val steer: Double = 0.0,
    val targetMps: Double? = null,
    val maxAccel: Double? = null,
)

/**
 * Simple longitudinal + yaw vehicle model (same physics as docs/theme-lab.html).
 * Position is kept in metres east/north of the start point; [heading] is radians clockwise from north.
 */
class SimVehicle(val startLat: Double = 35.6812, val startLon: Double = 139.7671) {
    var v = 0.0
        private set
    var accel = 0.0
        private set
    var lateral = 0.0
        private set
    var heading = 0.35
        private set
    var eastM = 0.0
        private set
    var northM = 0.0
        private set
    var altM = 40.0
        private set
    var cadenceSpm = 0.0
        private set

    /** True distance travelled, for checking the engine. */
    var odometerM = 0.0
        private set

    val lat: Double get() = Geo.offset(startLat, startLon, eastM, northM).first
    val lon: Double get() = Geo.offset(startLat, startLon, eastM, northM).second

    fun step(dt: Double, input: SimInput, mode: Mode) {
        val a = when {
            input.targetMps != null -> {
                val up = input.maxAccel ?: if (mode == Mode.STEP) 1.2 else 2.6
                ((input.targetMps - v) * 0.8).coerceIn(-4.5, up)
            }
            mode == Mode.STEP -> {
                val tgt = if (input.throttle) (if (input.sport) 11.5 else 5.2) / 3.6 else 0.0
                if (input.brake || input.hardBrake) -3.0 else ((tgt - v) * if (tgt > v) 1.1 else 2.2).coerceIn(-3.0, 1.6)
            }
            else -> {
                val power = if (input.sport) 190.0 else 72.0
                val aMax = if (input.sport) 6.8 else 3.6
                val drag = if (v > 0.05) 0.12 + 3.3e-4 * v * v else 0.0
                val aThrottle = if (input.throttle) min(aMax, power / max(v, 2.0)) else 0.0
                val aBrake = if (input.hardBrake) 9.3 else if (input.brake) 5.2 else 0.0
                aThrottle - drag - if (v > 0.01) aBrake else 0.0
            }
        }
        v = max(0.0, v + a * dt)
        accel = if (v > 0 || a > 0) a else 0.0
        val yaw = input.steer * 0.6 * (v / 6).coerceIn(0.0, 1.0) / (1 + v / 30)
        lateral = (v * yaw).coerceIn(-9.0, 9.0)
        heading = (heading + yaw * dt).mod(2 * Math.PI)
        eastM += v * sin(heading) * dt
        northM += v * cos(heading) * dt
        odometerM += v * dt
        altM = 40 + 12 * sin(northM / 900) + 6 * cos(eastM / 700)
        val kmh = v * 3.6
        cadenceSpm = when {
            mode != Mode.STEP || kmh < 1 -> 0.0
            kmh < 7.5 -> 96 + (kmh - 3) * 7
            else -> 150 + (kmh - 8) * 3.2
        }
    }
}
