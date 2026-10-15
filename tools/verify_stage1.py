#!/usr/bin/env python3
"""Статическая проверка проекта без Android SDK.

Что проверяет:
  1. Все XML-ресурсы и манифест хорошо сформированы.
  2. Каждый R.string.* / @string/* ссылается на существующую строку.
  3. Каждый R.drawable.* / R.mipmap.* / @drawable/* / @mipmap/* существует.
  4. Внутренние импорты (com.converterplus.app.*) ведут на реально объявленные классы.
  5. Классы из AndroidManifest.xml существуют в исходниках.
  6. Баланс скобок в каждом .kt файле (ловит обрезанные файлы).
  7. Нет маркеров-заглушек (TODO / FIXME / "здесь будет").
  8. Количество юнит-тестов.
  9. Обязательные файлы сборки, воркфлоу и ключи рекламы.
 10. В GitHub Actions — компиляция и юнит-тесты, а ошибки выводятся аннотациями
     к прогону. Полный лог шага виден только после входа в аккаунт GitHub,
     аннотации же показываются всем и отдают точные файл и строку.

Запуск: python3 tools/verify_stage1.py
"""
from __future__ import annotations

import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MAIN_JAVA = os.path.join(ROOT, "app", "src", "main", "java")
TEST_JAVA = os.path.join(ROOT, "app", "src", "test", "java")
RES = os.path.join(ROOT, "app", "src", "main", "res")
MANIFEST = os.path.join(ROOT, "app", "src", "main", "AndroidManifest.xml")

errors: list[str] = []
warnings: list[str] = []


def kt_files(base: str) -> list[str]:
    found = []
    for dirpath, _, names in os.walk(base):
        for name in names:
            if name.endswith(".kt"):
                found.append(os.path.join(dirpath, name))
    return sorted(found)


# ---------------------------------------------------------------- 1. XML
def check_xml() -> dict[str, set[str]]:
    declared: dict[str, set[str]] = {"string": set(), "color": set(), "style": set()}
    xml_paths = [MANIFEST]
    for dirpath, _, names in os.walk(RES):
        for name in names:
            if name.endswith(".xml"):
                xml_paths.append(os.path.join(dirpath, name))

    for path in xml_paths:
        try:
            tree = ET.parse(path)
        except ET.ParseError as exc:
            errors.append(f"XML: не разбирается {os.path.relpath(path, ROOT)}: {exc}")
            continue

        if path.endswith("strings.xml"):
            for node in tree.getroot():
                name = node.get("name")
                if name:
                    declared["string"].add(name)
        if path.endswith("colors.xml"):
            for node in tree.getroot():
                name = node.get("name")
                if name:
                    declared["color"].add(name)
        if path.endswith("themes.xml"):
            for node in tree.getroot():
                name = node.get("name")
                if name:
                    declared["style"].add(name)
    return declared


