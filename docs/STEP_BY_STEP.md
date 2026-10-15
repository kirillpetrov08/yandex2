# Рунбук: от пустого компьютера до опубликованного приложения

Это **самый подробный** документ проекта: что нажимать, куда нажимать, какие команды
вводить и что должно получиться. Все остальные файлы в `docs/` — справочники по отдельным
темам, этот — сквозная инструкция «делай по порядку и не думай».

- Реклама уже подключена к боевым блокам (баннер `R-M-20230616-2`, межстраничная
  `R-M-20230616-4`, rewarded `R-M-20230616-7`) — в кабинете РСЯ вам останется только
  добавить ссылку на RuStore после публикации (шаг 12).
- Ключ подписи, секреты GitHub и сборку делаете один раз (шаги 1–3), дальше это 3 команды.
- Ориентировочное время: подготовка 20 мин, сборка и проверки 40 мин,
  заполнение карточки в RuStore 1–2 часа, модерация RuStore до 72 часов.

Обозначения:

| Знак | Смысл |
|---|---|
| **Где:** | в каком окне/на какой странице это делается |
| **Что ввести:** | точные значения для полей |
| **Команда:** | вставлять целиком, в командной строке **из корня проекта** |
| **Проверка:** | как убедиться, что шаг сделан правильно |

Команды даны в двух вариантах: **Windows (cmd)** и **macOS/Linux (bash)**. Для Windows
удобнее открыть «Командную строку» (Win+R → `cmd` → Enter) и перейти в папку проекта:

```cmd
cd C:\путь\к\yandex2
```

Проверить, что вы в корне проекта (должны быть видны `gradlew.bat`, `settings.gradle.kts`,
папка `app`):

```cmd
dir gradlew.bat settings.gradle.kts
```

---

## Шаг 0. Что установить и как проверить

**Где:** скачать и установить **Android Studio** — <https://developer.android.com/studio>.
При первом запуске мастер сам доставит Android SDK. Никакой отдельный JDK ставить не нужно:
внутри Android Studio уже лежит нужная Java 17.

Стандартные пути, которые используются дальше:

| Инструмент | Windows | macOS |
|---|---|---|
| Java (JDK 17) | `C:\Program Files\Android\Android Studio\jbr` | `/Applications/Android Studio.app/Contents/jbr/Contents/Home` |
| `adb` | `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe` | `~/Library/Android/sdk/platform-tools/adb` |
| `apksigner` | `%LOCALAPPDATA%\Android\Sdk\build-tools\36.0.0\apksigner.bat` | `~/Library/Android/sdk/build-tools/36.0.0/apksigner` |
| `keytool` | `%ProgramFiles%\Android\Android Studio\jbr\bin\keytool.exe` | `/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/keytool` |

**Команда (Windows)** — проверить Java и Gradle:

```cmd
set "JAVA_HOME=%ProgramFiles%\Android\Android Studio\jbr"
"%JAVA_HOME%\bin\java" -version
gradlew.bat --version
```

**Что должно получиться:** Java 17.x, Gradle **8.11.1**.

Если Android Studio не хотите ставить вообще — можно всё собрать через CI на GitHub
(шаг 2.5), но телефон для проверки рекламы всё равно понадобится.

---

## Шаг 1. Один раз создать ключ подписи приложения

Этот ключ — единственный, которым подписывается APK для RuStore. Потеря ключа = потеря
возможности выпускать обновления, поэтому сразу приготовьте второе место для копии
(облако, флешка, менеджер паролей).

**Где:** командная строка в корне проекта.

**Команда (Windows):**

```cmd
set "JAVA_HOME=%ProgramFiles%\Android\Android Studio\jbr"
"%JAVA_HOME%\bin\keytool" -genkeypair -v -keystore app-signing.keystore -alias sign -keyalg RSA -keysize 4096 -validity 10000 -storetype PKCS12
```

**Команда (macOS/Linux):**

```bash
"/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/keytool" -genkeypair -v -keystore app-signing.keystore -alias sign -keyalg RSA -keysize 4096 -validity 10000 -storetype PKCS12
```

**Что вводить в вопросы keytool** (нажимать Enter после каждой строки):

| Вопрос keytool | Что вводить |
|---|---|
| `Enter keystore password:` | пароль (минимум 6 символов). Запишите его — он нужен в сборке и в секретах GitHub |
| `Re-enter new password:` | тот же пароль |
| `What is your first and last name?` | ваше имя или название студии, например `Kirill Petrov` |
| `What is the name of your organizational unit?` | можно `Mobile` или нажать Enter |
| `What is the name of your organization?` | `Self-employed` или название компании |
| `What is the name of your City or Locality?` | город, например `Helsinki` |
| `What is the name of your State or Province?` | регион, например `Uusimaa` |
| `What is the two-letter country code for this unit?` | `RU` или `FI` |
| `Is CN=..., correct?` | `yes` + Enter |

