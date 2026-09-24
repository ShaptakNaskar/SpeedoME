package com.sappy.speedome.gauges

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.floor

/**
 * Aircraft primary-flight-display style: a speed tape scrolling past a rolling-digit pointer box,
 * a yellow trend arrow (speed in 10 s), altitude tape, heading strip and PFD annunciations.
 */
object SpeedTapeTheme : GaugeTheme {
    override val id = "tape"
    override val title = "SPEED TAPE"
    override val spring = NeedleSpring(omega = 12.0, zeta = 1.0)
    override val usesAutoRange = false

    private val tapeBg = Color(0xFF2A3038)
    private val ink = Color(0xFFE9EEF2)
    private val green = Color(0xFF39E58C)
    private val magenta = Color(0xFFFF5BD6)
    private val yellow = Color(0xFFFFD400)

    private class Geo(size: Size, val density: Float) {
        val w = size.width
        val h = size.height
        val land = w > h * 1.15f
        val tx = if (land) w * .08f else w * .07f
        val tw = if (land) w * .17f else w * .3f
        val ty = h * .07f
        val th = if (land) h * .72f else h * .6f
        val mid = ty + th / 2
        val boxH = 46 * density
        val boxX = tx - 6 * density
        val boxW = tw * .78f
        val ax = if (land) w * .8f else w * .76f
        val aw = if (land) w * .12f else w * .17f
        val hy = if (land) h * .93f else ty + th + 50 * density
        val hw = if (land) w * .5f else w * .86f
        val hx = (w - hw) / 2
        val tape = Rect(tx, ty, tx + tw, ty + th)
        val alt = Rect(ax, ty, ax + aw, ty + th)
        val heading = Rect(hx, hy - 22 * density, hx + hw, hy + 12 * density)
    }

    private fun DrawScope.geo(assets: GaugeAssets) = assets.memo("tape-geo", size) { Geo(size, density) }

    override fun DrawScope.drawStatic(frame: GaugeFrame, assets: GaugeAssets) {
        val g = geo(assets)
        drawRect(Brush.verticalGradient(listOf(Color(0xFF0A0E13), Color(0xFF121A22))))
        drawRect(tapeBg, g.tape.topLeft, g.tape.size)
        drawRect(tapeBg, g.alt.topLeft, g.alt.size)
        drawRect(tapeBg, g.heading.topLeft, g.heading.size)
        val mono = assets.paint(assets.b612)
        text("GS  KM/H", g.tape.center.x, g.ty - 12.dp.toPx(), mono, 11.dp.toPx(), green)
        text("ALT M", g.alt.center.x, g.ty - 12.dp.toPx(), mono, 11.dp.toPx(), green)
        val tri = Path().apply {
            moveTo(g.w / 2 - 7.dp.toPx(), g.hy - 30.dp.toPx()); lineTo(g.w / 2 + 7.dp.toPx(), g.hy - 30.dp.toPx()); lineTo(g.w / 2, g.hy - 20.dp.toPx()); close()
        }
        drawPath(tri, yellow)
    }

