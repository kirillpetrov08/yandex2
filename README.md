# Копилка — Финансы офлайн

Офлайн-учёт расходов и доходов для Android. Всё хранится на устройстве, без интернета и без разрешений на хранилище.

- Пакет: `com.copilka.app`
- minSdk 26 (Android 8.0), targetSdk 36, compileSdk 36
- Kotlin 2.1.20, Compose M3, AGP 8.9.2, Gradle 8.11.1, JVM 17
- Монетизация: только Яндекс РСЯ (баннер, межстраничная, rewarded), без покупок

## Что умеет

- Счета: Наличные, Карта, Накопления
- Категории: Продукты, Транспорт, Жильё, Подписки, Развлечения, Здоровье + доходы
- Операции: расход/доход/перевод, сумма, категория, счёт, дата, комментарий, фото чека
- Бюджеты: месячные лимиты по категориям с прогресс-барами и превышением
- История: поиск, фильтры, группы Сегодня / Вчера / Ранее
- Статистика: графики (pie по категориям, bar по периодам), баланс, итоги
- Повторяющиеся платежи: ежедневно/еженедельно/ежемесячно (WorkManager)
- Экспорт: CSV/PDF через MediaStore/Downloads или SAF-папку (без storage-разрешений)
- Онбординг 3 экрана + согласие на персонализированную рекламу

## Реклама

- SDK стартует только после согласия (онбординг 3)
- AdPolicy: interstitial — каждая 3-я операция и ≥120 сек, nullable timestamps, Clock инжекция
- Баннер: Главная, История, Статистика — фиксированная высота, ≥12dp от кнопок, никогда в онбординге/настройках/процессе/шите
- Interstitial — после закрытия шита результата
- Rewarded — по кнопке, 30 мин без рекламы в `onAdRewarded()`
- Конфиг в `gradle.properties` (`ads.testMode`, ID, пороги)

Настроено: `ads.testMode=false` и боевые ID `R-M-20230616-2/-4/-7`. Для своего приложения замените ID на свои из кабинета РСЯ.

## Файлы и разрешения

- Выбор чека: `PickVisualMedia` / `OpenDocument` без разрешений
- Кэш чистится при старте (файлы старше 24 ч)
- Сохранение: API 29+ → MediaStore.Downloads; своя папка → SAF; API 26-28 без папки → системный диалог
- Разрешения: `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`

## Структура

```
app/src/main/java/com/copilka/app/
├── CopilkaApp.kt               Application + WorkManager
├── MainActivity.kt             портрет, Compose
├── Config.kt                   почта, политика, магазин
├── ads/                        реклама изолирована
├── data/FinanceStore.kt        transactions.tsv, атомарная запись
├── engine/finance/             Money, Category, Account, Transaction, Calculator
├── storage/FileSaver.kt        MediaStore/SAF
├── work/                       повторяющиеся платежи, уведомления
└── ui/theme, components, navigation, screens (home/history/stats/settings/onboarding/removeads)
```

## Быстрый старт

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew bundleRelease
python3 tools/verify_stage1.py
```

## Контакты

- Почта: kirillpetrov08@gmail.com
- Политика: https://kirillpetrov08.github.io/copilka/privacy-policy.html
- Сайт: https://kirillpetrov08.github.io
- app-ads.txt: yandex.com, 110347355, DIRECT
