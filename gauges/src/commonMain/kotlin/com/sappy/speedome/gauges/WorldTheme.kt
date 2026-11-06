package com.sappy.speedome.gauges

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/** A map world's colours (from its Waypoint style): UI accent, route line, land, roads and buildings. */
data class WorldPalette(val accent: Color, val route: Color, val ground: Color, val road: Color, val building: Color) {
    /** Light-land worlds (parchment, snow, paper) get a light card with dark ink. */
    val light: Boolean get() = ground.luminance() > .3f
    val panel: Color get() = if (light) lerp(ground, Color.White, .35f) else lerp(ground, Color.Black, .45f)
    val ink: Color get() = if (light) lerp(building, Color.Black, .82f) else Color(0xFFF2F2EE)

    /** The arc and unit colour: the accent, or the darker route colour on a light card where pale accents wash out. */
    val strong: Color get() = if (light) route else accent
}

/**
 * Where a world speedo's dial and stats go on its card: on a wide card (portrait, docked under the
 * map) the dial hugs the left edge with the stats beside it; a tall card (landscape) stacks them.
 */
fun cardLayout(size: Size): GaugeLayout {
    val w = size.width
    val h = size.height
    if (w <= h * 1.15f) return layoutFor(size)
    val r = minOf(h * .4f, w * .2f)
    val cx = h * .1f + r * 1.12f
    return GaugeLayout(true, Offset(cx, h * .52f), r, Rect(cx + r * 1.25f, h * .1f, w - h * .08f, h * .9f))
}

/**
 * The trip stats on a world card: a 2×2 grid beside the dial (wide) or one row under it (tall).
 * [shadow] adds a hard drop shadow (colour, offset) for pixel-style lettering.
 */
fun DrawScope.cardStats(
    frame: GaugeFrame,
    l: GaugeLayout,
    value: GaugePaint,
    label: GaugePaint,
    ink: Color,
    muted: Color,
    scale: Float = 1f,
    shadow: Pair<Color, Float>? = null,
) {
    val pairs = frame.statPairs()
    val s = l.stats
    val n = pairs.size
    pairs.forEachIndexed { i, (k, v) ->
        val rows = (n + 1) / 2
        val x = if (l.landscape) s.left + s.width * (i % 2 + .5f) / 2 else s.left + s.width * (i + .5f) / n
        val y = if (l.landscape) s.top + s.height * (i / 2 + .4f) / rows else s.top + minOf(s.height * .3f, 30.dp.toPx())
        val sz = scale * if (l.landscape) minOf(s.height / rows * .38f, 30.dp.toPx()) else minOf(s.width / n * .3f, 26.dp.toPx())
        val cellW = s.width / if (l.landscape) 2 else n
        val valueSize = minOf(sz, cellW * .86f / (v.length * .65f))
        val labelSize = minOf(maxOf(sz * .36f, 8.dp.toPx()), cellW * .9f / (k.length * .72f))
        shadow?.let { (c, o) -> text(v, x + o, y + o, value, valueSize, c) }
        text(v, x, y, value, valueSize, ink)
        text(k, x, y + sz * .9f, label, labelSize, muted, spacingEm = .08f)
    }
}

/**
 * The speedo docked on a world's map, coloured from its [palette]: a card with a 270° arc, the speed
 * and the trip stats. Worlds without a hand-made speedo use this one. Wide boxes put the stats beside
 * the dial, tall ones below it ([layoutFor]).
 */
data class WorldTheme(override val id: String, override val title: String, val palette: WorldPalette) : GaugeTheme {
    override val spring = NeedleSpring(omega = 12.0, zeta = 1.0)

    override fun DrawScope.drawStatic(frame: GaugeFrame, assets: GaugeAssets) {
        val p = palette
        val corner = CornerRadius(18.dp.toPx())
        drawRoundRect(p.panel.copy(alpha = .94f), cornerRadius = corner)
        drawRoundRect(p.accent.copy(alpha = .35f), cornerRadius = corner, style = Stroke(1.dp.toPx()))
        val l = cardLayout(size)
        val c = l.center
        val r = l.radius
        val lw = r * .11f
        drawArc(
            p.road.copy(alpha = if (p.light) .5f else .35f), DIAL_START_DEG, DIAL_SWEEP_DEG, false,
            Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), style = Stroke(lw, cap = StrokeCap.Round),
        )
        limitArc(frame, c, r, lw, LIMIT_RED.copy(alpha = .55f))
        val labels = assets.paint(assets.outfit, 500)
        for (set in frame.scaleSets()) {
            val step = niceStep(set.max)
            var v = 0f
            while (v <= set.max + 1e-3f && v <= frame.rangeKmh * 1.001f) {
                val col = if (frame.inRedZone(v)) LIMIT_RED else p.ink
                val at = polar(c, r * .74f, angleRad(v, frame.rangeKmh))
                text(fmt(v.toDouble(), 0), at.x, at.y, labels, r * .12f, col.copy(alpha = .5f * set.alpha))
                v += step.label * 2
            }
        }
        text(frame.options.units.label.uppercase(), c.x, c.y + r * .5f, assets.paint(assets.outfit, 600), r * .12f, p.strong, spacingEm = .3f)
    }

    override fun DrawScope.drawDynamic(frame: GaugeFrame, assets: GaugeAssets) {
        val p = palette
        val l = cardLayout(size)
        val c = l.center
        val r = l.radius
        val lw = r * .11f
        if (frame.needleKmh > .05f) {
            val sweep = DIAL_SWEEP_DEG * (frame.needleKmh / frame.rangeKmh).coerceIn(0f, 1.02f)
            drawArc(
                frame.warned(p.strong), DIAL_START_DEG, sweep, false, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r),
                style = Stroke(lw, cap = StrokeCap.Round),
            )
            drawCircle(if (p.light) p.ink else p.route, lw * .42f, polar(c, r, angleRad(frame.needleKmh, frame.rangeKmh)))
        }
        text(frame.readout.toString(), c.x, c.y, assets.paint(assets.outfit, 500), r * .62f, frame.warned(p.ink))
        cardStats(frame, l, assets.paint(assets.outfit, 500), assets.paint(assets.outfit, 600), p.ink, p.ink.copy(alpha = .55f))
    }
}
