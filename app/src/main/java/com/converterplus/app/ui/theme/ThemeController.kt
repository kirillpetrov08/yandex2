package com.converterplus.app.ui.theme

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.converterplus.app.data.SettingsStore
import com.converterplus.app.data.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Наблюдаемое состояние темы: настройки меняют его, Compose перерисовывается сразу,
 * без повторного входа в приложение. [AppCompatDelegate] синхронизирует системные окна.
 */
object ThemeController {

    private val _mode = MutableStateFlow(ThemeMode.SYSTEM)
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    fun init(context: Context) {
        _mode.value = SettingsStore.themeMode
        applyNightMode(_mode.value)
    }

    fun set(mode: ThemeMode) {
        SettingsStore.themeMode = mode
        _mode.value = mode
        applyNightMode(mode)
    }

    fun applyNightMode(mode: ThemeMode = _mode.value) {
        val night = when (mode) {
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        if (AppCompatDelegate.getDefaultNightMode() != night) {
            AppCompatDelegate.setDefaultNightMode(night)
        }
    }
}
