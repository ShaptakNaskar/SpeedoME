package com.sappy.speedome.gauges

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI

// Original map-card instruments. Geometry follows cardLayout in both docked and tall cards.
private fun DrawScope.hero6Readout(frame: GaugeFrame, assets: GaugeAssets, l: GaugeLayout, face: GaugeTypeface, ink: Color, accent: Color) {
    val c = l.center
    val r = l.radius
    text(frame.readout.toString(), c.x, c.y, assets.paint(face), r * .53f, frame.warned(ink))
    text(frame.options.units.label.uppercase(), c.x, c.y + r * .31f, assets.paint(assets.courierBold), r * .13f, accent)
}

private fun DrawScope.hero6Stats(frame: GaugeFrame, assets: GaugeAssets, l: GaugeLayout, ink: Color, muted: Color) {
    cardStats(frame, l, assets.paint(assets.courierBold), assets.paint(assets.courier), ink, muted, scale = .82f)
}

private fun DrawScope.hero6Ticks(frame: GaugeFrame, assets: GaugeAssets, l: GaugeLayout, color: Color, radius: Float = .88f) {
    for (set in frame.scaleSets()) {
        set.forEachTick(frame.rangeKmh) { v, major ->
            val a = angleRad(v, frame.rangeKmh)
            val col = (if (frame.inRedZone(v)) LIMIT_RED else color).copy(alpha = set.alpha)
            drawLine(col, polar(l.center, l.radius * radius, a), polar(l.center, l.radius * (radius - if (major) .09f else .035f), a), l.radius * .012f)
        }
        val step = niceStep(set.max).label * 2
        // Indexed labels avoid accumulating floating-point error at the end of the scale.
        for (i in 0..(set.max / step).toInt()) {
            val v = i * step
            if (v <= frame.rangeKmh * 1.001f) {
                val at = polar(l.center, l.radius * (radius - .2f), angleRad(v, frame.rangeKmh))
                text(fmt(v.toDouble(), 0), at.x, at.y, assets.paint(assets.courier), l.radius * .105f,
                    (if (frame.inRedZone(v)) LIMIT_RED else color).copy(alpha = set.alpha))
            }
        }
    }
    limitArc(frame, l.center, l.radius * (radius + .035f), l.radius * .025f, LIMIT_RED)
}

/** A gold royal seal in a garnet folio, with pointed heraldic leaves. */
object OldKingdomTheme : GaugeTheme {
    override val id = "world-old_kingdom"
    override val title = "OLD KINGDOM"
    override val spring = NeedleSpring(omega = 10.0, zeta = .85)
    private val gold = Color(0xFFE6CF94)
    private val ink = Color(0xFF291D24)

    override fun DrawScope.drawStatic(frame: GaugeFrame, assets: GaugeAssets) {
        drawRect(Color(0xFF34242D))
        val l = cardLayout(size)
        val c = l.center
        val r = l.radius
        drawRect(gold.copy(alpha = .55f), Offset(4f, 4f), Size(size.width - 8f, size.height - 8f), style = Stroke(1f))
        drawCircle(gold, r * .98f, c)
        drawCircle(ink, r * .93f, c, style = Stroke(r * .025f))
        for (i in 0 until 24) {
            val a = (i * PI / 12).toFloat()
            val p = polar(c, r * .99f, a)
            val leaf = Path().apply {
                val tip = polar(c, r * 1.065f, a)
                val left = polar(c, r * 1.015f, a - .04f)
                val right = polar(c, r * 1.015f, a + .04f)
                moveTo(p.x, p.y); lineTo(left.x, left.y); lineTo(tip.x, tip.y); lineTo(right.x, right.y); close()
            }
            drawPath(leaf, gold)
        }
        hero6Ticks(frame, assets, l, ink, .85f)
        text("CROWN ROAD", c.x, c.y - r * .38f, assets.paint(assets.cinzel, 600), r * .12f, ink)
        val crown = Path().apply {
            moveTo(c.x - r * .19f, c.y + r * .49f)
            lineTo(c.x - r * .16f, c.y + r * .64f)
            lineTo(c.x + r * .16f, c.y + r * .64f)
            lineTo(c.x + r * .19f, c.y + r * .49f)
            lineTo(c.x + r * .07f, c.y + r * .55f)
            lineTo(c.x, c.y + r * .44f)
            lineTo(c.x - r * .07f, c.y + r * .55f); close()
        }
        drawPath(crown, ink)
    }

