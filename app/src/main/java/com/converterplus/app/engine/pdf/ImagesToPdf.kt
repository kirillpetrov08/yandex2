package com.converterplus.app.engine.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import com.converterplus.app.engine.CannotOpenFileException
import com.converterplus.app.engine.NoInputException
import com.converterplus.app.engine.OutputArtifact
import com.converterplus.app.engine.PdfPageSize
import com.converterplus.app.engine.Watermark
import com.converterplus.app.engine.image.ImageConverter
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext

/**
 * Картинки → один PDF. Только платформенный [PdfDocument] (API 19+).
 * Одна страница = одно изображение: либо A4 с полями, либо по размеру фото.
 */
object ImagesToPdf {

    /** A4 в пунктах (72 pt = 1 дюйм). */
    private const val A4_WIDTH = 595
    private const val A4_HEIGHT = 842
    private const val A4_MARGIN = 24f

    /** Ограничение размера страницы «по размеру фото», чтобы PDF не получился гигантским. */
    private const val MAX_FIT_SIDE = 1600f

    suspend fun convert(
        inputs: List<File>,
        outputFile: File,
        pageSize: PdfPageSize,
        watermarkText: String?,
        onProgress: (Int) -> Unit,
    ): OutputArtifact {
        if (inputs.isEmpty()) throw NoInputException()

        val document = PdfDocument()
        var pageNumber = 0

        try {
            for (input in inputs) {
                coroutineContext.ensureActive()
                if (!input.exists()) throw CannotOpenFileException()

                val bitmap = ImageConverter.decode(input)
                try {
                    val (width, height) = pageDimensions(bitmap, pageSize)
                    val info = PdfDocument.PageInfo.Builder(width, height, pageNumber + 1).create()
                    val page = document.startPage(info)
                    try {
                        drawScaled(page.canvas, bitmap, width, height, pageSize)
                        if (watermarkText != null) {
                            Watermark.applyToPdfCanvas(page.canvas, width, height, watermarkText)
                        }
                    } finally {
                        document.finishPage(page)
                    }
                    pageNumber++
                    onProgress((pageNumber * 90) / inputs.size)
                } finally {
                    bitmap.recycle()
                }
            }

            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { stream -> document.writeTo(stream) }
            onProgress(100)
        } finally {
            document.close()
        }

        if (!outputFile.exists() || outputFile.length() == 0L) throw CannotOpenFileException()
        return OutputArtifact(
            path = outputFile.absolutePath,
            name = outputFile.name,
            bytes = outputFile.length(),
        )
    }

    private fun pageDimensions(bitmap: Bitmap, pageSize: PdfPageSize): Pair<Int, Int> {
        if (pageSize == PdfPageSize.A4) return A4_WIDTH to A4_HEIGHT
        val longest = maxOf(bitmap.width, bitmap.height).toFloat()
        val scale = if (longest > MAX_FIT_SIDE) MAX_FIT_SIDE / longest else 1f
        return (bitmap.width * scale).toInt().coerceAtLeast(1) to
            (bitmap.height * scale).toInt().coerceAtLeast(1)
    }

    private fun drawScaled(canvas: Canvas, bitmap: Bitmap, pageWidth: Int, pageHeight: Int, pageSize: PdfPageSize) {
        if (pageSize == PdfPageSize.FIT_IMAGE) {
            canvas.drawBitmap(bitmap, null, Rect(0, 0, pageWidth, pageHeight), Paint(Paint.FILTER_BITMAP_FLAG))
            return
        }

        val availableWidth = pageWidth - A4_MARGIN * 2
        val availableHeight = pageHeight - A4_MARGIN * 2
        val scale = minOf(availableWidth / bitmap.width, availableHeight / bitmap.height)
        val targetWidth = bitmap.width * scale
        val targetHeight = bitmap.height * scale
        val left = (pageWidth - targetWidth) / 2f
        val top = (pageHeight - targetHeight) / 2f

        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(
            bitmap,
            null,
            Rect(
                left.toInt(),
                top.toInt(),
                (left + targetWidth).toInt(),
                (top + targetHeight).toInt(),
            ),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
    }
}
