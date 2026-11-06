package com.sappy.speedome.gauges

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import kotlin.math.PI
import kotlin.math.sin

// Original map instruments. All geometry follows the shared docked/tall card layout.
private fun GaugeFrame.worldFiveFraction() = (needleKmh / rangeKmh).coerceIn(0f, 1f)

private fun DrawScope.worldFiveReadout(
    frame: GaugeFrame, assets: GaugeAssets, l: GaugeLayout,
    face: GaugeTypeface, ink: Color, accent: Color, muted: Color,
) {
    val r = l.radius
    val c = l.center
    text(frame.readout.toString(), c.x, c.y, assets.paint(face), r * .57f, frame.warned(ink))
    text(frame.options.units.label.uppercase(), c.x, c.y + r * .36f,
        assets.paint(assets.courierBold), r * .12f, accent)
    cardStats(frame, l, assets.paint(assets.courierBold), assets.paint(assets.courier), ink, muted, scale = .88f)
}

private fun DrawScope.worldFiveTicks(frame: GaugeFrame, l: GaugeLayout, color: Color, assets: GaugeAssets) {
    val c = l.center
    val r = l.radius
    for (set in frame.scaleSets()) {
        set.forEachTick(frame.rangeKmh) { v, major ->
            val a = angleRad(v, frame.rangeKmh)
            val col = (if (frame.inRedZone(v)) LIMIT_RED else color).copy(alpha = set.alpha)
            drawLine(col, polar(c, r * if (major) .83f else .89f, a), polar(c, r * .95f, a), r * .014f)
            if (major) {
                val p = polar(c, r * .72f, a)
                text(fmt(v.toDouble(), 0), p.x, p.y, assets.paint(assets.courier), r * .105f, col)
            }
        }
    }
}

/** A municipal network console: routed traces, junctions and an interrupted square speed rail. */
object ConnectedCityTheme : GaugeTheme {
    override val id = "world-connected_city"
    override val title = "CONNECTED CITY"
    override val spring = NeedleSpring(omega = 17.0, zeta = 1.0)
    override val light = true
    private val paper = Color(0xFFE9EEE5)
    private val ink = Color(0xFF173440)
    private val blue = Color(0xFF236C91)
    private val orange = Color(0xFFB84B13)

    override fun DrawScope.drawStatic(frame: GaugeFrame, assets: GaugeAssets) {
        drawRect(paper)
        val l = cardLayout(size)
        val c = l.center
        val r = l.radius
        for (i in 0..5) {
            val x = c.x - r + i * r * .4f
            val y = c.y - r + i * r * .35f
            val path = Path().apply {
                moveTo(x, c.y - r)
                lineTo(x, y)
                lineTo(c.x + r, y)
            }
            drawPath(path, blue.copy(alpha = .12f), style = Stroke(r * .018f))
            drawCircle(blue.copy(alpha = .3f), r * .035f, Offset(x, y))
        }
        text("CITY / MOBILITY", c.x, c.y - r * .51f, assets.paint(assets.orbitron), r * .105f, blue)
        for (i in 0 until 20) {
            val x = c.x - r * .88f + i * r * .092f
            val red = frame.limitFraction()?.let { (i + .5f) / 20 >= it } == true
            drawRect((if (red) LIMIT_RED else blue).copy(alpha = .2f), Offset(x, c.y + r * .68f), Size(r * .065f, r * .17f))
        }
        drawLine(blue.copy(alpha = .35f), Offset(l.stats.left, l.stats.top), Offset(l.stats.right, l.stats.top), r * .015f)
    }

    override fun DrawScope.drawDynamic(frame: GaugeFrame, assets: GaugeAssets) {
        val l = cardLayout(size)
        val r = l.radius
        for (i in 0 until 20) {
            if ((i + .5f) / 20 > frame.worldFiveFraction()) break
            val red = frame.limitFraction()?.let { (i + .5f) / 20 >= it } == true
            drawRect(if (red) LIMIT_RED else frame.warned(orange),
                Offset(l.center.x - r * .88f + i * r * .092f, l.center.y + r * .68f), Size(r * .065f, r * .17f))
        }
        worldFiveReadout(frame, assets, l, assets.orbitron, ink, blue, blue)
    }
}