# ---------------------------------------------------------------- 2-3. ресурсы
def check_resource_references(strings: set[str]) -> None:
    drawables = set()
    mipmaps = set()
    for dirpath, _, names in os.walk(RES):
        folder = os.path.basename(dirpath)
        for name in names:
            if not name.endswith(".xml") and not name.endswith(".png") and not name.endswith(".webp"):
                continue
            stem = os.path.splitext(name)[0]
            if folder.startswith("drawable"):
                drawables.add(stem)
            elif folder.startswith("mipmap"):
                mipmaps.add(stem)

    pattern = re.compile(r"R\.(string|drawable|mipmap|color|style)\.([A-Za-z0-9_]+)")
    xml_pattern = re.compile(r"@(string|drawable|mipmap|color|style)/([A-Za-z0-9_]+)")

    for path in kt_files(MAIN_JAVA) + kt_files(TEST_JAVA):
        text = open(path, encoding="utf-8").read()
        for kind, name in pattern.findall(text):
            if kind == "string" and name not in strings:
                errors.append(f"{rel(path)}: R.string.{name} не объявлена в strings.xml")
            if kind == "drawable" and name not in drawables:
                errors.append(f"{rel(path)}: R.drawable.{name} не найден")
            if kind == "mipmap" and name not in mipmaps:
                errors.append(f"{rel(path)}: R.mipmap.{name} не найден")

    for dirpath, _, names in os.walk(RES):
        for name in names:
            if not name.endswith(".xml"):
                continue
            path = os.path.join(dirpath, name)
            text = open(path, encoding="utf-8").read()
            for kind, ref in xml_pattern.findall(text):
                if ref.startswith("android:") or ref.startswith("color/"):
                    continue
                if kind == "string" and ref not in strings:
                    errors.append(f"{rel(path)}: @string/{ref} не объявлена")
                if kind == "drawable" and ref not in drawables:
                    errors.append(f"{rel(path)}: @drawable/{ref} не найден")
                if kind == "mipmap" and ref not in mipmaps:
                    errors.append(f"{rel(path)}: @mipmap/{ref} не найден")

    unused = strings - {
        m.group(2)
        for path in kt_files(MAIN_JAVA)
        for m in pattern.finditer(open(path, encoding="utf-8").read())
    }
    if unused:
        warnings.append(f"strings.xml: не используется в коде: {', '.join(sorted(unused))}")


# ---------------------------------------------------------------- 4. импорты
DECLARATION = re.compile(
    r"^(?:public |internal |private |abstract |sealed |data |open |value |annotation )*"
    r"(?:class|object|interface|enum class|val|var|const val)\s+([A-Za-z0-9_]+)"
    r"|^(?:public |internal |private )*(?:suspend )?fun\s+(?:[\w<>.]+\.)?([A-Za-z0-9_]+)\s*\(",
    re.MULTILINE,
)

# Генерируемые классы: их нет в исходниках, они появляются при сборке.
GENERATED = {"R", "BuildConfig", "Manifest", "DatabindingManifest"}


def build_symbol_index() -> dict[str, set[str]]:
    index: dict[str, set[str]] = {}
    for path in kt_files(MAIN_JAVA):
        text = open(path, encoding="utf-8").read()
        package = re.search(r"^package\s+([\w.]+)", text, re.MULTILINE)
        if not package:
            continue
        index.setdefault(package.group(1), set()).update(_declared_symbols(text))
    return index


def check_imports(index: dict[str, set[str]]) -> None:
    import_pattern = re.compile(r"^import\s+(com\.converterplus\.app\.[\w.]+)", re.MULTILINE)
    for path in kt_files(MAIN_JAVA) + kt_files(TEST_JAVA):
        text = open(path, encoding="utf-8").read()
        for fq in import_pattern.findall(text):
            parts = fq.split(".")
            symbol = parts[-1]
            package = ".".join(parts[:-1])
            if symbol in GENERATED:
                continue
            known = index.get(package, set())
            if symbol in known:
                continue
            parent_known = index.get(".".join(parts[:-2]), set())
            if parts[-2] in parent_known:
                continue
            errors.append(f"{rel(path)}: import {fq} — символ не найден")


# ---------------------------------------------------------------- 5. манифест
def check_manifest() -> None:
    text = open(MANIFEST, encoding="utf-8").read()
    index = build_symbol_index()
    for name in re.findall(r'android:name="\.([A-Za-z0-9_.]+)"', text):
        parts = name.split(".")
        prefix = ".".join(parts[:-1])
        package = "com.converterplus.app." + prefix if prefix else "com.converterplus.app"
        symbol = parts[-1]
        if symbol not in index.get(package, set()):
            errors.append(f"AndroidManifest.xml: .{name} не найден в {package}")


def _declared_symbols(text: str) -> set[str]:
    result = set()
    for groups in DECLARATION.findall(text):
        symbol = next((g for g in groups if g), None)
        if symbol:
            result.add(symbol)
    return result


