package com.sappy.speedome.gauges

import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** 1980s neon: a grid that rushes toward the horizon at your real speed, under a striped sun. */
object SynthwaveTheme : GaugeTheme {
    override val id = "synth"
    override val title = "SYNTHWAVE"
    override val spring = NeedleSpring(omega = 10.0, zeta = .75)

    private val pink = Color(0xFFFF3CAC)
    private val cyan = Color(0xFF2DE2E6)
    private val stars = Random(42).let { r -> List(70) { Triple(r.nextFloat(), r.nextFloat() * .5f, r.nextFloat() * 6.28f) } }

    private fun horizon(size: Size) = size.height * if (size.width > size.height * 1.15f) .56f else .5f

    private fun skyBrush(hz: Float) = Brush.verticalGradient(0f to Color(0xFF0D0221), .6f to Color(0xFF2A0845), 1f to Color(0xFF8A1A6B), startY = 0f, endY = hz)

    override fun DrawScope.drawStatic(frame: GaugeFrame, assets: GaugeAssets) {
        val w = size.width
        val h = size.height
        val land = w > h * 1.15f
        val hz = horizon(size)
        val sky = skyBrush(hz)
        drawRect(sky, size = Size(w, hz))
        val sr = min(w, h) * if (land) .3f else .27f
        val sun = Offset(w / 2, hz - sr * .15f)
        clipRect(0f, 0f, w, hz) {
            backlightGlow(sun, sr * 1.7f, Color(0xFFFF5C9A), .42f, frame.options)
            drawCircle(Brush.verticalGradient(0f to Color(0xFFFFE66D), .55f to Color(0xFFFF8A5C), 1f to Color(0xFFFF2E88), startY = sun.y - sr, endY = sun.y + sr), sr, sun)
            for (i in 0 until 7) {
                val y = sun.y + sr * (.08f + i * .13f)
                drawRect(sky, Offset(sun.x - sr, y), Size(sr * 2, (2 + i * 1.6f).dp.toPx() / 2))
            }
            val ridge = Path().apply {
                moveTo(0f, hz)
                for (i in 0..24) {
                    val m = abs(sin(i * 1.7f) * .6f + sin(i * .6f) * .4f)
                    lineTo(i / 24f * w, hz - m * hz * .16f - (i % 2) * 6.dp.toPx() / 2)
                }
                lineTo(w, hz); close()
            }
            drawPath(ridge, Color(0xFF16052B))
            drawPath(ridge, Color(0xFFB44DFF), style = Stroke(1.2f.dp.toPx()))
        }
        drawRect(Color(0xFF0A0012), Offset(0f, hz), Size(w, h - hz))
        for (i in -14..14) {
            val top = Offset(w / 2 + i * w * .02f, hz)
            val bottom = Offset(w / 2 + i * w * .22f, h)
            drawLine(cyan.copy(alpha = .25f), top, bottom, strokeWidth = 4.dp.toPx())
            drawLine(cyan, top, bottom, strokeWidth = 1.5f.dp.toPx())
        }
        val (size2, ny) = digitsGeometry(w, h, land, hz, sr)
        val bw = w * .7f
        drawRect(cyan.copy(alpha = .15f), Offset((w - bw) / 2, ny + size2 * .7f), Size(bw, 4.dp.toPx()))
    }

    private fun digitsGeometry(w: Float, h: Float, land: Boolean, hz: Float, sr: Float): Pair<Float, Float> {
        val s = if (land) h * .34f else w * .34f
        return s to (if (land) hz - h * .2f else hz - sr * .55f)
    }

    override fun DrawScope.drawDynamic(frame: GaugeFrame, assets: GaugeAssets) {
        val w = size.width
        val h = size.height
        val land = w > h * 1.15f
        val hz = horizon(size)
        for ((x, y, p) in stars) {
            val a = .25f + .35f * sin(frame.timeS.toFloat() * 1.3f + p).let { it * it }
            drawRect(Color.White.copy(alpha = a), Offset(x * w, y * hz), Size(1.5f.dp.toPx(), 1.5f.dp.toPx()))
        }
        // The horizontal grid lines scroll toward you at the displayed speed.
        val offset = (frame.scrollM * .08f) % 1f
        val k = h - hz
        for (i in 0 until 26) {
            val d = 1 + i - offset
            val y = hz + k / d
            if (y > h) continue
            val a = (1 - i / 26f).coerceIn(.15f, 1f)
            drawLine(pink.copy(alpha = .3f * a), Offset(0f, y), Offset(w, y), strokeWidth = 4.dp.toPx())
            drawLine(pink.copy(alpha = a), Offset(0f, y), Offset(w, y), strokeWidth = 1.5f.dp.toPx())
        }
        val sr = min(w, h) * if (land) .3f else .27f
        val (sz, ny) = digitsGeometry(w, h, land, hz, sr)
        chromeText(frame.readout.toString(), w / 2, ny, sz, assets)
        text(frame.options.units.label.uppercase(), w / 2, ny + sz * .52f, assets.paint(assets.exoItalic, 800), sz * .13f, cyan, spacingEm = .3f)
        val bw = w * .7f
        val frac = (frame.needleKmh / frame.rangeKmh).coerceIn(0f, 1f)
        drawRect(cyan.copy(alpha = .35f), Offset((w - bw) / 2, ny + sz * .7f - 3.dp.toPx()), Size(bw * frac, 10.dp.toPx()))
        drawRect(cyan, Offset((w - bw) / 2, ny + sz * .7f), Size(bw * frac, 4.dp.toPx()))
        val info = "AVG ${fmt(frame.stats.avgOverallKmh, 0)}   MAX ${fmt(frame.stats.maxKmh, 0)}   " +
            if (frame.stats.stepMode) "${frame.stats.steps} STEPS" else "${frame.fmtDist(frame.stats.distanceM)} ${frame.options.units.distanceLabel.uppercase()}"
        text(info, w / 2, h * if (land) .93f else .9f, assets.paint(assets.exoItalic, 800), 14.dp.toPx(), cyan, spacingEm = .12f)
    }

    /** Chrome-gradient italic digits with a pink neon outline. */
    private fun DrawScope.chromeText(s: String, x: Float, y: Float, sizePx: Float, assets: GaugeAssets) {
        val fill = assets.paint(assets.exoItalic, 900)
        fill.textSize = sizePx
        fill.textAlign = Paint.Align.CENTER
        fill.letterSpacing = 0f
        val fm = fill.fontMetrics
        val base = y - (fm.ascent + fm.descent) / 2
        val stroke = Paint(fill).apply { style = Paint.Style.STROKE; shader = null }
        val canvas = drawContext.canvas.nativeCanvas
        stroke.color = pink.copy(alpha = .35f).toArgb()
        stroke.strokeWidth = 9.dp.toPx()
        canvas.drawText(s, x, base, stroke)
        stroke.color = pink.toArgb()
        stroke.strokeWidth = 3.dp.toPx()
        canvas.drawText(s, x, base, stroke)
        fill.shader = LinearGradient(
            0f, y - sizePx / 2, 0f, y + sizePx / 2,
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFBFE6FF.toInt(), 0xFF3A2A86.toInt(), 0xFFFF9DE6.toInt(), 0xFFFFFFFF.toInt()),
            floatArrayOf(0f, .44f, .5f, .78f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawText(s, x, base, fill)
        fill.shader = null
    }
}
