package com.converterplus.app.engine.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import com.converterplus.app.engine.CannotOpenFileException
import com.converterplus.app.engine.NoInputException
import com.converterplus.app.engine.OutOfSpaceException
import com.converterplus.app.engine.OutputArtifact
import com.converterplus.app.engine.PdfPageSize
import com.converterplus.app.engine.Watermark
import com.converterplus.app.engine.image.ImageConverter
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext

/**
 * Картинки → один PDF. Только платформенный [PdfDocument].
 *
 * PdfDocument не растеризует страницу в [PdfDocument.finishPage]: он хранит ссылку на битмап
 * до [PdfDocument.writeTo]. Камерные снимки (DISPLAY_P3, HARDWARE, unpremultiplied) при
 * прямой отрисовке на canvas PDF дают нативный SIGSEGV. Поэтому каждое фото сначала
 * рисуется на небольшой software sRGB ARGB_8888, исходник сразу освобождается, а в PDF
 * уходит только эта страница.
 */
object ImagesToPdf {

    /** A4 в пунктах (72 pt = 1 дюйм). */
    private const val A4_WIDTH = 595
    private const val A4_HEIGHT = 842
    private const val A4_MARGIN = 24f

    /** Потолок стороны страницы «по размеру фото» — и для PDF, и для декода. */
    private const val MAX_PAGE_SIDE = 1200

    private val srgb: ColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    private fun decodeSide(fileCount: Int): Int = when {
        fileCount <= 4 -> MAX_PAGE_SIDE
        fileCount <= 10 -> 1000
        else -> 800
    }

    suspend fun convert(
        inputs: List<File>,
        outputFile: File,
        pageSize: PdfPageSize,
        watermarkText: String?,
        onProgress: (Int) -> Unit,
    ): OutputArtifact {
        if (inputs.isEmpty()) throw NoInputException()

        val document = PdfDocument()
        val pages = mutableListOf<Bitmap>()
        val maxSide = decodeSide(inputs.size)

        try {
            for (input in inputs) {
                coroutineContext.ensureActive()
                if (!input.exists() || input.length() == 0L) throw CannotOpenFileException()

                val pageBitmap = rasterize(input, pageSize, maxSide, watermarkText)
                pages += pageBitmap

                val info = PdfDocument.PageInfo.Builder(
                    pageBitmap.width,
                    pageBitmap.height,
                    pages.size,
                ).create()
                val page = document.startPage(info)
                try {
                    page.canvas.drawColor(Color.WHITE)
                    page.canvas.drawBitmap(pageBitmap, 0f, 0f, bitmapPaint)
                } finally {
                    document.finishPage(page)
                }
                onProgress((pages.size * 90) / inputs.size)
            }

            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { stream -> document.writeTo(stream) }
            onProgress(100)
        } catch (oom: OutOfMemoryError) {
            runCatching { outputFile.delete() }
            throw OutOfSpaceException(oom)
        } catch (t: Throwable) {
            runCatching { outputFile.delete() }
            throw t
        } finally {
            runCatching { document.close() }
            pages.forEach { bitmap -> runCatching { bitmap.recycle() } }
        }

        if (!outputFile.exists() || outputFile.length() == 0L) throw CannotOpenFileException()
        return OutputArtifact(
            path = outputFile.absolutePath,
            name = outputFile.name,
            bytes = outputFile.length(),
        )
    }

    /**
     * Декодирует фото, рисует его на software sRGB-страницу нужного размера и сразу
     * освобождает исходник. В [PdfDocument] эта страница живёт до конца [PdfDocument.writeTo].
     */
    private fun rasterize(
        input: File,
        pageSize: PdfPageSize,
        maxSide: Int,
        watermarkText: String?,
    ): Bitmap {
        val photo = ImageConverter.decodeForPdf(input, maxSide)
        try {
            if (photo.isRecycled || photo.width <= 0 || photo.height <= 0) {
                throw CannotOpenFileException()
            }
            val (width, height) = pageDimensions(photo.width, photo.height, pageSize)
            val page = srgbBitmap(width, height)
            val canvas = Canvas(page)
            canvas.drawColor(Color.WHITE)
            drawPhoto(canvas, photo, width, height, pageSize)
            if (watermarkText != null) {
                Watermark.applyToPdfCanvas(canvas, width, height, watermarkText)
            }
            return page
        } finally {
            runCatching { photo.recycle() }
        }
    }

    private fun srgbBitmap(width: Int, height: Int): Bitmap {
        val w = width.coerceIn(1, MAX_PAGE_SIDE)
        val h = height.coerceIn(1, MAX_PAGE_SIDE)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888, true, srgb)
        bitmap.density = Bitmap.DENSITY_NONE
        return bitmap
    }

    internal fun pageDimensions(bitmapWidth: Int, bitmapHeight: Int, pageSize: PdfPageSize): Pair<Int, Int> {
        if (pageSize == PdfPageSize.A4) return A4_WIDTH to A4_HEIGHT
        val w = bitmapWidth.coerceAtLeast(1)
        val h = bitmapHeight.coerceAtLeast(1)
        val longest = maxOf(w, h).toFloat()
        val scale = if (longest > MAX_PAGE_SIDE) MAX_PAGE_SIDE / longest else 1f
        return (w * scale).toInt().coerceIn(1, MAX_PAGE_SIDE) to
            (h * scale).toInt().coerceIn(1, MAX_PAGE_SIDE)
    }

    private fun drawPhoto(
        canvas: Canvas,
        bitmap: Bitmap,
        pageWidth: Int,
        pageHeight: Int,
        pageSize: PdfPageSize,
    ) {
        if (pageSize == PdfPageSize.FIT_IMAGE) {
            canvas.drawBitmap(bitmap, null, Rect(0, 0, pageWidth, pageHeight), bitmapPaint)
            return
        }
        val availableWidth = pageWidth - A4_MARGIN * 2
        val availableHeight = pageHeight - A4_MARGIN * 2
        val scale = minOf(
            availableWidth / bitmap.width.coerceAtLeast(1),
            availableHeight / bitmap.height.coerceAtLeast(1),
        )
        val targetWidth = bitmap.width * scale
        val targetHeight = bitmap.height * scale
        val left = (pageWidth - targetWidth) / 2f
        val top = (pageHeight - targetHeight) / 2f
        canvas.drawBitmap(
            bitmap,
            null,
            Rect(
                left.toInt().coerceAtLeast(0),
                top.toInt().coerceAtLeast(0),
                (left + targetWidth).toInt().coerceAtMost(pageWidth),
                (top + targetHeight).toInt().coerceAtMost(pageHeight),
            ),
            bitmapPaint,
        )
    }
}
