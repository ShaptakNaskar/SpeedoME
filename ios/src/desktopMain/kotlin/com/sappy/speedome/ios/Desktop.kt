package com.sappy.speedome.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sappy.speedome.AppContainer
import com.sappy.speedome.DesktopPlatform
import com.sappy.speedome.LocalAppContainer
import com.sappy.speedome.engine.sim.SimPreset
import com.sappy.speedome.gauges.loadGaugeFonts
import com.sappy.speedome.ui.SettingsScreen
import com.sappy.speedome.ui.SpeedScreen
import com.sappy.speedome.ui.SpeedThemes
import com.sappy.speedome.ui.SpeedoApp
import com.sappy.speedome.ui.theme.SpeedoColors
import com.sappy.speedome.ui.theme.SpeedoTheme
import com.sappy.speedome.ui.trips.TripDetail
import com.sappy.speedome.ui.trips.TripsScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/**
 * Linux preview of the shared app as it runs on iOS (same Compose code; desktop fonts stand in for
 * San Francisco, and maps show the plain route drawing). The engine's simulator replaces GPS.
 * No arguments: an iPhone-sized window with the whole app (`./gradlew :ios:preview`). With a
 * directory: PNG screenshots of every screen there (`./gradlew :ios:shots`).
 */
fun main(args: Array<String>) {
    val app = AppContainer(DesktopPlatform())
    app.visible.value = true
    runBlocking {
        loadGaugeFonts()
        app.settings.update { it.copy(devMode = true, simulator = true, startupSweep = false) }
    }
    app.simulator.update { it.copy(preset = SimPreset.CITY) }
    if (args.isEmpty()) preview(app) else shots(app, File(args[0]))
}

/** iPhone 15/16 screen in points. */
private val PHONE = DpSize(393.dp, 852.dp)

private fun preview(app: AppContainer) = application {
    Window(::exitApplication, rememberWindowState(size = PHONE), title = "SpeedoME preview") {
        Root(app) { SpeedoApp() }
    }
}

@Composable
private fun Root(app: AppContainer, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppContainer provides app) {
        SpeedoTheme { Box(Modifier.fillMaxSize().background(SpeedoColors.Background)) { content() } }
    }
}

/** A lifecycle that is always resumed, for off-screen scenes (screens collect flows "while started"). */
private object Resumed : LifecycleOwner {
    override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
}

private fun shots(app: AppContainer, dir: File) {
    dir.mkdirs()
    dir.listFiles()?.forEach { it.delete() }
    // A short city drive, recorded, so trips and the live screens have data.
    Thread.sleep(3_000)
    app.recorder.startTrip()
    Thread.sleep(25_000)
    for (entry in SpeedThemes.all) {
        runBlocking { app.settings.update { it.copy(theme = entry.id) } }
        shot(dir, "speed-${entry.id}", PHONE, app) { SpeedScreen() }
    }
    runBlocking { app.settings.update { it.copy(theme = "retro") } }
    shot(dir, "speed-landscape", DpSize(PHONE.height, PHONE.width), app) { SpeedScreen() }
    app.recorder.stopTrip()
    val tripId = runBlocking { app.db.trips().trips().first { it.isNotEmpty() }.first().id }
    app.recorder.dismissSummary()
    shot(dir, "trips", PHONE, app) { TripsScreen() }
    shot(dir, "trip-detail", PHONE, app) { TripDetail(tripId, onBack = {}) }
    shot(dir, "settings", PHONE, app) { SettingsScreen() }
    shot(dir, "app", PHONE, app) { SpeedoApp() }
    System.exit(0) // the simulator and recorder keep coroutines alive
}

/** Renders [content] off screen at the iPhone's 3x scale, letting a second of frames and data settle. */
private fun shot(dir: File, name: String, size: DpSize, app: AppContainer, content: @Composable () -> Unit) {
    val scale = 3f
    val scene = ImageComposeScene((size.width.value * scale).toInt(), (size.height.value * scale).toInt(), Density(scale)) {
        CompositionLocalProvider(LocalLifecycleOwner provides Resumed) { Root(app, content) }
    }
    var t = 0L
    repeat(20) {
        scene.render(t)
        t += 50_000_000L
        Thread.sleep(50)
    }
    val png = scene.render(t).encodeToData(EncodedImageFormat.PNG)?.bytes ?: error("PNG encoding failed")
    scene.close()
    File(dir, "$name.png").writeBytes(png)
    println("wrote ${File(dir, "$name.png")}")
}
