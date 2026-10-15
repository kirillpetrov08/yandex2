# Подпись приложения для RuStore — окно «Ключ подписи приложения»

> **Этот документ нужен только для загрузки AAB.** Если вы публикуете прямой APK
> (наш основной путь, см. `docs/APK_RELEASE.md` и `docs/STEP_BY_STEP.md`, шаг 5),
> окно «Ключ подписи приложения» и PEPK **не нужны вообще**: APK подписывается
> ключом `app-signing.keystore`, alias `sign`, и RuStore принимает его как есть.

Коротко про AAB: консоль просит **два разных файла**, и это не одно и то же:

| Поле в окне | Что грузить | Откуда берётся |
|---|---|---|
| «Добавьте созданный ZIP-архив» (до 100 КБ) | `pepk_out.zip` — ваш **ключ подписи приложения**, зашифрованный утилитой PEPK | делает `pepk.jar` из вашего `app-signing.keystore` + `encryptionkey` из этого окна |
| Сертификат ключа загрузки (PEM) | `uploadcert.pem` — сертификат **ключа загрузки**, которым подписан AAB | `keytool -exportcert` из того же keystore, что подписывает AAB (`upload-keystore.jks`, он же в секретах CI) |

В окне есть прокрутка (ползунок справа) — поле для PEM находится ниже шага с ZIP.

Порядок в консоли: **сначала подпись, потом сам AAB**. До загрузки подписи файл `.aab` не принимается.

---

## 1. Зачем два ключа

RuStore работает с AAB так (официальная схема):

1. вы подписываете `.aab` **ключом загрузки** и загружаете его в RuStore;
2. RuStore по сертификату ключа загрузки убеждается, что вы — это вы;
3. RuStore сам собирает из AAB набор APK и подписывает **каждый APK ключом подписи приложения**;
4. пользователи получают APK, подписанные вашим ключом подписи приложения.

Отсюда требование: `pepk_out.zip` должен содержать **тот ключ подписи приложения, которым подписаны (или будут подписаны) все версии приложения во всех магазинах**.

| Роль | Файл у нас | Чем подписывает | Куда попадает | Если потеряете |
|---|---|---|---|---|
| **Ключ подписи приложения** | `app-signing.keystore`, alias `sign` | ничем локально | `pepk_out.zip` → RuStore; PEPK-архив → Google Play | обновления выпустить нельзя, только смена ключа через поддержку RuStore (`support@rustore.ru`) и Google Play |
| **Ключ загрузки** | `upload-keystore.jks`, alias `upload` | подписывает `.aab` (в CI и локально) | `uploadcert.pem` → RuStore; в Google Play — автоматически при первой загрузке AAB | заменяется: RuStore — через поддержку, Google Play — запросом на сброс ключа загрузки |

> Требования RuStore: RSA **не менее 2048 бит** (у нас 4096) и Java **11+** на машине.

---

## 2. Почему ключ должен совпадать с Google Play

RuStore перевыпускает ваши APK, подписывая их ключом подписи приложения. Если в Google Play используется **другой** ключ, Android не даст поставить обновление поверх: «Приложение не установлено» — пользователю придётся удалять старую версию. Официальная рекомендация RuStore — **один сертификат на все магазины**.

Практически это значит:

- при первой загрузке в Google Play выберите **«Использовать другой ключ подписи приложений»** и загрузите туда PEPK-архив, собранный из **того же** `app-signing.keystore` (у Play Console свой `encryptionkey` — PEPK запускается второй раз с ключом из Play);
- решение принимается один раз при первом релизе, поменять ключ подписи потом можно только через поддержку.

Если вы намеренно оставляете в Play ключ, сгенерированный Google, приложение будет с разными подписями в двух магазинах: обновление «поверх» между магазинами работать не будет. Для «Конвертер+» рекомендован первый вариант — один свой ключ.

---

## 3. Быстрый путь: скрипт

### Windows (запускать из `cmd.exe` или PowerShell, **не** из Git Bash)

```cmd
cd C:\путь\к\репозиторию
tools\rustore-signature.cmd --encryption-key=ВСТАВИТЬ_КЛЮЧ_ИЗ_ОКНА
```

### macOS / Linux / Git Bash

```bash
tools/rustore-signature.sh --encryption-key=ВСТАВИТЬ_КЛЮЧ_ИЗ_ОКНА
```

Скрипт делает пять шагов: находит JDK (JAVA_HOME → Android Studio JBR → PATH), проверяет аргументы, создаёт `app-signing.keystore`, если его ещё нет, прогоняет PEPK и выгружает сертификат ключа загрузки. На выходе:

```
rustore-out/pepk_out.zip     ← грузим в поле «ZIP-архив»
rustore-out/uploadcert.pem   ← грузим в поле сертификата ключа загрузки
```

Пароли keytool и PEPK спрашивают сами — в командной строке их указывать не нужно (и не нужно, чтобы они попали в историю консоли). Вывод `.cmd` версии на английском намеренно: кириллица в `.cmd` ломается на большинстве кодовых страниц Windows.

Полезные варианты: `--keystore=`, `--alias=`, `--upload-keystore=`, `--upload-alias=`, `--out=`. Если у вас другой проект (не «Конвертер+»), укажите его keystore и alias — процедура та же.

---

## 4. Вручную: Windows (cmd.exe)

