# Что делает человек руками — 8 пунктов

1. **Открыть проект в Android Studio** и дождаться Gradle Sync
   (нужен установленный Android SDK с платформой Android 36).
   Проверка: `./gradlew testDebugUnitTest` и `./gradlew assembleDebug` проходят без ошибок.

2. **Создать ключ подписи** один раз и сохранить его в двух местах:
   ```bash
   keytool -genkeypair -v -keystore upload-keystore.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
   ```

3. **Добавить секреты в GitHub** (Settings → Secrets → Actions):
   `KEYSTORE_BASE64` (`openssl base64 -A -in upload-keystore.jks`),
   `KEYSTORE_PASSWORD`, `KEY_ALIAS` (= `upload`), `KEY_PASSWORD`.
   Если файл `.github/workflows/android.yml` не принял push от бота — создать его руками
   через веб-интерфейс GitHub (Add file → Create new file), содержимое уже готово.

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

8. **Загрузить AAB в магазины и заполнить формы** текстами из `docs/STORE_TEXTS.md`
   (Google Play и RuStore), указать «содержит рекламу — да», «покупок нет»,
   приложить скриншоты и отправить на модерацию.
