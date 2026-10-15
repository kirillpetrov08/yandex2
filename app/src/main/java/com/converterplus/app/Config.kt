package com.converterplus.app

/**
 * Внешние константы приложения. Значения, помеченные «ЗАМЕНИТЬ», заказчик меняет перед публикацией
 * (см. docs/DEPLOY.md, шаг 7).
 */
object Config {

    /** Почта для пункта «Написать разработчику» и для формы Google Play. ЗАМЕНИТЬ на свою. */
    const val SUPPORT_EMAIL = "support.converterplus@gmail.com"

    /** Адрес опубликованной политики конфиденциальности. ЗАМЕНИТЬ на свой домен. */
    const val PRIVACY_POLICY_URL = "https://converterplus.example.com/privacy_policy.html"

    /** Адрес приложения в магазине. ЗАМЕНИТЬ после первой публикации. */
    const val STORE_URL = "https://play.google.com/store/apps/details?id=com.converterplus.app"

    /** Домен разработчика, на котором должен лежать app-ads.txt (требование РСЯ). */
    const val APP_ADS_DOMAIN = "converterplus.example.com"

    /** Сколько минут рекламы убирается за просмотр Rewarded. */
    const val NO_ADS_MINUTES = 30

    /** Порог, после которого предупреждаем о долгой конвертации. */
    const val LARGE_FILE_BYTES = 200L * 1024L * 1024L

    /** Порог, после которого Rewarded предлагается перед конвертацией. */
    const val HUGE_FILE_BYTES = 100L * 1024L * 1024L
}
