package com.sappy.speedome.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sappy.speedome.BuildConfig
import com.sappy.speedome.LocalAppContainer
import com.sappy.speedome.engine.Mode
import com.sappy.speedome.engine.ShrinkPolicy
import com.sappy.speedome.gauges.AverageDisplay
import com.sappy.speedome.gauges.DigitalColor
import com.sappy.speedome.gauges.GaugeThemes
import com.sappy.speedome.settings.Accent
import com.sappy.speedome.settings.AppSettings
import com.sappy.speedome.ui.theme.SpeedoColors
import kotlinx.coroutines.launch

private const val TAPS_TO_UNLOCK = 7

@Composable
fun SettingsScreen() {
    val app = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by app.settings.state.collectAsStateWithLifecycle()
    var taps by remember { mutableIntStateOf(0) }
    fun edit(t: (AppSettings) -> AppSettings) {
        scope.launch { app.settings.update(t) }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, color = SpeedoColors.Text)

        Section("THEME")
        Choices(GaugeThemes.all.map { it.title.lowercase().replaceFirstChar(Char::uppercase) to it.id }, s.theme) { id -> edit { it.copy(theme = id) } }
        Choices(listOf("Black dial" to false, "Cream dial" to true), s.retroCream, title = "Retro") { v -> edit { it.copy(retroCream = v) } }
        Choices(
            listOf("VFD cyan" to DigitalColor.VFD, "LED red" to DigitalColor.LED, "LCD amber" to DigitalColor.LCD), s.digital, title = "Digital",
        ) { v -> edit { it.copy(digital = v) } }
        Text("Accent (Modern)", color = SpeedoColors.Text, fontSize = 15.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Accent.entries.forEach { a ->
                val on = s.accent == a
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(Color(a.argb))
                        .border(if (on) 3.dp else 0.dp, if (on) SpeedoColors.Text else Color.Transparent, CircleShape)
                        .selectable(on, role = Role.RadioButton) { edit { it.copy(accent = a) } }
                        .semantics { contentDescription = "${a.label} accent" },
                )
            }
        }

        Section("DISPLAY")
        Choices(
            listOf("Both averages" to AverageDisplay.BOTH, "Moving" to AverageDisplay.MOVING, "Overall" to AverageDisplay.OVERALL), s.average,
            title = "Average speed",
        ) { v -> edit { it.copy(average = v) } }
        ToggleRow("Startup sweep", "The needle sweeps to the top and back when the gauge appears.", s.startupSweep) { on -> edit { it.copy(startupSweep = on) } }

        Section("AUTO-RANGE")
        Choices(
            listOf("With delay" to ShrinkPolicy.WITH_DELAY, "Only grow" to ShrinkPolicy.ONLY_GROW, "Immediate" to ShrinkPolicy.IMMEDIATE, "Off" to ShrinkPolicy.OFF),
            s.shrink, title = "Shrink back",
        ) { v -> edit { it.copy(shrink = v) } }
        Text(
            when (s.shrink) {
                ShrinkPolicy.WITH_DELAY -> "The dial grows as you speed up and shrinks one step after 15 s well below the smaller range."
                ShrinkPolicy.ONLY_GROW -> "The dial only grows during a session; Reset or a new trip starts it small again."
                ShrinkPolicy.IMMEDIATE -> "The dial always uses the smallest range that fits your speed."
                ShrinkPolicy.OFF -> "A fixed dial. Step mode always uses 0–20 km/h."
            },
            color = SpeedoColors.Muted, fontSize = 13.sp,
        )
        if (s.shrink == ShrinkPolicy.OFF) {
            Choices(listOf(60, 80, 120, 160, 200, 260).map { "0–$it" to it }, s.fixedKmh, title = "Fixed dial") { v -> edit { it.copy(fixedKmh = v) } }
        }

        HorizontalDivider(color = SpeedoColors.Raised)
        Column(
            Modifier.fillMaxWidth().clickable {
                if (s.devMode) {
                    Toast.makeText(context, "Developer options are already on", Toast.LENGTH_SHORT).show()
                } else if (++taps >= TAPS_TO_UNLOCK) {
                    taps = 0
                    edit { it.copy(devMode = true) }
                    Toast.makeText(context, "Developer options on", Toast.LENGTH_SHORT).show()
                }
            }.padding(vertical = 8.dp),
        ) {
            Text("Version", color = SpeedoColors.Text, fontSize = 16.sp)
            Text("SpeedoME ${BuildConfig.VERSION_NAME}", color = SpeedoColors.Muted, fontSize = 14.sp)
        }
        if (s.devMode) DeveloperSection(s, ::edit)
    }
}

@Composable
private fun Section(title: String) {
    Text(title, color = SpeedoColors.Accent, fontSize = 12.sp, letterSpacing = 2.sp, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun <T> Choices(options: List<Pair<String, T>>, selected: T, title: String? = null, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (title != null) Text(title, color = SpeedoColors.Text, fontSize = 15.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (label, value) -> FilterChip(selected == value, onClick = { onSelect(value) }, label = { Text(label) }) }
        }
    }
}

@Composable
private fun DeveloperSection(s: AppSettings, edit: ((AppSettings) -> AppSettings) -> Unit) {
    Section("DEVELOPER")
    ToggleRow("Simulator", "Drive with simulated GPS instead of the real receiver.", s.simulator) { on -> edit { it.copy(simulator = on) } }
    ToggleRow("Needle prediction", "Between fixes the needle follows speed plus acceleration.", s.predictNeedle) { on -> edit { it.copy(predictNeedle = on) } }
    Choices(listOf("Drive" to Mode.DRIVE, "Step" to Mode.STEP), s.mode, title = "Mode") { v -> edit { it.copy(mode = v) } }
    OutlinedButton(onClick = { edit { it.copy(devMode = false, simulator = false) } }) { Text("Turn off developer options") }
}

@Composable
private fun ToggleRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = SpeedoColors.Text, fontSize = 16.sp)
            Text(body, color = SpeedoColors.Muted, fontSize = 13.sp)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
