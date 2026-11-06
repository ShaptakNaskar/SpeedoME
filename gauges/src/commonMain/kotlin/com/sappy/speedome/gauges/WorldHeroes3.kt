package com.sappy.speedome.gauges

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

// Hand-made world speedos, third batch (WorldHeroes.kt and WorldHeroes2.kt hold the others). Original
// designs in each world's spirit, drawn as cards that dock on the map.

private fun GaugeFrame.frac() = (needleKmh / rangeKmh).coerceIn(0f, 1f)

/** Scale labels every [every] label steps, cross-fading with auto-range. */
private inline fun GaugeFrame.scaleLabels(every: Int, block: (v: Float, alpha: Float) -> Unit) {
    for (set in scaleSets()) {
        val step = niceStep(set.max).label * every
        var v = 0f
        while (v <= set.max + 1e-3f && v <= rangeKmh * 1.001f) {
            block(v, set.alpha)
            v += step
        }
    }
}

/** Every other label on crowded scales (more than [most] steps). */
private fun GaugeFrame.labelEvery(most: Int = 6) = if (rangeKmh / niceStep(rangeKmh).label > most) 2 else 1

/** Major and minor ticks reaching in from radius [outer]; the red zone's ticks in [red]. */
private fun DrawScope.dialTicks(
    frame: GaugeFrame,
    c: Offset,
    outer: Float,
    major: Float,
    minor: Float,
    majorW: Float,
    minorW: Float,
    color: Color,
    red: Color = LIMIT_RED,
    minorAlpha: Float = .5f,
) {
    for (set in frame.scaleSets()) {
        set.forEachTick(frame.rangeKmh) { v, isMajor ->
            val a = angleRad(v, frame.rangeKmh)
            val base = if (frame.inRedZone(v)) red else color
            val col = base.copy(alpha = base.alpha * set.alpha * if (isMajor) 1f else minorAlpha)
            drawLine(col, polar(c, outer - if (isMajor) major else minor, a), polar(c, outer, a), if (isMajor) majorW else minorW)
        }
    }
}

/** An arc of the 270° dial up to [fraction] in a gradient running [from] → [to] over the whole scale. */
private fun DrawScope.gradientArc(c: Offset, r: Float, width: Float, fraction: Float, from: Color, to: Color, alpha: Float = 1f) {
    // The sweep gradient starts at 3 o'clock, so turn it to the dial's start; past the end it wraps back
    // to [from], which colours the round cap at the start.
    val brush = Brush.sweepGradient(0f to from, .75f to to, 1f to from, center = c)
    rotate(DIAL_START_DEG, c) {
        drawArc(brush, 0f, DIAL_SWEEP_DEG * fraction, false, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), alpha, Stroke(width, cap = StrokeCap.Round))
    }
}

private fun DrawScope.cardPath(corner: Float) = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(corner))) }

/** Deterministic 0–1 noise for textures (grit, lit windows): the same picture on every frame. */
private fun noise(i: Int, salt: Int): Float {
    var x = i * 0x27d4eb2d + salt * 0x165667b1
    x = x xor (x ushr 15)
    x *= 0x2c1b3c6d
    x = x xor (x ushr 12)
    x *= 0x297a2d39
    x = x xor (x ushr 15)
    return (x ushr 8) / 16777216f
}

/** Palm City: a satin dusk dashboard: a mint-to-violet arc over palm silhouettes, tall condensed numerals on a violet bar. */
object PalmCityTheme : GaugeTheme {
    override val id = "world-palm_city"
    override val title = "PALM CITY"
    override val spring = NeedleSpring(omega = 12.0, zeta = .9)

    private val satinTop = Color(0xFF2D2F34)
    private val satinBottom = Color(0xFF17181B)
    private val well = Color(0xFF101113)
    private val mint = Color(0xFFB6DB91)
    private val violet = Color(0xFFB786ED)
    private val ink = Color(0xFFF4F2EC)

    /** Palms along the bottom edge: x as a fraction of the width, height, lean. */
    private val palms = listOf(
        Triple(.03f, .8f, .2f), Triple(.17f, 1f, -.14f), Triple(.46f, .72f, .18f),
        Triple(.61f, .95f, -.2f), Triple(.8f, .78f, .15f), Triple(.96f, 1f, -.24f),
    )
    private val fronds = floatArrayOf(-168f, -140f, -112f, -78f, -48f, -18f, 152f, 28f)