**Частые вопросы:**

- «Просит пароль для ключа отдельно» — при `-storetype PKCS12` пароль ключа совпадает
  с паролем хранилища. Если ваш JDK всё-таки спросит — введите тот же пароль.
- «Ошибка `keytool` не найден» — не задан `JAVA_HOME`; выполните первую строку команды.
- «Хочу поменять пароль» — не нужно: пароль задаётся один раз вместе с ключом.

**Проверка** — ключ создан и alias правильный:

```cmd
"%JAVA_HOME%\bin\keytool" -list -v -keystore app-signing.keystore -alias sign
```

В выводе должны быть `Alias name: sign`, `Signature algorithm name: SHA256withRSA`
и строка `SHA256:` с отпечатком — **сохраните этот отпечаток**, он понадобится, чтобы
сверить подпись с той, что покажет RuStore.

Файл `app-signing.keystore` лежит в корне проекта. В git он не попадёт: `*.keystore`,
`*.jks`, `*.pem` уже перечислены в `.gitignore`.

---

## Шаг 2. GitHub: репозиторий, секреты, автосборка

### 2.1 Проверить, что проект уже в GitHub

**Где:** <https://github.com/kirillpetrov08/yandex2>.

Если репозиторий есть — переходите к 2.2. Если нет (например, вы распаковали архив):

```cmd
git init
git add .
git -c user.name="Ваше Имя" -c user.email="ваш@email" commit -m "Конвертер+: исходники"
git branch -M main
git remote add origin https://github.com/ВАШ_ЛОГИН/ВАШ_РЕПОЗИТОРИЙ.git
git push -u origin main
```

### 2.2 Секреты для подписи

**Где:** GitHub → ваш репозиторий → **Settings** → **Secrets and variables** → **Actions** →
**New repository secret**. Добавьте четыре секрета (по одному, кнопкой «New repository secret»):

| Имя секрета (Name) | Значение (Secret) |
|---|---|
| `KEYSTORE_BASE64` | весь файл `app-signing.keystore`, закодированный в Base64 (команда ниже) |
| `KEYSTORE_PASSWORD` | пароль хранилища из шага 1 |
| `KEY_ALIAS` | `sign` |
| `KEY_PASSWORD` | пароль ключа (при PKCS12 — тот же пароль) |

**Команда, чтобы получить Base64 ключа** (выводит длинную строку — скопируйте её целиком):

Windows, Git Bash:

```bash
openssl base64 -A -in app-signing.keystore
```

Windows, PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("app-signing.keystore"))
```

macOS / Linux:

```bash
base64 -i app-signing.keystore | tr -d '\n'
```

> Важно: секрет `KEYSTORE_BASE64` должен содержать **тот же самый** файл
> `app-signing.keystore`, которым вы подписываете APK локально. В воркфлоу GitHub он
> сохраняется под именем `upload-keystore.jks` — это просто имя файла внутри сборки,
> важны сам ключ и секрет `KEY_ALIAS=sign`.

### 2.3 Создать файл автосборки через веб-интерфейс

Push файла `.github/workflows/*` ботом отклоняется (у GitHub App нет права `workflows`),
поэтому файл создаётся руками — это две минуты:

1. **Где:** GitHub → репозиторий → **Add file** → **Create new file**.
2. **Имя файла:** введите ровно `.github/workflows/android.yml`
   (слэши создадут папки автоматически).
3. **Содержимое:** скопируйте его из файла `docs/ci-android.yml` вашего репозитория
   (GitHub → `docs/ci-android.yml` → значок «Copy raw contents» или выделить и скопировать весь текст)
   и вставьте в редактор целиком.
4. **Где кнопка:** внизу страницы **Commit changes** → **Commit changes**
   (коммит прямо в `main`).
5. Если файл уже был создан ранее — вставьте актуальную версию заново: в ней есть
   сборка `assembleRelease` и проверка подписи APK.

### 2.4 Запустить сборку на GitHub и забрать APK

1. **Где:** GitHub → вкладка **Actions** → слева **Android CI** → справа **Run workflow** →
   зелёная кнопка **Run workflow**.
2. Ждём зелёной галочки (5–10 минут, первый прогон дольше: скачиваются зависимости).
3. **Где:** откройте прогон → внизу блок **Artifacts** → скачайте `build-outputs`.
4. Внутри архива APK лежит по пути `apk/release/app-release.apk`.
5. В логе шага **Проверка подписи (APK и AAB)** должно быть `Подпись APK OK` и строка
   `SHA-256`. Если там `::warning::APK не подписан` — значит секреты не добавились
   или в `KEYSTORE_BASE64` попал не тот файл.

> Тесты в CI идут автоматически (`./gradlew testDebugUnitTest`) — зелёный прогон означает,
> что 95 тестов чистой логики прошли.

### 2.5 Альтернатива: собрать локально

Если Android Studio установлена (шаг 0), можно вообще не настраивать CI и сразу перейти
к Части 2 — APK соберётся на вашем компьютере.

---

## Шаг 3. Политика конфиденциальности и app-ads.txt (GitHub Pages)

RuStore требует ссылку на политику в карточке **и** внутри приложения, РСЯ хочет
`app-ads.txt` на сайте разработчика. И то, и другое отдаётся одним переключателем.

**Где:** GitHub → репозиторий → **Settings** → **Pages** (левое меню) →
Source: **Deploy from a branch** → Branch: **main**, Folder: **/docs** → **Save**.

