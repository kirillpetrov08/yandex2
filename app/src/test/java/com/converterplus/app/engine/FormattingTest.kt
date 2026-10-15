package com.converterplus.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormattingTest {

    @Test
    fun `bytes are formatted in russian units`() {
        assertEquals("0 Б", Formatting.formatBytes(0))
        assertEquals("512 Б", Formatting.formatBytes(512))
        assertEquals("1023 Б", Formatting.formatBytes(1023))
    }

    @Test
    fun `kilobytes use comma separator`() {
        assertEquals("1 КБ", Formatting.formatBytes(1024))
        assertEquals("1,5 КБ", Formatting.formatBytes(1536))
        assertEquals("2 КБ", Formatting.formatBytes(2048))
    }

    @Test
    fun `megabytes and gigabytes`() {
        assertEquals("1 МБ", Formatting.formatBytes(1024L * 1024L))
        assertEquals("1,5 МБ", Formatting.formatBytes(1536L * 1024L))
        assertEquals("1 ГБ", Formatting.formatBytes(1024L * 1024L * 1024L))
        assertEquals("2,5 ГБ", Formatting.formatBytes(2560L * 1024L * 1024L))
    }

    @Test
    fun `negative size is clamped`() {
        assertEquals("0 Б", Formatting.formatBytes(-100))
    }

    @Test
    fun `saved percent is rounded`() {
        assertEquals(25, Formatting.savedPercent(1000, 750))
        assertEquals(0, Formatting.savedPercent(1000, 1000))
        assertEquals(-20, Formatting.savedPercent(500, 600))
        assertEquals(33, Formatting.savedPercent(300, 200))
    }

    @Test
    fun `saved percent is null for unknown source size`() {
        assertNull(Formatting.savedPercent(0, 100))
        assertNull(Formatting.savedPercent(-5, 100))
    }

    @Test
    fun `percent sign and minus`() {
        assertEquals("37 %", Formatting.formatPercent(37))
        assertEquals("0 %", Formatting.formatPercent(0))
        assertEquals("−12 %", Formatting.formatPercent(-12))
    }

    @Test
    fun `duration formatting`() {
        assertEquals("0 сек", Formatting.formatDuration(0))
        assertEquals("45 сек", Formatting.formatDuration(45_000))
        assertEquals("1 мин", Formatting.formatDuration(60_000))
        assertEquals("3 мин 20 сек", Formatting.formatDuration(200_000))
        assertEquals("1 ч 05 мин", Formatting.formatDuration(3_900_000))
        assertEquals("0 сек", Formatting.formatDuration(-1))
    }
}
