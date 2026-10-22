package com.sappy.speedome.engine

import com.sappy.speedome.engine.sim.SimRig

/** Drives the engine from the simulator, recording truth vs. output at every fix. */
class Harness(seed: Int = 7) {
    val rig = SimRig(seed)
    var state = EngineState()

    data class Sample(val tS: Double, val trueMps: Double, val outMps: Double, val rawMps: Double?)

    val samples = mutableListOf<Sample>()

    val settings: EngineSettings get() = EngineSettings(mode = rig.mode)
    val view: TrackView get() = state.view(rig.nowNanos)

    fun run(seconds: Double) {
        rig.run(seconds) { e ->
            state = Engine.reduce(state, e, settings)
            if (e is FixEvent) samples += Sample(rig.timeS, rig.vehicle.v, state.filter.output, e.speed?.toDouble())
        }
    }

    /** Runs the simulator while the engine sees nothing (app process dead). */
    fun runUnseen(seconds: Double) = rig.run(seconds) { }

    fun command(c: Command) {
        state = Engine.reduce(state, CommandEvent(rig.nowNanos, rig.nowUtcMillis, c), settings)
    }

    fun samplesAfter(tS: Double) = samples.filter { it.tS > tS }
}

fun Double.kmh() = this * 3.6

fun kmhToMps(kmh: Double) = kmh / 3.6