    override fun DrawScope.drawStatic(frame: GaugeFrame, assets: GaugeAssets) {
        val w = size.width
        val h = size.height
        val corner = 16.dp.toPx()
        clipPath(cardPath(corner)) {
            drawRect(Brush.verticalGradient(listOf(satinTop, satinBottom)), alpha = .97f)
            // Dusk glows up from the bottom edge; a satin sheen runs across the top.
            drawRect(Brush.verticalGradient(0f to Color.Transparent, 1f to violet.copy(alpha = .26f), startY = h * .4f, endY = h))
            drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = .08f), Color.Transparent), start = Offset.Zero, end = Offset(w * .5f, h * .8f)))
            // The palms share one translucent layer, so overlapping fronds don't stack up.
            val tall = minOf(h * .55f, w * .3f)
            drawIntoCanvas { it.saveLayer(Rect(0f, h - tall * 1.5f, w, h), Paint().apply { alpha = .32f }) }
            for ((x, k, lean) in palms) palm(Offset(w * x, h + 2f), tall * k, lean, violet)
            drawIntoCanvas { it.restore() }
        }
        drawRoundRect(Color.White.copy(alpha = .1f), cornerRadius = CornerRadius(corner), style = Stroke(1.dp.toPx()))
        val l = cardLayout(size)
        val c = l.center
        val r = l.radius
        // The gauge well, dark over the palms.
        drawCircle(Brush.radialGradient(listOf(satinBottom, well), center = c, radius = r * 1.12f), r * 1.12f, c, alpha = .94f)
        drawCircle(Color.White.copy(alpha = .09f), r * 1.12f, c, style = Stroke(1.dp.toPx()))
        drawArc(Color.White.copy(alpha = .07f), DIAL_START_DEG, DIAL_SWEEP_DEG, false, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), style = Stroke(r * .12f, cap = StrokeCap.Round))
        limitArc(frame, c, r, r * .12f, LIMIT_RED.copy(alpha = .5f))
        dialTicks(frame, c, r * .88f, r * .09f, r * .05f, r * .024f, r * .014f, ink.copy(alpha = .75f))
        val labels = assets.paint(assets.oswald, 600)
        frame.scaleLabels(frame.labelEvery()) { v, a ->
            val at = polar(c, r * .66f, angleRad(v, frame.rangeKmh))
            text(fmt(v.toDouble(), 0), at.x, at.y, labels, r * .14f, (if (frame.inRedZone(v)) LIMIT_RED else ink).copy(alpha = .75f * a))
        }
        // The violet bar under the numerals, then the unit.
        drawRoundRect(violet, Offset(c.x - r * .24f, c.y + r * .28f), Size(r * .48f, r * .055f), CornerRadius(r * .03f))
        text(frame.options.units.label.uppercase(), c.x, c.y + r * .5f, assets.paint(assets.oswald, 600), r * .13f, mint, spacingEm = .25f)
    }

    override fun DrawScope.drawDynamic(frame: GaugeFrame, assets: GaugeAssets) {
        val l = cardLayout(size)
        val c = l.center
        val r = l.radius
        if (frame.needleKmh > .05f) {
            val f = frame.frac()
            val from = frame.warned(mint)
            val to = frame.warned(violet)
            gradientArc(c, r, r * .26f, f, from, to, alpha = .18f) // glow
            gradientArc(c, r, r * .12f, f, from, to)
            drawCircle(ink, r * .045f, polar(c, r, angleRad(frame.needleKmh, frame.rangeKmh)))
        }
        text(frame.readout.toString(), c.x, c.y - r * .08f, assets.paint(assets.oswald, 700), r * .6f, frame.warned(ink))
        val bold = assets.paint(assets.oswald, 600)
        cardStats(frame, l, bold, bold, ink, mint, scale = .9f)
    }

    /** A palm: a bowed, tapering trunk and drooping lens-shaped fronds from the crown. */
    private fun DrawScope.palm(base: Offset, height: Float, lean: Float, color: Color) {
        val crown = Offset(base.x + lean * height, base.y - height)
        val bow = Offset(base.x + lean * height * .1f, base.y - height * .55f)
        val trunk = Path()
        val back = ArrayList<Offset>()
        for (i in 0..12) {
            val t = i / 12f
            val u = 1 - t
            val p = base * (u * u) + bow * (2 * u * t) + crown * (t * t)
            val d = (bow - base) * (2 * u) + (crown - bow) * (2 * t)
            val half = height * (.04f - .022f * t)
            val n = Offset(-d.y, d.x) / d.getDistance() * half
            if (i == 0) trunk.moveTo(p.x + n.x, p.y + n.y) else trunk.lineTo(p.x + n.x, p.y + n.y)
            back.add(p - n)
        }
        for (b in back.asReversed()) trunk.lineTo(b.x, b.y)
        trunk.close()
        drawPath(trunk, color)
        val len = height * .46f
        for (deg in fronds) {
            val a = deg * PI.toFloat() / 180
            val dir = Offset(cos(a), sin(a))
            val n = Offset(-dir.y, dir.x) * (len * .11f)
            val droop = Offset(0f, len * .55f * abs(dir.x))
            val tip = crown + dir * len + droop
            val mid = crown + dir * (len * .55f) + droop * .3f
            drawPath(
                Path().apply {
                    moveTo(crown.x, crown.y)
                    quadraticTo(mid.x + n.x, mid.y + n.y, tip.x, tip.y)
                    quadraticTo(mid.x - n.x, mid.y - n.y, crown.x, crown.y)
                    close()
                },
                color,
            )
        }
        drawCircle(color, height * .04f, crown)
    }
}

/** Back Streets: spray paint on a concrete wall: a gold sprayed arc with overspray, outlined numerals with drips. */
object BackStreetsTheme : GaugeTheme {
    override val id = "world-back_streets"
    override val title = "BACK STREETS"
    override val spring = NeedleSpring(omega = 12.0, zeta = .8)

    private val wall = Color(0xFFC0C2B0)
    private val wallLow = Color(0xFFA7AA98)
    private val gold = Color(0xFFF2C877)
    private val brick = Color(0xFFDA5B3C)
    private val ink = Color(0xFF1E1D1A)
    private val brickInk = Color(0xFF7A2614)

