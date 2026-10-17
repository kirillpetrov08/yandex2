package com.converterplus.app.engine.ocr

/** Распознанный текст страницы. */
data class OcrPage(val number: Int, val text: String)

/**
 * Сборка итогового текста из страниц. Чистая логика — покрыта тестами.
 *
 * Tesseract отдаёт «сырой» текст: хвостовые пробелы, пачки пустых строк и мусорные
 * строки из декоративных элементов постера (`| <> — — =`). Здесь всё это вычищается.
 */
object OcrText {

    private val MULTIPLE_NEWLINES = Regex("\n{3,}")
    private val SPACES = Regex("[ \\t]{2,}")
    private val SPACE_BEFORE_PUNCT = Regex("\\s+([,.;:!?])")

    /**
     * Строка считается мусором, если осмысленных символов (букв и цифр) в ней
     * меньше 30 % от всех видимых. Так отсекаются линейки, рамки и «+ _ _ _»,
     * но остаются короткие подписи и номера страниц.
     */
    private const val MEANINGFUL_RATIO_PERCENT = 30

    /** Символы, из которых состоит «мусорный» токен: рамки, линейки, плюсы, решётки. */
    private const val NOISE_CHARS = "|<>~^=+_`´•·*#\\/@$%&"

    /**
     * Приводит текст в человеческий вид:
     * убирает мусорные строки, склеивает переносы, схлопывает пробелы.
     */
    fun clean(raw: String): String {
        val lines = raw
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')
            .map { normalizeLine(it) }

        val merged = ArrayList<String>(lines.size)
        for (line in lines) {
            val previous = merged.lastOrNull()
            // Перенос слова: «конверта-\nция» → «конвертация».
            if (line.isNotEmpty() &&
                previous != null &&
                previous.endsWith("-") &&
                line.first().isLowerCase()
            ) {
                merged[merged.lastIndex] = previous.dropLast(1) + line
            } else {
                merged.add(line)
            }
        }

        return merged
            .filter { it.isEmpty() || isMeaningful(it) }
            .joinToString("\n")
            .replace(MULTIPLE_NEWLINES, "\n\n")
            .trim()
    }

    /**
     * Чистка одной строки: убирает мусорные токены по краям и лишние пробелы.
     * Внутри строки текст не переписываем — распознанное содержимое важнее косметики.
     */
    fun normalizeLine(line: String): String {
        var tokens = line.trim()
            .split(' ')
            .filter { it.isNotEmpty() && !it.all { char -> char in NOISE_CHARS } }
        while (tokens.isNotEmpty() && tokens.first().none { it.isLetterOrDigit() }) {
            tokens = tokens.drop(1)
        }
        while (tokens.isNotEmpty() && tokens.last().none { it.isLetterOrDigit() }) {
            tokens = tokens.dropLast(1)
        }
        var result = tokens.joinToString(" ")
        result = result.replace(SPACES, " ")
        result = result.replace(SPACE_BEFORE_PUNCT, "$1")
        result = joinSpacedLetters(result)
        return result.trim()
    }

    /**
     * Строка несёт смысл, если в ней достаточно букв и цифр.
     * Отдельно пропускаем короткие числа — это номера страниц и пунктов.
     */
    fun isMeaningful(line: String): Boolean {
        val visible = line.count { !it.isWhitespace() }
        if (visible == 0) return false
        val meaningful = line.count { it.isLetterOrDigit() }
        if (meaningful == 0) return false
        if (meaningful * 100 >= visible * MEANINGFUL_RATIO_PERCENT) return true
        // «4», «12» — номер страницы, а не мусор.
        val trimmed = line.trim()
        return meaningful == visible && trimmed.length <= 4
    }

    /**
     * Постеры и «растянутые» шрифты иногда распознаются по одной букве через пробел:
     * «М и н и м а л ь н ы й». Если вся строка состоит из одиночных букв — склеиваем.
     */
    private fun joinSpacedLetters(line: String): String {
        val parts = line.split(' ')
        if (parts.size < 5 || parts.size > 24) return line
        val singleLetters = parts.count { it.length == 1 && it[0].isLetter() }
        if (singleLetters != parts.size) return line
        return parts.joinToString("")
    }

    /** Заголовок страницы в итоговом файле. */
    fun pageHeader(number: Int): String = "— Страница $number —"

    /**
     * Склеивает страницы в один текст. Заголовки добавляются, только если страниц больше одной,
     * а пустые страницы пропускаются — иначе в файле получаются заголовки без содержимого.
     */
    fun joinPages(pages: List<OcrPage>): String {
        val cleaned = pages
            .map { OcrPage(it.number, clean(it.text)) }
            .filter { it.text.isNotEmpty() }

        if (cleaned.isEmpty()) return ""
        if (cleaned.size == 1) return cleaned.first().text

        return cleaned.joinToString(separator = "\n\n") { page ->
            "${pageHeader(page.number)}\n\n${page.text}"
        }
    }
}
