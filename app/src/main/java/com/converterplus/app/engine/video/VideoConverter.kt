package com.converterplus.app.engine.video

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.converterplus.app.engine.CannotOpenFileException
import com.converterplus.app.engine.Formats
import com.converterplus.app.engine.NoAudioTrackException
import com.converterplus.app.engine.OutputArtifact
import com.converterplus.app.engine.QualityPreset
import com.converterplus.app.engine.UnsupportedFormatException
import com.converterplus.app.engine.VideoDecodeException
import com.converterplus.app.engine.VideoUnsupportedException
import com.converterplus.app.engine.media.MediaProbe
import com.converterplus.app.engine.media.VideoQuality
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Видео → MP4 (H.264 + AAC) и извлечение звуковой дорожки в M4A.
 *
 * Используется media3 Transformer — он собирает перекодирование из платформенных кодеков
 * и сам разбирает контейнеры MP4/MOV/MKV.
 *
 * Тонкости, из-за которых код выглядит именно так:
 * * Transformer создаётся и запускается на главном потоке (у него свой Looper);
 * * прогресс берётся опросом [Transformer.getProgress] и только когда он доступен;
 * * при отмене корутины вызов [Transformer.cancel] обязателен, иначе кодеки останутся занятыми.
 */
object VideoConverter {

    private const val PROGRESS_POLL_MS = 200L

    /** Поддерживается ли видео на этом устройстве (нужна платформенная поддержка H.264). */
    fun isSupported(input: File): Boolean = MediaProbe.hasVideoTrack(input)

    suspend fun convert(
        context: Context,
        input: File,
        output: File,
        outputFormat: String,
        quality: QualityPreset,
        onProgress: (Int) -> Unit,
    ): OutputArtifact {
        if (outputFormat != Formats.MP4 && outputFormat != Formats.M4A) {
            throw UnsupportedFormatException(outputFormat)
        }
        if (!input.exists() || input.length() == 0L) throw CannotOpenFileException()

        val extractAudio = outputFormat == Formats.M4A
        if (!MediaProbe.hasVideoTrack(input)) throw VideoUnsupportedException()
        if (extractAudio && !MediaProbe.hasAudioTrack(input)) throw NoAudioTrackException()

        output.parentFile?.mkdirs()
        val mediaItem = MediaItem.fromUri(Uri.fromFile(input))

        withContext(Dispatchers.Main.immediate) {
            coroutineScope {
                val finished = CompletableDeferred<ExportResult>()
                val transformer = buildTransformer(context, input, quality, extractAudio, finished)

                val editedItem = EditedMediaItem.Builder(mediaItem)
                    .setRemoveVideo(extractAudio)
                    .build()

                val progressJob = launch {
                    val holder = ProgressHolder()
                    while (isActive && !finished.isCompleted) {
                        delay(PROGRESS_POLL_MS)
                        if (finished.isCompleted) break
                        val state = transformer.getProgress(holder)
                        if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                            onProgress(holder.progress.coerceIn(0, 99))
                        }
                    }
                }

                try {
                    transformer.start(editedItem, output.absolutePath)
                    finished.await()
                } catch (cancelled: CancellationException) {
                    transformer.cancel()
                    throw cancelled
                } catch (failure: Throwable) {
                    transformer.cancel()
                    throw failure
                } finally {
                    progressJob.cancel()
                }
            }
        }

        coroutineContext.ensureActive()
        onProgress(100)
        if (!output.exists() || output.length() == 0L) throw VideoUnsupportedException()
        return OutputArtifact(path = output.absolutePath, name = output.name, bytes = output.length())
    }

    private fun buildTransformer(
        context: Context,
        input: File,
        quality: QualityPreset,
        extractAudio: Boolean,
        finished: CompletableDeferred<ExportResult>,
    ): Transformer {
        val builder = Transformer.Builder(context)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(
                object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        finished.complete(exportResult)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        finished.completeExceptionally(
                            exportException.toConversionFailure(extractAudio),
                        )
                    }
                },
            )

        if (!extractAudio) {
            builder.setVideoMimeType(MimeTypes.VIDEO_H264)
            val size = MediaProbe.videoSize(input)
            if (size != null) {
                val bitrate = VideoQuality.bitrateFor(size.first, size.second, quality)
                val encoderFactory = DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(
                        VideoEncoderSettings.Builder().setBitrate(bitrate).build(),
                    )
                    .build()
                builder.setEncoderFactory(encoderFactory)
            }
        }
        return builder.build()
    }

    /** Ошибки Transformer → понятные человеку сообщения приложения. */
    private fun ExportException.toConversionFailure(extractAudio: Boolean): Throwable = when (errorCode) {
        ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        ExportException.ERROR_CODE_DECODER_INIT_FAILED,
        ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED,
        ExportException.ERROR_CODE_ENCODER_INIT_FAILED,
        -> VideoUnsupportedException(this)

        ExportException.ERROR_CODE_IO_FILE_NOT_FOUND,
        ExportException.ERROR_CODE_IO_NO_PERMISSION,
        -> CannotOpenFileException(this)

        ExportException.ERROR_CODE_AUDIO_PROCESSING_FAILED ->
            if (extractAudio) NoAudioTrackException() else VideoUnsupportedException(this)

        else -> VideoDecodeException(this)
    }
}
