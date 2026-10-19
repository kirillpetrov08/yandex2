package com.copilka.app.engine.audio

import kotlin.math.floor

/**
 * Линейный ресемплер PCM 16 бит, покадрово (frame = все каналы одного момента времени).
 *
 * Нужен для OGG/Opus: Opus принимает только частоты 8/12/16/24/48 кГц, а большинство
 * исходников — 44,1 кГц. Пересчёт идёт с сохранением дробной позиции между вызовами,
 * поэтому склейка чанков не даёт щелчков и «плывущей» длины.
 *
 * Класс чистый: ни Android, ни файлов — покрыт юнит-тестами.
 */
class PcmResampler(
    private val sourceRate: Int,
    private val targetRate: Int,
    private val channels: Int,
) {

    /** Кадры, ещё не отданные в вывод. Последний кадр нужен как правая точка интерполяции. */
    private var pending = ShortArray(0)

    /** Дробная позиция внутри [pending], в кадрах. */
    private var position = 0.0

    val ratio: Double = targetRate.toDouble() / sourceRate.toDouble()

    /** true, если частота совпадает и пересчёт не нужен. */
    val isIdentity: Boolean get() = sourceRate == targetRate

    /**
     * @param samples interleaved PCM 16 бит, [count] значащих элементов.
     * @return пересчитанные сэмплы (может быть пустым массивом).
     */
    fun process(samples: ShortArray, count: Int): ShortArray {
        if (count <= 0) return ShortArray(0)
        if (isIdentity) return samples.copyOf(count)

        val joined = ShortArray(pending.size + count)
        System.arraycopy(pending, 0, joined, 0, pending.size)
        System.arraycopy(samples, 0, joined, pending.size, count)

        val totalFrames = joined.size / channels
        if (totalFrames < 2) {
            pending = joined
            return ShortArray(0)
        }

        // Сколько выходных кадров можно получить, пока есть хотя бы одна пара точек.
        val framesOut = ((totalFrames - 1 - position) * ratio).toInt().coerceAtLeast(0)
        val output = interpolate(joined, totalFrames, framesOut)

        val consumed = position + framesOut / ratio
        val keepFrom = floor(consumed).toInt().coerceIn(0, totalFrames - 1)
        pending = joined.copyOfRange(keepFrom * channels, joined.size)
        position = consumed - keepFrom
        return output
    }

    /**
     * Хвост потока: добираем последние кадры, удерживая последний сэмпл справа.
     * Без этого в конце файла терялись бы доли секунды.
     */
    fun flush(): ShortArray {
        val totalFrames = pending.size / channels
        if (totalFrames < 2) {
            val tail = pending
            pending = ShortArray(0)
            return tail
        }
        val framesOut = ((totalFrames - position) * ratio).toInt().coerceAtLeast(0)
        val output = interpolate(pending, totalFrames, framesOut)
        pending = ShortArray(0)
        position = 0.0
        return output
    }

    private fun interpolate(source: ShortArray, totalFrames: Int, framesOut: Int): ShortArray {
        if (framesOut <= 0) return ShortArray(0)
        val output = ShortArray(framesOut * channels)
        for (frame in 0 until framesOut) {
            val sourcePosition = position + frame / ratio
            val left = floor(sourcePosition).toInt().coerceIn(0, totalFrames - 1)
            val right = (left + 1).coerceAtMost(totalFrames - 1)
            val fraction = (sourcePosition - left).coerceIn(0.0, 1.0)
            for (ch in 0 until channels) {
                val a = source[left * channels + ch].toDouble()
                val b = source[right * channels + ch].toDouble()
                val value = a + (b - a) * fraction
                output[frame * channels + ch] = value.toInt().coerceIn(-32768, 32767).toShort()
            }
        }
        return output
    }
}
