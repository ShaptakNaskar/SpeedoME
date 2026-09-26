package com.sappy.speedome.gauges

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin

/** Dial geometry shared by the round themes: 270° sweep starting bottom-left (docs/plan.md §9). */
const val DIAL_START_DEG = 135f
const val DIAL_SWEEP_DEG = 270f

fun angleRad(value: Float, max: Float): Float {
    val f = (value / max).coerceIn(0f, 1.02f)
    return ((DIAL_START_DEG + DIAL_SWEEP_DEG * f) * PI / 180).toFloat()
}

fun polar(c: Offset, r: Float, a: Float) = Offset(c.x + cos(a) * r, c.y + sin(a) * r)

/** The speed-limit red: the red zone on every scale, and what gauges fade to as you near the limit. */
val LIMIT_RED = Color(0xFFFF3B30)

/** [base] faded toward [alert] as the speed nears the limit ([GaugeFrame.limitWarn]). */
fun GaugeFrame.warned(base: Color, alert: Color = LIMIT_RED): Color = if (limitWarn <= 0f) base else lerp(base, alert, limitWarn)

/** Where the limit sits on a 0–[max] scale (0–1); null when there's no limit or it's off the scale. */
fun GaugeFrame.limitFraction(max: Float = rangeKmh): Float? = limitKmh?.let { it / max }?.takeIf { it < 1f }

/** True for scale values in the red zone (at or above the limit). */
fun GaugeFrame.inRedZone(value: Float): Boolean = limitKmh?.let { value >= it - 1e-3f } ?: false

/** An arc of the 270° dial from [from] to [to] on a 0–[max] scale. */
fun DrawScope.scaleArc(center: Offset, radius: Float, width: Float, from: Float, to: Float, max: Float, color: Color) {
    val a0 = DIAL_SWEEP_DEG * (from / max).coerceIn(0f, 1f)
    val a1 = DIAL_SWEEP_DEG * (to / max).coerceIn(0f, 1f)
    if (a1 <= a0) return
    drawArc(color, DIAL_START_DEG + a0, a1 - a0, false, Offset(center.x - radius, center.y - radius), Size(2 * radius, 2 * radius), style = Stroke(width))
}

/** The red zone of a round dial: from the limit to the end of the 0–[max] scale, like a rev counter's redline. */
fun DrawScope.limitArc(frame: GaugeFrame, center: Offset, radius: Float, width: Float, color: Color, max: Float = frame.rangeKmh) {
    val limit = frame.limitKmh ?: return
    scaleArc(center, radius, width, limit, max, max, color)
}

/** Label and minor-tick spacing for a dial maximum. */
data class Step(val label: Float, val minor: Float)

/** Scale steps by dial maximum; the keys are the auto-range rungs (and Night Focus's dials). */
private val STEPS = listOf(
    10f to Step(1f, .5f), 20f to Step(2f, 1f), 40f to Step(5f, 1f), 60f to Step(10f, 2f), 80f to Step(10f, 2f),
    120f to Step(20f, 5f), 160f to Step(20f, 5f), 200f to Step(20f, 10f), 240f to Step(20f, 10f), 260f to Step(20f, 10f),
    320f to Step(40f, 10f), 500f to Step(50f, 10f), 1000f to Step(100f, 20f),
)

fun niceStep(max: Float): Step {
    val s = STEPS.firstOrNull { max <= it.first + 1e-3f }?.second ?: Step(100f, 20f)
    // A scale between rungs (a speed-limit scale such as 0–65) ticks every 5, so its end gets a tick.
    val ticks = max / s.minor
    return if (kotlin.math.abs(ticks - ticks.roundToLong()) < 1e-3f || max % 5f > 1e-3f) s else s.copy(minor = 5f)
}

/**
 * Labels under a bar scale: quarters on an auto-range rung (0–80: 0, 20, 40, 60, 80), otherwise the
 * dial's label step, so a speed-limit scale such as 0–65 reads 0, 10, … 60.
 */
fun barLabels(max: Float): List<Float> {
    if (STEPS.any { kotlin.math.abs(it.first - max) < 1e-3f }) return List(5) { max * it / 4 }
    val step = niceStep(max).label
    return List(floor(max / step + 1e-3f).toInt() + 1) { step * it }
}

/** Old and new scales cross-fading while auto-range changes the dial. */
data class ScaleSet(val max: Float, val alpha: Float)

