package com.uip.oneapp.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.uip.oneapp.ui.screens.settings.settingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// =====================================================================================
// DrainQ SA-Design — Theme-Quelle: themeMode-Pref (System/Dunkel/Hell) via DataStore.
// Gesetzt in den Einstellungen (DqThemeToggle), gelesen in MainActivity.
// =====================================================================================

enum class ThemeMode { SYSTEM, DARK, LIGHT }

private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")

/** Persistierter Theme-Modus (Default: System). */
fun themeModeFlow(context: Context): Flow<ThemeMode> =
    context.settingsStore.data.map { prefs ->
        when (prefs[KEY_THEME_MODE]) {
            ThemeMode.DARK.name  -> ThemeMode.DARK
            ThemeMode.LIGHT.name -> ThemeMode.LIGHT
            else                 -> ThemeMode.SYSTEM
        }
    }

suspend fun setThemeMode(context: Context, mode: ThemeMode) {
    context.settingsStore.edit { it[KEY_THEME_MODE] = mode.name }
}

/** Löst den persistierten [ThemeMode] in ein konkretes Dark/Light-Flag auf. */
@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.DARK   -> true
    ThemeMode.LIGHT  -> false
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

/** Bequemer Composable-Reader für die App-Wurzel. */
@Composable
fun rememberThemeMode(context: Context): ThemeMode {
    val mode by themeModeFlow(context).collectAsState(initial = ThemeMode.SYSTEM)
    return mode
}
