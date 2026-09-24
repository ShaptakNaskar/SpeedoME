package com.sappy.speedome.ui

import android.content.res.Configuration
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sappy.speedome.ui.theme.SpeedoColors

/** App-level navigation that screens can trigger (e.g. the Speed tab's reliability card). */
class Navigator(val openReliability: () -> Unit)

val LocalNavigator = androidx.compose.runtime.staticCompositionLocalOf { Navigator {} }

enum class Tab(val label: String, val icon: ImageVector) {
    Speed("Speed", TabIcons.Speed),
    Trips("Trips", TabIcons.Trips),
    Settings("Settings", TabIcons.Settings),
}

@Composable
fun SpeedoApp() {
    var tab by rememberSaveable { mutableStateOf(Tab.Speed) }
    var reliability by rememberSaveable { mutableStateOf(false) }
    val navigator = androidx.compose.runtime.remember {
        Navigator {
            tab = Tab.Settings
            reliability = true
        }
    }
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
            androidx.compose.runtime.CompositionLocalProvider(LocalNavigator provides navigator) {
                when {
                    tab == Tab.Speed -> SpeedScreen()
                    tab == Tab.Trips -> com.sappy.speedome.ui.trips.TripsScreen()
                    reliability -> ReliabilityScreen(onBack = { reliability = false })
                    else -> SettingsScreen()
                }
            }
        }
        // In landscape the Speed tab is a full-screen car-mount display (as in the Theme Lab).
        val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (!(landscape && tab == Tab.Speed)) {
            TabBar(selected = tab, onSelect = {
                if (it == Tab.Settings && tab == Tab.Settings) reliability = false
                tab = it
            })
        }
    }
    // App-wide: resume decisions and the post-trip summary show on any tab.
    ResumeOfferDialog()
    ResumedNotice()
    TripSummarySheet()
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
fun EmptyState(title: String, body: String) {
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
