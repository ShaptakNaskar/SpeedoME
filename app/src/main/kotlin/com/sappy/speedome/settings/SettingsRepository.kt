package com.sappy.speedome.settings

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sappy.speedome.engine.Mode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Every user-facing setting (docs/plan.md §10). Grows milestone by milestone. */
data class AppSettings(
    val devMode: Boolean = false,
    val simulator: Boolean = false,
    val mode: Mode = Mode.DRIVE,
    val predictNeedle: Boolean = true,
)

private val Context.settingsStore by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context, scope: CoroutineScope) {
    private val store = context.applicationContext.settingsStore

    private object Keys {
        val devMode = booleanPreferencesKey("dev_mode")
        val simulator = booleanPreferencesKey("simulator")
        val mode = stringPreferencesKey("mode")
        val predictNeedle = booleanPreferencesKey("predict_needle")
    }

    val state: StateFlow<AppSettings> = store.data
        .map { it.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
    }

    private fun Preferences.toSettings() = AppSettings(
        devMode = this[Keys.devMode] ?: false,
        simulator = this[Keys.simulator] ?: false,
        mode = this[Keys.mode]?.let { runCatching { Mode.valueOf(it) }.getOrNull() } ?: Mode.DRIVE,
        predictNeedle = this[Keys.predictNeedle] ?: true,
    )

    private fun MutablePreferences.write(s: AppSettings) {
        this[Keys.devMode] = s.devMode
        this[Keys.simulator] = s.simulator
        this[Keys.mode] = s.mode.name
        this[Keys.predictNeedle] = s.predictNeedle
    }
}
