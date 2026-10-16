#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Подпись приложения для RuStore.
#
# Делает ровно два файла, которые просит окно «Загрузка подписи приложения»
# в RuStore Консоль:
#   rustore-out/pepk_out.zip    — ключ подписи приложения (PEPK, шифруется
#                                 уникальным ключом из окна консоли);
#   rustore-out/uploadcert.pem  — сертификат ключа загрузки, которым подписан
#                                 AAB (тот же keystore, что в секретах CI).
#
# Запуск:
#   tools/rustore-signature.sh --encryption-key=<hex из окна RuStore>
#
# Необязательные аргументы:
#   --keystore=app-signing.keystore   ключ подписи приложения (создастся, если нет)
#   --alias=sign                      alias ключа подписи приложения
#   --upload-keystore=upload-keystore.jks  ключ загрузки (тот, что в CI)
#   --upload-alias=upload             alias ключа загрузки
#   --out=rustore-out                 каталог для результатов
#
# ВАЖНО (Windows): PEPK спрашивает пароли через настоящую консоль. Из Git Bash
# (MinTTY) он падает с «Error: null» — запускайте tools/rustore-signature.cmd
# из cmd.exe либо используйте winpty (скрипт подставляет его сам, если нашёл).
# ---------------------------------------------------------------------------
set -euo pipefail

ENCRYPTION_KEY=""
KEYSTORE="app-signing.keystore"
ALIAS="sign"
UPLOAD_KEYSTORE="upload-keystore.jks"
UPLOAD_ALIAS="upload"
OUTDIR="rustore-out"
DNAME="CN=Converter Plus, O=Converter Plus, C=RU"

die() { printf '\nОШИБКА: %s\n' "$*" >&2; exit 1; }
info() { printf '  %s\n' "$*"; }
warn() { printf '  ! %s\n' "$*" >&2; }
step() { printf '\n[%s] %s\n' "$1" "$2"; }

usage() {
    sed -n '2,25p' "$0" | sed 's/^# \{0,1\}//'
}

if [ $# -eq 0 ]; then usage; exit 1; fi
for arg in "$@"; do
    case "$arg" in
        --encryption-key=*) ENCRYPTION_KEY="${arg#*=}" ;;
        --keystore=*)       KEYSTORE="${arg#*=}" ;;
        --alias=*)          ALIAS="${arg#*=}" ;;
        --upload-keystore=*) UPLOAD_KEYSTORE="${arg#*=}" ;;
        --upload-alias=*)   UPLOAD_ALIAS="${arg#*=}" ;;
        --out=*)            OUTDIR="${arg#*=}" ;;
        -h|--help)          usage; exit 0 ;;
        *) die "неизвестный аргумент: $arg (см. --help)" ;;
    esac
done

# --- 1. JDK ----------------------------------------------------------------
find_bin() {
    local name="$1" dir dirs=()
    if [ -n "${JAVA_HOME:-}" ]; then dirs+=("$JAVA_HOME/bin"); fi
    case "$(uname -s 2>/dev/null)" in
        MINGW*|MSYS*|CYGWIN*)
            dirs+=("/c/Program Files/Android/Android Studio/jbr/bin")
            dirs+=("/c/Program Files/Java"/*/bin)
            ;;
        Darwin)
            dirs+=("/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin")
            dirs+=("$HOME/Library/Java/JavaVirtualMachines"/*/Contents/Home/bin)
            ;;
    esac
    dirs+=("/usr/lib/jvm/default/bin" "/usr/local/opt/openjdk/bin" "/opt/homebrew/opt/openjdk/bin")
    for dir in "${dirs[@]}"; do
        if [ -x "$dir/$name" ]; then printf '%s\n' "$dir/$name"; return 0; fi
        if [ -x "$dir/$name.exe" ]; then printf '%s\n' "$dir/$name.exe"; return 0; fi
    done
    if command -v "$name" >/dev/null 2>&1; then command -v "$name"; return 0; fi
    if command -v "$name.exe" >/dev/null 2>&1; then command -v "$name.exe"; return 0; fi
    return 1
}

step 1/5 "Ищу JDK (JAVA_HOME → Android Studio JBR → PATH)"
if ! JAVA="$(find_bin java)"; then
    die "java не найдена. Установите JDK 11+ или Android Studio и задайте JAVA_HOME."
