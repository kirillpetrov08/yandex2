# Что делает человек руками — 8 пунктов

Подробная версия каждого пункта с точными командами и значениями полей — `docs/STEP_BY_STEP.md`.

1. **Открыть проект в Android Studio** и дождаться Gradle Sync
   (нужен установленный Android SDK с платформой Android 36).
   Проверка: `./gradlew testDebugUnitTest` и `./gradlew assembleDebug` проходят без ошибок.

2. **Создать ключ подписи приложения** один раз и сохранить файл с паролем в двух местах:
   ```bash
   keytool -genkeypair -v -keystore app-signing.keystore -alias sign -keyalg RSA -keysize 4096 -validity 10000 -storetype PKCS12
   ```
   Этим ключом подписывается APK для RuStore. Второй ключ (ключ загрузки `upload-keystore.jks`)
   нужен **только** если будете загружать AAB (Google Play или RuStore через окно PEPK) —
   тогда порядок в `docs/RUSTORE_SIGNING.md`.

3. **Добавить секреты в GitHub** (Settings → Secrets and variables → Actions):
   `KEYSTORE_BASE64` (base64 от **app-signing.keystore** — тогда CI-сборка APK тоже годится
   для RuStore), `KEYSTORE_PASSWORD`, `KEY_ALIAS` (= `sign`), `KEY_PASSWORD`.
   Затем создать воркфлоу руками через веб-интерфейс GitHub (Add file → Create new file →
   имя `.github/workflows/android.yml` → вставить содержимое `docs/ci-android.yml`):
   push этого пути от бота отклоняется, у GitHub App нет права `workflows`.

4. **Проверить блоки РСЯ** (https://partner.yandex.ru): для приложения «конвертатор» уже
   созданы баннер, межстраничная и rewarded, а их ID (`R-M-20230616-2`, `-4`, `-7`) вписаны
   в `gradle.properties` вместе с `ads.testMode=false` — вписывать ничего не нужно.
   Останется положить `docs/app-ads.txt` на сайт разработчика (включить GitHub Pages:
   Settings → Pages → Deploy from a branch, main, /docs) и после публикации в RuStore
   добавить ссылку на карточку в кабинете РСЯ.
   Доход начнётся только после статуса «Активно» (модерация до 2 дней) — `docs/APK_RELEASE.md`.

5. **Заменить контакты на свои**: почту `support.converterplus@gmail.com` в
   `app/src/main/java/com/converterplus/app/Config.kt`, `docs/privacy_policy.html`,
   `docs/STORE_TEXTS.md`. Там же — ссылка на карточку магазина (`Config.STORE_URL`)
   после публикации.

6. **Включить GitHub Pages** (Settings → Pages → Deploy from a branch → main → /docs):
   после этого политика откроется по адресу из `Config.PRIVACY_POLICY_URL`, а `app-ads.txt`
   — по адресу домена разработчика для РСЯ. Если хостите сами — залейте эти два файла
   на свой домен и поправьте константы в `Config.kt`.

7. **Собрать релиз**: дождаться зелёного прогона GitHub Actions, скачать артефакт
   `build-outputs`. Для Google Play взять `app/build/outputs/bundle/release/*.aab`,
   для RuStore можно взять APK `app/build/outputs/apk/release/app-release.apk`
   (как собрать APK локально и что проверить — `docs/APK_RELEASE.md`).
   Перед каждой загрузкой поднимать `versionCode` в `app/build.gradle.kts`.

8. **Загрузить AAB в магазины и заполнить формы.** В RuStore сначала окно
   «Ключ подписи приложения»: `tools\rustore-signature.cmd --encryption-key=<ключ из окна>`
   → загрузить `rustore-out/pepk_out.zip` и `rustore-out/uploadcert.pem`, и только потом сам AAB
   (`docs/RUSTORE_SIGNING.md`). Дальше тексты из `docs/STORE_TEXTS.md` (Google Play и RuStore),
   «содержит рекламу — да», «покупок нет», скриншоты, отправка на модерацию.
   Полный порядок для RuStore — в `docs/RUSTORE_DEPLOY.md` (если грузите APK, а не AAB,
   файл подписывается вашим ключом приложения, окно PEPK не нужно).