/** A cream enamel trail badge, contour lines and a mountain-cut outer rim. */
object BackcountryTheme : GaugeTheme {
    override val id = "world-backcountry"
    override val title = "BACKCOUNTRY"
    override val spring = NeedleSpring(omega = 10.0, zeta = .9)
    private val forest = Color(0xFF253A2E)
    private val cream = Color(0xFFF3E4B9)
    private val moss = Color(0xFFA8B58A)

    override fun DrawScope.drawStatic(frame: GaugeFrame, assets: GaugeAssets) {
        drawRect(forest)
        val l = cardLayout(size)
        val c = l.center
        val r = l.radius
        for (j in 0..5) {
            val p = Path().apply {
                for (i in 0..48) {
                    val x = size.width * i / 48
                    val y = size.height * (.12f + j * .15f) + r * .08f * sin(i * .36f + j)
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
            }
            clipRect(
                right = if (l.landscape) l.stats.left - r * .08f else size.width,
                bottom = if (l.landscape) size.height else l.stats.top - r * .08f,
            ) {
                drawPath(p, moss.copy(alpha = .1f), style = Stroke(r * .012f))
            }
        }
        val badge = Path().apply {
            moveTo(c.x - r, c.y + r * .68f)
            lineTo(c.x - r, c.y - r * .5f)
            lineTo(c.x - r * .4f, c.y - r)
            lineTo(c.x, c.y - r * .79f)
            lineTo(c.x + r * .4f, c.y - r)
            lineTo(c.x + r, c.y - r * .5f)
            lineTo(c.x + r, c.y + r * .68f)
            lineTo(c.x, c.y + r)
            close()
        }
        drawPath(badge, forest)
        drawPath(badge, cream, style = Stroke(r * .025f))
        text("TRAIL PACE", c.x, c.y - r * .53f, assets.paint(assets.cinzel), r * .14f, cream)
        limitArc(frame, c, r * .84f, r * .05f, LIMIT_RED)
    }

    override fun DrawScope.drawDynamic(frame: GaugeFrame, assets: GaugeAssets) {
        val l = cardLayout(size)
        val r = l.radius
        val p = polar(l.center, r * .84f, angleRad(frame.needleKmh, frame.rangeKmh))
        drawCircle(frame.warned(cream), r * .06f, p)
        worldFiveReadout(frame, assets, l, assets.oswald, cream, moss, moss)
    }
}

/** A folded delivery waybill: diagonal dispatch tab, barcode and parcel progress chevrons. */
object CourierTheme : GaugeTheme {
    override val id = "world-courier"
    override val title = "COURIER"
    override val spring = NeedleSpring(omega = 19.0, zeta = 1.0)
    private val navy = Color(0xFF142A36)
    private val mint = Color(0xFF9AE5DD)
    private val paper = Color(0xFFF1E9CC)

    override fun DrawScope.drawStatic(frame: GaugeFrame, assets: GaugeAssets) {
        drawRect(navy)
        val l = cardLayout(size)
        val c = l.center
        val r = l.radius
        drawRect(paper, Offset(c.x - r, c.y - r), Size(r * 2, r * 1.48f))
        drawPath(Path().apply {
            moveTo(c.x + r * .65f, c.y - r)
            lineTo(c.x + r, c.y - r * .65f)
            lineTo(c.x + r * .65f, c.y - r * .65f)
            close()
        }, mint)
        text("EXPRESS / PACE", c.x - r * .83f, c.y - r * .75f,
            assets.paint(assets.courierBold), r * .125f, navy, Align.LEFT)
        for (i in 0..30) {
            val x = c.x - r * .85f + i * r * .047f
            drawRect(navy, Offset(x, c.y - r * .57f), Size(r * if (i % 3 == 0) .025f else .012f, r * .13f))
        }
        for (i in 0 until 10) courierChevron(l, i, mint.copy(alpha = .16f))
        frame.limitFraction()?.let { f ->
            val x = c.x - r * .94f + r * 1.88f * f
            drawLine(LIMIT_RED, Offset(x, c.y + r * .58f), Offset(x, c.y + r * .96f), r * .035f)
        }
    }

