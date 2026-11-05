package com.sappy.speedome.gauges

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

// Hand-made world speedos, second batch (the first is in WorldHeroes.kt). Original designs in each
// world's spirit, drawn as cards that dock on the map.

private fun GaugeFrame.lit() = (needleKmh / rangeKmh).coerceIn(0f, 1f)

/** Scale labels every [every] label steps, cross-fading with auto-range. */
private inline fun GaugeFrame.labels(every: Int = 1, block: (v: Float, alpha: Float) -> Unit) {
    for (set in scaleSets()) {
        val step = niceStep(set.max).label * every
        var v = 0f
        while (v <= set.max + 1e-3f && v <= rangeKmh * 1.001f) {
            block(v, set.alpha)
            v += step
        }
    }
}

/** Sunset Coast: an 80s synthwave card: a striped sun inside a neon dial, over a perspective grid. */
object SunsetCoastTheme : GaugeTheme {
    override val id = "world-sunset_coast"
    override val title = "SUNSET COAST"
    override val spring = NeedleSpring(omega = 13.0, zeta = .85)

    private val night = Color(0xFF1B0A33)
    private val dusk = Color(0xFF4A0F5C)
    private val rose = Color(0xFF8A1D5E)
    private val pink = Color(0xFFF46AC0)
    private val sun = Color(0xFFFFD84D)
    private val cyan = Color(0xFF4DE8FF)

    override fun DrawScope.drawStatic(frame: GaugeFrame, assets: GaugeAssets) {
        val w = size.width
        val h = size.height
        drawRoundRect(Brush.verticalGradient(listOf(night, dusk, rose)), cornerRadius = CornerRadius(18.dp.toPx()), alpha = .97f)
        // The grid: horizontal lines closing up towards the horizon, verticals fanning from the middle.
        val horizon = h * .64f
        val line = 1.dp.toPx()
        for (k in 1..6) {
            val y = horizon + (h - horizon) * (k / 6f).pow(1.8f)
            drawLine(pink.copy(alpha = .35f), Offset(0f, y), Offset(w, y), line)
        }
        for (k in -8..8) {
            val bottom = w / 2 + k * w / 9
            drawLine(pink.copy(alpha = .3f), Offset(w / 2 + (bottom - w / 2) * .2f, horizon), Offset(bottom, h), line)
        }
        drawLine(cyan.copy(alpha = .6f), Offset(0f, horizon), Offset(w, horizon), line)
        val l = cardLayout(size)
        val c = l.center
        val r = l.radius
        // The sun: solid above the middle, sliced into thinning bands below.
        val sr = r * .62f
        val sunBrush = Brush.verticalGradient(listOf(sun, pink), startY = c.y - sr, endY = c.y + sr)
        clipRect(0f, c.y - sr, w, c.y + sr * .2f) { drawCircle(sunBrush, sr, c) }
        var y = c.y + sr * .2f
        var band = sr * .2f
        while (y < c.y + sr) {
            val gap = band * .45f
            clipRect(0f, y + gap, w, y + band) { drawCircle(sunBrush, sr, c) }
            y += band
            band *= .85f
        }
        val box = Offset(c.x - r, c.y - r)
        drawArc(cyan.copy(alpha = .25f), DIAL_START_DEG, DIAL_SWEEP_DEG, false, box, Size(2 * r, 2 * r), style = Stroke(r * .06f, cap = StrokeCap.Round))
        limitArc(frame, c, r, r * .06f, LIMIT_RED.copy(alpha = .5f))
        val italic = assets.paint(assets.outfit, 600, skewX = -.2f)
        frame.labels(every = 2) { v, a ->
            val at = polar(c, r * .82f, angleRad(v, frame.rangeKmh))
            text(fmt(v.toDouble(), 0), at.x, at.y, italic, r * .11f, (if (frame.inRedZone(v)) LIMIT_RED else Color.White).copy(alpha = .75f * a))
        }
        text(frame.options.units.label.uppercase(), c.x, c.y + r * .86f, italic, r * .11f, cyan, spacingEm = .3f)
    }

