package com.sappy.speedome.gauges

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.floor

// Hand-made speedos for the map worlds (docs/progress.md, World page). Each is an original design
// in the world's spirit, drawn as a card that docks on the map; [cardLayout] places the dial.

/** The speedo's lit fraction of the dial, 0–1. */
private fun GaugeFrame.fraction() = (needleKmh / rangeKmh).coerceIn(0f, 1f)

/** Neon City: a chamfered night-city HUD with a segmented arc and a glitching yellow readout. */
object NeonCityTheme : GaugeTheme {
    override val id = "world-neon_city"
    override val title = "NEON CITY"
    override val spring = NeedleSpring(omega = 16.0, zeta = .9)

    private val bg = Color(0xFF0A0E16)
    private val cyan = Color(0xFF6DE9DD)
    private val yellow = Color(0xFFF6E75C)
    private val magenta = Color(0xFFFF2A6D)
    private val dim = Color(0xFF258DA3)
    private const val SEGMENTS = 36

    override fun DrawScope.drawStatic(frame: GaugeFrame, assets: GaugeAssets) {
        val cut = 16.dp.toPx()
        val w = size.width
        val h = size.height
        val panel = Path().apply {
            moveTo(cut, 0f); lineTo(w, 0f); lineTo(w, h - cut); lineTo(w - cut, h); lineTo(0f, h); lineTo(0f, cut); close()
        }
        drawPath(panel, bg.copy(alpha = .95f))
        drawPath(panel, cyan.copy(alpha = .7f), style = Stroke(1.5.dp.toPx()))
        val tick = 2.dp.toPx()
        drawLine(yellow, Offset(w - 30.dp.toPx(), tick * 1.5f), Offset(w - 6.dp.toPx(), tick * 1.5f), tick)
        drawLine(yellow, Offset(6.dp.toPx(), h - tick * 1.5f), Offset(30.dp.toPx(), h - tick * 1.5f), tick)
        val l = cardLayout(size)
        segments(l, frame, lit = false)
        val labels = assets.paint(assets.orbitron, 500)
        for (set in frame.scaleSets()) {
            val step = niceStep(set.max).label * 2
            var v = 0f
            while (v <= set.max + 1e-3f && v <= frame.rangeKmh * 1.001f) {
                val at = polar(l.center, l.radius * .7f, angleRad(v, frame.rangeKmh))
                text(fmt(v.toDouble(), 0), at.x, at.y, labels, l.radius * .1f, (if (frame.inRedZone(v)) LIMIT_RED else cyan).copy(alpha = .5f * set.alpha))
                v += step
            }
        }
        text(frame.options.units.label.uppercase(), l.center.x, l.center.y + l.radius * .48f, assets.paint(assets.orbitron, 600), l.radius * .11f, cyan, spacingEm = .25f)
    }

    override fun DrawScope.drawDynamic(frame: GaugeFrame, assets: GaugeAssets) {
        val l = cardLayout(size)
        val c = l.center
        if (frame.needleKmh > .05f) segments(l, frame, lit = true)
        // A chromatic split behind the digits, like a glitching display.
        val s = frame.readout.toString()
        val p = assets.paint(assets.orbitron, 800)
        val sz = l.radius * .5f
        text(s, c.x - sz * .05f, c.y, p, sz, magenta.copy(alpha = .55f))
        text(s, c.x + sz * .05f, c.y, p, sz, cyan.copy(alpha = .55f))
        text(s, c.x, c.y, p, sz, frame.warned(yellow))
        cardStats(frame, l, assets.paint(assets.orbitron, 600), assets.paint(assets.orbitron, 500), Color(0xFFE8F6F4), cyan.copy(alpha = .65f), scale = .85f)
    }

    private fun DrawScope.segments(l: GaugeLayout, frame: GaugeFrame, lit: Boolean) {
        val c = l.center
        val r = l.radius
        val lw = r * .16f
        val each = DIAL_SWEEP_DEG / SEGMENTS
        val limit = frame.limitFraction()
        val n = if (lit) ceil(frame.fraction() * SEGMENTS).toInt().coerceIn(1, SEGMENTS) else SEGMENTS
        val box = Offset(c.x - r, c.y - r)
        val boxSize = Size(2 * r, 2 * r)
        for (i in 0 until n) {
            val red = limit != null && (i + .5f) / SEGMENTS >= limit
            val col = when {
                !lit -> (if (red) LIMIT_RED else dim).copy(alpha = .28f)
                i == n - 1 -> yellow
                red -> LIMIT_RED
                else -> frame.warned(cyan)
            }
            val start = DIAL_START_DEG + i * each + 1f
            if (lit) drawArc(col.copy(alpha = .2f), start - .6f, each - .8f, false, box, boxSize, style = Stroke(lw * 1.8f)) // glow
            drawArc(col, start, each - 2f, false, box, boxSize, style = Stroke(lw))
        }
    }
}

