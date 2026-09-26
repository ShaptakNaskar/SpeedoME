package com.sappy.speedome.gauges

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Today's digital-dashboard look: pure black, glowing 270° arc, big clean number (docs/plan.md §9). */
object ModernTheme : GaugeTheme {
    override val id = "modern"
    override val title = "MODERN"
    override val spring = NeedleSpring(omega = 13.0, zeta = 1.0)

    override fun DrawScope.drawStatic(frame: GaugeFrame, assets: GaugeAssets) {
        val l = layoutFor(size)
        val c = l.center
        val r = l.radius
        val accent = frame.options.accent
        drawRect(Color.Black)
        val haloCenter = Offset(c.x, c.y + r * .4f)
        backlightGlow(haloCenter, r * 1.6f, accent, .11f, frame.options)
        val lw = r * .085f
        drawArc(
            Color(0xFF151718), DIAL_START_DEG, DIAL_SWEEP_DEG, false, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r),
            style = Stroke(lw, cap = StrokeCap.Round),
        )
        // Speed limit: the track turns red from the limit on. The round cap starts right at the limit.
        frame.limitFraction()?.let { f ->
            val cap = Math.toDegrees(lw / 2.0 / r).toFloat()
            val start = DIAL_START_DEG + DIAL_SWEEP_DEG * f + cap
            val sweep = (DIAL_SWEEP_DEG * (1 - f) - cap).coerceAtLeast(0f)
            drawArc(LIMIT_RED.copy(alpha = .4f), start, sweep, false, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), style = Stroke(lw, cap = StrokeCap.Round))
        }
        val labels = assets.paint(assets.outfit, 300)
        for (set in frame.scaleSets()) {
            val step = niceStep(set.max)
            var v = 0f
            while (v <= set.max + 1e-3f && v <= frame.rangeKmh * 1.001f) {
                val a = angleRad(v, frame.rangeKmh)
                val red = frame.inRedZone(v)
                val col = if (red) LIMIT_RED else Color.White
                drawCircle(col.copy(alpha = .35f * set.alpha), r * .008f + 1.dp.toPx() / 2, polar(c, r * 1.12f, a))
                val p = polar(c, r * .8f, a)
                text(fmt(v.toDouble(), 0), p.x, p.y, labels, r * .075f, col.copy(alpha = (if (red) .7f else .42f) * set.alpha))
                v += step.label
            }
        }
        text(frame.options.units.label.uppercase(), c.x, c.y + r * .42f, assets.paint(assets.outfit, 600), r * .07f, accent, spacingEm = .4f)
    }

    override fun DrawScope.drawDynamic(frame: GaugeFrame, assets: GaugeAssets) {
        val l = layoutFor(size)
        val c = l.center
        val r = l.radius
        val accent = frame.options.accent
        val lw = r * .085f
        val box = Offset(c.x - r, c.y - r)
        val boxSize = Size(2 * r, 2 * r)
        if (frame.needleKmh > .05f) {
            val sweep = DIAL_SWEEP_DEG * (frame.needleKmh / frame.rangeKmh).coerceIn(0f, 1.02f)
            // Near the limit the arc fades to red; in 16 steps, so only a handful of brushes are ever cached.
            val arc = if (frame.limitWarn > 0f) lerp(accent, LIMIT_RED, (frame.limitWarn * 16).roundToInt() / 16f) else accent
            val brush = assets.memo("modern-arc-${arc.value}", size) {
                Brush.sweepGradient(0f to arc.copy(alpha = .25f), .75f to arc, 1f to arc, center = c)
            }
            rotate(DIAL_START_DEG, c) {
                // soft glow: wider, fainter passes under the arc
                drawArc(arc.copy(alpha = .08f), 0f, sweep, false, box, boxSize, style = Stroke(lw * 2.4f, cap = StrokeCap.Round))
                drawArc(arc.copy(alpha = .12f), 0f, sweep, false, box, boxSize, style = Stroke(lw * 1.7f, cap = StrokeCap.Round))
                drawArc(brush, 0f, sweep, false, box, boxSize, style = Stroke(lw, cap = StrokeCap.Round))
            }
            drawCircle(Color.White, lw * .32f, polar(c, r, angleRad(frame.needleKmh, frame.rangeKmh)))
        }
        frame.target?.let { t -> targetRing(c, r, t, accent) }
        bloomText(frame.readout.toString(), c.x, c.y + r * .02f, assets.paint(assets.outfit, 200), r * .6f, frame.warned(Color.White), frame.options, strength = .45f)
        stats(frame, l, assets)
    }

    /** Thin outer ring: target progress over the same 270° as the dial, white once reached. */
    private fun DrawScope.targetRing(c: Offset, r: Float, t: GaugeTarget, accent: Color) {
        val rr = r * 1.2f
        val box = Offset(c.x - rr, c.y - rr)
        val boxSize = Size(2 * rr, 2 * rr)
        val w = r * .018f
        drawArc(Color.White.copy(alpha = .08f), DIAL_START_DEG, DIAL_SWEEP_DEG, false, box, boxSize, style = Stroke(w, cap = StrokeCap.Round))
        if (t.progress > 0f) {
            val col = if (t.arrived) Color.White else accent.copy(alpha = .85f)
            drawArc(col, DIAL_START_DEG, DIAL_SWEEP_DEG * t.progress, false, box, boxSize, style = Stroke(w, cap = StrokeCap.Round))
        }
    }

    private fun DrawScope.stats(frame: GaugeFrame, l: GaugeLayout, assets: GaugeAssets) {
        val pairs = frame.statPairs()
        val n = pairs.size
        val s = l.stats
        val value = assets.paint(assets.outfit, 300)
        val label = assets.paint(assets.outfit, 600)
        pairs.forEachIndexed { i, (k, v) ->
            val x = if (l.landscape) s.center.x else s.left + s.width * (i + .5f) / n
            val y = if (l.landscape) s.top + s.height * (i + .5f) / (n + 1) else s.top + minOf(s.height * .3f, 34.dp.toPx())
            val sz = if (l.landscape) minOf(s.height / (n + 1) * .46f, 40.dp.toPx()) else minOf(s.width / n * .3f, 28.dp.toPx())
            text(v, x, y, value, sz, Color(0xFFF2F2F2))
            text(k, x, y + sz * .9f, label, sz * .34f, Color.White.copy(alpha = .4f), spacingEm = .18f)
        }
    }
}
