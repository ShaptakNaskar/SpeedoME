package com.sappy.speedome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sappy.speedome.gauges.warned
import com.sappy.speedome.tracking.RoutePoint
import com.sappy.speedome.ui.theme.SpeedoColors
import kotlin.math.abs

// The Map theme's platform-neutral parts, shared by the Android (MapLibre Android) and iOS
// (maplibre-compose) map pages.

/** The live speed card in the map's corner. */
@Composable
internal fun SpeedCard(driver: GaugeDriver, view: com.sappy.speedome.engine.TrackView, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xE6090B0B)).padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${driver.frame.readout}", color = driver.frame.warned(Color.White), fontSize = 40.sp, fontWeight = FontWeight.Bold, lineHeight = 40.sp)
            Text(Fmt.speedUnit, color = SpeedoColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
        }
        Text(
            "${Fmt.dist(view.distanceM)} ${Fmt.distUnit} · avg ${Fmt.decimal(Fmt.speed(view.avgOverallMps), 0)} · max ${Fmt.decimal(Fmt.speed(view.maxMps), 0)}",
            color = SpeedoColors.Text, fontSize = 12.sp,
        )
    }
}

/** Course over ground from the last two route points at least ~5 m apart, in degrees from north. */
internal fun courseOf(route: List<RoutePoint>): Double? {
    val b = route.lastOrNull() ?: return null
    val a = route.asReversed().firstOrNull { abs(it.lat - b.lat) + abs(it.lon - b.lon) > 5e-5 } ?: return null
    val k = kotlin.math.cos(b.lat * kotlin.math.PI / 180)
    return (kotlin.math.atan2((b.lon - a.lon) * k, b.lat - a.lat) * 180 / kotlin.math.PI + 360) % 360
}

/** Closer in when slow, further out on the motorway. */
internal fun zoomFor(kmh: Double): Double = when {
    kmh < 8 -> 17.0
    kmh > 120 -> 13.6
    else -> 17.0 - (kmh - 8) / 112 * 3.4
}