    override fun DrawScope.drawStatic(frame: GaugeFrame, assets: GaugeAssets) {
        val w = size.width
        val h = size.height
        val corner = 6.dp.toPx()
        clipPath(cardPath(corner)) {
            drawRect(Brush.verticalGradient(listOf(wall, wallLow)))
            // Weathering: soft damp stains, a formwork seam, then grit in two tones.
            for ((x, y, k) in listOf(Triple(.82f, .1f, .7f), Triple(.12f, .95f, .5f), Triple(.5f, .55f, .35f))) {
                val p = Offset(w * x, h * y)
                val rad = minOf(w, h) * k
                drawCircle(Brush.radialGradient(listOf(ink.copy(alpha = .09f), Color.Transparent), center = p, radius = rad), rad, p)
            }
            val seam = h * .5f
            drawLine(ink.copy(alpha = .12f), Offset(0f, seam), Offset(w, seam), 1.dp.toPx())
            drawLine(Color.White.copy(alpha = .25f), Offset(0f, seam + 1.dp.toPx()), Offset(w, seam + 1.dp.toPx()), 1.dp.toPx())
            val step = 8.dp.toPx()
            val n = (w * h / (step * step)).toInt()
            val dark = ArrayList<Offset>(n)
            val light = ArrayList<Offset>(n / 2)
            for (i in 0 until n) {
                val p = Offset(noise(i, 1) * w, noise(i, 2) * h)
                if (noise(i, 3) < .6f) dark.add(p) else light.add(p)
            }
            drawPoints(dark, PointMode.Points, ink.copy(alpha = .22f), 1.3.dp.toPx(), StrokeCap.Round)
            drawPoints(light, PointMode.Points, Color.White.copy(alpha = .45f), 1.dp.toPx(), StrokeCap.Round)
            drawPoints(dark.subList(0, dark.size / 8), PointMode.Points, ink.copy(alpha = .16f), 2.6.dp.toPx(), StrokeCap.Round)
        }
        val l = cardLayout(size)
        val c = l.center
        val r = l.radius
        // A faint ghost of the arc, as if sketched before painting; the red zone sprayed in brick.
        drawArc(ink.copy(alpha = .1f), DIAL_START_DEG, DIAL_SWEEP_DEG, false, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), style = Stroke(r * .14f, cap = StrokeCap.Round))
        frame.limitKmh?.let { limit ->
            scaleArc(c, r, r * .3f, limit, frame.rangeKmh, frame.rangeKmh, brick.copy(alpha = .2f))
            scaleArc(c, r, r * .14f, limit, frame.rangeKmh, frame.rangeKmh, brick.copy(alpha = .9f))
        }
        dialTicks(frame, c, r * .8f, r * .09f, r * .045f, r * .035f, r * .018f, ink.copy(alpha = .8f), red = brickInk)
        val labels = assets.paint(assets.oswald, 700)
        frame.scaleLabels(frame.labelEvery()) { v, a ->
            val at = polar(c, r * .6f, angleRad(v, frame.rangeKmh))
            text(fmt(v.toDouble(), 0), at.x, at.y, labels, r * .15f, (if (frame.inRedZone(v)) brickInk else ink).copy(alpha = .85f * a))
        }
        text(frame.options.units.label.uppercase(), c.x, c.y + r * .72f, labels, r * .14f, ink, spacingEm = .15f)
    }

    override fun DrawScope.drawDynamic(frame: GaugeFrame, assets: GaugeAssets) {
        val l = cardLayout(size)
        val c = l.center
        val r = l.radius
        val paint = frame.warned(gold)
        if (frame.needleKmh > .05f) {
            val sweep = DIAL_SWEEP_DEG * frame.frac()
            val box = Offset(c.x - r, c.y - r)
            val sz = Size(2 * r, 2 * r)
            drawArc(paint.copy(alpha = .2f), DIAL_START_DEG, sweep, false, box, sz, style = Stroke(r * .36f, cap = StrokeCap.Round)) // overspray
            drawArc(ink, DIAL_START_DEG, sweep, false, box, sz, style = Stroke(r * .2f, cap = StrokeCap.Round))
            drawArc(paint, DIAL_START_DEG, sweep, false, box, sz, style = Stroke(r * .12f, cap = StrokeCap.Round))
            // Stray droplets around the nozzle.
            val tip = angleRad(frame.needleKmh, frame.rangeKmh)
            for (k in 0 until 7) {
                val p = polar(c, r * (1f + (noise(k, 7) - .5f) * .5f), tip + (noise(k, 8) - .25f) * .3f)
                drawCircle(paint.copy(alpha = .75f), r * (.012f + .02f * noise(k, 9)), p)
            }
        }
        // The numerals: a brick extrusion, a dark outline, gold fill and paint drips.
        val s = frame.readout.toString()
        val p = assets.paint(assets.oswald, 700)
        val sz = r * .54f
        val y = c.y - r * .1f
        val o = sz * .05f
        for (k in 1..3) text(s, c.x + o * .8f * k, y + o * .8f * k, p, sz, brick)
        // Drips hang from the first and last digit, where each glyph has ink at its foot.
        val bottom = y + sz * .42f // Oswald's baseline sits .45 em below the text's centre
        val dripW = sz * .07f
        var left = c.x - s.sumOf { digitAdvance(it).toDouble() }.toFloat() * sz / 2
        val drips = ArrayList<Pair<Float, Float>>(2)
        s.forEachIndexed { i, ch ->
            if (i == 0 || i == s.length - 1) {
                drips += (left + digitAdvance(ch) * sz * footAt(ch)) to bottom + sz * (.14f + .2f * noise(ch.code + i, 11))
            }
            left += digitAdvance(ch) * sz
        }
        for ((x, end) in drips) {
            drawLine(ink, Offset(x, bottom - dripW), Offset(x, end), dripW + 2 * o, StrokeCap.Round)
            drawCircle(ink, dripW * .85f + o, Offset(x, end))
        }
        for (k in 0 until 8) {
            val a = k * PI / 4
            text(s, c.x + cos(a).toFloat() * o, y + sin(a).toFloat() * o, p, sz, ink)
        }
        for ((x, end) in drips) {
            drawLine(paint, Offset(x, bottom - dripW * 2), Offset(x, end), dripW, StrokeCap.Round)
            drawCircle(paint, dripW * .85f, Offset(x, end))
        }
        text(s, c.x, y, p, sz, paint)
        val bold = assets.paint(assets.oswald, 700)
        cardStats(frame, l, bold, bold, ink, brickInk, scale = .9f)
    }

    /** Oswald Bold's digit widths in ems (the font's digits are proportional). */
    private fun digitAdvance(ch: Char) = when (ch) {
        '1' -> .41f
        '7' -> .42f
        '0' -> .56f
        else -> .53f
    }

    /** Where along its width a digit's foot has ink: the stem for 1, 4 and 7, the bowl's bottom otherwise. */
    private fun footAt(ch: Char) = when (ch) {
        '1' -> .58f
        '4' -> .68f
        '7' -> .4f
        else -> .5f
    }
}

/** Midnight Run: a street-racing tachometer on carbon fibre: shift lights across the top, a floating cyan needle. */
object MidnightRunTheme : GaugeTheme {
    override val id = "world-midnight_run"
    override val title = "MIDNIGHT RUN"
    override val spring = NeedleSpring(omega = 17.0, zeta = .65)

    private val carbon = Color(0xFF0A0B0D)
    private val weave = Color(0xFF1B1D21)
    private val rim = Color(0xFF33363D)
    private val cyan = Color(0xFF75EBF5)
    private val pink = Color(0xFFFFA9E8)
    private val green = Color(0xFF52E58A)
    private val yellow = Color(0xFFFFD84A)
    private val ink = Color(0xFFF3F5F8)
    private const val LEDS = 10

    private fun ledColor(i: Int) = when {
        i < 4 -> green
        i < 7 -> yellow
        else -> pink
    }

    /** [cardLayout] with the dial nudged down, below the shift lights. */
    private fun DrawScope.dial(): GaugeLayout {
        val l = cardLayout(size)
        val h = size.height
        if (!l.landscape) return l.copy(center = Offset(l.center.x, l.center.y + l.radius * .07f), radius = l.radius * .93f)
        val r = l.radius * .88f
        val c = Offset(h * .1f + r * 1.12f, h * .58f)
        return GaugeLayout(true, c, r, Rect(c.x + r * 1.3f, h * .24f, l.stats.right, h * .94f))
    }

    /** Shift-light centres across the top, and their radius. */
    private fun DrawScope.leds(): Pair<List<Offset>, Float> {
        val w = size.width
        val h = size.height
        val wide = w > h * 1.15f
        val rad = minOf(w * .022f, h * .028f)
        val y = if (wide) h * .085f else w * .075f
        val span = if (wide) w * .56f else w * .8f
        return List(LEDS) { Offset(w / 2 - span / 2 + span * it / (LEDS - 1), y) } to rad
    }