/** Old West: a brass-rimmed dial on parchment, red needle, woodtype numerals. */
object OldWestTheme : GaugeTheme {
    override val id = "world-old_west"
    override val title = "OLD WEST"
    override val spring = NeedleSpring(omega = 9.0, zeta = .55)

    private val paper = Color(0xFFEFE3C6)
    private val face = Color(0xFFF7EEDA)
    private val ink = Color(0xFF3B2A1A)
    private val brass = Color(0xFFC99A47)
    private val brassDark = Color(0xFF6E4E1E)
    private val red = Color(0xFFB52E31)

    override fun DrawScope.drawStatic(frame: GaugeFrame, assets: GaugeAssets) {
        val corner = CornerRadius(12.dp.toPx())
        drawRoundRect(paper.copy(alpha = .97f), cornerRadius = corner)
        val i1 = 5.dp.toPx()
        val i2 = 8.dp.toPx()
        drawRoundRect(ink.copy(alpha = .6f), Offset(i1, i1), Size(size.width - 2 * i1, size.height - 2 * i1), CornerRadius(8.dp.toPx()), Stroke(1.2.dp.toPx()))
        drawRoundRect(ink.copy(alpha = .3f), Offset(i2, i2), Size(size.width - 2 * i2, size.height - 2 * i2), CornerRadius(6.dp.toPx()), Stroke(.8.dp.toPx()))
        val l = cardLayout(size)
        val c = l.center
        val r = l.radius
        drawCircle(Brush.radialGradient(0f to face, .85f to face, 1f to Color(0xFFE2D2AE), center = c, radius = r * 1.04f), r * 1.04f, c)
        drawCircle(
            Brush.sweepGradient(0f to brass, .25f to brassDark, .5f to brass, .75f to brassDark, 1f to brass, center = c),
            r * 1.08f, c, style = Stroke(r * .1f),
        )
        drawCircle(ink.copy(alpha = .7f), r * 1.02f, c, style = Stroke(1.dp.toPx()))
        limitArc(frame, c, r * .9f, r * .06f, red.copy(alpha = .5f))
        val numerals = assets.paint(assets.rye)
        for (set in frame.scaleSets()) {
            set.forEachTick(frame.rangeKmh) { v, major ->
                val a = angleRad(v, frame.rangeKmh)
                val col = (if (frame.inRedZone(v)) red else ink).copy(alpha = set.alpha)
                val len = if (major) r * .14f else r * .07f
                drawLine(col, polar(c, r * .96f - len, a), polar(c, r * .96f, a), if (major) r * .028f else r * .013f)
            }
            val step = niceStep(set.max).label
            val every = if (set.max / step > 6) 2 else 1
            var v = 0f
            var k = 0
            while (v <= set.max + 1e-3f && v <= frame.rangeKmh * 1.001f) {
                if (k % every == 0) {
                    val at = polar(c, r * .64f, angleRad(v, frame.rangeKmh))
                    text(fmt(v.toDouble(), 0), at.x, at.y, numerals, r * .15f, (if (frame.inRedZone(v)) red else ink).copy(alpha = set.alpha))
                }
                v += step
                k++
            }
        }
        text(frame.options.units.label.uppercase(), c.x, c.y + r * .32f, numerals, r * .1f, brassDark, spacingEm = .1f)
    }

    override fun DrawScope.drawDynamic(frame: GaugeFrame, assets: GaugeAssets) {
        val l = cardLayout(size)
        val c = l.center
        val r = l.radius
        val rye = assets.paint(assets.rye)
        text(frame.readout.toString(), c.x, c.y + r * .7f, rye, r * .2f, frame.warned(ink, red))
        needle(c, r * .86f, angleRad(frame.needleKmh, frame.rangeKmh), red, tail = .22f, width = .04f)
        drawCircle(brass, r * .09f, c)
        drawCircle(brassDark, r * .09f, c, style = Stroke(r * .025f))
        cardStats(frame, l, rye, rye, ink, ink.copy(alpha = .6f), scale = .9f)
    }
}

