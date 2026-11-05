package com.sappy.speedome.ui.worlds

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.sappy.speedome.LocalAppContainer
import com.sappy.speedome.gauges.GaugeView
import com.sappy.speedome.resources.Res
import com.sappy.speedome.settings.AppSettings
import com.sappy.speedome.ui.GaugeDriver
import com.sappy.speedome.ui.ArrowPicker
import com.sappy.speedome.ui.MapPage
import com.sappy.speedome.ui.PickOption
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

    BoxWithConstraints(modifier.background(world.palette.ground)) {
        val gaugeWidth = maxWidth * .36f
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    MapPage(driver, settings.mapNorthUp, Modifier.fillMaxSize(), world)
                    WorldPill(world, Modifier.align(Alignment.TopCenter)) { w -> scope.launch { app.settings.update { it.copy(worldId = w) } } }
                }
                key(world.id) { GaugeView(theme, { driver.frame }, Modifier.width(gaugeWidth).fillMaxHeight().padding(8.dp)) }
            }
        } else {
            val cardHeight = min(maxWidth * .44f, 180.dp)
            MapPage(driver, settings.mapNorthUp, Modifier.fillMaxSize(), world, insetBottom = cardHeight)
            WorldPill(world, Modifier.align(Alignment.TopCenter)) { w -> scope.launch { app.settings.update { it.copy(worldId = w) } } }
            key(world.id) {
                // The empty pointerInput keeps touches off the map behind the card, so a drag here scrolls the page.
                GaugeView(
                    theme, { driver.frame },
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(cardHeight).pointerInput(Unit) {}.padding(10.dp),
                )
            }
        }
    }
}

/**
 * ‹ WORLD ›: the arrows step through the worlds; tap the name for the list, or hold it and slide onto
 * a world (see [com.sappy.speedome.ui.ArrowPicker]). On a pill so it reads over any map.
 */
@Composable
private fun WorldPill(world: World, modifier: Modifier, onPick: (String) -> Unit) {
    val p = world.palette
    val options = remember { Worlds.all.map { w -> PickOption(w.id, w.title, listOf(w.palette.ground, w.palette.road, w.palette.route)) } }
    ArrowPicker(
        world.title, "world", options, world.id, onPick, ink = p.ink, titleWidth = 132.dp,
        modifier = modifier.padding(10.dp).clip(RoundedCornerShape(50)).background(p.panel.copy(alpha = .92f))
            .border(1.dp, p.accent.copy(alpha = .5f), RoundedCornerShape(50)),
    )
}
