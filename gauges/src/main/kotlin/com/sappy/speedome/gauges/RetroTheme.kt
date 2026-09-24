package com.sappy.speedome.gauges

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.math.max

/** Classic 1960s–70s car gauge: chrome bezel, domed glass, rolling odometer drum (docs/plan.md §9). */
object RetroTheme : GaugeTheme {
    override val id = "retro"
    override val title = "RETRO"
    override val spring = NeedleSpring(omega = 6.5, zeta = .5)

    private fun ink(cream: Boolean) = if (cream) Color(0xFF1D1812) else Color(0xFFF1E6C9)

    override fun DrawScope.drawStatic(frame: GaugeFrame, assets: GaugeAssets) {
        val l = layoutFor(size)
        val c = l.center
        val r = l.radius
        val cream = frame.options.retroCream
        drawRect(Brush.radialGradient(listOf(Color(0xFF1A1714), Color(0xFF050404)), c, max(size.width, size.height)))
        drawCircle(
            Brush.linearGradient(
                0f to Color(0xFFF6F6F3), .32f to Color(0xFF8B8D8E), .55f to Color(0xFFECECE8), .8f to Color(0xFF55585A), 1f to Color(0xFFD4D5D3),
                start = Offset(c.x - r, c.y - r), end = Offset(c.x + r, c.y + r),
            ),
            r * 1.1f, c,
        )
        drawCircle(Color(0xFF171613), r * 1.035f, c)
        val faceA = if (cream) Color(0xFFF3EAD2) else Color(0xFF24221E)
        val faceB = if (cream) Color(0xFFD2C29C) else Color(0xFF0C0B0A)
        drawCircle(Brush.radialGradient(listOf(faceA, faceB), Offset(c.x, c.y - r * .3f), r * 1.05f), r, c)
        // Warm incandescent backlight bleeding through the black face around the numerals.
        if (!cream) backlightGlow(c, r * .98f, Color(0xFFFFB060), .07f, frame.options)

        val ink = ink(cream)
        val labelPaint = assets.paint(assets.oswald, 500)
        for (set in frame.scaleSets()) {
            set.forEachTick(frame.rangeKmh) { v, major ->
                val a = angleRad(v, frame.rangeKmh)
                drawLine(
                    ink.copy(alpha = set.alpha), polar(c, r * .93f, a), polar(c, if (major) r * .8f else r * .87f, a),
                    strokeWidth = if (major) r * .022f else r * .009f,
                )
                if (major) {
                    val p = polar(c, r * .66f, a)
                    text(fmt(v.toDouble(), 0), p.x, p.y, labelPaint, r * .135f, ink.copy(alpha = set.alpha))
                }
            }
        }
        text(frame.options.units.label, c.x, c.y - r * .3f, assets.paint(assets.oswald, 400), r * .075f, ink.copy(alpha = .7f))
        val brand = assets.paint(assets.oswald, 500).apply { textSkewX = -.22f }
        text("SPEEDO·ME", c.x, c.y + r * .66f, brand, r * .06f, ink.copy(alpha = .55f), spacingEm = .3f)
        brand.textSkewX = 0f
        val (drumTop, cell) = drumGeometry(c, r)
        drawRect(Color.Black, Offset(drumTop.x - 3, drumTop.y - 3), Size(cell.width * 6 + 6, cell.height + 6))
    }

    override fun DrawScope.drawDynamic(frame: GaugeFrame, assets: GaugeAssets) {
        val l = layoutFor(size)
        val c = l.center
        val r = l.radius
        val (drumTop, cell) = drumGeometry(c, r)
        drum(drumTop, cell, frame.stats.distanceM / frame.options.units.metresPerDistance, 6, assets, Color(0xFFEFE8D6), Color(0xFF141414), Color(0xFF141414), Color(0xFFE9E1CC))

        val a = angleRad(frame.needleKmh, frame.rangeKmh)
        needle(Offset(c.x, c.y + r * .02f), r * .9f, a, Color.Black.copy(alpha = .45f), tail = .2f, width = .04f)
        needle(c, r * .9f, a, Color(0xFFFF6A18), tail = .2f, width = .032f)
        val cap = assets.memo("retro-cap", size) {
            Brush.radialGradient(listOf(Color(0xFF5A5A58), Color(0xFF0D0D0C)), Offset(c.x - r * .03f, c.y - r * .03f), r * .1f)
        }
        drawCircle(cap, r * .09f, c)
        glassReflection(c, r, frame.options)
        stats(frame, l, assets)
    }

    private fun drumGeometry(c: Offset, r: Float): Pair<Offset, Size> {
        val cell = Size(r * .1f, r * .14f)
        return Offset(c.x - cell.width * 3, c.y + r * .19f) to cell
    }

    private fun DrawScope.stats(frame: GaugeFrame, l: GaugeLayout, assets: GaugeAssets) {
        val pairs = frame.statPairs()
        val n = pairs.size
        val s = l.stats
        val value = assets.paint(assets.oswald, 500)
        val label = assets.paint(assets.oswald, 400)
        val col = Color(0xFFE9DFC4)
        pairs.forEachIndexed { i, (k, v) ->
            val x = if (l.landscape) s.center.x else s.left + s.width * (i + .5f) / n
            val y = if (l.landscape) s.top + s.height * (i + .5f) / (n + 1) else s.top + minOf(s.height * .3f, 34.dp.toPx())
            val sz = if (l.landscape) minOf(s.height / (n + 1) * .5f, 44.dp.toPx()) else minOf(s.width / n * .34f, 30.dp.toPx())
            text(v, x, y, value, sz, col)
            text(k, x, y + sz * .85f, label, sz * .38f, col.copy(alpha = .55f), spacingEm = .12f)
        }
    }
}
