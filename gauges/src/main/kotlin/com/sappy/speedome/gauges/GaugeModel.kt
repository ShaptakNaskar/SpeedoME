package com.sappy.speedome.gauges

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/** Which averages the gauges show (docs/plan.md §6, setting). */
enum class AverageDisplay { BOTH, MOVING, OVERALL }

enum class GpsDot { GOOD, FALLBACK, NONE }

enum class DigitalColor(val on: Color, val glow: Color) {
    VFD(Color(0xFF52F2CF), Color(0xFF16E0B0)),
    LED(Color(0xFFFF4034), Color(0xFFFF1A0D)),
    LCD(Color(0xFFFFB627), Color(0xFFFF9D00)),
}

/** Per-user look options for the themes (docs/plan.md §10 "Theme options"). */
data class ThemeOptions(
    val retroCream: Boolean = false,
    val digital: DigitalColor = DigitalColor.VFD,
    val accent: Color = Color(0xFFE8A33D),
    val average: AverageDisplay = AverageDisplay.BOTH,
)

data class GaugeStats(
    val distanceM: Double = 0.0,
    val avgMovingKmh: Double = 0.0,
    val avgOverallKmh: Double = 0.0,
    val maxKmh: Double = 0.0,
    val elapsedS: Double = 0.0,
    val steps: Long = 0,
    val stepMode: Boolean = false,
)

/**
 * Everything a theme needs to draw one frame. Speeds in km/h. [rangeKmh] is the animated dial
 * maximum; while auto-range changes it moves from [rangeFromKmh] to [rangeToKmh] ([rangeProgress] 0→1).
 */
data class GaugeFrame(
    val needleKmh: Float = 0f,
    val readout: Int = 0,
    val rangeKmh: Float = 20f,
    val rangeFromKmh: Int = 20,
    val rangeToKmh: Int = 20,
    val rangeProgress: Float = 1f,
    val stats: GaugeStats = GaugeStats(),
    val gps: GpsDot = GpsDot.GOOD,
    val options: ThemeOptions = ThemeOptions(),
    val accelKmhS: Float = 0f,
    /** Seconds since the gauge appeared; drives ambient motion. */
    val timeS: Double = 0.0,
)

/** Needle feel per theme: natural frequency ω (rad/s) and damping ζ (docs/plan.md §9). */
data class NeedleSpring(val omega: Double, val zeta: Double)

interface GaugeTheme {
    val id: String
    val title: String
    val spring: NeedleSpring

    /** False for themes with their own fixed scale (Night Focus, Speed Tape, Nerd). */
    val usesAutoRange: Boolean get() = true

    /** True when the theme wants the light app chrome (Sunlight). */
    val light: Boolean get() = false

    /**
     * Everything that only depends on the dial scale and options (face, ticks, labels). It is
     * recorded once into a graphics layer and replayed until [StaticKey] changes (docs/plan.md §9).
     */
    fun DrawScope.drawStatic(frame: GaugeFrame, assets: GaugeAssets)

    /** Needle, digits, glow and stats: redrawn every frame on top of the static layer. */
    fun DrawScope.drawDynamic(frame: GaugeFrame, assets: GaugeAssets)
}

/** The part of a frame the static layer depends on; the layer re-records only when this changes. */
data class StaticKey(
    val rangeKmh: Float,
    val rangeFromKmh: Int,
    val rangeToKmh: Int,
    val rangeProgress: Float,
    val options: ThemeOptions,
    val stepMode: Boolean,
) {
    fun toFrame() = GaugeFrame(
        rangeKmh = rangeKmh, rangeFromKmh = rangeFromKmh, rangeToKmh = rangeToKmh, rangeProgress = rangeProgress,
        options = options, stats = GaugeStats(stepMode = stepMode),
    )
}

fun GaugeFrame.staticKey() = StaticKey(rangeKmh, rangeFromKmh, rangeToKmh, rangeProgress, options, stats.stepMode)

object GaugeThemes {
    val all: List<GaugeTheme> = listOf(RetroTheme, ModernTheme, DigitalTheme)

    fun byId(id: String?): GaugeTheme = all.firstOrNull { it.id == id } ?: all.first()
}
