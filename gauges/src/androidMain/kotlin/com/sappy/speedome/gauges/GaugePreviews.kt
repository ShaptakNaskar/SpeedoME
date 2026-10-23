package com.sappy.speedome.gauges

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

private val sampleFrame = GaugeFrame(
    needleKmh = 62f, readout = 62, rangeKmh = 80f, rangeFromKmh = 80, rangeToKmh = 80,
    stats = GaugeStats(distanceM = 12_400.0, avgMovingKmh = 41.0, avgOverallKmh = 33.0, maxKmh = 88.0, elapsedS = 1320.0),
)

@Preview(widthDp = 390, heightDp = 640)
@Composable
private fun RetroPreview() = GaugeView(RetroTheme, { sampleFrame }, Modifier.size(390.dp, 640.dp))

@Preview(widthDp = 390, heightDp = 640)
@Composable
private fun ModernPreview() = GaugeView(ModernTheme, { sampleFrame }, Modifier.size(390.dp, 640.dp))

@Preview(widthDp = 390, heightDp = 640)
@Composable
private fun DigitalPreview() = GaugeView(DigitalTheme, { sampleFrame }, Modifier.size(390.dp, 640.dp))
