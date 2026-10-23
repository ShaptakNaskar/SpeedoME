package com.sappy.speedome.gauges

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Typeface
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import kotlin.math.roundToInt

// Android side of the gauge toolkit: android.graphics text, AGSL shaders, bitmap mesh.

actual typealias GaugePaint = Paint

private fun androidAssets(context: Context): GaugeAssets {
    fun font(id: Int): Typeface = ResourcesCompat.getFont(context, id) ?: Typeface.DEFAULT
    val faces = mapOf(
        GaugeTypeface.OSWALD to font(R.font.oswald),
        GaugeTypeface.OUTFIT to font(R.font.outfit),
        GaugeTypeface.B612 to font(R.font.b612_mono),
        GaugeTypeface.B612_BOLD to font(R.font.b612_mono_bold),
        GaugeTypeface.BARLOW_SEMI to font(R.font.barlow_semi_condensed_semibold),
    )
    return GaugeAssets { face, weight, skewX ->
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = faces.getValue(face)
            if (weight > 0) fontVariationSettings = "'wght' $weight"
            textSkewX = skewX
        }
    }
}

@Composable
actual fun rememberGaugeAssets(): GaugeAssets? {
    val context = LocalContext.current
    return remember(context) { androidAssets(context.applicationContext) }
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

internal actual val shadersAvailable: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

@RequiresApi(33)
internal actual fun glowShaderBrush(center: Offset, radius: Float, tint: Color, intensity: Float): Brush {
    val s = RuntimeShader(Effects.GLOW)
    s.setFloatUniform("center", center.x, center.y)
    s.setFloatUniform("radius", radius)
    s.setFloatUniform("intensity", intensity)
    s.setColorUniform("tint", tint.toArgb())
    return ShaderBrush(s)
}

@RequiresApi(33)
internal actual fun glassShaderBrush(center: Offset, radius: Float): Brush {
    val s = RuntimeShader(Effects.GLASS)
    s.setFloatUniform("center", center.x, center.y)
    s.setFloatUniform("radius", radius)
    return ShaderBrush(s)
}

private val halos = HashMap<Int, Paint>()

internal actual fun DrawScope.haloText(s: String, x: Float, y: Float, paint: GaugePaint, sizePx: Float, color: Color, align: Align) {
    val halo = halos.getOrPut(System.identityHashCode(paint)) { Paint(paint) }.apply {
        typeface = paint.typeface
        fontVariationSettings = paint.fontVariationSettings
        textSkewX = paint.textSkewX
    }
    if (halo.textSize != sizePx || halo.maskFilter == null) halo.maskFilter = BlurMaskFilter((sizePx * .16f).coerceAtLeast(.5f), BlurMaskFilter.Blur.NORMAL)
    text(s, x, y, halo, sizePx, color, align)
}

internal actual fun meshBrush(density: Float): ShaderBrush {
    val n = (3 * density).roundToInt().coerceAtLeast(3)
    val bmp = createBitmap(n, n)
    val line = android.graphics.Color.argb(90, 0, 0, 0)
    val t = density.roundToInt().coerceAtLeast(1)
    for (x in 0 until n) for (y in 0 until n) if (x >= n - t || y >= n - t) bmp[x, y] = line
    val image: ImageBitmap = bmp.asImageBitmap()
    return ShaderBrush(ImageShader(image, TileMode.Repeated, TileMode.Repeated))
}