    override fun DrawScope.drawDynamic(frame: GaugeFrame, assets: GaugeAssets) {
        val l = cardLayout(size)
        val c = l.center
        val r = l.radius
        if (frame.needleKmh > .05f) {
            val box = Offset(c.x - r, c.y - r)
            val sweep = DIAL_SWEEP_DEG * frame.lit()
            drawArc(pink.copy(alpha = .25f), DIAL_START_DEG, sweep, false, box, Size(2 * r, 2 * r), style = Stroke(r * .16f, cap = StrokeCap.Round))
            drawArc(frame.warned(pink), DIAL_START_DEG, sweep, false, box, Size(2 * r, 2 * r), style = Stroke(r * .06f, cap = StrokeCap.Round))
            drawCircle(Color.White, r * .045f, polar(c, r, angleRad(frame.needleKmh, frame.rangeKmh)))
        }
        // Chrome italics: a magenta drop, a white face, dark on the sun.
        val s = frame.readout.toString()
        val p = assets.paint(assets.outfit, 800, skewX = -.2f)
        val sz = r * .5f
        val y = c.y - r * .1f
        text(s, c.x + sz * .04f, y + sz * .04f, p, sz, pink)
        text(s, c.x, y, p, sz, frame.warned(night))
        cardStats(frame, l, p, assets.paint(assets.outfit, 600), Color.White, cyan.copy(alpha = .85f), scale = .85f)
    }
}

/** Sea Chart: a brass ship's instrument on chart paper, with a compass rose and a log window. */
object SeaChartTheme : GaugeTheme {
    override val id = "world-sea_chart"
    override val title = "SEA CHART"
    override val spring = NeedleSpring(omega = 10.0, zeta = .7)

    private val paper = Color(0xFFEDE3C8)
    private val navy = Color(0xFF1C3557)
    private val face = Color(0xFF13263F)
    private val brass = Color(0xFFC9A45C)
    private val brassDark = Color(0xFF7A5A22)
    private val red = Color(0xFFA13C32)

    override fun DrawScope.drawStatic(frame: GaugeFrame, assets: GaugeAssets) {
        drawRoundRect(paper.copy(alpha = .97f), cornerRadius = CornerRadius(10.dp.toPx()))
        val cell = 18.dp.toPx()
        var x = cell
        while (x < size.width) {
            drawLine(navy.copy(alpha = .07f), Offset(x, 0f), Offset(x, size.height), .8.dp.toPx()); x += cell
        }
        var gy = cell
        while (gy < size.height) {
            drawLine(navy.copy(alpha = .07f), Offset(0f, gy), Offset(size.width, gy), .8.dp.toPx()); gy += cell
        }
        val i = 5.dp.toPx()
        drawRoundRect(navy.copy(alpha = .7f), Offset(i, i), Size(size.width - 2 * i, size.height - 2 * i), CornerRadius(6.dp.toPx()), Stroke(1.2.dp.toPx()))
        val l = cardLayout(size)
        val c = l.center
        val r = l.radius
        drawCircle(Brush.sweepGradient(0f to brass, .25f to brassDark, .5f to brass, .75f to brassDark, 1f to brass, center = c), r * 1.08f, c, style = Stroke(r * .1f))
        drawCircle(face, r * 1.03f, c)
        // An eight-point compass rose, faint behind the scale.
        val rose = Path()
        for (k in 0 until 16) {
            val a = (k * PI / 8 - PI / 2).toFloat()
            val len = when {
                k % 4 == 0 -> r * .58f
                k % 2 == 0 -> r * .36f
                else -> r * .12f
            }
            val p = polar(c, len, a)
            if (k == 0) rose.moveTo(p.x, p.y) else rose.lineTo(p.x, p.y)
        }
        rose.close()
        drawPath(rose, brass.copy(alpha = .22f))
        drawCircle(Color.White.copy(alpha = .4f), r * .93f, c, style = Stroke(.8.dp.toPx()))
        limitArc(frame, c, r * .9f, r * .07f, red.copy(alpha = .8f))
        for (set in frame.scaleSets()) {
            set.forEachTick(frame.rangeKmh) { v, major ->
                val a = angleRad(v, frame.rangeKmh)
                val col = (if (frame.inRedZone(v)) Color(0xFFFF8A7A) else Color.White).copy(alpha = set.alpha * if (major) .95f else .55f)
                val len = if (major) r * .13f else r * .06f
                drawLine(col, polar(c, r * .93f - len, a), polar(c, r * .93f, a), if (major) r * .024f else r * .012f)
            }
        }
        val labels = assets.paint(assets.oswald, 500)
        frame.labels(every = if (frame.rangeKmh / niceStep(frame.rangeKmh).label > 6) 2 else 1) { v, a ->
            val at = polar(c, r * .66f, angleRad(v, frame.rangeKmh))
            text(fmt(v.toDouble(), 0), at.x, at.y, labels, r * .14f, Color.White.copy(alpha = a))
        }
        text(frame.options.units.label.uppercase(), c.x, c.y - r * .34f, labels, r * .09f, brass, spacingEm = .2f)
    }

