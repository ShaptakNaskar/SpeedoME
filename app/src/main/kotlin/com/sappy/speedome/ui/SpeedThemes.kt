package com.sappy.speedome.ui

import com.sappy.speedome.gauges.DigitalTheme
import com.sappy.speedome.gauges.GaugeTheme
import com.sappy.speedome.gauges.ModernTheme
import com.sappy.speedome.gauges.NightFocusTheme
import com.sappy.speedome.gauges.RetroTheme
import com.sappy.speedome.gauges.SpeedTapeTheme
import com.sappy.speedome.gauges.SunlightTheme
import com.sappy.speedome.gauges.SynthwaveTheme

/**
 * The Speed-tab carousel in plan order (docs/plan.md §9). Canvas themes come from :gauges; the Map
 * and Nerd pages are app screens because they need live data beyond a gauge frame.
 */
data class SpeedThemeEntry(val id: String, val title: String, val gauge: GaugeTheme?)

object SpeedThemes {
    const val MAP = "map"
    const val NERD = "nerd"

    val all: List<SpeedThemeEntry> = listOf(
        RetroTheme.entry(), ModernTheme.entry(), DigitalTheme.entry(), NightFocusTheme.entry(),
        SpeedThemeEntry(MAP, "MAP", null), SpeedThemeEntry(NERD, "NERD", null),
        SpeedTapeTheme.entry(), SynthwaveTheme.entry(), SunlightTheme.entry(),
    )

    fun byId(id: String?) = all.firstOrNull { it.id == id } ?: all.first()

    private fun GaugeTheme.entry() = SpeedThemeEntry(id, title, this)
}
