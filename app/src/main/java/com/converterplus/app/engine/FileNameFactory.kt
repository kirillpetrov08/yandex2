package com.converterplus.app.engine

/**
 * Имена выходных файлов: расширение, недопустимые символы, коллизии.
 * Чистые функции — покрыты юнит-тестами.
 */
object FileNameFactory {

    /** Символы, которые нельзя использовать в имени файла. */
    private val INVALID_CHARS = Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]")

    private const val FALLBACK_BASE = "conversion"
    private const val MAX_BASE_LENGTH = 80

    fun baseNameWithoutExtension(fileName: String): String {
        val name = fileName.substringBeforeLast('/', fileName)
        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(0, dot) else name
    }

    /** Убирает недопустимые символы и лишние пробелы, гарантирует непустое имя. */
    fun sanitizeBaseName(name: String): String {
        val cleaned = INVALID_CHARS.replace(name, "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('.')
        val limited = if (cleaned.length > MAX_BASE_LENGTH) cleaned.takeLast(MAX_BASE_LENGTH) else cleaned
        return limited.ifBlank { FALLBACK_BASE }
    }

    /** Собирает имя: «основа.ext» с нормализованным расширением. */
    fun withExtension(base: String, extension: String): String {
        val ext = extension.trimStart('.').lowercase()
        return "${sanitizeBaseName(base)}.$ext"
    }

    /**
     * Имя выходного файла без коллизий: photo.png → photo.png, photo (1).png, photo (2).png…
     *
     * @param existingNames уже занятые имена (без учёта регистра).
     */
    fun outputName(inputFileName: String, outputFormat: String, existingNames: Set<String>): String {
        val base = sanitizeBaseName(baseNameWithoutExtension(inputFileName))
        val ext = outputFormat.trimStart('.').lowercase()
        val taken = existingNames.map { it.lowercase() }.toSet()

        var candidate = "$base.$ext"
        var index = 1
        while (candidate.lowercase() in taken) {
            candidate = "$base ($index).$ext"
            index++
        }
        return candidate
    }

    /** Имена для пачки файлов: page-01.png, page-02.png… */
    fun pagedNames(baseName: String, outputFormat: String, pages: List<Int>): List<String> {
        val base = sanitizeBaseName(baseNameWithoutExtension(baseName))
        val ext = outputFormat.trimStart('.').lowercase()
        val width = pages.size.toString().length.coerceAtLeast(2)
        return pages.mapIndexed { index, page ->
            val number = (index + 1).toString().padStart(width, '0')
            "$base-$number.$ext"
        }
    }
}
