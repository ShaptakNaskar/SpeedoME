package com.sappy.speedome.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sappy.speedome.LocalAppContainer
import com.sappy.speedome.data.SessionEntity
import com.sappy.speedome.engine.Command
import com.sappy.speedome.engine.SessionKind
import com.sappy.speedome.engine.TrackView
import com.sappy.speedome.gauges.LIMIT_RED
import com.sappy.speedome.ui.theme.SpeedoColors
import kotlin.math.roundToInt

/**
 * Record / Pause / Resume / Stop / Reset, as in the Theme Lab footer, then Target, Limit and Drive/Walk.
 * When the row doesn't fit (a narrow screen or large text), those three drop their labels.
 */
@Composable
fun SessionControls(v: TrackView, onTarget: () -> Unit, onLimit: () -> Unit) {
    SubcomposeLayout(Modifier.fillMaxWidth()) { constraints ->
        val labelled = subcompose(true) { ControlsRow(v, onTarget, onLimit, labels = true) }.single()
        val fits = labelled.maxIntrinsicWidth(constraints.maxHeight) <= constraints.maxWidth
        val row = if (fits) labelled else subcompose(false) { ControlsRow(v, onTarget, onLimit, labels = false) }.single()
        val p = row.measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(constraints.maxWidth, p.height) { p.place((constraints.maxWidth - p.width) / 2, 0) }
    }
}

@Composable
private fun ControlsRow(v: TrackView, onTarget: () -> Unit, onLimit: () -> Unit, labels: Boolean) {
    val app = LocalAppContainer.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        when {
            v.sessionKind == SessionKind.LIVE -> {
                Pill("● RECORD", Color(0xFFFF5B4E), "Record trip") { app.recorder.startTrip() }
                Pill("RESET", SpeedoColors.Text) { app.recorder.reset() }
            }
            v.paused -> {
                Pill("RESUME", SpeedoColors.Accent) { app.tracking.command(Command.Resume) }
                Pill("■ STOP", SpeedoColors.Text, "Stop trip") { app.recorder.stopTrip() }
            }
            else -> {
                Pill("PAUSE", SpeedoColors.Text) { app.tracking.command(Command.Pause) }
                Pill("■ STOP", SpeedoColors.Text, "Stop trip") { app.recorder.stopTrip() }
            }
        }
        val target = if (v.target != null) "Edit target" else "Set target"
        IconButtonLabeled(TabIcons.Target, "TARGET".takeIf { labels }, v.target != null, target, onTarget)
        LimitButton(labels, onLimit)
        ModeButton(labels)
    }
}

@Composable
private fun ModeButton(labels: Boolean) {
    val app = LocalAppContainer.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val s by app.settings.state.collectAsStateWithLifecycle()
    val walk = s.mode == com.sappy.speedome.engine.Mode.STEP
    IconButtonLabeled(
        if (walk) TabIcons.Walk else TabIcons.Car, (if (walk) "WALK" else "DRIVE").takeIf { labels }, walk,
        if (walk) "Walk and run mode. Switch to drive" else "Drive mode. Switch to walk and run",
    ) {
        scope.launch {
            app.settings.update { it.copy(mode = if (walk) com.sappy.speedome.engine.Mode.DRIVE else com.sappy.speedome.engine.Mode.STEP) }
        }
    }
}

/** A tiny road sign: the limit in a red ring when one is set, the slashed "end of limit" sign when not. */
@Composable
private fun LimitButton(labels: Boolean, onClick: () -> Unit) {
    val app = LocalAppContainer.current
    val s by app.settings.state.collectAsStateWithLifecycle()
    val limit = s.speedLimit?.let { (it * s.engine.unitsPerMps).roundToInt() }
    val description = if (limit != null) "Speed limit $limit ${s.units.label}. Change" else "Set a speed limit"
    LabeledButton("LIMIT".takeIf { labels }, limit != null, description, onClick) { tint ->
        if (limit == null) {
            androidx.compose.material3.Icon(TabIcons.NoLimit, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        } else {
            // Sized in dp like the other icons, so large fonts can't push the number out of the sign.
            val size = with(LocalDensity.current) { (if (limit >= 100) 7.5.dp else 9.5.dp).toSp() }
            Box(Modifier.size(22.dp).border(2.5.dp, LIMIT_RED, CircleShape), contentAlignment = Alignment.Center) {
                Text("$limit", color = SpeedoColors.Text, fontSize = size, lineHeight = size, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun IconButtonLabeled(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String?,
    active: Boolean,
    description: String,
    onClick: () -> Unit,
) = LabeledButton(label, active, description, onClick) { tint ->
    androidx.compose.material3.Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
}

/** An icon over a small label (or the icon alone, 48 dp tall, when [label] is null), amber while active. */
@Composable
private fun LabeledButton(label: String?, active: Boolean, description: String, onClick: () -> Unit, icon: @Composable (tint: Color) -> Unit) {
    val color = if (active) SpeedoColors.Accent else SpeedoColors.Text
    Column(
        Modifier
            .widthIn(min = 44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = 8.dp, vertical = if (label == null) 13.dp else 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon(color)
        if (label != null) Text(label, color = color, fontSize = 9.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Pill(label: String, color: Color, description: String? = null, onClick: () -> Unit) {
    Text(
        label,
        color = color,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.5.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.07f))
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .padding(horizontal = 14.dp, vertical = 15.dp), // 48 dp touch target
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

/** Asked on launch when an unfinished trip is 30 min or older. */
@Composable
fun ResumeOfferDialog() {
    val app = LocalAppContainer.current
    val offer by app.recorder.offer.collectAsStateWithLifecycle()
    val o = offer ?: return
    AlertDialog(
        onDismissRequest = { },
        title = { Text("Unfinished trip") },
        text = { Text("SpeedoME stopped ${Fmt.ago(o.gapMillis)} during a trip of ${Fmt.dist(o.session.distanceM)} ${Fmt.distUnit}.") },
        confirmButton = { TextButton(onClick = { app.recorder.acceptOffer() }) { Text("Resume") } },
        dismissButton = {
            Row {
                TextButton(onClick = { app.recorder.discardOffer() }) { Text("Discard") }
                TextButton(onClick = { app.recorder.finishOffer() }) { Text("Save & finish") }
            }
        },
    )
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
