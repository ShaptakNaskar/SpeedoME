package com.sappy.speedome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sappy.speedome.ui.theme.SpeedoColors

enum class Tab(val label: String, val icon: ImageVector) {
    Speed("Speed", TabIcons.Speed),
    Trips("Trips", TabIcons.Trips),
    Settings("Settings", TabIcons.Settings),
}

@Composable
fun SpeedoApp() {
    var tab by rememberSaveable { mutableStateOf(Tab.Speed) }
    Column(
        Modifier
            .fillMaxSize()
            .background(SpeedoColors.Background),
    ) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .statusBarsPadding(),
        ) {
            when (tab) {
                Tab.Speed -> SpeedScreen()
                Tab.Trips -> TripsScreen()
                Tab.Settings -> SettingsScreen()
            }
        }
        TabBar(selected = tab, onSelect = { tab = it })
    }
}

@Composable
private fun TabBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
        HorizontalDivider(color = SpeedoColors.Raised)
        Row(Modifier.fillMaxWidth().height(64.dp).selectableGroup()) {
            Tab.entries.forEach { t ->
                val on = t == selected
                val tint = if (on) SpeedoColors.Accent else SpeedoColors.Muted
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .selectable(selected = on, onClick = { onSelect(t) }, role = Role.Tab),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(t.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                    Text(t.label, color = tint, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun TripsScreen() {
    EmptyState(title = "No trips yet", body = "Press Record on the Speed tab and your trips will be listed here.")
}

@Composable
private fun EmptyState(title: String, body: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = SpeedoColors.Text)
        Spacer(Modifier.height(8.dp))
        Caption(body)
    }
}

@Composable
fun Caption(text: String) {
    Text(
        text,
        color = SpeedoColors.Muted,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(max = 320.dp),
    )
}
