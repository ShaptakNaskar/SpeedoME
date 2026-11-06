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
import androidx.compose.ui.graphics.drawscope.clipRect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Original expedition instruments. Decoration stays in the cached pass; only speed and stats move.

private fun DrawScope.journeyScale(frame: GaugeFrame, l: GaugeLayout, assets: GaugeAssets, ink: Color) {
    val c = l.center
    val r = l.radius
    for (set in frame.scaleSets()) {
        set.forEachTick(frame.rangeKmh) { v, major ->
            val a = angleRad(v, frame.rangeKmh)
            val color = (if (frame.inRedZone(v)) LIMIT_RED else ink).copy(alpha = set.alpha)
            drawLine(color, polar(c, r * .87f, a), polar(c, r * (if (major) .77f else .82f), a), r * .012f)
            if (major && (v / niceStep(set.max).label).toInt() % 2 == 0) {
                val p = polar(c, r * .66f, a)
                text(fmt(v.toDouble(), 0), p.x, p.y, assets.paint(assets.courier), r * .105f, color)
            }
        }
    }
    limitArc(frame, c, r * .93f, r * .035f, LIMIT_RED)
}

private fun DrawScope.journeyReadout(
    frame: GaugeFrame, l: GaugeLayout, assets: GaugeAssets,
    ink: Color, muted: Color, face: GaugeTypeface, name: String,
) {
    val c = l.center
    val r = l.radius
    val digits = frame.readout.toString()
    text(digits, c.x, c.y + r * .09f, assets.paint(face, 600), minOf(r * .53f, r * 1.45f / (digits.length * .65f)), frame.warned(ink))
    text(frame.options.units.label.uppercase(), c.x, c.y + r * .40f, assets.paint(assets.courierBold), r * .12f, muted, spacingEm = .12f)
    text(name, c.x, c.y + r * .72f, assets.paint(assets.cinzel, 600), r * .095f, muted, spacingEm = .08f)
    cardStats(frame, l, assets.paint(assets.courierBold), assets.paint(assets.courier), ink, muted, scale = .9f)
}

private fun DrawScope.journeyMarker(frame: GaugeFrame, l: GaugeLayout, color: Color) {
    val a = angleRad(frame.needleKmh, frame.rangeKmh)
    val r = l.radius
    drawPath(Path().apply {
        val tip = polar(l.center, r * .76f, a)
        val left = polar(l.center, r * .99f, a - .065f)
        val right = polar(l.center, r * .99f, a + .065f)
        moveTo(tip.x, tip.y); lineTo(left.x, left.y); lineTo(right.x, right.y); close()
    }, frame.warned(color))
}

/** A linen travel journal: saddle stitches, a folded corner and a vermilion route ribbon. */
object WayfarerTheme : GaugeTheme {
    override val id = "world-wayfarer"
    override val title = "WAYFARER"
    override val spring = NeedleSpring(omega = 10.0, zeta = .9)
    override val light = true
    private val paper = Color(0xFFF0E5C8)
    private val ink = Color(0xFF403C2D)
    private val red = Color(0xFF9F3435)

    override fun DrawScope.drawStatic(frame: GaugeFrame, assets: GaugeAssets) {
        val l = cardLayout(size)
        val r = l.radius
        drawRoundRect(paper, cornerRadius = CornerRadius(r * .09f))
        for (i in 0..40) {
            val x = size.width * (i + .5f) / 41
            drawLine(ink.copy(alpha = .035f), Offset(x, 0f), Offset(x, size.height), r * .012f)
        }
        for (i in 0..28) {
            val x = r * .1f + (size.width - r * .2f) * i / 29
            for (y in listOf(r * .10f, size.height - r * .10f)) {
                drawLine(red.copy(alpha = .5f), Offset(x, y), Offset(x + r * .055f, y), r * .012f)
            }
        }
        val fold = r * .24f
        drawPath(Path().apply {
            moveTo(size.width - fold, 0f); lineTo(size.width - fold, fold); lineTo(size.width, fold); close()
        }, Color(0xFFD2C3A0))
        drawCircle(ink.copy(alpha = .22f), r * .98f, l.center, style = Stroke(r * .016f))
        drawCircle(red.copy(alpha = .13f), r * .53f, l.center, style = Stroke(r * .012f))
        journeyScale(frame, l, assets, ink)
    }

    override fun DrawScope.drawDynamic(frame: GaugeFrame, assets: GaugeAssets) {
        val l = cardLayout(size)
        scaleArc(l.center, l.radius * .93f, l.radius * .045f, 0f, frame.needleKmh, frame.rangeKmh, frame.warned(red))
        journeyMarker(frame, l, red)
        journeyReadout(frame, l, assets, ink, red, assets.cinzel, "WAYFARER")
    }
}

