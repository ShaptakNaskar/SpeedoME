package com.sappy.speedome.ui

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import com.sappy.speedome.engine.EngineState
import com.sappy.speedome.engine.view
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Animated needle value (km/h) and the matching steady integer readout. */
@Stable
class NeedleState {
    var kmh by mutableFloatStateOf(0f)
        internal set
    var readout by mutableIntStateOf(0)
        internal set
}

/**
 * Chases the engine's display target every frame through a spring (ω, ζ per theme, docs/plan.md §9).
 * The integer readout only changes once the value clearly crosses the next number (±0.6).
 */
@Composable
fun rememberNeedle(
    engine: StateFlow<EngineState>,
    predict: Boolean,
    omega: Double = 13.0,
    zeta: Double = 1.0,
): NeedleState {
    val needle = remember { NeedleState() }
    LaunchedEffect(engine, predict, omega, zeta) {
        var v = needle.kmh.toDouble()
        var vel = 0.0
        var last = 0L
        while (true) {
            withFrameNanos { frame ->
                val now = SystemClock.elapsedRealtimeNanos()
                val target = engine.value.view(now).displayTargetMps(now, predict) * 3.6
                val dt = if (last == 0L) 1.0 / 60 else ((frame - last) / 1e9).coerceIn(0.0, 0.05)
                last = frame
                repeat(2) {
                    val h = dt / 2
                    val a = omega * omega * (target - v) - 2 * zeta * omega * vel
                    vel += a * h
                    v = max(0.0, v + vel * h)
                }
                needle.kmh = v.toFloat()
                if (abs(v - needle.readout) > 0.6 || (v < 0.5 && needle.readout != 0)) needle.readout = v.roundToInt()
            }
        }
    }
    return needle
}
