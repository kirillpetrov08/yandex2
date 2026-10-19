package com.copilka.app.engine

import com.copilka.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilitiesTest {

    private val api26 = DeviceInfo(sdkInt = 26)
    private val api28 = DeviceInfo(sdkInt = 28)
    private val api31 = DeviceInfo(sdkInt = 31)

    private fun image(ext: String, mime: String = "image/$ext") = InputInfo(
        mediaKind = MediaKind.IMAGE,
        mimeType = mime,
        extension = ext,
        sizeBytes = 1024,
    )

    private fun pdf() = InputInfo(
        mediaKind = MediaKind.PDF,
        mimeType = "application/pdf",
        extension = "pdf",
        sizeBytes = 2048,
    )

    // ---------------------------------------------------------------- изображения

    @Test
    fun `jpg to png supported on any api`() {
        assertTrue(Capabilities.isSupported(ToolKind.IMAGES, image("jpg"), Formats.PNG, api26))
    }

    @Test
    fun `png to jpg supported on any api`() {
        assertTrue(Capabilities.isSupported(ToolKind.IMAGES, image("png"), Formats.JPG, api26))
    }

    @Test
    fun `webp to jpg supported`() {
        assertTrue(Capabilities.isSupported(ToolKind.IMAGES, image("webp"), Formats.JPG, api26))
    }

    @Test
    fun `jpg to webp supported`() {
        assertTrue(Capabilities.isSupported(ToolKind.IMAGES, image("jpg"), Formats.WEBP, api26))
    }

    @Test
    fun `heic to jpg requires api 28`() {
        assertFalse(Capabilities.isSupported(ToolKind.IMAGES, image("heic"), Formats.JPG, api26))
        assertTrue(Capabilities.isSupported(ToolKind.IMAGES, image("heic"), Formats.JPG, api28))
    }

    @Test
    fun `heic on api 26 has human readable reason`() {
        assertEquals(
            R.string.reason_heic_needs_android9,
            Capabilities.reason(ToolKind.IMAGES, image("heic"), Formats.JPG, api26),
        )
    }

    @Test
    fun `avif to png requires api 31`() {
        assertFalse(Capabilities.isSupported(ToolKind.IMAGES, image("avif"), Formats.PNG, api28))
        assertTrue(Capabilities.isSupported(ToolKind.IMAGES, image("avif"), Formats.PNG, api31))
    }

    @Test
    fun `avif on api 28 has reason`() {
        assertEquals(
            R.string.reason_avif_needs_android12,
            Capabilities.reason(ToolKind.IMAGES, image("avif"), Formats.PNG, api28),
        )
    }

    @Test
    fun `heic and avif are never offered as output`() {
        val options = Capabilities.optionsFor(ToolKind.IMAGES, image("jpg"), api31)
        assertTrue(options.none { it.id == "heic" || it.id == "avif" })
    }

    @Test
    fun `gif bmp tiff are never offered`() {
        val options = Capabilities.optionsFor(ToolKind.IMAGES, image("jpg"), api31).map { it.id }
        assertFalse(options.contains("gif"))
        assertFalse(options.contains("bmp"))
        assertFalse(options.contains("tiff"))
    }

    @Test
    fun `image tool offers exactly jpg png webp`() {
        val options = Capabilities.optionsFor(ToolKind.IMAGES, image("png"), api31)
        assertEquals(listOf(Formats.JPG, Formats.PNG, Formats.WEBP), options.map { it.id })
    }

    @Test
    fun `unavailable input disables every output option`() {
        val options = Capabilities.optionsFor(ToolKind.IMAGES, image("heic"), api26)
        assertTrue(options.isNotEmpty())
        assertTrue(options.all { !it.enabled })
        assertTrue(options.all { it.disabledReasonRes == R.string.reason_heic_needs_android9 })
    }

    // ---------------------------------------------------------------- pdf

    @Test
    fun `pdf to png and jpg supported`() {
        assertTrue(Capabilities.isSupported(ToolKind.PDF_TO_IMAGES, pdf(), Formats.PNG, api26))
        assertTrue(Capabilities.isSupported(ToolKind.PDF_TO_IMAGES, pdf(), Formats.JPG, api26))
    }

    @Test
    fun `pdf to webp is not supported`() {
        assertFalse(Capabilities.isSupported(ToolKind.PDF_TO_IMAGES, pdf(), Formats.WEBP, api26))
        assertEquals(
            R.string.reason_output_not_supported,
            Capabilities.reason(ToolKind.PDF_TO_IMAGES, pdf(), Formats.WEBP, api26),
        )
    }

    @Test
    fun `password protected pdf reports reason`() {
        val device = api26.copy(inputPasswordProtected = true, canReadInput = false)
        assertEquals(
            R.string.reason_pdf_password,
            Capabilities.reason(ToolKind.PDF_TO_IMAGES, pdf(), Formats.PNG, device),
        )
    }

    @Test
    fun `unreadable container reports reason`() {
        val device = api26.copy(canReadInput = false)
        assertEquals(
            R.string.reason_container_not_supported,
            Capabilities.reason(ToolKind.PDF_TO_IMAGES, pdf(), Formats.PNG, device),
        )
    }

    @Test
    fun `images to pdf offers only pdf`() {
        val options = Capabilities.optionsFor(ToolKind.IMAGES_TO_PDF, image("jpg"), api26)
        assertEquals(listOf(Formats.PDF), options.map { it.id })
    }

    @Test
    fun `wrong file type for tool`() {
        assertEquals(
            R.string.reason_wrong_file_type,
            Capabilities.reason(ToolKind.PDF_TO_IMAGES, image("jpg"), Formats.PNG, api26),
        )
    }

    // ---------------------------------------------------------------- аудио, видео, OCR

    private fun audio(ext: String = "mp3") = InputInfo(
        mediaKind = MediaKind.AUDIO,
        mimeType = "audio/mpeg",
        extension = ext,
        sizeBytes = 4096,
    )

    private fun video(ext: String = "mp4") = InputInfo(
        mediaKind = MediaKind.VIDEO,
        mimeType = "video/mp4",
        extension = ext,
        sizeBytes = 8192,
    )

    @Test
    fun `audio formats depend on input and device`() {
        // Порядок форматов одинаков всегда; доступность OGG зависит от энкодера Opus.
        val withOpus = Capabilities.optionsFor(ToolKind.AUDIO, audio(), api31.copy(hasOpusEncoder = true))
        assertEquals(listOf(Formats.M4A, Formats.WAV, Formats.OGG), withOpus.map { it.id })
        assertTrue(withOpus.all { it.enabled })

        val withoutOpus = Capabilities.optionsFor(ToolKind.AUDIO, audio(), api31)
        assertEquals(listOf(Formats.M4A, Formats.WAV, Formats.OGG), withoutOpus.map { it.id })
        assertEquals(listOf(Formats.M4A, Formats.WAV), withoutOpus.filter { it.enabled }.map { it.id })
    }

    @Test
    fun `audio formats are disabled without a readable file`() {
        assertTrue(Capabilities.optionsFor(ToolKind.AUDIO, null, api31).none { it.enabled })
        assertTrue(Capabilities.optionsFor(ToolKind.AUDIO, image("jpg"), api31).none { it.enabled })
    }

    @Test
    fun `ogg needs android 10 and opus encoder`() {
        val withoutEncoder = DeviceInfo(sdkInt = 31, hasOpusEncoder = false)
        val oldAndroid = DeviceInfo(sdkInt = 28, hasOpusEncoder = true)

        assertEquals(R.string.reason_opus_unavailable, Capabilities.opusReason(withoutEncoder))
        assertEquals(R.string.reason_opus_unavailable, Capabilities.opusReason(oldAndroid))
        assertNull(Capabilities.opusReason(api31.copy(hasOpusEncoder = true)))

        val disabled = Capabilities.optionsFor(ToolKind.AUDIO, audio(), withoutEncoder)
            .first { it.id == Formats.OGG }
        assertFalse(disabled.enabled)
        assertEquals(R.string.reason_opus_unavailable, disabled.disabledReasonRes)
    }

    @Test
    fun `video offers mp4 and audio extraction`() {
        val options = Capabilities.optionsFor(ToolKind.VIDEO, video(), api31)
        assertEquals(listOf(Formats.MP4, Formats.M4A), options.map { it.id })
        assertTrue(options.all { it.enabled })
    }

    @Test
    fun `audio extraction needs an audio track`() {
        val silent = DeviceInfo(sdkInt = 31, hasAudioTrack = false)
        val options = Capabilities.optionsFor(ToolKind.VIDEO, video(), silent)

        assertTrue(options.first { it.id == Formats.MP4 }.enabled)
        val extract = options.first { it.id == Formats.M4A }
        assertFalse(extract.enabled)
        assertEquals(R.string.err_no_audio_track, extract.disabledReasonRes)
    }

    @Test
    fun `ocr accepts images and pdf only`() {
        assertTrue(Capabilities.isSupported(ToolKind.OCR, image("jpg"), Formats.TXT, api31))
        assertTrue(Capabilities.isSupported(ToolKind.OCR, pdf(), Formats.TXT, api31))

        assertEquals(
            R.string.reason_wrong_file_type,
            Capabilities.reason(ToolKind.OCR, audio(), Formats.TXT, api31),
        )
        assertEquals(
            R.string.reason_output_not_supported,
            Capabilities.reason(ToolKind.OCR, image("jpg"), Formats.PDF, api31),
        )
    }

    @Test
    fun `watermark and page selection are limited to the right tools`() {
        assertTrue(Capabilities.supportsWatermark(ToolKind.IMAGES))
        assertTrue(Capabilities.supportsWatermark(ToolKind.IMAGES_TO_PDF))
        assertFalse(Capabilities.supportsWatermark(ToolKind.AUDIO))
        assertFalse(Capabilities.supportsWatermark(ToolKind.VIDEO))
        assertFalse(Capabilities.supportsWatermark(ToolKind.OCR))

        assertTrue(Capabilities.supportsPageSelection(ToolKind.PDF_TO_IMAGES))
        assertTrue(Capabilities.supportsPageSelection(ToolKind.OCR))
        assertFalse(Capabilities.supportsPageSelection(ToolKind.VIDEO))
    }

    @Test
    fun `gallery source exists only where gallery can show the file`() {
        assertTrue(Capabilities.hasGallerySource(ToolKind.IMAGES))
        assertTrue(Capabilities.hasGallerySource(ToolKind.OCR))
        assertFalse(Capabilities.hasGallerySource(ToolKind.PDF_TO_IMAGES))
        assertFalse(Capabilities.hasGallerySource(ToolKind.AUDIO))
        assertFalse(Capabilities.hasGallerySource(ToolKind.VIDEO))
    }

    @Test
    fun `media mime types are requested from the document picker`() {
        assertEquals(listOf("audio/*"), Capabilities.mimeTypesFor(ToolKind.AUDIO).toList())
        assertTrue("video/mp4" in Capabilities.mimeTypesFor(ToolKind.VIDEO))
        assertTrue("application/pdf" in Capabilities.mimeTypesFor(ToolKind.OCR))
    }

    // ---------------------------------------------------------------- служебное

    @Test
    fun `disabled option always carries a reason`() {
        val options = Capabilities.optionsFor(ToolKind.IMAGES, image("heic"), api26)
        options.filter { !it.enabled }.forEach { option ->
            assertTrue(option.disabledReasonRes != null)
        }
        val enabled = Capabilities.optionsFor(ToolKind.IMAGES, image("jpg"), api31)
        enabled.filter { it.enabled }.forEach { option ->
            assertNull(option.disabledReasonRes)
        }
    }

    @Test
    fun `default format is always enabled for a valid input`() {
        val cases = listOf(
            ToolKind.IMAGES to image("jpg"),
            ToolKind.PDF_TO_IMAGES to pdf(),
            ToolKind.IMAGES_TO_PDF to image("png"),
        )
        cases.forEach { (tool, input) ->
            val default = Capabilities.defaultFormat(tool)
            assertTrue(
                "$tool -> $default",
                Capabilities.isSupported(tool, input, default, api31),
            )
        }
    }
}
