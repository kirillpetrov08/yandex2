package com.converterplus.app.ads

import android.content.Context
import android.content.SharedPreferences

/**
 * Согласие пользователя на обработку данных рекламными сервисами и передача его в SDK.
 * SDK Яндекса инициализируется только после того, как согласие получено (см. [Ads.initIfConsented]).
 */
object ConsentManager {

    private const val PREFS = "consent"
    private const val KEY_CONSENT_GIVEN = "consent_given"
    private const val KEY_PERSONALIZED = "personalized_ads"
    private const val KEY_CONSENT_TIMESTAMP = "consent_timestamp"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isConsentGiven(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CONSENT_GIVEN, false)

    fun setConsentGiven(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_CONSENT_GIVEN, value).apply()
    }

    fun isPersonalizedAds(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PERSONALIZED, false)

    fun setPersonalizedAds(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_PERSONALIZED, value).apply()
    }

    fun consentTimestamp(context: Context): Long =
        prefs(context).getLong(KEY_CONSENT_TIMESTAMP, 0L)

    fun persistConsentTimestamp(context: Context) {
        if (prefs(context).getLong(KEY_CONSENT_TIMESTAMP, 0L) == 0L) {
            prefs(context).edit().putLong(KEY_CONSENT_TIMESTAMP, System.currentTimeMillis()).apply()
        }
    }

    /**
     * Значение согласия, которое уходит в YandexAds.setUserConsent().
     * Персонализация выключена — передаём false: реклама будет, но неперсонализированная.
     */
    fun userConsentForSdk(context: Context): Boolean =
        isConsentGiven(context) && isPersonalizedAds(context)
}
