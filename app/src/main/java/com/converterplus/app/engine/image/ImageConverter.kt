package com.converterplus.app.engine.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import com.converterplus.app.engine.CannotOpenFileException
import com.converterplus.app.engine.Formats
import com.converterplus.app.engine.OutOfSpaceException
import com.converterplus.app.engine.OutputArtifact
import com.converterplus.app.engine.QualityPreset
import com.converterplus.app.engine.UnsupportedFormatException
import com.converterplus.app.engine.Watermark
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Конвертация изображений: JPG ↔ PNG ↔ WEBP, а также HEIC (Android 9+) и AVIF (Android 12+) на вход.
 * Выход в HEIC/AVIF невозможен — Android не умеет их кодировать, поэтому таких форматов нет в интерфейсе.
 */
object ImageConverter {

    /**
     * @param watermarkText текст водяного знака; null — без водяного знака.
     * @param targetWidth ширина результата в пикселях; null — не менять размер.
     * @param targetHeight высота результата в пикселях; null — не менять размер.
     * @param fitMode как вписывать в рамку: целиком или с обрезкой краёв.
     */
    fun convert(
        input: File,
        output: File,
        outputFormat: String,
        quality: QualityPreset,
        watermarkText: String?,
        targetWidth: Int? = null,
        targetHeight: Int? = null,
        fitMode: FitMode = FitMode.FIT,
    ): OutputArtifact {
        var current = decode(input)
        try {
            // Порядок важен: сначала размер, потом водяной знак — иначе подпись
            // масштабировалась бы вместе с фото и оказалась нечитаемой.
            val target = ImageSizing.normalize(targetWidth, targetHeight)
            if (target != null) {
                val result = ImageSizing.resolve(
                    source = ImageSize(current.width, current.height),
                    target = target,
                    mode = fitMode,
                )
                val resized = scale(current, result, fitMode)
                if (resized !== current) current.recycle()
                current = resized
            }
            if (watermarkText != null) {
                val marked = Watermark.applyTo(current, watermarkText)
                if (marked !== current) current.recycle()
                current = marked
            }

            output.parentFile?.mkdirs()
            FileOutputStream(output).use { stream ->
                val (format, level) = compressFormat(outputFormat, quality)
                val ok = current.compress(format, level, stream)
                if (!ok) throw UnsupportedFormatException(outputFormat)
                stream.flush()
            }
        } catch (e: IOException) {
            if (output.length() == 0L) output.delete()
            throw e
        } finally {
            current.recycle()
        }
        if (!output.exists() || output.length() == 0L) throw OutOfSpaceException()
        return OutputArtifact(
            path = output.absolutePath,
            name = output.name,
            bytes = output.length(),
        )
    }