    override fun DrawScope.drawStatic(frame: GaugeFrame, assets: GaugeAssets) {
        val w = size.width
        val h = size.height
        val corner = 14.dp.toPx()
        clipPath(cardPath(corner)) {
            drawRect(carbon)
            // Carbon twill: diagonal steps of lighter tows, a fine hatch across them, a vignette.
            val cell = 3.dp.toPx()
            val tows = ArrayList<Offset>()
            for (j in 0..(h / cell).toInt()) for (i in 0..(w / cell).toInt()) if ((i + j) % 4 < 2) tows.add(Offset((i + .5f) * cell, (j + .5f) * cell))
            drawPoints(tows, PointMode.Points, weave, cell, StrokeCap.Square)
            var x = -h
            while (x < w) {
                drawLine(Color.Black.copy(alpha = .35f), Offset(x, h), Offset(x + h, 0f), .7.dp.toPx())
                x += cell
            }
            drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .6f)), center = center, radius = size.maxDimension * .7f))
        }
        drawRoundRect(rim, cornerRadius = CornerRadius(corner), style = Stroke(1.dp.toPx()))
        // The shift-light housing and its dark LEDs.
        val (at, rad) = leds()
        drawRoundRect(Color.Black.copy(alpha = .85f), Offset(at.first().x - rad * 2.4f, at.first().y - rad * 1.8f), Size(at.last().x - at.first().x + rad * 4.8f, rad * 3.6f), CornerRadius(rad * 1.8f))
        drawRoundRect(rim, Offset(at.first().x - rad * 2.4f, at.first().y - rad * 1.8f), Size(at.last().x - at.first().x + rad * 4.8f, rad * 3.6f), CornerRadius(rad * 1.8f), Stroke(1.dp.toPx()))
        at.forEachIndexed { i, p -> drawCircle(ledColor(i).copy(alpha = .2f), rad, p) }
        val l = dial()
        val c = l.center
        val r = l.radius
        drawCircle(Brush.radialGradient(listOf(Color(0xFF1C1E23), Color(0xFF08090B)), center = c, radius = r * 1.06f), r * 1.06f, c)
        drawCircle(rim, r * 1.06f, c, style = Stroke(r * .035f))
        drawCircle(cyan.copy(alpha = .35f), r * 1.1f, c, style = Stroke(1.dp.toPx()))
        limitArc(frame, c, r * .93f, r * .12f, LIMIT_RED.copy(alpha = .6f))
        dialTicks(frame, c, r * .99f, r * .14f, r * .07f, r * .026f, r * .013f, ink.copy(alpha = .9f))
        val labels = assets.paint(assets.orbitron, 700)
        frame.scaleLabels(frame.labelEvery(most = 8)) { v, a ->
            val at2 = polar(c, r * .72f, angleRad(v, frame.rangeKmh))
            text(fmt(v.toDouble(), 0), at2.x, at2.y, labels, r * .13f, (if (frame.inRedZone(v)) LIMIT_RED else ink).copy(alpha = .85f * a))
        }
        drawCircle(ink.copy(alpha = .12f), r * .48f, c, style = Stroke(1.dp.toPx()))
        text(frame.options.units.label.uppercase(), c.x, c.y + r * .3f, labels, r * .1f, pink, spacingEm = .2f)
    }

    override fun DrawScope.drawDynamic(frame: GaugeFrame, assets: GaugeAssets) {
        // Shift lights fill with speed; at the limit they all flash red.
        val (at, rad) = leds()
        val over = frame.limitKmh?.let { frame.needleKmh >= it } ?: false
        val lit = if (over) {
            if ((frame.timeS * 8).toInt() % 2 == 0) LEDS else 0
        } else {
            floor(frame.frac() * (LEDS + 1)).toInt().coerceAtMost(LEDS)
        }
        for (i in 0 until lit) {
            val col = if (over) LIMIT_RED else ledColor(i)
            drawCircle(col.copy(alpha = .3f), rad * 2f, at[i])
            drawCircle(col, rad, at[i])
            drawCircle(Color.White.copy(alpha = .55f), rad * .35f, at[i] - Offset(rad * .3f, rad * .3f))
        }
        val l = dial()
        val c = l.center
        val r = l.radius
        val needle = frame.warned(cyan)
        if (frame.needleKmh > .05f) {
            val rr = r * 1.025f
            drawArc(needle.copy(alpha = .6f), DIAL_START_DEG, DIAL_SWEEP_DEG * frame.frac(), false, Offset(c.x - rr, c.y - rr), Size(2 * rr, 2 * rr), style = Stroke(r * .03f))
        }
        // A floating needle: it starts off the hub, leaving the middle to the digits.
        withTransform({
            translate(c.x, c.y)
            rotate((angleRad(frame.needleKmh, frame.rangeKmh) * 180 / PI).toFloat(), Offset.Zero)
        }) {
            val path = Path().apply { moveTo(r * .97f, 0f); lineTo(r * .54f, -r * .04f); lineTo(r * .54f, r * .04f); close() }
            drawPath(path, needle.copy(alpha = .3f), style = Stroke(r * .06f, join = StrokeJoin.Round))
            drawPath(path, needle)
        }
        text(frame.readout.toString(), c.x, c.y - r * .04f, assets.paint(assets.orbitron, 800, skewX = -.18f), r * .34f, frame.warned(ink))
        val bold = assets.paint(assets.orbitron, 700)
        cardStats(frame, l, bold, bold, ink, cyan, scale = .8f)
    }
}

/** Concrete Grid: a city cab's taximeter: a black meter with screws, amber segment digits in a recessed window, button-like stat keys. */
object ConcreteGridTheme : GaugeTheme {
    override val id = "world-concrete_grid"
    override val title = "CONCRETE GRID"
    override val spring = NeedleSpring(omega = 18.0, zeta = 1.0)

    private val bodyHi = Color(0xFF2C2E31)
    private val bodyLo = Color(0xFF111214)
    private val glass = Color(0xFF0A0806)
    private val amber = Color(0xFFFFD06D)
    private val amberDeep = Color(0xFFEFA937)
    private val grey = Color(0xFFADB2B6)
    private val keyHi = Color(0xFF36393D)
    private val keyLo = Color(0xFF232528)
    private const val BARS = 20
    private const val DIGITS = 3

    private class Meter(
        val window: Rect,
        val digitH: Float,
        val digitW: Float,
        val digitsX: Float,
        val digitsY: Float,
        val shapes: SegmentShapes,
        val bar: Rect,
        val keys: List<Rect>,
        val small: Float,
        val labelSize: Float,
        val valueSize: Float,
        val screw: Float,
    ) {
        fun digit(i: Int) = Offset(digitsX + i * digitW * 1.22f, digitsY)
    }

    private fun DrawScope.meter(assets: GaugeAssets): Meter = assets.memo("concrete-grid", size) {
        val w = size.width
        val h = size.height
        val window: Rect
        val bar: Rect
        val keyArea: Rect
        if (w > h * 1.15f) {
            val p = h * .1f
            window = Rect(p * 1.4f, p * 1.2f, w * .57f, h - p * 2.4f)
            bar = Rect(window.left, h - p * 1.7f, window.right, h - p * 1.05f)
            keyArea = Rect(w * .6f, p * 1.2f, w - p * 1.4f, h - p * 1.05f)
        } else {
            val p = w * .06f
            window = Rect(p * 1.3f, p * 1.6f, w - p * 1.3f, h * .4f)
            bar = Rect(window.left, h * .44f, window.right, h * .475f)
            keyArea = Rect(window.left, h * .52f, window.right, h - p * 1.6f)
        }
        val dh = minOf(window.height * .6f, window.width / 4.6f / .55f)
        val dw = dh * .55f
        val total = DIGITS * dw + (DIGITS - 1) * dw * .22f
        val gap = keyArea.width * .04f
        val kw = (keyArea.width - gap) / 2
        val kh = (keyArea.height - gap) / 2
        val keys = List(4) { i -> Rect(Offset(keyArea.left + (i % 2) * (kw + gap), keyArea.top + (i / 2) * (kh + gap)), Size(kw, kh)) }
        Meter(
            window, dh, dw, window.center.x - total / 2 - dw * .3f, window.center.y - dh / 2 + window.height * .05f, SegmentShapes(dw, dh),
            bar, keys,
            small = minOf(window.height * .1f, 14.dp.toPx()),
            // "MOVING AVG" in Barlow Semi Condensed with its letter spacing is about 5.7 em wide; values
            // are up to 5 B612 Mono cells of .65 em.
            labelSize = minOf(kh * .18f, kw * .86f / 5.7f),
            valueSize = minOf(kh * .38f, kw * .88f / 3.25f),
            screw = minOf(w, h) * .026f,
        )
    }

