package com.converterplus.app.engine

import com.converterplus.app.R

/** Определение типа файла по MIME и расширению. Чистая функция — покрыта тестами. */
object MediaKindDetector {

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "avif")
    private val PDF_EXTENSIONS = setOf("pdf")
    private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "wav", "ogg", "opus", "flac")
    private val VIDEO_EXTENSIONS = setOf("mp4", "mov", "mkv")
    private val TEXT_EXTENSIONS = setOf("txt")

    fun kindOf(mimeType: String?, extension: String?): MediaKind {
        val ext = extension?.lowercase().orEmpty()
        val mime = mimeType?.lowercase().orEmpty()

        return when {
            ext in IMAGE_EXTENSIONS || mime.startsWith("image/") -> MediaKind.IMAGE
            ext in PDF_EXTENSIONS || mime == "application/pdf" -> MediaKind.PDF
            ext in AUDIO_EXTENSIONS || mime.startsWith("audio/") -> MediaKind.AUDIO
            ext in VIDEO_EXTENSIONS || mime.startsWith("video/") -> MediaKind.VIDEO
            ext in TEXT_EXTENSIONS || mime.startsWith("text/plain") -> MediaKind.TEXT
            else -> MediaKind.UNKNOWN
        }
    }

    fun isImageExtension(ext: String?): Boolean = ext?.lowercase() in IMAGE_EXTENSIONS
    fun isPdfExtension(ext: String?): Boolean = ext?.lowercase() in PDF_EXTENSIONS
}

/** Идентификаторы выходных форматов. */
object Formats {
    const val JPG = "jpg"
    const val PNG = "png"
    const val WEBP = "webp"
    const val PDF = "pdf"
    const val TXT = "txt"
    const val WAV = "wav"
    const val M4A = "m4a"
    const val OGG = "ogg"
    const val MP4 = "mp4"

    val ALL: Set<String> = setOf(JPG, PNG, WEBP, PDF, TXT, WAV, M4A, OGG, MP4)
}

/** Формат, который недоступен, но о котором надо честно рассказать. */
data class FormatOption(
    val id: String,
    val titleRes: Int,
    val hintRes: Int,
    val enabled: Boolean,
    val disabledReasonRes: Int? = null,
)

/**
 * Что известно о конкретном устройстве и входном файле. Передаётся из Android-слоя,
 * чтобы сама логика возможностей была чистой и тестируемой.
 */
data class DeviceInfo(
    val sdkInt: Int,
    /** true, если MediaCodecList нашёл энкодер audio/opus. */
    val hasOpusEncoder: Boolean = false,
    /** false, если MediaExtractor не смог открыть входной контейнер. */
    val canReadInput: Boolean = true,
    /** true, если PDF защищён паролем. */
    val inputPasswordProtected: Boolean = false,
    /** Есть ли во входном видео видеодорожка. */
    val hasVideoTrack: Boolean = true,
    /** Есть ли во входном видео звуковая дорожка. */
    val hasAudioTrack: Boolean = true,
)

/**
 * Реестр возможностей — главный механизм надёжности.
 *
 * Форматы не зашиты намертво: часть зависит от версии Android и от кодеков конкретного телефона.
 * Если формат недоступен, он остаётся в списке, но выключен и с понятной причиной,
 * поэтому приложение не падает и не обещает того, чего не умеет.
 */
object Capabilities {

    /** Инструменты, которые работают. Список — единственный источник правды для интерфейса. */
    val AVAILABLE_TOOLS: Set<ToolKind> = setOf(
        ToolKind.IMAGES,
        ToolKind.PDF_TO_IMAGES,
        ToolKind.OCR,
        ToolKind.AUDIO,
        ToolKind.VIDEO,
    )

    fun isToolAvailable(tool: ToolKind): Boolean = tool in AVAILABLE_TOOLS

    /** Причина недоступности инструмента (ресурс строки). */
    fun toolUnavailableReason(tool: ToolKind): Int? =
        if (isToolAvailable(tool)) null else R.string.reason_tool_soon

    fun defaultFormat(tool: ToolKind): String = when (tool) {
        ToolKind.IMAGES -> Formats.JPG
        ToolKind.PDF_TO_IMAGES -> Formats.PNG
        ToolKind.IMAGES_TO_PDF -> Formats.PDF
        ToolKind.OCR -> Formats.TXT
        ToolKind.AUDIO -> Formats.M4A
        ToolKind.VIDEO -> Formats.MP4
    }

