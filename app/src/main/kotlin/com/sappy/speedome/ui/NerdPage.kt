package com.sappy.speedome.ui

import android.location.GnssStatus
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sappy.speedome.LocalAppContainer
import com.sappy.speedome.engine.SpeedSource
import com.sappy.speedome.engine.view
import com.sappy.speedome.gauges.Align
import com.sappy.speedome.gauges.rememberGaugeAssets
import com.sappy.speedome.gauges.text
import com.sappy.speedome.tracking.Satellite
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private data class Constellation(val name: String, val color: Color)

private val CONSTELLATIONS = mapOf(
    GnssStatus.CONSTELLATION_GPS to Constellation("GPS", Color(0xFF4DA3FF)),
    GnssStatus.CONSTELLATION_GLONASS to Constellation("GLO", Color(0xFFFF6B6B)),
    GnssStatus.CONSTELLATION_GALILEO to Constellation("GAL", Color(0xFFF5C542)),
    GnssStatus.CONSTELLATION_BEIDOU to Constellation("BDS", Color(0xFF3FD49B)),
    GnssStatus.CONSTELLATION_QZSS to Constellation("QZS", Color(0xFFC77DFF)),
    7 /* GnssStatus.CONSTELLATION_IRNSS, API 29 */ to Constellation("NAV", Color(0xFFFF9F43)),
    GnssStatus.CONSTELLATION_SBAS to Constellation("SBS", Color(0xFF9AA5A0)),
)

private fun Satellite.style() = CONSTELLATIONS[constellation] ?: Constellation("???", Color.Gray)