Через 1–2 минуты проверьте ссылки (должны открыться в браузере):

- <https://kirillpetrov08.github.io/yandex2/privacy_policy.html>
- <https://kirillpetrov08.github.io/yandex2/app-ads.txt>

**Команда для проверки из терминала:**

```bash
curl -I https://kirillpetrov08.github.io/yandex2/privacy_policy.html
curl -I https://kirillpetrov08.github.io/yandex2/app-ads.txt
```

Ожидаемые заголовки: `HTTP/2 200`.

### 3.1 Вписать publisher ID в app-ads.txt

**Где:** кабинет РСЯ <https://partner.yandex.ru> → **Настройки** → **Общие** → блок
**app-ads.txt** (там есть кнопка скачивания готового файла).

1. Скачайте готовый файл из кабинета и посмотрите строку вида
   `ssaaq.yandex.ru, 123456789, DIRECT`.
2. В своём репозитории откройте `docs/app-ads.txt`, замените `ВАШ_PUBLISHER_ID`
   на номер из кабинета и закоммитьте:

**Команда:**

```bash
git add docs/app-ads.txt
git -c user.name="Ваше Имя" -c user.email="ваш@email" commit -m "app-ads.txt: publisher ID из кабинета РСЯ"
git push
```

3. Проверка: откройте <https://kirillpetrov08.github.io/yandex2/app-ads.txt> — строка
   должна быть с вашим номером. РСЯ проверяет файл примерно раз в сутки.

### 3.2 Заменить свою почту

Почта `support.converterplus@gmail.com` сейчас стоит заглушкой. Она нужна в трёх местах:
в приложении (пункт «Написать разработчику»), в политике и в текстах магазина.

**Команда (Windows) — найти все места:**

```cmd
findstr /s /n /c:"support.converterplus@gmail.com" app\src\main\java\*.kt docs\privacy_policy.html docs\STORE_TEXTS.md
```

**Что ввести:** в каждом найденном файле замените адрес на свою реальную почту:

| Файл | Строка |
|---|---|
| `app/src/main/java/com/converterplus/app/Config.kt` | `const val SUPPORT_EMAIL = "ваша@почта"` |
| `docs/privacy_policy.html` | две ссылки `mailto:` (в начале и в разделе контактов) |
| `docs/STORE_TEXTS.md` | блоки «СВЯЗЬ» и «Поддержка» (2 места) |

После правки `Config.kt` приложение надо пересобрать (шаг 5).

**Проверка:**

```cmd
findstr /s /n /c:"converterplus@gmail.com" app\src\main\java\*.kt docs\*.md docs\*.html
```

Команда ничего не должна найти.

---

## Шаг 4. Проверить, что проект собирается, и прогнать тесты

**Где:** командная строка в корне проекта (или Android Studio: зелёная кнопка ▶).

**Команда:**

```cmd
gradlew.bat testDebugUnitTest assembleDebug
```

Для macOS/Linux — `./gradlew testDebugUnitTest assembleDebug`.

**Что должно получиться:**

- `BUILD SUCCESSFUL`;
- отчёт по тестам: `app/build/reports/tests/testDebugUnitTest/index.html`
  (95 тестов, 0 падений);
- отладочный APK: `app/build/outputs/apk/debug/app-debug.apk`.

**Установить на телефон** (включите на телефоне «Отладку по USB», подключите кабелем,
на запрос «Разрешить отладку» ответьте «Разрешить»):

