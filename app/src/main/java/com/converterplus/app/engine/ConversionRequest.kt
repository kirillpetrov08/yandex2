package com.converterplus.app.engine

import com.converterplus.app.engine.image.FitMode

/** Инструменты приложения: изображения, PDF, OCR, аудио и видео. */
enum class ToolKind(val id: String) {
    IMAGES("images"),
    PDF_TO_IMAGES("pdf_to_images"),
    IMAGES_TO_PDF("images_to_pdf"),
    OCR("ocr"),
    AUDIO("audio"),
    VIDEO("video");

    companion object {
        fun fromId(id: String): ToolKind? = entries.firstOrNull { it.id == id }
    }
}

/** Тип входного файла. Нужен реестру возможностей, чтобы решать, что поддерживается. */
enum class MediaKind { IMAGE, PDF, AUDIO, VIDEO, TEXT, UNKNOWN }

/** Качество по умолчанию и в экране инструмента. */
enum class QualityPreset { MAX, BALANCED, MIN_SIZE }

/** Размер страницы при сборке PDF из картинок. */
enum class PdfPageSize { A4, FIT_IMAGE }

/**
 * Выбранный пользователем файл.
 * К моменту конвертации файл уже скопирован в кэш приложения ([localPath]),
 * поэтому фоновая работа не зависит от срока действия URI-гранта.
 */
data class PickedFile(
    val localPath: String,
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String?,
) {
    val extension: String get() = displayName.substringAfterLast('.', "").lowercase()

    fun toInputInfo(): InputInfo = InputInfo(
        mediaKind = MediaKindDetector.kindOf(mimeType, extension),
        mimeType = mimeType,
        extension = extension,
        sizeBytes = sizeBytes,
    )
}

/** Всё, что нужно реестру возможностей для решения о поддержке формата. */
data class InputInfo(
    val mediaKind: MediaKind,
    val mimeType: String?,
    val extension: String?,
    val sizeBytes: Long,
)

/** Полный запрос на конвертацию. Передаётся в WorkManager. */
data class ConversionRequest(
    val id: String,
    val tool: ToolKind,
    val inputs: List<PickedFile>,
    val outputFormat: String,
    val quality: QualityPreset,
    val watermark: Boolean = true,
    /** Номера страниц PDF (с 1). null или пусто — все страницы. */
    val selectedPages: List<Int>? = null,
    val pdfDpi: Int = 150,
    val pdfPageSize: PdfPageSize = PdfPageSize.A4,
    /**
     * Размер результата для изображений. null — оставить как есть (пункт «Оригинал»).
     * В запрос попадают уже проверенные значения из [com.converterplus.app.engine.image.ImageSizing].
     */
    val targetWidth: Int? = null,
    val targetHeight: Int? = null,
    val fitMode: FitMode = FitMode.FIT,
) {
    val totalInputBytes: Long get() = inputs.sumOf { it.sizeBytes }

    companion object {
        const val DPI_LOW = 150
        const val DPI_HIGH = 300
    }
}
