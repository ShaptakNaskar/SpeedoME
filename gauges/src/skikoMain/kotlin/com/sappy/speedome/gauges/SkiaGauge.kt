package com.sappy.speedome.gauges

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeShader
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.sappy.speedome.gauges.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.skia.Data
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontEdging
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontVariation
import org.jetbrains.skia.MaskFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.TextLine
import org.jetbrains.skia.Typeface
import kotlin.math.roundToInt
import org.jetbrains.skia.Paint as SkPaint

// iOS and desktop side of the gauge toolkit: Skia fonts and SkSL shaders (same source as the AGSL).

actual class GaugePaint internal constructor(private val typeface: Typeface, private val skewX: Float) {
    private val fonts = HashMap<Float, Font>()

    /** Fonts are cached per pixel size; gauge sizes only change with the layout. */
    internal fun font(size: Float): Font {
        if (fonts.size > 48) fonts.clear()
        return fonts.getOrPut(size) {
            Font(typeface, size).apply {
                skewX = this@GaugePaint.skewX
                isSubpixel = true
                edging = FontEdging.ANTI_ALIAS
            }
        }
    }
}

private val fontFiles = mapOf(
    GaugeTypeface.OSWALD to "oswald.ttf",
    GaugeTypeface.OUTFIT to "outfit.ttf",
    GaugeTypeface.B612 to "b612_mono.ttf",
    GaugeTypeface.B612_BOLD to "b612_mono_bold.ttf",
    GaugeTypeface.BARLOW_SEMI to "barlow_semi_condensed_semibold.ttf",
)

private var loadedFaces: Map<GaugeTypeface, Typeface>? = null

/** Loads the bundled fonts once (they are the same files Android uses, from src/androidMain/res/font). */
@OptIn(ExperimentalResourceApi::class)
suspend fun loadGaugeFonts(): Map<GaugeTypeface, Typeface> {
    loadedFaces?.let { return it }
    val faces = fontFiles.mapValues { (_, file) ->
        FontMgr.default.makeFromData(Data.makeFromBytes(Res.readBytes("font/$file"))) ?: error("can't read font $file")
    }
    loadedFaces = faces
    return faces
}

private fun skiaAssets(faces: Map<GaugeTypeface, Typeface>) = GaugeAssets { face, weight, skewX ->
    val base = faces.getValue(face)
    GaugePaint(if (weight > 0) base.makeClone(arrayOf(FontVariation("wght", weight.toFloat()))) else base, skewX)
}

@Composable
actual fun rememberGaugeAssets(): GaugeAssets? {
    val faces by produceState(loadedFaces) { if (value == null) value = loadGaugeFonts() }
    return remember(faces) { faces?.let(::skiaAssets) }
}

private fun startX(x: Float, width: Float, align: Align) = when (align) {
    Align.LEFT -> x
    Align.CENTER -> x - width / 2
    Align.RIGHT -> x - width
}

private fun DrawScope.drawSkText(text: String, x: Float, y: Float, paint: GaugePaint, sizePx: Float, sk: SkPaint, align: Align, spacingEm: Float, top: Boolean) {
    val font = paint.font(sizePx)
    val m = font.metrics
    val baseline = if (top) y - m.ascent else y - (m.ascent + m.descent) / 2
    val canvas = drawContext.canvas.nativeCanvas
    val spacing = spacingEm * sizePx
    if (spacing == 0f) {
        val line = TextLine.make(text, font)
        canvas.drawTextLine(line, startX(x, line.width, align), baseline, sk)
        return
    }
    // Letter spacing like Android's: added to every glyph's advance, half on each side.
    val widths = text.map { font.measureTextWidth(it.toString()) }
    var cx = startX(x, widths.sum() + spacing * text.length, align) + spacing / 2
    text.forEachIndexed { i, ch ->
        canvas.drawString(ch.toString(), cx, baseline, font, sk)
        cx += widths[i] + spacing
    }
}

actual fun DrawScope.text(
    text: String,
    x: Float,
    y: Float,
    paint: GaugePaint,
    sizePx: Float,
    color: Color,
    align: Align,
    spacingEm: Float,
    top: Boolean,
) = drawSkText(text, x, y, paint, sizePx, SkPaint().apply { this.color = color.toArgb() }, align, spacingEm, top)

internal actual val shadersAvailable: Boolean get() = true

private val glowEffect by lazy { RuntimeEffect.makeForShader(Effects.GLOW) }
private val glassEffect by lazy { RuntimeEffect.makeForShader(Effects.GLASS) }

internal actual fun glowShaderBrush(center: Offset, radius: Float, tint: Color, intensity: Float): Brush {
    val b = RuntimeShaderBuilder(glowEffect)
    b.uniform("center", center.x, center.y)
    b.uniform("radius", radius)
    b.uniform("intensity", intensity)
    b.uniform("tint", tint.red, tint.green, tint.blue, tint.alpha)
    return ShaderBrush(b.makeShader().asComposeShader())
}

internal actual fun glassShaderBrush(center: Offset, radius: Float): Brush {
    val b = RuntimeShaderBuilder(glassEffect)
    b.uniform("center", center.x, center.y)
    b.uniform("radius", radius)
    return ShaderBrush(b.makeShader().asComposeShader())
}

internal actual fun DrawScope.haloText(s: String, x: Float, y: Float, paint: GaugePaint, sizePx: Float, color: Color, align: Align) {
    // Android's BlurMaskFilter takes a radius; Skia wants a sigma (radius ≈ 1.73 σ).
    val sigma = (sizePx * .16f).coerceAtLeast(.5f) * .57735f + .5f
    val sk = SkPaint().apply {
        this.color = color.toArgb()
        maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, sigma)
    }
    drawSkText(s, x, y, paint, sizePx, sk, align, 0f, false)
}

internal actual fun meshBrush(density: Float): ShaderBrush {
    val n = (3 * density).roundToInt().coerceAtLeast(3)
    val t = density.roundToInt().coerceAtLeast(1).toFloat()
    val image = ImageBitmap(n, n)
    val line = androidx.compose.ui.graphics.Paint().apply { color = Color(0, 0, 0, 90); isAntiAlias = false }
    Canvas(image).apply {
        drawRect(Rect(n - t, 0f, n.toFloat(), n.toFloat()), line)
        drawRect(Rect(0f, n - t, n - t, n.toFloat()), line)
    }
    return ShaderBrush(ImageShader(image, TileMode.Repeated, TileMode.Repeated))
}
