package com.sappy.speedome.ui.theme

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sappy.speedome.LocalAppContainer
import com.sappy.speedome.gauges.DigitalTheme
import com.sappy.speedome.gauges.GaugeTypeface
import com.sappy.speedome.gauges.ModernTheme
import com.sappy.speedome.gauges.NightFocusTheme
import com.sappy.speedome.gauges.RetroTheme
import com.sappy.speedome.gauges.SpeedTapeTheme
import com.sappy.speedome.gauges.SunlightTheme
import com.sappy.speedome.gauges.fontFamily
import com.sappy.speedome.settings.AppSettings
import com.sappy.speedome.ui.SpeedThemes
import com.sappy.speedome.ui.SystemBars
import com.sappy.speedome.ui.worlds.Worlds

/**
 * The app's look: chrome colours and lettering. It follows the Speed tab's theme (or the Map's world),
 * so the whole app matches the speedo. [font] letters titles, numbers, buttons and tabs; [reading]
 * letters captions and descriptions (the system font when null).
 */
@Immutable
data class AppLook(
    val background: Color,
    val raised: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val light: Boolean = false,
    val font: GaugeTypeface? = null,
    val reading: GaugeTypeface? = null,
)

/** SpeedoME's own look: true black (saves power on OLED) with amber. Graph and Nerd keep it. */
private val DEFAULT_LOOK = AppLook(
    background = Color(0xFF000000), raised = Color(0xFF0E1011), text = Color(0xFFCFD4D2),
    muted = Color(0xFF7D8683), accent = Color(0xFFE8A33D),
)

/** The look for the current settings: the selected speedo theme's, or on the Map its world's. */
fun lookFor(s: AppSettings): AppLook = when (SpeedThemes.byId(s.theme).id) {
    RetroTheme.id -> if (s.retroCream) {
        AppLook(Color(0xFFF3EAD2), Color(0xFFE5D9B8), Color(0xFF1D1812), Color(0xFF6F6452), Color(0xFFB4410C), light = true, font = GaugeTypeface.OSWALD)
    } else {
        AppLook(Color(0xFF0C0B0A), Color(0xFF1D1B18), Color(0xFFF1E6C9), Color(0xFF9A8F78), Color(0xFFFF8A3D), font = GaugeTypeface.OSWALD)
    }
    ModernTheme.id -> AppLook(Color(0xFF000000), Color(0xFF101215), Color(0xFFF2F2F2), Color(0xFF8A9099), Color(s.accent.argb), font = GaugeTypeface.OUTFIT, reading = GaugeTypeface.OUTFIT)
    DigitalTheme.id -> s.digital.on.let { on ->
        AppLook(Color(0xFF000000), lerp(Color.Black, on, .1f), lerp(Color(0xFFE6E6E6), on, .35f), lerp(Color.Black, on, .6f), on, font = GaugeTypeface.B612)
    }
    NightFocusTheme.id -> AppLook(Color(0xFF000000), Color(0xFF0A0D0B), Color(0xFFA9C4B0), Color(0xFF5F6F63), Color(0xFF6FD98A), font = GaugeTypeface.BARLOW_SEMI)
    SpeedTapeTheme.id -> AppLook(Color(0xFF101317), Color(0xFF242A31), Color(0xFFE9EEF2), Color(0xFF8C96A0), Color(0xFF39E58C), font = GaugeTypeface.B612)
    SunlightTheme.id -> AppLook(Color(0xFFFFFFFF), Color(0xFFEDEDED), Color(0xFF000000), Color(0xFF4A4A4A), Color(0xFFB4410C), light = true, font = GaugeTypeface.OUTFIT, reading = GaugeTypeface.OUTFIT)
    SpeedThemes.MAP -> Worlds.byId(s.worldId).let { w ->
        val p = w.palette
        val bg = if (p.light) lerp(p.ground, Color.White, .3f) else lerp(p.ground, Color.Black, .62f)
        AppLook(bg, p.panel, p.ink, lerp(p.ink, bg, .45f), p.strong, light = p.light, font = w.font, reading = w.bodyFont)
    }
    else -> DEFAULT_LOOK
}

/**
 * App chrome colours, from the current [AppLook] (the gauges carry their own palettes). Snapshot
 * state, so composables and draw blocks that read a colour update when the look changes.
 */
object SpeedoColors {
    var look by mutableStateOf(DEFAULT_LOOK)
    val Background get() = look.background
    val Raised get() = look.raised
    val Text get() = look.text
    val Muted get() = look.muted
    val Accent get() = look.accent
    val AccentDim get() = lerp(look.background, look.accent, .22f)
}

/** The look's reading font for captions and descriptions. */
val readingFont: FontFamily
    @Composable @ReadOnlyComposable get() = MaterialTheme.typography.bodyMedium.fontFamily ?: FontFamily.Default

@Composable
fun SpeedoTheme(content: @Composable () -> Unit) {
    val settings by LocalAppContainer.current.settings.state.collectAsStateWithLifecycle()
    val look = lookFor(settings)
    if (SpeedoColors.look != look) SpeedoColors.look = look // before any child reads a colour
    SystemBars(light = look.light)
    val font = look.font?.fontFamily() ?: FontFamily.Default
    val reading = look.reading?.fontFamily() ?: FontFamily.Default
    val scheme = if (look.light) {
        lightColorScheme(
            primary = look.accent, onPrimary = look.background, secondaryContainer = SpeedoColors.AccentDim, onSecondaryContainer = look.text,
            background = look.background, onBackground = look.text, surface = look.background, onSurface = look.text,
            surfaceVariant = look.raised, onSurfaceVariant = look.muted, surfaceContainerLow = look.raised, surfaceContainerHigh = look.raised,
            outline = look.muted, outlineVariant = look.raised,
        )
    } else {
        darkColorScheme(
            primary = look.accent, onPrimary = look.background, secondaryContainer = SpeedoColors.AccentDim, onSecondaryContainer = look.accent,
            background = look.background, onBackground = look.text, surface = look.background, onSurface = look.text,
            surfaceVariant = look.raised, onSurfaceVariant = look.muted, surfaceContainerLow = look.raised, surfaceContainerHigh = look.raised,
            outline = look.muted, outlineVariant = look.raised,
        )
    }
    val type = Typography().run {
        fun TextStyle.f(family: FontFamily) = copy(fontFamily = family)
        copy(
            displayLarge = displayLarge.f(font), displayMedium = displayMedium.f(font), displaySmall = displaySmall.f(font),
            headlineLarge = headlineLarge.f(font), headlineMedium = headlineMedium.f(font), headlineSmall = headlineSmall.f(font),
            titleLarge = titleLarge.f(font), titleMedium = titleMedium.f(font), titleSmall = titleSmall.f(font),
            labelLarge = labelLarge.f(font), labelMedium = labelMedium.f(font), labelSmall = labelSmall.f(font),
            bodyLarge = bodyLarge.f(reading), bodyMedium = bodyMedium.f(reading), bodySmall = bodySmall.f(reading),
        )
    }
    MaterialTheme(colorScheme = scheme, typography = type) {
        // Plain Text (labels, numbers, headers) takes the look's lettering; descriptions use [readingFont].
        CompositionLocalProvider(LocalTextStyle provides type.bodyLarge.copy(fontFamily = font), content = content)
    }
}
