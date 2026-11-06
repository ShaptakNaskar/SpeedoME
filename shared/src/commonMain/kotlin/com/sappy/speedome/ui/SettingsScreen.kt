package com.sappy.speedome.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sappy.speedome.AppInfo
import com.sappy.speedome.LocalAppContainer
import com.sappy.speedome.engine.Mode
import com.sappy.speedome.engine.ShrinkPolicy
import com.sappy.speedome.gauges.AverageDisplay
import com.sappy.speedome.gauges.DigitalColor
import com.sappy.speedome.gauges.SpeedUnit
import com.sappy.speedome.settings.Accent
import com.sappy.speedome.settings.AppSettings
import com.sappy.speedome.ui.theme.SpeedoColors
import com.sappy.speedome.ui.theme.readingFont
import com.sappy.speedome.ui.worlds.Worlds
import kotlinx.coroutines.launch

private const val TAPS_TO_UNLOCK = 7

@Composable
fun SettingsScreen() {
    val app = LocalAppContainer.current
    val actions = rememberPlatformActions()
    val scope = rememberCoroutineScope()
    val s by app.settings.state.collectAsStateWithLifecycle()
    var taps by rememberSaveable { mutableIntStateOf(0) }
    var licences by rememberSaveable { mutableStateOf(false) }
    val theme = SpeedThemes.byId(s.theme)
    val themeTitle = theme.title.lowercase().replaceFirstChar(Char::uppercase)
    val cockpitTitle = if (theme.id == SpeedThemes.MAP) Worlds.byId(s.worldId).title else themeTitle
    fun edit(t: (AppSettings) -> AppSettings) {
        scope.launch { app.settings.update(t) }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, color = SpeedoColors.Text)

        Card(colors = CardDefaults.cardColors(containerColor = SpeedoColors.Raised)) {
            HorizontalDivider(color = SpeedoColors.Accent, thickness = 2.dp)
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Current look", color = SpeedoColors.Accent, style = MaterialTheme.typography.labelLarge)
                Text(cockpitTitle, color = SpeedoColors.Text, style = MaterialTheme.typography.headlineMedium)
                Text(
                    "${if (s.mode == Mode.STEP) "Walk & run" else "Drive"} · ${s.units.label}",
                    color = SpeedoColors.Text, fontFamily = readingFont,
                )
            }
        }

        SettingsGroup("Mode & units", "${if (s.mode == Mode.STEP) "Walk & run" else "Drive"} · ${s.units.label}") {
            Choices(listOf("Drive" to Mode.DRIVE, "Walk & run" to Mode.STEP), s.mode, title = "Activity") { v -> edit { it.copy(mode = v) } }
            Text(
                if (s.mode == Mode.STEP) {
                    "Counts steps, shows pace and cadence, and uses your steps for speed when GPS is weak. The dial starts at 0–10 ${s.units.label}."
                } else {
                    "Speed from GPS, dial from 0–20 ${s.units.label} upward."
                },
                color = SpeedoColors.Muted, fontSize = 14.sp, fontFamily = readingFont,
            )

            Choices(listOf("km/h" to SpeedUnit.KMH, "mph" to SpeedUnit.MPH), s.units, title = "Units") { v -> edit { it.copy(units = v) } }
        }

        SettingsGroup("Appearance", cockpitTitle, initiallyExpanded = true) {
            Choices(SpeedThemes.all.map { it.title.lowercase().replaceFirstChar(Char::uppercase) to it.id }, theme.id, title = "Speed screen") { id -> edit { it.copy(theme = id) } }
            if (theme.id == "retro") Choices(listOf("Black dial" to false, "Cream dial" to true), s.retroCream, title = "Retro") { v -> edit { it.copy(retroCream = v) } }
            if (theme.id == "digital") Choices(
                listOf("VFD cyan" to DigitalColor.VFD, "LED red" to DigitalColor.LED, "LCD amber" to DigitalColor.LCD), s.digital, title = "Digital",
            ) { v -> edit { it.copy(digital = v) } }
            if (theme.id == "night") {
                Choices(listOf(60, 80, 100, 120, 140, 160).map { "$it" to it }, s.nightFocusKmh, title = "Lit up to (km/h${if (s.units == SpeedUnit.MPH) ", shown as nearest mph" else ""})") { v -> edit { it.copy(nightFocusKmh = v) } }
                Choices(listOf(200, 240, 260).map { "0–$it" to it }, s.nightMaxKmh, title = "Dial range (km/h)") { v -> edit { it.copy(nightMaxKmh = v) } }
                Choices(listOf("Dim" to .45f, "Mid" to .7f, "Full" to 1f), s.nightBrightness, title = "Brightness") { v -> edit { it.copy(nightBrightness = v) } }
            }
            if (theme.id == SpeedThemes.MAP) {
                Choices(Worlds.all.map { it.title to it.id }, s.worldId, title = "Map world") { v -> edit { it.copy(worldId = v) } }
                Choices(listOf("Travel direction up" to false, "North up" to true), s.mapNorthUp, title = "Map") { v -> edit { it.copy(mapNorthUp = v) } }
            }
            if (theme.id == "modern") Choices(Accent.entries.map { it.label to it }, s.accent, title = "Accent") { v -> edit { it.copy(accent = v) } }
        }

        SettingsGroup("Display", "Stats, instruments & effects") {
            Choices(
                listOf("Both averages" to AverageDisplay.BOTH, "Moving avg + time" to AverageDisplay.MOVING, "Avg + time" to AverageDisplay.OVERALL), s.average,
                title = "Map card statistics",
            ) { v -> edit { it.copy(average = v) } }
            ToggleRow("Nerd strip", "Satellites, accuracy, position and altitude under any gauge.", s.nerdStrip) { on -> edit { it.copy(nerdStrip = on) } }
            ToggleRow("Heading", "Compass heading under the gauge.", s.showHeading) { on -> edit { it.copy(showHeading = on) } }
            ToggleRow("G-force", "Sideways and forward G under the gauge.", s.showGForce) { on -> edit { it.copy(showGForce = on) } }
            ToggleRow(
                "GPU effects",
                if (com.sappy.speedome.gauges.Effects.available) "Glass reflections, backlight glow and numeral bloom." else "Needs Android 13; simpler gradients are used on this phone.",
                s.gpuEffects && com.sappy.speedome.gauges.Effects.available,
                enabled = com.sappy.speedome.gauges.Effects.available,
            ) { on -> if (com.sappy.speedome.gauges.Effects.available) edit { it.copy(gpuEffects = on) } }
            ToggleRow("Startup sweep", "The needle sweeps to the top and back once when the app starts.", s.startupSweep) { on -> edit { it.copy(startupSweep = on) } }
        }

        SettingsGroup("Tracking & dial range", "Background recording & scale") {
            Text(
                "The live meter runs while SpeedoME is open and resets when you leave it. Press Record to keep going in the background.",
                color = SpeedoColors.Muted, fontSize = 14.sp, fontFamily = readingFont,
            )
            if (hasReliabilityScreen) {
                val nav = LocalNavigator.current
                LinkRow("Background reliability", "Keep recording when the screen is off or Android closes apps.") { nav.openReliability() }
            }

            HorizontalDivider(color = SpeedoColors.Muted.copy(alpha = .2f))
            Choices(
                listOf("With delay" to ShrinkPolicy.WITH_DELAY, "Only grow" to ShrinkPolicy.ONLY_GROW, "Immediate" to ShrinkPolicy.IMMEDIATE, "Off" to ShrinkPolicy.OFF),
                s.shrink, title = "Shrink back",
            ) { v -> edit { it.copy(shrink = v) } }
            Text(
                when (s.shrink) {
                    ShrinkPolicy.WITH_DELAY -> "The dial grows as you speed up and shrinks one step after 15 s well below the smaller range."
                    ShrinkPolicy.ONLY_GROW -> "The dial only grows during a session; Reset or a new trip starts it small again."
                    ShrinkPolicy.IMMEDIATE -> "The dial always uses the smallest range that fits your speed."
                    ShrinkPolicy.OFF -> "A fixed dial. Step mode always uses 0–20 ${s.units.label}."
                },
                color = SpeedoColors.Muted, fontSize = 14.sp, fontFamily = readingFont,
            )
            if (s.shrink == ShrinkPolicy.OFF) {
                Choices(listOf(60, 80, 120, 160, 200, 260).map { "0–$it" to it }, s.fixedKmh, title = "Fixed dial (km/h)") { v -> edit { it.copy(fixedKmh = v) } }
            }
            if (s.speedLimit != null) {
                Text(
                    "While a speed limit is set, dials and bars stay fixed at 25 % over it instead.",
                    color = SpeedoColors.Accent, fontSize = 14.sp, fontFamily = readingFont,
                )
            }
        }

        SettingsGroup("About SpeedoME", "Privacy, licences & source") {
            LinkRow("Privacy policy", "Your trips stay on your phone. Map tiles are the only network use.") { actions.openUrl("$REPO/blob/main/docs/privacy.md") }
            LinkRow("Open-source licences", "MapLibre, OpenStreetMap data, Waypoint styles, fonts and AndroidX.") { licences = true }
            LinkRow("Source code", "GPL-3.0 on GitHub.") { actions.openUrl(REPO) }

            HorizontalDivider(color = SpeedoColors.Muted.copy(alpha = .2f))
            Column(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) {
                    if (s.devMode) {
                        actions.toast("Developer options are already on")
                    } else if (++taps >= TAPS_TO_UNLOCK) {
                        taps = 0
                        edit { it.copy(devMode = true) }
                        actions.toast("Developer options on")
                    }
                }.padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text("Version", color = SpeedoColors.Text, fontSize = 16.sp)
                Text("SpeedoME ${AppInfo.versionName}", color = SpeedoColors.Muted, fontSize = 14.sp, fontFamily = readingFont)
            }
        }
        if (s.devMode) SettingsGroup("Developer", "Simulator & diagnostics") { DeveloperSection(s, ::edit) }
    }
    if (licences) LicencesDialog { licences = false }
}

