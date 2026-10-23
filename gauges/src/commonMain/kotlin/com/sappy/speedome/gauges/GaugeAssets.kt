package com.sappy.speedome.gauges

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/** The bundled OFL typefaces (docs/plan.md §9). */
enum class GaugeTypeface { OSWALD, OUTFIT, B612, B612_BOLD, BARLOW_SEMI }

/**
 * A text paint: a bundled typeface at a variable-font weight, optionally skewed. Android uses
 * android.graphics.Paint; iOS and the desktop preview use Skia fonts (skikoMain).
 */
expect class GaugePaint

/** Makes paints for the bundled typefaces; each platform loads the fonts its own way. */
fun interface PaintFactory {
    fun create(face: GaugeTypeface, weight: Int, skewX: Float): GaugePaint
}

/** Fonts, cached text paints and a per-size memo for the themes. */
class GaugeAssets(private val factory: PaintFactory) {
    val oswald = GaugeTypeface.OSWALD
    val outfit = GaugeTypeface.OUTFIT
    val b612 = GaugeTypeface.B612
    val b612Bold = GaugeTypeface.B612_BOLD
    val barlowSemi = GaugeTypeface.BARLOW_SEMI

    private data class Key(val face: GaugeTypeface, val weight: Int, val skewX: Float)

    private val paints = HashMap<Key, GaugePaint>()
    private val memo = HashMap<String, Pair<Size, Any>>()

    /** A text paint for [face] at variable-font weight [weight] (0 = the font's default), skewed by [skewX]. */
    fun paint(face: GaugeTypeface, weight: Int = 0, skewX: Float = 0f): GaugePaint =
        paints.getOrPut(Key(face, weight, skewX)) { factory.create(face, weight, skewX) }

    /** Recomputes [build] only when the canvas size changes. */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> memo(key: String, size: Size, build: () -> T): T {
        val hit = memo[key]
        if (hit != null && hit.first == size) return hit.second as T
        return build().also { memo[key] = size to it }
    }
}

/** The assets for this platform, or null while fonts are still loading (Skia platforms only). */
@Composable
expect fun rememberGaugeAssets(): GaugeAssets?

enum class Align { LEFT, CENTER, RIGHT }

/**
 * Draws [text] with its visual centre at [y] (or its top at [y] when [top]), like canvas text with
 * textBaseline = middle. [spacingEm] is letter spacing in ems.
 */
expect fun DrawScope.text(
    text: String,
    x: Float,
    y: Float,
    paint: GaugePaint,
    sizePx: Float,
    color: Color,
    align: Align = Align.CENTER,
    spacingEm: Float = 0f,
    top: Boolean = false,
)
