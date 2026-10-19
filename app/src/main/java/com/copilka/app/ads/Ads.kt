package com.copilka.app.ads

import android.app.Application
import android.content.Context
import com.copilka.app.BuildConfig
import com.copilka.app.data.SettingsStore
import com.yandex.mobile.ads.common.YandexAds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Единая точка входа в рекламу. Весь SDK Яндекса изолирован в пакете [com.copilka.app.ads]:
 * остальное приложение знает только про «показать баннер», «показать межстраничную», «показать rewarded».
 *
 * Порядок инициализации строго такой:
 * 1. пользователь дал согласие (третий экран онбординга);
 * 2. [YandexAds.setUserConsent] / [YandexAds.setLocationTracking] / [YandexAds.setAgeRestricted];
 * 3. [YandexAds.initialize].
 * В манифесте AUTOMATIC_SDK_INITIALIZATION=false, поэтому до шага 3 SDK не стартует.
 */
object Ads {

    val units: AdUnits = AdUnits.fromBuildConfig()

    private val _initialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _initialized.asStateFlow()

    private val _adsAllowed = MutableStateFlow(true)

    /** Можно ли показывать рекламу сейчас (согласие дано и режим «без рекламы» не активен). */
    val adsAllowed: StateFlow<Boolean> = _adsAllowed.asStateFlow()

    private var initStarted = false

    fun policy(context: Context): AdPolicy = AdPolicy(
        store = SettingsStore,
        clock = SystemClock,
        config = AdPolicyConfig(
            interstitialEveryNthConversion = BuildConfig.ADS_INTERSTITIAL_EVERY_NTH_CONVERSION,
            interstitialMinIntervalMillis = BuildConfig.ADS_INTERSTITIAL_MIN_INTERVAL_SECONDS * 1000L,
            noAdsDurationMillis = BuildConfig.ADS_REWARDED_NO_ADS_MINUTES * 60_000L,
        ),
    )

    /** Обновляет признак «реклама разрешена». Вызывается при смене экрана и после награды. */
    fun refreshAdsAllowed(context: Context) {
        val allowed = ConsentManager.isConsentGiven(context) && policy(context).isAdsAllowed()
        if (_adsAllowed.value != allowed) _adsAllowed.value = allowed
    }

    /** Инициализация SDK, если согласие уже получено. Повторные вызовы игнорируются. */
    fun initIfConsented(app: Application) {
        if (!ConsentManager.isConsentGiven(app)) {
            AdsLog.info("init", "пропущено: согласие не получено")
            _adsAllowed.value = false
            return
        }
        if (initStarted) {
            refreshAdsAllowed(app)
            return
        }
        initStarted = true

        if (units.hasDemoUnitsInRelease) {
            AdsLog.error("config", "В релизной сборке остались демо-блоки: дохода не будет")
        }
        if (!units.isValid) {
            AdsLog.error("config", "Некорректные ID рекламных блоков: ${units}")
        }

        try {
            YandexAds.setUserConsent(ConsentManager.userConsentForSdk(app))
            YandexAds.setLocationTracking(false)
            YandexAds.setAgeRestricted(false)
            YandexAds.initialize(app) {
                _initialized.value = true
                refreshAdsAllowed(app)
                AdsLog.info("init", "SDK готов, testMode=${units.testMode}")
            }
        } catch (t: Throwable) {
            initStarted = false
            AdsLog.error("init", t.message ?: "неизвестная ошибка", t)
        }
    }

    private val interstitialController: InterstitialAdController by lazy {
        InterstitialAdController(units.interstitialUnitId)
    }

    private val rewardedController: RewardedAdController by lazy {
        RewardedAdController(units.rewardedUnitId)
    }

    /** Один инстанс контроллера на приложение: объект одноразовый, но создаётся один раз. */
    fun interstitial(): InterstitialAdController = interstitialController

    fun rewarded(): RewardedAdController = rewardedController

    /** Предзагрузка межстраничной, если реклама сейчас разрешена. */
    fun maybePreloadInterstitial(context: Context) {
        if (_initialized.value && policy(context).isAdsAllowed()) {
            interstitialController.preload(context)
        }
    }

    fun maybePreloadRewarded(context: Context) {
        if (_initialized.value) rewardedController.preload(context)
    }

    /** Ширина баннера в dp: вся доступная ширина экрана. */
    fun bannerWidthDp(context: Context): Int {
        val metrics = context.resources.displayMetrics
        return (metrics.widthPixels / metrics.density).toInt().coerceIn(280, 720)
    }
}
