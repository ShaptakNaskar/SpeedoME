package com.sappy.speedome.gauges

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.res.ResourcesCompat

/** Bundled OFL typefaces (docs/plan.md §9) plus cached text paints and per-size memo. */
class GaugeAssets(context: Context) {
    val oswald: Typeface = font(context, R.font.oswald)
    val outfit: Typeface = font(context, R.font.outfit)
    val b612: Typeface = font(context, R.font.b612_mono)
    val b612Bold: Typeface = font(context, R.font.b612_mono_bold)
    val barlowSemi: Typeface = font(context, R.font.barlow_semi_condensed_semibold)
    val exoItalic: Typeface = font(context, R.font.exo2_italic)

    private val paints = HashMap<String, Paint>()
    private val memo = HashMap<String, Pair<Size, Any>>()

    /** A text paint for [typeface] at variable-font weight [weight] (0 = the font's default). */
    fun paint(typeface: Typeface, weight: Int = 0): Paint = paints.getOrPut("${System.identityHashCode(typeface)}/$weight") {
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            this.typeface = typeface
            if (weight > 0) fontVariationSettings = "'wght' $weight"
        }
    }

    /** Recomputes [build] only when the canvas size changes. */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> memo(key: String, size: Size, build: () -> T): T {
        val hit = memo[key]
        if (hit != null && hit.first == size) return hit.second as T
        return build().also { memo[key] = size to it }
    }

    private fun font(context: Context, id: Int): Typeface = ResourcesCompat.getFont(context, id) ?: Typeface.DEFAULT
}

@Composable
fun rememberGaugeAssets(): GaugeAssets {
    val context = LocalContext.current
    return remember(context) { GaugeAssets(context.applicationContext) }
}

enum class Align { LEFT, CENTER, RIGHT }

/**
 * Draws [text] with its visual centre at [y] (or its top at [y] when [top]), like canvas text with
 * textBaseline = middle. [spacingEm] is letter spacing in ems.
 */
fun DrawScope.text(
    text: String,
    x: Float,
    y: Float,
    paint: Paint,
    sizePx: Float,
    color: Color,
    align: Align = Align.CENTER,
    spacingEm: Float = 0f,
    top: Boolean = false,
) {
    paint.textSize = sizePx
    paint.color = color.toArgb()
    paint.letterSpacing = spacingEm
    paint.textAlign = when (align) {
        Align.LEFT -> Paint.Align.LEFT
        Align.CENTER -> Paint.Align.CENTER
        Align.RIGHT -> Paint.Align.RIGHT
    }
    val fm = paint.fontMetrics
    val baseline = if (top) y - fm.ascent else y - (fm.ascent + fm.descent) / 2
    drawContext.canvas.nativeCanvas.drawText(text, x, baseline, paint)
}
