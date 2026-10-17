package com.converterplus.app.engine.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrTextTest {

    @Test
    fun `noise symbols around the text are removed`() {
        assertEquals(
            "Кубок студенческой лиги",
            OcrText.normalizeLine("> Кубок студенческой лиги ="),
        )
        assertEquals(
            "ПУЛЬС ДУ: ТИШИНА",
            OcrText.normalizeLine("+ + ПУЛЬС ДУ: ТИШИНА +"),
        )
        assertEquals("ро", OcrText.normalizeLine("| <> — — — = ро ."))
    }

    @Test
    fun `lines without letters or digits are dropped`() {
        assertFalse(OcrText.isMeaningful("| <> — — — = + _ _"))
        assertFalse(OcrText.isMeaningful("; +"))
        assertFalse(OcrText.isMeaningful("||| ||"))
        assertEquals("", OcrText.clean("| <> = + _ _\n; +\n"))
    }

    @Test
    fun `short page numbers survive cleaning`() {
        assertTrue(OcrText.isMeaningful("4"))
        assertTrue(OcrText.isMeaningful("12"))
        assertEquals("4", OcrText.clean("4"))
    }

    @Test
    fun `excess spaces are collapsed`() {
        assertEquals("Диплом 2", OcrText.normalizeLine("  Диплом   2  "))
        assertEquals("слово, слово", OcrText.normalizeLine("слово , слово"))
    }

    @Test
    fun `letter spaced text is glued back`() {
        assertEquals(
            "Минимальный",
            OcrText.normalizeLine("М и н и м а л ь н ы й"),
        )
        // Короткие фразы из обычных слов не трогаем.
        assertEquals("Я и ты", OcrText.normalizeLine("Я и ты"))
    }

    @Test
    fun `hyphenated word break is joined`() {
        assertEquals(
            "конвертация файлов",
            OcrText.clean("конверта-\nция файлов"),
        )
    }

    @Test
    fun `extra blank lines collapse to one`() {
        assertEquals("Первый\n\nВторой", OcrText.clean("Первый\n\n\n\n\nВторой"))
    }

    @Test
    fun `trailing spaces are trimmed`() {
        assertEquals("Строка\nВторая", OcrText.clean("Строка   \nВторая\t"))
    }

    @Test
    fun `single page has no page header`() {
        val text = OcrText.joinPages(listOf(OcrPage(1, "Привет")))
        assertEquals("Привет", text)
    }

    @Test
    fun `several pages get headers and skip empty ones`() {
        val text = OcrText.joinPages(
            listOf(
                OcrPage(1, "Первая"),
                OcrPage(2, "   \n  "),
                OcrPage(3, "Третья"),
            ),
        )
        assertEquals("— Страница 1 —\n\nПервая\n\n— Страница 3 —\n\nТретья", text)
    }

    @Test
    fun `poster garbage with a few real words is cleaned`() {
        val raw = """
            | Казанский · Казанский федеральный РУ] 4
            Не =
            + _ _ _
            > За выдающийся личный результат
            4 || || пу; М £’
            ; +
            Петрову Кириллу Игоревичу ~
        """.trimIndent()

        val cleaned = OcrText.clean(raw)

        assertFalse(cleaned.contains("|"))
        assertFalse(cleaned.contains("_"))
        assertTrue(cleaned.contains("За выдающийся личный результат"))
        assertTrue(cleaned.contains("Петрову Кириллу Игоревичу"))
        assertTrue(cleaned.contains("Не"))
    }
}
