package com.sappy.speedome.ui

import android.os.SystemClock
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.sappy.speedome.engine.EngineState
import com.sappy.speedome.engine.GpsQuality
import com.sappy.speedome.engine.Mode
import com.sappy.speedome.engine.view
import com.sappy.speedome.gauges.GaugeFrame
import com.sappy.speedome.gauges.GaugeStats
import com.sappy.speedome.gauges.GaugeTarget
import com.sappy.speedome.gauges.SpeedUnit
import com.sappy.speedome.gauges.GaugeTheme
import com.sappy.speedome.gauges.GpsDot
import com.sappy.speedome.gauges.ThemeOptions
import com.sappy.speedome.settings.AppSettings
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** Holds the latest [GaugeFrame]; gauges read it in their draw phase (no recomposition per frame). */
@Stable
class GaugeDriver {
    var frame by mutableStateOf(GaugeFrame())
        internal set

    internal var sweepStartNanos: Long? = null

    /** Plays the 0 → max → 0 startup sweep (docs/plan.md §9). */
    fun sweep() {
        sweepStartNanos = SystemClock.elapsedRealtimeNanos()
    }
}

private const val SWEEP_S = 1.3

/** Night Focus speeds are chosen in km/h; in mph they become the nearest round 10 (260 → 160). */
private fun displayRound(kmh: Int, units: SpeedUnit): Int =
    if (units == SpeedUnit.MPH) ((kmh * 0.621371 / 10).roundToInt() * 10) else kmh
private const val RANGE_ANIM_S = 0.45

/**
 * The per-frame loop behind every gauge: spring needle toward the engine's display target, steady
 * integer readout, animated auto-range changes and the startup sweep.
 */
@Composable
fun rememberGaugeDriver(
    engine: StateFlow<EngineState>,
    theme: GaugeTheme,
    settings: AppSettings,
    heading: () -> Float? = { null },
    batteryLow: () -> Boolean = { false },
): GaugeDriver {
    val driver = remember { GaugeDriver() }
    val context = LocalContext.current
    val animationsOff = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val currentTheme by rememberUpdatedState(theme)
    val currentSettings by rememberUpdatedState(settings)
    val currentHeading by rememberUpdatedState(heading)
    val currentBattery by rememberUpdatedState(batteryLow)

    LaunchedEffect(Unit) {
        if (settings.startupSweep && !animationsOff) driver.sweep()
    }
    LaunchedEffect(engine) {
        var v = 0.0
        var vel = 0.0
        var readout = 0
        var last = 0L
        var rangeFrom = -1
        var rangeTo = -1
        var rangeAt = 0L
        var nightUpper = 0.0
        var scroll = 0.0
        val start = SystemClock.elapsedRealtimeNanos()
        while (true) {
            withFrameNanos { frameNanos ->
                val now = SystemClock.elapsedRealtimeNanos()
                val s = currentSettings
                val view = engine.value.view(now)
                val spring = currentTheme.spring
                val u = s.engine.unitsPerMps // everything on the gauge is in display units
                val target = view.displayTargetMps(now, s.predictNeedle) * u
                val dt = if (last == 0L) 1.0 / 60 else ((frameNanos - last) / 1e9).coerceIn(0.0, 0.05)
                last = frameNanos
                repeat(2) {
                    val h = dt / 2
                    val a = spring.omega * spring.omega * (target - v) - 2 * spring.zeta * spring.omega * vel
                    vel += a * h
                    v = max(0.0, v + vel * h)
                }
                if (abs(v - readout) > 0.6 || (v < 0.5 && readout != 0)) readout = v.roundToInt()

                val nightFocus = displayRound(s.nightFocusKmh, s.units)
                val options = ThemeOptions(
                    retroCream = s.retroCream, digital = s.digital, accent = Color(s.accent.argb), average = s.average,
                    nightFocusKmh = nightFocus, nightMaxKmh = displayRound(s.nightMaxKmh, s.units), nightBrightness = s.nightBrightness,
                    shaders = s.gpuEffects, units = s.units,
                )
                val wanted = currentTheme.fixedRangeKmh(options) ?: view.rangeKmh
                if (rangeTo < 0) {
                    rangeFrom = wanted
                    rangeTo = wanted
                } else if (wanted != rangeTo) {
                    rangeFrom = rangeTo
                    rangeTo = wanted
                    rangeAt = now
                }
                val p = ((now - rangeAt) / 1e9 / RANGE_ANIM_S).coerceIn(0.0, 1.0)
                val eased = if (p < .5) 4 * p * p * p else 1 - (-2 * p + 2).pow(3) / 2
                val rangeNow = rangeFrom + (rangeTo - rangeFrom) * eased

                var needle = v
                driver.sweepStartNanos?.let { t0 ->
                    val t = (now - t0) / 1e9
                    if (t in 0.0..SWEEP_S) needle = max(v, rangeNow * sin(PI * t / SWEEP_S)) else driver.sweepStartNanos = null
                }

                val upperTarget = when {
                    v >= nightFocus - 5 -> 1.0
                    v < nightFocus - 10 -> 0.0
                    else -> if (nightUpper > .5) 1.0 else 0.0
                }
                nightUpper += (upperTarget - nightUpper) * minOf(1.0, dt * 3.5)
                scroll += v / u * dt

                driver.frame = GaugeFrame(
                    needleKmh = needle.toFloat(),
                    readout = readout,
                    rangeKmh = rangeNow.toFloat(),
                    rangeFromKmh = rangeFrom,
                    rangeToKmh = rangeTo,
                    rangeProgress = p.toFloat(),
                    stats = GaugeStats(
                        distanceM = view.distanceM,
                        avgMovingKmh = view.avgMovingMps * u,
                        avgOverallKmh = view.avgOverallMps * u,
                        maxKmh = view.maxMps * u,
                        elapsedS = view.elapsedS,
                        steps = view.steps,
                        stepMode = s.mode == Mode.STEP,
                    ),
                    gps = when (view.quality) {
                        GpsQuality.GOOD -> GpsDot.GOOD
                        GpsQuality.FALLBACK -> GpsDot.FALLBACK
                        GpsQuality.NONE -> GpsDot.NONE
                    },
                    options = options,
                    accelKmhS = (view.accelMps2 * u).toFloat(),
                    timeS = (now - start) / 1e9,
                    nightUpper = nightUpper.toFloat(),
                    scrollM = scroll.toFloat(),
                    target = view.target?.let { GaugeTarget(it.progress, it.remainingM, it.arrived) },
                    headingDeg = currentHeading() ?: view.lastFix?.bearing?.toFloat(),
                    altitudeM = view.lastFix?.altM,
                    batteryLow = currentBattery(),
                )
            }
        }
    }
    return driver
}