    /**
     * Приводит картинку к нужному размеру.
     *
     * Уменьшение идёт ступенями (вдвое), а не одним прыжком: так пиксели не «слипаются»
     * и края остаются резкими. Для обрезки рисуем центр исходника в кадр целиком.
     */
    private fun scale(source: Bitmap, result: ImageSize, fitMode: FitMode): Bitmap {
        if (result.width == source.width && result.height == source.height) return source

        // Для вписывания заранее уменьшаем по целым кратностям — это резче и экономнее по памяти.
        var step = source
        if (fitMode == FitMode.FIT) {
            while (step.width / 2 >= result.width && step.height / 2 >= result.height) {
                val half = Bitmap.createScaledBitmap(step, step.width / 2, step.height / 2, true)
                if (step !== source) step.recycle()
                step = half
            }
        }

        val target = Bitmap.createBitmap(result.width, result.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(target)
        if (fitMode == FitMode.CROP) {
            val scaleFactor = maxOf(
                result.width.toFloat() / step.width,
                result.height.toFloat() / step.height,
            )
            val drawnWidth = step.width * scaleFactor
            val drawnHeight = step.height * scaleFactor
            val left = (result.width - drawnWidth) / 2f
            val top = (result.height - drawnHeight) / 2f
            canvas.drawBitmap(
                step,
                null,
                RectF(left, top, left + drawnWidth, top + drawnHeight),
                Paint(Paint.FILTER_BITMAP_FLAG),
            )
        } else {
            canvas.drawBitmap(
                step,
                null,
                RectF(0f, 0f, result.width.toFloat(), result.height.toFloat()),
                Paint(Paint.FILTER_BITMAP_FLAG),
            )
        }
        if (step !== source) step.recycle()
        return target
    }

    /** Декодирует файл любого поддерживаемого формата с учётом EXIF-ориентации. */
    fun decode(file: File): Bitmap = decodeScaled(file, maxSide = 0)

    /**
     * Декодирует файл с ограничением длинной стороны кадра.
     *
     * Нужно там, где полноразмерный битмап не требуется: фото 12 Мп в ARGB_8888 занимает ~48 МБ,
     * а для страницы PDF это лишние десятки мегабайт и риск OOM. `maxSide <= 0` — без ограничения.
     */
    fun decodeScaled(file: File, maxSide: Int): Bitmap {
        if (!file.exists() || file.length() == 0L) throw CannotOpenFileException()

        val extension = file.name.substringAfterLast('.', "").lowercase()
        val needsImageDecoder = (extension == "heic" || extension == "heif" || extension == "avif") &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

        val decoded = if (needsImageDecoder) {
            decodeWithImageDecoder(file, maxSide)
        } else {
            decodeSampled(file, maxSide)
        } ?: throw CannotOpenFileException()

        return applyExifOrientation(file, decoded)
    }

    /** Уменьшенное превью для интерфейса: не грузим полноразмерный битмап. */
    fun decodePreview(file: File, maxSide: Int): Bitmap? {
        if (!file.exists()) return null
        return try {
            decodeScaled(file, maxSide)
        } catch (e: Exception) {
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeWithImageDecoder(file: File, maxSide: Int = 0): Bitmap? = try {
        val source = ImageDecoder.createSource(file)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            // Без premultiplied alpha цвета полупрозрачных пикселей не «съедаются» при перекодировании.
            decoder.setUnpremultipliedRequired(true)
            // По умолчанию декодер может вернуть HARDWARE-битмап: его нельзя ни рисовать
            // на обычном canvas, ни копировать в ARGB_8888. Просим софтверную память явно.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            if (maxSide > 0) {
                val sample = sampleSize(info.size.width, info.size.height, maxSide)
                if (sample > 1) {
                    decoder.setTargetSampleSize(sample)
                }
            }
        }
    } catch (e: IOException) {
        null
    }

    private fun decodeSampled(file: File, maxSide: Int = 0): Bitmap? {
        val options = BitmapFactory.Options().apply {
            // Всегда ARGB_8888: декодер сам решает иначе (RGB_565 для JPEG), а такой битмап
            // нельзя использовать как основу для canvas — рисование на нём падает.
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        if (maxSide > 0) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide)
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
        if (maxSide <= 0 || (width <= maxSide && height <= maxSide)) return 1
        var sample = 1
        while (width / (sample * 2) >= maxSide && height / (sample * 2) >= maxSide) {
            sample *= 2
        }
        return sample.coerceAtLeast(1)
    }

    private fun applyExifOrientation(file: File, bitmap: Bitmap): Bitmap {
        val orientation = try {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return ensureOpaque(bitmap)
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return ensureOpaque(rotated)
    }

    /** JPG не поддерживает прозрачность: подкладываем белый фон. */
    private fun ensureOpaque(bitmap: Bitmap): Bitmap {
        if (!bitmap.hasAlpha()) return bitmap
        val opaque = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(opaque)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        bitmap.recycle()
        return opaque
    }

    private fun compressFormat(format: String, quality: QualityPreset): Pair<Bitmap.CompressFormat, Int> {
        return when (format.lowercase()) {
            Formats.JPG -> Bitmap.CompressFormat.JPEG to qualityForJpeg(quality)

            Formats.PNG -> Bitmap.CompressFormat.PNG to 100

            Formats.WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (quality == QualityPreset.MAX) {
                    Bitmap.CompressFormat.WEBP_LOSSLESS to 100
                } else {
                    Bitmap.CompressFormat.WEBP_LOSSY to qualityForJpeg(quality)
                }
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP to qualityForJpeg(quality)
            }

            else -> throw UnsupportedFormatException(format)
        }
    }

    private fun qualityForJpeg(quality: QualityPreset): Int = when (quality) {
        QualityPreset.MAX -> 95
        QualityPreset.BALANCED -> 85
        QualityPreset.MIN_SIZE -> 60
    }
}
