package com.converterplus.app.engine.audio

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import com.converterplus.app.engine.AudioEncodeException
import com.converterplus.app.engine.CannotOpenFileException
import com.converterplus.app.engine.Formats
import com.converterplus.app.engine.NoAudioTrackException
import com.converterplus.app.engine.OpusUnavailableException
import com.converterplus.app.engine.OutputArtifact
import com.converterplus.app.engine.QualityPreset
import com.converterplus.app.engine.UnsupportedFormatException
import kotlinx.coroutines.ensureActive
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext

/** Сколько ждать буфер кодека, микросекунды. Нужен и объекту-конвертеру, и [EncodeSink]. */
private const val CODEC_TIMEOUT_US = 10_000L

/**
 * Аудио → WAV / M4A / OGG.
 *
 * Только платформенные средства: [MediaExtractor] читает контейнер, [MediaCodec] декодирует
 * в PCM, дальше либо PCM пишется в WAV напрямую, либо кодируется AAC (M4A) / Opus (OGG)
 * и упаковывается [MediaMuxer].
 *
 * OGG требует Android 10+ ([MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG] — API 29) и энкодер Opus
 * на устройстве; если чего-то нет, формат честно выключен в интерфейсе с причиной.
 */
object AudioConverter {

    /** Opus всегда кодируется в 48 кГц — это требование кодека, остальное ресемплируется. */
    const val OPUS_SAMPLE_RATE = 48_000
    private const val OPUS_MIME = "audio/opus"

    private val SUPPORTED_OUTPUTS = setOf(Formats.WAV, Formats.M4A, Formats.OGG)

    /** Есть ли на устройстве энкодер Opus. */
    fun hasOpusEncoder(): Boolean = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
            info.isEncoder && info.supportedTypes.any { it.equals(OPUS_MIME, ignoreCase = true) }
        }
    }.getOrDefault(false)

    /** Доступен ли формат OGG на этом устройстве. */
    fun supportsOgg(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasOpusEncoder()

    /** Длительность аудиодорожки в миллисекундах; null, если файл не читается. */
    fun audioDurationMs(input: File): Long? = runCatching {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(input.absolutePath)
            val index = audioTrackIndex(extractor) ?: return null
            val format = extractor.getTrackFormat(index)
            if (!format.containsKey(MediaFormat.KEY_DURATION)) return null
            format.getLong(MediaFormat.KEY_DURATION) / 1000L
        } finally {
            extractor.release()
        }
    }.getOrNull()

    /** true, если в файле есть читаемая звуковая дорожка. */
    fun hasAudioTrack(input: File): Boolean = runCatching {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(input.absolutePath)
            audioTrackIndex(extractor) != null
        } finally {
            extractor.release()
        }
    }.getOrDefault(false)

    suspend fun convert(
        input: File,
        output: File,
        outputFormat: String,
        quality: QualityPreset,
        onProgress: (Int) -> Unit,
    ): OutputArtifact {
        if (outputFormat !in SUPPORTED_OUTPUTS) throw UnsupportedFormatException(outputFormat)
        if (outputFormat == Formats.OGG && !supportsOgg()) throw OpusUnavailableException()
        if (!input.exists() || input.length() == 0L) throw CannotOpenFileException()

        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var sink: PcmSink? = null
        try {
            extractor.setDataSource(input.absolutePath)
            val trackIndex = audioTrackIndex(extractor) ?: throw NoAudioTrackException()
            extractor.selectTrack(trackIndex)
            val trackFormat = extractor.getTrackFormat(trackIndex)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: throw NoAudioTrackException()
            val sourceRate = trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val sourceChannels = trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            val channels = sourceChannels.coerceAtMost(MAX_CHANNELS)
            val durationUs = if (trackFormat.containsKey(MediaFormat.KEY_DURATION)) {
                trackFormat.getLong(MediaFormat.KEY_DURATION)
            } else {
                0L
            }

            decoder = MediaCodec.createDecoderByType(mime).also {
                it.configure(trackFormat, null, null, 0)
                it.start()
            }

            sink = createSink(output, outputFormat, quality, sourceRate, sourceChannels, channels)
            decodeTo(extractor, decoder, sink, sourceChannels, channels, durationUs, onProgress)
            sink.finish()
        } catch (t: Throwable) {
            sink?.abort()
            throw t
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            extractor.release()
        }

        output.parentFile?.mkdirs()
        return OutputArtifact(path = output.absolutePath, name = output.name, bytes = output.length())
    }

    // ------------------------------------------------------------------ декодирование

    private suspend fun decodeTo(
        extractor: MediaExtractor,
        decoder: MediaCodec,
        sink: PcmSink,
        sourceChannels: Int,
        targetChannels: Int,
        durationUs: Long,
        onProgress: (Int) -> Unit,
    ) {
        val info = MediaCodec.BufferInfo()
        var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            coroutineContext.ensureActive()

            if (!inputDone) {
                val index = decoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
                if (index >= 0) {
                    val buffer = decoder.getInputBuffer(index) ?: throw CannotOpenFileException()
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) {
                        decoder.queueInputBuffer(index, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        decoder.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outIndex = decoder.dequeueOutputBuffer(info, CODEC_TIMEOUT_US)
            when {
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val format = decoder.outputFormat
                    pcmEncoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    } else {
                        AudioFormat.ENCODING_PCM_16BIT
                    }
                }

                outIndex >= 0 -> {
                    val buffer = decoder.getOutputBuffer(outIndex)
                    if (buffer != null && info.size > 0) {
                        val samples = Pcm16.read(buffer, info, pcmEncoding, sourceChannels, targetChannels)
                        if (samples.isNotEmpty()) sink.write(samples, samples.size)
                    }
                    decoder.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    if (durationUs > 0L) {
                        val percent = (info.presentationTimeUs * 100L / durationUs).toInt()
                        onProgress(percent.coerceIn(0, 99))
                    }
                }
            }
        }
        onProgress(100)
    }

    // ------------------------------------------------------------------ приёмники PCM

    private fun createSink(
        output: File,
        outputFormat: String,
        quality: QualityPreset,
        sourceRate: Int,
        sourceChannels: Int,
        channels: Int,
    ): PcmSink = when (outputFormat) {
        Formats.WAV -> WavSink(output, sourceRate, channels)

        Formats.OGG -> {
            val target = EncodeSink(
                file = output,
                mime = OPUS_MIME,
                sampleRate = OPUS_SAMPLE_RATE,
                channels = channels,
                bitRate = opusBitRate(quality),
                muxerFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG,
            )
            ResamplingSink(target, PcmResampler(sourceRate, OPUS_SAMPLE_RATE, channels))
        }

        else -> EncodeSink(
            file = output,
            mime = MediaFormat.MIMETYPE_AUDIO_AAC,
            sampleRate = sourceRate,
            channels = channels,
            bitRate = aacBitRate(quality),
            muxerFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
        )
    }

    private fun aacBitRate(quality: QualityPreset): Int = when (quality) {
        QualityPreset.MAX -> 192_000
        QualityPreset.BALANCED -> 128_000
        QualityPreset.MIN_SIZE -> 96_000
    }

    private fun opusBitRate(quality: QualityPreset): Int = when (quality) {
        QualityPreset.MAX -> 128_000
        QualityPreset.BALANCED -> 96_000
        QualityPreset.MIN_SIZE -> 64_000
    }

    private fun audioTrackIndex(extractor: MediaExtractor): Int? =
        (0 until extractor.trackCount).firstOrNull { index ->
            extractor.getTrackFormat(index)
                .getString(MediaFormat.KEY_MIME)
                ?.startsWith("audio/") == true
        }

    private const val MAX_CHANNELS = 2
}

