package com.sappy.speedome.ui.worlds

import androidx.compose.ui.graphics.Color
import com.sappy.speedome.gauges.BackStreetsTheme
import com.sappy.speedome.gauges.ConcreteGridTheme
import com.sappy.speedome.gauges.DesertSandsTheme
import com.sappy.speedome.gauges.DosAtlasTheme
import com.sappy.speedome.gauges.ExplorerTheme
import com.sappy.speedome.gauges.GaugeTheme
import com.sappy.speedome.gauges.GaugeTypeface
import com.sappy.speedome.gauges.GoldenThreadTheme
import com.sappy.speedome.gauges.GreenTerminalTheme
import com.sappy.speedome.gauges.HighlandsTheme
import com.sappy.speedome.gauges.InkWindTheme
import com.sappy.speedome.gauges.MidnightRunTheme
import com.sappy.speedome.gauges.NeonCityTheme
import com.sappy.speedome.gauges.NorthernRoadTheme
import com.sappy.speedome.gauges.OldWestTheme
import com.sappy.speedome.gauges.OpenRoadTheme
import com.sappy.speedome.gauges.PalmCityTheme
import com.sappy.speedome.gauges.PixelWorldTheme
import com.sappy.speedome.gauges.Quest16Theme
import com.sappy.speedome.gauges.SeaChartTheme
import com.sappy.speedome.gauges.SkylineTheme
import com.sappy.speedome.gauges.SunsetCoastTheme
import com.sappy.speedome.gauges.TacticalRadarTheme
import com.sappy.speedome.gauges.VioletCityTheme
import com.sappy.speedome.gauges.WayfarerTheme
import com.sappy.speedome.gauges.WildsTheme
import com.sappy.speedome.gauges.WorldPalette
import com.sappy.speedome.gauges.WorldTheme

/**
 * A map world: one of the game-inspired map styles from Waypoint (MIT, imported by
 * tools/import_worlds.py into composeResources/files/worlds/<id>.json) with its palette, which also
 * colours the world's speedo unless it has a hand-made one ([hero]). Names are neutral on purpose:
 * no game titles. While a world is on, the app's lettering uses [font] for titles, numbers and buttons
 * and [bodyFont] for running text (the system font when null).
 */
data class World(
    val id: String,
    val title: String,
    val palette: WorldPalette,
    val hero: GaugeTheme? = null,
    val font: GaugeTypeface? = null,
    val bodyFont: GaugeTypeface? = null,
) {
    val stylePath get() = "files/worlds/$id.json"

    /** The speedo docked on this world's map. */
    val gauge: GaugeTheme by lazy { hero ?: WorldTheme("world-$id", title, palette) }
}

