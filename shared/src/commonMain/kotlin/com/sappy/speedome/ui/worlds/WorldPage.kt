package com.sappy.speedome.ui.worlds

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.sappy.speedome.LocalAppContainer
import com.sappy.speedome.gauges.GaugeView
import com.sappy.speedome.resources.Res
import com.sappy.speedome.settings.AppSettings
import com.sappy.speedome.ui.GaugeDriver
import com.sappy.speedome.ui.MapPage
import com.sappy.speedome.ui.theme.SpeedoColors
import kotlinx.coroutines.launch

/** The world's bundled MapLibre style document. */
suspend fun World.styleJson(): String = Res.readBytes(stylePath).decodeToString()

/**
 * The World page: the live map drawn as a game-inspired world, with that world's speedo docked on it
 * (under the map in portrait, beside it in landscape). The pill at the top picks the world.
 */
@Composable
fun WorldPage(driver: GaugeDriver, settings: AppSettings, modifier: Modifier = Modifier) {
    val app = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val world = Worlds.byId(settings.worldId)
    val theme = world.gauge
    var picking by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier.background(world.palette.ground)) {
        val gaugeWidth = maxWidth * .36f
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    MapPage(driver, settings.mapNorthUp, Modifier.fillMaxSize(), world)
                    WorldPill(world, Modifier.align(Alignment.TopCenter)) { picking = true }
                }
                key(world.id) { GaugeView(theme, { driver.frame }, Modifier.width(gaugeWidth).fillMaxHeight().padding(8.dp)) }
            }
        } else {
            val cardHeight = min(maxWidth * .44f, 180.dp)
            MapPage(driver, settings.mapNorthUp, Modifier.fillMaxSize(), world, insetBottom = cardHeight)
            WorldPill(world, Modifier.align(Alignment.TopCenter)) { picking = true }
            key(world.id) {
                GaugeView(theme, { driver.frame }, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(cardHeight).padding(10.dp))
            }
        }
    }
    if (picking) {
        WorldPicker(world, onDismiss = { picking = false }) { w ->
            picking = false
            scope.launch { app.settings.update { it.copy(worldId = w.id) } }
        }
    }
}

@Composable
private fun WorldPill(world: World, modifier: Modifier, onClick: () -> Unit) {
    val p = world.palette
    Text(
        "${world.title}  ▾",
        color = p.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp,
        modifier = modifier.padding(10.dp).clip(RoundedCornerShape(50)).background(p.panel.copy(alpha = .92f))
            .border(1.dp, p.accent.copy(alpha = .5f), RoundedCornerShape(50))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "World: ${world.title.lowercase()}. Change" }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorldPicker(current: World, onDismiss: () -> Unit, onPick: (World) -> Unit) {
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (Worlds.all.indexOf(current) - 2).coerceAtLeast(0))
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SpeedoColors.Raised) {
        Text("Worlds", color = SpeedoColors.Text, fontSize = 22.sp, modifier = Modifier.padding(start = 24.dp, bottom = 8.dp))
        LazyColumn(state = list, modifier = Modifier.fillMaxWidth().height(460.dp)) {
            items(Worlds.all, key = { it.id }) { w ->
                val p = w.palette
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(w) }
                        .background(if (w == current) Color.White.copy(alpha = .06f) else Color.Transparent)
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // A tiny map swatch: land, a road and the route.
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(p.ground)) {
                        Box(Modifier.align(Alignment.Center).fillMaxWidth().height(6.dp).background(p.road))
                        Box(Modifier.align(Alignment.Center).width(6.dp).fillMaxHeight().background(p.route))
                        Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(8.dp).clip(CircleShape).background(p.accent))
                    }
                    Text(w.title, color = if (w == current) SpeedoColors.Accent else SpeedoColors.Text, fontSize = 15.sp, letterSpacing = 1.5.sp)
                }
            }
        }
    }
}