/** Получатель PCM 16 бит. */
private interface PcmSink {
    fun write(samples: ShortArray, count: Int)

    /** Завершить файл: дописать хвост, закрыть контейнер. */
    fun finish()

    /** Освободить ресурсы без завершения файла (ошибка или отмена). */
    fun abort()
}

/** PCM → WAV без перекодирования. */
private class WavSink(file: File, sampleRate: Int, channels: Int) : PcmSink {

    private val writer = WavFileWriter(file, sampleRate, channels)
    private val bytes = ByteArray(16 * 1024)

    override fun write(samples: ShortArray, count: Int) {
        var offset = 0
        while (offset < count) {
            val chunk = minOf(count - offset, bytes.size / 2)
            var at = 0
            for (i in 0 until chunk) {
                val value = samples[offset + i].toInt()
                bytes[at++] = (value and 0xFF).toByte()
                bytes[at++] = ((value shr 8) and 0xFF).toByte()
            }
            writer.write(bytes, 0, at)
            offset += chunk
        }
    }

    override fun finish() {
        writer.close()
    }

    override fun abort() {
        writer.close()
    }
}

/** Пересчёт частоты дискретизации перед кодированием (нужен только для Opus). */
private class ResamplingSink(
    private val target: PcmSink,
    private val resampler: PcmResampler,
) : PcmSink {

    override fun write(samples: ShortArray, count: Int) {
        val resampled = resampler.process(samples, count)
        if (resampled.isNotEmpty()) target.write(resampled, resampled.size)
    }

    override fun finish() {
        val tail = resampler.flush()
        if (tail.isNotEmpty()) target.write(tail, tail.size)
        target.finish()
    }

    override fun abort() {
        target.abort()
    }
}

