package com.converterplus.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNameFactoryTest {

    @Test
    fun `extension is replaced, base kept`() {
        assertEquals("photo.jpg", FileNameFactory.outputName("photo.png", "jpg", emptySet()))
        assertEquals("photo.png", FileNameFactory.outputName("photo.jpg", "png", emptySet()))
        assertEquals("doc-1.pdf", FileNameFactory.outputName("doc-1.jpg", "pdf", emptySet()))
    }

    @Test
    fun `extension normalised to lowercase without dot`() {
        assertEquals("photo.webp", FileNameFactory.outputName("photo.jpg", ".WEBP", emptySet()))
    }

    @Test
    fun `collision gets numeric suffix`() {
        val taken = setOf("photo.png")
        assertEquals("photo (1).png", FileNameFactory.outputName("photo.png", "png", taken))

        val more = setOf("photo.png", "photo (1).png")
        assertEquals("photo (2).png", FileNameFactory.outputName("photo.png", "png", more))
    }

    @Test
    fun `collision check ignores case`() {
        assertEquals("photo (1).png", FileNameFactory.outputName("photo.png", "png", setOf("PHOTO.PNG")))
    }

    @Test
    fun `invalid characters are removed`() {
        assertEquals(
            "a_b_c_d_e_f_g_h_i_h",
            FileNameFactory.sanitizeBaseName("a/b\\c:d*e?f\"g<h>i|h"),
        )
        assertEquals("my file", FileNameFactory.sanitizeBaseName("  my   file  "))
    }

    @Test
    fun `empty name falls back to default`() {
        assertEquals("conversion", FileNameFactory.sanitizeBaseName("   "))
        assertEquals("conversion.pdf", FileNameFactory.outputName("", "pdf", emptySet()))
    }

    @Test
    fun `very long name is truncated`() {
        val long = "a".repeat(200)
        val result = FileNameFactory.outputName(long, "png", emptySet())
        assertTrue(result.length <= 80 + 4)
    }

    @Test
    fun `base name without extension`() {
        assertEquals("photo", FileNameFactory.baseNameWithoutExtension("photo.jpeg"))
        assertEquals("archive.tar", FileNameFactory.baseNameWithoutExtension("archive.tar.gz"))
        assertEquals("noext", FileNameFactory.baseNameWithoutExtension("noext"))
    }

    @Test
    fun `paged names are zero padded`() {
        assertEquals(
            listOf("doc-01.png", "doc-02.png", "doc-03.png"),
            FileNameFactory.pagedNames("doc.pdf", "png", listOf(1, 2, 3)),
        )
    }

    @Test
    fun `paged names width grows with page count`() {
        val pages = (1..12).toList()
        val names = FileNameFactory.pagedNames("report.pdf", "jpg", pages)
        assertEquals("report-01.jpg", names.first())
        assertEquals("report-12.jpg", names.last())
        assertEquals(12, names.size)
        assertEquals(names.size, names.distinct().size)
    }

    @Test
    fun `generated names never contain forbidden characters`() {
        val name = FileNameFactory.outputName("отчёт/финал:версия?.png", "jpg", emptySet())
        assertFalse(name.contains('/'))
        assertFalse(name.contains(':'))
        assertFalse(name.contains('?'))
        assertTrue(name.endsWith(".jpg"))
    }
}
