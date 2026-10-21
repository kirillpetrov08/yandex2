package com.converterplus.app.engine.pdf

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * Проверка PDF до конвертации: читается ли файл и не защищён ли он паролем.
 * Пароль не поддерживается намеренно — об этом честно говорит интерфейс.
 */
object PdfProbe {

    sealed interface Result {
        data class Pages(val count: Int) : Result
        data object PasswordProtected : Result
        data object Unreadable : Result
    }

    fun probe(file: File): Result {
        if (!file.exists() || file.length() == 0L) return Result.Unreadable
        return try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer -> Result.Pages(renderer.pageCount) }
            }
        } catch (e: Exception) {
            if (isPasswordError(e)) Result.PasswordProtected else Result.Unreadable
        }
    }

    /** Количество страниц; null, если файл не читается. */
    fun pageCount(file: File): Int? = when (val result = probe(file)) {
        is Result.Pages -> result.count
        else -> null
    }

    private fun isPasswordError(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            val message = current.message?.lowercase().orEmpty()
            if (message.contains("password") || message.contains("парол")) return true
            current = current.cause
        }
        return false
    }
}