    override fun DrawScope.drawDynamic(frame: GaugeFrame, assets: GaugeAssets) {
        val l = cardLayout(size)
        hero6Readout(frame, assets, l, assets.cinzel, ink, ink)
        val at = polar(l.center, l.radius * .9f, angleRad(frame.needleKmh, frame.rangeKmh))
        drawCircle(frame.warned(ink), l.radius * .035f, at)
        hero6Stats(frame, assets, l, gold, gold.copy(alpha = .8f))
    }
}

/** A stepped Art Deco dashboard: ivory fan, black lacquer and a vermilion pointer. */
object ProhibitionTheme : GaugeTheme {
    override val id = "world-prohibition"
    override val title = "PROHIBITION"
    override val spring = NeedleSpring(omega = 12.0, zeta = .8)
    private val ivory = Color(0xFFE8D29A)
    private val black = Color(0xFF1F2422)
    private val red = Color(0xFFBD624C)

    override fun DrawScope.drawStatic(frame: GaugeFrame, assets: GaugeAssets) {
        drawRect(black)
        val l = cardLayout(size)
        val c = l.center
        val r = l.radius
        for (i in 0..2) {
            val inset = r * (.055f + i * .055f)
            drawRect(ivory.copy(alpha = .6f), Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2), style = Stroke(r * .012f))
        }
        val fan = Path().apply {
            moveTo(c.x - r, c.y + r * .62f)
            lineTo(c.x - r, c.y - r * .45f)
            lineTo(c.x - r * .55f, c.y - r * .9f)
            lineTo(c.x + r * .55f, c.y - r * .9f)
            lineTo(c.x + r, c.y - r * .45f)
            lineTo(c.x + r, c.y + r * .62f); close()
        }
        drawPath(fan, ivory)
        for (i in 0..10) {
            val a = ((200 + i * 14) * PI / 180).toFloat()
            drawLine(black.copy(alpha = .25f), polar(c, r * .69f, a), polar(c, r * .72f, a), r * .012f)
        }
        hero6Ticks(frame, assets, l, black, .83f)
        drawRect(black, Offset(c.x - r * .44f, c.y - r * .25f), Size(r * .88f, r * .63f))
        text("MOTOR CLUB", c.x, c.y - r * .43f, assets.paint(assets.cinzel, 600), r * .13f, black)
    }

    override fun DrawScope.drawDynamic(frame: GaugeFrame, assets: GaugeAssets) {
        val l = cardLayout(size)
        val c = l.center
        val r = l.radius
        hero6Readout(frame, assets, l, assets.oswald, ivory, ivory)
        val a = angleRad(frame.needleKmh, frame.rangeKmh)
        drawLine(frame.warned(red), polar(c, r * .85f, a), polar(c, r * .69f, a), r * .05f)
        hero6Stats(frame, assets, l, ivory, ivory.copy(alpha = .75f))
    }
}

/** An open travel journal: ruled pages, sewn spine and a live ribbon bookmark. */
object NewChapterTheme : GaugeTheme {
    override val id = "world-new_chapter"
    override val title = "NEW CHAPTER"
    override val spring = NeedleSpring(omega = 11.0, zeta = 1.0)
    override val light = true
    private val paper = Color(0xFFF7EACB)
    private val ink = Color(0xFF473927)
    private val ochre = Color(0xFFA95C21)