    override fun DrawScope.drawDynamic(frame: GaugeFrame, assets: GaugeAssets) {
        val g = geo(assets)
        val mono = assets.paint(assets.b612)
        val v = frame.needleKmh
        val step = frame.stats.stepMode
        val window = if (step) 24f else 120f
        val ppu = g.th / window
        val label = if (step) 2 else 10
        val minor = if (step) 1 else 5
        clipRect(g.tape.left, g.tape.top, g.tape.right, g.tape.bottom) {
            var t = (floor((v - window / 2) / minor) * minor).toInt()
            while (t <= v + window / 2) {
                if (t >= 0) {
                    val y = g.mid - (t - v) * ppu
                    val major = t % label == 0
                    drawLine(ink, Offset(g.tape.right, y), Offset(g.tape.right - (if (major) 16 else 9).dp.toPx(), y), strokeWidth = 2.dp.toPx())
                    if (major) text(t.toString(), g.tape.right - 22.dp.toPx(), y, mono, 15.dp.toPx(), ink, Align.RIGHT)
                }
                t += minor
            }
            val trend = v + frame.accelKmhS * 10
            if (abs(trend - v) > .8f) {
                val y2 = (g.mid - (trend - v) * ppu).coerceIn(g.tape.top, g.tape.bottom)
                val x = g.tape.right - 5.dp.toPx()
                drawLine(yellow, Offset(x, g.mid), Offset(x, y2), strokeWidth = 3.dp.toPx())
                val dir = if (trend > v) -1 else 1
                val head = Path().apply { moveTo(x - 6.dp.toPx(), y2 - dir * 8.dp.toPx()); lineTo(x, y2); lineTo(x + 6.dp.toPx(), y2 - dir * 8.dp.toPx()) }
                drawPath(head, yellow, style = Stroke(3.dp.toPx()))
            }
        }
        val box = Path().apply {
            moveTo(g.boxX, g.mid - g.boxH / 2); lineTo(g.boxX + g.boxW, g.mid - g.boxH / 2); lineTo(g.boxX + g.boxW + 10.dp.toPx(), g.mid)
            lineTo(g.boxX + g.boxW, g.mid + g.boxH / 2); lineTo(g.boxX, g.mid + g.boxH / 2); close()
        }
        drawPath(box, Color.Black)
        drawPath(box, Color.White, style = Stroke(2.dp.toPx()))
        rollingDigits(v, g.boxX + 4.dp.toPx(), g.mid, g.boxW - 8.dp.toPx(), g.boxH * .72f, assets.paint(assets.b612Bold), g.boxH * .62f)

        val alt = frame.altitudeM ?: 0.0
        val appm = g.th / 160
        clipRect(g.alt.left, g.alt.top, g.alt.right, g.alt.bottom) {
            var a = (floor((alt - 80) / 10) * 10).toInt()
            while (a <= alt + 80) {
                val y = (g.mid - (a - alt) * appm).toFloat()
                drawLine(ink, Offset(g.alt.left, y), Offset(g.alt.left + (if (a % 50 == 0) 14 else 8).dp.toPx(), y), strokeWidth = 2.dp.toPx())
                if (a % 50 == 0) text(a.toString(), g.alt.left + 18.dp.toPx(), y, mono, 12.dp.toPx(), ink, Align.LEFT)
                a += 10
            }
        }
        drawRect(Color.Black, Offset(g.alt.left - 4.dp.toPx(), g.mid - 16.dp.toPx()), Size(g.alt.width + 8.dp.toPx(), 32.dp.toPx()))
        drawRect(Color.White, Offset(g.alt.left - 4.dp.toPx(), g.mid - 16.dp.toPx()), Size(g.alt.width + 8.dp.toPx(), 32.dp.toPx()), style = Stroke(1.dp.toPx()))
        text(if (frame.altitudeM == null) "---" else fmt(alt, 0), g.alt.center.x, g.mid, mono, 16.dp.toPx(), Color.White)

        val hdg = frame.headingDeg ?: 0f
        val ppd = g.hw / 80
        clipRect(g.heading.left, g.heading.top, g.heading.right, g.heading.bottom) {
            var d = (floor((hdg - 45) / 5) * 5).toInt()
            while (d <= hdg + 45) {
                val x = g.w / 2 + (d - hdg) * ppd
                val dd = ((d % 360) + 360) % 360
                drawLine(ink, Offset(x, g.heading.top), Offset(x, g.heading.top + (if (dd % 10 == 0) 9 else 5).dp.toPx()), strokeWidth = 1.5f.dp.toPx())
                if (dd % 30 == 0) {
                    val t = when (dd) { 0 -> "N"; 90 -> "E"; 180 -> "S"; 270 -> "W"; else -> (dd / 10).toString() }
                    text(t, x, g.hy, mono, 12.dp.toPx(), ink)
                }
                d += 5
            }
        }

        val ix = if (g.land) g.w * .34f else g.tape.right + 22.dp.toPx()
        val iy = if (g.land) g.h * .2f else g.mid - 70.dp.toPx()
        val lines = listOf(
            "AVG ${fmt(frame.stats.avgOverallKmh, 0).padStart(3, '0')}" to green,
            "MAX ${fmt(frame.stats.maxKmh, 0).padStart(3, '0')}" to green,
            (if (step) "STP ${frame.stats.steps}" else "TRP ${fmtKm(frame.stats.distanceM)}") to ink,
            "TIM ${fmtDuration(frame.stats.elapsedS)}" to ink,
        )
        lines.forEachIndexed { i, (s, col) -> text(s, ix, iy + i * 24.dp.toPx(), mono, 15.dp.toPx(), col, Align.LEFT) }
        val alerts = buildList {
            if (frame.gps == GpsDot.NONE) add("GPS LOST")
            frame.target?.let { add(if (it.arrived) "TGT REACHED" else "TGT ${fmtKm(it.remainingM)}") }
        }
        alerts.forEachIndexed { i, s -> text(s, ix, iy + (lines.size + i) * 24.dp.toPx(), mono, 15.dp.toPx(), magenta, Align.LEFT) }
    }

    /** Aircraft-style readout: tens and hundreds are fixed, the units digit rolls continuously. */
    private fun DrawScope.rollingDigits(v: Float, x: Float, y: Float, w: Float, h: Float, paint: android.graphics.Paint, sizePx: Float) {
        val value = maxOf(0f, v)
        val whole = floor(value).toInt()
        val frac = value - whole
        val units = whole % 10
        val rest = whole / 10
        if (rest > 0) text(rest.toString(), x + w * .62f, y, paint, sizePx, Color.White, Align.RIGHT)
        clipRect(x + w * .62f, y - h * .64f, x + w * .98f, y + h * .64f) { // stays inside the pointer box
            for (k in -1..1) text(((units + k + 10) % 10).toString(), x + w * .64f, y + (frac - k) * h * 1.15f, paint, sizePx, Color.White, Align.LEFT)
        }
    }
}
