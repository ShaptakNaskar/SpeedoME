package com.sappy.speedome.engine.sim

import com.sappy.speedome.engine.EngineEvent
import com.sappy.speedome.engine.Mode
import com.sappy.speedome.engine.StepCountEvent
import com.sappy.speedome.engine.StepDetectedEvent
import com.sappy.speedome.engine.TickEvent
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/** Autopilot profiles, matching the Theme Lab and the developer panel. Speeds in km/h. */
enum class SimPreset(
    val label: String,
    val mode: Mode,
    val kmh: Double,
    val spreadKmh: Double,
    val everyS: Double,
    val stops: Boolean = false,
    val maxAccel: Double? = null,
) {
    WALK("Walk", Mode.STEP, 5.2, 0.8, 14.0),
    CYCLE("Cycle", Mode.DRIVE, 22.0, 5.0, 10.0),
    CITY("City", Mode.DRIVE, 42.0, 10.0, 9.0, stops = true),
    HIGHWAY("Highway", Mode.DRIVE, 112.0, 12.0, 12.0),
    TRAIN("Train", Mode.DRIVE, 295.0, 15.0, 20.0, maxAccel = 0.55),
    JET("Jet", Mode.DRIVE, 860.0, 25.0, 25.0, maxAccel = 2.4),
}

/**
 * Vehicle + receiver + autopilot on one clock. [advance] returns the events a real phone would
 * deliver in that slice of time: fixes, step detector/counter events and ~1 Hz ticks.
 */
class SimRig(seed: Int = 7, private val startUtcMillis: Long = 1_790_000_000_000L) {
    private val rnd = Random(seed)
    val vehicle = SimVehicle()
    val gnss = SimGnss(rnd)

    var mode = Mode.DRIVE
    var preset: SimPreset? = null
        set(value) {
            field = value
            nextRetarget = 0.0
            stopUntil = -1.0
            if (value != null) mode = value.mode
        }
    var cruiseMps: Double? = null
    var manual = SimInput()

    var timeS = 0.0
        private set

    private var target = 0.0
    private var nextRetarget = 0.0
    private var stopUntil = -1.0
    private var nextTick = 0.0
    private var stepCarry = 0.0
    private var stepTotal = 52_000L

    val nowNanos: Long get() = (timeS * 1e9).toLong()
    val nowUtcMillis: Long get() = startUtcMillis + (timeS * 1000).toLong()

    fun advance(dt: Double): List<EngineEvent> {
        vehicle.step(dt, currentInput(), mode)
        timeS += dt
        val t = nowNanos
        val utc = nowUtcMillis
        val out = ArrayList<EngineEvent>(3)
        gnss.step(dt, timeS, vehicle, t, utc)?.let(out::add)
        if (mode == Mode.STEP) {
            stepCarry += vehicle.cadenceSpm / 60 * dt
            while (stepCarry >= 1) {
                stepCarry -= 1
                stepTotal++
                out += StepDetectedEvent(t)
            }
        }
        if (timeS >= nextTick) {
            nextTick = timeS + 1
            if (mode == Mode.STEP) out += StepCountEvent(t, stepTotal)
            out += TickEvent(t, utc)
        }
        return out
    }

    /** Runs for [seconds] in [dt] steps, handing every event to [onEvent]. */
    fun run(seconds: Double, dt: Double = 0.02, onEvent: (EngineEvent) -> Unit) {
        val end = timeS + seconds
        while (timeS < end - 1e-9) advance(dt).forEach(onEvent)
    }

    private fun currentInput(): SimInput {
        cruiseMps?.let { return SimInput(targetMps = it, steer = manual.steer) }
        val p = preset ?: return manual
        if (timeS >= nextRetarget) {
            target = max(0.0, p.kmh + rnd.gauss() * p.spreadKmh) / 3.6
            nextRetarget = timeS + p.everyS * (0.6 + rnd.nextDouble() * 0.8)
            if (p.stops && rnd.nextDouble() < 0.35) stopUntil = timeS + 8 + rnd.nextDouble() * 12
        }
        val steer = sin(timeS * 0.11) * 0.35 + sin(timeS * 0.037 + 1) * 0.45
        return SimInput(targetMps = if (timeS < stopUntil) 0.0 else target, maxAccel = p.maxAccel, steer = steer)
    }
}