/** A surveyor's olive field instrument: cyan sight lines, brass fasteners, a vernier scale. */
object ExplorerTheme : GaugeTheme {
    override val id = "world-explorer"
    override val title = "EXPLORER"
    override val spring = NeedleSpring(omega = 14.0, zeta = 1.0)
    private val night = Color(0xFF252C23)
    private val brass = Color(0xFFE7CD80)
    private val cyan = Color(0xFF81D9FF)
    private val white = Color(0xFFF2F0D9)

    override fun DrawScope.drawStatic(frame: GaugeFrame, assets: GaugeAssets) {
        val l = cardLayout(size)
        val c = l.center
        val r = l.radius
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF424733), night)), cornerRadius = CornerRadius(r * .08f))
        for (x in listOf(r * .12f, size.width - r * .12f)) {
            for (y in listOf(r * .12f, size.height - r * .12f)) {
                drawCircle(brass, r * .035f, Offset(x, y))
                drawLine(night, Offset(x - r * .025f, y), Offset(x + r * .025f, y), r * .012f)
            }
        }
        drawCircle(brass.copy(alpha = .6f), r * 1.01f, c, style = Stroke(r * .035f))
        drawCircle(cyan.copy(alpha = .25f), r * .53f, c, style = Stroke(r * .008f))
        drawLine(cyan, Offset(c.x, c.y - r * .44f), Offset(c.x, c.y - r * .56f), r * .014f)
        journeyScale(frame, l, assets, brass)
    }

    override fun DrawScope.drawDynamic(frame: GaugeFrame, assets: GaugeAssets) {
        val l = cardLayout(size)
        val a = angleRad(frame.needleKmh, frame.rangeKmh)
        val p = polar(l.center, l.radius * .94f, a)
        drawCircle(cyan.copy(alpha = .15f), l.radius * .095f, p)
        drawCircle(frame.warned(cyan), l.radius * .038f, p)
        drawLine(frame.warned(cyan), polar(l.center, l.radius * .77f, a), p, l.radius * .018f)
        journeyReadout(frame, l, assets, white, brass, assets.b612, "EXPLORER")
    }
}

/** A slate road marker: a faceted ice rim, snow ridges and a luminous moving chevron. */
object NorthernRoadTheme : GaugeTheme {
    override val id = "world-northern_road"
    override val title = "NORTHERN ROAD"
    override val spring = NeedleSpring(omega = 11.0, zeta = 1.0)
    private val ice = Color(0xFFDCE9EE)
    private val blue = Color(0xFF99CFDD)
    private val slate = Color(0xFF293E49)

    override fun DrawScope.drawStatic(frame: GaugeFrame, assets: GaugeAssets) {
        val l = cardLayout(size)
        val c = l.center
        val r = l.radius
        drawRoundRect(Brush.verticalGradient(listOf(slate, Color(0xFF172930))), cornerRadius = CornerRadius(r * .12f))
        val rim = Path().apply {
            for (i in 0..8) {
                val p = polar(c, r * 1.02f, ((i * 45 - 112.5) * PI / 180).toFloat())
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
            close()
        }
        drawPath(rim, ice.copy(alpha = .4f), style = Stroke(r * .018f))
        for (i in 0..5) {
            val x = size.width * i / 5
            val y = size.height - r * (.12f + (i % 3) * .08f)
            drawLine(blue.copy(alpha = .15f), Offset(x, size.height), Offset(x + r * .25f, y), r * .02f)
            drawLine(ice.copy(alpha = .15f), Offset(x + r * .25f, y), Offset(x + r * .5f, size.height), r * .02f)
        }
        journeyScale(frame, l, assets, ice)
        drawLine(ice.copy(alpha = .25f), Offset(c.x - r * .40f, c.y + r * .52f), Offset(c.x + r * .40f, c.y + r * .52f), r * .012f)
    }

    override fun DrawScope.drawDynamic(frame: GaugeFrame, assets: GaugeAssets) {
        val l = cardLayout(size)
        val a = angleRad(frame.needleKmh, frame.rangeKmh)
        val r = l.radius
        val p = polar(l.center, r * .93f, a)
        drawLine(frame.warned(blue), polar(l.center, r * .83f, a - .07f), p, r * .035f, cap = StrokeCap.Round)
        drawLine(frame.warned(blue), p, polar(l.center, r * .83f, a + .07f), r * .035f, cap = StrokeCap.Round)
        journeyReadout(frame, l, assets, ice, blue, assets.oswald, "NORTHERN ROAD")
    }
}

/** A jeweller's woven dial: two interlaced gold strands, seed pearls and an enamel centre. */
object GoldenThreadTheme : GaugeTheme {
    override val id = "world-golden_thread"
    override val title = "GOLDEN THREAD"
    override val spring = NeedleSpring(omega = 9.0, zeta = .85)
    private val gold = Color(0xFFEAC968)
    private val ivory = Color(0xFFFFEBC3)
    private val enamel = Color(0xFF342E29)