    override fun DrawScope.drawDynamic(frame: GaugeFrame, assets: GaugeAssets) {
        val l = cardLayout(size)
        val c = l.center
        val r = l.radius
        // The log window: a paper counter set into the face.
        val ww = r * .66f
        val wh = r * .26f
        val wy = c.y + r * .5f
        drawRect(paper, Offset(c.x - ww / 2, wy - wh / 2), Size(ww, wh))
        drawRect(brass, Offset(c.x - ww / 2, wy - wh / 2), Size(ww, wh), style = Stroke(r * .025f))
        val oswald = assets.paint(assets.oswald, 600)
        text(frame.readout.toString(), c.x, wy, oswald, wh * .78f, frame.warned(navy, red))
        // A compass-style needle: red point, white tail.
        val len = r * .86f
        val half = r * .045f
        withTransform({
            translate(c.x, c.y)
            rotate((angleRad(frame.needleKmh, frame.rangeKmh) * 180 / PI).toFloat(), Offset.Zero)
        }) {
            drawPath(Path().apply { moveTo(len, 0f); lineTo(0f, -half); lineTo(0f, half); close() }, red)
            drawPath(Path().apply { moveTo(-len * .3f, 0f); lineTo(0f, -half); lineTo(0f, half); close() }, Color(0xFFF2EAD6))
        }
        drawCircle(brass, r * .08f, c)
        drawCircle(brassDark, r * .08f, c, style = Stroke(r * .022f))
        cardStats(frame, l, oswald, assets.paint(assets.oswald, 500), navy, navy.copy(alpha = .6f), scale = .9f)
    }
}

/** Tactical Radar: a radar scope with range rings, a turning sweep and blips; the speed rings the scope. */
object TacticalRadarTheme : GaugeTheme {
    override val id = "world-tactical_radar"
    override val title = "TACTICAL RADAR"
    override val spring = NeedleSpring(omega = 15.0, zeta = 1.0)

    private val bg = Color(0xFF191A16)
    private val scope = Color(0xFF0E120E)
    private val line = Color(0xFFD6D2BD)
    private val amber = Color(0xFFFFD37F)
    private val cyan = Color(0xFF67CBEE)
    private val ink = Color(0xFFF2EFE2)

    /** Contacts as (bearing degrees, distance 0–1). */
    private val blips = listOf(40f to .62f, 118f to .34f, 205f to .78f, 290f to .5f, 330f to .22f)

    override fun DrawScope.drawStatic(frame: GaugeFrame, assets: GaugeAssets) {
        drawRoundRect(bg.copy(alpha = .96f), cornerRadius = CornerRadius(6.dp.toPx()))
        // Corner brackets.
        val b = 12.dp.toPx()
        val m = 6.dp.toPx()
        val sw = 1.5.dp.toPx()
        for ((x, y, dx, dy) in listOf(listOf(m, m, 1f, 1f), listOf(size.width - m, m, -1f, 1f), listOf(m, size.height - m, 1f, -1f), listOf(size.width - m, size.height - m, -1f, -1f))) {
            drawLine(amber.copy(alpha = .7f), Offset(x, y), Offset(x + b * dx, y), sw)
            drawLine(amber.copy(alpha = .7f), Offset(x, y), Offset(x, y + b * dy), sw)
        }
        val l = cardLayout(size)
        val c = l.center
        val r = l.radius
        drawCircle(scope, r, c)
        for (k in 1..3) drawCircle(line.copy(alpha = .12f), r * k / 4f, c, style = Stroke(1.dp.toPx()))
        drawLine(line.copy(alpha = .12f), Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1.dp.toPx())
        drawLine(line.copy(alpha = .12f), Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1.dp.toPx())
        for (d in 0 until 360 step 10) {
            val a = (d * PI / 180).toFloat()
            val len = if (d % 30 == 0) r * .07f else r * .035f
            drawLine(line.copy(alpha = .35f), polar(c, r - len, a), polar(c, r, a), 1.dp.toPx())
        }
        drawCircle(line.copy(alpha = .4f), r, c, style = Stroke(1.dp.toPx()))
        val box = Offset(c.x - r * .88f, c.y - r * .88f)
        drawArc(line.copy(alpha = .14f), DIAL_START_DEG, DIAL_SWEEP_DEG, false, box, Size(r * 1.76f, r * 1.76f), style = Stroke(r * .07f))
        limitArc(frame, c, r * .88f, r * .07f, LIMIT_RED.copy(alpha = .55f))
        val mono = assets.paint(assets.b612)
        frame.labels(every = 2) { v, a ->
            val at = polar(c, r * .72f, angleRad(v, frame.rangeKmh))
            text(fmt(v.toDouble(), 0), at.x, at.y, mono, r * .09f, (if (frame.inRedZone(v)) LIMIT_RED else amber).copy(alpha = .6f * a))
        }
    }

