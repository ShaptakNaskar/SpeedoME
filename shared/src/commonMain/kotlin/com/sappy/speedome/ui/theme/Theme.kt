package com.sappy.speedome.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** App chrome colours (the gauges carry their own palettes per theme). True black saves power on OLED. */
object SpeedoColors {
    val Background = Color(0xFF000000)
    val Raised = Color(0xFF0E1011)
    val Text = Color(0xFFCFD4D2)
    val Muted = Color(0xFF7D8683)
    val Accent = Color(0xFFE8A33D)
    val AccentDim = Color(0xFF3A2D17)
}

@Composable
fun SpeedoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = SpeedoColors.Accent,
            secondaryContainer = SpeedoColors.AccentDim,
            onSecondaryContainer = SpeedoColors.Accent,
            onPrimary = SpeedoColors.Background,
            background = SpeedoColors.Background,
            onBackground = SpeedoColors.Text,
            surface = SpeedoColors.Background,
            onSurface = SpeedoColors.Text,
            surfaceVariant = SpeedoColors.Raised,
            onSurfaceVariant = SpeedoColors.Muted,
        ),
        content = content,
    )
}