    override fun DrawScope.drawStatic(frame: GaugeFrame, assets: GaugeAssets) {
        val l = cardLayout(size)
        val c = l.center
        val r = l.radius
        drawRoundRect(enamel, cornerRadius = CornerRadius(r * .18f))
        drawRoundRect(gold.copy(alpha = .45f), Offset(r * .07f, r * .07f), Size(size.width - r * .14f, size.height - r * .14f), CornerRadius(r * .13f), style = Stroke(r * .012f))
        for (strand in 0..1) {
            val braid = Path().apply {
                for (i in 0..180) {
                    val a = (2 * PI * i / 180).toFloat()
                    val rr = r * (.97f + .035f * sin(a * 18 + strand * PI.toFloat()))
                    val p = polar(c, rr, a)
                    if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                }
            }
            drawPath(braid, if (strand == 0) gold else ivory.copy(alpha = .65f), style = Stroke(r * .012f))
        }
        drawCircle(Color(0xFF453D30), r * .53f, c)
        drawCircle(gold.copy(alpha = .4f), r * .53f, c, style = Stroke(r * .009f))
        for (i in 0..11) drawCircle(ivory.copy(alpha = .65f), r * .012f, polar(c, r * 1.04f, (i * PI / 6).toFloat()))
        journeyScale(frame, l, assets, gold)
    }

    override fun DrawScope.drawDynamic(frame: GaugeFrame, assets: GaugeAssets) {
        val l = cardLayout(size)
        scaleArc(l.center, l.radius * .91f, l.radius * .018f, 0f, frame.needleKmh, frame.rangeKmh, frame.warned(ivory))
        val p = polar(l.center, l.radius * .94f, angleRad(frame.needleKmh, frame.rangeKmh))
        val d = l.radius * .055f
        drawPath(Path().apply {
            moveTo(p.x, p.y - d); lineTo(p.x + d, p.y); lineTo(p.x, p.y + d); lineTo(p.x - d, p.y); close()
        }, frame.warned(gold))
        journeyReadout(frame, l, assets, ivory, gold, assets.cinzel, "GOLDEN THREAD")
    }
}

/** A terracotta sundial: scalloped solar rays above contour-cut dunes, with a dark gnomon. */
object DesertSandsTheme : GaugeTheme {
    override val id = "world-desert_sands"
    override val title = "DESERT SANDS"
    override val spring = NeedleSpring(omega = 10.0, zeta = 1.0)
    override val light = true
    private val sand = Color(0xFFF0DDB3)
    private val clay = Color(0xFF964B2E)
    private val ink = Color(0xFF51371F)

    override fun DrawScope.drawStatic(frame: GaugeFrame, assets: GaugeAssets) {
        val l = cardLayout(size)
        val c = l.center
        val r = l.radius
        drawRoundRect(sand, cornerRadius = CornerRadius(r * .08f))
        clipRect(right = if (l.landscape) l.stats.left - r * .1f else size.width, bottom = if (l.landscape) size.height else l.stats.top - r * .08f) {
            for (layer in 0..3) {
                val dune = Path().apply {
                    for (i in 0..60) {
                        val x = size.width * i / 60
                        val y = c.y + r * (1.04f - layer * .045f) + r * .015f * sin(i * .16f + layer)
                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                    }
                }
                drawPath(dune, clay.copy(alpha = .16f), style = Stroke(r * .012f))
            }
        }
        for (i in 0..24) {
            val a = ((DIAL_START_DEG + DIAL_SWEEP_DEG * i / 24) * PI / 180).toFloat()
            drawLine(clay.copy(alpha = .65f), polar(c, r * .94f, a), polar(c, r * (if (i % 2 == 0) 1.03f else .98f), a), r * .022f)
        }
        drawCircle(clay.copy(alpha = .07f), r * .54f, c)
        journeyScale(frame, l, assets, ink)
    }

    override fun DrawScope.drawDynamic(frame: GaugeFrame, assets: GaugeAssets) {
        val l = cardLayout(size)
        journeyMarker(frame, l, clay)
        val a = angleRad(frame.needleKmh, frame.rangeKmh)
        drawLine(ink.copy(alpha = .2f), polar(l.center, l.radius * .55f, a + .07f), polar(l.center, l.radius * .85f, a + .07f), l.radius * .055f)
        journeyReadout(frame, l, assets, ink, clay, assets.cinzel, "DESERT SANDS")
    }
}

/** A forest trail marker: irregular growth rings, a split bark edge and firefly-green sap. */
object WildsTheme : GaugeTheme {
    override val id = "world-wilds"
    override val title = "WILDS"
    override val spring = NeedleSpring(omega = 12.0, zeta = .9)
    private val moss = Color(0xFF202F28)
    private val cream = Color(0xFFF0DBAC)
    private val sap = Color(0xFFBADA8A)

