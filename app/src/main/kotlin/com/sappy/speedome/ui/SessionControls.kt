package com.sappy.speedome.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sappy.speedome.LocalAppContainer
import com.sappy.speedome.data.SessionEntity
import com.sappy.speedome.engine.Command
import com.sappy.speedome.engine.SessionKind
import com.sappy.speedome.engine.TrackView
import com.sappy.speedome.ui.theme.SpeedoColors

/** Record / Pause / Resume / Stop / Reset, as in the Theme Lab footer. */
@Composable
fun SessionControls(v: TrackView, onTarget: () -> Unit) {
    val app = LocalAppContainer.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
        when {
            v.sessionKind == SessionKind.LIVE -> {
                Pill("● RECORD", Color(0xFFFF5B4E)) { app.recorder.startTrip() }
                Pill("RESET", SpeedoColors.Text) { app.recorder.reset() }
                TargetPill(v, onTarget)
            }
            v.paused -> {
                Pill("RESUME", SpeedoColors.Accent) { app.tracking.command(Command.Resume) }
                Pill("■ STOP", SpeedoColors.Text) { app.recorder.stopTrip() }
                TargetPill(v, onTarget)
            }
            else -> {
                Pill("PAUSE", SpeedoColors.Text) { app.tracking.command(Command.Pause) }
                Pill("■ STOP", SpeedoColors.Text) { app.recorder.stopTrip() }
                TargetPill(v, onTarget)
            }
        }
    }
}

/** A round "◎" button; amber while a target is set. */
@Composable
private fun TargetPill(v: TrackView, onClick: () -> Unit) {
    Text(
        "◎",
        color = if (v.target != null) SpeedoColors.Accent else SpeedoColors.Text,
        fontSize = 17.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.07f))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (v.target != null) "Edit target" else "Set target" }
            .padding(horizontal = 15.dp, vertical = 8.dp),
    )
}

@Composable
private fun Pill(label: String, color: Color, onClick: () -> Unit) {
    Text(
        label,
        color = color,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 2.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.07f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp),
    )
}

/** Summary sheet after Stop: saved by default, with Discard (docs/plan.md §7). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripSummarySheet() {
    val app = LocalAppContainer.current
    val id by app.recorder.finished.collectAsStateWithLifecycle()
    val tripId = id ?: return
    val trip by remember(tripId) { app.db.trips().observeSession(tripId) }.collectAsStateWithLifecycle(null)
    ModalBottomSheet(onDismissRequest = { app.recorder.dismissSummary() }, containerColor = SpeedoColors.Raised) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Trip saved", color = SpeedoColors.Text, fontSize = 22.sp)
            trip?.let { TripStatsGrid(it) }
            Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { app.recorder.discardTrip(tripId) }) { Text("Discard", color = Color(0xFFFF6B5E)) }
                FilledTonalButton(onClick = { app.recorder.dismissSummary() }) { Text("Done") }
            }
        }
    }
}

@Composable
fun TripStatsGrid(t: SessionEntity) {
    val avgMoving = if (t.movingS > 1) t.distanceM / t.movingS else 0.0
    val avgAll = if (t.elapsedS > 1) t.distanceM / t.elapsedS else 0.0
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Figure(Fmt.dist(t.distanceM), Fmt.distUnit.uppercase())
            Figure(Fmt.duration(t.elapsedS), "TIME")
            Figure(Fmt.decimal(Fmt.speed(t.maxMps), 0), "MAX ${Fmt.speedUnit.uppercase()}")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Figure(Fmt.decimal(Fmt.speed(avgMoving), 0), "MOVING AVG")
            Figure(Fmt.decimal(Fmt.speed(avgAll), 0), "AVG")
            Figure(if (t.steps > 0) t.steps.toString() else Fmt.duration(t.movingS), if (t.steps > 0) "STEPS" else "MOVING")
        }
    }
}

@Composable
private fun Figure(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = SpeedoColors.Text, fontSize = 24.sp, fontWeight = FontWeight.Light)
        Text(label, color = SpeedoColors.Muted, fontSize = 10.sp, letterSpacing = 1.5.sp)
    }
}

/** Asked on launch when an unfinished session is 30 min or older. */
@Composable
fun ResumeOfferDialog() {
    val app = LocalAppContainer.current
    val offer by app.recorder.offer.collectAsStateWithLifecycle()
    val o = offer ?: return
    val ago = Fmt.ago(o.gapMillis)
    if (o.session.kind == SessionKind.TRIP.name) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Unfinished trip") },
            text = { Text("SpeedoME stopped $ago during a trip of ${Fmt.dist(o.session.distanceM)} ${Fmt.distUnit}.") },
            confirmButton = { TextButton(onClick = { app.recorder.acceptOffer() }) { Text("Resume") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { app.recorder.discardOffer() }) { Text("Discard") }
                    TextButton(onClick = { app.recorder.finishOffer() }) { Text("Save & finish") }
                }
            },
        )
    } else {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Continue the live meter?") },
            text = { Text("It stopped $ago at ${Fmt.dist(o.session.distanceM)} ${Fmt.distUnit}.") },
            confirmButton = { TextButton(onClick = { app.recorder.acceptOffer() }) { Text("Continue") } },
            dismissButton = { TextButton(onClick = { app.recorder.discardOffer() }) { Text("Start fresh") } },
        )
    }
}

/** Shows "Resumed: 12 min gap" once after an automatic resume. */
@Composable
fun ResumedNotice() {
    val app = LocalAppContainer.current
    val context = LocalContext.current
    val gap by app.recorder.resumed.collectAsStateWithLifecycle()
    LaunchedEffect(gap) {
        val g = gap ?: return@LaunchedEffect
        Toast.makeText(context, "Resumed: ${Fmt.gap(g)} gap", Toast.LENGTH_LONG).show()
        app.recorder.clearResumedNotice()
    }
}