private const val REPO = "https://github.com/ShaptakNaskar/SpeedoME"

@Composable
private fun LinkRow(title: String, body: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = SpeedoColors.Text, fontSize = 16.sp)
            Text(body, color = SpeedoColors.Muted, fontSize = 14.sp, fontFamily = readingFont)
        }
        Text("›", color = SpeedoColors.Accent, fontSize = 24.sp, modifier = Modifier.clearAndSetSemantics {})
    }
}

@Composable
private fun LicencesDialog(onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open-source licences") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    "SpeedoME" to "GNU General Public License v3.0 or later.",
                    "MapLibre Native" to "BSD 2-Clause. © MapLibre contributors, Mapbox.",
                    "Map data" to "© OpenStreetMap contributors (ODbL). Tiles by OpenFreeMap, schema © OpenMapTiles.",
                    "World map styles" to "From Waypoint (github.com/the-geeky-couldron/waypoint). Copyright (c) 2026 Waypoint contributors, MIT License; the full notice ships with the styles. Lettering changed to Noto Sans.",
                    "Elevation (hill shading on some worlds)" to "Terrain Tiles on the AWS Open Data Registry: Mapzen, with SRTM, GMTED, ETOPO1 and other sources.",
                    "Fonts" to "Oswald, Outfit, B612 Mono, Barlow Semi Condensed, Rye, VT323, Silkscreen, Orbitron, Cinzel, Courier Prime, and Noto Sans on the maps: SIL Open Font License 1.1.",
                    "AndroidX, Jetpack Compose, Kotlin, kotlinx" to "Apache License 2.0.",
                ).forEach { (name, text) ->
                    Column {
                        Text(name, color = SpeedoColors.Text, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        Text(text, color = SpeedoColors.Muted, fontSize = 14.sp, fontFamily = readingFont)
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun SettingsGroup(
    title: String,
    summary: String,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    Card(colors = CardDefaults.cardColors(containerColor = SpeedoColors.Raised)) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 72.dp)
                    .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                    .clickable(role = Role.Button) { expanded = !expanded }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, color = SpeedoColors.Text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    Text(summary, color = SpeedoColors.Muted, fontSize = 14.sp, fontFamily = readingFont)
                }
                Text(if (expanded) "−" else "+", color = SpeedoColors.Accent, fontSize = 24.sp, modifier = Modifier.clearAndSetSemantics {})
            }
            if (expanded) {
                HorizontalDivider(color = SpeedoColors.Muted.copy(alpha = .2f))
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
            }
        }
    }
}

