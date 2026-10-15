package com.converterplus.app.engine.ocr

/** Распознанный текст страницы. */
data class OcrPage(val number: Int, val text: String)

/**
 * Сборка итогового текста из страниц. Чистая логика — покрыта тестами:
 * Tesseract отдаёт «сырой» текст с хвостовыми пробелами и пачками пустых строк.
 */
object OcrText {

    private val MULTIPLE_NEWLINES = Regex("\n{3,}")

    /** Приводит текст в человеческий вид: убирает хвостовые пробелы и лишние пустые строки. */
    fun clean(raw: String): String = raw
        .replace("\r\n", "\n")
        .lines()
        .joinToString("\n") { it.trimEnd() }
        .replace(MULTIPLE_NEWLINES, "\n\n")
        .trim()

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