```cmd
rem 1. Java из Android Studio (отдельно ставить JDK не нужно)
set "JAVA=%ProgramFiles%\Android\Android Studio\jbr\bin\java.exe"
set "KEYTOOL=%ProgramFiles%\Android\Android Studio\jbr\bin\keytool.exe"

rem 2. Ключ подписи приложения — один раз, файл и пароль сохранить навсегда
"%KEYTOOL%" -genkeypair -v -keystore app-signing.keystore -alias sign ^
  -keyalg RSA -keysize 4096 -validity 10000 -storetype PKCS12

rem 3. PEPK: команду скопируйте кнопкой из окна RuStore и замените три аргумента
"%JAVA%" -jar pepk.jar --keystore app-signing.keystore --alias sign ^
  --output pepk_out.zip --encryptionkey=КЛЮЧ_ИЗ_ОКНА --include-cert

rem 4. Сертификат ключа загрузки (тот keystore, что подписывает AAB)
"%KEYTOOL%" -exportcert -alias upload -keystore upload-keystore.jks -rfc -file uploadcert.pem
```

`pepk.jar` скачивается кнопкой **«Скачать»** в окне консоли и должен лежать в том же каталоге, из которого вы запускаете команду.

## 5. Вручную: macOS / Linux

Тот же набор, `java` и `keytool` берутся из `PATH` (или из `JAVA_HOME`):

```bash
keytool -genkeypair -v -keystore app-signing.keystore -alias sign \
  -keyalg RSA -keysize 4096 -validity 10000 -storetype PKCS12

java -jar pepk.jar --keystore app-signing.keystore --alias sign \
  --output pepk_out.zip --encryptionkey=КЛЮЧ_ИЗ_ОКНА --include-cert

keytool -exportcert -alias upload -keystore upload-keystore.jks -rfc -file uploadcert.pem
```

---

## 6. Проверка перед загрузкой

```cmd
rem отпечатки ключа подписи приложения — сравните со страницей «Подпись приложения» в RuStore
"%KEYTOOL%" -list -v -keystore app-signing.keystore -alias sign | findstr SHA
```

```bash
# то же на macOS/Linux
keytool -list -v -keystore app-signing.keystore -alias sign | grep -E 'SHA1|SHA256'
```

- `uploadcert.pem` начинается со строки `-----BEGIN CERTIFICATE-----` (скрипт это проверяет);
- `pepk_out.zip` — не больше 100 КБ (скрипт это проверяет);
- отпечаток SHA-256 должен совпасть с тем, что RuStore покажет на странице подписи, и с тем, что показывает Google Play (для одного и того же ключа они обязаны быть одинаковыми).

Отпечатки из PEM без keytool:

```bash
grep -v ^- uploadcert.pem | base64 -d | sha256sum
```

---

## 7. Частые ошибки

| Симптом | Причина и решение |
|---|---|
| `Error: null`, пароль не спрашивается | запущено из Git Bash (MinTTY): у Java нет настоящей консоли. Запускайте из `cmd.exe`/PowerShell; обходной путь — `winpty java -jar pepk.jar ...` (наш `.sh` подставляет `winpty` сам, если нашёл) |
| «Не подойдёт архив, который используется для других магазинов приложений» | `encryptionkey` уникален для каждого окна: ZIP, сделанный для Google Play, в RuStore не принимается. Прогоните PEPK отдельно с ключом из окна RuStore |
| `Keystore was tampered with, or password was incorrect` | неверный пароль либо несовпадение формата. Если хранилище в формате JKS, RuStore советует перевести его в PKCS12: `keytool -importkeystore -srckeystore old.jks -destkeystore new.keystore -srcstorepass ПАРОЛЬ -deststorepass ПАРОЛЬ` |
| `java: command not found` | нет JDK. Либо поставьте JDK 11+, либо используйте JBR из Android Studio (пути в разделе 4) |
| Консоль отклоняет ключ | ключ меньше 2048 бит: пересоздайте с `-keysize 4096` |
| RuStore пишет, что подпись не совпадает с прошлой версией | в ZIP должен лежать ключ, которым подписаны **предыдущие** APK этого приложения. Если приложение уже публиковалось как APK — берите тот же ключ, иначе обновление не встанет |
| AAB не принимается после загрузки подписи | `.aab` подписан не тем ключом, чей PEM вы загрузили. У нас AAB подписывает `upload-keystore.jks` (alias `upload`) — и PEM должен быть из него же |

---

## 8. Бэкапы

В двух разных местах (менеджер паролей + офлайн-носитель) должны лежать:

- `app-signing.keystore` + его пароль — **незаменимы**;
- `upload-keystore.jks` + пароль (он же в секретах GitHub: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`);
- `pepk_out.zip` — полезная копия: это тот самый архив подписи приложения.

В git ничего из этого не попадает: `*.jks`, `*.keystore`, `*.pem`, `pepk.jar`, `rustore-out/` перечислены в `.gitignore`.

---

## 9. Альтернатива: путь через APK

Если не хочется вообще связываться с PEPK, в RuStore можно грузить **APK**, подписанный вашим ключом: подпись приложения в этом случае отдельно не запрашивается. Минусы: RuStore не оптимизирует доставку под устройство (APK тяжелее AAB), а подпись всё равно должна совпадать с той, что в других магазинах, иначе обновление «поверх» не встанет. Для «Конвертер+» рекомендуем AAB — CI уже собирает его и подписывает.

---

Официальная документация, по которой составлена эта инструкция:

- загрузка AAB и подписи: <https://www.rustore.ru/help/developers/publishing-and-verifying-apps/app-publication/new-version-app/upload-aab>
- подписи APK и AAB, почему ключ должен совпадать между магазинами: <https://www.rustore.ru/help/developers/publishing-and-verifying-apps/app-publication/apk-signature>
- загрузка версии: <https://www.rustore.ru/help/developers/publishing-and-verifying-apps/app-publication/new-version-app>