@Composable
private fun <T> Choices(options: List<Pair<String, T>>, selected: T, title: String, onSelect: (T) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    LinkRow(title, options.firstOrNull { it.second == selected }?.first ?: selected.toString()) { open = true }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
                options.forEach { (label, value) ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp)
                            .selectable(selected == value, role = Role.RadioButton) { onSelect(value); open = false }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(selected = selected == value, onClick = null)
                        Text(label, color = SpeedoColors.Text, fontFamily = readingFont, modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
    )
}

@Composable
private fun DeveloperSection(s: AppSettings, edit: ((AppSettings) -> AppSettings) -> Unit) {
    ToggleRow("Simulator", "Drive with simulated GPS instead of the real receiver.", s.simulator) { on -> edit { it.copy(simulator = on) } }
    ToggleRow("Raw GPS log", "Writes every raw fix and satellite count to Android/data/…/files/rawlogs for replay tests.", s.rawLog) { on -> edit { it.copy(rawLog = on) } }
    ToggleRow("Needle prediction", "Between fixes the needle follows speed plus acceleration.", s.predictNeedle) { on -> edit { it.copy(predictNeedle = on) } }
    OutlinedButton(modifier = Modifier.heightIn(min = 48.dp), onClick = { edit { it.copy(devMode = false, simulator = false) } }) { Text("Turn off developer options") }
}

@Composable
private fun ToggleRow(title: String, body: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = SpeedoColors.Text, fontSize = 16.sp)
            Text(body, color = SpeedoColors.Muted, fontSize = 14.sp, fontFamily = readingFont)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