/** Pixel World: a blocky 8-bit card, a dial of square blocks and a chunky pixel readout. */
object PixelWorldTheme : GaugeTheme {
    override val id = "world-pixel_world"
    override val title = "PIXEL WORLD"
    override val spring = NeedleSpring(omega = 14.0, zeta = 1.0)

    private val dark = Color(0xFF1F2618)
    private val frameCol = Color(0xFFC7C4A5)
    private val panel = Color(0xFF3B4A2E)
    private val grass = Color(0xFF8DC05A)
    private val red = Color(0xFFE95345)
    private val stone = Color(0xFF777D6C)
    private const val BLOCKS = 22

    /** The pixel unit: everything snaps to it. */
    private fun DrawScope.unit() = floor(minOf(size.width, size.height) / 96f).coerceAtLeast(2f)

    private fun DrawScope.notched(x: Float, y: Float, w: Float, h: Float, u: Float, color: Color) {
        drawRect(color, Offset(x + u, y), Size(w - 2 * u, h))
        drawRect(color, Offset(x, y + u), Size(w, h - 2 * u))
    }

    override fun DrawScope.drawStatic(frame: GaugeFrame, assets: GaugeAssets) {
        val u = unit()
        val w = size.width
        val h = size.height
        val f = 2 * u // the frame is drawn in double pixels
        notched(0f, 0f, w, h, f, dark)
        notched(f, f, w - 2 * f, h - 2 * f, f, frameCol)
        notched(2 * f, 2 * f, w - 4 * f, h - 4 * f, f, panel)
        blocks(cardLayout(size), frame, lit = false, u)
    }

    override fun DrawScope.drawDynamic(frame: GaugeFrame, assets: GaugeAssets) {
        val u = unit()
        val l = cardLayout(size)
        val c = l.center
        if (frame.needleKmh > .05f) blocks(l, frame, lit = true, u)
        val pixel = assets.paint(assets.silkscreen)
        val s = frame.readout.toString()
        val sz = l.radius * .44f
        text(s, c.x + u, c.y + u, pixel, sz, dark)
        text(s, c.x, c.y, pixel, sz, frame.warned(Color.White, red))
        text(frame.options.units.label.uppercase(), c.x, c.y + l.radius * .46f, pixel, l.radius * .12f, grass)
        cardStats(frame, l, pixel, pixel, Color.White, frameCol, scale = .8f, shadow = dark to u * .6f)
    }

    private fun DrawScope.blocks(l: GaugeLayout, frame: GaugeFrame, lit: Boolean, u: Float) {
        val b = (floor(l.radius * .19f / u) * u).coerceAtLeast(4 * u) // room for the bevel rows
        val limit = frame.limitFraction()
        val n = if (lit) ceil(frame.fraction() * BLOCKS).toInt().coerceIn(1, BLOCKS) else BLOCKS
        for (i in 0 until n) {
            val f = i / (BLOCKS - 1f)
            val p = polar(l.center, l.radius, angleRad(f * frame.rangeKmh, frame.rangeKmh))
            val x = floor((p.x - b / 2) / u) * u
            val y = floor((p.y - b / 2) / u) * u
            val over = limit != null && f >= limit
            if (!lit) {
                drawRect(dark.copy(alpha = .55f), Offset(x, y), Size(b, b))
                drawRect((if (over) red else stone).copy(alpha = .35f), Offset(x + u, y + u), Size(b - 2 * u, b - 2 * u))
                continue
            }
            val col = if (i == n - 1 || over) red else grass
            drawRect(dark, Offset(x, y), Size(b, b))
            drawRect(col, Offset(x + u, y + u), Size(b - 2 * u, b - 2 * u))
            drawRect(Color.White.copy(alpha = .35f), Offset(x + u, y + u), Size(b - 2 * u, u)) // lit top edge
            drawRect(Color.Black.copy(alpha = .25f), Offset(x + u, y + b - 2 * u), Size(b - 2 * u, u)) // shaded bottom edge
        }
    }
}

/** Green Terminal: a phosphor CRT readout with scanlines, a block bar and a blinking cursor. */
object GreenTerminalTheme : GaugeTheme {
    override val id = "world-green_terminal"
    override val title = "GREEN TERMINAL"
    override val spring = NeedleSpring(omega = 18.0, zeta = 1.0)

    private val bg = Color(0xFF041008)
    private val phos = Color(0xFF9AF477)
    private val bright = Color(0xFFD5FF9B)
    private const val CELLS = 20

