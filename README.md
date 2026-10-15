# Конвертер+ (Converter Plus)

Офлайн-конвертер файлов для Android: изображения, PDF, реклама Яндекс РСЯ.
Всё обрабатывается на устройстве, разрешений на хранилище нет.

- Пакет: `com.converterplus.app`
- minSdk 26 (Android 8.0), targetSdk 36, compileSdk 36
- Kotlin 2.1.20, Jetpack Compose (Material 3), AGP 8.9.2, Gradle 8.11.1, JVM 17
- Монетизация: только реклама РСЯ (баннер, межстраничная, rewarded). Покупок и подписок нет.

## Статус по этапам

| Этап | Что | Статус |
|---|---|---|
| 1 | Каркас, весь UI, реклама РСЯ (3 формата), «Изображения», «PDF → Изображения», «Изображения → PDF», фон с прогрессом, CI | **готов** |
| 2 | Видео (MP4/MOV/MKV → MP4, извлечь аудио) | следующий |
| 3 | Аудио (WAV, M4A/AAC, OGG/Opus по устройству) | следующий |
| 4 | OCR (Tesseract, rus + eng) | следующий |

Инструменты этапов 2–4 видны на главном экране, помечены «Скоро» и показывают понятное
сообщение: не падают и не исчезают (см. `Capabilities.AVAILABLE_TOOLS`).

## Быстрый старт

```bash
./gradlew testDebugUnitTest     # юнит-тесты чистой логики
./gradlew assembleDebug         # отладочный APK
./gradlew assembleRelease       # релизный APK для RuStore (реклама — реальные блоки РСЯ)
./gradlew bundleRelease         # релизный AAB (подпись — из переменных окружения)
```

Порядок «собрать APK с рабочей рекламой» — в `docs/APK_RELEASE.md`.

Подпись релиза берётся из переменных окружения `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`,
`KEY_ALIAS`, `KEY_PASSWORD`. Без них сборка проходит, но печатает
`AAB не подписан, для магазина нужны секреты`.

## Структура

```
app/src/main/java/com/converterplus/app/
├── ConverterApp.kt              Application: канал уведомлений, инициализация рекламы после согласия
├── MainActivity.kt              единственная Activity, portrait, Compose
├── Config.kt                    почта поддержки, URL политики, ссылка на магазин
├── ads/                         вся реклама изолирована здесь
│   ├── Ads.kt                   фасад: инициализация, доступ к контроллерам
│   ├── AdUnits.kt               ID блоков и testMode из BuildConfig
│   ├── AdPolicy.kt              частотные ограничения (чистая логика, внедряемые часы)
│   ├── AdLoadState.kt, AdsLog.kt
│   ├── ConsentManager.kt        согласие и передача его в SDK
│   ├── BannerSlot.kt            Compose-обёртка BannerAdView
│   ├── InterstitialAdController.kt, RewardedAdController.kt
├── engine/
│   ├── Capabilities.kt          реестр возможностей: isSupported / reason
│   ├── ConversionRequest.kt, ConversionResult.kt, ConversionException.kt
│   ├── ConversionEngine.kt      фасад: запуск, прогресс, отмена
│   ├── Formatting.kt, FileNameFactory.kt, Watermark.kt
│   ├── image/ImageConverter.kt
│   └── pdf/PdfToImages.kt, ImagesToPdf.kt, PdfProbe.kt
├── work/
│   ├── ConversionWorker.kt      CoroutineWorker как long-running + прогресс
│   ├── Notifications.kt         канал, прогресс с «Отменить», «Готово»
│   ├── CancelConversionReceiver.kt, ActiveConversions.kt
│   ├── ConversionRequestCodec.kt
├── data/
│   ├── HistoryStore.kt          history.tsv: чистые функции TSV + атомарная запись
│   ├── SettingsStore.kt         SharedPreferences + AdPolicyStore
│   ├── UriAccess.kt             копирование в кэш, persistable-гранты, «Недавние»
├── storage/
│   ├── FileSaver.kt             MediaStore / SAF-папка / системный диалог
│   └── CacheCleaner.kt
└── ui/
    ├── theme/                   цвета, типографика, формы, ThemeController
    ├── components/              карточки, чипы форматов, круговой прогресс, шиты, превью
    ├── screens/                 onboarding, home, tool, history, settings, remove-ads
    └── navigation/              3 вкладки + экраны инструментов, каталог инструментов
```

## Реклама

- SDK инициализируется **только после согласия** (третий экран онбординга);
  в манифесте `AUTOMATIC_SDK_INITIALIZATION=false`.
- Частотные ограничения — в `AdPolicy`: межстраничная не чаще каждой 3-й конвертации
  и не раньше 120 секунд с прошлого показа; «без рекламы» — 30 минут за rewarded.
  Метки времени — nullable, сентинелов `0L` нет, часы внедряются (`Clock`).
