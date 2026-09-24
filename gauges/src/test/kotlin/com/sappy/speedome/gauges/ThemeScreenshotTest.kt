package com.sappy.speedome.gauges

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Golden images of every theme in representative states (docs/plan.md §11). Record with
 * `./gradlew :gauges:recordRoborazziDebug`; `./gradlew check` verifies them.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w800dp-h800dp-mdpi")
class ThemeScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val trip = GaugeStats(distanceM = 12_400.0, avgMovingKmh = 41.0, avgOverallKmh = 33.0, maxKmh = 88.0, elapsedS = 1320.0)

    private val states = mapOf(
        "idle" to GaugeFrame(),
        "mid" to GaugeFrame(needleKmh = 62f, readout = 62, rangeKmh = 80f, rangeFromKmh = 80, rangeToKmh = 80, stats = trip),
        "high" to GaugeFrame(needleKmh = 151f, readout = 151, rangeKmh = 160f, rangeFromKmh = 160, rangeToKmh = 160, stats = trip.copy(maxKmh = 152.0)),
        "range_change" to GaugeFrame(needleKmh = 58f, readout = 58, rangeKmh = 70f, rangeFromKmh = 60, rangeToKmh = 80, rangeProgress = .5f, stats = trip),
    )

    private data class Shot(val name: String, val frame: GaugeFrame, val landscape: Boolean = false)

    /** setContent may only run once per test, so the shots are driven through state. */
    private fun shoot(theme: GaugeTheme, shots: List<Shot>) {
        var current by mutableStateOf(shots.first())
        compose.setContent {
            val shot = current
            GaugeView(theme, { shot.frame }, if (shot.landscape) Modifier.size(780.dp, 360.dp) else Modifier.size(390.dp, 640.dp))
        }
        for (shot in shots) {
            current = shot
            compose.waitForIdle()
            compose.onRoot().captureRoboImage("src/test/screenshots/${theme.id}_${shot.name}.png")
        }
    }

    private fun standard() = states.map { (name, frame) -> Shot(name, frame) } + Shot("landscape", states.getValue("mid"), landscape = true)

    /** The same frame on the gradient fallback path (Android < 13, or effects turned off). */
    private fun noShader(frame: GaugeFrame) = Shot("noshader", frame.copy(options = frame.options.copy(shaders = false)))

    @Test
    fun retro() = shoot(RetroTheme, standard() + Shot("cream", states.getValue("mid").copy(options = ThemeOptions(retroCream = true))) + noShader(states.getValue("mid")) +
        Shot("mph", states.getValue("mid").copy(options = ThemeOptions(units = SpeedUnit.MPH))))

    @Test
    fun modern() = shoot(ModernTheme, standard() + Shot("blue", states.getValue("mid").copy(options = ThemeOptions(accent = Color(0xFF4DA3FF)))) + noShader(states.getValue("mid")) +
        Shot("target", states.getValue("mid").copy(target = GaugeTarget(.62f, 3_800.0, false))))

    @Test
    fun digital() = shoot(
        DigitalTheme,
        standard() + Shot("led", states.getValue("mid").copy(options = ThemeOptions(digital = DigitalColor.LED))) +
            Shot("steps", states.getValue("mid").copy(stats = trip.copy(stepMode = true, steps = 4312))) + noShader(states.getValue("mid")) +
            Shot("mph", states.getValue("mid").copy(options = ThemeOptions(units = SpeedUnit.MPH))),
    )

    @Test
    fun night() = shoot(
        NightFocusTheme,
        listOf(
            Shot("cruise", GaugeFrame(needleKmh = 92f, readout = 92, rangeKmh = 260f, rangeFromKmh = 260, rangeToKmh = 260, stats = trip)),
            Shot("upper_lit", GaugeFrame(needleKmh = 150f, readout = 150, rangeKmh = 260f, rangeFromKmh = 260, rangeToKmh = 260, nightUpper = 1f, stats = trip)),
            Shot("warnings", GaugeFrame(needleKmh = 40f, readout = 40, rangeKmh = 260f, rangeFromKmh = 260, rangeToKmh = 260, gps = GpsDot.NONE, batteryLow = true)),
            Shot("landscape", GaugeFrame(needleKmh = 92f, readout = 92, rangeKmh = 260f, rangeFromKmh = 260, rangeToKmh = 260), landscape = true),
            noShader(GaugeFrame(needleKmh = 92f, readout = 92, rangeKmh = 260f, rangeFromKmh = 260, rangeToKmh = 260, stats = trip)),
            Shot("target", GaugeFrame(needleKmh = 70f, readout = 70, rangeKmh = 260f, rangeFromKmh = 260, rangeToKmh = 260, target = GaugeTarget(.93f, 640.0, false))),
        ),
    )

    @Test
    fun tape() = shoot(
        SpeedTapeTheme,
        listOf(
            Shot("mid", states.getValue("mid").copy(accelKmhS = 1.5f, headingDeg = 47f, altitudeM = 41.0)),
            Shot("braking", states.getValue("high").copy(accelKmhS = -4f, headingDeg = 312f, altitudeM = 120.0)),
            Shot("landscape", states.getValue("mid").copy(headingDeg = 47f, altitudeM = 41.0), landscape = true),
            Shot("target", states.getValue("mid").copy(headingDeg = 47f, altitudeM = 41.0, gps = GpsDot.NONE, target = GaugeTarget(.4f, 7_300.0, false))),
        ),
    )

    @Test
    fun synth() = shoot(SynthwaveTheme, (standard() + noShader(states.getValue("mid")) + Shot("mph", states.getValue("mid").copy(options = ThemeOptions(units = SpeedUnit.MPH)))).map { it.copy(frame = it.frame.copy(scrollM = 123f, timeS = 4.0)) })

    @Test
    fun sun() = shoot(SunlightTheme, standard())
}
