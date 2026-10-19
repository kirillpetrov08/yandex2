package com.copilka.app.engine.media

import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import com.copilka.app.engine.QualityPreset
import java.io.File
import kotlin.math.max

/**
 * Метаданные медиафайла для превью и подписей: длительность, первый кадр, наличие дорожек.
 * Всё через платформенные [MediaMetadataRetriever] и [MediaExtractor].
 */
object MediaProbe {

    /** Длительность в миллисекундах; null, если файл не читается. */
    fun durationMs(file: File): Long? {
        if (!file.exists()) return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (t: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** Первый видеокадр, уменьшенный до [maxSide] по большей стороне. */
    fun firstFrame(file: File, maxSide: Int): Bitmap? {
        if (!file.exists()) return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
            downscale(frame, maxSide)
        } catch (t: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** Размеры видео по первому кадру — нужны, чтобы посчитать битрейт под качество. */
    fun videoSize(file: File): Pair<Int, Int>? {
        val frame = firstFrame(file, MAX_PROBE_SIDE) ?: return null
        val size = frame.width to frame.height
        frame.recycle()
        return size
    }

    fun hasVideoTrack(file: File): Boolean = hasTrack(file, "video/")

    fun hasAudioTrack(file: File): Boolean = hasTrack(file, "audio/")

    /** Читается ли контейнер и есть ли в нём подходящая дорожка. */
    fun hasTrack(file: File, mimePrefix: String): Boolean = runCatching {
        if (!file.exists() || file.length() == 0L) return false
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            (0 until extractor.trackCount).any { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith(mimePrefix) == true
            }
        } finally {
            extractor.release()
        }
    }.getOrDefault(false)

    private fun downscale(bitmap: Bitmap, maxSide: Int): Bitmap {
        if (maxSide <= 0) return bitmap
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= maxSide) return bitmap
        val scale = maxSide.toFloat() / largest
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    private const val MAX_PROBE_SIDE = 1920
}

/**
 * Битрейт видео под выбранное качество. Чистая функция — покрыта тестами.
 * Считается от разрешения: один и тот же «фактор» даёт разумный битрейт и для 480p, и для 4K.
 */
object VideoQuality {

    fun bitrateFor(width: Int, height: Int, quality: QualityPreset, fps: Int = DEFAULT_FPS): Int {
        val pixels = width.coerceAtLeast(1).toLong() * height.coerceAtLeast(1).toLong()
        val bitsPerPixel = when (quality) {
            QualityPreset.MAX -> 0.20
            QualityPreset.BALANCED -> 0.12
            QualityPreset.MIN_SIZE -> 0.07
        }
        val raw = (pixels * fps.coerceAtLeast(1) * bitsPerPixel).toLong()
        return raw.coerceIn(MIN_BITRATE, MAX_BITRATE).toInt()
    }

    private const val DEFAULT_FPS = 30
    private const val MIN_BITRATE = 1_000_000L
    private const val MAX_BITRATE = 20_000_000L
}