fun GaugeFrame.scaleSets(): List<ScaleSet> =
    if (rangeProgress >= 1f || rangeFromKmh == rangeToKmh) {
        listOf(ScaleSet(rangeToKmh.toFloat(), 1f))
    } else {
        listOf(ScaleSet(rangeFromKmh.toFloat(), 1f - rangeProgress), ScaleSet(rangeToKmh.toFloat(), rangeProgress))
    }

/** Visits every tick of a scale set that fits the currently animated dial. */
inline fun ScaleSet.forEachTick(currentMax: Float, block: (value: Float, major: Boolean) -> Unit) {
    val step = niceStep(max)
    val n = floor(max / step.minor + 1e-3f).toInt()
    for (i in 0..n) {
        val v = i * step.minor
        if (v > currentMax * 1.001f) break
        val major = ((v / step.label) - (v / step.label).roundToLong()).let { kotlin.math.abs(it) < 1e-3f }
        block(v, major)
    }
}

/** Portrait: gauge on top, stats below. Landscape: gauge left, stats right (for car mounts). */
data class GaugeLayout(val landscape: Boolean, val center: Offset, val radius: Float, val stats: Rect)

fun layoutFor(size: Size): GaugeLayout {
    val w = size.width
    val h = size.height
    if (w > h * 1.15f) {
        val r = minOf(h * .4f, w * .24f)
        return GaugeLayout(true, Offset(w * .29f, h * .5f), r, Rect(w * .56f, h * .12f, w * .96f, h * .88f))
    }
    // Portrait: centre the dial (2.2 r with bezel) plus a stats band (0.6 r) in the available height.
    val r = minOf(w * .4f, h / 2.9f)
    val block = r * 2.2f + r * .6f
    val cy = (h - block) / 2 + r * 1.1f
    val top = cy + r * 1.2f
    return GaugeLayout(false, Offset(w / 2, cy), r, Rect(w * .05f, top, w * .95f, minOf(h - 4f, top + r * .7f)))
}

/** Tapered needle from the hub, with a short tail. */
fun DrawScope.needle(center: Offset, length: Float, angle: Float, color: Color, tail: Float = .18f, width: Float = .035f) {
    val path = Path().apply {
        moveTo(length, 0f)
        lineTo(-length * tail, -length * width)
        lineTo(-length * tail, length * width)
        close()
    }
    withTransform({
        translate(center.x, center.y)
        rotate(Math.toDegrees(angle.toDouble()).toFloat(), Offset.Zero)
    }) { drawPath(path, color) }
}

fun fmt(value: Double, places: Int): String = String.format(Locale.ROOT, "%.${places}f", value)

/** Distance in the frame's units (km or mi), one decimal below 100. */
fun GaugeFrame.fmtDist(metres: Double): String {
    val v = metres / options.units.metresPerDistance
    return fmt(v, if (v < 99.95) 1 else 0)
}

fun fmtDuration(seconds: Double): String {
    val s = seconds.toLong().coerceAtLeast(0)
    val h = s / 3600
    val m = s % 3600 / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec) else String.format(Locale.ROOT, "%d:%02d", m, sec)
}

/** The stat pairs a theme shows, following the average-display setting. */
fun GaugeFrame.statPairs(): List<Pair<String, String>> = buildList {
    if (options.average != AverageDisplay.OVERALL) add("MOVING AVG" to fmt(stats.avgMovingKmh, 0))
    if (options.average != AverageDisplay.MOVING) add("AVG" to fmt(stats.avgOverallKmh, 0))
    add("MAX" to fmt(stats.maxKmh, 0))
    if (stats.stepMode) add("STEPS" to stats.steps.toString()) else add("TRIP" to fmtDist(stats.distanceM))
}

/**
 * Mechanical odometer drum: [km] rolls through [cells] digits, the last one tenths in inverted colours.
 * Higher digits roll only while every lower digit is at 9, like the real thing.
 */
