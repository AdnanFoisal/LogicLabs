package com.logiclabs.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * How the bench is lit across the 4-Theme Hardware Suite:
 * - `OBSIDIAN`: Obsidian Stealth (default AMOLED)
 * - `AMBER_CRT`: Amber CRT / Vintage Tektronix
 * - `HP_SLATE`: HP Slate Precision
 * - `CLEANROOM`: Cleanroom White (Daylight Architectural)
 * - `SYSTEM`: Follows system dark/light mode
 */
enum class ThemeMode {
    SYSTEM,
    OBSIDIAN,
    AMBER_CRT,
    HP_SLATE,
    CLEANROOM,
    CYBERPUNK_NEON,
    TOKYO_NIGHT,
    SOLARIZED_DARK,
    VINTAGE_BRITISH_LAB,
    TITANIUM_FROST;

    companion object {
        fun fromString(value: String?): ThemeMode = when (value) {
            "SYSTEM" -> SYSTEM
            "OBSIDIAN", "DARK" -> OBSIDIAN
            "AMBER_CRT" -> AMBER_CRT
            "HP_SLATE" -> HP_SLATE
            "CLEANROOM", "LIGHT" -> CLEANROOM
            "CYBERPUNK_NEON" -> CYBERPUNK_NEON
            "TOKYO_NIGHT" -> TOKYO_NIGHT
            "SOLARIZED_DARK" -> SOLARIZED_DARK
            "VINTAGE_BRITISH_LAB" -> VINTAGE_BRITISH_LAB
            "TITANIUM_FROST" -> TITANIUM_FROST
            else -> OBSIDIAN
        }
    }
}

/**
 * Every preference the app persists, as one immutable snapshot.
 *
 * Defaults here are the factory state of the trainer: sound on, haptics on, afterglow
 * and bloom on (they are the bench's signature), red default jumpers, catenary routing,
 * autosave on.
 */
data class Settings(
    val themeMode: ThemeMode = ThemeMode.OBSIDIAN,
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    /** LED afterglow for signals faster than the frame rate. */
    val ledAfterglow: Boolean = true,
    /** Bloom/glow halos on lit indicators and traces. */
    val bloomEffects: Boolean = true,
    /** Colour pre-armed for the next jumper wire drawn. */
    val defaultWireColor: String = "RED",
    /** `true` = right-angle routing for new wires, `false` = catenary. */
    val defaultRoutingManhattan: Boolean = false,
    /** Periodically write the open bench into the recovery draft. */
    val autosaveEnabled: Boolean = true
)

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "logiclabs_settings"
)

/**
 * Single source of truth for preferences, over Preferences DataStore.
 *
 * Reads are a cold [Flow] that emits the merged snapshot; writes are fire-and-forget
 * functions launched on an IO scope, because no caller ever needs the write's result —
 * DataStore serialises edits internally, so ordering is preserved without awaiting.
 * This keeps Compose call sites one-liners (`settings.setSoundEnabled(false)`) while
 * the flow above drives the UI back.
 */
class SettingsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settings: Flow<Settings> = appContext.settingsDataStore.data.map { p ->
        Settings(
            themeMode = p[KEY_THEME].toThemeMode(),
            soundEnabled = p[KEY_SOUND] ?: true,
            hapticsEnabled = p[KEY_HAPTICS] ?: true,
            ledAfterglow = p[KEY_AFTERGLOW] ?: true,
            bloomEffects = p[KEY_BLOOM] ?: true,
            defaultWireColor = p[KEY_WIRE_COLOR] ?: "RED",
            defaultRoutingManhattan = p[KEY_ROUTING] ?: false,
            autosaveEnabled = p[KEY_AUTOSAVE] ?: true
        )
    }

    fun setThemeMode(mode: ThemeMode) = write { it[KEY_THEME] = mode.name }

    fun setSoundEnabled(enabled: Boolean) = write { it[KEY_SOUND] = enabled }

    fun setHapticsEnabled(enabled: Boolean) = write { it[KEY_HAPTICS] = enabled }

    fun setLedAfterglow(enabled: Boolean) = write { it[KEY_AFTERGLOW] = enabled }

    fun setBloomEffects(enabled: Boolean) = write { it[KEY_BLOOM] = enabled }

    fun setDefaultWireColor(colorName: String) = write { it[KEY_WIRE_COLOR] = colorName }

    fun setDefaultRoutingManhattan(manhattan: Boolean) = write { it[KEY_ROUTING] = manhattan }

    fun setAutosaveEnabled(enabled: Boolean) = write { it[KEY_AUTOSAVE] = enabled }

    /** Resets every preference to factory defaults. */
    fun reset() = write { it.clear() }

    private fun write(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        scope.launch { appContext.settingsDataStore.edit(block) }
    }

    private fun String?.toThemeMode(): ThemeMode = ThemeMode.fromString(this)

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_SOUND = booleanPreferencesKey("sound_enabled")
        val KEY_HAPTICS = booleanPreferencesKey("haptics_enabled")
        val KEY_AFTERGLOW = booleanPreferencesKey("led_afterglow")
        val KEY_BLOOM = booleanPreferencesKey("bloom_effects")
        val KEY_WIRE_COLOR = stringPreferencesKey("default_wire_color")
        val KEY_ROUTING = booleanPreferencesKey("default_routing_manhattan")
        val KEY_AUTOSAVE = booleanPreferencesKey("autosave_enabled")
    }
}