    override fun DrawScope.drawDynamic(frame: GaugeFrame, assets: GaugeAssets) {
        val l = cardLayout(size)
        val c = l.center
        val r = l.radius
        // The sweep turns every four seconds, trailing a fading wedge; blips glow as it passes.
        val sweepDeg = ((frame.timeS * 90) % 360).toFloat()
        rotate(sweepDeg, c) {
            drawCircle(Brush.sweepGradient(0f to Color.Transparent, .82f to Color.Transparent, 1f to cyan.copy(alpha = .3f), center = c), r, c)
        }
        drawLine(cyan.copy(alpha = .8f), c, polar(c, r, (sweepDeg * PI / 180).toFloat()), 1.5.dp.toPx())
        for ((bearing, d) in blips) {
            val since = ((sweepDeg - bearing) % 360 + 360) % 360
            val glow = (1f - since / 200f).coerceIn(0f, 1f)
            if (glow > 0f) drawCircle(amber.copy(alpha = glow * .9f), r * .035f, polar(c, r * d, (bearing * PI / 180).toFloat()))
        }
        if (frame.needleKmh > .05f) {
            val box = Offset(c.x - r * .88f, c.y - r * .88f)
            drawArc(frame.warned(amber), DIAL_START_DEG, DIAL_SWEEP_DEG * frame.lit(), false, box, Size(r * 1.76f, r * 1.76f), style = Stroke(r * .07f))
        }
        val bold = assets.paint(assets.b612Bold)
        text(frame.readout.toString(), c.x, c.y, bold, r * .36f, frame.warned(ink))
        text(frame.options.units.label.uppercase(), c.x, c.y + r * .3f, bold, r * .09f, cyan, spacingEm = .2f)
        cardStats(frame, l, bold, bold, ink, line.copy(alpha = .65f), scale = .8f)
    }
}

/** DOS Atlas: an amber text-mode program: a double-line window, an inverted title bar and F-keys. */
object DosAtlasTheme : GaugeTheme {
    override val id = "world-dos_atlas"
    override val title = "DOS ATLAS"
    override val spring = NeedleSpring(omega = 18.0, zeta = 1.0)

    private val bg = Color(0xFF120D0A)
    private val amber = Color(0xFFFFB847)
    private val bright = Color(0xFFFFD18A)
    private const val CELLS = 24

    private data class Grid(val pad: Float, val bar: Float, val numberX: Float, val numberY: Float, val numberSize: Float, val meterY: Float, val meterW: Float, val statsX: Float, val statsR: Float, val statsY: Float, val statsStep: Float, val statsSize: Float)

    private fun DrawScope.grid(): Grid {
        val w = size.width
        val h = size.height
        val pad = minOf(w, h) * .06f
        val bar = minOf(h * .13f, 26.dp.toPx())
        return if (w > h * 1.15f) {
            val body = h - 2 * bar - 2 * pad
            Grid(pad, bar, pad * 2, bar + pad + body * .4f, body * .55f, bar + pad + body * .86f, w * .5f - pad * 2, w * .56f, w - pad * 2, bar + pad + body * .14f, body * .25f, body * .17f)
        } else {
            val body = h - 2 * bar - 2 * pad
            Grid(pad, bar, pad * 2, bar + pad + body * .16f, minOf(body * .22f, w * .3f), bar + pad + body * .36f, w - pad * 4, pad * 2, w - pad * 2, bar + pad + body * .52f, body * .12f, minOf(body * .08f, w * .08f))
        }
    }

