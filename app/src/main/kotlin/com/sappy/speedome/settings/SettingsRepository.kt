package com.sappy.speedome.settings

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sappy.speedome.engine.AutoRangeSettings
import com.sappy.speedome.engine.EngineSettings
import com.sappy.speedome.engine.Mode
import com.sappy.speedome.engine.ShrinkPolicy
import com.sappy.speedome.gauges.AverageDisplay
import com.sappy.speedome.gauges.DigitalColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Accent colours offered for Modern and Map (ARGB). */
enum class Accent(val argb: Long, val label: String) {
    AMBER(0xFFE8A33D, "Amber"),
    BLUE(0xFF4DA3FF, "Blue"),
    RED(0xFFFF5A4E, "Red"),
    GREEN(0xFF5BE89A, "Green"),
}

/** Every user-facing setting (docs/plan.md §10). */
data class AppSettings(
    val devMode: Boolean = false,
    val simulator: Boolean = false,
    val mode: Mode = Mode.DRIVE,
    val predictNeedle: Boolean = true,
    val theme: String = "retro",
    val average: AverageDisplay = AverageDisplay.BOTH,
    val startupSweep: Boolean = true,
    val shrink: ShrinkPolicy = ShrinkPolicy.WITH_DELAY,
    val fixedKmh: Int = 120,
    val retroCream: Boolean = false,
    val digital: DigitalColor = DigitalColor.VFD,
    val accent: Accent = Accent.AMBER,
) {
    val engine: EngineSettings get() = EngineSettings(mode = mode, autoRange = AutoRangeSettings(shrink, fixedKmh))
}

private val Context.settingsStore by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context, scope: CoroutineScope) {
    private val store = context.applicationContext.settingsStore

    private object Keys {
        val devMode = booleanPreferencesKey("dev_mode")
        val simulator = booleanPreferencesKey("simulator")
        val mode = stringPreferencesKey("mode")
        val predictNeedle = booleanPreferencesKey("predict_needle")
        val theme = stringPreferencesKey("theme")
        val average = stringPreferencesKey("average")
        val startupSweep = booleanPreferencesKey("startup_sweep")
        val shrink = stringPreferencesKey("shrink")
        val fixedKmh = intPreferencesKey("fixed_kmh")
        val retroCream = booleanPreferencesKey("retro_cream")
        val digital = stringPreferencesKey("digital")
        val accent = stringPreferencesKey("accent")
    }

    val state: StateFlow<AppSettings> = store.data
        .map { it.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
    }

    private inline fun <reified E : Enum<E>> String?.enumOr(default: E): E =
        this?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: default

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            devMode = this[Keys.devMode] ?: d.devMode,
            simulator = this[Keys.simulator] ?: d.simulator,
            mode = this[Keys.mode].enumOr(d.mode),
            predictNeedle = this[Keys.predictNeedle] ?: d.predictNeedle,
            theme = this[Keys.theme] ?: d.theme,
            average = this[Keys.average].enumOr(d.average),
            startupSweep = this[Keys.startupSweep] ?: d.startupSweep,
            shrink = this[Keys.shrink].enumOr(d.shrink),
            fixedKmh = this[Keys.fixedKmh] ?: d.fixedKmh,
            retroCream = this[Keys.retroCream] ?: d.retroCream,
            digital = this[Keys.digital].enumOr(d.digital),
            accent = this[Keys.accent].enumOr(d.accent),
        )
    }

    private fun MutablePreferences.write(s: AppSettings) {
        this[Keys.devMode] = s.devMode
        this[Keys.simulator] = s.simulator
        this[Keys.mode] = s.mode.name
        this[Keys.predictNeedle] = s.predictNeedle
        this[Keys.theme] = s.theme
        this[Keys.average] = s.average.name
        this[Keys.startupSweep] = s.startupSweep
        this[Keys.shrink] = s.shrink.name
        this[Keys.fixedKmh] = s.fixedKmh
        this[Keys.retroCream] = s.retroCream
        this[Keys.digital] = s.digital.name
        this[Keys.accent] = s.accent.name
    }
}
