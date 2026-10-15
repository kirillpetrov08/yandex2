package com.converterplus.app.ads

import android.app.Activity
import android.content.Context
import com.yandex.mobile.ads.common.AdError
import com.yandex.mobile.ads.common.AdRequest
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData
import com.yandex.mobile.ads.rewarded.Reward
import com.yandex.mobile.ads.rewarded.RewardedAd
import com.yandex.mobile.ads.rewarded.RewardedAdEventListener
import com.yandex.mobile.ads.rewarded.RewardedAdLoadListener
import com.yandex.mobile.ads.rewarded.RewardedAdLoader

/**
 * Реклама за вознаграждение.
 *
 * Главное правило: награда выдаётся СТРОГО в onRewarded() — единственном колбэке, который
 * SDK вызывает после просмотра ролика до конца. Если пользователь закрыл ролик раньше —
 * награды нет, и никаких упрёков в интерфейсе (см. [RewardedOutcome.NOT_REWARDED]).
 */
class RewardedAdController(private val unitId: String) {

    private var loader: RewardedAdLoader? = null
    private var loadedAd: RewardedAd? = null

    @Volatile
    var state: AdLoadState = AdLoadState.Idle
        private set

    fun preload(context: Context) {
        if (!AdUnits.isValidUnitId(unitId)) {
            state = AdLoadState.Failed(-1, "Некорректный ID блока: $unitId")
            return
        }
        if (loadedAd != null) return

        val adLoader = RewardedAdLoader(context.applicationContext)
        loader = adLoader
        state = AdLoadState.Loading
        adLoader.loadAd(
            AdRequest.Builder(unitId).build(),
            object : RewardedAdLoadListener {
                override fun onAdLoaded(ad: RewardedAd) {
                    loadedAd = ad
                    state = AdLoadState.Ready
                    AdsLog.info("rewarded", "загружена")
                }

                override fun onAdFailedToLoad(error: AdRequestError) {
                    state = AdLoadState.Failed(error.code, error.description.orEmpty())
                    AdsLog.warn("rewarded", error.code, error.description.orEmpty())
                }
            },
        )
    }

    /**
     * @param onOutcome вызывается один раз в любом случае: награда выдана / не выдана / нечего показать.
     */
    fun showIfReady(activity: Activity, onOutcome: (RewardedOutcome) -> Unit): Boolean {
        val ad = loadedAd ?: return false
        loadedAd = null
        state = AdLoadState.Idle

        var rewarded = false
        ad.setAdEventListener(
            object : RewardedAdEventListener {
                override fun onRewarded(reward: Reward) {
                    rewarded = true
                    AdsLog.info("rewarded", "награда выдана: ${reward.amount} ${reward.type}")
                }

                override fun onAdShown() {
                    AdsLog.info("rewarded", "показана")
                }

                override fun onAdDismissed() {
                    onOutcome(if (rewarded) RewardedOutcome.REWARDED else RewardedOutcome.NOT_REWARDED)
                }

                override fun onAdFailedToShow(adError: AdError) {
                    AdsLog.warn("rewarded", null, adError.description.orEmpty())
                    onOutcome(RewardedOutcome.FAILED)
                }

                override fun onAdClicked() {
                    AdsLog.info("rewarded", "клик")
                }

                override fun onAdImpression(data: ImpressionData?) {
                    AdsLog.info("rewarded", "показ засчитан")
                }
            },
        )
        try {
            ad.show(activity)
        } catch (t: Throwable) {
            AdsLog.error("rewarded", t.message ?: "show failed", t)
            onOutcome(RewardedOutcome.FAILED)
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

enum class RewardedOutcome {
    REWARDED,
    NOT_REWARDED,
    FAILED,
}