/** PCM → сжатый поток (AAC или Opus) → контейнер (MP4/M4A или OGG). */
private class EncodeSink(
    file: File,
    mime: String,
    private val sampleRate: Int,
    private val channels: Int,
    bitRate: Int,
    muxerFormat: Int,
) : PcmSink {

    private val encoder: MediaCodec = MediaCodec.createEncoderByType(mime)
    private val muxer: MediaMuxer = MediaMuxer(file.absolutePath, muxerFormat)
    private val info = MediaCodec.BufferInfo()

    private var trackIndex = -1
    private var muxerStarted = false
    private var inputDone = false
    private var outputDone = false
    private var framesWritten = 0L

    init {
        val format = MediaFormat.createAudioFormat(mime, sampleRate, channels).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            if (mime == MediaFormat.MIMETYPE_AUDIO_AAC) {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            }
        }
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()
    }

    override fun write(samples: ShortArray, count: Int) {
        var offset = 0
        while (offset < count) {
            val index = encoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
            if (index < 0) {
                drain(blocking = false)
                continue
            }
            val buffer = encoder.getInputBuffer(index) ?: throw AudioEncodeException()
            buffer.clear()
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            val capacity = buffer.capacity() / 2
            val chunk = minOf(count - offset, capacity)
            for (i in 0 until chunk) buffer.putShort(samples[offset + i])

            val presentationTimeUs = if (sampleRate > 0) framesWritten * 1_000_000L / sampleRate else 0L
            encoder.queueInputBuffer(index, 0, chunk * 2, presentationTimeUs, 0)
            framesWritten += if (channels > 0) chunk / channels else chunk
            offset += chunk
            drain(blocking = false)
        }
    }

    override fun finish() {
        while (!inputDone) {
            val index = encoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
            if (index >= 0) {
                val pts = if (sampleRate > 0) framesWritten * 1_000_000L / sampleRate else 0L
                encoder.queueInputBuffer(index, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                inputDone = true
            }
        }
        var guard = 0
        while (!outputDone && guard++ < MAX_DRAIN_ATTEMPTS) drain(blocking = true)
        release()
    }

    override fun abort() {
        release()
    }

    private fun drain(blocking: Boolean) {
        while (!outputDone) {
            val index = encoder.dequeueOutputBuffer(info, if (blocking) CODEC_TIMEOUT_US else 0L)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> return

                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!muxerStarted) {
                        trackIndex = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                }

                index >= 0 -> {
                    val buffer = encoder.getOutputBuffer(index)
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (buffer != null && info.size > 0 && muxerStarted) {
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        muxer.writeSampleData(trackIndex, buffer, info)
                    }
                    val endOfStream = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    encoder.releaseOutputBuffer(index, false)
                    if (endOfStream) outputDone = true
                }
            }
        }
    }

    private fun release() {
        runCatching { encoder.stop() }
        runCatching { encoder.release() }
        runCatching {
            if (muxerStarted && trackIndex >= 0) muxer.stop()
        }
        runCatching { muxer.release() }
    }

    private companion object {
        const val MAX_DRAIN_ATTEMPTS = 20_000
    }
}

/** PCM из буфера декодера → interleaved 16 бит с нужным числом каналов. */
object Pcm16 {

    /**
     * @param encoding [AudioFormat.ENCODING_PCM_16BIT] или [AudioFormat.ENCODING_PCM_FLOAT].
     * @param sourceChannels каналов в потоке декодера.
     * @param targetChannels сколько каналов оставить (1 или 2).
     */
    fun read(
        buffer: ByteBuffer,
        info: MediaCodec.BufferInfo,
        encoding: Int,
        sourceChannels: Int,
        targetChannels: Int,
    ): ShortArray {
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(info.offset)
        buffer.limit(info.offset + info.size)

        return if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
            val floats = FloatArray(info.size / 4)
            buffer.asFloatBuffer().get(floats)
            val shorts = ShortArray(floats.size)
            for (i in floats.indices) {
                shorts[i] = (floats[i] * 32767f).toInt().coerceIn(-32768, 32767).toShort()
            }
            remapChannels(shorts, sourceChannels, targetChannels)
        } else {
            val shorts = ShortArray(info.size / 2)
            buffer.asShortBuffer().get(shorts)
            remapChannels(shorts, sourceChannels, targetChannels)
        }
    }

    /** Оставить первые [target] каналов (для 5.1 это фронтальные левый/правый). */
    fun remapChannels(samples: ShortArray, source: Int, target: Int): ShortArray {
        if (source <= target || source <= 0 || target <= 0) return samples
        val frames = samples.size / source
        val result = ShortArray(frames * target)
        for (frame in 0 until frames) {
            for (ch in 0 until target) {
                result[frame * target + ch] = samples[frame * source + ch]
            }
        }
        return result
    }
}