def _declared_by_kind(text: str) -> tuple[set[str], set[str]]:
    """(классы/объекты/интерфейсы, функции) — объявления верхнего уровня."""
    classes: set[str] = set()
    functions: set[str] = set()
    for groups in DECLARATION.findall(text):
        if groups[0]:
            classes.add(groups[0])
        if groups[1]:
            functions.add(groups[1])
    return classes, functions


# ---------------------------------------------------------------- 5a. забытые импорты
KOTLIN_BUILTIN = {
    "String", "Int", "Long", "Float", "Double", "Boolean", "Any", "Unit", "Nothing",
    "List", "Map", "Set", "Array", "Pair", "Triple", "Result", "Enum", "Throwable",
    "Exception", "Error", "CharSequence", "Char", "Byte", "Short", "Number", "Comparable",
    "Lazy", "Thread", "Object", "Override", "Suppress", "JvmStatic", "Volatile",
    "Composable", "OptIn", "ExperimentalMaterial3Api", "RequiresApi", "Test", "Rule",
}


def check_missing_imports(index: dict[str, set[str]]) -> None:
    """Символ объявлен в проекте, но используется без импорта и вне своего пакета.

    Полностью квалифицированные ссылки (com.converterplus.app.X.Y) импорта не требуют,
    поэтому идентификаторы после точки не учитываются.
    """
    class_owner: dict[str, set[str]] = {}
    function_owner: dict[str, set[str]] = {}
    for path in kt_files(MAIN_JAVA):
        text = strip_strings_and_comments(open(path, encoding="utf-8").read())
        match = re.search(r"^package\s+([\w.]+)", text, re.MULTILINE)
        if not match:
            continue
        classes, functions = _declared_by_kind(text)
        for name in classes:
            class_owner.setdefault(name, set()).add(match.group(1))
        for name in functions:
            function_owner.setdefault(name, set()).add(match.group(1))

    # Идентификатор, перед которым нет точки: отсечены квалифицированные обращения.
    ident = re.compile(r"(?<![\w.$])([A-Za-z_][A-Za-z0-9_]*)")

    for path in kt_files(MAIN_JAVA) + kt_files(TEST_JAVA):
        text = open(path, encoding="utf-8").read()
        package_match = re.search(r"^package\s+([\w.]+)", text, re.MULTILINE)
        package = package_match.group(1) if package_match else ""
        imported = {
            imp.rsplit(".", 1)[-1]
            for imp in re.findall(r"^import\s+([\w.]+)", text, re.MULTILINE)
        }
        body = strip_strings_and_comments(text)
        declared_here = _declared_symbols(body)

        for name in set(ident.findall(body)):
            if name in KOTLIN_BUILTIN or name in imported or name in declared_here:
                continue
            packages = (class_owner if name[0].isupper() else function_owner).get(name)
            if not packages:
                continue
            if package in packages:
                continue
            errors.append(
                f"{rel(path)}: используется {name} (пакет {sorted(packages)[0]}) без импорта",
            )


# ---------------------------------------------------------------- 5b. пакеты и дубли
def check_packages_and_duplicates() -> None:
    seen_classes: dict[str, str] = {}
    seen_composables: dict[str, str] = {}
    composable_decl = re.compile(
        r"^@Composable\s*(?:@[\w.]+\s*)*(?:private |internal )?fun\s+([A-Za-z0-9_]+)\s*\(",
        re.MULTILINE,
    )
    class_decl = re.compile(
        r"^(?:public |internal )?(?:abstract |sealed |data |open )*(?:class|object|interface|enum class)\s+([A-Za-z0-9_]+)",
        re.MULTILINE,
    )

    for path in kt_files(MAIN_JAVA):
        text = open(path, encoding="utf-8").read()
        package_match = re.search(r"^package\s+([\w.]+)", text, re.MULTILINE)
        if not package_match:
            errors.append(f"{rel(path)}: нет объявления package")
            continue
        package = package_match.group(1)

        expected_dir = os.path.join(MAIN_JAVA, package.replace(".", os.sep))
        if os.path.realpath(os.path.dirname(path)) != os.path.realpath(expected_dir):
            errors.append(
                f"{rel(path)}: package {package} не совпадает с каталогом "
                f"{os.path.relpath(os.path.dirname(path), MAIN_JAVA)}",
            )

        # Разрешаем вложенные объявления: берём только те, что в начале строки без отступа.
        body = strip_strings_and_comments(text)
        for name in class_decl.findall(body):
            key = f"{package}.{name}"
            if key in seen_classes:
                errors.append(f"{rel(path)}: дублируется объявление {key} (уже есть в {seen_classes[key]})")
            seen_classes[key] = rel(path)

        for name in composable_decl.findall(body):
            key = f"{package}.{name}"
            if key in seen_composables:
                errors.append(
                    f"{rel(path)}: дублируется @Composable {key} (уже есть в {seen_composables[key]})",
                )
            seen_composables[key] = rel(path)


