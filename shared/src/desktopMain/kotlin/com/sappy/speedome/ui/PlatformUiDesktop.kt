package com.sappy.speedome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sappy.speedome.LocalAppContainer
import com.sappy.speedome.data.PointEntity
import com.sappy.speedome.ui.theme.SpeedoColors
import com.sappy.speedome.ui.trips.RouteView

// The desktop preview's stand-ins: messages go to stdout, maps become the plain route drawing.

private object Actions : PlatformActions {
    override fun toast(message: String, long: Boolean) = println("toast: $message")
    override fun confirmHaptic() {}
    override fun openUrl(url: String) = println("open: $url")
    override suspend fun shareFile(fileName: String, mime: String, text: String) = println("share: $fileName (${text.length} chars)")
}

@Composable
actual fun rememberPlatformActions(): PlatformActions = Actions

@Composable
actual fun rememberSaveFile(mime: String): (fileName: String, content: () -> String) -> Unit = { name, _ -> println("save: $name") }

@Composable
actual fun rememberOpenTextFile(onOpened: (name: String, text: String) -> Unit): () -> Unit = {}

@Composable
actual fun rememberBatteryLow(): Boolean = false

@Composable
actual fun rememberAnimationsOff(): Boolean = false

private object AllowAll : PermissionsUi {
    override val canTrack = true

    @Composable
    override fun Cards(mode: com.sappy.speedome.engine.Mode, recording: Boolean) {}
}

@Composable
actual fun rememberPermissionsUi(): PermissionsUi = AllowAll

actual val hasReliabilityScreen: Boolean = false

@Composable
actual fun ReliabilityScreen(onBack: () -> Unit) {}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {}

/** No MapLibre on the desktop preview: the live route drawn plainly, with the speed card. */
@Composable
actual fun MapPage(driver: GaugeDriver, northUp: Boolean, modifier: Modifier) {
    val app = LocalAppContainer.current
    val route by app.liveRoute.points.collectAsStateWithLifecycle()
    val view = rememberTrackView(app.tracking.state, 500)
    val points = route.mapIndexed { i, p -> PointEntity(sessionId = 0, t = i.toLong(), segment = p.segment, lat = p.lat, lon = p.lon, speedMps = p.speedMps, hAcc = 5f, source = "") }
    Box(modifier.background(SpeedoColors.Background)) {
        RouteView(points, Modifier.fillMaxSize().padding(24.dp))
        SpeedCard(driver, view, Modifier.align(Alignment.BottomStart).padding(12.dp))
        Text("MAP (preview: no map tiles)", color = SpeedoColors.Muted, fontSize = 12.sp, modifier = Modifier.align(Alignment.TopStart).padding(14.dp))
    }
}

actual val hasSatelliteData: Boolean = true