    private fun DrawScope.courierChevron(l: GaugeLayout, i: Int, color: Color) {
        val r = l.radius
        val x = l.center.x - r * .94f + i * r * .188f
        val y = l.center.y + r * .76f
        drawPath(Path().apply {
            moveTo(x, y - r * .13f); lineTo(x + r * .09f, y)
            lineTo(x, y + r * .13f); lineTo(x + r * .065f, y + r * .13f)
            lineTo(x + r * .155f, y); lineTo(x + r * .065f, y - r * .13f); close()
        }, color)
    }

    override fun DrawScope.drawDynamic(frame: GaugeFrame, assets: GaugeAssets) {
        val l = cardLayout(size)
        val c = l.center
        val r = l.radius
        text(frame.readout.toString(), c.x, c.y - r * .1f, assets.paint(assets.barlowSemi), r * .62f, frame.warned(navy))
        text(frame.options.units.label.uppercase(), c.x, c.y + r * .29f, assets.paint(assets.courierBold), r * .13f, navy)
        for (i in 0 until 10) {
            if ((i + .5f) / 10 > frame.worldFiveFraction()) break
            courierChevron(l, i, if (frame.limitFraction()?.let { (i + .5f) / 10 >= it } == true) LIMIT_RED else frame.warned(mint))
        }
        cardStats(frame, l, assets.paint(assets.courierBold), assets.paint(assets.courier), paper, mint, scale = .88f)
    }
}

/** A blueprint survey reticle with a calibrated semicircular vernier and travelling index. */
object SurfaceSurveyTheme : GaugeTheme {
    override val id = "world-surface_survey"
    override val title = "SURFACE SURVEY"
    override val spring = NeedleSpring(omega = 14.0, zeta = 1.0)
    private val blue = Color(0xFF142A45)
    private val white = Color(0xFFEEF1E9)
    private val gold = Color(0xFFF3C56F)
    private val grid = Color(0xFF7196AF)

    override fun DrawScope.drawStatic(frame: GaugeFrame, assets: GaugeAssets) {
        drawRect(blue)
        val l = cardLayout(size)
        val c = l.center
        val r = l.radius
        for (i in 0..12) {
            val x = size.width * i / 12
            drawLine(grid.copy(alpha = .13f), Offset(x, 0f), Offset(x, size.height), r * .008f)
        }
        for (i in 0..8) {
            val y = size.height * i / 8
            drawLine(grid.copy(alpha = .13f), Offset(0f, y), Offset(size.width, y), r * .008f)
        }
        drawCircle(grid.copy(alpha = .4f), r, c, style = Stroke(r * .012f))
        worldFiveTicks(frame, l, white, assets)
        limitArc(frame, c, r, r * .04f, LIMIT_RED)
        text("SURFACE / V", c.x, c.y - r * .43f, assets.paint(assets.b612), r * .105f, gold)
        for (sign in listOf(-1, 1)) {
            drawLine(gold, Offset(c.x + sign * r * .58f, c.y - r * .51f), Offset(c.x + sign * r * .58f, c.y - r * .37f), r * .018f)
        }
    }