```cmd
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
```

**Проверить логи рекламы:**

```cmd
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" logcat -s ConverterAds:V
```

| Строка | Смысл |
|---|---|
| `init: пропущено: согласие не получено` | норма: реклама не стартует до третьего экрана онбординга |
| `SDK готов: Initialized, testMode=false` | всё правильно, ID боевые |
| `interstitial: загружена` / `rewarded: загружена` | блоки живые |

Если видите `ERROR config: Некорректные ID рекламных блоков` — сверьте `gradle.properties`
с кабинетом РСЯ (раздел «Реклама Яндекс РСЯ» в этом файле).

---

## Шаг 5. Собрать релизный APK для RuStore

Правило: **APK для RuStore подписывается ключом `app-signing.keystore`, alias `sign`.**
Прямой APK магазин не переподписывает.

**Команда (Windows, cmd, из корня проекта):**

```cmd
set "JAVA_HOME=%ProgramFiles%\Android\Android Studio\jbr"
set "KEYSTORE_FILE=app-signing.keystore"
set "KEYSTORE_PASSWORD=пароль_хранилища"
set "KEY_ALIAS=sign"
set "KEY_PASSWORD=пароль_ключа"
gradlew.bat clean assembleRelease
```

**Команда (PowerShell):**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:KEYSTORE_FILE = "app-signing.keystore"
$env:KEYSTORE_PASSWORD = "пароль_хранилища"
$env:KEY_ALIAS = "sign"
$env:KEY_PASSWORD = "пароль_ключа"
.\gradlew.bat clean assembleRelease
```

**Команда (macOS/Linux):**

```bash
export KEYSTORE_FILE=app-signing.keystore
export KEYSTORE_PASSWORD='пароль_хранилища'
export KEY_ALIAS=sign
export KEY_PASSWORD='пароль_ключа'
./gradlew clean assembleRelease
```

**Что должно получиться:** файл
`app/build/outputs/apk/release/app-release.apk` (примерно 30 МБ — внутри 7,7 МБ данных OCR).

**Проверка подписи:**

```cmd
"%LOCALAPPDATA%\Android\Sdk\build-tools\36.0.0\apksigner.bat" verify --print-certs app\build\outputs\apk\release\app-release.apk
```

В выводе должно быть:

- `Verified using v2 scheme (APK Signature Scheme v2): true`
- `Signer #1 certificate DN: CN=...` — ваши данные из шага 1
- `Signer #1 certificate SHA-256 digest: ...` — **тот же отпечаток**, что показал
  `keytool -list -v` в шаге 1.

**Установить на телефон и проверить руками:**

```cmd
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\release\app-release.apk
```

Чек-лист живой проверки (не пропускать — модерация RuStore проверяет работоспособность):

- [ ] Изображения: JPG → PNG, PNG → WEBP;
- [ ] PDF → Изображения: все страницы и выбранные;
- [ ] Изображения → PDF: 3 фото в один файл;
- [ ] Извлечение текста: фото документа → TXT с русским текстом;
- [ ] Аудио: MP3 → WAV, MP3 → M4A;
- [ ] Видео: MOV/MKV → MP4, «Только звук (M4A)»;
- [ ] Конвертация в фоне: свернуть приложение → в шторке виден прогресс;
- [ ] Результат сохранён в «Загрузки», шторка результата закрывается;
- [ ] Баннер внизу главной, не перекрывает кнопки;
- [ ] Настройки → «Убрать рекламу» → смотреть видео → реклама исчезает на 30 минут.

---

## Часть 3. Кабинет РСЯ: проверить (вводить уже нечего)

## Шаг 6. Что должно быть в РСЯ

**Где:** <https://partner.yandex.ru> → **Реклама в приложениях** → **Приложения**.

| Что смотреть | Нормальное состояние |
|---|---|
| Приложение «конвертатор» | есть, `PAGE_ID = 20230616` |
| Блок «Баннер» | ID `R-M-20230616-2` |
| Блок «Межстраничная реклама» | ID `R-M-20230616-4` |
| Блок «Реклама с вознаграждением» | ID `R-M-20230616-7` |
| Ссылка на магазин | «Добавить ссылку» — заполним **после** публикации (шаг 12) |
| Статус приложения | «Тестовый режим» до модерации РСЯ, затем «Активно» |

Если статус когда-то стоял «Остановить» — нажмите **Действия → Включить показы**,
само это не восстановится.

---

## Часть 4. Публикация в RuStore

## Шаг 7. Аккаунт разработчика

