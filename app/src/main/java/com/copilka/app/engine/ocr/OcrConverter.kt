package com.copilka.app.engine.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.copilka.app.engine.CannotOpenFileException
import com.copilka.app.engine.EmptyPdfException
import com.copilka.app.engine.NoPagesSelectedException
import com.copilka.app.engine.OcrNoTextException
import com.copilka.app.engine.OutputArtifact
import com.copilka.app.engine.image.ImageConverter
import kotlinx.coroutines.ensureActive
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Распознавание текста: картинка или PDF → TXT.
 *
 * Страницы PDF рендерятся платформенным [PdfRenderer] в битмап и распознаются по очереди
 * одним экземпляром движка. Прогресс считается по страницам, а для одиночной картинки
 * равен 100 % сразу после распознавания.
 */
object OcrConverter {

    suspend fun convert(
        context: Context,
        inputFile: File,
        outputFile: File,
        selectedPages: List<Int>?,
        renderDpi: Int,
        imageMaxSide: Int,
        onProgress: (Int) -> Unit,
    ): OutputArtifact {
        if (!inputFile.exists() || inputFile.length() == 0L) throw CannotOpenFileException()

        val isPdf = inputFile.name.substringAfterLast('.', "").lowercase() == "pdf"
        val dataPath = TessdataStore.prepare(context).absolutePath
        val engine = OcrEngine(dataPath)
        engine.start()

        val pages: List<OcrPage> = try {
            if (isPdf) {
                recognizePdf(engine, inputFile, selectedPages, renderDpi, onProgress)
            } else {
                val bitmap = ImageConverter.decodePreview(inputFile, imageMaxSide)
                    ?: throw CannotOpenFileException()
                try {
                    onProgress(50)
                    val prepared = OcrPreprocess.prepare(bitmap)
                    try {
                        listOf(OcrPage(1, engine.recognize(prepared)))
                    } finally {
                        prepared.recycle()
                    }
                } finally {
                    bitmap.recycle()
                }
            }
        } finally {
            engine.close()
        }

        val text = OcrText.joinPages(pages)
        if (text.isBlank()) throw OcrNoTextException()

        outputFile.parentFile?.mkdirs()
        outputFile.writeText(text, Charsets.UTF_8)
        onProgress(100)
        return OutputArtifact(
            path = outputFile.absolutePath,
            name = outputFile.name,
            bytes = outputFile.length(),
        )
    }

    /** Сколько страниц можно распознать (для подписи в интерфейсе). */
    fun pageCount(inputFile: File): Int? = try {
        ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { it.pageCount }
        }
    } catch (t: Throwable) {
        null
    }

    private suspend fun recognizePdf(
        engine: OcrEngine,
        inputFile: File,
        selectedPages: List<Int>?,
        dpi: Int,
        onProgress: (Int) -> Unit,
    ): List<OcrPage> {
        val descriptor = try {
            ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (t: Throwable) {
            throw CannotOpenFileException(t)
        }
        val renderer = try {
            PdfRenderer(descriptor)
        } catch (t: Throwable) {
            descriptor.close()
            throw CannotOpenFileException(t)
        }

        try {
            val pageCount = renderer.pageCount
            if (pageCount == 0) throw EmptyPdfException()

            val numbers = selectedPages
                ?.filter { it in 1..pageCount }
                ?.distinct()
                ?.sorted()
                ?.takeIf { it.isNotEmpty() }
                ?: (1..pageCount).toList()

            val result = ArrayList<OcrPage>(numbers.size)
            numbers.forEachIndexed { index, number ->
                coroutineContext.ensureActive()
                onProgress((index * 100) / numbers.size)
                val bitmap = renderPage(renderer, number - 1, dpi)
                try {
                    val prepared = OcrPreprocess.prepare(bitmap)
                    try {
                        // Страница PDF — ровный документ: сегментация «один блок» точнее.
                        result += OcrPage(number, engine.recognize(prepared, singleBlock = true))
                    } finally {
                        prepared.recycle()
                    }
                } finally {
                    bitmap.recycle()
                }
            }
            onProgress(100)
            return result
        } finally {
            renderer.close()
            descriptor.close()
        }
    }

    private fun renderPage(renderer: PdfRenderer, index: Int, dpi: Int): Bitmap {
        val page = renderer.openPage(index)
        return try {
            val width = (page.width * dpi / 72f).toInt().coerceAtLeast(1)
            val height = (page.height * dpi / 72f).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).apply { drawColor(Color.WHITE) }
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        } finally {
            page.close()
        }
    }
}
