package com.sappy.speedome.ios

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.sappy.speedome.engine.Engine
import com.sappy.speedome.engine.EngineSettings
import com.sappy.speedome.engine.EngineState
import com.sappy.speedome.engine.TrackView
import com.sappy.speedome.engine.sim.SimPreset
import com.sappy.speedome.engine.sim.SimRig
import com.sappy.speedome.engine.view
import kotlinx.coroutines.delay
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/**
 * Linux preview of the iOS screens, fed by the engine's simulator instead of CoreLocation
 * (docs/ios.md). No arguments: an iPhone-sized window with a live highway drive
 * (`./gradlew :ios:preview`). With a directory: PNG screenshots there (`./gradlew :ios:shots`).
 * Fonts are the desktop's, not San Francisco, so text widths differ slightly from the phone.
 */
fun main(args: Array<String>) {
    if (args.isEmpty()) preview() else shots(File(args[0]))
}

/** The shared engine driven by the simulator, on the simulator's clock. */
private class SimDrive(preset: SimPreset) {
    private val rig = SimRig().apply { this.preset = preset }
    private val settings = EngineSettings(mode = rig.mode)
    private var state = EngineState()

    fun run(seconds: Double) = rig.run(seconds) { e -> state = Engine.reduce(state, e, settings) }

    val view: TrackView get() = state.view(rig.nowNanos)
}

/** iPhone 15/16 screen in points. */
private val PHONE = DpSize(393.dp, 852.dp)

private fun preview() = application {
    val drive = remember { SimDrive(SimPreset.HIGHWAY) }
    var view by remember { mutableStateOf(drive.view) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(50)
            drive.run(0.05)
            view = drive.view
        }
    }
    Window(::exitApplication, rememberWindowState(size = PHONE), title = "SpeedoME iOS preview") {
        SpeedoScreen(view, denied = false)
    }
}

private fun shots(dir: File) {
    dir.mkdirs()
    val driving = SimDrive(SimPreset.HIGHWAY).apply { run(90.0) }.view
    val waiting = SimDrive(SimPreset.HIGHWAY).view
    shot(dir, "ios-drive", PHONE) { SpeedoScreen(driving, denied = false) }
    shot(dir, "ios-drive-landscape", DpSize(PHONE.height, PHONE.width)) { SpeedoScreen(driving, denied = false) }
    shot(dir, "ios-nofix", PHONE) { SpeedoScreen(waiting, denied = false) }
    shot(dir, "ios-denied", PHONE) { SpeedoScreen(waiting, denied = true) }
}

/** Renders [content] off screen at the iPhone's 3x scale, no window or display needed. */
private fun shot(dir: File, name: String, size: DpSize, content: @androidx.compose.runtime.Composable () -> Unit) {
    val scale = 3f
    val scene = ImageComposeScene((size.width.value * scale).toInt(), (size.height.value * scale).toInt(), Density(scale), content = content)
    val png = scene.render().encodeToData(EncodedImageFormat.PNG)?.bytes ?: error("PNG encoding failed")
    scene.close()
    File(dir, "$name.png").writeBytes(png)
    println("wrote ${File(dir, "$name.png")}")
}
