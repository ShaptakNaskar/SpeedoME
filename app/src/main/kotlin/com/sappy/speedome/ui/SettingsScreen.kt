package com.sappy.speedome.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sappy.speedome.BuildConfig
import com.sappy.speedome.LocalAppContainer
import com.sappy.speedome.engine.Mode
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
    fun edit(t: (AppSettings) -> AppSettings) = scope.launch { app.settings.update(t) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, color = SpeedoColors.Text)
        Text(
            "More settings arrive together with the features they control.",
            color = SpeedoColors.Muted,
            fontSize = 14.sp,
        )
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
private fun DeveloperSection(s: AppSettings, edit: ((AppSettings) -> AppSettings) -> Unit) {
    HorizontalDivider(color = SpeedoColors.Raised)
    Text("DEVELOPER", color = SpeedoColors.Accent, fontSize = 12.sp, letterSpacing = 2.sp)
    ToggleRow("Simulator", "Drive with simulated GPS instead of the real receiver.", s.simulator) { on -> edit { it.copy(simulator = on) } }
    ToggleRow("Needle prediction", "Between fixes the needle follows speed plus acceleration.", s.predictNeedle) { on -> edit { it.copy(predictNeedle = on) } }
    Text("Mode", color = SpeedoColors.Text, fontSize = 16.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Mode.entries.forEach { m ->
            FilterChip(s.mode == m, onClick = { edit { it.copy(mode = m) } }, label = { Text(if (m == Mode.DRIVE) "Drive" else "Step") })
        }
    }
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