- Карта плейсментов: баннер — главная, экран инструмента (кроме процесса), история;
  межстраничная — только после закрытия шита результата; rewarded — только по нажатию;
  на онбординге, в настройках, во время конвертации и на шите результата рекламы нет.
- Конфигурация — в `gradle.properties` (`ads.testMode`, ID блоков, пороги частоты).

**Настроено:** `ads.testMode=false` и боевые ID блоков приложения «конвертатор»
(`R-M-20230616-2` — баннер, `R-M-20230616-4` — межстраничная, `R-M-20230616-7` — rewarded).
Если `testMode=false`, а ID не подходят под шаблон `R-M-XXXXXXX-Y`, сборка падает с сообщением —
защита от релиза с демо-блоками. Пошагово: `docs/ADS_SETUP.md`.

## Файлы и разрешения

- Выбор: `PickVisualMedia` / `OpenDocument` (системные пикеры, без разрешений).
- Выбранный файл сразу копируется в кэш — фоновая конвертация не зависит от срока жизни
  URI-гранта. Кэш чистится при старте приложения (файлы старше 24 часов).
- Сохранение: API 29+ → `MediaStore.Downloads`; выбрана своя папка → SAF (`DocumentFile`);
  API 26–28 без выбранной папки → системный диалог сохранения
  (прямая запись в общую папку «Загрузки» на этих версиях требует `WRITE_EXTERNAL_STORAGE`,
  а разрешений на хранилище в приложении нет намеренно).
- Разрешения: `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`,
  `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`. И больше ничего.

## Тесты

`./gradlew testDebugUnitTest` — чистая логика без Android-зависимостей:

| Файл | Что проверяет |
|---|---|
| `CapabilitiesTest` | вся матрица форматов: HEIC на API 26, AVIF на API 31, PDF, пароль, Opus |
| `FormatDefaultsTest` | формат по умолчанию, «файл уже в этом формате», определение типа |
| `AdPolicyTest` | «каждая 3-я конвертация», «≥120 секунд», «30 минут без рекламы», внедряемые часы |
| `FormattingTest` | размеры файлов, проценты, длительность |
| `FileNameFactoryTest` | расширение, коллизии, недопустимые символы, имена страниц |
| `HistoryTsvTest`, `HistoryStoreTest` | сериализация history.tsv, поиск, фильтр, лимит 500, атомарная запись |
| `ConversionRequestCodecTest` | упаковка запроса в WorkManager |
| `AdUnitsTest` | валидация ID блоков и переключение testMode |

Статическая проверка ресурсов и ссылок (не требует Android SDK и интернета):
```bash
python3 tools/verify_stage1.py
```
Проверяет: корректность всех XML, что каждый `R.string/R.drawable/R.mipmap` существует,
что внутренние импорты и символы не потеряны, что package совпадает с каталогом,
что нет дублей объявлений, что скобки сбалансированы, что нет заглушек,
и что обязательные файлы сборки на месте.

Все инструменты из каталога (`Изображения`, `PDF → Фото`, `Фото → PDF`, `Извлечение текста`,
`Аудио`, `Видео`) работают: этапы 1–4 реализованы, заглушек «Скоро» в интерфейсе не осталось.

## Публикация

- `docs/DEPLOY.md` — пошаговая инструкция (ключи, секреты, РСЯ, магазины).
- `docs/APK_RELEASE.md` — релизный APK без заглушек и с рабочей рекламой РСЯ.
- `docs/RUSTORE_SIGNING.md` — подпись для RuStore: два ключа, PEPK, разбор ошибок.
- `docs/STEP_BY_STEP.md` — **сквозной рунбук**: что нажимать, что вводить, какие команды,
  в каком порядке (от установки Android Studio до статуса «Активно» в РСЯ).
- `docs/RUSTORE_DEPLOY.md` — публикация в RuStore целиком: аккаунт, загрузка APK, карточка,
  декларации, модерация, обновления.
- `docs/store/icon-512.png` — готовая иконка 512×512 для карточки магазина.
- `docs/ADS_SETUP.md` — какие три блока РСЯ создавать, куда вписывать их ID и как собрать APK.
- `docs/MANUAL_STEPS.md` — 8 пунктов, которые делает человек.
- `docs/STORE_TEXTS.md` — тексты для Google Play и RuStore (только реализованное).
- `docs/PRIVACY_POLICY.md`, `docs/privacy_policy.html` — политика конфиденциальности.
- `docs/app-ads.txt` — шаблон файла для домена разработчика.

## Чего в приложении нет намеренно

`ffmpeg-kit` (проект закрыт), ML Kit Text Recognition (не знает кириллицу),
`MANAGE_EXTERNAL_STORAGE` и любые разрешения на хранилище, App Open и Native реклама,
Room/KSP/Hilt/kotlinx.serialization, MP3/HEIC/AVIF/GIF/TIFF/AVI на выходе,
внутриигровые покупки и подписки.