fi
if ! KEYTOOL="$(find_bin keytool)"; then
    die "keytool не найден рядом с java ($JAVA). Нужен полный JDK, а не только JRE."
fi
info "java:    $JAVA"
info "keytool: $KEYTOOL"
info "версия:  $("$JAVA" -version 2>&1 | head -n 1)"

# PEPK на Windows под MinTTY не получает консоль — подставляем winpty.
JAVA_CMD=("$JAVA")
case "$(uname -s 2>/dev/null)" in
    MINGW*|MSYS*|CYGWIN*)
        if command -v winpty >/dev/null 2>&1; then
            JAVA_CMD=(winpty "$JAVA")
            info "winpty:  найден, PEPK будет запущен через него"
        else
            warn "winpty не найден: если PEPK упадёт с «Error: null», запустите tools/rustore-signature.cmd из cmd.exe"
        fi
        ;;
esac

# --- 2. Аргументы ----------------------------------------------------------
step 2/5 "Проверяю аргументы"
[ -n "$ENCRYPTION_KEY" ] || die "--encryption-key обязателен: скопируйте ключ из окна «Загрузка подписи приложения» в RuStore Консоль."
if [ "${#ENCRYPTION_KEY}" -lt 32 ] || printf '%s' "$ENCRYPTION_KEY" | grep -qv '^[0-9a-fA-F]*$'; then
    warn "encryptionkey не похож на hex-строку (получено ${#ENCRYPTION_KEY} символов). Проверьте, что скопирован целиком."
fi
PEPK=""
for candidate in "$OUTDIR/pepk.jar" "pepk.jar" "$HOME/pepk.jar"; do
    if [ -f "$candidate" ]; then PEPK="$candidate"; break; fi
done
[ -n "$PEPK" ] || die "pepk.jar не найден. Скачайте его кнопкой в окне «Загрузка подписи приложения» и положите в $OUTDIR/ или рядом со скриптом."
info "pepk.jar:      $PEPK"
info "ключ подписи:  $KEYSTORE (alias $ALIAS)"
info "ключ загрузки: $UPLOAD_KEYSTORE (alias $UPLOAD_ALIAS)"

# --- 3. Ключ подписи приложения -------------------------------------------
step 3/5 "Ключ подписи приложения"
mkdir -p "$OUTDIR" "$(dirname "$KEYSTORE")" 2>/dev/null || true
if [ -f "$KEYSTORE" ]; then
    info "уже существует, не трогаю: $KEYSTORE"
else
    warn "создаю НОВЫЙ ключ подписи приложения — этим ключом RuStore будет подписывать APK."
    "$KEYTOOL" -genkeypair -v -keystore "$KEYSTORE" -alias "$ALIAS" \
        -keyalg RSA -keysize 4096 -validity 10000 -storetype PKCS12 -dname "$DNAME"
    warn "СОХРАНИТЕ КОПИЮ: $KEYSTORE + пароль, в двух разных местах."
    warn "Потеря этого файла = невозможность выпускать обновления."
fi

# --- 3b. Проверка алиаса ---------------------------------------------------
# PEPK падает с «No key with alias» уже после ввода пароля, поэтому проверяем
# заранее: читаем список записей ключа и ищем в нём нужный алиас. Пароль
# спрашивается один раз (тот же keytool, который потом зовёт PEPK).
step 3b/5 "Проверяю алиас «$ALIAS» в ключе $KEYSTORE"
ALIASES_FILE="$(mktemp 2>/dev/null || echo "/tmp/rustore-aliases.$$")"
if "$KEYTOOL" -list -keystore "$KEYSTORE" > "$ALIASES_FILE" 2>&1; then
    if grep -q "^$ALIAS," "$ALIASES_FILE"; then
        info "алиас «$ALIAS» найден"
    else
        warn "в ключе нет записи с алиасом «$ALIAS». Записи, которые есть:"
        sed 's/^/      /' "$ALIASES_FILE" >&2
        rm -f "$ALIASES_FILE"
        die "запустите заново с --alias=<нужный алиас из списка выше>"
    fi
else
    warn "список алиасов прочитать не удалось — продолжаю как есть"
fi
rm -f "$ALIASES_FILE"

