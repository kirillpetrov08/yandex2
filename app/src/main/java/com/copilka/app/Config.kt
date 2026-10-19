package com.copilka.app

object Config {
    const val SUPPORT_EMAIL = "kirillpetrov08@gmail.com"
    const val PRIVACY_POLICY_URL = "https://kirillpetrov08.github.io/copilka/privacy-policy.html"
    const val STORE_URL = "https://www.rustore.ru/catalog/app/com.copilka.app"
    const val DEVELOPER_SITE = "https://kirillpetrov08.github.io"

    /** Сколько минут реклама скрыта после rewarded. Совпадает с ads.rewardedNoAdsMinutes. */
    const val NO_ADS_MINUTES = 30

    /** Порог «большой файл»: показываем предупреждение перед фоновой работой. */
    const val LARGE_FILE_BYTES = 200L * 1024L * 1024L

    /** Порог, выше которого водяной знак на бесплатной версии слишком дорог по времени. */
    const val HUGE_FILE_BYTES = 500L * 1024L * 1024L
}
