# Публикация «Конвертер+» — пошагово

Всё, что делает человек руками, собрано в `docs/MANUAL_STEPS.md` (8 пунктов).
Ниже — подробности по каждому шагу.

---

## 1. Подготовка рабочего места (один раз)

1. Установить **Android Studio** (последняя стабильная, Koala/Ladybug или новее).
2. В Android Studio: `Open` → выбрать папку репозитория → дождаться окончания
   Gradle Sync (первый запуск качает зависимости, 5–15 минут).
3. Проверить, что установлена платформа **Android 36**:
   `SDK Manager` → `SDK Platforms` → `Android 36 (API 36)`.

Проверка локальной сборки:
```bash
./gradlew testDebugUnitTest      # 40+ юнит-тестов чистой логики
./gradlew assembleDebug          # отладочный APK
```

---

## 2. Ключ подписи (создаётся один раз, теряется навсегда)

```bash
keytool -genkeypair -v -keystore upload-keystore.jks -alias upload \
  -keyalg RSA -keysize 4096 -validity 10000
```

- Пароль — **12+ случайных символов** (слабые пароли Google Play не принимает).
- Ответы на вопросы keytool могут быть любыми, но запишите их.
- **Сохраните файл и пароли в двух разных местах** (менеджер паролей + офлайн-копия).
  Потеря ключа = невозможность выпускать обновления приложения.
- Файл `upload-keystore.jks` уже добавлен в `.gitignore` — в репозиторий он не попадёт.

Секреты для GitHub (Settings → Secrets and variables → Actions → New repository secret):

| Секрет | Значение |
|---|---|
| `KEYSTORE_BASE64` | `openssl base64 -A -in upload-keystore.jks` |
| `KEYSTORE_PASSWORD` | пароль хранилища |
| `KEY_ALIAS` | `upload` |
| `KEY_PASSWORD` | пароль ключа |

Если секретов нет, сборка всё равно проходит, но печатает
`AAB не подписан, для магазина нужны секреты`.

---

## 3. GitHub Actions

Содержимое воркфлоу лежит в репозитории в файле **`docs/ci-android.yml`**.

Gradle Wrapper (`gradlew`, `gradle/wrapper/gradle-wrapper.jar`, версия 8.11.1) уже в репозитории —
отдельно генерировать его не нужно.

> Проверено на практике: push файла `.github/workflows/android.yml` от бота отклоняется
> (`refusing to allow a GitHub App to create or update workflow ... without workflows permission`).
> Поэтому в репозитории лежит копия `docs/ci-android.yml`, а сам файл создаётся руками.

Если шаг «Установка платформы» упадёт на лицензиях, добавьте перед ним строку:
`run: yes | sdkmanager --licenses`

**Важно:** GitHub может не принять файлы в `.github/workflows/` от бота или токена
(ошибка `refusing to allow an app to create or update workflow`). Создайте файл руками:
GitHub → ваш репозиторий → `Add file` → `Create new file` →
в имени введите `.github/workflows/android.yml` → вставьте содержимое `docs/ci-android.yml` → `Commit`.

Проверка: вкладка **Actions** → запуск `Android CI` → все шаги зелёные,
в артефактах `build-outputs` лежат APK и AAB.

---

## 4. Реклама РСЯ

1. Зарегистрироваться в РСЯ как разработчик мобильных приложений:
   https://partner.yandex.ru → «Мобильная реклама».
2. Создать приложение, указать пакет `com.converterplus.app` и ссылку на магазин.
3. Создать **три** блока и получить ID вида `R-M-XXXXXXX-Y`:
   - баннер (320×50 / адаптивный);
   - межстраничная реклама;
   - rewarded (реклама за вознаграждение).
4. Вписать ID в `gradle.properties`:
   ```properties
   ads.testMode=false
   ads.bannerUnitId=R-M-XXXXXXX-1
   ads.interstitialUnitId=R-M-XXXXXXX-2
   ads.rewardedUnitId=R-M-XXXXXXX-3
   ```
   Если `ads.testMode=false`, а ID остались демо-версиями, сборка **упадёт** с понятным
   сообщением — это защита от релиза без дохода.
5. Положить `app-ads.txt` (шаблон: `docs/app-ads.txt`) в корень домена разработчика:
   `https://ваш-домен/app-ads.txt`. Без него показы могут не тарифицироваться.
6. Пройти модерацию приложения в РСЯ. До модерации показы идут в тестовом режиме без дохода.

Проверить в релизной сборке (logcat, тег `ConverterAds`):
- `SDK готов: ... testMode=false`;
- нет строк `В релизной сборке остались демо-блоки`.

---

## 5. Политика конфиденциальности

1. Заменить в `Config.kt` и в текстах магазинов `converterplus.example.com` на свой домен
   и `support.converterplus@gmail.com` на свою почту.
2. Опубликовать `docs/privacy_policy.html` по адресу
   `https://ваш-домен/privacy_policy.html` (GitHub Pages, Netlify, любой хостинг).
   Страница должна открываться без авторизации.

---

## 6. Google Play

1. Создать аккаунт разработчика Google Play (25 $ единоразово).
2. `Создать приложение` → название `Конвертер+ — PDF и фото`, язык `Русский`,
   тип `Приложение`, бесплатное.
3. Заполнить карточку текстами из `docs/STORE_TEXTS.md`.
4. Загрузить AAB из артефактов CI (`app/build/outputs/bundle/release/*.aab`).
5. Заполнить разделы: «Конфиденциальность и безопасность» (по таблице из STORE_TEXTS),
   «Целевая аудитория» (13+), «Реклама» — **Да**, «Приложение для Wear» — нет.
6. Указать ссылку на политику конфиденциальности и почту поддержки.
7. Закрыть задачи в «Панели задач», отправить на проверку.

---

## 7. RuStore

1. Регистрация: https://developer.rustore.ru (нужен телефон и почта).
2. `Добавить приложение` → загрузить тот же AAB (или подписанный APK).
3. Тексты — из `docs/STORE_TEXTS.md`, раздел RuStore.
4. Встроенные покупки — **не указывать**: по правилам RuStore с 01.02.2026 платные механики
   могут публиковать только ИП и юрлица.
5. Отправить на модерацию.

---

## 8. Что проверить перед каждой публикацией

- [ ] `./gradlew testDebugUnitTest` — все тесты зелёные
- [ ] `ads.testMode=false` и три реальных ID в `gradle.properties`
- [ ] versionCode увеличен, versionName обновлён в `app/build.gradle.kts`
- [ ] AAB подписан (шаг «Проверка подписи AAB» в CI зелёный)
- [ ] Политика конфиденциальности открывается по ссылке
- [ ] `app-ads.txt` доступен по адресу домена
- [ ] В описании магазина нет нереализованных форматов