    override fun DrawScope.drawStatic(frame: GaugeFrame, assets: GaugeAssets) {
        val l = cardLayout(size)
        val c = l.center
        val r = l.radius
        drawRoundRect(moss, cornerRadius = CornerRadius(r * .20f))
        for (ring in 0..4) {
            val path = Path().apply {
                for (i in 0..120) {
                    val a = (i * PI / 60).toFloat()
                    val rr = r * (.56f + ring * .11f + .012f * sin(7 * a + ring) + .009f * cos(11 * a))
                    val p = polar(c, rr, a)
                    if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                }
                close()
            }
            drawPath(path, cream.copy(alpha = if (ring == 4) .5f else .12f), style = Stroke(r * .012f))
        }
        for (i in 0..4) {
            val y = size.height * (.15f + i * .17f)
            drawLine(cream.copy(alpha = .23f), Offset(size.width - r * .09f, y), Offset(size.width - r * .2f, y + r * .09f), r * .025f)
        }
        journeyScale(frame, l, assets, cream)
    }

    override fun DrawScope.drawDynamic(frame: GaugeFrame, assets: GaugeAssets) {
        val l = cardLayout(size)
        val a = angleRad(frame.needleKmh, frame.rangeKmh)
        val p = polar(l.center, l.radius * .93f, a)
        drawCircle(frame.warned(sap).copy(alpha = .15f), l.radius * .10f, p)
        drawCircle(frame.warned(sap), l.radius * .035f, p)
        scaleArc(l.center, l.radius * .91f, l.radius * .025f, 0f, frame.needleKmh, frame.rangeKmh, frame.warned(sap))
        journeyReadout(frame, l, assets, cream, sap, assets.courierBold, "WILDS")
    }
}

/** A highland walking-club crest: a peaked shield, brass contours and a summit pennant. */
object HighlandsTheme : GaugeTheme {
    override val id = "world-highlands"
    override val title = "HIGHLANDS"
    override val spring = NeedleSpring(omega = 11.0, zeta = .95)
    private val peat = Color(0xFF30372F)
    private val gold = Color(0xFFDABD69)
    private val cream = Color(0xFFF3E8C9)

    override fun DrawScope.drawStatic(frame: GaugeFrame, assets: GaugeAssets) {
        val l = cardLayout(size)
        val c = l.center
        val r = l.radius
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF4E5140), peat)), cornerRadius = CornerRadius(r * .07f))
        val crest = Path().apply {
            moveTo(c.x, c.y - r * 1.06f)
            lineTo(c.x + r * 1.01f, c.y - r * .62f)
            lineTo(c.x + r * .91f, c.y + r * .55f)
            lineTo(c.x, c.y + r * 1.06f)
            lineTo(c.x - r * .91f, c.y + r * .55f)
            lineTo(c.x - r * 1.01f, c.y - r * .62f)
            close()
        }
        drawPath(crest, gold.copy(alpha = .6f), style = Stroke(r * .025f))
        for (i in 0..2) {
            val y = c.y - r * (.35f - .025f * i)
            val hills = Path().apply {
                moveTo(c.x - r * .46f, y)
                lineTo(c.x - r * .19f, y - r * .12f)
                lineTo(c.x - r * .05f, y - r * .05f)
                lineTo(c.x + r * .13f, y - r * .18f)
                lineTo(c.x + r * .45f, y)
            }
            drawPath(hills, gold.copy(alpha = .16f), style = Stroke(r * .012f))
        }
        journeyScale(frame, l, assets, cream)
    }

    override fun DrawScope.drawDynamic(frame: GaugeFrame, assets: GaugeAssets) {
        val l = cardLayout(size)
        val a = angleRad(frame.needleKmh, frame.rangeKmh)
        val p = polar(l.center, l.radius * .94f, a)
        val d = l.radius * .10f
        val color = frame.warned(gold)
        drawLine(color, Offset(p.x, p.y - d), Offset(p.x, p.y + d * .55f), l.radius * .015f)
        drawPath(Path().apply {
            moveTo(p.x, p.y - d); lineTo(p.x + d * .8f, p.y - d * .55f); lineTo(p.x, p.y); close()
        }, color)
        journeyReadout(frame, l, assets, cream, gold, assets.cinzel, "HIGHLANDS")
    }
}
