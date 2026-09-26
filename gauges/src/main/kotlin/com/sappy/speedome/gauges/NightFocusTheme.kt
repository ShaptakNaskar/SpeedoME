package com.sappy.speedome.gauges

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

/**
 * Inspired by Saab's Night Panel: only the speedometer is lit, and only up to your focus speed; the
 * rest of the scale wakes up as you approach it. Everything else stays dark unless it needs you.
 */
object NightFocusTheme : GaugeTheme {
    override val id = "night"
    override val title = "NIGHT FOCUS"
    override val spring = NeedleSpring(omega = 11.0, zeta = 1.0)
    override val usesAutoRange = false

    private val green = Color(0xFF7DFF9A)
    private val orange = Color(0xFFFF7A22)
    private val amber = Color(0xFFFFB347)

    override fun fixedRangeKmh(options: ThemeOptions) = options.nightMaxKmh

    /** Its own dial setting, or the speed-limit scale (the driver's range) while a limit is set. */
    private fun GaugeFrame.dialMax() = if (limitKmh != null) rangeKmh else options.nightMaxKmh.toFloat()

    override fun DrawScope.drawStatic(frame: GaugeFrame, assets: GaugeAssets) {
        val l = layoutFor(size)
        val c = l.center
        val r = l.radius
        val o = frame.options
        val max = frame.dialMax()
        val bright = o.nightBrightness
        drawRect(Color.Black)
        val step = niceStep(max)
        val labels = assets.paint(assets.barlowSemi)
        val n = kotlin.math.floor(max / step.minor + 1e-3f).toInt()
        for (i in 0..n) {
            val v = i * step.minor
            val lit = (if (v <= o.nightFocusKmh + 1e-3f) 1f else frame.nightUpper) * bright
            if (lit < .01f) continue
            val a = angleRad(v, max)
            val major = kotlin.math.abs(v / step.label - kotlin.math.round(v / step.label)) < 1e-3f
            val inner = if (major) r * .84f else r * .9f
            val w = if (major) r * .018f else r * .008f
            val col = if (frame.inRedZone(v)) LIMIT_RED else green
            drawLine(col.copy(alpha = .22f * lit), polar(c, r, a), polar(c, inner, a), strokeWidth = w * 3.2f)
            drawLine(col.copy(alpha = .9f * lit), polar(c, r, a), polar(c, inner, a), strokeWidth = w)
            if (major) {
                val p = polar(c, r * .7f, a)
                bloomText(fmt(v.toDouble(), 0), p.x, p.y, labels, r * .11f, col.copy(alpha = .95f * lit), frame.options, strength = .8f)
            }
        }
        // Speed limit: a thin redline outside the ticks, lit like the scale beneath it.
        frame.limitKmh?.takeIf { it < max }?.let { limit ->
            val focus = o.nightFocusKmh.toFloat()
            scaleArc(c, r * 1.035f, r * .02f, limit, minOf(focus, max), max, LIMIT_RED.copy(alpha = .85f * bright))
            if (frame.nightUpper > .01f) scaleArc(c, r * 1.035f, r * .02f, maxOf(limit, focus), max, max, LIMIT_RED.copy(alpha = .85f * bright * frame.nightUpper))
        }
        text(frame.options.units.label, c.x, c.y - r * .32f, labels, r * .07f, green.copy(alpha = .55f * bright))
    }

    override fun DrawScope.drawDynamic(frame: GaugeFrame, assets: GaugeAssets) {
        val l = layoutFor(size)
        val c = l.center
        val r = l.radius
        val bright = frame.options.nightBrightness
        val alpha = maxOf(.35f, bright)
        val a = angleRad(frame.needleKmh, frame.dialMax())
        val needleColor = frame.warned(orange)
        needle(c, r * .92f, a, needleColor.copy(alpha = .28f * alpha), tail = .12f, width = .06f)
        needle(c, r * .92f, a, needleColor.copy(alpha = alpha), tail = .12f, width = .026f)
        drawCircle(Color(0xFF050505), r * .07f, c)
        drawCircle(green.copy(alpha = .25f * bright), r * .07f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))

        val warnings = buildList {
            frame.limitKmh?.let { if (frame.limitWarn >= 1f) add("SPEED LIMIT ${fmt(it.toDouble(), 0)}" to LIMIT_RED) }
            if (frame.gps == GpsDot.NONE) add("GPS SIGNAL LOST" to amber)
            if (frame.batteryLow) add("PHONE BATTERY LOW" to amber)
            frame.target?.let { t ->
                // The target only lights up when it needs attention: the last kilometre and arrival.
                val units = frame.options.units
                when {
                    t.arrived -> add("TARGET REACHED" to amber)
                    t.remainingM > units.metresPerDistance -> Unit // the last km (or mile) only
                    units == SpeedUnit.KMH -> add("TARGET ${fmt(t.remainingM, 0)} M" to amber)
                    else -> add("TARGET ${frame.fmtDist(t.remainingM)} MI" to amber)
                }
            }
        }
        val s = l.stats
        val paint = assets.paint(assets.barlowSemi)
        warnings.forEachIndexed { i, (w, col) ->
            val y = if (l.landscape) s.top + s.height * .3f + i * 30.dp.toPx() else s.top + 26.dp.toPx() + i * 30.dp.toPx()
            val x = s.center.x
            drawCircle(col, 4.dp.toPx(), Offset(x - w.length * 4.6f.dp.toPx() - 10.dp.toPx(), y))
            text(w, x, y, paint, 15.dp.toPx(), col, spacingEm = .12f)
        }
    }
}
