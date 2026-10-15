# Что делает человек руками — 8 пунктов

1. **Открыть проект в Android Studio** и дождаться Gradle Sync
   (нужен установленный Android SDK с платформой Android 36).
   Проверка: `./gradlew testDebugUnitTest` и `./gradlew assembleDebug` проходят без ошибок.

2. **Создать два ключа подписи** один раз и сохранить каждый в двух местах:
   ```bash
   # ключ загрузки — им CI подписывает AAB (уходит в секреты GitHub)
   keytool -genkeypair -v -keystore upload-keystore.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
   # ключ подписи приложения — уходит в RuStore и Google Play, в CI не попадает
   keytool -genkeypair -v -keystore app-signing.keystore -alias sign -keyalg RSA -keysize 4096 -validity 10000 -storetype PKCS12
   ```
   Зачем два и что с ними делать — `docs/RUSTORE_SIGNING.md`.

3. **Добавить секреты в GitHub** (Settings → Secrets → Actions):
   `KEYSTORE_BASE64` (`openssl base64 -A -in upload-keystore.jks`),
   `KEYSTORE_PASSWORD`, `KEY_ALIAS` (= `upload`), `KEY_PASSWORD`.
   Затем создать воркфлоу руками через веб-интерфейс GitHub (Add file → Create new file →
   имя `.github/workflows/android.yml` → вставить содержимое `docs/ci-android.yml`):
   push этого пути от бота отклоняется, у GitHub App нет права `workflows`.

4. **Завести блоки в РСЯ** (https://partner.yandex.ru) и вписать три реальных ID
   вида `R-M-XXXXXXX-Y` в `gradle.properties`, там же поставить `ads.testMode=false`.
   Положить `docs/app-ads.txt` в корень своего домена.

5. **Заменить контакты на свои** в трёх местах:
   `app/src/main/java/com/converterplus/app/Config.kt` (почта, домен, ссылка на магазин),
   `docs/privacy_policy.html`, тексты в `docs/STORE_TEXTS.md`.

6. **Опубликовать политику конфиденциальности** — залить `docs/privacy_policy.html`
   на свой домен так, чтобы открывалась по адресу из `Config.PRIVACY_POLICY_URL`.

7. **Собрать релиз**: дождаться зелёного прогона GitHub Actions, скачать артефакт
   `build-outputs`, взять файл `app/build/outputs/bundle/release/*.aab`.

8. **Загрузить AAB в магазины и заполнить формы.** В RuStore сначала окно
   «Ключ подписи приложения»: `tools\rustore-signature.cmd --encryption-key=<ключ из окна>`
   → загрузить `rustore-out/pepk_out.zip` и `rustore-out/uploadcert.pem`, и только потом сам AAB
   (`docs/RUSTORE_SIGNING.md`). Дальше тексты из `docs/STORE_TEXTS.md` (Google Play и RuStore),
   «содержит рекламу — да», «покупок нет», скриншоты, отправка на модерацию.
