package com.sappy.speedome.ui

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
import com.sappy.speedome.gauges.GaugeThemes
import com.sappy.speedome.gauges.GaugeView
import com.sappy.speedome.ui.theme.SpeedoColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs

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
private val CONTROLS_HEIGHT = 64.dp

@Composable
fun SpeedScreen() {
    val app = LocalAppContainer.current
    val settings by app.settings.state.collectAsStateWithLifecycle()
    val simRunning by remember { app.simulator.truth.map { it.running }.distinctUntilChanged() }.collectAsStateWithLifecycle(false)
    val view = rememberTrackView(app.tracking.state)
    val theme = GaugeThemes.byId(settings.theme)
    val driver = rememberGaugeDriver(app.tracking.state, theme, settings)
    val permissions = rememberPermissions()
    val scope = rememberCoroutineScope()

    fun switchTheme(delta: Int) {
        val all = GaugeThemes.all
        val next = all[(all.indexOf(theme) + delta + all.size) % all.size]
        scope.launch { app.settings.update { it.copy(theme = next.id) } }
        if (settings.startupSweep && driver.frame.needleKmh < 1f) driver.sweep()
    }
    val onSwipe by rememberUpdatedState(::switchTheme)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Landscape (car mount): the gauge takes the full height and the controls move into the header.
        val landscape = maxWidth > maxHeight
        val gaugeHeight = if (landscape) {
            (maxHeight - HEADER_HEIGHT).coerceAtLeast(160.dp)
        } else {
            (maxHeight - HEADER_HEIGHT - CONTROLS_HEIGHT).coerceAtLeast(240.dp)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Header(
                view.quality, simRunning, theme.title, onPrev = { switchTheme(-1) }, onNext = { switchTheme(1) },
                controls = if (landscape) ({ SessionControls(view) }) else null,
            )
            Box(Modifier.padding(horizontal = 16.dp)) { PermissionCards(permissions, settings.mode) }
            Box(
                Modifier.fillMaxWidth().height(gaugeHeight).pointerInput(Unit) {
                    var dx = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dx = 0f },
                        onDragEnd = { if (abs(dx) > 60.dp.toPx()) onSwipe(if (dx < 0) 1 else -1) },
                        onHorizontalDrag = { _, amount -> dx += amount },
                    )
                },
            ) {
                Crossfade(theme, animationSpec = tween(250), label = "theme") { t ->
                    GaugeView(t, { driver.frame }, Modifier.fillMaxSize())
                }
            }
            if (!landscape) Box(Modifier.height(CONTROLS_HEIGHT).fillMaxWidth(), contentAlignment = Alignment.Center) { SessionControls(view) }
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (view.lastFix == null && permissions.state.canTrack && !simRunning) {
                    Caption("Waiting for GPS. The first fix is quickest outdoors or near a window.")
                }
                if (settings.devMode) DevPanel(view)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun Header(
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
    Row(Modifier.fillMaxWidth().height(HEADER_HEIGHT).padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Text(
            if (simulated) "$label · SIM" else label,
            color = SpeedoColors.Muted, fontSize = 12.sp, letterSpacing = 2.sp,
            modifier = Modifier.padding(start = 8.dp).weight(1f),
        )
        if (controls != null) Box(Modifier.weight(1.4f)) { controls() }
        Arrow("‹", "Previous theme", onPrev)
        Text(
            title, color = SpeedoColors.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.5.sp,
            textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 96.dp),
        )
        Arrow("›", "Next theme", onNext)
    }
}

@Composable
private fun Arrow(glyph: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text(glyph, color = SpeedoColors.Text, fontSize = 22.sp) }
}
