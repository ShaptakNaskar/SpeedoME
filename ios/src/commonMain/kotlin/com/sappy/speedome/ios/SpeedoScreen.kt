package com.sappy.speedome.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sappy.speedome.engine.GpsQuality
import com.sappy.speedome.engine.SpeedSource
import com.sappy.speedome.engine.TrackView
import kotlin.math.roundToInt

private val Amber = Color(0xFFE8A33D)
private val Muted = Color(0xFF7D8683)

/**
 * The iOS speed screen. Shared code: the iPhone shows it fed by CoreLocation, and the Linux desktop
 * preview (`./gradlew :ios:preview`, `:ios:shots`) shows it fed by the engine's simulator.
 */
@Composable
fun SpeedoScreen(v: TrackView, denied: Boolean) {
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
        val source = if (v.source == SpeedSource.NONE) "" else " · ${v.source}"
        BasicText("● $gpsText$source", style = TextStyle(color = dot, fontSize = 14.sp, letterSpacing = 1.5.sp))
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
                style = TextStyle(color = Color(0xFFFF5A4F), fontSize = 14.sp, textAlign = TextAlign.Center),
            )
        }
    }
}
