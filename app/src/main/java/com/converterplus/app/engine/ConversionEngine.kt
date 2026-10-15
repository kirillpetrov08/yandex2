package com.converterplus.app.engine

import android.content.Context
import com.converterplus.app.R
import com.converterplus.app.engine.image.ImageConverter
import com.converterplus.app.engine.pdf.ImagesToPdf
import com.converterplus.app.engine.pdf.PdfToImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Фасад конвертации. Единственная точка, из которой воркер и тесты запускают преобразование.
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
        val watermarkText = if (request.watermark) context.getString(R.string.watermark_text) else null

        if (!Capabilities.isToolAvailable(request.tool)) {
            throw UnsupportedToolException(request.tool)
        }
        if (request.inputs.isEmpty()) throw NoInputException()

        val artifacts = when (request.tool) {
            ToolKind.IMAGES -> convertImages(request, outputDir, watermarkText, onProgress)
            ToolKind.PDF_TO_IMAGES -> convertPdf(request, outputDir, watermarkText, onProgress)
            ToolKind.IMAGES_TO_PDF -> convertToPdf(request, outputDir, watermarkText, onProgress)
            ToolKind.OCR, ToolKind.AUDIO, ToolKind.VIDEO -> throw UnsupportedToolException(request.tool)
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

    private suspend fun convertToPdf(
        request: ConversionRequest,
        outputDir: File,
        watermarkText: String?,
        onProgress: (Int) -> Unit,
    ): List<OutputArtifact> {
        val first = request.inputs.first()
        val name = FileNameFactory.outputName(first.displayName, Formats.PDF, emptySet())
        val target = File(outputDir, name)
        val artifact = ImagesToPdf.convert(
            inputs = request.inputs.map { File(it.localPath) },
            outputFile = target,
            pageSize = request.pdfPageSize,
            watermarkText = watermarkText,
            onProgress = onProgress,
        )
        return listOf(artifact)
    }
}