    override fun DrawScope.drawStatic(frame: GaugeFrame, assets: GaugeAssets) {
        val m = meter(assets)
        val w = size.width
        val h = size.height
        val corner = 10.dp.toPx()
        drawRoundRect(Brush.verticalGradient(listOf(bodyHi, bodyLo)), cornerRadius = CornerRadius(corner))
        drawRoundRect(Color.White.copy(alpha = .14f), cornerRadius = CornerRadius(corner), style = Stroke(1.dp.toPx()))
        val lip = 2.5.dp.toPx()
        drawRoundRect(Color.Black.copy(alpha = .6f), Offset(lip, lip), Size(w - 2 * lip, h - 2 * lip), CornerRadius(corner - lip), Stroke(1.dp.toPx()))
        val s = m.screw
        listOf(Offset(s * 2.1f, s * 2.1f), Offset(w - s * 2.1f, s * 2.1f), Offset(s * 2.1f, h - s * 2.1f), Offset(w - s * 2.1f, h - s * 2.1f))
            .forEachIndexed { i, at -> screw(at, s, 25f + i * 50f) }
        // The recessed window: dark glass, shadowed under its top edge, with a faint amber backlight.
        val win = m.window
        val wr = CornerRadius(4.dp.toPx())
        val e = 1.5.dp.toPx()
        drawRoundRect(Color.Black, win.topLeft - Offset(e, e), Size(win.width + 2 * e, win.height + 2 * e), wr)
        drawRoundRect(Color.White.copy(alpha = .1f), win.topLeft - Offset(e, e), Size(win.width + 2 * e, win.height + 2 * e), wr, Stroke(1.dp.toPx()))
        drawRoundRect(glass, win.topLeft, win.size, wr)
        clipRect(win.left, win.top, win.right, win.bottom) {
            backlightGlow(win.center, win.width * .45f, amberDeep, .1f, frame.options)
            drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .7f), Color.Transparent), startY = win.top, endY = win.top + win.height * .22f), win.topLeft, win.size)
        }
        for (i in 0 until DIGITS) seg7(m.digit(i), m.shapes, m.digitH, '8', amber, .07f, null, ghostOnly = true)
        val bold = assets.paint(assets.b612Bold)
        text("SPEED", win.left + m.small * .8f, win.top + m.small * 1.1f, bold, m.small * .85f, amber.copy(alpha = .75f), Align.LEFT, spacingEm = .15f)
        text(frame.options.units.label.uppercase(), win.right - m.small * .8f, m.digitsY + m.digitH - m.small * .45f, bold, m.small, amber, Align.RIGHT)
        // The bar meter in its own slot.
        val b = m.bar
        drawRoundRect(glass, b.topLeft - Offset(e, e), Size(b.width + 2 * e, b.height + 2 * e), CornerRadius(2.dp.toPx()))
        val cw = b.width / BARS
        val redFrom = redFrom(frame)
        for (i in 0 until BARS) drawRect((if (i >= redFrom) LIMIT_RED else amber).copy(alpha = if (i >= redFrom) .3f else .12f), Offset(b.left + i * cw + cw * .12f, b.top), Size(cw * .76f, b.height))
        // The stat keys: raised buttons with their legends (a condensed face: B612's A loses its bar this small).
        val legend = assets.paint(assets.barlowSemi)
        val kr = CornerRadius(5.dp.toPx())
        val pairs = frame.statPairs()
        m.keys.forEachIndexed { i, k ->
            drawRoundRect(Color.Black.copy(alpha = .7f), k.topLeft + Offset(0f, 2.dp.toPx()), k.size, kr)
            drawRoundRect(Brush.verticalGradient(listOf(keyHi, keyLo), startY = k.top, endY = k.bottom), k.topLeft, k.size, kr)
            drawLine(Color.White.copy(alpha = .16f), Offset(k.left + kr.x, k.top + .5f), Offset(k.right - kr.x, k.top + .5f), 1.dp.toPx())
            pairs.getOrNull(i)?.let { (label, _) -> text(label, k.center.x, k.top + k.height * .25f, legend, m.labelSize, grey, spacingEm = .08f) }
        }
    }

    override fun DrawScope.drawDynamic(frame: GaugeFrame, assets: GaugeAssets) {
        val m = meter(assets)
        val on = frame.warned(amber)
        val glow = frame.warned(amberDeep)
        frame.readout.coerceAtMost(999).toString().padStart(DIGITS).forEachIndexed { i, ch -> seg7(m.digit(i), m.shapes, m.digitH, ch, on, 0f, glow) }
        val b = m.bar
        val cw = b.width / BARS
        val redFrom = redFrom(frame)
        val lit = (frame.frac() * BARS).roundToInt()
        for (i in 0 until lit) drawRect(if (i >= redFrom) LIMIT_RED else on, Offset(b.left + i * cw + cw * .12f, b.top), Size(cw * .76f, b.height))
        val bold = assets.paint(assets.b612Bold)
        frame.statPairs().forEachIndexed { i, (_, v) ->
            val k = m.keys[i]
            monoNumber(v, k.center.x, k.top + k.height * .63f, bold, minOf(m.valueSize, k.width * .84f / (v.length * .65f)), amber)
        }
    }

    /** B612 Mono gives the decimal point a whole cell; this sets it in a narrow one so "12.4" reads as one number. */
    private fun DrawScope.monoNumber(v: String, cx: Float, y: Float, p: GaugePaint, sz: Float, color: Color) {
        val dot = v.indexOf('.')
        if (dot < 0) return text(v, cx, y, p, sz, color)
        val cell = sz * .65f
        val narrow = cell * .45f
        val whole = v.substring(0, dot)
        val frac = v.substring(dot + 1)
        val x = cx - ((whole.length + frac.length) * cell + narrow) / 2 + whole.length * cell
        text(whole, x, y, p, sz, color, Align.RIGHT)
        text(".", x + narrow / 2, y, p, sz, color)
        text(frac, x + narrow, y, p, sz, color, Align.LEFT)
    }

    /** First bar of the red zone; [BARS] when there is none on the scale. */
    private fun redFrom(frame: GaugeFrame) = frame.limitFraction()?.let { ceil(it * BARS - 1e-3f).toInt() } ?: BARS

    /** A slotted screw head turned [deg] degrees. */
    private fun DrawScope.screw(at: Offset, s: Float, deg: Float) {
        drawCircle(Color.Black.copy(alpha = .6f), s * 1.2f, at)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFC9CDD1), Color(0xFF5C6064)), center = at - Offset(s * .35f, s * .35f), radius = s * 1.4f), s, at)
        val a = deg * PI.toFloat() / 180
        val d = Offset(cos(a), sin(a)) * (s * .75f)
        drawLine(Color(0xFF26282B), at - d, at + d, s * .28f)
    }
}