    /** Список форматов результата для инструмента с учётом входа и устройства. */
    fun optionsFor(tool: ToolKind, input: InputInfo?, device: DeviceInfo): List<FormatOption> {
        if (!isToolAvailable(tool)) return emptyList()
        return when (tool) {
            ToolKind.IMAGES -> listOf(
                imageOption(Formats.JPG, R.string.format_jpg, R.string.hint_jpg, input, device),
                imageOption(Formats.PNG, R.string.format_png, R.string.hint_png, input, device),
                imageOption(Formats.WEBP, R.string.format_webp, R.string.hint_webp, input, device),
            )

            ToolKind.PDF_TO_IMAGES -> listOf(
                FormatOption(Formats.PNG, R.string.format_png, R.string.hint_png, enabled = pdfReadable(input, device)),
                FormatOption(Formats.JPG, R.string.format_jpg, R.string.hint_jpg, enabled = pdfReadable(input, device)),
            )

            ToolKind.IMAGES_TO_PDF -> listOf(
                FormatOption(Formats.PDF, R.string.format_pdf, R.string.hint_pdf, enabled = true),
            )

            ToolKind.OCR -> listOf(
                FormatOption(
                    id = Formats.TXT,
                    titleRes = R.string.format_txt,
                    hintRes = R.string.hint_txt,
                    enabled = ocrReadable(input, device),
                    disabledReasonRes = if (ocrReadable(input, device)) null else inputReadReason(input, device),
                ),
            )

            ToolKind.AUDIO -> {
                val readable = audioReadable(input, device)
                listOf(
                    FormatOption(Formats.M4A, R.string.format_m4a, R.string.hint_m4a, enabled = readable),
                    FormatOption(Formats.WAV, R.string.format_wav, R.string.hint_wav, enabled = readable),
                    FormatOption(
                        id = Formats.OGG,
                        titleRes = R.string.format_ogg,
                        hintRes = R.string.hint_ogg,
                        enabled = readable && opusReason(device) == null,
                        disabledReasonRes = if (readable) opusReason(device) else null,
                    ),
                )
            }

            ToolKind.VIDEO -> {
                val readable = videoReadable(input, device)
                listOf(
                    FormatOption(Formats.MP4, R.string.format_mp4, R.string.hint_mp4, enabled = readable),
                    FormatOption(
                        id = Formats.M4A,
                        titleRes = R.string.format_audio_only,
                        hintRes = R.string.hint_audio_only,
                        enabled = readable && device.hasAudioTrack,
                        disabledReasonRes = if (readable && !device.hasAudioTrack) R.string.err_no_audio_track else null,
                    ),
                )
            }
        }
    }

    private fun imageOption(
        id: String,
        titleRes: Int,
        hintRes: Int,
        input: InputInfo?,
        device: DeviceInfo,
    ): FormatOption {
        val reason = inputReadReason(input, device)
        return FormatOption(
            id = id,
            titleRes = titleRes,
            hintRes = hintRes,
            enabled = reason == null,
            disabledReasonRes = reason,
        )
    }

    private fun pdfReadable(input: InputInfo?, device: DeviceInfo): Boolean =
        input?.mediaKind == MediaKind.PDF && !device.inputPasswordProtected && device.canReadInput

    /** Картинка или PDF для распознавания текста. */
    private fun ocrReadable(input: InputInfo?, device: DeviceInfo): Boolean =
        input != null &&
            (input.mediaKind == MediaKind.IMAGE || input.mediaKind == MediaKind.PDF) &&
            device.canReadInput &&
            !device.inputPasswordProtected

    private fun audioReadable(input: InputInfo?, device: DeviceInfo): Boolean =
        input?.mediaKind == MediaKind.AUDIO && device.canReadInput && device.hasAudioTrack

    private fun videoReadable(input: InputInfo?, device: DeviceInfo): Boolean =
        input?.mediaKind == MediaKind.VIDEO && device.canReadInput && device.hasVideoTrack

    /**
     * Причина, по которой входной файл нельзя прочитать.
     * null — всё в порядке.
     */
    fun inputReadReason(input: InputInfo?, device: DeviceInfo): Int? {
        if (input == null) return R.string.err_no_input
        val ext = input.extension.orEmpty()
        return when {
            (ext == "heic" || ext == "heif") && device.sdkInt < 28 -> R.string.reason_heic_needs_android9
            ext == "avif" && device.sdkInt < 31 -> R.string.reason_avif_needs_android12
            device.inputPasswordProtected -> R.string.reason_pdf_password
            !device.canReadInput -> R.string.reason_container_not_supported
            else -> null
        }
    }

    /** Поддерживается ли пара «вход → выход». */
    fun isSupported(tool: ToolKind, input: InputInfo?, output: String, device: DeviceInfo): Boolean =
        reason(tool, input, output, device) == null

