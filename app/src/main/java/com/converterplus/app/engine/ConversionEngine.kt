package com.converterplus.app.engine

import android.content.Context
import com.converterplus.app.R
import com.converterplus.app.engine.audio.AudioConverter
import com.converterplus.app.engine.image.ImageConverter
import com.converterplus.app.engine.ocr.OcrConverter
import com.converterplus.app.engine.pdf.PdfToImages
import com.converterplus.app.engine.video.VideoConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Фасад конвертации. Единственная точка, из которой воркер запускает преобразование.
 *
 * Тяжёлые операции выполняются на [Dispatchers.IO], прогресс отдаётся колбэком,
 * отмена — через отмену корутины ([ensureActive] внутри конвертеров).
 */
class ConversionEngine(private val context: Context) {

    suspend fun convert(
        request: ConversionRequest,
        onProgress: (Int) -> Unit,
    ): ConversionResult = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val outputDir = File(context.cacheDir, "results/${request.id}").apply { mkdirs() }
        // Водяной знак имеет смысл только для изображений и PDF: аудио/видео/TXT не портим.
        val watermarkText = if (request.watermark && Capabilities.supportsWatermark(request.tool)) {
            context.getString(R.string.watermark_text)
        } else {
            null
        }

        if (!Capabilities.isToolAvailable(request.tool)) {
            throw UnsupportedToolException(request.tool)
        }
        if (request.inputs.isEmpty()) throw NoInputException()

        val artifacts = when (request.tool) {
            ToolKind.IMAGES -> convertImages(request, outputDir, watermarkText, onProgress)
            ToolKind.PDF_TO_IMAGES -> convertPdf(request, outputDir, watermarkText, onProgress)
            ToolKind.IMAGES_TO_PDF -> throw UnsupportedToolException(request.tool)
            ToolKind.AUDIO -> convertAudio(request, outputDir, onProgress)
            ToolKind.VIDEO -> convertVideo(request, outputDir, onProgress)
            ToolKind.OCR -> convertOcr(request, outputDir, onProgress)
        }

        coroutineContext.ensureActive()
        ConversionResult(
            requestId = request.id,
            tool = request.tool,
            outputFormat = request.outputFormat,
            artifacts = artifacts,
            inputBytes = request.totalInputBytes,
            durationMillis = System.currentTimeMillis() - startedAt,
        )
    }

    private suspend fun convertImages(
        request: ConversionRequest,
        outputDir: File,
        watermarkText: String?,
        onProgress: (Int) -> Unit,
    ): List<OutputArtifact> {
        val artifacts = ArrayList<OutputArtifact>(request.inputs.size)
        request.inputs.forEachIndexed { index, picked ->
            coroutineContext.ensureActive()
            val taken = artifacts.map { it.name }.toSet()
            val name = FileNameFactory.outputName(picked.displayName, request.outputFormat, taken)
            val target = File(outputDir, name)
            artifacts += ImageConverter.convert(
                input = File(picked.localPath),
                output = target,
                outputFormat = request.outputFormat,
                quality = request.quality,
                watermarkText = watermarkText,
                targetWidth = request.targetWidth,
                targetHeight = request.targetHeight,
                fitMode = request.fitMode,
            )
            onProgress(((index + 1) * 100) / request.inputs.size)
        }
        return artifacts
    }

    private suspend fun convertPdf(
        request: ConversionRequest,
        outputDir: File,
        watermarkText: String?,
        onProgress: (Int) -> Unit,
    ): List<OutputArtifact> {
        val picked = request.inputs.first()
        return PdfToImages.convert(
            inputFile = File(picked.localPath),
            outputDir = outputDir,
            outputFormat = request.outputFormat,
            dpi = request.pdfDpi,
            quality = request.quality,
            selectedPages = request.selectedPages,
            watermarkText = watermarkText,
            onProgress = onProgress,
        )
    }

    private suspend fun convertAudio(
        request: ConversionRequest,
        outputDir: File,
        onProgress: (Int) -> Unit,
    ): List<OutputArtifact> {
        val picked = request.inputs.first()
        val name = FileNameFactory.outputName(picked.displayName, request.outputFormat, emptySet())
        val target = File(outputDir, name)
        return listOf(
            AudioConverter.convert(
                input = File(picked.localPath),
                output = target,
                outputFormat = request.outputFormat,
                quality = request.quality,
                onProgress = onProgress,
            ),
        )
    }

    private suspend fun convertVideo(
        request: ConversionRequest,
        outputDir: File,
        onProgress: (Int) -> Unit,
    ): List<OutputArtifact> {
        val picked = request.inputs.first()
        val name = FileNameFactory.outputName(picked.displayName, request.outputFormat, emptySet())
        val target = File(outputDir, name)
        return listOf(
            VideoConverter.convert(
                context = context,
                input = File(picked.localPath),
                output = target,
                outputFormat = request.outputFormat,
                quality = request.quality,
                onProgress = onProgress,
            ),
        )
    }

    private suspend fun convertOcr(
        request: ConversionRequest,
        outputDir: File,
        onProgress: (Int) -> Unit,
    ): List<OutputArtifact> {
        val picked = request.inputs.first()
        val name = FileNameFactory.outputName(picked.displayName, Formats.TXT, emptySet())
        val target = File(outputDir, name)
        return listOf(
            OcrConverter.convert(
                context = context,
                inputFile = File(picked.localPath),
                outputFile = target,
                selectedPages = request.selectedPages,
                renderDpi = request.pdfDpi,
                imageMaxSide = if (request.pdfDpi >= ConversionRequest.DPI_HIGH) {
                    OCR_IMAGE_SIDE_HIGH
                } else {
                    OCR_IMAGE_SIDE
                },
                onProgress = onProgress,
            ),
        )
    }

    private companion object {
        /** Большая сторона картинки, которую отдаём в распознавание. */
        const val OCR_IMAGE_SIDE = 2_000
        const val OCR_IMAGE_SIDE_HIGH = 3_200
    }
}
