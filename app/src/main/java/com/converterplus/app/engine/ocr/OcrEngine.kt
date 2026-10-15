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
    }

    /** Распознаёт текст на битмапе. Движок можно переиспользовать для следующих страниц. */
    fun recognize(bitmap: Bitmap): String {
        val prepared = if (bitmap.config != Bitmap.Config.ARGB_8888) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
        } else {
            bitmap
        }
        return try {
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