**Где:** <https://console.rustore.ru/sign-in> → вход через **VK ID** (телефон или e-mail).

| Поле | Что вводить |
|---|---|
| Тип аккаунта | **Физическое лицо** (проще) или **ИП/ЮЛ** (потребуется подпись КЭП) |
| Имя/название | ваше имя или название студии |
| Публичный e-mail | **обязательно**: по нему модерация задаёт вопросы — укажите почту, которую читаете |
| Телефон | ваш номер |
| Страна | Россия (или ваша) |

Имя разработчика отсюда попадает в карточку приложения. Согласитесь с условиями
и дождитесь подтверждения e-mail. Инструкция RuStore:
<https://www.rustore.ru/help/developers/developer-account/registration-developer>.

## Шаг 8. Создать приложение и загрузить APK

**Где:** консоль RuStore → вкладка **Приложения**.

1. Кнопка **Добавить приложение** → **Название приложения**: `Конвертер+` → **Добавить**.
   (Полное маркетинговое имя `Конвертер+ — PDF и фото` вводится позже, в карточке версии.)
2. Откройте созданное приложение → кнопка **Загрузить версию**.
3. Тип приложения — **Универсальный** (подходит и для телефонов, и для планшетов).
4. Перетащите файл `app/build/outputs/apk/release/app-release.apk` в окно загрузки.
5. **Не закрывайте страницу до конца загрузки и обработки** — иначе загрузка прервётся.
   Обработку можно пережидать, уйдя со страницы (черновик сохранится).
6. Если консоль пожалуется на подпись или версию — сверьтесь с шагом 5 (APK должен быть
   подписан `app-signing.keystore`, `versionCode` должен быть больше предыдущего).
7. Загруженные файлы хранятся **14 дней** — за это время версию надо отправить на модерацию.

## Шаг 9. Карточка версии: поле за полем

**Где:** консоль → ваше приложение → карточка версии (форма из нескольких блоков).

### 9.1 Информация о приложении

| Поле | Что ввести |
|---|---|
| Наименование | `Конвертер+ — PDF и фото` (22 символа из 30) |
| Тип приложения | неигровое приложение |
| Категория (основная) | **Полезные инструменты** |
| Категория дополнительная | **оставить пустой** (правила RuStore запрещают «Полезные инструменты» вторым значением) |
| Возрастное ограничение | **12+** — при персонализированной рекламе 0+/6+ запрещены |
| Поисковые теги (до 5) | `конвертер`, `pdf`, `jpg`, `png`, `документы` |
| Краткое описание (≤80 символов) | `Офлайн-конвертер: JPG, PNG, WEBP и PDF прямо на телефоне.` |
| Полное описание (≤4000 символов) | скопировать блок **RuStore** из `docs/STORE_TEXTS.md`, раздел 2 |
| Минимальная версия Android | подставляется сама: Android 8.0 (minSdk 26) |

### 9.2 Контакты

Обязательно заполнить хотя бы одно поле. Впишите тот же публичный e-mail, что и в профиле
разработчика.

### 9.3 Иконка и скриншоты

**Иконка.** Готовый файл лежит в репозитории: `docs/store/icon-512.png`
(512×512 px, 5 КБ, синий фон залит целиком, без прозрачных краёв — как требует RuStore).
Просто выберите этот файл в поле «Иконка». Скачать его:
<https://github.com/kirillpetrov08/yandex2/blob/main/docs/store/icon-512.png> → **Download**.

Если захотите свою иконку — требования: `.png`/`.jpg`, ровно 512×512, ≤3 МБ, фон по всей площади.

**Скриншоты.** Нужно **не менее 3** (можно до 10) вертикальных снимков до 2160×3840, ≤3 МБ каждый.

Снять прямо с телефона, на котором стоит релизная сборка:

```cmd
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" exec-out screencap -p > screen-1.png
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" exec-out screencap -p > screen-2.png
```

> Делайте это в **cmd**, Git Bash или Terminal. В PowerShell 5.1 перенаправление в файл
> может испортить PNG — если работаете только в нём, сохраняйте снимки самим телефоном.

Какие экраны снять (по одному фото на экран):

1. Главный экран со списком инструментов;
2. Выбор файла («Из галереи» / «Из файлов»);
3. Экран формата результата с чипами JPG/PNG/WEBP;
4. Прогресс конвертации (можно с плавающим уведомлением);
5. Шторка результата «Готово!» с экономией размера;
6. История конвертаций;
7. Экран распознавания текста с готовым TXT.

Планшетные скриншоты не обязательны: если их не загрузить, на планшетах покажутся телефонные.

