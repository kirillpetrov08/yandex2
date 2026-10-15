package com.converterplus.app.ads

import android.app.Activity
import android.content.Context
import com.yandex.mobile.ads.interstitial.GenericAdEventListener
import com.yandex.mobile.ads.interstitial.InterstitialAd
import com.yandex.mobile.ads.interstitial.InterstitialAdLoadListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoader
import com.yandex.mobile.ads.common.AdFailureError
import com.yandex.mobile.ads.common.AdRequest
import com.yandex.mobile.ads.common.AdRequestError

/**
 * Межстраничная реклама.
 *
 * Правила, которые здесь зашиты:
 *  - объект одноразовый: показали — загружаем новый;
 *  - показ только по решению [AdPolicy.canShowInterstitial] (проверяет вызывающая сторона);
 *  - никогда при запуске приложения и во время конвертации (проверяет вызывающая сторона).
 */
class InterstitialAdController(private val unitId: String) {

    private var loader: InterstitialAdLoader? = null
    private var loadedAd: InterstitialAd? = null

    @Volatile
    var state: AdLoadState = AdLoadState.Idle
        private set

    fun preload(context: Context) {
        if (!AdUnits.isValidUnitId(unitId)) {
            state = AdLoadState.Failed(-1, "Некорректный ID блока: $unitId")
            return
        }
        if (loadedAd != null) return

        val adLoader = InterstitialAdLoader(context.applicationContext)
        loader = adLoader
        state = AdLoadState.Loading
        adLoader.loadAd(
            AdRequest.Builder(unitId).build(),
            object : InterstitialAdLoadListener {
                override fun onAdLoaded(ad: InterstitialAd) {
                    loadedAd = ad
                    state = AdLoadState.Ready
                    AdsLog.info("interstitial", "загружена")
                }

                override fun onAdFailedToLoad(error: AdRequestError) {
                    state = AdLoadState.Failed(error.code, error.description.orEmpty())
                    AdsLog.warn("interstitial", error.code, error.description.orEmpty())
                }
            },
        )
    }

    /**
     * Показывает рекламу, если она загружена.
     * @return true — реклама показана (дальнейший поток приложения продолжится в onAdDismissed),
     *         false — показывать нечего, вызывающая сторона продолжает сценарий сразу.
     */
    fun showIfReady(activity: Activity, onFinished: () -> Unit): Boolean {
        val ad = loadedAd ?: return false
        loadedAd = null
        state = AdLoadState.Idle

        ad.setAdEventListener(
            object : GenericAdEventListener() {
                override fun onAdDismissed() {
                    AdsLog.info("interstitial", "закрыта")
                    onFinished()
                }

                override fun onAdFailedToShow(error: AdFailureError) {
                    AdsLog.warn("interstitial", error.code, error.description.orEmpty())
                    onFinished()
                }
            },
        )
        try {
            ad.show(activity)
        } catch (t: Throwable) {
            AdsLog.error("interstitial", t.message ?: "show failed", t)
            onFinished()
            return false
        }
        return true
    }

    fun release() {
        loadedAd = null
        loader = null
        state = AdLoadState.Idle
    }
}
