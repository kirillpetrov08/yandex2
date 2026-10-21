package com.converterplus.app.engine.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.converterplus.app.engine.CannotOpenFileException
import com.converterplus.app.engine.EmptyPdfException
import com.converterplus.app.engine.FileNameFactory
import com.converterplus.app.engine.Formats
import com.converterplus.app.engine.NoPagesSelectedException
import com.converterplus.app.engine.OutputArtifact
import com.converterplus.app.engine.QualityPreset
import com.converterplus.app.engine.UnsupportedFormatException
import com.converterplus.app.engine.Watermark
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext

/**
 * Страницы PDF → картинки. Только платформенный [PdfRenderer] (API 21+), без сторонних библиотек.
 * PDF с паролем не поддерживается: [PdfRenderer] не умеет его открывать, об этом говорит интерфейс.
 */
object PdfToImages {

    /**
     * @param selectedPages номера страниц с 1; null или пустой список — все страницы.
     * @param onProgress прогресс 0..100.
     */
    suspend fun convert(
        inputFile: File,
        outputDir: File,
        outputFormat: String,
        dpi: Int,
        quality: QualityPreset,
        selectedPages: List<Int>?,
        watermarkText: String?,
        onProgress: (Int) -> Unit,
    ): List<OutputArtifact> {
        if (outputFormat != Formats.PNG && outputFormat != Formats.JPG) {
            throw UnsupportedFormatException(outputFormat)
        }
        if (!inputFile.exists() || inputFile.length() == 0L) throw CannotOpenFileException()

        val descriptor = try {
            ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (e: Exception) {
            throw CannotOpenFileException(e)
        }

        val renderer = try {
            PdfRenderer(descriptor)
        } catch (e: Exception) {
            descriptor.close()
            throw CannotOpenFileException(e)
        }

        return try {
            val pageCount = renderer.pageCount
            if (pageCount == 0) throw EmptyPdfException()

            val pages = selectedPages
                ?.filter { it in 1..pageCount }
                ?.distinct()
                ?.sorted()
                ?.takeIf { it.isNotEmpty() }
                ?: (1..pageCount).toList()

            if (pages.isEmpty()) throw NoPagesSelectedException()

            outputDir.mkdirs()
            val baseName = FileNameFactory.baseNameWithoutExtension(inputFile.name)
            val names = FileNameFactory.pagedNames(baseName, outputFormat, pages)
            val artifacts = ArrayList<OutputArtifact>(pages.size)

            pages.forEachIndexed { index, pageNumber ->
                coroutineContext.ensureActive()
                onProgress((index * 90) / pages.size)

                val page = renderer.openPage(pageNumber - 1)
                try {
                    val width = (page.width * dpi / 72f).toInt().coerceAtLeast(1)
                    val height = (page.height * dpi / 72f).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    if (watermarkText != null) Watermark.applyTo(bitmap, watermarkText)

                    val target = File(outputDir, names[index])
                    FileOutputStream(target).use { stream ->
                        val format = if (outputFormat == Formats.PNG) {
                            Bitmap.CompressFormat.PNG
                        } else {
                            Bitmap.CompressFormat.JPEG
                        }
                        val level = if (outputFormat == Formats.PNG) 100 else jpegQuality(quality)
                        if (!bitmap.compress(format, level, stream)) {
                            throw UnsupportedFormatException(outputFormat)
                        }
                        stream.flush()
                    }
                    bitmap.recycle()

                    artifacts += OutputArtifact(
                        path = target.absolutePath,
                        name = target.name,
                        bytes = target.length(),
                    )
                } finally {
                    page.close()
                }
                onProgress(((index + 1) * 90) / pages.size)
            }

            onProgress(100)
            artifacts
        } finally {
            renderer.close()
            descriptor.close()
        }
    }

    /** Рендерит одну страницу в битмап — нужно для превью в интерфейсе. */
    fun renderFirstPage(inputFile: File, maxSide: Int): Bitmap? {
        if (!inputFile.exists()) return null
        return try {
            ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    if (renderer.pageCount == 0) return null
                    renderer.openPage(0).use { page ->
                        val scale = if (maxSide > 0) {
                            maxSide.toFloat() / maxOf(page.width, page.height).coerceAtLeast(1)
                        } else {
                            2f
                        }
                        val width = (page.width * scale).toInt().coerceAtLeast(1)
                        val height = (page.height * scale).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Сколько страниц в PDF; null, если файл не читается. */
    fun pageCount(inputFile: File): Int? = try {
        ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { it.pageCount }
        }
    } catch (e: Exception) {
        null
    }

    private fun jpegQuality(quality: QualityPreset): Int = when (quality) {
        QualityPreset.MAX -> 95
        QualityPreset.BALANCED -> 88
        QualityPreset.MIN_SIZE -> 65
    }
}
