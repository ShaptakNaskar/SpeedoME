package com.sappy.speedome.gauges

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb

/**
 * GPU effects (docs/plan.md §9): backlight glow, glass reflection and numeral bloom. They use AGSL
 * [RuntimeShader]s on Android 13+ when [ThemeOptions.shaders] is on, and gradients otherwise. The
 * shaders add what gradients can't: dithering (no banding in dark glows) and curved glass highlights.
 */
object Effects {
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    val available: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun enabled(options: ThemeOptions) = options.shaders && available

    /** Exponential falloff with ±½ LSB noise so dim glows on black don't band. */
    private const val GLOW = """
        uniform float2 center;
        uniform float radius;
        uniform float intensity;
        layout(color) uniform half4 tint;
        half4 main(float2 p) {
            float d = length(p - center) / radius;
            float g = exp(-d * d * 3.2) * intensity;
            float n = fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453) - 0.5;
            g = clamp(g + n / 255.0, 0.0, 1.0);
            return tint * half(g);
        }
    """

    /** Domed glass: a crescent highlight along the upper-left rim, a faint broad sheen, a dark lower rim. */
    private const val GLASS = """
        uniform float2 center;
        uniform float radius;
        half4 main(float2 p) {
            float2 v = (p - center) / radius;
            float d = length(v);
            if (d > 1.0) return half4(0);
            float facing = clamp(dot(normalize(v + float2(1e-4)), float2(-0.62, -0.78)), 0.0, 1.0);
            float rim = smoothstep(0.62, 0.93, d) * smoothstep(1.0, 0.95, d);
            float crescent = rim * pow(facing, 2.2) * 0.30;
            float2 s = v - float2(-0.35, -0.45);
            float sheen = exp(-dot(s, s) * 3.5) * 0.07;
            float shade = smoothstep(0.8, 1.0, d) * clamp(-dot(v, float2(-0.62, -0.78)), 0.0, 1.0) * 0.25;
            float w = crescent + sheen;
            half hw = half(w);
            return half4(hw, hw, hw, hw + half(shade));
        }
    """

    private val brushes = HashMap<Any, Brush>()
    private val halos = HashMap<Int, Paint>()

    /** Brushes are cached by their inputs; a gauge only ever uses a handful. */
    internal fun brush(key: Any, build: () -> Brush): Brush {
        if (brushes.size > 64) brushes.clear()
        return brushes.getOrPut(key, build)
    }

    internal fun halo(paint: Paint): Paint = halos.getOrPut(System.identityHashCode(paint)) { Paint(paint) }.apply {
        typeface = paint.typeface
        fontVariationSettings = paint.fontVariationSettings
        textSkewX = paint.textSkewX
    }

    @RequiresApi(33)
    internal fun glowBrush(center: Offset, radius: Float, tint: Color, intensity: Float): Brush {
        val s = RuntimeShader(GLOW)
        s.setFloatUniform("center", center.x, center.y)
        s.setFloatUniform("radius", radius)
        s.setFloatUniform("intensity", intensity)
        s.setColorUniform("tint", tint.toArgb())
        return ShaderBrush(s)
    }

    @RequiresApi(33)
    internal fun glassBrush(center: Offset, radius: Float): Brush {
        val s = RuntimeShader(GLASS)
        s.setFloatUniform("center", center.x, center.y)
        s.setFloatUniform("radius", radius)
        return ShaderBrush(s)
    }
}

/** A soft light source (backlight, sun, halo) centred on [center]. */
fun DrawScope.backlightGlow(center: Offset, radius: Float, tint: Color, intensity: Float, options: ThemeOptions) {
    val on = Effects.enabled(options)
    val brush = Effects.brush(listOf("glow", on, center, radius, tint, intensity)) {
        if (on) Effects.glowBrush(center, radius, tint, intensity) else
        Brush.radialGradient(
            0f to tint.copy(alpha = intensity), .45f to tint.copy(alpha = intensity * .35f), 1f to Color.Transparent,
            center = center, radius = radius,
        )
    }
    drawCircle(brush, radius, center)
}

/** The reflection on a round gauge glass of [radius]. */
fun DrawScope.glassReflection(center: Offset, radius: Float, options: ThemeOptions) {
    val on = Effects.enabled(options)
    val brush = Effects.brush(listOf("glass", on, center, radius)) {
        if (on) Effects.glassBrush(center, radius) else
        Brush.linearGradient(
            0f to Color.White.copy(alpha = .13f), .5f to Color.Transparent,
            start = Offset(center.x - radius, center.y - radius), end = Offset(center.x + radius * .2f, center.y + radius * .2f),
        )
    }
    drawCircle(brush, radius, center)
}

/**
 * Text that glows like a lit numeral: a blurred copy under the sharp one. With effects off it is
 * plain text (a stroked stand-in halo looked like a double outline on thin fonts).
 */
fun DrawScope.bloomText(
    s: String,
    x: Float,
    y: Float,
    paint: Paint,
    sizePx: Float,
    color: Color,
    options: ThemeOptions,
    strength: Float = 1f,
    align: Align = Align.CENTER,
) {
    if (Effects.enabled(options)) {
        val halo = Effects.halo(paint)
        if (halo.textSize != sizePx || halo.maskFilter == null) halo.maskFilter = BlurMaskFilter((sizePx * .16f).coerceAtLeast(.5f), BlurMaskFilter.Blur.NORMAL)
        text(s, x, y, halo, sizePx, color.copy(alpha = (color.alpha * .9f * strength).coerceIn(0f, 1f)), align)
    }
    text(s, x, y, paint, sizePx, color, align)
}