### 9.4 Что нового?

Обязательное поле. Вставьте:

```
Первый выпуск: изображения (JPG/PNG/WEBP), PDF → изображения, изображения → PDF,
аудио и видео, распознавание текста, конвертация в фоне с уведомлением о прогрессе.
```

### 9.5 FAQ (по желанию, но снимает часть вопросов модерации)

До 10 пар: вопрос ≤120 символов, ответ ≤500. Готовые формулировки —
`docs/RUSTORE_DEPLOY.md`, раздел 6.5.

## Шаг 10. Блок «Безопасность данных пользователя»

Заполняется для каждой версии. Консоль уже прочитала разрешения из APK — проверьте и дополните.

### 10.1 Чувствительные разрешения

Единственное «опасное» разрешение в приложении — `POST_NOTIFICATIONS`. В поле
**Обоснование** вставьте:

```
Разрешение на уведомления нужно, чтобы показывать прогресс конвертации файла в шторке
уведомлений при работе в фоне. Запрашивается не при первом запуске, а только в момент
первой конвертации; без него приложение полностью работоспособно, а уведомление просто
не появится. Других разрешений приложение не запрашивает: файлы выбираются через системные
диалоги, камера, микрофон и доступ к хранилищу не используются.
```

> Если в списке разрешений появилось «Доступ к файлам/фото/камера/микрофон» — APK собран
> не из этого проекта. Пересоберите (шаг 5) и загрузите заново, а не пишите обоснование.

### 10.2 Категории и типы данных

| Тип данных | Отметить | Почему |
|---|---|---|
| ID устройства или другие идентификаторы | **да** | рекламный SDK передаёт рекламный идентификатор |
| Взаимодействие с приложением | **да**, если есть в списке | SDK собирает статистику показов |
| Файлы и документы | нет | файлы не покидают устройство |
| Фото, видео, аудиофайлы | нет | обрабатываются локально |
| Местоположение, контакты, сообщения, финансы | нет | не запрашиваются |
| Отчёты об ошибках и диагностика | нет | crash-репортинг не подключён |

Пояснение, которое можно вставить в комментарий:

```
Приложение обрабатывает выбранные пользователем файлы только на устройстве и никуда их
не передаёт. Интернет используется исключительно для загрузки рекламы Яндекс РСЯ.
Рекламный SDK передаёт рекламный идентификатор устройства и данные о показах —
это указано в политике конфиденциальности.
```

### 10.3 Декларации

| Что | Как отвечать |
|---|---|
| Рекламные SDK и сети | **Yandex Mobile Ads (РСЯ)**, форматы: баннер, межстраничная, за вознаграждение |
| Платёжные решения | нет: покупок, подписок и внешних платежей нет |
| Механизмы, мешающие RuStore | нет (Google Play Integrity и подобное не используются) |

## Шаг 11. Публикация версии и модерация

**Где:** карточка версии → внизу страница **Настройка публикации**.

| Поле / выбор | Что выбрать |
|---|---|
| Режим публикации | **Вручную** — рекомендую для первой версии: после модерации успеете прочитать письмо модератора и только потом нажать «Опубликовать» |
| Комментарий для модератора (если есть) | можно оставить пустым |
| Кнопка | **Отправить на модерацию** |

Дальше: автоматическая проверка (антивирус, уязвимости) → проверка модератором →
при необходимости спецпроверка. **Срок до 72 часов.** Результат придёт на публичный e-mail.

Что делать при отказе: в уведомлении указаны конкретные причины. Если дело в карточке —
правьте и снова жмите «Отправить на модерацию»; если в приложении — соберите новую версию
(`versionCode + 1`), загрузите APK и снова отправьте. Апелляция — письмом на
`support@rustore.ru`, одна на решение, срок рассмотрения до 30 дней.

Если выбрали **Вручную**, после модерации версия получает статус «Готова к публикации» —
жмите **Опубликовать**.

## Шаг 12. После публикации: связать RuStore с РСЯ

1. **Где:** консоль RuStore → карточка приложения → скопируйте ссылку вида
   `https://www.rustore.ru/catalog/app/com.converterplus.app` (не набирайте руками,
   копируйте из консоли).
2. **Где:** кабинет РСЯ → **Реклама в приложениях** → **Приложения** → карточка приложения →
   поле «Добавить ссылку» → вставьте ссылку на RuStore → сохраните.
3. После добавления ссылки приложение **заново уйдёт на модерацию РСЯ** — до 2 дней.
   До этого на ваших блоках показывается тестовая реклама.
