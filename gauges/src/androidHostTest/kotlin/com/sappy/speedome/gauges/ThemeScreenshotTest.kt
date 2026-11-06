package com.sappy.speedome.gauges

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Golden images of every theme in representative states (docs/plan.md §11). Record with
 * `./gradlew :gauges:recordRoborazziAndroidHostTest`; `./gradlew check` verifies them.
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

    private data class Shot(val name: String, val frame: GaugeFrame, val landscape: Boolean = false, val size: DpSize? = null)

    /** setContent may only run once per test, so the shots are driven through state. */
    private fun shoot(theme: GaugeTheme, shots: List<Shot>) {
        var current by mutableStateOf(shots.first())
        compose.setContent {
            val shot = current
            val box = shot.size ?: if (shot.landscape) DpSize(780.dp, 360.dp) else DpSize(390.dp, 640.dp)
            GaugeView(theme, { shot.frame }, Modifier.size(box))
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

    /**
     * A speed limit of [limit], which fixes the scale at 125 % of it rounded up to a multiple of 5 (50 → 0–65):
     * 4 % under the limit (the red fade has begun), 14 % over, and 40 % over (needle and bar stop at the
     * end; digits still show the speed).
     */
    private fun limitShots(base: GaugeFrame = states.getValue("mid"), limit: Float = 50f): List<Shot> {
        val max = (ceil(limit * 1.25f / 5) * 5).toInt()
        val scale = base.copy(rangeKmh = max.toFloat(), rangeFromKmh = max, rangeToKmh = max, limitKmh = limit)
        return listOf(
            Shot("limit", scale.copy(needleKmh = limit * .96f, readout = (limit * .96f).roundToInt(), limitWarn = .6f)),
            Shot("over", scale.copy(needleKmh = limit * 1.14f, readout = (limit * 1.14f).roundToInt(), limitWarn = 1f)),
            Shot("pinned", scale.copy(needleKmh = limit * 1.4f, readout = (limit * 1.4f).roundToInt(), limitWarn = 1f)),
        )
    }

    @Test
    fun retro() = shoot(RetroTheme, standard() + Shot("cream", states.getValue("mid").copy(options = ThemeOptions(retroCream = true))) + noShader(states.getValue("mid")) +
        Shot("mph", states.getValue("mid").copy(options = ThemeOptions(units = SpeedUnit.MPH))) + limitShots())

    @Test
    fun modern() = shoot(ModernTheme, standard() + Shot("blue", states.getValue("mid").copy(options = ThemeOptions(accent = Color(0xFF4DA3FF)))) + noShader(states.getValue("mid")) +
        Shot("target", states.getValue("mid").copy(target = GaugeTarget(.62f, 3_800.0, false))) + limitShots())

    @Test
    fun digital() = shoot(
        DigitalTheme,
        standard() + Shot("led", states.getValue("mid").copy(options = ThemeOptions(digital = DigitalColor.LED))) +
            Shot("steps", states.getValue("mid").copy(stats = trip.copy(stepMode = true, steps = 4312))) + noShader(states.getValue("mid")) +
            Shot("mph", states.getValue("mid").copy(options = ThemeOptions(units = SpeedUnit.MPH))) + limitShots(),
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
        ) + limitShots(GaugeFrame(rangeKmh = 260f, rangeFromKmh = 260, rangeToKmh = 260, stats = trip), limit = 100f),
    )

    @Test
    fun tape() = shoot(
        SpeedTapeTheme,
        listOf(
            Shot("mid", states.getValue("mid").copy(accelKmhS = 1.5f, headingDeg = 47f, altitudeM = 41.0)),
            Shot("braking", states.getValue("high").copy(accelKmhS = -4f, headingDeg = 312f, altitudeM = 120.0)),
            Shot("landscape", states.getValue("mid").copy(headingDeg = 47f, altitudeM = 41.0), landscape = true),
            Shot("target", states.getValue("mid").copy(headingDeg = 47f, altitudeM = 41.0, gps = GpsDot.NONE, target = GaugeTarget(.4f, 7_300.0, false))),
        ) + limitShots(states.getValue("mid").copy(headingDeg = 47f, altitudeM = 41.0)),
    )

    @Test
    fun sun() = shoot(SunlightTheme, standard() + limitShots())

    /** World speedos: the wide card docked under the map, the tall one beside it in landscape, idle and at a limit. */
    private fun cardShots(): List<Shot> {
        val card = DpSize(390.dp, 170.dp)
        return listOf(
            Shot("card", states.getValue("mid"), size = card),
            Shot("idle", states.getValue("idle"), size = card),
            Shot("tall", states.getValue("high"), size = DpSize(280.dp, 360.dp)),
            Shot("time_mph", states.getValue("mid").let { it.copy(
                stats = it.stats.copy(elapsedS = 45_296.0),
                options = it.options.copy(average = AverageDisplay.MOVING, units = SpeedUnit.MPH),
            ) }, size = card),
        ) + limitShots().take(2).map { it.copy(size = card) }
    }

    /** The palette speedo, on Palm City's (dark) and Back Streets' (light) colours; its own ids, as those worlds now have hand-made ones. */
    @Test
    fun worldPalette() = shoot(
        WorldTheme("world-palette_dark", "PALETTE", WorldPalette(Color(0xFFB6DB91), Color(0xFFB786ED), Color(0xFF20221F), Color(0xFF73776C), Color(0xFF383B35))),
        cardShots(),
    )

    @Test
    fun worldPaletteLight() = shoot(
        WorldTheme("world-palette_light", "PALETTE", WorldPalette(Color(0xFFF2C877), Color(0xFFDA5B3C), Color(0xFFC2C5AD), Color(0xFFF4F0D8), Color(0xFFA4A48E))),
        cardShots(),
    )

    @Test
    fun neonCity() = shoot(NeonCityTheme, cardShots())

    @Test
    fun oldWest() = shoot(OldWestTheme, cardShots())

    @Test
    fun pixelWorld() = shoot(PixelWorldTheme, cardShots())

    @Test
    fun greenTerminal() = shoot(GreenTerminalTheme, cardShots())

    @Test
    fun sunsetCoast() = shoot(SunsetCoastTheme, cardShots())

    @Test
    fun seaChart() = shoot(SeaChartTheme, cardShots())

    @Test
    fun tacticalRadar() = shoot(TacticalRadarTheme, cardShots())

    @Test
    fun dosAtlas() = shoot(DosAtlasTheme, cardShots())

    @Test
    fun quest16() = shoot(Quest16Theme, cardShots())

    @Test
    fun inkWind() = shoot(InkWindTheme, cardShots())

    @Test
    fun palmCity() = shoot(PalmCityTheme, cardShots())

    @Test
    fun backStreets() = shoot(BackStreetsTheme, cardShots())

    @Test
    fun midnightRun() = shoot(MidnightRunTheme, cardShots())

    @Test
    fun concreteGrid() = shoot(ConcreteGridTheme, cardShots())

    @Test
    fun violetCity() = shoot(VioletCityTheme, cardShots())

    @Test
    fun skyline() = shoot(SkylineTheme, cardShots())

    @Test
    fun openRoad() = shoot(OpenRoadTheme, cardShots())
    @Test
    fun wayfarer() = shoot(WayfarerTheme, cardShots())

    @Test
    fun explorer() = shoot(ExplorerTheme, cardShots())

    @Test
    fun northern_road() = shoot(NorthernRoadTheme, cardShots())

    @Test
    fun golden_thread() = shoot(GoldenThreadTheme, cardShots())

    @Test
    fun desert_sands() = shoot(DesertSandsTheme, cardShots())

    @Test
    fun wilds() = shoot(WildsTheme, cardShots())

    @Test
    fun highlands() = shoot(HighlandsTheme, cardShots())

    @Test
    fun connected_city() = shoot(ConnectedCityTheme, cardShots())

    @Test
    fun backcountry() = shoot(BackcountryTheme, cardShots())

    @Test
    fun courier() = shoot(CourierTheme, cardShots())

    @Test
    fun surface_survey() = shoot(SurfaceSurveyTheme, cardShots())

    @Test
    fun islands() = shoot(IslandsTheme, cardShots())

    @Test
    fun quarantine() = shoot(QuarantineTheme, cardShots())

    @Test
    fun field_ops() = shoot(FieldOpsTheme, cardShots())

    @Test
    fun old_kingdom() = shoot(OldKingdomTheme, cardShots())

    @Test
    fun prohibition() = shoot(ProhibitionTheme, cardShots())

    @Test
    fun new_chapter() = shoot(NewChapterTheme, cardShots())

    @Test
    fun vector_command() = shoot(VectorCommandTheme, cardShots())

    @Test
    fun home_console() = shoot(HomeConsoleTheme, cardShots())

    @Test
    fun exclusion_zone() = shoot(ExclusionZoneTheme, cardShots())

    @Test
    fun machine_atlas() = shoot(MachineAtlasTheme, cardShots())

}
