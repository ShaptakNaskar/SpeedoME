package com.sappy.speedome.ui

import com.sappy.speedome.gauges.DigitalTheme
import com.sappy.speedome.gauges.GaugeTheme
import com.sappy.speedome.gauges.ModernTheme
import com.sappy.speedome.gauges.NightFocusTheme
import com.sappy.speedome.gauges.RetroTheme
import com.sappy.speedome.gauges.SpeedTapeTheme
import com.sappy.speedome.gauges.SunlightTheme

/**
 * The Speed-tab carousel in plan order (docs/plan.md §9). Canvas themes come from :gauges; the Map
 * (a world map with its speedo), Graph and Nerd pages are app screens: they need live data beyond a gauge frame.
 */
data class SpeedThemeEntry(val id: String, val title: String, val gauge: GaugeTheme?)

object SpeedThemes {
    const val MAP = "map"
    const val NERD = "nerd"
    const val GRAPH = "graph"

    val all: List<SpeedThemeEntry> = listOf(
        RetroTheme.entry(), ModernTheme.entry(), DigitalTheme.entry(), NightFocusTheme.entry(),
        SpeedThemeEntry(MAP, "MAP", null), SpeedThemeEntry(GRAPH, "GRAPH", null), SpeedThemeEntry(NERD, "NERD", null),
        SpeedTapeTheme.entry(), SunlightTheme.entry(),
    )

    /** Unknown ids (such as the removed Synthwave theme) fall back to Retro; 0.13.0's World page is now the Map. */
    fun byId(id: String?) = all.firstOrNull { it.id == if (id == "world") MAP else id } ?: all.first()

    private fun GaugeTheme.entry() = SpeedThemeEntry(id, title, this)
}