    override fun DrawScope.drawDynamic(frame: GaugeFrame, assets: GaugeAssets) {
        val l = cardLayout(size)
        val p = polar(l.center, l.radius, angleRad(frame.needleKmh, frame.rangeKmh))
        drawCircle(blue, l.radius * .065f, p)
        drawCircle(frame.warned(gold), l.radius * .065f, p, style = Stroke(l.radius * .025f))
        worldFiveReadout(frame, assets, l, assets.b612Bold, white, gold, white.copy(alpha = .8f))
    }
}

/** A gilded island medallion: scalloped bezel, engraved waves and a pearl on a tidal scale. */
object IslandsTheme : GaugeTheme {
    override val id = "world-islands"
    override val title = "ISLANDS"
    override val spring = NeedleSpring(omega = 9.0, zeta = .85)
    private val sea = Color(0xFF103341)
    private val gold = Color(0xFFF0CF6A)
    private val foam = Color(0xFFAAEEE2)

    override fun DrawScope.drawStatic(frame: GaugeFrame, assets: GaugeAssets) {
        drawRect(sea)
        val l = cardLayout(size)
        val c = l.center
        val r = l.radius
        for (j in 0..3) {
            val wave = Path().apply {
                for (i in 0..64) {
                    val x = size.width * i / 64
                    val y = size.height * (.72f + j * .065f) + r * .035f * sin(i * .5f + j)
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
            }
            clipRect(
                right = if (l.landscape) l.stats.left - r * .08f else size.width,
                bottom = if (l.landscape) size.height else l.stats.top - r * .08f,
            ) {
                drawPath(wave, foam.copy(alpha = .12f), style = Stroke(r * .012f))
            }
        }
        for (i in 0 until 36) {
            val a = (i * PI / 18).toFloat()
            drawCircle(gold.copy(alpha = .7f), r * .025f, polar(c, r * .98f, a))
        }
        drawCircle(gold, r * .91f, c, style = Stroke(r * .018f))
        drawCircle(gold.copy(alpha = .35f), r * .83f, c, style = Stroke(r * .008f))
        text("ISLAND PASSAGE", c.x, c.y - r * .5f, assets.paint(assets.cinzel), r * .12f, gold)
        drawArc(foam.copy(alpha = .2f), DIAL_START_DEG, DIAL_SWEEP_DEG, false,
            Offset(c.x - r * .76f, c.y - r * .76f), Size(r * 1.52f, r * 1.52f), style = Stroke(r * .035f))
        limitArc(frame, c, r * .76f, r * .045f, LIMIT_RED)
    }

    override fun DrawScope.drawDynamic(frame: GaugeFrame, assets: GaugeAssets) {
        val l = cardLayout(size)
        val r = l.radius
        val p = polar(l.center, r * .76f, angleRad(frame.needleKmh, frame.rangeKmh))
        drawCircle(sea, r * .09f, p)
        drawCircle(frame.warned(gold), r * .055f, p)
        worldFiveReadout(frame, assets, l, assets.cinzel, foam, gold, foam.copy(alpha = .75f))
    }
}

/** An industrial containment meter: hazard edge, orange bracketed readout and warning segments. */
object QuarantineTheme : GaugeTheme {
    override val id = "world-quarantine"
    override val title = "QUARANTINE"
    override val spring = NeedleSpring(omega = 16.0, zeta = 1.0)
    private val dark = Color(0xFF14282A)
    private val orange = Color(0xFFFFAE61)
    private val pale = Color(0xFFE6EBDA)
    private val muted = Color(0xFF9BB5AC)