# ---------------------------------------------------------------- 6. скобки
def check_balanced() -> None:
    pairs = {"{": "}", "(": ")", "[": "]"}
    closers = set(pairs.values())
    for path in kt_files(MAIN_JAVA) + kt_files(TEST_JAVA):
        text = open(path, encoding="utf-8").read()
        cleaned = strip_strings_and_comments(text)
        stack: list[str] = []
        for char in cleaned:
            if char in pairs:
                stack.append(pairs[char])
            elif char in closers:
                if not stack or stack.pop() != char:
                    errors.append(f"{rel(path)}: несбалансированные скобки")
                    break
        else:
            if stack:
                errors.append(f"{rel(path)}: не закрыто {len(stack)} скобок")


def strip_strings_and_comments(text: str) -> str:
    out = []
    i = 0
    n = len(text)
    while i < n:
        chunk = text[i : i + 3]
        if chunk == '"""':
            end = text.find('"""', i + 3)
            i = n if end == -1 else end + 3
            continue
        if text[i : i + 2] == "//":
            end = text.find("\n", i)
            i = n if end == -1 else end
            continue
        if text[i : i + 2] == "/*":
            end = text.find("*/", i + 2)
            i = n if end == -1 else end + 2
            continue
        if text[i] == '"':
            i += 1
            while i < n and text[i] != '"':
                if text[i] == "\\":
                    i += 1
                i += 1
            i += 1
            continue
        if text[i] == "'":
            i += 1
            while i < n and text[i] != "'":
                if text[i] == "\\":
                    i += 1
                i += 1
            i += 1
            continue
        out.append(text[i])
        i += 1
    return "".join(out)


# ---------------------------------------------------------------- 7. заглушки
def check_placeholders() -> None:
    pattern = re.compile(r"\bTODO\b|\bFIXME\b|здесь будет|реализовать позже|\.\.\.\s*$")
    for path in kt_files(MAIN_JAVA) + kt_files(TEST_JAVA):
        for number, line in enumerate(open(path, encoding="utf-8"), start=1):
            if pattern.search(line):
                errors.append(f"{rel(path)}:{number}: заглушка: {line.strip()}")


# ---------------------------------------------------------------- 8. тесты
def count_tests() -> tuple[int, int]:
    methods = 0
    files = 0
    for path in kt_files(TEST_JAVA):
        text = open(path, encoding="utf-8").read()
        found = len(re.findall(r"@Test", text))
        if found:
            files += 1
        methods += found
    return files, methods


