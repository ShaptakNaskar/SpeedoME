package com.sappy.speedome.gauges

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Draws [theme] with the frame from [frame]. The provider is read inside the draw phase, so
 * per-frame needle motion only redraws; it never recomposes.
 */
@Composable
fun GaugeView(theme: GaugeTheme, frame: () -> GaugeFrame, modifier: Modifier = Modifier) {
    val assets = rememberGaugeAssets()
    val currentFrame by rememberUpdatedState(frame)
    val key by remember(theme) { derivedStateOf { currentFrame().staticKey() } }
    Spacer(
        modifier.drawWithCache {
            val layer = obtainGraphicsLayer()
            val staticFrame = key.toFrame()
            layer.record { with(theme) { drawStatic(staticFrame, assets) } }
            onDrawBehind {
                drawLayer(layer)
                with(theme) { drawDynamic(currentFrame(), assets) }
            }
        },
    )
}

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
