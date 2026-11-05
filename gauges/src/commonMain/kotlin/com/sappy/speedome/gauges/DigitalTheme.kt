package com.sappy.speedome.gauges

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.roundToInt

/** 1980s digital dash: ramp bar graph, ghosted seven-segment digits, VFD mesh (docs/plan.md §9). */
object DigitalTheme : GaugeTheme {
    override val id = "digital"
    override val title = "DIGITAL"
    override val spring = NeedleSpring(omega = 18.0, zeta = 1.0)

    private const val BARS = 36
    private const val TRIP_CELLS = 5
    private const val SMALL_CELLS = 3

    /** Every position on screen, derived from the canvas size (and whether the dial is 4 digits wide). */
    private class Geometry(size: Size, density: Float, wide: Boolean) {
        val w = size.width
        val h = size.height
        val land = w > h * 1.15f
        val pad = w * .07f
        val bw = w - pad * 2
        val bh = if (land) h * .2f else minOf(h * .1f, w * .2f)
        val dh = if (land) h * .38f else minOf(h * .22f, w * .36f)
        val sh = minOf(h * .045f, 30 * density)
        /** Portrait stacks a second stats row (moving average, trip time); landscape fits all five in one. */
        private val row2 = if (land) 0f else sh + 34 * density
        private val blockH = bh + h * .08f + dh + h * .09f + sh + 20 * density + row2
        val top = if (land) h * .12f else ((h - blockH) / 2).coerceAtLeast(h * .06f)
        val bars: List<Path> = (0 until BARS).map { i ->
            val sw = bw / BARS
            val bar = bh * (.3f + .7f * i / BARS)
            val x = pad + i * sw
            val y = top + bh - bar
            Path().apply { moveTo(x + sw * .18f, y + bar); lineTo(x + sw * .38f, y); lineTo(x + sw * .95f, y); lineTo(x + sw * .75f, y + bar); close() }
        }
        val digits = if (wide) 4 else 3
        val dw = dh * .55f
        val gap = dw * .22f
        val total = digits * dw + (digits - 1) * gap
        val dx = (w - total) / 2 - dw * .3f
        val dy = if (land) h * .37f else top + bh + h * .08f
        val big = SegmentShapes(dw, dh)
        val rowY = if (land) h * .86f else dy + dh + h * .09f
        val rowY2 = rowY + row2
        val cellW = (w - pad * 2) / (if (land) 5 else 3)
        val sww = sh * .55f
        val small = SegmentShapes(sww, sh)

        fun bigCell(i: Int) = Offset(dx + i * (dw + gap), dy)

        fun smallCell(group: Int, i: Int): Offset {
            // The time group (4) leaves room for its colons after the hour and the minutes.
            val x = i * sww * 1.25f + if (group == 4) sww * .5f * ((if (i > 0) 1 else 0) + (if (i > 2) 1 else 0)) else 0f
            return if (land || group < 3) Offset(pad + cellW * group + x, rowY) else Offset(pad + cellW * (group - 3) + x, rowY2)
        }
    }

    private fun DrawScope.geometry(frame: GaugeFrame, assets: GaugeAssets): Geometry {
        val wide = frame.rangeToKmh > 999
        return assets.memo("digital-geo-$wide", size) { Geometry(size, density, wide) }
    }

    /** First bar of the red zone (bars from the speed limit up); [BARS] when there is none on the scale. */
    private fun GaugeFrame.redFrom(): Int = limitFraction()?.let { ceil(it * BARS - 1e-3f).toInt() } ?: BARS

    override fun DrawScope.drawStatic(frame: GaugeFrame, assets: GaugeAssets) {
        val g = geometry(frame, assets)
        val on = frame.options.digital.on
        drawRect(Color(0xFF030504))
        val redFrom = frame.redFrom()
        g.bars.forEachIndexed { i, bar -> drawPath(bar, if (i >= redFrom) LIMIT_RED.copy(alpha = .16f) else on.copy(alpha = .07f)) }
        val mono = assets.paint(assets.b612)
        for (set in frame.scaleSets()) for (v in barLabels(set.max)) {
            if (v > frame.rangeKmh * 1.001f) break
            text(fmt(v.toDouble(), 0), g.pad + g.bw * v / frame.rangeKmh, g.top + g.bh + 14.dp.toPx(), mono, 11.dp.toPx(), on.copy(alpha = .6f * set.alpha))
        }
        // Phosphor glow on the glass behind the big digits.
        backlightGlow(Offset(g.dx + g.total / 2, g.dy + g.dh / 2), g.total * .62f, frame.options.digital.glow, .09f, frame.options)
        for (i in 0 until g.digits) seg7(g.bigCell(i), g.big, g.dh, '8', on, .06f, null, ghostOnly = true)
        text(frame.options.units.label.uppercase(), g.dx + g.total + g.dw * .15f, g.dy + g.dh * .85f, assets.paint(assets.b612Bold), g.dh * .09f, on, Align.LEFT)
        val labels = listOf(if (frame.stats.stepMode) "STEPS" else "TRIP", "AVG", "MAX", "MOVING AVG", "TIME")
        labels.forEachIndexed { group, k ->
            val at = g.smallCell(group, 0)
            text(k, at.x + 2, at.y - 10.dp.toPx(), assets.paint(assets.b612Bold), 10.dp.toPx(), on.copy(alpha = .7f), Align.LEFT, spacingEm = .2f)
            val cells = if (group == 0 || group == 4) TRIP_CELLS else SMALL_CELLS
            for (i in 0 until cells) seg7(g.smallCell(group, i), g.small, g.sh, '8', on, .06f, null, ghostOnly = true)
        }
    }

