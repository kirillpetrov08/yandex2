package com.copilka.app.ads

import com.copilka.app.BuildConfig

/**
 * Идентификаторы рекламных блоков и режим тестирования.
 * Значения приходят из gradle.properties через BuildConfig (см. app/build.gradle.kts).
 */
data class AdUnits(
    val testMode: Boolean,
    val bannerUnitId: String,
    val interstitialUnitId: String,
    val rewardedUnitId: String,
) {

    /** Все три блока заполнены и имеют допустимый вид. */
    val isValid: Boolean
        get() = isValidUnitId(bannerUnitId) &&
            isValidUnitId(interstitialUnitId) &&
            isValidUnitId(rewardedUnitId)

    /** Демо-блок в релизной сборке = показы без дохода. Такую конфигурацию показываем как ошибку. */
    val hasDemoUnitsInRelease: Boolean
        get() = !testMode && (
            isDemoUnitId(bannerUnitId) || isDemoUnitId(interstitialUnitId) || isDemoUnitId(rewardedUnitId)
            )

    companion object {
        val DEMO_UNIT_IDS: Set<String> = setOf(
            "demo-banner-yandex",
            "demo-interstitial-yandex",
            "demo-rewarded-yandex",
        )

        /** Реальный ID блока РСЯ выглядит как R-M-1234567-1. */
        private val REAL_UNIT_ID_PATTERN = Regex("^R-M-\\d{4,}-\\d{1,3}$")

        fun isDemoUnitId(id: String): Boolean = id in DEMO_UNIT_IDS

        fun isValidUnitId(id: String): Boolean =
            id.isNotBlank() && (isDemoUnitId(id) || REAL_UNIT_ID_PATTERN.matches(id))

        fun fromBuildConfig(): AdUnits = AdUnits(
            testMode = BuildConfig.ADS_TEST_MODE,
            bannerUnitId = BuildConfig.ADS_BANNER_UNIT_ID,
            interstitialUnitId = BuildConfig.ADS_INTERSTITIAL_UNIT_ID,
            rewardedUnitId = BuildConfig.ADS_REWARDED_UNIT_ID,
        )
    }
}
