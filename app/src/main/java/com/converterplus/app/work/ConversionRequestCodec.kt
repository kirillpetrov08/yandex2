package com.converterplus.app.work

import com.converterplus.app.engine.ConversionRequest
import com.converterplus.app.engine.PdfPageSize
import com.converterplus.app.engine.PickedFile
import com.converterplus.app.engine.QualityPreset
import com.converterplus.app.engine.ToolKind

/**
 * Упаковка запроса в одну строку для передачи в WorkManager (Data).
 *
 * Сериализационные библиотеки не используются намеренно: формат простой, а функции чистые
 * и покрыты юнит-тестами. Разделители — управляющие символы, которых не бывает в именах файлов.
 */
object ConversionRequestCodec {

    private const val FIELD = '\u001F'
    private const val RECORD = '\u001E'
    private const val VERSION = "1"

    fun encode(request: ConversionRequest): String {
        val header = listOf(
            VERSION,
            request.id,
            request.tool.id,
            request.outputFormat,
            request.quality.name,
            if (request.watermark) "1" else "0",
            request.pdfDpi.toString(),
            request.pdfPageSize.name,
            request.selectedPages?.joinToString(",") { it.toString() }.orEmpty(),
        ).joinToString(FIELD.toString())

        val inputs = request.inputs.joinToString(RECORD.toString()) { file ->
            listOf(
                file.localPath,
                file.displayName,
                file.sizeBytes.toString(),
                file.mimeType.orEmpty(),
            ).joinToString(FIELD.toString())
        }

        return header + RECORD + inputs
    }

    fun decode(raw: String?): ConversionRequest? {
        if (raw.isNullOrBlank()) return null
        val records = raw.split(RECORD)
        if (records.size < 2) return null

        val header = records[0].split(FIELD)
        if (header.size < 9 || header[0] != VERSION) return null

        val tool = ToolKind.fromId(header[2]) ?: return null
        val quality = runCatching { QualityPreset.valueOf(header[4]) }.getOrNull() ?: return null
        val pageSize = runCatching { PdfPageSize.valueOf(header[7]) }.getOrNull() ?: PdfPageSize.A4
        val pages = header[8]
            .split(',')
            .mapNotNull { it.toIntOrNull() }
            .takeIf { it.isNotEmpty() }

        val inputs = records.drop(1)
            .filter { it.isNotBlank() }
            .mapNotNull { record ->
                val fields = record.split(FIELD)
                if (fields.size < 3) return@mapNotNull null
                PickedFile(
                    localPath = fields[0],
                    displayName = fields[1],
                    sizeBytes = fields[2].toLongOrNull() ?: 0L,
                    mimeType = fields.getOrNull(3)?.takeIf { it.isNotBlank() },
                )
            }
        if (inputs.isEmpty()) return null

        return ConversionRequest(
            id = header[1],
            tool = tool,
            inputs = inputs,
            outputFormat = header[3],
            quality = quality,
            watermark = header[5] != "0",
            selectedPages = pages,
            pdfDpi = header[6].toIntOrNull() ?: ConversionRequest.DPI_LOW,
            pdfPageSize = pageSize,
        )
    }
}
