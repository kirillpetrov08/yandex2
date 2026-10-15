package com.converterplus.app.ui.theme

import android.content.Context
import com.converterplus.app.data.SettingsStore
import com.converterplus.app.data.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Наблюдаемое состояние темы: настройки меняют его, Compose перерисовывается.
 */
object ThemeController {

    private val _mode = MutableStateFlow(ThemeMode.SYSTEM)
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    fun init(context: Context) {
        _mode.value = SettingsStore.themeMode
    }

    fun set(mode: ThemeMode) {
        SettingsStore.themeMode = mode
        _mode.value = mode
    }
}
