package com.sappy.speedome.ui

import com.sappy.speedome.monotonicNanos
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
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

/**
 * Samples the engine a few times a second for text readouts (the gauge has its own frame loop).
 * It pauses while the app is off screen, so a closed app doesn't wake the CPU.
 */
@Composable
fun rememberTrackView(engine: StateFlow<EngineState>, periodMs: Long = 250): TrackView {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val v by produceState(TrackView.EMPTY, engine, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = engine.value.view(monotonicNanos())
                delay(periodMs)
            }
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
    val batteryLow = rememberBatteryLow()
    val driver = rememberGaugeDriver(
        app.tracking.state, theme, settings,
        heading = { if (needsCompass) app.motion.motion.value.headingDeg else null },
        batteryLow = { batteryLow },
    )
    // The app's look follows the theme (ui/theme/Theme.kt), so the chrome around the gauge matches it.
    val chrome = SpeedoColors.Background
    val ink = SpeedoColors.Text
    val permissions = rememberPermissionsUi()
    var targetDialog by remember { mutableStateOf(false) }
    if (targetDialog) TargetDialog(view.target) { targetDialog = false }
    var limitDialog by remember { mutableStateOf(false) }
    if (limitDialog) LimitDialog { limitDialog = false }
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
        // On the Map, swipes pan the map instead of switching themes (the header arrows still do). The page
        // still scrolls (to the dev panel): the map view keeps its own drags and pinches (GestureMapView).
        val mapShown = entry.id == SpeedThemes.MAP
        Column(Modifier.fillMaxSize().background(chrome).verticalScroll(rememberScrollState())) {
            Header(
                height = if (landscape) HEADER_HEIGHT_LANDSCAPE else HEADER_HEIGHT,
                ink = ink,
                view.quality, simRunning,
                picker = {
                    // Each theme's chip shows its app look: background and accent.
                    val options = remember(settings) {
                        SpeedThemes.all.map { e -> com.sappy.speedome.ui.theme.lookFor(settings.copy(theme = e.id)).let { l -> PickOption(e.id, e.title, listOf(l.background, l.accent)) } }
                    }
                    ArrowPicker(entry.title, "theme", options, entry.id, onSelect = { id -> scope.launch { app.settings.update { it.copy(theme = id) } } }, ink)
                },
                controls = if (landscape) ({ SessionControls(view, onTarget = { targetDialog = true }, onLimit = { limitDialog = true }) }) else null,
            )
            Column(Modifier.padding(horizontal = 16.dp)) {
                permissions.Cards(settings.mode, recording = view.sessionKind == com.sappy.speedome.engine.SessionKind.TRIP)
            }
            // Canvas gauges have no text for TalkBack; describe what they show (read when focused).
            val gaugeDescription = if (entry.gauge != null) {
                val limit = settings.speedLimit?.let { ", limit ${Fmt.speed(it).roundToInt()}" }.orEmpty()
                "${entry.title.lowercase()} gauge: ${Fmt.speed(view.speedMps).roundToInt()} ${Fmt.speedUnit}$limit, " +
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
                        e.id == SpeedThemes.MAP -> com.sappy.speedome.ui.worlds.WorldPage(driver, settings, Modifier.fillMaxSize())
                        e.id == SpeedThemes.GRAPH -> GraphPage(driver, settings.graphWindowS, Modifier.fillMaxSize())
                        else -> NerdPage(Modifier.fillMaxSize())
                    }
                }
            }
            view.target?.let { t -> TargetStrip(t) { targetDialog = true } }
            if (showStrip) InfoStrip(settings, view, driver)
            if (!landscape) {
                Box(Modifier.height(CONTROLS_HEIGHT).fillMaxWidth().background(chrome), contentAlignment = Alignment.Center) {
                    SessionControls(view, onTarget = { targetDialog = true }, onLimit = { limitDialog = true })
                }
            }
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (view.lastFix == null && permissions.canTrack && !simRunning) {
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
    picker: @Composable () -> Unit,
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
        picker()
    }
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
    Box(Modifier.fillMaxWidth().height(STRIP_HEIGHT).background(SpeedoColors.Background), contentAlignment = Alignment.Center) {
        Text(
            parts.joinToString(" · ") { it.replace(' ', '\u00A0') }, color = SpeedoColors.Muted, fontSize = 10.sp, lineHeight = 13.sp, textAlign = TextAlign.Center,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, maxLines = 2, modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

private fun compassPoint(deg: Float): String = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[(((deg % 360) + 360) % 360 / 45f + .5f).toInt() % 8]