object Worlds {
    val all: List<World> = listOf(
        World("palm_city", "PALM CITY", WorldPalette(accent = Color(0xFFB6DB91), route = Color(0xFFB786ED), ground = Color(0xFF20221F), road = Color(0xFF73776C), building = Color(0xFF383B35)), hero = PalmCityTheme, font = GaugeTypeface.OSWALD),
        World("old_west", "OLD WEST", WorldPalette(accent = Color(0xFFE4BE82), route = Color(0xFFB52E31), ground = Color(0xFFDAC8A2), road = Color(0xFF665F4B), building = Color(0xFFC6B18D)), hero = OldWestTheme, font = GaugeTypeface.RYE),
        World("neon_city", "NEON CITY", WorldPalette(accent = Color(0xFF6DE9DD), route = Color(0xFFF6E75C), ground = Color(0xFF0C1019), road = Color(0xFF258DA3), building = Color(0xFF8B2029)), hero = NeonCityTheme, font = GaugeTypeface.ORBITRON),
        World("pixel_world", "PIXEL WORLD", WorldPalette(accent = Color(0xFFB8E088), route = Color(0xFFE95345), ground = Color(0xFF566943), road = Color(0xFFC7C4A5), building = Color(0xFF777D6C)), hero = PixelWorldTheme, font = GaugeTypeface.SILKSCREEN),
        World("green_terminal", "GREEN TERMINAL", WorldPalette(accent = Color(0xFF9AF477), route = Color(0xFFD5FF9B), ground = Color(0xFF071109), road = Color(0xFF6BCF55), building = Color(0xFF1E4922)), hero = GreenTerminalTheme, font = GaugeTypeface.VT323, bodyFont = GaugeTypeface.VT323),
        World("back_streets", "BACK STREETS", WorldPalette(accent = Color(0xFFF2C877), route = Color(0xFFDA5B3C), ground = Color(0xFFC2C5AD), road = Color(0xFFF4F0D8), building = Color(0xFFA4A48E)), hero = BackStreetsTheme, font = GaugeTypeface.OSWALD),
        World("midnight_run", "MIDNIGHT RUN", WorldPalette(accent = Color(0xFFFFA9E8), route = Color(0xFF75EBF5), ground = Color(0xFF555946), road = Color(0xFFD2D2B6), building = Color(0xFF6A6A5A)), hero = MidnightRunTheme, font = GaugeTypeface.ORBITRON),
        World("wayfarer", "WAYFARER", WorldPalette(accent = Color(0xFFD6D59A), route = Color(0xFF9F3435), ground = Color(0xFFB6AF8D), road = Color(0xFFE4D6AE), building = Color(0xFF93886B)), hero = WayfarerTheme, font = GaugeTypeface.CINZEL),
        World("sunset_coast", "SUNSET COAST", WorldPalette(accent = Color(0xFFFFAFE1), route = Color(0xFFF46AC0), ground = Color(0xFF94909D), road = Color(0xFFF6F1DD), building = Color(0xFFB4B4B0)), hero = SunsetCoastTheme, font = GaugeTypeface.OUTFIT),
        World("concrete_grid", "CONCRETE GRID", WorldPalette(accent = Color(0xFFFFD06D), route = Color(0xFFEFA937), ground = Color(0xFF222527), road = Color(0xFF92999C), building = Color(0xFF42484B)), hero = ConcreteGridTheme, font = GaugeTypeface.B612),
        World("connected_city", "CONNECTED CITY", WorldPalette(accent = Color(0xFF78C9FF), route = Color(0xFFF28D31), ground = Color(0xFFE5E9DD), road = Color(0xFFFFFFFF), building = Color(0xFFC8CED0))),
        World("explorer", "EXPLORER", WorldPalette(accent = Color(0xFF81D9FF), route = Color(0xFFF0CE6B), ground = Color(0xFF4B4932), road = Color(0xFFA69C64), building = Color(0xFF5F5B3B)), hero = ExplorerTheme, font = GaugeTypeface.B612),
        World("northern_road", "NORTHERN ROAD", WorldPalette(accent = Color(0xFFDCE9EE), route = Color(0xFFE9E4CE), ground = Color(0xFF859395), road = Color(0xFFBDC4BE), building = Color(0xFF647577)), hero = NorthernRoadTheme, font = GaugeTypeface.OSWALD),
        World("golden_thread", "GOLDEN THREAD", WorldPalette(accent = Color(0xFFE3CC8D), route = Color(0xFFEAC968), ground = Color(0xFFB1A789), road = Color(0xFFE0D1AA), building = Color(0xFF817963)), hero = GoldenThreadTheme, font = GaugeTypeface.CINZEL),
        World("open_road", "OPEN ROAD", WorldPalette(accent = Color(0xFFFF92D6), route = Color(0xFF51C4F1), ground = Color(0xFF8A966A), road = Color(0xFFF5F0DD), building = Color(0xFFA3A18A)), hero = OpenRoadTheme, font = GaugeTypeface.OUTFIT),
        World("desert_sands", "DESERT SANDS", WorldPalette(accent = Color(0xFFEBD28C), route = Color(0xFFE7C65D), ground = Color(0xFFB49B6E), road = Color(0xFFE7D6AD), building = Color(0xFFA49477)), hero = DesertSandsTheme, font = GaugeTypeface.CINZEL),
        World("backcountry", "BACKCOUNTRY", WorldPalette(accent = Color(0xFFF4D57B), route = Color(0xFFEAC35B), ground = Color(0xFF6E7A60), road = Color(0xFFDDD9BC), building = Color(0xFF818777))),
        World("ink_wind", "INK & WIND", WorldPalette(accent = Color(0xFFE8E5D7), route = Color(0xFFB84435), ground = Color(0xFFC9CCC2), road = Color(0xFFEEEADE), building = Color(0xFFAEB4A8)), hero = InkWindTheme, font = GaugeTypeface.OUTFIT, bodyFont = GaugeTypeface.OUTFIT),
        World("courier", "COURIER", WorldPalette(accent = Color(0xFF9AE5DD), route = Color(0xFFD8C87A), ground = Color(0xFF182B37), road = Color(0xFF86AAB1), building = Color(0xFF344953))),
        World("wilds", "WILDS", WorldPalette(accent = Color(0xFFF0DBAC), route = Color(0xFFF1D784), ground = Color(0xFF5B6251), road = Color(0xFFC6B88C), building = Color(0xFF707365)), hero = WildsTheme, font = GaugeTypeface.COURIER),
        World("skyline", "SKYLINE", WorldPalette(accent = Color(0xFFFF9E9D), route = Color(0xFFF6D156), ground = Color(0xFF102A38), road = Color(0xFF4290A4), building = Color(0xFF1B6173)), hero = SkylineTheme, font = GaugeTypeface.OSWALD),
        World("highlands", "HIGHLANDS", WorldPalette(accent = Color(0xFFE5CE93), route = Color(0xFFDABD69), ground = Color(0xFF77765F), road = Color(0xFFC4BEA0), building = Color(0xFF72765E)), hero = HighlandsTheme, font = GaugeTypeface.CINZEL),
        World("surface_survey", "SURFACE SURVEY", WorldPalette(accent = Color(0xFFEEF1E9), route = Color(0xFFF3C56F), ground = Color(0xFF1B304B), road = Color(0xFF7196AF), building = Color(0xFF36546A))),
        World("old_kingdom", "OLD KINGDOM", WorldPalette(accent = Color(0xFFE6CF94), route = Color(0xFFDFBA52), ground = Color(0xFFB2A37D), road = Color(0xFFE7D4AA), building = Color(0xFFA39472))),
        World("violet_city", "VIOLET CITY", WorldPalette(accent = Color(0xFFD0A6FB), route = Color(0xFFB893F3), ground = Color(0xFF1B1C22), road = Color(0xFF7C8490), building = Color(0xFF363740)), hero = VioletCityTheme, font = GaugeTypeface.OUTFIT),
        World("islands", "ISLANDS", WorldPalette(accent = Color(0xFF87ECDF), route = Color(0xFFF0CF6A), ground = Color(0xFF143748), road = Color(0xFF6FA4AD), building = Color(0xFF305360))),
        World("prohibition", "PROHIBITION", WorldPalette(accent = Color(0xFFE8D29A), route = Color(0xFFBD624C), ground = Color(0xFFC1C1A4), road = Color(0xFFE8D174), building = Color(0xFFAAA99A))),
        World("quarantine", "QUARANTINE", WorldPalette(accent = Color(0xFFFFAE61), route = Color(0xFFFF9D3D), ground = Color(0xFF182C2E), road = Color(0xFF819A98), building = Color(0xFF355152))),
        World("sea_chart", "SEA CHART", WorldPalette(accent = Color(0xFFE7D29C), route = Color(0xFFA13C32), ground = Color(0xFFD8CBAB), road = Color(0xFFF2E6C7), building = Color(0xFFACA080)), hero = SeaChartTheme, font = GaugeTypeface.OSWALD),
        World("dos_atlas", "DOS ATLAS", WorldPalette(accent = Color(0xFFFFD18A), route = Color(0xFFFFE4A0), ground = Color(0xFF15100D), road = Color(0xFFB38D52), building = Color(0xFF4C3D28)), hero = DosAtlasTheme, font = GaugeTypeface.VT323, bodyFont = GaugeTypeface.VT323),
        World("new_chapter", "NEW CHAPTER", WorldPalette(accent = Color(0xFFF0C862), route = Color(0xFFD47D29), ground = Color(0xFFCEBC92), road = Color(0xFFF7EACB), building = Color(0xFFB3A78B))),
        World("tactical_radar", "TACTICAL RADAR", WorldPalette(accent = Color(0xFFFFD37F), route = Color(0xFF67CBEE), ground = Color(0xFF302F2A), road = Color(0xFFD6D2BD), building = Color(0xFF65635B)), hero = TacticalRadarTheme, font = GaugeTypeface.B612),
        World("vector_command", "VECTOR COMMAND", WorldPalette(accent = Color(0xFF9AC8FF), route = Color(0xFFFFFFFF), ground = Color(0xFF030815), road = Color(0xFF5D9BD4), building = Color(0xFF112449))),
        World("field_ops", "FIELD OPS", WorldPalette(accent = Color(0xFFCDDCAC), route = Color(0xFFF1E9A5), ground = Color(0xFF31382F), road = Color(0xFFBBC2A8), building = Color(0xFF535D4E))),
        World("quest_16bit", "16-BIT QUEST", WorldPalette(accent = Color(0xFFFFEBA3), route = Color(0xFFE56470), ground = Color(0xFF88A95C), road = Color(0xFFE5CA89), building = Color(0xFFA2856F)), hero = Quest16Theme, font = GaugeTypeface.SILKSCREEN),
        World("home_console", "HOME CONSOLE", WorldPalette(accent = Color(0xFF78CBE8), route = Color(0xFF2185BA), ground = Color(0xFFE8EDF0), road = Color(0xFFFFFFFF), building = Color(0xFFC5CFD7))),
        World("exclusion_zone", "EXCLUSION ZONE", WorldPalette(accent = Color(0xFFE6C274), route = Color(0xFF9EE376), ground = Color(0xFF3A3F31), road = Color(0xFFB6B39A), building = Color(0xFF66695D))),
        World("machine_atlas", "MACHINE ATLAS", WorldPalette(accent = Color(0xFFD3C9A4), route = Color(0xFFA14F37), ground = Color(0xFFCCC8B0), road = Color(0xFFE5E1CD), building = Color(0xFF9D9B88))),
    )

    /** Unknown ids fall back to the first world. */
    fun byId(id: String?): World = all.firstOrNull { it.id == id } ?: all.first()
}