# ---------------------------------------------------------------- 9. сборка
def check_gradle() -> None:
    for required in [
        "settings.gradle.kts",
        "build.gradle.kts",
        "gradle.properties",
        "gradle/wrapper/gradle-wrapper.jar",
        "gradle/wrapper/gradle-wrapper.properties",
        "gradlew",
        "app/build.gradle.kts",
        "app/proguard-rules.pro",
        # Документы и скрипты публикации — часть поставки, не удалять.
        "docs/RUSTORE_SIGNING.md",
        "docs/APK_RELEASE.md",
        "tools/rustore-signature.cmd",
        "tools/rustore-signature.sh",
    ]:
        if not os.path.exists(os.path.join(ROOT, required)):
            errors.append(f"нет обязательного файла: {required}")

    # Воркфлоу: либо на месте, либо лежит копией в docs (push в .github/workflows боту запрещён).
    if not (
        os.path.exists(os.path.join(ROOT, ".github", "workflows", "android.yml"))
        or os.path.exists(os.path.join(ROOT, "docs", "ci-android.yml"))
    ):
        errors.append("нет CI-воркфлоу: ни .github/workflows/android.yml, ни docs/ci-android.yml")

    props = open(os.path.join(ROOT, "gradle.properties"), encoding="utf-8").read()
    for key in [
        "ads.testMode",
        "ads.bannerUnitId",
        "ads.interstitialUnitId",
        "ads.rewardedUnitId",
        "ads.interstitialEveryNthConversion",
        "ads.interstitialMinIntervalSeconds",
        "ads.rewardedNoAdsMinutes",
    ]:
        if not re.search(rf"^{re.escape(key)}=", props, re.MULTILINE):
            errors.append(f"gradle.properties: нет ключа {key}")

    wrapper = open(
        os.path.join(ROOT, "gradle", "wrapper", "gradle-wrapper.properties"), encoding="utf-8"
    ).read()
    if "gradle-8.11.1-bin.zip" not in wrapper:
        errors.append("gradle-wrapper.properties: ожидается Gradle 8.11.1")


def rel(path: str) -> str:
    return os.path.relpath(path, ROOT)


# ---------------------------------------------------------------- 10. CI: сборка
# Ошибки, по которым видно причину падения. Порядок строк сохраняем: сначала
# ошибки компилятора (файл и строка), потом блок «What went wrong» из Gradle.
ERROR_LINE_PATTERNS = (
    re.compile(r"^e: "),                        # ошибки компилятора Kotlin
    re.compile(r"^\s*error: "),                 # ошибки компилятора Java
    re.compile(r"^e: file://"),                 # дубль на случай префикса
    re.compile(r"^> Task .* FAILED$"),          # упавшая задача
    re.compile(r"FAILURE: Build failed"),
    re.compile(r"^\* What went wrong:"),
    re.compile(r"^> Could not"),
    re.compile(r"^> A problem occurred"),
    re.compile(r"^> Execution failed for task"),
    re.compile(r"^(?:Caused by: )"),
    re.compile(r"FAILED$"),                     # «Класс > метод FAILED» из тестов
)

CI_DIAGNOSTICS_TIMEOUT = 1500  # секунд: столько ждём Gradle перед тем, как прервать
ANNOTATION_LIMIT = 2500        # символов в одной аннотации
ANNOTATION_COUNT = 6           # максимум аннотаций с ошибками


def gh_escape_property(text: str) -> str:
    """Экранирование значения свойства workflow-команды (title=)."""
    return text.replace("%", "%25").replace("\r", "").replace("\n", "%0A") \
        .replace(":", "%3A").replace(",", "%2C")


def gh_escape_message(text: str) -> str:
    """Экранирование текста аннотации: переводы строк переносятся, а не рвут команду."""
    return text.replace("%", "%25").replace("\r", "").replace("\n", "%0A")


def gh_error(title: str, message: str) -> None:
    print(f"::error title={gh_escape_property(title)}::{gh_escape_message(message)}", flush=True)


def pick_error_lines(lines: list[str]) -> list[str]:
    """Оставляет строки, по которым видно причину падения (файл, строка, текст)."""
    picked: list[str] = []
    for line in lines:
        for pattern in ERROR_LINE_PATTERNS:
            if pattern.search(line):
                picked.append(line.rstrip())
                break
    return picked


def chunk_lines(lines: list[str], limit: int) -> list[str]:
    """Собирает строки в порции длиной не больше limit символов."""
    chunks: list[str] = []
    current: list[str] = []
    size = 0
    for line in lines:
        if current and size + len(line) + 1 > limit:
            chunks.append("\n".join(current))
            current, size = [], 0
        current.append(line)
        size += len(line) + 1
    if current:
        chunks.append("\n".join(current))
    return chunks