    override fun DrawScope.drawDynamic(frame: GaugeFrame, assets: GaugeAssets) {
        val g = geometry(frame, assets)
        val on = frame.options.digital.on
        val glow = frame.options.digital.glow
        val lit = ((frame.needleKmh / frame.rangeKmh).coerceIn(0f, 1f) * BARS).roundToInt()
        // Nearing the limit the bar and digits fade to red; bars inside the red zone are always red.
        val redFrom = frame.redFrom()
        val barOn = frame.warned(on)
        val barGlow = frame.warned(glow)
        for (i in 0 until lit) {
            val red = i >= redFrom
            drawPath(g.bars[i], (if (red) LIMIT_RED else barGlow).copy(alpha = .3f), style = Stroke(g.bw / BARS * .3f))
            drawPath(g.bars[i], if (red) LIMIT_RED else barOn)
        }
        frame.readout.coerceAtMost(9999).toString().padStart(g.digits, ' ').takeLast(g.digits).forEachIndexed { i, ch ->
            seg7(g.bigCell(i), g.big, g.dh, ch, barOn, 0f, barGlow)
        }
        // Trip in tenths of a km or mile (a dot before the last cell) or steps; then average and max.
        val trip = if (frame.stats.stepMode) {
            (frame.stats.steps % 100_000).toString().padStart(TRIP_CELLS)
        } else {
            (frame.stats.distanceM / frame.options.units.metresPerDistance * 10).toLong().coerceAtMost(99_999).toString().padStart(2, '0').padStart(TRIP_CELLS)
        }
        trip.forEachIndexed { i, ch -> seg7(g.smallCell(0, i), g.small, g.sh, ch, on, 0f, glow) }
        if (!frame.stats.stepMode) {
            val last = g.smallCell(0, TRIP_CELLS - 1)
            drawCircle(on, g.sh * .06f, Offset(last.x - g.sww * .14f, last.y + g.sh * .95f))
        }
        fmt(frame.stats.avgOverallKmh.coerceAtMost(999.0), 0).padStart(SMALL_CELLS).forEachIndexed { i, ch -> seg7(g.smallCell(1, i), g.small, g.sh, ch, on, 0f, glow) }
        fmt(frame.stats.maxKmh.coerceAtMost(999.0), 0).padStart(SMALL_CELLS).forEachIndexed { i, ch -> seg7(g.smallCell(2, i), g.small, g.sh, ch, on, 0f, glow) }
        fmt(frame.stats.avgMovingKmh.coerceAtMost(999.0), 0).padStart(SMALL_CELLS).forEachIndexed { i, ch -> seg7(g.smallCell(3, i), g.small, g.sh, ch, on, 0f, glow) }
        // Trip time as H:MM:SS on five cells (M:SS under an hour), with drawn colons.
        val t = frame.stats.elapsedS.toLong().coerceIn(0, 9 * 3600 + 3599)
        val hours = t / 3600
        val mm = (t % 3600 / 60).toString().let { if (hours > 0) it.padStart(2, '0') else it.padStart(2) }
        val clock = (if (hours > 0) hours.toString() else " ") + mm + (t % 60).toString().padStart(2, '0')
        clock.forEachIndexed { i, ch -> seg7(g.smallCell(4, i), g.small, g.sh, ch, on, 0f, glow) }
        for (after in listOfNotNull(if (hours > 0) 0 else null, 2)) {
            val next = g.smallCell(4, after + 1)
            for (fy in listOf(.3f, .7f)) drawCircle(on, g.sh * .06f, Offset(next.x - g.sww * .4f + g.sh * .1f * (.5f - fy), next.y + g.sh * fy))
        }
        drawRect(assets.memo("mesh", Size(density, density)) { meshBrush(density) })
    }
}
