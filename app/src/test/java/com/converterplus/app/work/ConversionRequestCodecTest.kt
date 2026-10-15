package com.converterplus.app.work

import com.converterplus.app.engine.ConversionRequest
import com.converterplus.app.engine.PdfPageSize
import com.converterplus.app.engine.PickedFile
import com.converterplus.app.engine.QualityPreset
import com.converterplus.app.engine.ToolKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversionRequestCodecTest {

    private val request = ConversionRequest(
        id = "req-123",
        tool = ToolKind.PDF_TO_IMAGES,
        inputs = listOf(
            PickedFile("/cache/inputs/req-123/doc.pdf", "doc.pdf", 20480, "application/pdf"),
            PickedFile("/cache/inputs/req-123/отчёт.pdf", "отчёт.pdf", 1024, null),
        ),
        outputFormat = "png",
        quality = QualityPreset.MAX,
        watermark = false,
        selectedPages = listOf(1, 3, 5),
        pdfDpi = 300,
        pdfPageSize = PdfPageSize.FIT_IMAGE,
    )

    @Test
    fun `roundtrip keeps every field`() {
        assertEquals(request, ConversionRequestCodec.decode(ConversionRequestCodec.encode(request)))
    }

    @Test
    fun `images request roundtrip`() {
        val images = ConversionRequest(
            id = "img-1",
            tool = ToolKind.IMAGES,
            inputs = listOf(PickedFile("/c/photo.jpg", "photo.jpg", 999, "image/jpeg")),
            outputFormat = "webp",
            quality = QualityPreset.MIN_SIZE,
        )
        val decoded = ConversionRequestCodec.decode(ConversionRequestCodec.encode(images))
        assertEquals(images, decoded)
        assertNull(decoded?.selectedPages)
        assertTrue(decoded?.watermark == true)
    }

    @Test
    fun `garbage input returns null`() {
        assertNull(ConversionRequestCodec.decode(null))
        assertNull(ConversionRequestCodec.decode(""))
        assertNull(ConversionRequestCodec.decode("not-a-request"))
    }

    @Test
    fun `unknown tool id returns null`() {
        val broken = ConversionRequestCodec.encode(request).replace("pdf_to_images", "teleport")
        assertNull(ConversionRequestCodec.decode(broken))
    }

    @Test
    fun `request without inputs returns null`() {
        val broken = ConversionRequestCodec.encode(
            ConversionRequest(
                id = "x",
                tool = ToolKind.IMAGES,
                inputs = emptyList(),
                outputFormat = "jpg",
                quality = QualityPreset.BALANCED,
            ),
        )
        assertNull(ConversionRequestCodec.decode(broken))
    }

    @Test
    fun `file names with spaces and cyrillic survive`() {
        val decoded = ConversionRequestCodec.decode(ConversionRequestCodec.encode(request))
        assertEquals("отчёт.pdf", decoded?.inputs?.get(1)?.displayName)
    }
}
