package com.sappy.speedome.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sappy.speedome.BuildConfig
import com.sappy.speedome.LocalAppContainer
import com.sappy.speedome.engine.TrackView
import com.sappy.speedome.engine.sim.SimPreset
import com.sappy.speedome.tracking.SimulatorSource
import com.sappy.speedome.ui.theme.SpeedoColors
import kotlinx.coroutines.launch

/** Developer-only controls under the gauge: simulator driving, session commands and raw engine readout. */
@Composable
fun DevPanel(view: TrackView, truth: SimulatorSource.Truth) {
    val app = LocalAppContainer.current
    DisposableEffect(Unit) {
        app.motion.acquire()
        onDispose { app.motion.release() }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (truth.running) SimControls()
        if (BuildConfig.DEBUG) ResumeTests()
        DebugCard(view, truth)
    }
}

@Composable
private fun SimControls() {
    val app = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val c by app.simulator.controls.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label("SIMULATOR")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(c.preset == null && c.cruiseKmh == null, onClick = { app.simulator.update { it.copy(preset = null, cruiseKmh = null) } }, label = { Text("Manual") })
            SimPreset.entries.forEach { p ->
                FilterChip(c.preset == p, onClick = {
                    app.simulator.update { it.copy(preset = p, cruiseKmh = null) }
                    scope.launch { app.settings.update { s -> s.copy(mode = p.mode) } }
                }, label = { Text(p.label) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HoldButton("BRAKE", Color(0xFF3A3F42), Modifier.weight(1f)) { down ->
                app.simulator.update { it.copy(brake = down, preset = if (down) null else it.preset, cruiseKmh = if (down) null else it.cruiseKmh) }
            }
            HoldButton("GAS", Color(0xFFC97A12), Modifier.weight(1f)) { down ->
                app.simulator.update { it.copy(throttle = down, preset = if (down) null else it.preset, cruiseKmh = if (down) null else it.cruiseKmh) }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(!c.signal, onClick = { app.simulator.update { it.copy(signal = !it.signal) } }, label = { Text("Signal loss") })
            FilterChip(!c.doppler, onClick = { app.simulator.update { it.copy(doppler = !it.doppler) } }, label = { Text("No speed field") })
            FilterChip(false, onClick = { app.simulator.spike() }, label = { Text("200 km/h spike") })
            listOf(1.0, 5.0, 10.0).forEach { hz ->
                FilterChip(c.hz == hz, onClick = { app.simulator.update { it.copy(hz = hz) } }, label = { Text("${hz.toInt()} Hz") })
            }
        }
    }
}

/** Kills the process mid-session so both resume paths can be tested on a device. */
@Composable
private fun ResumeTests() {
    val app = LocalAppContainer.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Label("TEST RESUME")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { app.recorder.debugKill(0) }) { Text("Kill app") }
            OutlinedButton(onClick = { app.recorder.debugKill(31) }) { Text("Kill, 31 min later") }
        }
    }
}

@Composable
private fun DebugCard(v: TrackView, truth: SimulatorSource.Truth) {
    val app = LocalAppContainer.current
    val motion by app.motion.motion.collectAsStateWithLifecycle()
    val gnss by app.gnss.snapshot.collectAsStateWithLifecycle()
    val f = v.lastFix
    val now = SystemClock.elapsedRealtimeNanos()
    val lines = buildList {
        add("speed   ${Fmt.decimal(Fmt.kmh(v.speedMps), 1)} km/h  raw ${v.rawSpeedMps?.let { Fmt.decimal(Fmt.kmh(it), 1) } ?: "–"}")
        add("source  ${v.source}  quality ${v.quality}  rejects ${v.rejectTotal}")
        add("accel   ${Fmt.decimal(v.accelMps2, 2)} m/s²  zero ${v.zero}")
        add("dist    ${Fmt.decimal(v.distanceM, 1)} m  gaps ${v.gaps}")
        add("time    moving ${Fmt.duration(v.movingS)}  total ${Fmt.duration(v.elapsedS)}")
        add("steps   ${v.steps}  cadence ${Fmt.decimal(v.cadenceSpm, 0)}  pace ${Fmt.pace(v.paceSecPerKm)}")
        add("session ${v.sessionKind}${if (v.paused) " PAUSED" else ""}  segment ${v.segment}")
        if (f != null) {
            add("fix     ${Fmt.decimal(f.lat, 6)}, ${Fmt.decimal(f.lon, 6)}  ±${Fmt.decimal(f.hAcc, 1)} m")
            add("        age ${(now - f.tNanos) / 1_000_000} ms${if (f.isMock) "  MOCK" else ""}")
        }
        add("sats    ${gnss.usedCount} used / ${gnss.satellites.size} in view${if (gnss.dualFrequency) "  L1+L5" else ""}  ${gnss.hardwareModel ?: ""}")
        add("heading ${motion.headingDeg?.let { Fmt.decimal(it.toDouble(), 0) + "°" } ?: "–"}  yaw ${Fmt.decimal(motion.yawRateRadS.toDouble(), 2)} rad/s")
        if (truth.running) add("truth   ${Fmt.decimal(Fmt.kmh(truth.speedMps), 1)} km/h  odo ${Fmt.decimal(truth.odometerM, 1)} m")
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SpeedoColors.Raised).padding(12.dp),
    ) {
        Label("ENGINE")
        lines.forEach { Text(it, color = SpeedoColors.Text, fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = SpeedoColors.Muted, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun HoldButton(label: String, color: Color, modifier: Modifier, onHold: (Boolean) -> Unit) {
    var down by remember { mutableStateOf(false) }
    Box(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (down) color else color.copy(alpha = 0.7f))
            .semantics { role = Role.Button }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    down = true
                    onHold(true)
                    tryAwaitRelease()
                    down = false
                    onHold(false)
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
    }
}
