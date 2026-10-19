package com.copilka.app.data

import android.content.Context
import android.content.SharedPreferences
import com.copilka.app.ads.AdPolicyStore
import com.copilka.app.ads.Ads
import com.copilka.app.ads.ConsentManager
import com.copilka.app.engine.PdfPageSize
import com.copilka.app.engine.QualityPreset

enum class ThemeMode { LIGHT, DARK, SYSTEM }

object SettingsStore : AdPolicyStore {
    private const val PREFS = "settings"
    private const val KEY_ONBOARDING_DONE = "onboarding_done"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_QUALITY = "quality_preset"
    private const val KEY_SAVE_FOLDER_URI = "save_folder_uri"
    private const val KEY_SHOW_PROGRESS_NOTIFICATION = "show_progress_notification"
    private const val KEY_HAPTICS = "haptics_enabled"
    private const val KEY_PDF_PAGE_SIZE = "pdf_page_size"
    @Volatile private var prefs: SharedPreferences? = null
    fun init(context: Context) { if (prefs == null) prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private fun get(): SharedPreferences = checkNotNull(prefs) { "SettingsStore.init не вызван" }
    private inline fun edit(block: SharedPreferences.Editor.() -> Unit) { get().edit().apply(block).apply() }

    var onboardingDone: Boolean
        get() = get().getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = edit { putBoolean(KEY_ONBOARDING_DONE, value) }

    var themeMode: ThemeMode
        get() = runCatching { ThemeMode.valueOf(get().getString(KEY_THEME, ThemeMode.SYSTEM.name)!!) }.getOrDefault(ThemeMode.SYSTEM)
        set(value) = edit { putString(KEY_THEME, value.name) }

    var hapticsEnabled: Boolean
        get() = get().getBoolean(KEY_HAPTICS, true)
        set(value) = edit { putBoolean(KEY_HAPTICS, value) }

    var qualityPreset: QualityPreset
        get() = runCatching { QualityPreset.valueOf(get().getString(KEY_QUALITY, QualityPreset.BALANCED.name)!!) }.getOrDefault(QualityPreset.BALANCED)
        set(value) = edit { putString(KEY_QUALITY, value.name) }

    var saveFolderUri: String?
        get() = get().getString(KEY_SAVE_FOLDER_URI, null)
        set(value) = edit { putString(KEY_SAVE_FOLDER_URI, value) }

    var pdfPageSize: PdfPageSize
        get() = runCatching { PdfPageSize.valueOf(get().getString(KEY_PDF_PAGE_SIZE, PdfPageSize.A4.name)!!) }.getOrDefault(PdfPageSize.A4)
        set(value) = edit { putString(KEY_PDF_PAGE_SIZE, value.name) }

    var showProgressNotification: Boolean
        get() = get().getBoolean(KEY_SHOW_PROGRESS_NOTIFICATION, true)
        set(value) = edit { putBoolean(KEY_SHOW_PROGRESS_NOTIFICATION, value) }

    // Совместимость с CopilkaApp
    fun hasConsent(context: Context): Boolean = ConsentManager.isConsentGiven(context)
    fun isPersonalizedAdsEnabled(context: Context): Boolean = ConsentManager.isPersonalizedAds(context)

    override fun getLong(key: String): Long? { val p = get(); return if (p.contains(key)) p.getLong(key, 0L) else null }
    override fun putLong(key: String, value: Long?) { if (value == null) edit { remove(key) } else edit { putLong(key, value) } }
    override fun getInt(key: String, defaultValue: Int): Int = get().getInt(key, defaultValue)
    override fun putInt(key: String, value: Int) = edit { putInt(key, value) }
    fun resetForTests() { prefs = null }
    fun adPolicy(context: Context) = Ads.policy(context)
}
