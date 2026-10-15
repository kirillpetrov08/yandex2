package com.converterplus.app.engine

import com.converterplus.app.R
import java.io.IOException

/** Ошибки конвертации. Каждой соответствует понятное человеку сообщение из strings.xml. */
sealed class ConversionException(
    val messageRes: Int,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class NoInputException : ConversionException(R.string.err_no_input, "нет входного файла")

class CannotOpenFileException(cause: Throwable? = null) :
    ConversionException(R.string.err_cannot_open, "файл не открывается", cause)

class OutOfSpaceException(cause: Throwable? = null) :
    ConversionException(R.string.err_no_space, "недостаточно места", cause)

class EmptyPdfException : ConversionException(R.string.err_pdf_empty, "в PDF нет страниц")

class NoPagesSelectedException :
    ConversionException(R.string.err_no_pages_selected, "не выбрано ни одной страницы")

class UnsupportedToolException(tool: ToolKind) :
    ConversionException(R.string.reason_tool_soon, "инструмент ещё не реализован: ${tool.id}")

class UnsupportedFormatException(format: String) :
    ConversionException(R.string.reason_format_not_supported, "формат не поддерживается: $format")

/** Преобразование любого исключения в ресурс сообщения для интерфейса. */
fun Throwable.toMessageRes(): Int = when (this) {
    is ConversionException -> messageRes
    is kotlinx.coroutines.CancellationException -> R.string.err_cancelled
    is java.io.EOFException -> R.string.err_cannot_open
    is IOException -> if (isProbablyOutOfSpace()) R.string.err_no_space else R.string.err_generic
    else -> R.string.err_generic
}

private fun Throwable.isProbablyOutOfSpace(): Boolean {
    val text = (message.orEmpty() + " " + (cause?.message.orEmpty())).lowercase()
    return text.contains("no space left") ||
        text.contains("enospc") ||
        text.contains("not enough space") ||
        this is java.io.IOException && text.contains("write failed")
}
