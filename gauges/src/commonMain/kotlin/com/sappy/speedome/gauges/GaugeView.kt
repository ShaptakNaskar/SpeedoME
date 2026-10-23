package com.sappy.speedome.gauges

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Draws [theme] with the frame from [frame]. The provider is read inside the draw phase, so
 * per-frame needle motion only redraws; it never recomposes.
 */
@Composable
fun GaugeView(theme: GaugeTheme, frame: () -> GaugeFrame, modifier: Modifier = Modifier) {
    val assets = rememberGaugeAssets() ?: return Spacer(modifier) // fonts still loading
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
