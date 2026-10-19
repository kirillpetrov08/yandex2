package com.copilka.app.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Куда лёг сохранённый файл. */
sealed interface SaveOutcome {
    /** Сохранено через MediaStore или SAF — есть content:// URI. */
    data class Content(val uri: String) : SaveOutcome

    /** Сохранено напрямую в файловую систему — есть путь. */
    data class Path(val path: String) : SaveOutcome
}

/**
 * Сохранение результата.
 *
 * - API 29+: [MediaStore.Downloads] — файл появляется в «Загрузках», виден любому проводнику,
 *   разрешений не нужно.
 * - Выбрана своя папка (SAF): запись через [DocumentFile] — работает на всех версиях без разрешений.
 * - API 26–28 без выбранной папки: сохранение идёт через системный диалог
 *   [androidx.activity.result.contract.ActivityResultContracts.CreateDocument]
 *   (см. [needsDocumentPicker]). Прямая запись в общую папку «Загрузки» на этих версиях
 *   требует WRITE_EXTERNAL_STORAGE, а разрешений на хранилище в приложении нет намеренно.
 */
class FileSaver(private val context: Context) {

    /** Нужно ли на этом устройстве показывать системный диалог сохранения. */
    fun needsDocumentPicker(treeUri: String?): Boolean =
        treeUri.isNullOrBlank() && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    suspend fun save(
        source: File,
        displayName: String,
        treeUri: String?,
    ): SaveOutcome = withContext(Dispatchers.IO) {
        if (!source.exists()) throw IOException("Файл результата недоступен")

        when {
            !treeUri.isNullOrBlank() -> saveToTree(source, displayName, treeUri)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> saveToDownloads(source, displayName)
            else -> throw IOException("На этой версии Android нужна папка сохранения")
        }
    }

    /** Запись в URI, выбранный в системном диалоге сохранения. */
    suspend fun saveToUri(source: File, target: Uri): SaveOutcome = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(target)?.use { output ->
            source.inputStream().use { input -> input.copyTo(output) }
        } ?: throw IOException("Не удалось открыть поток записи")
        SaveOutcome.Content(target.toString())
    }

    private fun saveToTree(source: File, displayName: String, treeUri: String): SaveOutcome {
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
            ?: throw IOException("Папка недоступна")
        if (!tree.canWrite()) throw IOException("Нет доступа на запись в выбранную папку")

        val mime = mimeTypeOf(displayName)
        val baseName = displayName.substringBeforeLast('.', displayName)
        val created = tree.createFile(mime, baseName)
            ?: throw IOException("Не удалось создать файл в папке")

        context.contentResolver.openOutputStream(created.uri)?.use { output ->
            source.inputStream().use { input -> input.copyTo(output) }
        } ?: throw IOException("Не удалось открыть поток записи")

        return SaveOutcome.Content(created.uri.toString())
    }

    private fun saveToDownloads(source: File, displayName: String): SaveOutcome {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, mimeTypeOf(displayName))
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + SUBFOLDER)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("MediaStore не создал файл")

        try {
            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IOException("Не удалось открыть поток записи")
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }

        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return SaveOutcome.Content(uri.toString())
    }

    companion object {
        const val SUBFOLDER = "Конвертер+"

        fun mimeTypeOf(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "wav" -> "audio/wav"
            "m4a" -> "audio/mp4"
            "ogg", "opus" -> "audio/ogg"
            "mp4" -> "video/mp4"
            else -> "application/octet-stream"
        }
    }
}