def ci_build_and_report() -> bool:
    """В GitHub Actions компилирует проект и гоняет юнит-тесты.

    Возвращает True, если можно продолжать проверки. Падение превращается в
    аннотации к прогону: полный лог шага в браузере требует входа в аккаунт,
    а аннотации к прогону читаются и через API. Локально (без GITHUB_ACTIONS
    =true) функция ничего не делает, чтобы правка кода не запускала сборку.
    """
    if os.environ.get("GITHUB_ACTIONS") != "true":
        return True

    gradlew = os.path.join(ROOT, "gradlew")
    if not os.path.exists(gradlew):
        warnings.append("CI: нет ./gradlew — компиляция и тесты в этом шаге пропущены")
        return True

    command = [gradlew, "--console=plain", "--stacktrace", "testDebugUnitTest"]
    print("CI: " + " ".join(command), flush=True)
    try:
        process = subprocess.Popen(
            command,
            cwd=ROOT,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            errors="replace",
            bufsize=1,
        )
    except OSError as exc:
        warnings.append(f"CI: не удалось запустить Gradle: {exc}")
        return True

    collected: list[str] = []
    deadline = time.monotonic() + CI_DIAGNOSTICS_TIMEOUT
    assert process.stdout is not None
    for raw in process.stdout:
        collected.append(raw.rstrip("\n"))
        sys.stdout.write(raw)
        sys.stdout.flush()
        if time.monotonic() > deadline:
            process.kill()
            warnings.append(f"CI: Gradle не уложился в {CI_DIAGNOSTICS_TIMEOUT} с — прерываю")
            break
    process.wait()

    # Полный лог — в каталог, который CI забирает артефактом «Отчёт тестов».
    log_path = os.path.join(ROOT, "app", "build", "reports", "tests",
                            "testDebugUnitTest", "ci-gradle.log")
    try:
        os.makedirs(os.path.dirname(log_path), exist_ok=True)
        with open(log_path, "w", encoding="utf-8") as handle:
            handle.write("\n".join(collected) + "\n")
    except OSError:
        pass

    if process.returncode == 0:
        print("CI: компиляция и тесты прошли", flush=True)
        return True

    print(f"CI: Gradle завершился с кодом {process.returncode}", flush=True)
    interesting = pick_error_lines(collected)
    if not interesting:
        interesting = collected[-25:]
    for number, chunk in enumerate(chunk_lines(interesting, ANNOTATION_LIMIT), start=1):
        if number > ANNOTATION_COUNT:
            gh_error("Ошибок больше", "Остальные строки — в логе шага и в ci-gradle.log")
            break
        gh_error(f"Сборка упала ({number})", chunk)
    return False


def main() -> int:
    declared = check_xml()
    strings = declared["string"]
    check_resource_references(strings)
    symbol_index = build_symbol_index()
    check_imports(symbol_index)
    check_missing_imports(symbol_index)
    check_manifest()
    check_packages_and_duplicates()
    check_balanced()
    check_placeholders()
    check_gradle()
    test_files, test_methods = count_tests()

    main_count = len(kt_files(MAIN_JAVA))
    print(f"Kotlin-файлов (main): {main_count}")
    print(f"Тестовых классов: {test_files}, тестовых методов: {test_methods}")
    print(f"Строк в strings.xml: {len(strings)}")

    for warning in warnings:
        print(f"WARN  {warning}")
    for error in errors:
        print(f"ERROR {error}")

    if errors:
        print(f"\nПРОВАЛЕНО: ошибок {len(errors)}")
        return 1

    if not ci_build_and_report():
        print("\nПРОВАЛЕНО: сборка или тесты не прошли — смотрите аннотации к прогону")
        return 1

    print("\nСТАТИЧЕСКАЯ ПРОВЕРКА ПРОЙДЕНА")
    return 0


if __name__ == "__main__":
    sys.exit(main())
