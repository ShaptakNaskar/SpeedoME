package com.sappy.speedome.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sappy.speedome.LocalAppContainer
import com.sappy.speedome.engine.Command
import com.sappy.speedome.engine.TargetView
import com.sappy.speedome.ui.theme.SpeedoColors
import com.sappy.speedome.is24HourClock
import com.sappy.speedome.localHourMinute
import com.sappy.speedome.nextLocalOccurrence
import com.sappy.speedome.utcMillis

val TARGET_STRIP_HEIGHT = 44.dp // two lines when an arrive-by time is set

/**
 * Target progress under the gauge: a thin bar and the arrival line. Tapping it edits the target.
 * On arrival it buzzes once and flashes (docs/plan.md §6).
 */
@Composable
fun TargetStrip(t: TargetView, onEdit: () -> Unit) {
    val actions = rememberPlatformActions()
    val flash = remember { Animatable(0f) }
    LaunchedEffect(t.arrived) {
        if (t.arrived) {
            actions.confirmHaptic()
            repeat(3) {
                flash.animateTo(1f, tween(180))
                flash.animateTo(0f, tween(320))
            }
        }
    }
    val accent = if (t.arrived) SpeedoColors.Text else SpeedoColors.Accent
    Box(
        Modifier.fillMaxWidth().height(TARGET_STRIP_HEIGHT).background(androidx.compose.ui.graphics.Color.Black)
            .clickable(role = Role.Button, onClick = onEdit).semantics { contentDescription = "Edit target" },
    ) {
        Box(Modifier.fillMaxWidth().fillMaxHeight().background(SpeedoColors.Accent.copy(alpha = .35f * flash.value)))
        Box(Modifier.fillMaxWidth(t.progress).height(2.dp).background(accent))
        Text(
            Fmt.target(t).joinToString(" · ") { it.replace(' ', '\u00A0') },
            color = if (t.late) androidx.compose.ui.graphics.Color(0xFFFF5B4E) else accent,
            fontSize = 12.sp, lineHeight = 15.sp, letterSpacing = .5.sp, maxLines = 2, textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 12.dp),
        )
    }
}

/** Distance (km) plus an optional arrive-by time; the next occurrence of that time today or tomorrow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetDialog(current: TargetView?, onDismiss: () -> Unit) {
    val app = LocalAppContainer.current
    var km by rememberSaveable { mutableStateOf(current?.let { Fmt.decimal(it.targetM / 1000, 1) } ?: "") }
    var byOn by rememberSaveable { mutableStateOf(current?.arriveByUtc != null) }
    // The current target's time, or an hour from now rounded up to the quarter hour.
    val (initialH, initialM) = current?.arriveByUtc?.let(::localHourMinute)
        ?: localHourMinute(utcMillis() + 3_600_000).let { (h, m) -> (h * 60 + (m + 14) / 15 * 15).let { (it / 60 % 24) to (it % 60) } }
    val time = rememberTimePickerState(initialH, initialM, is24Hour = is24HourClock())
    val distanceM = km.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it < 100_000 }?.times(1000)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Target") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    km, { km = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(7) },
                    label = { Text("Distance from here (km)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("5", "10", "21.1", "42.2", "100").forEach { p -> FilterChip(km == p, onClick = { km = p }, label = { Text(p) }) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Arrive by", color = SpeedoColors.Text, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Switch(byOn, { byOn = it })
                }
                if (byOn) TimePicker(time)
            }
        },
        confirmButton = {
            TextButton(
                enabled = distanceM != null,
                onClick = {
                    val by = if (byOn) nextLocalOccurrence(time.hour, time.minute) else null
                    app.tracking.command(Command.SetTarget(checkNotNull(distanceM), by))
                    onDismiss()
                },
            ) { Text("Set") }
        },
        dismissButton = {
            Row {
                if (current != null) {
                    TextButton(onClick = {
                        app.tracking.command(Command.ClearTarget)
                        onDismiss()
                    }) { Text("Clear") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