    private data class Lines(val wide: Boolean, val pad: Float, val numberY: Float, val numberSize: Float, val barY: Float, val barW: Float, val statsX: Float, val statsY: Float, val statsStep: Float, val statsSize: Float)

    private fun DrawScope.lines(): Lines {
        val w = size.width
        val h = size.height
        val pad = minOf(w, h) * .07f
        return if (w > h * 1.15f) {
            Lines(true, pad, h * .44f, h * .4f, h * .8f, w * .5f, w * .58f, h * .22f, h * .19f, h * .13f)
        } else {
            Lines(false, pad, h * .2f, minOf(h * .2f, w * .32f), h * .37f, w - 2 * pad, pad, h * .52f, h * .11f, minOf(h * .075f, w * .08f))
        }
    }

    override fun DrawScope.drawStatic(frame: GaugeFrame, assets: GaugeAssets) {
        val corner = CornerRadius(8.dp.toPx())
        drawRoundRect(bg.copy(alpha = .96f), cornerRadius = corner)
        drawRoundRect(Brush.radialGradient(listOf(phos.copy(alpha = .1f), Color.Transparent), center = center, radius = size.maxDimension * .6f), cornerRadius = corner)
        var y = 0f
        val gap = 3.dp.toPx()
        while (y < size.height) {
            drawLine(Color.Black.copy(alpha = .3f), Offset(0f, y), Offset(size.width, y), 1f)
            y += gap
        }
        drawRoundRect(phos.copy(alpha = .55f), cornerRadius = corner, style = Stroke(1.dp.toPx()))
        val t = lines()
        val vt = assets.paint(assets.vt323)
        text("> SPEED", t.pad, t.numberY - t.numberSize * .62f, vt, t.numberSize * .3f, phos.copy(alpha = .7f), Align.LEFT)
        val cellW = t.barW / CELLS
        val limit = frame.limitFraction()
        for (i in 0 until CELLS) {
            val red = limit != null && (i + .5f) / CELLS >= limit
            drawRect((if (red) LIMIT_RED else phos).copy(alpha = .35f), Offset(t.pad + i * cellW + cellW * .12f, t.barY - cellW * .5f), Size(cellW * .76f, cellW), style = Stroke(1f))
        }
    }

    override fun DrawScope.drawDynamic(frame: GaugeFrame, assets: GaugeAssets) {
        val t = lines()
        val vt = assets.paint(assets.vt323)
        val digits = frame.readout.toString().padStart(3, '0')
        val ink = frame.warned(bright)
        glow(digits, t.pad, t.numberY, vt, t.numberSize, ink)
        val unitX = t.pad + t.numberSize * 1.5f
        glow(frame.options.units.label.uppercase(), unitX, t.numberY + t.numberSize * .2f, vt, t.numberSize * .28f, phos)
        if ((frame.timeS * 2).toInt() % 2 == 0) {
            drawRect(phos, Offset(unitX + t.numberSize * .75f, t.numberY + t.numberSize * .05f), Size(t.numberSize * .14f, t.numberSize * .26f)) // cursor
        }
        val cellW = t.barW / CELLS
        val limit = frame.limitFraction()
        val n = ceil(frame.fraction() * CELLS).toInt()
        for (i in 0 until n) {
            val red = limit != null && (i + .5f) / CELLS >= limit
            drawRect(if (red) LIMIT_RED else ink, Offset(t.pad + i * cellW + cellW * .12f, t.barY - cellW * .5f), Size(cellW * .76f, cellW))
        }
        frame.statPairs().forEachIndexed { i, (k, v) ->
            val y = t.statsY + i * t.statsStep
            text("> $k", t.statsX, y, vt, t.statsSize, phos.copy(alpha = .75f), Align.LEFT)
            glow(v, size.width - t.pad, y, vt, t.statsSize * 1.15f, bright, Align.RIGHT)
        }
    }

    /** Phosphor text: a faint halo, then the glyphs. */
    private fun DrawScope.glow(s: String, x: Float, y: Float, p: GaugePaint, sz: Float, color: Color, align: Align = Align.LEFT) {
        val o = sz * .03f
        for ((dx, dy) in listOf(o to 0f, -o to 0f, 0f to o, 0f to -o)) text(s, x + dx, y + dy, p, sz, color.copy(alpha = .22f), align)
        text(s, x, y, p, sz, color, align)
    }
}