4. Дождитесь статуса **Активно** — с этого момента показы тарифицируются.
5. **Проверка:** в РСЯ раздел app-ads.txt должен показать, что файл найден
   (проверяется раз в сутки).

С этого момента цикл закончен: приложение в магазине, реклама настоящая, доход начисляется.

## Шаг 13. Как выпускать обновления

1. Внести правки в код.
2. **Где:** `app/build.gradle.kts` — поднять номер версии:

```kotlin
versionCode = 2          // обязательно больше предыдущего
versionName = "1.0.1"    // на ваше усмотрение
```

3. Собрать и проверить APK (шаги 4–5) — **тем же ключом** `app-signing.keystore`.
4. **Где:** консоль RuStore → приложение → **Загрузить версию** → APK → заполнить
   **Что нового?** коротким списком изменений.
5. Выбрать режим публикации: для обычного обновления — «Автоматически»; если рискованное —
   «Поэтапно 10–100 %».
6. Отправить на модерацию.

Что нельзя менять никогда: имя пакета `com.converterplus.app`, ключ подписи, точку входа
приложения. Что стоит помнить: если 12 месяцев не выпускать обновлений, RuStore вправе
снять приложение с публикации.

---

## Приложение A. Все команды подряд (Windows, cmd)

```cmd
rem --- 0. Перейти в проект ---
cd C:\путь\к\yandex2
set "JAVA_HOME=%ProgramFiles%\Android\Android Studio\jbr"

rem --- проверка окружения ---
"%JAVA_HOME%\bin\java" -version
gradlew.bat --version

rem --- 1. Ключ подписи (один раз на проект) ---
"%JAVA_HOME%\bin\keytool" -genkeypair -v -keystore app-signing.keystore -alias sign -keyalg RSA -keysize 4096 -validity 10000 -storetype PKCS12
"%JAVA_HOME%\bin\keytool" -list -v -keystore app-signing.keystore -alias sign

rem --- 2. Секрет KEYSTORE_BASE64: одна строка вывода, копировать целиком в GitHub ---
powershell -NoProfile -Command "[Convert]::ToBase64String([IO.File]::ReadAllBytes('app-signing.keystore'))"

rem --- 3. Найти незакрытые заглушки ---
findstr /s /n /c:"support.converterplus@gmail.com" /c:"ВАШ_" /c:"demo-" app\src\main\java\*.kt gradle.properties docs\app-ads.txt

rem --- 4. Тесты и отладочная сборка ---
gradlew.bat testDebugUnitTest assembleDebug

rem --- 5. Релизный APK ---
set "KEYSTORE_FILE=app-signing.keystore"
set "KEYSTORE_PASSWORD=пароль_хранилища"
set "KEY_ALIAS=sign"
set "KEY_PASSWORD=пароль_ключа"
gradlew.bat clean assembleRelease

rem --- 6. Проверка подписи и установка ---
"%LOCALAPPDATA%\Android\Sdk\build-tools\36.0.0\apksigner.bat" verify --print-certs app\build\outputs\apk\release\app-release.apk
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\release\app-release.apk

rem --- 7. Логи рекламы ---
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" logcat -s ConverterAds:V

rem --- 8. Скриншоты для магазина ---
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" exec-out screencap -p > screen-1.png
```

> Строка `git bash -lc ...` — это запуск команды внутри Git for Windows
> (`openssl base64 -A -in app-signing.keystore`). Если Git Bash не установлен,
> используйте PowerShell-вариант из шага 2.2.

## Приложение B. Все команды подряд (macOS/Linux)

```bash
cd ~/путь/к/yandex2
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

"$JAVA_HOME/bin/keytool" -genkeypair -v -keystore app-signing.keystore -alias sign -keyalg RSA -keysize 4096 -validity 10000 -storetype PKCS12
base64 -i app-signing.keystore | tr -d '\n'      # → в секрет KEYSTORE_BASE64
./gradlew testDebugUnitTest assembleDebug

export KEYSTORE_FILE=app-signing.keystore
export KEYSTORE_PASSWORD='пароль_хранилища'
export KEY_ALIAS=sign
export KEY_PASSWORD='пароль_ключа'
./gradlew clean assembleRelease

~/Library/Android/sdk/build-tools/36.0.0/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/release/app-release.apk
~/Library/Android/sdk/platform-tools/adb logcat -s ConverterAds:V
```

## Приложение C. Сводная таблица «где что вводится»

