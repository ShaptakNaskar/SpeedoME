package com.sappy.speedome.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sappy.speedome.LocalAppContainer
import com.sappy.speedome.gauges.SpeedUnit
import com.sappy.speedome.ui.theme.SpeedoColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Speed limit (docs/plan.md §6): a value in the current units with presets, and the vibration switch.
 * The limit is kept in m/s, so it stays the same speed if the units change.
 */
@Composable
fun LimitDialog(onDismiss: () -> Unit) {
    val app = LocalAppContainer.current
    val s = remember { app.settings.state.value }
    val perMps = s.engine.unitsPerMps
    val current = s.speedLimit?.let { (it * perMps).roundToInt() }
    var text by rememberSaveable { mutableStateOf(current?.toString() ?: "") }
    var vibrate by rememberSaveable { mutableStateOf(s.limitVibrate) }
    val value = text.toIntOrNull()?.takeIf { it in 5..999 }
    val presets = if (s.units == SpeedUnit.MPH) listOf(20, 30, 40, 50, 60, 70) else listOf(30, 50, 60, 80, 100, 120)

    fun save(limitMps: Float) {
        // The app scope, not the dialog's: the dialog leaves the screen before the write finishes.
        app.appScope.launch { app.settings.update { it.copy(speedLimitMps = limitMps, limitVibrate = vibrate) } }
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Speed limit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    text, { text = it.filter(Char::isDigit).take(3) },
                    label = { Text("Limit (${s.units.label})") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { p -> FilterChip(text == "$p", onClick = { text = "$p" }, label = { Text("$p") }) }
                }
                Row(
                    Modifier.fillMaxWidth().toggleable(vibrate, role = Role.Switch) { vibrate = it },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Vibrate", color = SpeedoColors.Text, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Switch(vibrate, onCheckedChange = null)
                }
                Text(
                    "Dials and bars stay fixed at 25\u00A0% over the limit, with the part above it in red. The needle and " +
                        "digits turn red over the last 10\u00A0% before it. With vibration on, the phone buzzes once at the " +
                        "limit, then pulses faster the further over you go.",
                    color = SpeedoColors.Muted, fontSize = 13.sp,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = value != null, onClick = { save((checkNotNull(value) / perMps).toFloat()) }) { Text("Set") }
        },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = { save(0f) }) { Text("Turn off") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