    /** Причина недоступности пары «вход → выход». null — поддерживается. */
    fun reason(tool: ToolKind, input: InputInfo?, output: String, device: DeviceInfo): Int? {
        if (!isToolAvailable(tool)) return R.string.reason_tool_soon
        if (output !in Formats.ALL) return R.string.reason_output_not_supported

        val inputReason = inputReadReason(input, device)
        if (inputReason != null) return inputReason

        return when (tool) {
            ToolKind.IMAGES -> when {
                output !in setOf(Formats.JPG, Formats.PNG, Formats.WEBP) ->
                    R.string.reason_output_not_supported

                input?.mediaKind != MediaKind.IMAGE -> R.string.reason_wrong_file_type
                else -> null
            }

            ToolKind.PDF_TO_IMAGES -> when {
                output !in setOf(Formats.PNG, Formats.JPG) -> R.string.reason_output_not_supported
                input?.mediaKind != MediaKind.PDF -> R.string.reason_wrong_file_type
                else -> null
            }

            ToolKind.IMAGES_TO_PDF -> when {
                output != Formats.PDF -> R.string.reason_output_not_supported
                input?.mediaKind != MediaKind.IMAGE -> R.string.reason_wrong_file_type
                else -> null
            }

            ToolKind.OCR -> when {
                output != Formats.TXT -> R.string.reason_output_not_supported
                input?.mediaKind != MediaKind.IMAGE && input?.mediaKind != MediaKind.PDF ->
                    R.string.reason_wrong_file_type

                else -> null
            }

            ToolKind.AUDIO -> when {
                output !in setOf(Formats.M4A, Formats.WAV, Formats.OGG) ->
                    R.string.reason_output_not_supported

                input?.mediaKind != MediaKind.AUDIO -> R.string.reason_wrong_file_type
                output == Formats.OGG -> opusReason(device)
                else -> null
            }

            ToolKind.VIDEO -> when {
                output !in setOf(Formats.MP4, Formats.M4A) -> R.string.reason_output_not_supported
                input?.mediaKind != MediaKind.VIDEO -> R.string.reason_wrong_file_type
                output == Formats.M4A && !device.hasAudioTrack -> R.string.err_no_audio_track
                else -> null
            }
        }
    }

    /**
     * Причина недоступности OGG/Opus: нужен Android 10+ (MediaMuxer OGG) и энкодер Opus
     * на самом устройстве.
     */
    fun opusReason(device: DeviceInfo): Int? =
        if (device.sdkInt >= 29 && device.hasOpusEncoder) null else R.string.reason_opus_unavailable

    /** Файл уже в нужном формате: спрашиваем подтверждение, а не конвертируем молча. */
    fun isAlreadyInFormat(input: InputInfo?, output: String): Boolean {
        val ext = input?.extension.orEmpty()
        return when (output) {
            Formats.JPG -> ext == "jpg" || ext == "jpeg"
            Formats.PNG -> ext == "png"
            Formats.WEBP -> ext == "webp"
            Formats.PDF -> ext == "pdf"
            else -> ext == output
        }
    }

    /** Водяной знак ставим только туда, где он не портит результат (картинки и PDF). */
    fun supportsWatermark(tool: ToolKind): Boolean = tool in setOf(
        ToolKind.IMAGES,
        ToolKind.PDF_TO_IMAGES,
    )

    /** У инструмента есть выбор страниц PDF. */
    fun supportsPageSelection(tool: ToolKind): Boolean =
        tool == ToolKind.PDF_TO_IMAGES || tool == ToolKind.OCR

    /** Для OCR «качество» означает точность распознавания, а не битрейт. */
    fun hasQualityChoice(tool: ToolKind): Boolean = tool != ToolKind.OCR

    /** Допустимые MIME для системного пикера документов. */
    fun mimeTypesFor(tool: ToolKind): Array<String> = when (tool) {
        ToolKind.IMAGES -> arrayOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif", "image/avif")
        ToolKind.PDF_TO_IMAGES -> arrayOf("application/pdf")
        ToolKind.IMAGES_TO_PDF -> arrayOf("image/jpeg", "image/png", "image/webp")
        ToolKind.OCR -> arrayOf("image/jpeg", "image/png", "image/heic", "application/pdf")
        ToolKind.AUDIO -> arrayOf("audio/*")
        ToolKind.VIDEO -> arrayOf("video/mp4", "video/quicktime", "video/x-matroska")
    }

    /** Инструмент принимает только изображения — кнопка «Из файлов» не нужна. */
    fun isImageOnlyTool(tool: ToolKind): Boolean = tool == ToolKind.IMAGES

    /** У инструмента есть смысл в выборе «Из галереи» (в галерее только картинки). */
    fun hasGallerySource(tool: ToolKind): Boolean = tool in setOf(
        ToolKind.IMAGES,
        ToolKind.OCR,
    )

    /** Несколько файлов сразу больше не принимаем: «Фото → PDF» снят. */
    fun supportsMultipleFiles(tool: ToolKind): Boolean = false

    const val MAX_FILES_PER_CONVERSION = 30
}
