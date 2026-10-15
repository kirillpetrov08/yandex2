package com.converterplus.app

/**
 * Внешние константы приложения: контакты, адрес политики и данные для рекламы РСЯ.
 * Все значения уже заполнены боевыми — перед публикацией менять ничего не нужно
 * (см. docs/RUSTORE_DEPLOY.md и docs/STEP_BY_STEP.md).
 */
object Config {

    /** Почта для пункта «Написать разработчику» и для карточки в магазине. */
    const val SUPPORT_EMAIL = "kirillpetrov08@gmail.com"

    /**
     * Адрес опубликованной политики конфиденциальности.
     *
     * Страница лежит на сайте разработчика (GitHub Pages репозитория `kirillpetrov08.github.io`,
     * файл `converterplus/privacy-policy.html`) и открывается без авторизации.
     * Копия страницы для истории лежит в репозитории приложения: `docs/privacy_policy.html`.
     *
     * Требование RuStore (п. 5.1) и 152-ФЗ: ссылка должна открываться и из карточки магазина,
     * и изнутри приложения — эта константа используется в онбординге и в настройках.
     */
    const val PRIVACY_POLICY_URL = "https://kirillpetrov08.github.io/converterplus/privacy-policy.html"

    /** Адрес приложения в магазине. ЗАМЕНИТЬ на точную ссылку из карточки после публикации. */
    const val STORE_URL = "https://www.rustore.ru/catalog/app/com.converterplus.app"

    /**
     * Домен разработчика, на котором лежит app-ads.txt (требование РСЯ).
     * Файл опубликован в корне сайта: https://kirillpetrov08.github.io/app-ads.txt
     * и содержит строку партнёра: `yandex.com, 110347355, DIRECT`.
     */
    const val APP_ADS_DOMAIN = "kirillpetrov08.github.io"

    /** Сколько минут рекламы убирается за просмотр Rewarded. */
    const val NO_ADS_MINUTES = 30

    /** Порог, после которого предупреждаем о долгой конвертации. */
    const val LARGE_FILE_BYTES = 200L * 1024L * 1024L

    /** Порог, после которого Rewarded предлагается перед конвертацией. */
    const val HUGE_FILE_BYTES = 100L * 1024L * 1024L
}
