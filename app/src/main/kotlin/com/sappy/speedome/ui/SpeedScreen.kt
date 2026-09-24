package com.sappy.speedome.ui

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sappy.speedome.LocalAppContainer
import com.sappy.speedome.engine.EngineState
import com.sappy.speedome.engine.GpsQuality
import com.sappy.speedome.engine.TrackView
import com.sappy.speedome.engine.view
import com.sappy.speedome.gauges.RetroTheme
import com.sappy.speedome.gauges.GaugeView
import com.sappy.speedome.ui.theme.SpeedoColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Samples the engine a few times a second for text readouts (the gauge has its own frame loop). */
@Composable
fun rememberTrackView(engine: StateFlow<EngineState>, periodMs: Long = 250): TrackView {
    val v by produceState(TrackView.EMPTY, engine) {
        while (true) {
            value = engine.value.view(SystemClock.elapsedRealtimeNanos())
            delay(periodMs)
        }
    }
    return v
}

private val HEADER_HEIGHT = 44.dp
private val HEADER_HEIGHT_LANDSCAPE = 52.dp // hosts the 48 dp control pills
private val CONTROLS_HEIGHT = 64.dp
private val STRIP_HEIGHT = 40.dp // room for two lines when every switch is on

@Composable
fun SpeedScreen() {
    val app = LocalAppContainer.current
    val settings by app.settings.state.collectAsStateWithLifecycle()
    val simRunning by remember { app.simulator.truth.map { it.running }.distinctUntilChanged() }.collectAsStateWithLifecycle(false)
    val view = rememberTrackView(app.tracking.state)
    val entry = SpeedThemes.byId(settings.theme)
    val theme = entry.gauge ?: RetroTheme
    val showStrip = entry.gauge != null && theme.id != "night" && (settings.nerdStrip || settings.showHeading || settings.showGForce)
    val needsCompass = theme.id == "tape" || settings.showHeading || settings.showGForce
    DisposableEffect(needsCompass) {
        if (needsCompass) app.motion.acquire()
        onDispose { if (needsCompass) app.motion.release() }
    }
    val context = LocalContext.current
    val batteryLow by produceState(false) {
        while (true) {
            val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val charging = (i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
            value = !charging && level >= 0 && level * 100 / scale.coerceAtLeast(1) < 15
            delay(30_000)
        }
    }
    val driver = rememberGaugeDriver(
        app.tracking.state, theme, settings,
        heading = { if (needsCompass) app.motion.motion.value.headingDeg else null },
        batteryLow = { batteryLow },
    )
    val chrome = if (theme.light) Color.White else Color.Black
    val ink = if (theme.light) Color(0xFF111111) else SpeedoColors.Text
    val permissions = rememberPermissions()
    var targetDialog by remember { mutableStateOf(false) }
    if (targetDialog) TargetDialog(view.target) { targetDialog = false }
    val targetH = if (view.target != null) TARGET_STRIP_HEIGHT else 0.dp
    val scope = rememberCoroutineScope()

    fun switchTheme(delta: Int) {
        val all = SpeedThemes.all
        val next = all[(all.indexOf(entry) + delta + all.size) % all.size]
        scope.launch { app.settings.update { it.copy(theme = next.id) } }
    }
    val onSwipe by rememberUpdatedState(::switchTheme)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Landscape (car mount): the gauge takes the full height and the controls move into the header.
        val landscape = maxWidth > maxHeight
        val gaugeHeight = if (landscape) {
            (maxHeight - HEADER_HEIGHT_LANDSCAPE - targetH - if (showStrip) STRIP_HEIGHT else 0.dp).coerceAtLeast(160.dp)
        } else {
            (maxHeight - HEADER_HEIGHT - CONTROLS_HEIGHT - targetH - if (showStrip) STRIP_HEIGHT else 0.dp).coerceAtLeast(240.dp)
        }
        // On the Map the page must not scroll or swipe: map pinches would otherwise reach the page and
        // stretch the whole screen (Android's overscroll effect). Theme arrows still switch themes.
        val mapShown = entry.id == SpeedThemes.MAP
        Column(Modifier.fillMaxSize().background(chrome).verticalScroll(rememberScrollState(), enabled = !mapShown)) {
            Header(
                height = if (landscape) HEADER_HEIGHT_LANDSCAPE else HEADER_HEIGHT,
                ink = ink,
                view.quality, simRunning, entry.title, onPrev = { switchTheme(-1) }, onNext = { switchTheme(1) },
                controls = if (landscape) ({ SessionControls(view) { targetDialog = true } }) else null,
            )
            Column(Modifier.padding(horizontal = 16.dp)) {
                PermissionCards(permissions, settings.mode)
                ReliabilityOfferCard(permissions, recording = view.sessionKind == com.sappy.speedome.engine.SessionKind.TRIP)
            }
            // Canvas gauges have no text for TalkBack; describe what they show (read when focused).
            val gaugeDescription = if (entry.gauge != null) {
                "${entry.title.lowercase()} gauge: ${Fmt.speed(view.speedMps).roundToInt()} ${Fmt.speedUnit}, " +
                    "dial 0 to ${driver.frame.rangeToKmh}. Trip ${Fmt.dist(view.distanceM)} ${Fmt.distUnit}, max ${Fmt.speed(view.maxMps).roundToInt()}."
            } else {
                null
            }
            Box(
                Modifier.fillMaxWidth().height(gaugeHeight)
                    .then(if (gaugeDescription != null) Modifier.semantics { contentDescription = gaugeDescription } else Modifier)
                    .pointerInput(mapShown) {
                        if (mapShown) return@pointerInput
                    var dx = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dx = 0f },
                        onDragEnd = { if (abs(dx) > 60.dp.toPx()) onSwipe(if (dx < 0) 1 else -1) },
                        onHorizontalDrag = { _, amount -> dx += amount },
                    )
                },
            ) {
                Crossfade(entry, animationSpec = tween(250), label = "theme") { e ->
                    val gauge = e.gauge
                    when {
                        gauge != null -> GaugeView(gauge, { driver.frame }, Modifier.fillMaxSize())
                        e.id == SpeedThemes.MAP -> MapPage(driver, settings.mapNorthUp, Modifier.fillMaxSize())
                        else -> NerdPage(Modifier.fillMaxSize())
                    }
                }
            }
            view.target?.let { t -> TargetStrip(t) { targetDialog = true } }
            if (showStrip) InfoStrip(settings, view, driver)
            if (!landscape) Box(Modifier.height(CONTROLS_HEIGHT).fillMaxWidth().background(Color.Black), contentAlignment = Alignment.Center) { SessionControls(view) { targetDialog = true } }
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (view.lastFix == null && permissions.state.canTrack && !simRunning) {
                    Caption("Waiting for GPS. The first fix is quickest outdoors or near a window.")
                }
                if (settings.devMode) DevPanel(view, driver.frameInfo)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun Header(
    height: androidx.compose.ui.unit.Dp,
    ink: Color,
    quality: GpsQuality,
    simulated: Boolean,
    title: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    controls: (@Composable () -> Unit)? = null,
) {
    val (color, label) = when (quality) {
        GpsQuality.GOOD -> Color(0xFF3ECF7A) to "GPS"
        GpsQuality.FALLBACK -> Color(0xFFF0B43C) to "GPS · POSITION"
        GpsQuality.NONE -> Color(0xFFFF5B4E) to "NO FIX"
    }
    Row(Modifier.fillMaxWidth().height(height).padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Text(
            if (simulated) "$label · SIM" else label,
            color = SpeedoColors.Muted, fontSize = 12.sp, letterSpacing = 2.sp,
            modifier = Modifier.padding(start = 8.dp).weight(1f),
        )
        if (controls != null) Box(Modifier.weight(1.4f)) { controls() }
        Arrow("‹", "Previous theme", ink, onPrev)
        Text(
            title, color = ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.5.sp,
            textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 96.dp),
        )
        Arrow("›", "Next theme", ink, onNext)
    }
}

