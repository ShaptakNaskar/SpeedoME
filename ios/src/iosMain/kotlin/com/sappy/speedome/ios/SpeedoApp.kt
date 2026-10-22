package com.sappy.speedome.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ComposeUIViewController
import com.sappy.speedome.engine.Engine
import com.sappy.speedome.engine.EngineEvent
import com.sappy.speedome.engine.EngineSettings
import com.sappy.speedome.engine.EngineState
import com.sappy.speedome.engine.GpsQuality
import com.sappy.speedome.engine.TickEvent
import com.sappy.speedome.engine.view
import kotlinx.coroutines.delay
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import kotlin.math.roundToInt
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

private val Amber = Color(0xFFE8A33D)
private val Muted = Color(0xFF7D8683)

/** First iOS build: the shared engine fed by CoreLocation, showing live speed. Gauges come next. */
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

    val v = state.view(Clocks.nowNanos())
    val (dot, gpsText) = when (v.quality) {
        GpsQuality.GOOD -> Color(0xFF3FD49B) to "GPS"
        GpsQuality.FALLBACK -> Amber to "GPS (weak)"
        GpsQuality.NONE -> Color(0xFFFF5A4F) to "NO FIX"
    }
    Column(
        Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        BasicText("● $gpsText · ${v.source}", style = TextStyle(color = dot, fontSize = 14.sp, letterSpacing = 1.5.sp))
        BasicText(
            "${(v.speedMps * 3.6).roundToInt()}",
            style = TextStyle(color = Color.White, fontSize = 120.sp, fontWeight = FontWeight.Bold),
        )
        BasicText("km/h", style = TextStyle(color = Muted, fontSize = 18.sp))
        BasicText(
            "${(v.distanceM / 100).roundToInt() / 10.0} km · max ${(v.maxMps * 3.6).roundToInt()}",
            style = TextStyle(color = Amber, fontSize = 16.sp),
        )
        if (denied) {
            BasicText(
                "Location is off for SpeedoME. Turn it on in Settings → Privacy → Location Services.",
                style = TextStyle(color = Color(0xFFFF5A4F), fontSize = 14.sp),
            )
        }
    }
}