fun DrawScope.drum(
    topLeft: Offset,
    cell: Size,
    km: Double,
    cells: Int,
    assets: GaugeAssets,
    fg: Color,
    bg: Color,
    invFg: Color,
    invBg: Color,
) {
    val value = km * 10
    val whole = floor(value)
    val frac = (value - whole).toFloat()
    val paint = assets.paint(assets.oswald, 500)
    for (i in 0 until cells) {
        val p = 10.0.pow(i)
        val d = (floor(value / p) % 10).toInt()
        val roll = if (i == 0) frac else if ((whole % p).toLong() == p.toLong() - 1) frac else 0f
        val x = topLeft.x + (cells - 1 - i) * cell.width
        val inv = i == 0
        clipRect(x, topLeft.y, x + cell.width - 1, topLeft.y + cell.height) {
            drawRect(if (inv) invBg else bg, Offset(x, topLeft.y), Size(cell.width - 1, cell.height))
            val c = if (inv) invFg else fg
            text(d.toString(), x + cell.width / 2, topLeft.y + cell.height * (.5f - roll), paint, cell.height * .78f, c)
            text(((d + 1) % 10).toString(), x + cell.width / 2, topLeft.y + cell.height * (1.5f - roll), paint, cell.height * .78f, c)
            // curvature shading at the top and bottom of the drum
            drawRect(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = .55f), .25f to Color.Transparent, .75f to Color.Transparent, 1f to Color.Black.copy(alpha = .55f),
                    startY = topLeft.y, endY = topLeft.y + cell.height,
                ),
                Offset(x, topLeft.y), Size(cell.width, cell.height),
            )
        }
    }
}

private val SEGMENTS = mapOf(
    '0' to "abcdef", '1' to "bc", '2' to "abged", '3' to "abgcd", '4' to "fgbc", '5' to "afgcd",
    '6' to "afgedc", '7' to "abc", '8' to "abcdefg", '9' to "abcdfg", '-' to "g", ' ' to "",
)

/** Hexagonal seven-segment bars for a digit box, cached per size. */
class SegmentShapes(w: Float, h: Float) {
    val bars: Map<Char, Path>

    init {
        val t = w * .19f
        val gp = t * .14f
        fun horizontal(x1: Float, x2: Float, y: Float) = Path().apply {
            moveTo(x1, y); lineTo(x1 + t / 2, y - t / 2); lineTo(x2 - t / 2, y - t / 2)
            lineTo(x2, y); lineTo(x2 - t / 2, y + t / 2); lineTo(x1 + t / 2, y + t / 2); close()
        }
        fun vertical(x: Float, y1: Float, y2: Float) = Path().apply {
            moveTo(x, y1); lineTo(x + t / 2, y1 + t / 2); lineTo(x + t / 2, y2 - t / 2)
            lineTo(x, y2); lineTo(x - t / 2, y2 - t / 2); lineTo(x - t / 2, y1 + t / 2); close()
        }
        bars = mapOf(
            'a' to horizontal(t / 2 + gp, w - t / 2 - gp, t / 2),
            'g' to horizontal(t / 2 + gp, w - t / 2 - gp, h / 2),
            'd' to horizontal(t / 2 + gp, w - t / 2 - gp, h - t / 2),
            'f' to vertical(t / 2, t / 2 + gp, h / 2 - gp),
            'b' to vertical(w - t / 2, t / 2 + gp, h / 2 - gp),
            'e' to vertical(t / 2, h / 2 + gp, h - t / 2 - gp),
            'c' to vertical(w - t / 2, h / 2 + gp, h - t / 2 - gp),
        )
    }
}

/**
 * Draws [ch] at [topLeft]; unlit bars as faint ghosts ([ghost] alpha, 0 = none), lit bars with a soft
 * glow. With [ghostOnly] every bar is drawn as a ghost (the static layer under the lit digits).
 */
fun DrawScope.seg7(
    topLeft: Offset,
    shapes: SegmentShapes,
    h: Float,
    ch: Char,
    on: Color,
    ghost: Float,
    glow: Color?,
    skew: Float = .1f,
    ghostOnly: Boolean = false,
) {
    val lit = if (ghostOnly) "" else SEGMENTS[ch] ?: ""
    withTransform({
        translate(topLeft.x, topLeft.y)
        transform(androidx.compose.ui.graphics.Matrix().apply { values[androidx.compose.ui.graphics.Matrix.SkewX] = -skew; values[androidx.compose.ui.graphics.Matrix.TranslateX] = skew * h })
    }) {
        for ((k, path) in shapes.bars) {
            if (k in lit) {
                if (glow != null) drawPath(path, glow.copy(alpha = .35f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = h * .06f))
                drawPath(path, on)
            } else if (ghost > 0f) {
                drawPath(path, on.copy(alpha = ghost))
            }
        }
    }
}
