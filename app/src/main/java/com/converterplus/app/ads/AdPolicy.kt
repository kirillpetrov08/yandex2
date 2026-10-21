package com.converterplus.app.ads

/** Источник времени. Внедряется, чтобы политику можно было тестировать без ожидания. */
fun interface Clock {
    fun now(): Long
}

/** Системные часы по умолчанию. */
object SystemClock : Clock {
    override fun now(): Long = System.currentTimeMillis()
}

/**
 * Хранилище счётчиков политики. Реализация на SharedPreferences — в [ConsentManager],
 * в тестах — простая in-memory версия.
 */
interface AdPolicyStore {
    fun getLong(key: String): Long?
    fun putLong(key: String, value: Long?)
    fun getInt(key: String, defaultValue: Int): Int
    fun putInt(key: String, value: Int)
}

/** Настраиваемые параметры частотных ограничений. */
data class AdPolicyConfig(
    val interstitialEveryNthConversion: Int = 3,
    val interstitialMinIntervalMillis: Long = 120_000L,
    val noAdsDurationMillis: Long = 30L * 60L * 1000L,
)

/**
 * Частотная политика рекламы. Чистая логика: ни Android, ни SDK.
 *
 * Правила:
 *  - межстраничная показывается не чаще, чем каждая [AdPolicyConfig.interstitialEveryNthConversion]-я
 *    конвертация, и не раньше, чем через [AdPolicyConfig.interstitialMinIntervalMillis] после прошлого показа;
 *  - «без рекламы» действует ограниченное время; метки времени — nullable,
 *    никаких сентинелов вида 0L («ещё не показывали») не используется.
 */
class AdPolicy(
    private val store: AdPolicyStore,
    private val clock: Clock,
    private val config: AdPolicyConfig,
) {

    /** Сколько конвертаций прошло с последнего показа межстраничной. */
    fun conversionsSinceLastInterstitial(): Int =
        store.getInt(KEY_CONVERSIONS_SINCE_INTERSTITIAL, 0).coerceAtLeast(0)

    fun noteConversionFinished() {
        store.putInt(KEY_CONVERSIONS_SINCE_INTERSTITIAL, conversionsSinceLastInterstitial() + 1)
    }

    /** Можно ли показать межстраничную прямо сейчас. Вызывается после закрытия шита с результатом. */
    fun canShowInterstitial(): Boolean {
        if (isAdsFreeActive()) return false
        if (conversionsSinceLastInterstitial() < config.interstitialEveryNthConversion) return false
        val lastShown = store.getLong(KEY_LAST_INTERSTITIAL_AT)
        if (lastShown != null && clock.now() - lastShown < config.interstitialMinIntervalMillis) return false
        return true
    }

    fun noteInterstitialShown() {
        store.putLong(KEY_LAST_INTERSTITIAL_AT, clock.now())
        store.putInt(KEY_CONVERSIONS_SINCE_INTERSTITIAL, 0)
    }

    /** Награда за Rewarded: включаем режим без рекламы. */
    fun activateAdsFreePeriod() {
        store.putLong(KEY_ADS_FREE_UNTIL, clock.now() + config.noAdsDurationMillis)
    }

    /** Момент, до которого реклама скрыта, либо null. Просроченное значение не возвращается. */
    fun adsFreeUntil(): Long? =
        store.getLong(KEY_ADS_FREE_UNTIL)?.takeIf { it > clock.now() }

    fun isAdsFreeActive(): Boolean = adsFreeUntil() != null

    /** Баннер и другие форматы показываются только когда режим без рекламы не активен. */
    fun isAdsAllowed(): Boolean = !isAdsFreeActive()

    /** Сколько осталось до конца периода без рекламы, в целых минутах (округление вверх). */
    fun adsFreeMinutesLeft(): Int {
        val until = adsFreeUntil() ?: return 0
        val millisLeft = (until - clock.now()).coerceAtLeast(0L)
        val minutes = (millisLeft + 59_999L) / 60_000L
        return minutes.toInt()
    }

    companion object {
        const val KEY_CONVERSIONS_SINCE_INTERSTITIAL = "ads_conversions_since_interstitial"
        const val KEY_LAST_INTERSTITIAL_AT = "ads_last_interstitial_at"
        const val KEY_ADS_FREE_UNTIL = "ads_free_until"
    }
}
