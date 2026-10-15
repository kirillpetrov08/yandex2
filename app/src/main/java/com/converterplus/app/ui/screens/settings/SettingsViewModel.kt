package com.converterplus.app.ui.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.converterplus.app.BuildConfig
import com.converterplus.app.ads.Ads
import com.converterplus.app.ads.ConsentManager
import com.converterplus.app.data.SettingsStore
import com.converterplus.app.data.ThemeMode
import com.converterplus.app.engine.QualityPreset
import com.converterplus.app.engine.ocr.TessdataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val quality: QualityPreset = QualityPreset.BALANCED,
    val saveFolderUri: String? = null,
    val showProgressNotification: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val personalizedAds: Boolean = false,
    val adsFreeMinutesLeft: Int = 0,
    val versionName: String = BuildConfig.VERSION_NAME,
    /** Языковые данные распознавания уже разложены в приватный каталог. */
    val ocrReady: Boolean = false,
    /** Сколько занимают данные распознавания на устройстве. */
    val ocrDataBytes: Long = 0L,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val app = getApplication<Application>()
        val ocrReady = TessdataStore.isReady(app)
        _state.value = SettingsUiState(
            themeMode = SettingsStore.themeMode,
            quality = SettingsStore.qualityPreset,
            saveFolderUri = SettingsStore.saveFolderUri,
            showProgressNotification = SettingsStore.showProgressNotification,
            hapticsEnabled = SettingsStore.hapticsEnabled,
            personalizedAds = ConsentManager.isPersonalizedAds(app),
            adsFreeMinutesLeft = Ads.policy(app).adsFreeMinutesLeft(),
            versionName = BuildConfig.VERSION_NAME,
            ocrReady = ocrReady,
            ocrDataBytes = if (ocrReady) TessdataStore.sizeBytes(app) else 0L,
        )
    }

    fun setTheme(mode: ThemeMode) {
        SettingsStore.themeMode = mode
        refresh()
    }

    fun setQuality(quality: QualityPreset) {
        SettingsStore.qualityPreset = quality
        refresh()
    }

    fun setSaveFolder(uri: String?) {
        SettingsStore.saveFolderUri = uri
        refresh()
    }

    fun setShowProgressNotification(value: Boolean) {
        SettingsStore.showProgressNotification = value
        refresh()
    }

    fun setHaptics(value: Boolean) {
        SettingsStore.hapticsEnabled = value
        refresh()
    }

    /** Переключатель персонализации меняет согласие, которое уходит в SDK. */
    fun setPersonalizedAds(value: Boolean) {
        val app = getApplication<Application>()
        ConsentManager.setPersonalizedAds(app, value)
        Ads.initIfConsented(app)
        refresh()
    }
}