    override fun DrawScope.drawStatic(frame: GaugeFrame, assets: GaugeAssets) {
        val g = grid()
        val w = size.width
        val h = size.height
        drawRect(bg.copy(alpha = .97f))
        val vt = assets.paint(assets.vt323)
        // Title bar, inverted.
        drawRect(amber, Offset.Zero, Size(w, g.bar))
        text("SPEEDO.EXE", w / 2, g.bar / 2, vt, g.bar * .8f, bg, spacingEm = .1f)
        // Double-line window border.
        val o = g.pad * .5f
        val gap = 3.dp.toPx()
        drawRect(amber.copy(alpha = .8f), Offset(o, g.bar + o), Size(w - 2 * o, h - 2 * g.bar - 2 * o), style = Stroke(1.dp.toPx()))
        drawRect(amber.copy(alpha = .8f), Offset(o + gap, g.bar + o + gap), Size(w - 2 * (o + gap), h - 2 * g.bar - 2 * (o + gap)), style = Stroke(1.dp.toPx()))
        // Function-key bar.
        val keys = listOf("F1" to "Help", "F5" to "Trip", "F10" to "Exit")
        var x = g.pad
        val ky = h - g.bar / 2
        for ((k, label) in keys) {
            val kw = g.bar * .5f * k.length + g.bar * .2f
            drawRect(amber, Offset(x, h - g.bar * .9f), Size(kw, g.bar * .8f))
            text(k, x + kw / 2, ky, vt, g.bar * .7f, bg)
            text(label, x + kw + g.bar * .15f, ky, vt, g.bar * .7f, amber, Align.LEFT)
            x += kw + g.bar * (.6f + label.length * .42f)
        }
        val cellW = g.meterW / CELLS
        val limit = frame.limitFraction()
        for (i in 0 until CELLS) {
            val red = limit != null && (i + .5f) / CELLS >= limit
            drawRect((if (red) LIMIT_RED else amber).copy(alpha = .16f), Offset(g.numberX + i * cellW, g.meterY - cellW * .7f), Size(cellW - 1f, cellW * 1.4f))
        }
    }

    override fun DrawScope.drawDynamic(frame: GaugeFrame, assets: GaugeAssets) {
        val g = grid()
        val vt = assets.paint(assets.vt323)
        val ink = frame.warned(bright)
        text(frame.readout.toString().padStart(3, '0'), g.numberX, g.numberY, vt, g.numberSize, ink, Align.LEFT)
        text(frame.options.units.label.uppercase(), g.numberX + g.numberSize * 1.5f, g.numberY + g.numberSize * .22f, vt, g.numberSize * .3f, amber, Align.LEFT)
        val cellW = g.meterW / CELLS
        val limit = frame.limitFraction()
        val n = ceil(frame.lit() * CELLS).toInt()
        for (i in 0 until n) {
            val red = limit != null && (i + .5f) / CELLS >= limit
            drawRect(if (red) LIMIT_RED else ink, Offset(g.numberX + i * cellW, g.meterY - cellW * .7f), Size(cellW - 1f, cellW * 1.4f))
        }
        val dots = PathEffect.dashPathEffect(floatArrayOf(1.5.dp.toPx(), 3.dp.toPx()))
        frame.statPairs().forEachIndexed { i, (k, v) ->
            val y = g.statsY + i * g.statsStep
            text(k, g.statsX, y, vt, g.statsSize, amber, Align.LEFT)
            text(v, g.statsR, y, vt, g.statsSize * 1.1f, bright, Align.RIGHT)
            val from = g.statsX + g.statsSize * (k.length * .5f + .4f)
            val to = g.statsR - g.statsSize * (v.length * .55f + .5f)
            if (to > from) drawLine(amber.copy(alpha = .45f), Offset(from, y + g.statsSize * .2f), Offset(to, y + g.statsSize * .2f), 1.dp.toPx(), pathEffect = dots)
        }
    }
}

/** 16-bit Quest: a role-playing game's menu window: blue gradient, white frame, a speed gauge like a health bar. */
object Quest16Theme : GaugeTheme {
    override val id = "world-quest_16bit"
    override val title = "16-BIT QUEST"
    override val spring = NeedleSpring(omega = 14.0, zeta = 1.0)

    private val top = Color(0xFF3B57D0)
    private val bottom = Color(0xFF131C63)
    private val frameCol = Color(0xFFF4F4F4)
    private val shade = Color(0xFF080C2C)
    private val gold = Color(0xFFFFEBA3)
    private val green = Color(0xFF7CD77A)
    private val red = Color(0xFFE56470)