/** Violet City: party-city chrome: a glossy purple dial in a chrome bezel, a ring of LED dots and gold sparkles. */
object VioletCityTheme : GaugeTheme {
    override val id = "world-violet_city"
    override val title = "VIOLET CITY"
    override val spring = NeedleSpring(omega = 13.0, zeta = .75)

    private val night = Color(0xFF1B1C22)
    private val plum = Color(0xFF35234F)
    private val lilac = Color(0xFFD0A6FB)
    private val faceHi = Color(0xFF7144BC)
    private val faceLo = Color(0xFF240E45)
    private val chromeHi = Color(0xFFF6F0FF)
    private val chromeLo = Color(0xFF66568A)
    private val gold = Color(0xFFFFD36B)
    private val shade = Color(0xFF12051F)
    private val dotOff = Color(0xFF1C0C36)
    private const val DOTS = 30

    override fun DrawScope.drawStatic(frame: GaugeFrame, assets: GaugeAssets) {
        val w = size.width
        val h = size.height
        val corner = 20.dp.toPx()
        clipPath(cardPath(corner)) {
            drawRect(Brush.linearGradient(listOf(plum, night), start = Offset(w, 0f), end = Offset(0f, h)), alpha = .97f)
            // Club lights: soft lilac spots.
            for ((x, y, k) in listOf(Triple(.88f, .05f, .55f), Triple(.55f, 1f, .45f))) {
                val p = Offset(w * x, h * y)
                val rad = minOf(w, h) * k
                drawCircle(Brush.radialGradient(listOf(lilac.copy(alpha = .14f), Color.Transparent), center = p, radius = rad), rad, p)
            }
        }
        drawRoundRect(lilac.copy(alpha = .4f), cornerRadius = CornerRadius(corner), style = Stroke(1.dp.toPx()))
        val l = cardLayout(size)
        val c = l.center
        val r = l.radius
        drawCircle(Color.Black.copy(alpha = .35f), r * 1.18f, c + Offset(0f, r * .03f))
        drawCircle(Brush.sweepGradient(listOf(chromeHi, chromeLo, chromeHi, chromeLo, chromeHi), center = c), r * 1.1f, c, style = Stroke(r * .12f))
        drawCircle(Brush.radialGradient(listOf(faceHi, faceLo), center = c + Offset(-r * .25f, -r * .35f), radius = r * 1.4f), r * 1.04f, c)
        drawCircle(Color.Black.copy(alpha = .5f), r * 1.04f, c, style = Stroke(1.dp.toPx()))
        // The gloss across the top of the dome.
        clipPath(Path().apply { addOval(Rect(c, r * 1.04f)) }) {
            drawOval(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = .26f), Color.White.copy(alpha = 0f)), startY = c.y - r * 1.02f, endY = c.y - r * .12f),
                Offset(c.x - r * .86f, c.y - r * 1.02f), Size(r * 1.72f, r),
            )
        }
        dots(l, frame, null)
        val labels = assets.paint(assets.outfit, 700)
        frame.scaleLabels(frame.labelEvery()) { v, a ->
            val at = polar(c, r * .66f, angleRad(v, frame.rangeKmh))
            text(fmt(v.toDouble(), 0), at.x, at.y, labels, r * .13f, (if (frame.inRedZone(v)) Color(0xFFFF7B8A) else Color.White).copy(alpha = .9f * a))
        }
        text(frame.options.units.label.uppercase(), c.x, c.y + r * .38f, assets.paint(assets.outfit, 800), r * .12f, gold, spacingEm = .2f)
    }

    override fun DrawScope.drawDynamic(frame: GaugeFrame, assets: GaugeAssets) {
        val l = cardLayout(size)
        val c = l.center
        val r = l.radius
        if (frame.needleKmh > .05f) dots(l, frame, ceil(frame.frac() * DOTS).toInt().coerceIn(1, DOTS))
        val s = frame.readout.toString()
        val p = assets.paint(assets.outfit, 800)
        val sz = r * .54f
        val y = c.y - r * .06f
        text(s, c.x + sz * .03f, y + sz * .05f, p, sz, shade.copy(alpha = .8f))
        text(s, c.x, y, p, sz, frame.warned(Color.White))
        // Gold sparkles twinkle on the chrome and in the card's corner.
        val sparkles = listOf(
            polar(c, r * 1.1f, (-50 * PI / 180).toFloat()) to r * .14f,
            polar(c, r * 1.1f, (196 * PI / 180).toFloat()) to r * .09f,
            Offset(size.width - minOf(size.width, size.height) * .07f, minOf(size.width, size.height) * .07f) to r * .1f,
        )
        sparkles.forEachIndexed { k, (at, s0) -> sparkle(at, s0 * (.75f + .25f * sin(frame.timeS * 2.4 + k * 2.1).toFloat()), gold) }
        cardStats(frame, l, p, assets.paint(assets.outfit, 700), Color.White, lilac, scale = .9f)
    }

    /** The LED ring: all dots dark ([lit] null), or the first [lit] lit, the last one white-hot. */
    private fun DrawScope.dots(l: GaugeLayout, frame: GaugeFrame, lit: Int?) {
        val limit = frame.limitFraction()
        val rad = l.radius * .045f
        for (i in 0 until (lit ?: DOTS)) {
            val f = i / (DOTS - 1f)
            val p = polar(l.center, l.radius * .88f, ((DIAL_START_DEG + DIAL_SWEEP_DEG * f) * PI / 180).toFloat())
            val red = limit != null && f >= limit
            if (lit == null) {
                drawCircle(if (red) Color(0xFF5E1531) else dotOff, rad, p)
                continue
            }
            val col = when {
                i == lit - 1 -> Color.White
                red -> LIMIT_RED
                else -> frame.warned(lilac)
            }
            drawCircle(col.copy(alpha = .3f), rad * 2.1f, p)
            drawCircle(col, rad, p)
        }
    }

    private fun DrawScope.sparkle(at: Offset, s: Float, color: Color) {
        val k = s * .22f
        drawPath(
            Path().apply {
                moveTo(at.x, at.y - s); lineTo(at.x + k, at.y - k); lineTo(at.x + s, at.y); lineTo(at.x + k, at.y + k)
                lineTo(at.x, at.y + s); lineTo(at.x - k, at.y + k); lineTo(at.x - s, at.y); lineTo(at.x - k, at.y - k); close()
            },
            color,
        )
        drawCircle(Color.White.copy(alpha = .8f), s * .14f, at)
    }
}

