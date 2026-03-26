package com.mqunibi.mqplayer.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.core.content.edit

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

data class AppSettingsState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)

object AppSettingsRepository {
    private const val PREFS_NAME = "mq_player_settings"
    private const val KEY_THEME_MODE = "theme_mode"

    private val mutableState = MutableStateFlow(AppSettingsState())
    val state: StateFlow<AppSettingsState> = mutableState.asStateFlow()

    private lateinit var sharedPreferences: SharedPreferences
    private var initialized = false

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) {
            return
        }

        sharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        mutableState.value = AppSettingsState(themeMode = readThemeMode())
        initialized = true
    }

    fun setThemeMode(themeMode: ThemeMode) {
        if (!initialized) {
            return
        }

        sharedPreferences.edit {
            putString(KEY_THEME_MODE, themeMode.name)
        }
        mutableState.value = AppSettingsState(themeMode = themeMode)
    }

    private fun readThemeMode(): ThemeMode {
        val storedValue = sharedPreferences.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        return storedValue
            ?.let { value -> ThemeMode.entries.firstOrNull { it.name == value } }
            ?: ThemeMode.SYSTEM
    }
}

