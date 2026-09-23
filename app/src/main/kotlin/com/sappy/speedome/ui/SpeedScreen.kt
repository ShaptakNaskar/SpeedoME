package com.sappy.speedome.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sappy.speedome.LocalAppContainer
import com.sappy.speedome.engine.EngineState
import com.sappy.speedome.engine.GpsQuality
import com.sappy.speedome.engine.Mode
import com.sappy.speedome.engine.TrackView
import com.sappy.speedome.engine.view
import com.sappy.speedome.gauges.PlaceholderDial
import com.sappy.speedome.ui.theme.SpeedoColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/** Samples the engine a few times a second for text readouts (the needle has its own frame loop). */
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

@Composable
fun SpeedScreen() {
    val app = LocalAppContainer.current
    val settings by app.settings.state.collectAsStateWithLifecycle()
    val simTruth by app.simulator.truth.collectAsStateWithLifecycle()
    val needle = rememberNeedle(app.tracking.state, settings.predictNeedle)
    val view = rememberTrackView(app.tracking.state)
    val range = if (settings.mode == Mode.STEP) 20f else 120f // auto-range arrives in M5

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GpsRow(view.quality, simTruth.running)
        val permissions = rememberPermissions()
        PermissionCards(permissions, settings.mode)
        PlaceholderDial(Modifier.fillMaxWidth(0.78f), fraction = needle.kmh / range)
        Text(needle.readout.toString(), color = SpeedoColors.Text, fontSize = 72.sp, fontWeight = FontWeight.Light)
        Text("KM/H", color = SpeedoColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 3.sp)
        Spacer(Modifier.height(16.dp))
        StatsRow(view)
        Spacer(Modifier.height(16.dp))
        if (view.lastFix == null && permissions.state.canTrack && !simTruth.running) {
            Caption("Waiting for GPS. The first fix is quickest outdoors or near a window.")
            Spacer(Modifier.height(16.dp))
        }
        if (settings.devMode) DevPanel(view, simTruth)
    }
}

@Composable
private fun GpsRow(quality: GpsQuality, simulated: Boolean) {
    val (color, label) = when (quality) {
        GpsQuality.GOOD -> Color(0xFF3ECF7A) to "GPS"
        GpsQuality.FALLBACK -> Color(0xFFF0B43C) to "GPS · POSITION ONLY"
        GpsQuality.NONE -> Color(0xFFFF5B4E) to "NO FIX"
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Text(
            if (simulated) "$label · SIMULATED" else label,
            color = SpeedoColors.Muted,
            fontSize = 12.sp,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun StatsRow(v: TrackView) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        Stat(Fmt.decimal(Fmt.kmh(v.avgMovingMps), 0), "MOVING AVG")
        Stat(Fmt.decimal(Fmt.kmh(v.avgOverallMps), 0), "AVG")
        Stat(Fmt.decimal(Fmt.kmh(v.maxMps), 0), "MAX")
        Stat(Fmt.km(v.distanceM), "KM")
        Stat(Fmt.duration(v.elapsedS), "TIME")
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = SpeedoColors.Text, fontSize = 22.sp, fontWeight = FontWeight.Light)
        Text(label, color = SpeedoColors.Muted, fontSize = 10.sp, letterSpacing = 1.5.sp)
    }
}
