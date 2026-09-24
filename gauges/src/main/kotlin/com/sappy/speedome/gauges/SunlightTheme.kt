package com.sappy.speedome.gauges

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

/** Black on white, huge digits, maximum contrast: readable on a dashboard in direct sun. */
object SunlightTheme : GaugeTheme {
    override val id = "sun"
    override val title = "SUNLIGHT"
    override val spring = NeedleSpring(omega = 16.0, zeta = 1.0)
    override val light = true

    private class Geo(size: Size, density: Float) {
        val w = size.width
        val h = size.height
        val land = w > h * 1.15f
        val digit = if (land) h * .56f else minOf(w * .5f, h * .3f)
        val numberX = if (land) w * .36f else w / 2
        val numberY = if (land) h * .36f else h * .24f
        val barX = if (land) w * .06f else w * .07f
        val barW = if (land) w * .6f else w * .86f
        val barY = if (land) h * .78f else h * .46f
        val barH = 20 * density
        val statX = if (land) w * .7f else w * .07f
        val statY = if (land) h * .14f else h * .58f
        val colW = if (land) w * .26f else w * .43f
        val rowH = if (land) h * .2f else minOf(h * .1f, 80 * density)
    }

    private fun DrawScope.geo(assets: GaugeAssets) = assets.memo("sun-geo", size) { Geo(size, density) }

    override fun DrawScope.drawStatic(frame: GaugeFrame, assets: GaugeAssets) {
        val g = geo(assets)
        drawRect(Color.White)
        text(frame.options.units.label.uppercase(), g.numberX, g.numberY + g.digit * .5f, assets.paint(assets.outfit, 800), g.digit * .1f, Color.Black, spacingEm = .2f)
        drawRect(Color(0xFFE4E4E4), Offset(g.barX, g.barY), Size(g.barW, g.barH))
        val labels = assets.paint(assets.outfit, 700)
        for (set in frame.scaleSets()) for (q in 0..4) {
            val v = set.max * q / 4
            if (v > frame.rangeKmh * 1.001f) break
            text(fmt(v.toDouble(), 0), g.barX + g.barW * v / frame.rangeKmh, g.barY + g.barH + 14.dp.toPx(), labels, 13.dp.toPx(), Color.Black.copy(alpha = set.alpha))
        }
    }

    override fun DrawScope.drawDynamic(frame: GaugeFrame, assets: GaugeAssets) {
        val g = geo(assets)
        text(frame.readout.toString(), g.numberX, g.numberY, assets.paint(assets.outfit, 800), g.digit, Color.Black)
        drawRect(Color.Black, Offset(g.barX, g.barY), Size(g.barW * (frame.needleKmh / frame.rangeKmh).coerceIn(0f, 1f), g.barH))
        val value = assets.paint(assets.outfit, 800)
        val label = assets.paint(assets.outfit, 700)
        val valueSize = minOf(40.dp.toPx(), g.rowH * .55f)
        frame.statPairs().forEachIndexed { i, (k, v) ->
            val x = g.statX + if (g.land) 0f else (i % 2) * g.colW
            val y = g.statY + (if (g.land) i else i / 2) * g.rowH
            text(v, x, y, value, valueSize, Color.Black, Align.LEFT, top = true)
            text(k, x, y + valueSize + 4.dp.toPx(), label, 12.dp.toPx(), Color(0xFF333333), Align.LEFT, spacingEm = .15f, top = true)
        }
    }
}
