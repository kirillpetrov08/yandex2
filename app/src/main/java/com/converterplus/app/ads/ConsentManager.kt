package com.converterplus.app.ads

import android.content.Context
import android.content.SharedPreferences

/**
 * Персонализация рекламы и отметка, что пользователь видел экран про рекламу.
 * Показ рекламы от этого не зависит: SDK стартует при запуске приложения.
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
     * Значение, которое уходит в YandexAds.setUserConsent().
     * Влияет только на персонализацию: реклама показывается в любом случае.
     */
    fun userConsentForSdk(context: Context): Boolean = isPersonalizedAds(context)
}