| Значение | Где вводится | Чем проверить |
|---|---|---|
| Пароль хранилища ключа | шаг 5 (сборка) + секрет `KEYSTORE_PASSWORD` | APK подписан, `apksigner verify` = true |
| `KEY_ALIAS` = `sign` | шаг 5 + секрет `KEY_ALIAS` | `keytool -list` показывает alias `sign` |
| Base64 ключа | секрет `KEYSTORE_BASE64` | лог CI: `Подпись APK OK` |
| Publisher ID РСЯ | `docs/app-ads.txt` | файл открывается по адресу Pages |
| Ссылка на RuStore | `Config.STORE_URL` + карточка приложения в РСЯ | клик по «Версия» в настройках открывает карточку |
| Почта поддержки | `Config.SUPPORT_EMAIL`, `docs/privacy_policy.html`, `docs/STORE_TEXTS.md`, профиль RuStore | `findstr` не находит старую заглушку |
| ID блоков РСЯ | `gradle.properties` (**уже вписаны**) | logcat: `SDK готов: …, testMode=false` |
| Иконка 512×512 | карточка RuStore | файл `docs/store/icon-512.png` |
| Скриншоты | карточка RuStore | 3–10 PNG/JPG вертикально |
| Тексты описаний | карточка RuStore | `docs/STORE_TEXTS.md`, раздел 2 |

## Приложение D. Ошибки и что делать

| Сообщение | Причина | Что делать |
|---|---|---|
| `BUILD FAILED ... ads.testMode=false, но ID блоков не похожи на реальные` | в `gradle.properties` попали демо-ID или опечатка | вернуть боевые ID (`R-M-20230616-2/-4/-7`) |
| `Failed to read key ... from store` | неверный пароль или alias в переменных сборки | сверить пароли, `KEY_ALIAS=sign` |
| `APK не подписан` в CI | не добавлены секреты `KEYSTORE_*` | добавить 4 секрета из шага 2.2 |
| `App not installed` при установке поверх | APK подписан другим ключом | подписывать всегда `app-signing.keystore` |
| В RuStore «подпись не совпадает» | собран другим ключом или это новая версия другого приложения | пересобрать ключом `app-signing.keystore` |
| «Версия не больше предыдущей» | не поднят `versionCode` | поднять и пересобрать |
| Модерация RuStore: «нет политики конфиденциальности» | ссылка не открывается | проверить Pages (шаг 3) и поле в карточке |
| Модерация: «недостоверные сведения» | в описании обещаны функции, которых нет | использовать тексты из `docs/STORE_TEXTS.md` |
| `logcat`: `init: пропущено: согласие не получено` | не пройден онбординг | это нормально, реклама не должна показываться без согласия |
| Реклама есть, денег нет | приложение в РСЯ ещё не «Активно» | пройти шаг 12 |
| Баннер не виден | не дано согласие или активен режим «без рекламы» | проверить настройки и режим 30 минут |

## Приложение E. Восемь ручных шагов (краткая версия)

Полная версия — `docs/MANUAL_STEPS.md`. Коротко:

1. Открыть проект, прогнать `testDebugUnitTest` и `assembleDebug`.
2. Создать `app-signing.keystore` (alias `sign`) и сохранить его в двух местах.
3. Добавить 4 секрета GitHub и создать `.github/workflows/android.yml` из `docs/ci-android.yml`.
4. Проверить блоки РСЯ (ID уже вписаны) и выложить `app-ads.txt` с publisher ID.
5. Заменить почту поддержки на свою в трёх файлах.
6. Включить GitHub Pages (Settings → Pages → main → `/docs`).
7. Собрать релизный APK и проверить его на телефоне.
8. Загрузить APK в RuStore, заполнить карточку, дождаться модерации, связать с РСЯ.

## Приложение F. Карта документов

| Документ | О чём |
|---|---|
| `docs/STEP_BY_STEP.md` | этот рунбук: всё по порядку |
| `docs/ADS_SETUP.md` | реклама: блоки РСЯ, ID, сборка, логи |
| `docs/APK_RELEASE.md` | релизный APK без заглушек |
| `docs/RUSTORE_DEPLOY.md` | публикация в RuStore подробно |
| `docs/RUSTORE_SIGNING.md` | окно «Ключ подписи приложения» (PEPK), если грузите AAB |
| `docs/STORE_TEXTS.md` | тексты для карточки магазина |
| `docs/PRIVACY_POLICY.md`, `docs/privacy_policy.html` | политика конфиденциальности |
| `docs/ci-android.yml` | файл автосборки для GitHub |
| `docs/store/icon-512.png` | готовая иконка 512×512 для магазина |
| `docs/MANUAL_STEPS.md` | 8 ручных шагов |
| `docs/DEPLOY.md` | сжатая сводка по развёртыванию |
