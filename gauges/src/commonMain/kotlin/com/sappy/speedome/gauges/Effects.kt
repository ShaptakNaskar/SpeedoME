package com.sappy.speedome.gauges

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * GPU effects (docs/plan.md §9): backlight glow, glass reflection and numeral bloom. They use runtime
 * shaders (AGSL on Android 13+, SkSL on iOS and desktop) when [ThemeOptions.shaders] is on, and
 * gradients otherwise. The shaders add what gradients can't: dithering (no banding in dark glows)
 * and curved glass highlights. The same source runs as AGSL and SkSL.
 */
object Effects {
    val available: Boolean get() = shadersAvailable

    fun enabled(options: ThemeOptions) = options.shaders && available

    internal const val GLOW = """
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
    internal const val GLASS = """
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

    /** Brushes are cached by their inputs; a gauge only ever uses a handful. */
    internal fun brush(key: Any, build: () -> Brush): Brush {
        if (brushes.size > 64) brushes.clear()
        return brushes.getOrPut(key, build)
    }
}

internal expect val shadersAvailable: Boolean

internal expect fun glowShaderBrush(center: Offset, radius: Float, tint: Color, intensity: Float): Brush

internal expect fun glassShaderBrush(center: Offset, radius: Float): Brush

/** The blurred copy under a lit numeral (see [bloomText]). */
internal expect fun DrawScope.haloText(s: String, x: Float, y: Float, paint: GaugePaint, sizePx: Float, color: Color, align: Align)

/** The fine dark grid in front of a vacuum-fluorescent display (Digital), as a repeating brush. */
internal expect fun meshBrush(density: Float): ShaderBrush

/** A soft light source (backlight, sun, halo) centred on [center]. */
fun DrawScope.backlightGlow(center: Offset, radius: Float, tint: Color, intensity: Float, options: ThemeOptions) {
    val on = Effects.enabled(options)
    val brush = Effects.brush(listOf("glow", on, center, radius, tint, intensity)) {
        if (on) glowShaderBrush(center, radius, tint, intensity) else
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
        if (on) glassShaderBrush(center, radius) else
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
    paint: GaugePaint,
    sizePx: Float,
    color: Color,
    options: ThemeOptions,
    strength: Float = 1f,
    align: Align = Align.CENTER,
) {
    if (Effects.enabled(options)) {
        haloText(s, x, y, paint, sizePx, color.copy(alpha = (color.alpha * .9f * strength).coerceIn(0f, 1f)), align)
    }
    text(s, x, y, paint, sizePx, color, align)
}