# --- 4. PEPK → ZIP ---------------------------------------------------------
step 4/5 "Шифрую ключ подписи (PEPK) → $OUTDIR/pepk_out.zip"
rm -f "$OUTDIR/pepk_out.zip"
"${JAVA_CMD[@]}" -jar "$PEPK" \
    --keystore="$KEYSTORE" --alias="$ALIAS" \
    --output="$OUTDIR/pepk_out.zip" \
    --encryptionkey="$ENCRYPTION_KEY" --include-cert
[ -s "$OUTDIR/pepk_out.zip" ] || die "PEPK не создал архив: $OUTDIR/pepk_out.zip"
ZIP_BYTES="$(wc -c < "$OUTDIR/pepk_out.zip" | tr -d '[:space:]')"
if [ "$ZIP_BYTES" -gt 102400 ]; then
    warn "архив $ZIP_BYTES байт — консоль принимает ZIP не больше 100 КБ"
else
    info "готово: $OUTDIR/pepk_out.zip ($ZIP_BYTES байт, лимит 100 КБ)"
fi
info "внутри: certificate.pem + encryptedPrivateKey"
if command -v unzip >/dev/null 2>&1; then
    # Только для информации: сбой unzip не должен обрывать выгрузку сертификата.
    unzip -l "$OUTDIR/pepk_out.zip" 2>/dev/null | sed 's/^/    /' || true
fi

# --- 5. Сертификат ключа загрузки -----------------------------------------
step 5/5 "Сертификат ключа загрузки → $OUTDIR/uploadcert.pem"
if [ ! -f "$UPLOAD_KEYSTORE" ]; then
    die "нет файла ключа загрузки: $UPLOAD_KEYSTORE. Это тот keystore, которым подписан AAB (в CI — секреты KEYSTORE_FILE/KEY_ALIAS). Создайте его командой:
  keytool -genkeypair -v -keystore $UPLOAD_KEYSTORE -alias $UPLOAD_ALIAS -keyalg RSA -keysize 4096 -validity 10000"
fi
rm -f "$OUTDIR/uploadcert.pem"
"$KEYTOOL" -exportcert -alias "$UPLOAD_ALIAS" -keystore "$UPLOAD_KEYSTORE" -rfc -file "$OUTDIR/uploadcert.pem"
[ -s "$OUTDIR/uploadcert.pem" ] || die "сертификат не выгружен: $OUTDIR/uploadcert.pem"
grep -q 'BEGIN CERTIFICATE' "$OUTDIR/uploadcert.pem" || die "файл $OUTDIR/uploadcert.pem не похож на PEM-сертификат"
info "готово: $OUTDIR/uploadcert.pem"
info "отпечаток ключа загрузки (PEM):"
"$KEYTOOL" -printcert -file "$OUTDIR/uploadcert.pem" 2>/dev/null | grep -E 'SHA1|SHA256|Owner' | sed 's/^/    /' || true
info "отпечаток ключа подписи приложения (сравните со страницей «Подпись приложения»):"
"$KEYTOOL" -list -v -keystore "$KEYSTORE" -alias "$ALIAS" 2>/dev/null | grep -E 'SHA1:|SHA256:|Owner:' | sed 's/^/    /' || true

cat <<EOF

--------------------------------------------------------------------------
Готово. Дальше — в окне «Ключ подписи приложения» (RuStore Консоль):

  1. «Добавьте созданный ZIP-архив» ← $OUTDIR/pepk_out.zip  (не больше 100 КБ)
  2. поле сертификата ключа загрузки (PEM) ← $OUTDIR/uploadcert.pem
  3. «Отправить подпись», дождаться статуса «Подпись загружена».
  4. Только после этого загружать сам AAB: он должен быть подписан тем же
     ключом загрузки ($UPLOAD_KEYSTORE).

Проверьте перед релизом:
  * $KEYSTORE и его пароль — в двух местах (менеджер паролей + офлайн-копия);
  * в Google Play при первой загрузке выберите «Использовать другой ключ
    подписи приложений» и загрузите PEPK-архив, сделанный из ЭТОГО ЖЕ
    $KEYSTORE (со своим encryptionkey из Play Console) — тогда подпись
    в обоих магазинах совпадает и обновления не ломаются;
  * $OUTDIR/ коммитить нельзя — он в .gitignore.
--------------------------------------------------------------------------
EOF
