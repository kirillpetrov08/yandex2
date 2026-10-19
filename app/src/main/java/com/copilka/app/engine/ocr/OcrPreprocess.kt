package com.copilka.app.engine.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

/**
 * Подготовка картинки к распознаванию.
 *
 * Tesseract лучше всего читает тёмный текст на светлом фоне при достаточном контрасте.
 * Поэтому перед распознаванием:
 *  1) приводим картинку к разумному размеру (мелкий текст — увеличиваем, гигантский — уменьшаем);
 *  2) определяем, тёмная ли она в среднем, и при необходимости **инвертируем** —
 *     именно из-за этого чёрный постер с белым текстом распознавался символами вроде `| <> =`;
 *  3) переводим в оттенки серого и поднимаем контраст;
 *  4) добавляем белую рамку — Tesseract понимает текст у самого края хуже.
 *
 * Вся работа — над копией: исходный битмап не меняется и не освобождается.
 */
object OcrPreprocess {

    /** Длинная сторона, к которой приводим картинку. */
    private const val MAX_SIDE = 3000

    /** Мелкие картинки увеличиваем: иначе мелкий шрифт теряется при бинаризации. */
    private const val MIN_GOOD_SIDE = 1000
    private const val UPSCALED_SIDE = 2000

    /** Белая рамка вокруг текста. */
    private const val BORDER = 16

    /** Ниже этой средней яркости считаем фон тёмным и инвертируем. */
    private const val DARK_THRESHOLD = 0.45f

    /** Насколько поднять контраст после приведения к серому. */
    private const val CONTRAST = 1.35f

    fun prepare(source: Bitmap): Bitmap {
        val scaled = rescale(source)
        val inverted = isDark(scaled)
        val contrasted = applyGrayscaleAndContrast(scaled, inverted)
        if (scaled !== source) scaled.recycle()
        return withBorder(contrasted)
    }

    /**
     * Средняя яркость (0..1). Считается по крошечной копии — полноразмерный проход
     * по пикселям на телефоне слишком дорог и не нужен для решения «тёмное/светлое».
     */
    fun meanLuminance(bitmap: Bitmap): Float {
        val sample = if (bitmap.width > 64 || bitmap.height > 64) {
            Bitmap.createScaledBitmap(bitmap, 64, 64, false)
        } else {
            bitmap
        }
        var sum = 0.0
        var count = 0
        for (y in 0 until sample.height) {
            for (x in 0 until sample.width) {
                val pixel = sample.getPixel(x, y)
                val luminance = (0.299f * Color.red(pixel) +
                    0.587f * Color.green(pixel) +
                    0.114f * Color.blue(pixel)) / 255f
                sum += luminance
                count++
            }
        }
        if (sample !== bitmap) sample.recycle()
        return if (count == 0) 1f else (sum / count).toFloat()
    }

    /** true — картинка тёмная, текст на ней светлый: перед распознаванием инвертируем. */
    fun isDark(bitmap: Bitmap): Boolean = meanLuminance(bitmap) < DARK_THRESHOLD

    private fun rescale(source: Bitmap): Bitmap {
        val longSide = maxOf(source.width, source.height)
        if (longSide <= 0) return source
        val scale = when {
            longSide > MAX_SIDE -> MAX_SIDE.toFloat() / longSide
            longSide < MIN_GOOD_SIDE -> UPSCALED_SIDE.toFloat() / longSide
            else -> return source
        }
        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    private fun applyGrayscaleAndContrast(source: Bitmap, invert: Boolean): Bitmap {
        val scale = if (invert) -CONTRAST else CONTRAST
        // Формула: out = scale * in + offset. Для инверсии вокруг середины
        // получается -1.35 * in + 299.45, то есть белый фон и тёмный текст.
        val offset = if (invert) 127f * CONTRAST + 128f else 128f * (1f - CONTRAST)

        val grayscale = ColorMatrix().apply { setSaturation(0f) }
        val contrast = ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, offset,
                0f, scale, 0f, 0f, offset,
                0f, 0f, scale, 0f, offset,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        grayscale.postConcat(contrast)

        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(
            source,
            0f,
            0f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(grayscale) },
        )
        return result
    }

    private fun withBorder(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(
            source.width + BORDER * 2,
            source.height + BORDER * 2,
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(source, BORDER.toFloat(), BORDER.toFloat(), null)
        source.recycle()
        return result
    }
}