    override fun DrawScope.drawStatic(frame: GaugeFrame, assets: GaugeAssets) {
        drawRect(dark)
        val l = cardLayout(size)
        val c = l.center
        val r = l.radius
        for (i in 0 until 28) {
            val x = size.width * i / 28
            drawPath(Path().apply {
                moveTo(x, 0f); lineTo(x + size.width / 56, 0f)
                lineTo(x + size.width / 112, r * .09f); lineTo(x, r * .09f); close()
            }, orange.copy(alpha = .65f))
        }
        text("CONTAINMENT", c.x, c.y - r * .72f, assets.paint(assets.oswald), r * .17f, orange, spacingEm = .12f)
        for (sign in listOf(-1, 1)) {
            val x = c.x + sign * r * .9f
            drawPath(Path().apply {
                moveTo(x - sign * r * .16f, c.y - r * .42f)
                lineTo(x, c.y - r * .42f); lineTo(x, c.y + r * .44f)
                lineTo(x - sign * r * .16f, c.y + r * .44f)
            }, orange, style = Stroke(r * .035f))
        }
        for (i in 0 until 16) quarantineCell(l, i, orange.copy(alpha = .15f))
        frame.limitFraction()?.let { f ->
            val x = c.x - r * .85f + r * 1.7f * f
            drawLine(LIMIT_RED, Offset(x, c.y + r * .55f), Offset(x, c.y + r * .92f), r * .035f)
        }
    }

    private fun DrawScope.quarantineCell(l: GaugeLayout, i: Int, color: Color) {
        val r = l.radius
        drawRect(color, Offset(l.center.x - r * .85f + i * r * .106f, l.center.y + r * .66f), Size(r * .075f, r * .15f))
    }

    override fun DrawScope.drawDynamic(frame: GaugeFrame, assets: GaugeAssets) {
        val l = cardLayout(size)
        for (i in 0 until 16) {
            if ((i + .5f) / 16 > frame.worldFiveFraction()) break
            quarantineCell(l, i, if (frame.limitFraction()?.let { (i + .5f) / 16 >= it } == true) LIMIT_RED else frame.warned(orange))
        }
        worldFiveReadout(frame, assets, l, assets.oswald, pale, orange, muted)
    }
}

/** A bolted olive field instrument, luminous ticks and a mechanical pointer around the readout. */
object FieldOpsTheme : GaugeTheme {
    override val id = "world-field_ops"
    override val title = "FIELD OPS"
    override val spring = NeedleSpring(omega = 12.0, zeta = .85)
    private val olive = Color(0xFF2B3428)
    private val face = Color(0xFF171F19)
    private val lume = Color(0xFFCDDCAC)
    private val khaki = Color(0xFFF1E9A5)

    override fun DrawScope.drawStatic(frame: GaugeFrame, assets: GaugeAssets) {
        drawRect(olive)
        val l = cardLayout(size)
        val c = l.center
        val r = l.radius
        drawCircle(face, r * 1.04f, c)
        drawCircle(lume.copy(alpha = .5f), r * 1.04f, c, style = Stroke(r * .025f))
        worldFiveTicks(frame, l, lume, assets)
        limitArc(frame, c, r * .99f, r * .04f, LIMIT_RED)
        text("FIELD / PACE", c.x, c.y - r * .43f, assets.paint(assets.courierBold), r * .11f, khaki)
        for (x in listOf(r * .09f, size.width - r * .09f)) {
            for (y in listOf(r * .12f, size.height - r * .12f)) {
                drawCircle(lume.copy(alpha = .4f), r * .035f, Offset(x, y))
                drawLine(face, Offset(x - r * .025f, y), Offset(x + r * .025f, y), r * .014f)
            }
        }
        drawLine(lume.copy(alpha = .25f), Offset(l.stats.left, l.stats.bottom), Offset(l.stats.right, l.stats.bottom), r * .012f)
    }

    override fun DrawScope.drawDynamic(frame: GaugeFrame, assets: GaugeAssets) {
        val l = cardLayout(size)
        val c = l.center
        val r = l.radius
        val a = angleRad(frame.needleKmh, frame.rangeKmh)
        val tip = polar(c, r * .98f, a)
        val left = polar(c, r * .81f, a - .07f)
        val right = polar(c, r * .81f, a + .07f)
        drawPath(Path().apply {
            moveTo(tip.x, tip.y); lineTo(left.x, left.y); lineTo(right.x, right.y); close()
        }, frame.warned(khaki))
        worldFiveReadout(frame, assets, l, assets.courierBold, lume, khaki, lume.copy(alpha = .75f))
    }
}
