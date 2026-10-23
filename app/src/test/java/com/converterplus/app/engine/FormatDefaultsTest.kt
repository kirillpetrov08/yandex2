package com.converterplus.app.engine

import com.converterplus.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatDefaultsTest {

    @Test
    fun `default format for images is jpg`() {
        assertEquals(Formats.JPG, Capabilities.defaultFormat(ToolKind.IMAGES))
    }

    @Test
    fun `default format for pdf to images is png`() {
        assertEquals(Formats.PNG, Capabilities.defaultFormat(ToolKind.PDF_TO_IMAGES))
    }

    @Test
    fun `default format for images to pdf is pdf`() {
        assertEquals(Formats.PDF, Capabilities.defaultFormat(ToolKind.IMAGES_TO_PDF))
    }

    @Test
    fun `default format is defined for every tool`() {
        assertEquals(Formats.TXT, Capabilities.defaultFormat(ToolKind.OCR))
        assertEquals(Formats.M4A, Capabilities.defaultFormat(ToolKind.AUDIO))
        assertEquals(Formats.MP4, Capabilities.defaultFormat(ToolKind.VIDEO))
    }

    @Test
    fun `file already in target format is detected`() {
        val jpg = InputInfo(MediaKind.IMAGE, "image/jpeg", "jpg", 100)
        val jpeg = InputInfo(MediaKind.IMAGE, "image/jpeg", "jpeg", 100)
        val png = InputInfo(MediaKind.IMAGE, "image/png", "png", 100)

        assertTrue(Capabilities.isAlreadyInFormat(jpg, Formats.JPG))
        assertTrue(Capabilities.isAlreadyInFormat(jpeg, Formats.JPG))
        assertTrue(Capabilities.isAlreadyInFormat(png, Formats.PNG))
        assertFalse(Capabilities.isAlreadyInFormat(png, Formats.JPG))
        assertFalse(Capabilities.isAlreadyInFormat(null, Formats.JPG))
    }

    @Test
    fun `multi file is not offered`() {
        ToolKind.entries.forEach { tool ->
            assertFalse(Capabilities.supportsMultipleFiles(tool))
        }
    }

    @Test
    fun `image only tool hides file picker button`() {
        assertTrue(Capabilities.isImageOnlyTool(ToolKind.IMAGES))
        assertFalse(Capabilities.isImageOnlyTool(ToolKind.PDF_TO_IMAGES))
    }

    @Test
    fun `media kind detection by extension and mime`() {
        assertEquals(MediaKind.IMAGE, MediaKindDetector.kindOf("image/jpeg", "jpg"))
        assertEquals(MediaKind.IMAGE, MediaKindDetector.kindOf(null, "heic"))
        assertEquals(MediaKind.PDF, MediaKindDetector.kindOf("application/pdf", null))
        assertEquals(MediaKind.AUDIO, MediaKindDetector.kindOf("audio/mpeg", "mp3"))
        assertEquals(MediaKind.VIDEO, MediaKindDetector.kindOf("video/mp4", null))
        assertEquals(MediaKind.TEXT, MediaKindDetector.kindOf("text/plain", "txt"))
        assertEquals(MediaKind.UNKNOWN, MediaKindDetector.kindOf(null, "xyz"))
    }

    @Test
    fun `available tools exclude withdrawn images to pdf`() {
        assertFalse(Capabilities.isToolAvailable(ToolKind.IMAGES_TO_PDF))
        assertEquals(R.string.reason_tool_soon, Capabilities.toolUnavailableReason(ToolKind.IMAGES_TO_PDF))
        val available = ToolKind.entries.filter { it != ToolKind.IMAGES_TO_PDF }.toSet()
        assertEquals(available, Capabilities.AVAILABLE_TOOLS)
        available.forEach { tool ->
            assertTrue("инструмент ${tool.id} должен быть доступен", Capabilities.isToolAvailable(tool))
            assertNull(Capabilities.toolUnavailableReason(tool))
        }
    }
}