@Composable
private fun Arrow(glyph: String, description: String, ink: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text(glyph, color = ink, fontSize = 22.sp) }
}

/** Compact data strip under any gauge: nerd readouts, heading and G-force, per the Settings switches. */
@Composable
private fun InfoStrip(settings: com.sappy.speedome.settings.AppSettings, view: TrackView, driver: GaugeDriver) {
    val app = LocalAppContainer.current
    val gnss by app.gnss.snapshot.collectAsStateWithLifecycle()
    val motion by app.motion.motion.collectAsStateWithLifecycle()
    val parts = buildList {
        if (settings.nerdStrip) {
            add("SATS ${gnss.usedCount}/${gnss.satellites.size}")
            view.lastFix?.let { f ->
                add("±${Fmt.decimal(f.hAcc, 0)}m")
                add("${Fmt.decimal(f.lat, 4)},${Fmt.decimal(f.lon, 4)}")
                f.altM?.let { add("ALT ${Fmt.decimal(it, 0)}m") }
            }
        }
        if (settings.showHeading) {
            val h = motion.headingDeg ?: view.lastFix?.bearing?.toFloat()
            add(h?.let { "HDG ${it.toInt().toString().padStart(3, '0')}°${compassPoint(it)}" } ?: "HDG —")
        }
        if (settings.showGForce) {
            val g = gForce(view, motion)
            val lat = g.lateral
            val fwd = g.forward
            add("G ${Fmt.decimal(kotlin.math.abs(lat), 2)} lat ${Fmt.decimal(fwd, 2)} fwd")
        }
    }
    Box(Modifier.fillMaxWidth().height(STRIP_HEIGHT).background(Color.Black), contentAlignment = Alignment.Center) {
        Text(
            parts.joinToString(" · ") { it.replace(' ', '\u00A0') }, color = SpeedoColors.Muted, fontSize = 10.sp, lineHeight = 13.sp, textAlign = TextAlign.Center,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, maxLines = 2, modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

private fun compassPoint(deg: Float): String = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[(((deg % 360) + 360) % 360 / 45f + .5f).toInt() % 8]
