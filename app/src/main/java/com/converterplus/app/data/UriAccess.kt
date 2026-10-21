package com.converterplus.app.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import com.converterplus.app.engine.FileNameFactory
import com.converterplus.app.engine.PickedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Работа с URI: имя, размер, копирование в кэш, persistable-разрешения и список «Недавние».
 *
 * Разрешений на хранилище нет: выбор идёт системными пикерами, а доступ к выбранному URI
 * даётся самим пикером. Копия в кэше нужна, чтобы фоновая конвертация не зависела
 * от срока жизни URI-гранта.
 */
object UriAccess {

    private const val PREFS = "uri_access"
    private const val KEY_RECENTS = "recent_files"
    private const val RECENT_LIMIT = 3
    private const val RECENT_SEPARATOR = '\u001F'

    private val _recents = MutableStateFlow<List<RecentFile>>(emptyList())
    val recents: StateFlow<List<RecentFile>> = _recents.asStateFlow()

    fun init(context: Context) {
        _recents.value = readRecents(prefs(context)).filter { hasAccess(context, it.uri) }
    }

    fun displayName(context: Context, uri: Uri): String? =
        queryColumn(context, uri, OpenableColumns.DISPLAY_NAME)

    fun sizeBytes(context: Context, uri: Uri): Long =
        queryColumn(context, uri, OpenableColumns.SIZE)?.toLongOrNull() ?: 0L

    fun mimeType(context: Context, uri: Uri): String? = try {
        context.contentResolver.getType(uri)
    } catch (e: Exception) {
        null
    }

    /** Закрепляем доступ к URI, если пикер его выдал (ACTION_OPEN_DOCUMENT). */
    fun takePersistablePermission(context: Context, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (e: SecurityException) {
            // Пикер фотографий persistable-грант не выдаёт — это нормально, файл уже скопирован.
        }
    }

    /**
     * Копирует выбранный файл в кэш приложения и возвращает [PickedFile].
     * @param folderName подпапка кэша (обычно id запроса) — чистится после конвертации.
     */
    suspend fun copyToCache(context: Context, uri: Uri, folderName: String): PickedFile =
        withContext(Dispatchers.IO) {
            val originalName = displayName(context, uri) ?: "file"
            val safeName = FileNameFactory.sanitizeBaseName(originalName).let { base ->
                if (base.contains('.')) base else "$base.${extensionFromMime(mimeType(context, uri))}"
            }
            val dir = File(context.cacheDir, "inputs/$folderName").apply { mkdirs() }
            val target = uniqueFile(dir, safeName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            } ?: throw java.io.FileNotFoundException("Не удалось открыть $uri")

            PickedFile(
                localPath = target.absolutePath,
                displayName = originalName,
                sizeBytes = target.length(),
                mimeType = mimeType(context, uri),
            )
        }

    /** Есть ли ещё доступ к URI (для очистки «Недавних»). */
    fun hasAccess(context: Context, uriString: String): Boolean {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return false
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor: Cursor ->
                cursor.moveToFirst()
            } ?: false
        } catch (e: SecurityException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    /** «Недавние»: три последних файла, доступ к которым не отозван. */
    fun rememberRecent(context: Context, uriString: String, name: String) {
        val current = _recents.value.filterNot { it.uri == uriString }
        val updated = (listOf(RecentFile(uriString, name)) + current).take(RECENT_LIMIT)
        _recents.value = updated
        writeRecents(prefs(context), updated)
    }

    fun clearRecents(context: Context) {
        _recents.value = emptyList()
        prefs(context).edit().remove(KEY_RECENTS).apply()
    }

    /** Удаляет записи, к которым доступ отозван. */
    fun pruneInaccessible(context: Context) {
        val valid = _recents.value.filter { hasAccess(context, it.uri) }
        if (valid.size != _recents.value.size) {
            _recents.value = valid
            writeRecents(prefs(context), valid)
        }
    }

    private fun queryColumn(context: Context, uri: Uri, column: String): String? = try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(column)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    } catch (e: Exception) {
        null
    }

    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        var index = 1
        while (candidate.exists()) {
            val base = FileNameFactory.baseNameWithoutExtension(name)
            val ext = name.substringAfterLast('.', "")
            candidate = File(dir, if (ext.isBlank()) "$base($index)" else "$base($index).$ext")
            index++
        }
        return candidate
    }

    private fun extensionFromMime(mime: String?): String = when (mime?.lowercase()) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/heic", "image/heif" -> "heic"
        "image/avif" -> "avif"
        "application/pdf" -> "pdf"
        "text/plain" -> "txt"
        else -> "bin"
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun readRecents(prefs: SharedPreferences): List<RecentFile> =
        prefs.getString(KEY_RECENTS, null)
            ?.split('\n')
            ?.mapNotNull { line ->
                val parts = line.split(RECENT_SEPARATOR)
                if (parts.size < 2) null else RecentFile(parts[0], parts[1])
            }
            .orEmpty()

    private fun writeRecents(prefs: SharedPreferences, recents: List<RecentFile>) {
        val raw = recents.joinToString("\n") { "${it.uri}$RECENT_SEPARATOR${it.name}" }
        prefs.edit().putString(KEY_RECENTS, raw).apply()
    }
}

data class RecentFile(val uri: String, val name: String)