    override fun DrawScope.drawStatic(frame: GaugeFrame, assets: GaugeAssets) {
        drawRect(Color(0xFF927A51))
        drawRect(paper, Offset(5f, 4f), Size(size.width - 10f, size.height - 10f))
        val l = cardLayout(size)
        val c = l.center
        val r = l.radius
        val spine = if (l.landscape) l.stats.left - r * .1f else size.width * .08f
        drawLine(ink.copy(alpha = .3f), Offset(spine, 8f), Offset(spine, size.height - 12f), r * .025f)
        for (i in 1..12) {
            val y = size.height * i / 13
            drawLine(ink.copy(alpha = .08f), Offset(spine + 5f, y), Offset(size.width - 12f, y), 1f)
        }
        drawCircle(paper, r * .95f, c)
        drawArc(ochre, 155f, 230f, false, Offset(c.x - r * .91f, c.y - r * .91f), Size(r * 1.82f, r * 1.82f), style = Stroke(r * .018f))
        hero6Ticks(frame, assets, l, ink, .83f)
        text("THE OPEN ROAD", c.x, c.y - r * .36f, assets.paint(assets.cinzel, 600), r * .115f, ochre)
        text("A NEW CHAPTER", c.x, c.y + r * .6f, assets.paint(assets.courier), r * .1f, ink)
    }

    override fun DrawScope.drawDynamic(frame: GaugeFrame, assets: GaugeAssets) {
        val l = cardLayout(size)
        val c = l.center
        val r = l.radius
        hero6Readout(frame, assets, l, assets.cinzel, ink, ochre)
        val a = angleRad(frame.needleKmh, frame.rangeKmh)
        val tip = polar(c, r * .77f, a)
        val left = polar(c, r * 1.01f, a - .045f)
        val right = polar(c, r * 1.01f, a + .045f)
        drawPath(Path().apply { moveTo(tip.x, tip.y); lineTo(left.x, left.y); lineTo(right.x, right.y); close() }, frame.warned(ochre))
        hero6Stats(frame, assets, l, ink, ochre)
    }
}

/** A wireframe command display with a diamond speed cursor and broken targeting brackets. */
object VectorCommandTheme : GaugeTheme {
    override val id = "world-vector_command"
    override val title = "VECTOR COMMAND"
    override val spring = NeedleSpring(omega = 18.0, zeta = 1.0)
    private val blue = Color(0xFF9AC8FF)
    private val white = Color(0xFFEAF5FF)

    override fun DrawScope.drawStatic(frame: GaugeFrame, assets: GaugeAssets) {
        drawRect(Color(0xFF030815))
        val l = cardLayout(size)
        val c = l.center
        val r = l.radius
        for (i in 1..9) {
            val x = size.width * i / 10
            drawLine(blue.copy(alpha = .09f), Offset(x, 0f), Offset(x, size.height), 1f)
        }
        for (i in 1..5) {
            val y = size.height * i / 6
            drawLine(blue.copy(alpha = .09f), Offset(0f, y), Offset(size.width, y), 1f)
        }
        for (sx in listOf(-1f, 1f)) for (sy in listOf(-1f, 1f)) {
            val corner = Offset(c.x + sx * r * .97f, c.y + sy * r * .9f)
            drawLine(blue, corner, Offset(corner.x - sx * r * .24f, corner.y), r * .018f)
            drawLine(blue, corner, Offset(corner.x, corner.y - sy * r * .24f), r * .018f)
        }
        hero6Ticks(frame, assets, l, blue, .82f)
        text("VECTOR / SPD", c.x, c.y - r * .36f, assets.paint(assets.orbitron), r * .11f, blue)
        text("TELEMETRY", c.x, c.y + r * .58f, assets.paint(assets.courier), r * .11f, blue)
    }

    override fun DrawScope.drawDynamic(frame: GaugeFrame, assets: GaugeAssets) {
        val l = cardLayout(size)
        hero6Readout(frame, assets, l, assets.orbitron, white, blue)
        val at = polar(l.center, l.radius * .9f, angleRad(frame.needleKmh, frame.rangeKmh))
        val d = l.radius * .055f
        drawPath(Path().apply { moveTo(at.x, at.y - d); lineTo(at.x + d, at.y); lineTo(at.x, at.y + d); lineTo(at.x - d, at.y); close() }, frame.warned(white), style = Stroke(l.radius * .02f))
        hero6Stats(frame, assets, l, white, blue)
    }
}