    private fun DrawScope.unit() = floor(minOf(size.width, size.height) / 96f).coerceAtLeast(2f)

    private data class Rows(val pad: Float, val labelY: Float, val numberX: Float, val numberY: Float, val numberSize: Float, val barY: Float, val barW: Float, val barH: Float, val statsX: Float, val statsR: Float, val statsY: Float, val statsStep: Float, val statsSize: Float)

    private fun DrawScope.rows(u: Float): Rows {
        val w = size.width
        val h = size.height
        val pad = 8 * u
        return if (w > h * 1.15f) {
            // "MOVING AVG" and a value must fit the stats column: about 12.5 letters of this pixel font.
            Rows(pad, pad + h * .1f, pad + 3 * u, h * .48f, h * .3f, h * .8f, w * .5f - 2 * pad, 5 * u, w * .55f, w - pad, h * .24f, h * .19f, minOf(h * .11f, (w * .45f - pad) / 12.5f))
        } else {
            Rows(pad, pad + h * .05f, pad + 3 * u, h * .2f, minOf(h * .14f, w * .22f), h * .33f, w - 2 * pad, 5 * u, pad, w - pad, h * .48f, h * .11f, minOf(h * .065f, w * .075f))
        }
    }

    override fun DrawScope.drawStatic(frame: GaugeFrame, assets: GaugeAssets) {
        val u = unit()
        val corner = CornerRadius(4 * u)
        drawRoundRect(shade, cornerRadius = CornerRadius(5 * u))
        drawRoundRect(frameCol, Offset(u, u), Size(size.width - 2 * u, size.height - 2 * u), corner)
        drawRoundRect(shade, Offset(3 * u, 3 * u), Size(size.width - 6 * u, size.height - 6 * u), corner)
        drawRoundRect(Brush.verticalGradient(listOf(top, bottom)), Offset(4 * u, 4 * u), Size(size.width - 8 * u, size.height - 8 * u), CornerRadius(3 * u))
        val t = rows(u)
        val pixel = assets.paint(assets.silkscreen)
        text("SPEED", t.numberX + 6 * u, t.labelY, pixel, t.numberSize * .3f, gold, Align.LEFT)
        // The bar's frame.
        drawRect(shade, Offset(t.numberX - u, t.barY - t.barH / 2 - u), Size(t.barW + 2 * u, t.barH + 2 * u))
        drawRect(frameCol.copy(alpha = .25f), Offset(t.numberX, t.barY - t.barH / 2), Size(t.barW, t.barH))
        frame.limitFraction()?.let { f -> drawRect(red, Offset(t.numberX + t.barW * f, t.barY - t.barH / 2 - 2 * u), Size(u, t.barH + 4 * u)) }
    }

    override fun DrawScope.drawDynamic(frame: GaugeFrame, assets: GaugeAssets) {
        val u = unit()
        val t = rows(u)
        val pixel = assets.paint(assets.silkscreen)
        // The menu cursor blinks beside the label.
        if ((frame.timeS * 2).toInt() % 2 == 0) {
            val cy = t.labelY
            val s = t.numberSize * .14f
            drawPath(Path().apply { moveTo(t.numberX, cy - s); lineTo(t.numberX + s * 1.3f, cy); lineTo(t.numberX, cy + s); close() }, frameCol)
        }
        val s = frame.readout.toString()
        text(s, t.numberX + u, t.numberY + u, pixel, t.numberSize, shade, Align.LEFT)
        text(s, t.numberX, t.numberY, pixel, t.numberSize, frame.warned(Color.White, red), Align.LEFT)
        text(frame.options.units.label.uppercase(), t.numberX + t.numberSize * (s.length * .62f + .2f), t.numberY + t.numberSize * .25f, pixel, t.numberSize * .26f, gold, Align.LEFT)
        val f = frame.lit()
        val fill = when {
            frame.limitWarn > 0f || f > .85f -> red
            f > .55f -> gold
            else -> green
        }
        if (f > 0f) {
            drawRect(fill, Offset(t.numberX, t.barY - t.barH / 2), Size(t.barW * f, t.barH))
            drawRect(Color.White.copy(alpha = .35f), Offset(t.numberX, t.barY - t.barH / 2), Size(t.barW * f, u))
        }
        frame.statPairs().forEachIndexed { i, (k, v) ->
            val y = t.statsY + i * t.statsStep
            text(k, t.statsX, y, pixel, t.statsSize, gold, Align.LEFT)
            text(v, t.statsR + u * .6f, y + u * .6f, pixel, t.statsSize * 1.1f, shade, Align.RIGHT)
            text(v, t.statsR, y, pixel, t.statsSize * 1.1f, Color.White, Align.RIGHT)
        }
    }
}

