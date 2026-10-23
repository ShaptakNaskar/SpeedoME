package com.sappy.speedome.ui.trips

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sappy.speedome.data.PointEntity

/** No MapLibre on the desktop preview: the plain route drawing stands in. */
@Composable
actual fun TripMap(points: List<PointEntity>, modifier: Modifier) = RouteView(points, modifier)