/** A friendly molded console with a blue screen, controller buttons and a block speed ladder. */
object HomeConsoleTheme : GaugeTheme {
    override val id = "world-home_console"
    override val title = "HOME CONSOLE"
    override val spring = NeedleSpring(omega = 16.0, zeta = 1.0)
    override val light = true
    private val shell = Color(0xFFE8EDF0)
    private val blue = Color(0xFF17658E)
    private val screen = Color(0xFF15394B)

    override fun DrawScope.drawStatic(frame: GaugeFrame, assets: GaugeAssets) {
        drawRoundRect(shell, cornerRadius = CornerRadius(14f))
        val l = cardLayout(size)
        val c = l.center
        val r = l.radius
        drawRoundRect(Color(0xFFB2C2CC), Offset(c.x - r, c.y - r * .86f), Size(r * 2, r * 1.65f), CornerRadius(r * .15f))
        drawRoundRect(screen, Offset(c.x - r * .92f, c.y - r * .77f), Size(r * 1.84f, r * 1.46f), CornerRadius(r * .1f))
        text("PLAYER / SPEED", c.x, c.y - r * .52f, assets.paint(assets.silkscreen), r * .12f, Color(0xFFB7E9FF))
        for (i in 0 until 12) {
            val col = if (frame.inRedZone((i + .5f) / 12 * frame.rangeKmh)) LIMIT_RED else Color(0xFF78CBE8)
            drawRect(col.copy(alpha = .18f), Offset(c.x - r * .78f + i * r * .13f, c.y + r * .48f), Size(r * .095f, r * .1f))
        }
        frame.limitFraction()?.let { f ->
            val x = c.x - r * .78f + r * 1.56f * f
            drawLine(LIMIT_RED, Offset(x, c.y + r * .44f), Offset(x, c.y + r * .62f), r * .02f)
        }
        for (i in 0..2) drawCircle(listOf(blue, Color(0xFFB54F58), Color(0xFFB48A28))[i], r * .055f, Offset(c.x + (i - 1) * r * .24f, c.y + r * .91f))
    }

    override fun DrawScope.drawDynamic(frame: GaugeFrame, assets: GaugeAssets) {
        val l = cardLayout(size)
        val c = l.center
        val r = l.radius
        hero6Readout(frame, assets, l, assets.silkscreen, Color(0xFFE4F7FF), Color(0xFFB7E9FF))
        val f = (frame.needleKmh / frame.rangeKmh).coerceIn(0f, 1f)
        for (i in 0 until 12) if (i / 12f < f) {
            drawRect(if (frame.inRedZone((i + .5f) / 12 * frame.rangeKmh)) LIMIT_RED else frame.warned(Color(0xFF78CBE8)), Offset(c.x - r * .78f + i * r * .13f, c.y + r * .48f), Size(r * .095f, r * .1f))
        }
        hero6Stats(frame, assets, l, screen, blue)
    }
}

/** A battered field instrument: hazard edging, recessed glass and an analogue speed needle. */
object ExclusionZoneTheme : GaugeTheme {
    override val id = "world-exclusion_zone"
    override val title = "EXCLUSION ZONE"
    override val spring = NeedleSpring(omega = 9.0, zeta = .75)
    private val olive = Color(0xFF3A3F31)
    private val amber = Color(0xFFE6C274)
    private val cream = Color(0xFFE4E0BC)

