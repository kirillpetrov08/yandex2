package com.converterplus.app

/**
 * Внешние константы приложения. Значения, помеченные «ЗАМЕНИТЬ», заказчик меняет перед публикацией
 * (см. docs/RUSTORE_DEPLOY.md).
 */
object Config {

    /** Почта для пункта «Написать разработчику» и для карточки в магазине. ЗАМЕНИТЬ на свою. */
    const val SUPPORT_EMAIL = "support.converterplus@gmail.com"

    /**
     * Адрес опубликованной политики конфиденциальности.
     *
     * По умолчанию — GitHub Pages этого репозитория: Settings → Pages → Source = Deploy from a branch,
     * Branch = main, Folder = /docs. Тогда файл `docs/privacy_policy.html` открывается по этому адресу.
     * Если политика лежит на своём домене — поменяйте строку на свой адрес.
     */
    const val PRIVACY_POLICY_URL = "https://kirillpetrov08.github.io/yandex2/privacy_policy.html"

    /** Адрес приложения в магазине. ЗАМЕНИТЬ на точную ссылку из карточки после публикации. */
    const val STORE_URL = "https://www.rustore.ru/catalog/app/com.converterplus.app"

    /**
     * Домен разработчика, на котором должен лежать app-ads.txt (требование РСЯ).
     * При публикации через GitHub Pages — это <логин>.github.io, а файл `docs/app-ads.txt`
     * становится доступен как https://kirillpetrov08.github.io/yandex2/app-ads.txt.
     */
    const val APP_ADS_DOMAIN = "kirillpetrov08.github.io"

    /** Сколько минут рекламы убирается за просмотр Rewarded. */
    const val NO_ADS_MINUTES = 30

    /** Порог, после которого предупреждаем о долгой конвертации. */
    const val LARGE_FILE_BYTES = 200L * 1024L * 1024L

    /** Порог, после которого Rewarded предлагается перед конвертацией. */
    const val HUGE_FILE_BYTES = 100L * 1024L * 1024L
}