/** Raw receiver data: sky plot, signal bars, fix details, G-force circle and the NMEA feed. */
@Composable
fun NerdPage(modifier: Modifier = Modifier) {
    val app = LocalAppContainer.current
    DisposableEffect(Unit) {
        app.motion.acquire()
        app.sources.setNmeaEnabled(true)
        onDispose {
            app.motion.release()
            app.sources.setNmeaEnabled(false)
        }
    }
    val gnss by app.gnss.snapshot.collectAsStateWithLifecycle()
    val nmea by app.gnss.nmea.collectAsStateWithLifecycle()
    val motion by app.motion.motion.collectAsStateWithLifecycle()
    val trail = remember { ArrayDeque<Offset>() }
    val tick by produceState(0L) {
        while (true) {
            delay(100)
            value = SystemClock.elapsedRealtimeNanos()
        }
    }
    val assets = rememberGaugeAssets()
    val v = remember(tick) { app.tracking.state.value.view(tick) }
    val gLat = (v.speedMps * motion.yawRateRadS / 9.81).toFloat().coerceIn(-1.2f, 1.2f)
    val gFwd = (v.accelMps2 / 9.81).toFloat().coerceIn(-1.2f, 1.2f)
    LaunchedEffect(tick) {
        trail.addLast(Offset(gLat, gFwd))
        while (trail.size > 30) trail.removeFirst()
    }

    Canvas(modifier) {
        val ink = Color(0xFFDFE9E5)
        val dim = Color(0xFFD2E1DC).copy(alpha = .5f)
        val mono = assets.paint(assets.b612)
        val bold = assets.paint(assets.b612Bold)
        drawRect(Color(0xFF070909))
        val land = size.width > size.height * 1.15f
        val pad = 16.dp.toPx()

        // Sky plot: centre = overhead, rim = horizon; filled dots are used in the fix.
        val sr = if (land) size.height * .3f else minOf(size.width * .25f, size.height * .15f)
        val sc = Offset(pad + sr + 6.dp.toPx(), pad + sr + 14.dp.toPx())
        listOf(1f, 2 / 3f, 1 / 3f).forEach { drawCircle(Color(0x38A0BEB4), sr * it, sc, style = Stroke(1.dp.toPx())) }
        drawLine(Color(0x38A0BEB4), Offset(sc.x - sr, sc.y), Offset(sc.x + sr, sc.y))
        drawLine(Color(0x38A0BEB4), Offset(sc.x, sc.y - sr), Offset(sc.x, sc.y + sr))
        listOf("N" to Offset(0f, -1f), "E" to Offset(1f, 0f), "S" to Offset(0f, 1f), "W" to Offset(-1f, 0f)).forEach { (l, d) ->
            text(l, sc.x + d.x * (sr + 10.dp.toPx()), sc.y + d.y * (sr + 10.dp.toPx()), mono, 10.dp.toPx(), dim)
        }
        for (s in gnss.satellites) {
            val rr = sr * (90 - s.elevationDeg) / 90
            val a = Math.toRadians((s.azimuthDeg - 90).toDouble())
            val p = Offset(sc.x + cos(a).toFloat() * rr, sc.y + sin(a).toFloat() * rr)
            val dot = (2 + ((s.cn0DbHz - 15) / 30).coerceIn(0f, 1f) * 4).dp.toPx()
            if (s.usedInFix) drawCircle(s.style().color, dot, p) else drawCircle(s.style().color, dot, p, style = Stroke(1.2f.dp.toPx()))
        }

        // Constellation table and the strongest signals.
        val tx = sc.x + sr + 26.dp.toPx()
        var ty = sc.y - sr + 4.dp.toPx()
        text("SYS   USED/VIEW", tx, ty, mono, 10.dp.toPx(), dim, Align.LEFT)
        gnss.satellites.groupBy { it.style() }.entries.sortedBy { it.key.name }.take(6).forEach { (c, list) ->
            ty += 16.dp.toPx()
            drawRect(c.color, Offset(tx, ty - 4.dp.toPx()), androidx.compose.ui.geometry.Size(8.dp.toPx(), 8.dp.toPx()))
            text("${c.name}   ${list.count { it.usedInFix }.toString().padStart(2)}/${list.size}", tx + 14.dp.toPx(), ty, mono, 11.dp.toPx(), ink, Align.LEFT)
        }
        val bars = gnss.satellites.sortedByDescending { it.cn0DbHz }.take(14)
        val barTop = ty + 18.dp.toPx()
        val bw = (if (land) size.width * .2f else size.width - tx - pad) / 14
        bars.forEachIndexed { i, s ->
            val h = ((s.cn0DbHz - 10) / 40).coerceIn(0f, 1f) * 34.dp.toPx()
            val c = s.style().color
            drawRect(if (s.usedInFix) c else c.copy(alpha = .3f), Offset(tx + i * bw, barTop + 34.dp.toPx() - h), androidx.compose.ui.geometry.Size(bw - 2.dp.toPx(), h))
        }
        text("C/N0 dB-Hz", tx, barTop + 46.dp.toPx(), mono, 9.dp.toPx(), dim, Align.LEFT)

        // Fix readouts.
        val f = v.lastFix
        val age = f?.let { (tick - it.tNanos) / 1_000_000 }
        fun deg(x: Double, pos: String, neg: String) = "${Fmt.decimal(abs(x), 6)}°${if (x >= 0) pos else neg}"
        val rows = listOf(
            "LAT" to (f?.let { deg(it.lat, "N", "S") } ?: "—"),
            "LON" to (f?.let { deg(it.lon, "E", "W") } ?: "—"),
            "ALT" to (f?.altM?.let { "${Fmt.decimal(it, 1)} m ±${f.vAcc?.let { a -> Fmt.decimal(a, 0) } ?: "?"}" } ?: "—"),
            "H.ACC" to (f?.let { "±${Fmt.decimal(it.hAcc, 1)} m" } ?: "—"),
            "SPD RAW" to (f?.rawSpeed?.let { "${Fmt.decimal(it * 3.6, 1)} ±${f.speedAcc?.let { a -> Fmt.decimal(a * 3.6, 1) } ?: "?"}" } ?: "no speed field"),
            "SPD FILT" to "${Fmt.decimal(v.speedMps * 3.6, 1)} km/h",
            "SOURCE" to when (v.source) {
                SpeedSource.DOPPLER -> "DOPPLER"
                SpeedSource.DOPPLER_NO_ACCURACY -> "DOPPLER (no acc)"
                SpeedSource.POSITION -> "POSITION"
                SpeedSource.STEPS -> "STEPS"
                SpeedSource.NONE -> "NO FIX"
            },
            "FIX AGE" to (age?.let { "$it ms" } ?: "—"),
            "HDG" to (motion.headingDeg?.let { "${it.toInt().toString().padStart(3, '0')}° compass" } ?: "—"),
            "CRS" to (f?.bearing?.takeIf { v.speedMps > 1 }?.let { "${it.toInt().toString().padStart(3, '0')}° gps" } ?: "— (stopped)"),
            "SATS" to "${gnss.usedCount} used / ${gnss.satellites.size} in view",
            "FILTER" to "${v.rejectTotal} rejected",
        )
        val rx = if (land) size.width * .4f else pad
        val ry = if (land) size.height * .1f else sc.y + sr + 32.dp.toPx()
        val colW = if (land) size.width * .3f else (size.width - 2 * pad) / 2
        val rh = if (land) size.height * .62f / 6 else 30.dp.toPx()
        rows.forEachIndexed { i, (k, value) ->
            val col = if (land) i / 6 else i % 2
            val row = if (land) i % 6 else i / 2
            val x = rx + col * colW
            val y = ry + row * rh
            text(k, x, y, mono, 9.dp.toPx(), dim, Align.LEFT)
            text(value, x, y + 13.dp.toPx(), mono, 12.dp.toPx(), ink, Align.LEFT)
        }

        // G-force circle: forward up, lateral right; the fading trail shows the last few seconds.
        val gr = if (land) size.height * .14f else minOf(size.width * .15f, 58.dp.toPx())
        val gc = if (land) Offset(size.width * .45f, size.height * .8f) else Offset(pad + gr, ry + 6 * rh + 44.dp.toPx())
        listOf(1f, .5f).forEach { drawCircle(Color(0x40A0BEB4), gr * it, gc, style = Stroke(1.dp.toPx())) }
        drawLine(Color(0x40A0BEB4), Offset(gc.x - gr, gc.y), Offset(gc.x + gr, gc.y))
        drawLine(Color(0x40A0BEB4), Offset(gc.x, gc.y - gr), Offset(gc.x, gc.y + gr))
        trail.forEachIndexed { i, p -> drawCircle(Color(0xFFFFAA3C).copy(alpha = i / 30f * .6f), 2.dp.toPx(), Offset(gc.x + p.x * gr, gc.y - p.y * gr)) }
        drawCircle(Color(0xFFFFAE3C), 5.dp.toPx(), Offset(gc.x + gLat * gr, gc.y - gFwd * gr))
        text("G ${Fmt.decimal(abs(gLat).toDouble(), 2)} lat · ${Fmt.decimal(gFwd.toDouble(), 2)} fwd", gc.x + gr + 14.dp.toPx(), gc.y - 8.dp.toPx(), mono, 11.dp.toPx(), ink, Align.LEFT)
        var bx = gc.x + gr + 14.dp.toPx()
        buildList {
            if (gnss.dualFrequency) add("L1+L5")
            gnss.hardwareModel?.let { add(it.take(18)) }
            if (f?.isMock == true) add("MOCK")
        }.forEach { b ->
            val w = b.length * 7.2f.dp.toPx() + 12.dp.toPx()
            val c = if (b == "MOCK") Color(0xFFFF8A3D) else ink
            drawRect(c.copy(alpha = .6f), Offset(bx, gc.y + 6.dp.toPx()), androidx.compose.ui.geometry.Size(w, 18.dp.toPx()), style = Stroke(1.dp.toPx()))
            text(b, bx + w / 2, gc.y + 15.dp.toPx(), bold, 10.dp.toPx(), c)
            bx += w + 6.dp.toPx()
        }

        // Raw NMEA, newest last.
        val ny = if (land) size.height * .72f else gc.y + gr + 20.dp.toPx()
        val nx = if (land) size.width * .62f else pad
        val lines = ((size.height - ny - 8.dp.toPx()) / 14.dp.toPx()).toInt().coerceAtLeast(1)
        nmea.takeLast(lines).forEachIndexed { i, l ->
            text(l.take(60), nx, ny + i * 14.dp.toPx(), mono, 10.dp.toPx(), Color(0xBF6EDCA0), Align.LEFT)
        }
    }
}