/** Skyline: a night city: towers with lit windows along the bottom, a thin coral arc over a faint web, a yellow-tipped needle. */
object SkylineTheme : GaugeTheme {
    override val id = "world-skyline"
    override val title = "SKYLINE"
    override val spring = NeedleSpring(omega = 11.0, zeta = .8)

    private val skyTop = Color(0xFF0A1B27)
    private val skyBottom = Color(0xFF19404F)
    private val far = Color(0xFF10293A)
    private val near = Color(0xFF06111A)
    private val coral = Color(0xFFFF9E9D)
    private val yellow = Color(0xFFF6D156)
    private val teal = Color(0xFF4290A4)
    private val ink = Color(0xFFF1F5F7)
    private const val SPOKES = 16

    /** [cardLayout], with a wide card's stats lifted above the skyline. */
    private fun DrawScope.layout(): GaugeLayout {
        val l = cardLayout(size)
        return if (l.landscape) l.copy(stats = l.stats.copy(top = size.height * .04f, bottom = size.height * .7f)) else l
    }

    override fun DrawScope.drawStatic(frame: GaugeFrame, assets: GaugeAssets) {
        val w = size.width
        val h = size.height
        val corner = 12.dp.toPx()
        val l = layout()
        clipPath(cardPath(corner)) {
            drawRect(Brush.verticalGradient(listOf(skyTop, skyBottom)))
            drawPoints(List(40) { Offset(noise(it, 21) * w, noise(it, 22) * h * .55f) }, PointMode.Points, Color.White.copy(alpha = .35f), 1.dp.toPx(), StrokeCap.Round)
            val tall = minOf(h * .36f, w * .38f)
            towers(far, tall * 1.25f, 1, null, l.stats)
            towers(near, tall, 2, yellow, l.stats)
        }
        drawRoundRect(teal.copy(alpha = .4f), cornerRadius = CornerRadius(corner), style = Stroke(1.dp.toPx()))
        val c = l.center
        val r = l.radius
        drawCircle(skyTop.copy(alpha = .93f), r * 1.07f, c)
        drawCircle(teal.copy(alpha = .3f), r * 1.07f, c, style = Stroke(1.dp.toPx()))
        web(c, r, teal.copy(alpha = .28f))
        drawCircle(skyTop, r * .44f, c)
        drawCircle(teal.copy(alpha = .45f), r * .44f, c, style = Stroke(1.dp.toPx()))
        val rr = r * .97f
        drawArc(teal.copy(alpha = .35f), DIAL_START_DEG, DIAL_SWEEP_DEG, false, Offset(c.x - rr, c.y - rr), Size(2 * rr, 2 * rr), style = Stroke(r * .02f))
        limitArc(frame, c, rr, r * .06f, LIMIT_RED.copy(alpha = .7f))
        dialTicks(frame, c, r * .91f, r * .09f, r * .045f, r * .022f, r * .012f, ink.copy(alpha = .8f))
        val labels = assets.paint(assets.oswald, 600)
        frame.scaleLabels(frame.labelEvery()) { v, a ->
            val at = polar(c, r * .7f, angleRad(v, frame.rangeKmh))
            text(fmt(v.toDouble(), 0), at.x, at.y, labels, r * .13f, (if (frame.inRedZone(v)) LIMIT_RED else ink).copy(alpha = .85f * a))
        }
        text(frame.options.units.label.uppercase(), c.x, c.y + r * .27f, labels, r * .11f, coral, spacingEm = .2f)
    }

    override fun DrawScope.drawDynamic(frame: GaugeFrame, assets: GaugeAssets) {
        val l = layout()
        val c = l.center
        val r = l.radius
        val a = angleRad(frame.needleKmh, frame.rangeKmh)
        if (frame.needleKmh > .05f) {
            val sweep = DIAL_SWEEP_DEG * frame.frac()
            val rr = r * .97f
            val col = frame.warned(coral)
            drawArc(col.copy(alpha = .22f), DIAL_START_DEG, sweep, false, Offset(c.x - rr, c.y - rr), Size(2 * rr, 2 * rr), style = Stroke(r * .13f, cap = StrokeCap.Round))
            drawArc(col, DIAL_START_DEG, sweep, false, Offset(c.x - rr, c.y - rr), Size(2 * rr, 2 * rr), style = Stroke(r * .045f, cap = StrokeCap.Round))
        }
        // A pale needle from the plate's edge, ending in a lit yellow tip.
        drawLine(ink.copy(alpha = .85f), polar(c, r * .46f, a), polar(c, r * .8f, a), r * .025f, StrokeCap.Round)
        drawCircle(yellow.copy(alpha = .3f), r * .1f, polar(c, r * .95f, a))
        drawLine(yellow, polar(c, r * .78f, a), polar(c, r * 1.0f, a), r * .05f, StrokeCap.Round)
        text(frame.readout.toString(), c.x, c.y - r * .05f, assets.paint(assets.oswald, 600), r * .4f, frame.warned(ink))
        val bold = assets.paint(assets.oswald, 600)
        cardStats(frame, l, bold, bold, ink, coral, scale = .9f)
    }

    /** Spokes and sagging rings, like a web strung across the dial. */
    private fun DrawScope.web(c: Offset, r: Float, color: Color) {
        val line = .8.dp.toPx()
        val step = (2 * PI / SPOKES).toFloat()
        for (k in 0 until SPOKES) drawLine(color, polar(c, r * .44f, k * step), polar(c, r * .93f, k * step), line)
        for (ring in floatArrayOf(.56f, .7f, .84f)) {
            val path = Path()
            for (k in 0..SPOKES) {
                val p = polar(c, r * ring, k * step)
                if (k == 0) {
                    path.moveTo(p.x, p.y)
                } else {
                    val sag = polar(c, r * ring * .9f, (k - .5f) * step)
                    path.quadraticTo(sag.x, sag.y, p.x, p.y)
                }
            }
            drawPath(path, color, style = Stroke(line))
        }
    }