/** Ink & Wind: a brush-drawn circle on rice paper, ink-wash hills and a red seal. */
object InkWindTheme : GaugeTheme {
    override val id = "world-ink_wind"
    override val title = "INK & WIND"
    override val spring = NeedleSpring(omega = 9.0, zeta = .75)

    private val paper = Color(0xFFF2EEE3)
    private val ink = Color(0xFF1B1A17)
    private val wash = Color(0xFF8E8A80)
    private val seal = Color(0xFFB84435)
    private const val STROKES = 72

    override fun DrawScope.drawStatic(frame: GaugeFrame, assets: GaugeAssets) {
        val w = size.width
        val h = size.height
        drawRoundRect(paper.copy(alpha = .97f), cornerRadius = CornerRadius(4.dp.toPx()))
        // Two layers of ink-wash hills along the bottom.
        for ((layer, alpha) in listOf(0 to .1f, 1 to .17f)) {
            val hills = Path().apply {
                moveTo(0f, h)
                var x = 0f
                while (x <= w) {
                    val k = x / w * 2 * PI + layer * 1.7
                    val y = h * (.72f + layer * .1f) - h * .1f * (sin(k * 1.3) * .6 + sin(k * 3.1 + 1) * .3).toFloat()
                    lineTo(x, y)
                    x += w / 60
                }
                lineTo(w, h)
                close()
            }
            drawPath(hills, wash.copy(alpha = alpha))
        }
        val l = cardLayout(size)
        brush(l, 1f, ink.copy(alpha = .1f))
        limitArc(frame, l.center, l.radius * .84f, l.radius * .03f, seal.copy(alpha = .6f))
        val thin = assets.paint(assets.outfit, 300)
        frame.labels(every = 2) { v, a ->
            val at = polar(l.center, l.radius * .7f, angleRad(v, frame.rangeKmh))
            text(fmt(v.toDouble(), 0), at.x, at.y, thin, l.radius * .1f, (if (frame.inRedZone(v)) seal else wash).copy(alpha = a))
        }
    }

    override fun DrawScope.drawDynamic(frame: GaugeFrame, assets: GaugeAssets) {
        val l = cardLayout(size)
        val c = l.center
        val r = l.radius
        if (frame.needleKmh > .05f) brush(l, frame.lit(), frame.warned(ink, seal))
        text(frame.readout.toString(), c.x, c.y - r * .05f, assets.paint(assets.outfit, 300), r * .5f, frame.warned(ink, seal))
        // The seal holds the unit.
        val s = r * .3f
        val sy = c.y + r * .62f
        drawRoundRect(seal, Offset(c.x - s / 2, sy - s / 2), Size(s, s), CornerRadius(s * .12f))
        text(frame.options.units.label.uppercase(), c.x, sy, assets.paint(assets.outfit, 700), s * .3f, paper)
        cardStats(frame, l, assets.paint(assets.outfit, 400), assets.paint(assets.outfit, 500), ink, wash, scale = .9f)
    }

    /** A brush stroke round the dial up to [fraction]: heavy where it starts, thinning and drying out. */
    private fun DrawScope.brush(l: GaugeLayout, fraction: Float, color: Color) {
        val n = ceil(fraction * STROKES).toInt().coerceAtLeast(1)
        val r = l.radius * .92f
        val box = Offset(l.center.x - r, l.center.y - r)
        val each = DIAL_SWEEP_DEG / STROKES
        for (i in 0 until n) {
            val t = i / (STROKES - 1f)
            val width = l.radius * (.16f - .1f * t) * (1f + .12f * sin(i * 2.3f))
            val alpha = (1f - .45f * t.pow(2)) * color.alpha
            drawArc(color.copy(alpha = alpha), DIAL_START_DEG + i * each, each * 1.15f, false, box, Size(2 * r, 2 * r), style = Stroke(width, cap = StrokeCap.Round))
        }
    }
}
