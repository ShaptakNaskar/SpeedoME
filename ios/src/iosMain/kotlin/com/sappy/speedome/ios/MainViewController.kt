package com.sappy.speedome.ios

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import com.sappy.speedome.engine.Engine
import com.sappy.speedome.engine.EngineEvent
import com.sappy.speedome.engine.EngineSettings
import com.sappy.speedome.engine.EngineState
import com.sappy.speedome.engine.TickEvent
import com.sappy.speedome.engine.view
import kotlinx.coroutines.delay
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import kotlin.time.Clock
import kotlin.time.TimeSource

/** Entry point for the Swift app (`MainViewControllerKt.MainViewController()`): the whole UI is Compose. */
fun MainViewController(): UIViewController = ComposeUIViewController { SpeedoApp() }

/** The engine's monotonic clock on iOS. Starts at 1 s so no event time is ever zero. */
object Clocks {
    private val start = TimeSource.Monotonic.markNow()
    fun nowNanos(): Long = start.elapsedNow().inWholeNanoseconds + 1_000_000_000L
    fun utcMillis(): Long = Clock.System.now().toEpochMilliseconds()
}

/** The shared engine fed by CoreLocation and a 1 Hz tick, shown on [SpeedoScreen]. */
@Composable
fun SpeedoApp() {
    var state by remember { mutableStateOf(EngineState()) }
    var denied by remember { mutableStateOf(false) }
    val settings = remember { EngineSettings() }
    fun submit(e: EngineEvent) {
        state = Engine.reduce(state, e, settings)
    }
    DisposableEffect(Unit) {
        val gps = IosGps(onFix = ::submit, onDenied = { denied = it })
        gps.start()
        UIApplication.sharedApplication.idleTimerDisabled = true // a speedometer keeps the screen on
        onDispose {
            gps.stop()
            UIApplication.sharedApplication.idleTimerDisabled = false
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            submit(TickEvent(Clocks.nowNanos(), Clocks.utcMillis()))
        }
    }
    SpeedoScreen(state.view(Clocks.nowNanos()), denied)
}
