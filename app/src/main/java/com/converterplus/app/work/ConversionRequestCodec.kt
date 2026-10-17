package com.converterplus.app.work

import com.converterplus.app.engine.ConversionRequest
import com.converterplus.app.engine.PdfPageSize
import com.converterplus.app.engine.PickedFile
import com.converterplus.app.engine.QualityPreset
import com.converterplus.app.engine.ToolKind
import com.converterplus.app.engine.image.FitMode

/**
 * Упаковка запроса в одну строку для передачи в WorkManager (Data).
 *
 * Сериализационные библиотеки не используются намеренно: формат простой, а функции чистые
 * и покрыты юнит-тестами. Разделители — управляющие символы, которых не бывает в именах файлов.
 */
object ConversionRequestCodec {

    private const val FIELD = '\u001F'
    private const val RECORD = '\u001E'
    /** Версия 2 добавила размер результата; версия 1 читается для работ, поставленных до обновления. */
    private const val VERSION = "2"
    private const val VERSION_LEGACY = "1"

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
            request.targetWidth?.toString().orEmpty(),
            request.targetHeight?.toString().orEmpty(),
            request.fitMode.name,
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
        if (header.size < 9) return null
        if (header[0] != VERSION && header[0] != VERSION_LEGACY) return null

        val tool = ToolKind.fromId(header[2]) ?: return null
        val quality = runCatching { QualityPreset.valueOf(header[4]) }.getOrNull() ?: return null
        val pageSize = runCatching { PdfPageSize.valueOf(header[7]) }.getOrNull() ?: PdfPageSize.A4
        val pages = header[8]
            .split(',')
            .mapNotNull { it.toIntOrNull() }
            .takeIf { it.isNotEmpty() }

        val targetWidth = header.getOrNull(9)?.toIntOrNull()
        val targetHeight = header.getOrNull(10)?.toIntOrNull()
        val fitMode = runCatching { FitMode.valueOf(header.getOrNull(11).orEmpty()) }
            .getOrDefault(FitMode.FIT)

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
            // Если размер не сохранён (старая версия) или битый — просто оригинал.
            targetWidth = targetWidth?.takeIf { it > 0 },
            targetHeight = targetHeight?.takeIf { it > 0 },
            fitMode = fitMode,
        )
    }
}