    override fun DrawScope.drawStatic(frame: GaugeFrame, assets: GaugeAssets) {
        drawRect(olive)
        val l = cardLayout(size)
        val c = l.center
        val r = l.radius
        for (i in 0..19) {
            val x = size.width * i / 20
            drawRect(if (i % 2 == 0) amber else Color(0xFF191E18), Offset(x, 0f), Size(size.width / 20, r * .06f))
        }
        drawCircle(Color(0xFF191E18), r * 1.02f, c)
        drawCircle(Color(0xFF777B61), r * .98f, c, style = Stroke(r * .035f))
        drawCircle(cream, r * .89f, c)
        hero6Ticks(frame, assets, l, Color(0xFF272D23), .82f)
        text("FIELD / VELOCITY", c.x, c.y - r * .35f, assets.paint(assets.courierBold), r * .11f, olive)
        for (sx in listOf(-1, 1)) {
            val p = Offset(c.x + sx * r * .88f, c.y + r * .8f)
            drawCircle(Color(0xFF8A8C76), r * .055f, p)
            drawLine(olive, Offset(p.x - r * .03f, p.y), Offset(p.x + r * .03f, p.y), r * .015f)
        }
    }

    override fun DrawScope.drawDynamic(frame: GaugeFrame, assets: GaugeAssets) {
        val l = cardLayout(size)
        val c = l.center
        val r = l.radius
        val pivot = Offset(c.x, c.y + r * .55f)
        val end = polar(c, r * .49f, angleRad(frame.needleKmh, frame.rangeKmh))
        drawLine(frame.warned(Color(0xFF8A392C)), pivot, end, r * .025f)
        drawCircle(olive, r * .07f, pivot)
        drawRect(cream, Offset(c.x - r * .53f, c.y - r * .23f), Size(r * 1.06f, r * .62f))
        hero6Readout(frame, assets, l, assets.courierBold, olive, olive)
        hero6Stats(frame, assets, l, cream, amber)
    }
}

/** A mechanical survey atlas: blueprint lines, toothed brass bezel and a copper carriage. */
object MachineAtlasTheme : GaugeTheme {
    override val id = "world-machine_atlas"
    override val title = "MACHINE ATLAS"
    override val spring = NeedleSpring(omega = 13.0, zeta = .9)
    override val light = true
    private val paper = Color(0xFFE5E1CD)
    private val ink = Color(0xFF373A34)
    private val copper = Color(0xFF94452F)

    override fun DrawScope.drawStatic(frame: GaugeFrame, assets: GaugeAssets) {
        drawRect(paper)
        val l = cardLayout(size)
        val c = l.center
        val r = l.radius
        for (i in 1..14) {
            val x = size.width * i / 15
            drawLine(ink.copy(alpha = .08f), Offset(x, 0f), Offset(x, size.height), 1f)
        }
        for (i in 1..8) {
            val y = size.height * i / 9
            drawLine(ink.copy(alpha = .08f), Offset(0f, y), Offset(size.width, y), 1f)
        }
        for (i in 0 until 36) {
            val a = (i * PI / 18).toFloat()
            drawLine(ink, polar(c, r * .94f, a), polar(c, r * 1.015f, a), r * .06f)
        }
        drawCircle(Color(0xFFC3B88F), r * .95f, c)
        drawCircle(ink, r * .93f, c, style = Stroke(r * .018f))
        drawCircle(paper, r * .86f, c)
        hero6Ticks(frame, assets, l, ink, .81f)
        text("ATLAS / MOTION", c.x, c.y - r * .35f, assets.paint(assets.courierBold), r * .115f, copper)
        text("SURVEY INSTRUMENT", c.x, c.y + r * .58f, assets.paint(assets.courier), r * .09f, ink)
    }

    override fun DrawScope.drawDynamic(frame: GaugeFrame, assets: GaugeAssets) {
        val l = cardLayout(size)
        val c = l.center
        val r = l.radius
        hero6Readout(frame, assets, l, assets.courierBold, ink, copper)
        val a = angleRad(frame.needleKmh, frame.rangeKmh)
        drawLine(frame.warned(copper), polar(c, r * .8f, a), polar(c, r * .99f, a), r * .07f)
        drawCircle(paper, r * .022f, polar(c, r * .91f, a))
        hero6Stats(frame, assets, l, ink, copper)
    }
}
