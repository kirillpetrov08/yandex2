package com.converterplus.app.data

import java.io.File

/** Статус записи истории. */
enum class HistoryStatus { READY, SAVED, FAILED }

/**
 * Одна запись истории.
 * Поля соответствуют формату history.tsv (см. [HistoryTsv]):
 * id, timestamp, имя, из, в, размерБ, uri, статус, локальный путь.
 */
data class HistoryEntry(
    val id: String,
    val timestamp: Long,
    val fileName: String,
    val fromFormat: String,
    val toFormat: String,
    val sizeBytes: Long,
    val uri: String?,
    val status: HistoryStatus,
    val localPath: String?,
) {
    /** Пара «из → в» для карточки истории. */
    val formatPair: String get() = "${fromFormat.uppercase()} → ${toFormat.uppercase()}"
}

/**
 * Чистые функции сериализации истории: TSV, одна запись на строку.
 * Никаких баз данных и плагинов сериализации — парсинг покрыт юнит-тестами.
 */
object HistoryTsv {

    const val MAX_ENTRIES = 500
    private const val SEPARATOR = '\t'
    private const val EMPTY = "-"

    fun encode(entry: HistoryEntry): String = listOf(
        entry.id,
        entry.timestamp.toString(),
        escape(entry.fileName),
        entry.fromFormat,
        entry.toFormat,
        entry.sizeBytes.toString(),
        entry.uri ?: EMPTY,
        entry.status.name,
        entry.localPath ?: EMPTY,
    ).joinToString(SEPARATOR.toString())

    fun decode(line: String): HistoryEntry? {
        if (line.isBlank()) return null
        val parts = line.split(SEPARATOR)
        if (parts.size < 9) return null
        val id = unescape(parts[0])
        val timestamp = parts[1].toLongOrNull() ?: return null
        val status = runCatching { HistoryStatus.valueOf(parts[7]) }.getOrNull() ?: return null
        if (id.isBlank()) return null

        return HistoryEntry(
            id = id,
            timestamp = timestamp,
            fileName = unescape(parts[2]),
            fromFormat = parts[3],
            toFormat = parts[4],
            sizeBytes = parts[5].toLongOrNull() ?: 0L,
            uri = parts[6].takeIf { it != EMPTY },
            status = status,
            localPath = parts[8].takeIf { it != EMPTY },
        )
    }

    fun encodeAll(entries: List<HistoryEntry>): String =
        entries.joinToString("\n") { encode(it) }

    fun decodeAll(text: String): List<HistoryEntry> =
        text.lineSequence().mapNotNull { decode(it) }

    /** Новые записи сверху, сверх лимита отбрасываются. */
    fun enforceLimit(entries: List<HistoryEntry>, limit: Int = MAX_ENTRIES): List<HistoryEntry> =
        entries.sortedByDescending { it.timestamp }.take(limit)

    /** Поиск по имени и фильтр по выходному формату. */
    fun filter(entries: List<HistoryEntry>, query: String, format: String?): List<HistoryEntry> {
        val needle = query.trim().lowercase()
        return entries.filter { entry ->
            val matchesQuery = needle.isEmpty() ||
                entry.fileName.lowercase().contains(needle) ||
                entry.formatPair.lowercase().contains(needle)
            val matchesFormat = format.isNullOrBlank() ||
                format.equals(entry.toFormat, ignoreCase = true) ||
                format.equals(entry.fromFormat, ignoreCase = true)
            matchesQuery && matchesFormat
        }
    }

    /** Форматы, которые встречались в истории, — для chips-фильтра. */
    fun availableFormats(entries: List<HistoryEntry>): List<String> =
        entries.map { it.toFormat.uppercase() }.distinct().sorted()

    private fun escape(value: String): String =
        value.replace("\t", " ").replace("\n", " ").replace("\r", " ")

    private fun unescape(value: String): String = value
}

/**
 * История в одном текстовом файле `filesDir/history.tsv`.
 * Запись атомарная: временный файл + rename.
 */
class HistoryStore(private val file: File) {

    fun load(): List<HistoryEntry> {
        if (!file.exists()) return emptyList()
        return try {
            HistoryTsv.decodeAll(file.readText())
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(entry: HistoryEntry) {
        val updated = HistoryTsv.enforceLimit(listOf(entry) + load())
        writeAtomic(updated)
    }

    fun addAll(entries: List<HistoryEntry>) {
        if (entries.isEmpty()) return
        val updated = HistoryTsv.enforceLimit(entries + load())
        writeAtomic(updated)
    }

    fun update(entry: HistoryEntry) {
        val current = load()
        val index = current.indexOfFirst { it.id == entry.id }
        if (index < 0) {
            add(entry)
            return
        }
        val updated = current.toMutableList().also { it[index] = entry }
        writeAtomic(updated)
    }

    fun delete(id: String) {
        writeAtomic(load().filterNot { it.id == id })
    }

    fun findById(id: String): HistoryEntry? = load().firstOrNull { it.id == id }

    fun clear() {
        writeAtomic(emptyList())
    }

    private fun writeAtomic(entries: List<HistoryEntry>) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(HistoryTsv.encodeAll(entries))
        if (!temp.renameTo(file)) {
            // rename не сработал (редкий случай) — пишем напрямую
            file.writeText(HistoryTsv.encodeAll(entries))
            temp.delete()
        }
    }

    companion object {
        const val FILE_NAME = "history.tsv"

        fun default(context: android.content.Context): HistoryStore =
            HistoryStore(File(context.filesDir, FILE_NAME))
    }
}
