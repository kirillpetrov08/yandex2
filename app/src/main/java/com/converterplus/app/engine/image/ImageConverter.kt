package com.converterplus.app.engine.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
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
     */
    fun convert(
        input: File,
        output: File,
        outputFormat: String,
        quality: QualityPreset,
        watermarkText: String?,
    ): OutputArtifact {
        val source = decode(input)
        val prepared = if (watermarkText != null) Watermark.applyTo(source, watermarkText) else source
        try {
            output.parentFile?.mkdirs()
            FileOutputStream(output).use { stream ->
                val (format, level) = compressFormat(outputFormat, quality)
                val ok = prepared.compress(format, level, stream)
                if (!ok) throw UnsupportedFormatException(outputFormat)
                stream.flush()
            }
        } catch (e: IOException) {
            if (output.length() == 0L) output.delete()
            throw e
        } finally {
            if (prepared !== source) prepared.recycle()
            source.recycle()
        }
        if (!output.exists() || output.length() == 0L) throw OutOfSpaceException()
        return OutputArtifact(
            path = output.absolutePath,
            name = output.name,
            bytes = output.length(),
        )
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