    /**
     * A row of towers along the bottom up to [maxH] tall, with some setbacks and antennas; [windows] lights a few
     * windows, none behind the stats ([clear]).
     */
    private fun DrawScope.towers(color: Color, maxH: Float, salt: Int, windows: Color?, clear: Rect) {
        val w = size.width
        val h = size.height
        val unit = maxH / 10
        var x = -noise(salt, 30) * unit * 2
        var i = 0
        while (x < w) {
            val bw = unit * (2.2f + noise(i, salt) * 2.8f)
            val bh = maxH * (.3f + .7f * noise(i, salt + 10))
            drawRect(color, Offset(x, h - bh), Size(bw, bh))
            val crown = noise(i, salt + 20)
            if (crown < .35f) drawRect(color, Offset(x + bw * .25f, h - bh - unit * 1.2f), Size(bw * .5f, unit * 1.2f))
            if (crown > .82f) drawLine(color, Offset(x + bw * .5f, h - bh), Offset(x + bw * .5f, h - bh - unit * 3f), unit * .25f)
            if (windows != null) {
                val ww = unit * .38f
                val wh = unit * .5f
                var row = 0
                var wy = h - bh + unit * .7f
                while (wy < h - wh) {
                    var col = 0
                    var wx = x + unit * .45f
                    while (wx < x + bw - ww - unit * .3f) {
                        if (noise(i * 977 + row * 31 + col, salt + 40) < .17f && !clear.overlaps(Rect(Offset(wx, wy), Size(ww, wh)))) {
                            drawRect(windows.copy(alpha = .75f), Offset(wx, wy), Size(ww, wh))
                        }
                        wx += unit * .8f
                        col++
                    }
                    wy += unit * 1.05f
                    row++
                }
            }
            x += bw
            i++
        }
    }
}

/** Open Road: a bright festival racing HUD: a sweeping chevron bar from pink to cyan, an italic readout on a white plate. */
object OpenRoadTheme : GaugeTheme {
    override val id = "world-open_road"
    override val title = "OPEN ROAD"
    override val spring = NeedleSpring(omega = 14.0, zeta = .8)

    private val top = Color(0xFFF8F9F4)
    private val bottom = Color(0xFFDCE5E2)
    // Deeper than the map's pink and cyan, so the bar holds up on the light card.
    private val pink = Color(0xFFFF5CBE)
    private val cyan = Color(0xFF22ACE6)
    private val ink = Color(0xFF1B2030)
    private val off = Color(0xFFCCD2CB)
    private val alarm = Color(0xFFC81E14)
    private const val CHEVRONS = 26

    /** The sweeping bar as chevron segments widening along the sweep, cached per size. */
    private fun DrawScope.chevrons(assets: GaugeAssets): List<Path> = assets.memo("open-road-chevrons", size) {
        val l = cardLayout(size)
        val c = l.center
        val r = l.radius
        val each = DIAL_SWEEP_DEG / CHEVRONS
        fun at(radius: Float, deg: Float) = polar(c, radius, deg * PI.toFloat() / 180)
        List(CHEVRONS) { i ->
            val t = i / (CHEVRONS - 1f)
            val outer = r * 1.04f
            val inner = r * (.93f - .15f * t)
            val mid = (outer + inner) / 2
            val a0 = DIAL_START_DEG + i * each
            val a1 = a0 + each * .74f
            val point = each * .4f
            Path().apply {
                at(outer, a0).let { moveTo(it.x, it.y) }
                at(mid, a0 + point).let { lineTo(it.x, it.y) }
                at(inner, a0).let { lineTo(it.x, it.y) }
                at(inner, a1).let { lineTo(it.x, it.y) }
                at(mid, a1 + point).let { lineTo(it.x, it.y) }
                at(outer, a1).let { lineTo(it.x, it.y) }
                close()
            }
        }
    }

    /** The readout's plate: a slanted white parallelogram around the dial's centre. */
    private fun plate(c: Offset, r: Float): Path {
        val pw = r * .5f
        val ph = r * .22f
        val sl = r * .08f
        return Path().apply {
            moveTo(c.x - pw + sl, c.y - ph); lineTo(c.x + pw + sl, c.y - ph); lineTo(c.x + pw - sl, c.y + ph); lineTo(c.x - pw - sl, c.y + ph); close()
        }
    }

    override fun DrawScope.drawStatic(frame: GaugeFrame, assets: GaugeAssets) {
        val w = size.width
        val h = size.height
        val corner = 14.dp.toPx()
        clipPath(cardPath(corner)) {
            drawRect(Brush.verticalGradient(listOf(top, bottom)))
            // Speed streaks in the top corner and a livery stripe along the bottom.
            val u = minOf(w, h) * .05f
            for (k in 0 until 4) {
                val y = u * (.8f + k * .9f)
                drawLine(ink.copy(alpha = .07f), Offset(w - u * (9f - k * 1.6f), y), Offset(w + u, y - u * 1.6f), u * .35f)
            }
            val stripe = 4.dp.toPx()
            drawRect(Brush.horizontalGradient(listOf(pink, cyan)), Offset(0f, h - stripe), Size(w, stripe))
        }
        drawRoundRect(ink.copy(alpha = .12f), cornerRadius = CornerRadius(corner), style = Stroke(1.dp.toPx()))
        val l = cardLayout(size)
        val c = l.center
        val r = l.radius
        val limit = frame.limitFraction()
        chevrons(assets).forEachIndexed { i, p ->
            val red = limit != null && (i + .5f) / CHEVRONS >= limit
            drawPath(p, if (red) LIMIT_RED.copy(alpha = .4f) else off)
        }
        val labels = assets.paint(assets.outfit, 800, skewX = -.15f)
        frame.scaleLabels(frame.labelEvery()) { v, a ->
            val at = polar(c, r * .66f, angleRad(v, frame.rangeKmh))
            text(fmt(v.toDouble(), 0), at.x, at.y, labels, r * .13f, (if (frame.inRedZone(v)) alarm else ink).copy(alpha = .8f * a))
        }
        val plate = plate(c, r)
        translate(top = 2.dp.toPx()) { drawPath(plate, ink.copy(alpha = .14f)) }
        drawPath(plate, Color.White)
        drawPath(plate, ink.copy(alpha = .1f), style = Stroke(1.dp.toPx()))
        drawRect(Brush.horizontalGradient(listOf(pink, cyan), startX = c.x - r * .5f, endX = c.x + r * .5f), Offset(c.x - r * .5f, c.y + r * .25f), Size(r, r * .035f))
        text(frame.options.units.label.uppercase(), c.x, c.y + r * .42f, labels, r * .12f, ink.copy(alpha = .85f), spacingEm = .15f)
    }

    override fun DrawScope.drawDynamic(frame: GaugeFrame, assets: GaugeAssets) {
        val l = cardLayout(size)
        val c = l.center
        val r = l.radius
        if (frame.needleKmh > .05f) {
            val segs = chevrons(assets)
            val n = ceil(frame.frac() * CHEVRONS).toInt().coerceIn(1, CHEVRONS)
            val limit = frame.limitFraction()
            for (i in 0 until n) {
                val red = limit != null && (i + .5f) / CHEVRONS >= limit
                drawPath(segs[i], if (red) LIMIT_RED else frame.warned(lerp(pink, cyan, i / (CHEVRONS - 1f))))
            }
        }
        text(frame.readout.toString(), c.x, c.y - r * .01f, assets.paint(assets.outfit, 800, skewX = -.2f), r * .4f, frame.warned(ink, alarm))
        cardStats(frame, l, assets.paint(assets.outfit, 800, skewX = -.12f), assets.paint(assets.outfit, 700), ink, ink.copy(alpha = .68f), scale = .9f)
    }
}
