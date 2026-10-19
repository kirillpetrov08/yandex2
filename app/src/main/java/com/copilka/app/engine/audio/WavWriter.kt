package com.copilka.app.engine.audio

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile

/**
 * WAV (RIFF/WAVE), PCM без сжатия.
 *
 * Заголовок собирается чистой функцией [header] — она покрыта тестами и не зависит от Android.
 * Размер поля `data` заранее неизвестен, поэтому файл пишется в два прохода:
 * сначала нулевой заголовок, в конце — реальные размеры ([WavFileWriter.close]).
 */
object WavWriter {

    const val BITS_PER_SAMPLE = 16
    const val FORMAT_PCM = 1
    const val HEADER_BYTES = 44

    /**
     * Заголовок PCM-файла: RIFF + fmt + data.
     * @param dataBytes размер блока данных в байтах (без заголовка).
     */
    fun header(dataBytes: Long, sampleRate: Int, channels: Int): ByteArray {
        val bytesPerSample = BITS_PER_SAMPLE / 8
        val byteRate = sampleRate * channels * bytesPerSample
        val blockAlign = channels * bytesPerSample
        val out = ByteArray(HEADER_BYTES)
        var at = 0

        fun ascii(text: String) {
            for (ch in text) out[at++] = ch.code.toByte()
        }

        fun le32(value: Long) {
            out[at++] = (value and 0xFF).toByte()
            out[at++] = ((value shr 8) and 0xFF).toByte()
            out[at++] = ((value shr 16) and 0xFF).toByte()
            out[at++] = ((value shr 24) and 0xFF).toByte()
        }

        fun le16(value: Int) {
            out[at++] = (value and 0xFF).toByte()
            out[at++] = ((value shr 8) and 0xFF).toByte()
        }

        ascii("RIFF")
        le32(36 + dataBytes)
        ascii("WAVE")
        ascii("fmt ")
        le32(16)
        le16(FORMAT_PCM)
        le16(channels)
        le32(sampleRate.toLong())
        le32(byteRate.toLong())
        le16(blockAlign)
        le16(BITS_PER_SAMPLE)
        ascii("data")
        le32(dataBytes)
        return out
    }
}

/** Потоковая запись WAV: данные идут сразу на диск, в память файл не собирается. */
class WavFileWriter(
    private val file: File,
    private val sampleRate: Int,
    private val channels: Int,
) : Closeable {

    private val out = RandomAccessFile(file, "rw")
    private var dataBytes = 0L
    private var closed = false

    init {
        out.setLength(0)
        out.write(WavWriter.header(dataBytes = 0L, sampleRate = sampleRate, channels = channels))
    }

    /** PCM 16 бит little-endian, как его отдаёт MediaCodec. */
    fun write(buffer: ByteArray, offset: Int, length: Int) {
        if (length <= 0) return
        out.write(buffer, offset, length)
        dataBytes += length
    }

    fun writtenBytes(): Long = dataBytes

    override fun close() {
        if (closed) return
        closed = true
        if (dataBytes > 0) {
            out.seek(0)
            out.write(WavWriter.header(dataBytes, sampleRate, channels))
        }
        out.close()
    }
}
