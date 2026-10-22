package com.sappy.speedome.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sappy.speedome.LocalAppContainer
import com.sappy.speedome.engine.SpeedSample
import com.sappy.speedome.gauges.warned
import com.sappy.speedome.ui.theme.SpeedoColors
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.roundToInt

/** The Graph page's windows: seconds and chip label. */
private val WINDOWS = listOf(30 to "30s", 60 to "1m", 120 to "2m", 300 to "5m", 1800 to "30m", 3600 to "60m", 7200 to "120m")

/** Samples further apart than this are a gap (the meter wasn't running), drawn as a break in the graph. */
private const val GAP_NANOS = 3_000_000_000L

/** Speed over a chosen window, drawn like a task manager's usage graph, under the live speed. */
@Composable
fun GraphPage(driver: GaugeDriver, windowS: Int, modifier: Modifier = Modifier) {
    val app = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val history by app.speedHistory.collectAsStateWithLifecycle()
    val frame = driver.frame
    val accent = frame.options.accent
    val endNanos = history.lastOrNull()?.tNanos ?: 0L
    val startNanos = endNanos - windowS * 1_000_000_000L
    val shown = remember(history, windowS) { history.filter { it.tNanos >= startNanos } }
    val max = shown.maxOfOrNull { Fmt.speed(it.mps.toDouble()) } ?: 0.0
    val avg = if (shown.isEmpty()) 0.0 else shown.sumOf { Fmt.speed(it.mps.toDouble()) } / shown.size
    val scale = niceScale(max)
    val over = overText(windowS)

    Column(modifier.background(SpeedoColors.Background).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${frame.readout}", color = frame.warned(Color.White), fontSize = 56.sp, fontWeight = FontWeight.Bold, lineHeight = 56.sp)
            Text(Fmt.speedUnit, color = SpeedoColors.Muted, fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp, bottom = 8.dp))
            Spacer(Modifier.weight(1f))
            Stat("MAX", max)
            Stat("AVG", avg)
        }
        Row(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
            Text("Speed over $over", color = SpeedoColors.Muted, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Text("$scale ${Fmt.speedUnit}", color = SpeedoColors.Muted, fontSize = 12.sp)
        }
        Canvas(
            Modifier.fillMaxWidth().weight(1f).semantics {
                contentDescription = "Speed graph over the last $over: max ${max.roundToInt()}, average ${avg.roundToInt()} ${Fmt.speedUnit}"
            },
        ) {
            val grid = accent.copy(alpha = .14f)
            for (i in 1 until 6) drawLine(grid, Offset(size.width * i / 6, 0f), Offset(size.width * i / 6, size.height))
            for (i in 1 until 5) drawLine(grid, Offset(0f, size.height * i / 5), Offset(size.width, size.height * i / 5))
            val line = Path()
            val fill = Path()
            var runStartX = 0f
            var prev: SpeedSample? = null
            var x = 0f
            for (p in shown) {
                x = (p.tNanos - startNanos).toFloat() / (endNanos - startNanos) * size.width
                val y = size.height * (1f - (Fmt.speed(p.mps.toDouble()) / scale).toFloat())
                if (prev == null || p.tNanos - prev.tNanos > GAP_NANOS) {
                    if (prev != null) fill.lineTo((prev.tNanos - startNanos).toFloat() / (endNanos - startNanos) * size.width, size.height)
                    line.moveTo(x, y)
                    fill.moveTo(x, size.height)
                    runStartX = x
                }
                line.lineTo(x, y)
                fill.lineTo(x, y)
                prev = p
            }
            if (prev != null) {
                fill.lineTo(x, size.height)
                fill.lineTo(runStartX, size.height)
            }
            drawPath(fill, accent.copy(alpha = .22f))
            drawPath(line, accent, style = Stroke(1.5.dp.toPx()))
            drawRect(accent.copy(alpha = .5f), style = Stroke(1.dp.toPx()))
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for ((s, label) in WINDOWS) {
                val selected = s == windowS
                Box(
                    Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (selected) accent.copy(alpha = .2f) else SpeedoColors.Raised)
                        .border(1.dp, if (selected) accent else Color.Transparent, RoundedCornerShape(10.dp))
                        .selectable(selected, role = Role.Tab) { scope.launch { app.settings.update { it.copy(graphWindowS = s) } } },
                    contentAlignment = Alignment.Center,
                ) { Text(label, color = if (selected) accent else SpeedoColors.Text, fontSize = 13.sp, maxLines = 1) }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: Double) {
    Column(Modifier.padding(start = 16.dp, bottom = 6.dp), horizontalAlignment = Alignment.End) {
        Text(label, color = SpeedoColors.Muted, fontSize = 11.sp, letterSpacing = 1.sp)
        Text("${value.roundToInt()}", color = SpeedoColors.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** The graph's top value: the window's max with a little headroom, rounded up to 10s (20s above 50), at least 20. */
private fun niceScale(max: Double): Int {
    val step = if (max <= 50) 10 else 20
    return (ceil(max * 1.05 / step) * step).toInt().coerceAtLeast(20)
}

private fun overText(s: Int) = when {
    s < 60 -> "$s seconds"
    s < 3600 -> (s / 60).let { if (it == 1) "1 minute" else "$it minutes" }
    else -> (s / 3600).let { if (it == 1) "1 hour" else "$it hours" }
}
