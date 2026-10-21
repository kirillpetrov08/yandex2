package com.converterplus.app.engine.ocr

import android.graphics.Bitmap
import com.converterplus.app.engine.OcrInitException
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.Closeable

/**
 * Обёртка над Tesseract4Android. Единственное место в проекте, где вообще упоминается
 * нативный движок: если версия библиотеки изменится, править нужно только здесь.
 *
 * ВАЖНО: TessBaseAPI не потокобезопасен — один экземпляр на одно распознавание,
 * всегда на одном потоке (в проекте это Dispatchers.IO внутри конвертера).
 */
class OcrEngine(private val dataPath: String) : Closeable {

    private companion object {
        /** Значения PageSegMode из Tesseract: 3 — автоматическая сегментация. */
        const val PAGE_SEG_AUTO = 3

        /** 6 — страница как один блок текста (ровные PDF-документы). */
        const val PAGE_SEG_SINGLE_BLOCK = 6
    }

    private val api = TessBaseAPI()
    private var released = false

    /** Инициализация движка. Ошибка — понятная человеку [OcrInitException]. */
    fun start() {
        val started = try {
            api.init(dataPath, TessdataStore.LANGUAGE_SPEC)
        } catch (t: Throwable) {
            false
        }
        if (!started) {
            runCatching { api.recycle() }
            released = true
            throw OcrInitException()
        }
        configure()
    }

    /**
     * Настройки движка под наши документы.
     *
     * Tesseract по умолчанию схлопывает повторные пробелы и плохо понимает картинки
     * без указанного dpi. Параметры необязательные: если конкретная сборка их не знает,
     * распознавание всё равно работает — поэтому результат setVariable не проверяем.
     */
    private fun configure() {
        runCatching {
            api.setVariable("preserve_interword_spaces", "1")
            api.setVariable("user_defined_dpi", "300")
        }
    }

    /**
     * Распознаёт текст на битмапе. Движок можно переиспользовать для следующих страниц.
     *
     * [singleBlock] — режим для ровной страницы документа (текст идёт одним блоком).
     * Для фотографий и постеров оставляем автоматическую сегментацию: она сама находит
     * блоки текста в произвольной вёрстке.
     */
    fun recognize(bitmap: Bitmap, singleBlock: Boolean = false): String {
        val prepared = if (bitmap.config != Bitmap.Config.ARGB_8888) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
        } else {
            bitmap
        }
        return try {
            api.setPageSegMode(if (singleBlock) PAGE_SEG_SINGLE_BLOCK else PAGE_SEG_AUTO)
            api.setImage(prepared)
            api.getUTF8Text().orEmpty()
        } finally {
            if (prepared !== bitmap) prepared.recycle()
        }
    }

    override fun close() {
        if (released) return
        released = true
        runCatching { api.clear() }
        runCatching { api.recycle() }
    }
}
