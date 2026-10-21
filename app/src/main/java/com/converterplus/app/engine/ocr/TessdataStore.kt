package com.converterplus.app.engine.ocr

import android.content.Context
import com.converterplus.app.engine.OcrDataException
import java.io.File
import java.io.FileOutputStream

/**
 * Языковые данные Tesseract (`rus`, `eng`) лежат в assets и при первом распознавании
 * раскладываются в приватный каталог приложения.
 *
 * Почему так, а не чтение прямо из APK: начиная с Android 10 (targetSdk ≥ 29) нативный движок
 * Tesseract не имеет права открывать файлы внутри APK — путь должен быть обычным файлом
 * в приватной директории. Копируется один раз, повторно — только если данных нет.
 */
object TessdataStore {

    private const val ROOT = "tesseract"
    private const val DATA_SUBDIR = "tessdata"
    private const val MARKER = ".ready"
    private const val MARKER_VERSION = "1"

    /** Языки, которые кладутся в сборку. */
    val LANGUAGES = listOf("rus", "eng")

    /** Строка языков для TessBaseAPI.init: «rus+eng» — русский с английским. */
    val LANGUAGE_SPEC: String = LANGUAGES.joinToString("+")

    /** Готовы ли данные (без копирования). */
    fun isReady(context: Context): Boolean {
        val root = File(context.filesDir, ROOT)
        val dataDir = File(root, DATA_SUBDIR)
        val marker = File(root, MARKER)
        if (!marker.isFile || marker.readText().trim() != MARKER_VERSION) return false
        return LANGUAGES.all { File(dataDir, "$it.traineddata").let { f -> f.isFile && f.length() > 0 } }
    }

    /**
     * Раскладывает языковые данные и возвращает путь, который передаётся в `TessBaseAPI.init`:
     * внутри этого каталога обязана лежать папка `tessdata`.
     */
    fun prepare(context: Context): File {
        val root = File(context.filesDir, ROOT)
        val dataDir = File(root, DATA_SUBDIR)
        val marker = File(root, MARKER)

        if (isReady(context)) return root

        if (!dataDir.exists() && !dataDir.mkdirs()) throw OcrDataException()
        val assets = context.assets
        LANGUAGES.forEach { language ->
            val assetName = "$DATA_SUBDIR/$language.traineddata"
            val target = File(dataDir, "$language.traineddata")
            val needsCopy = !target.isFile || target.length() == 0L
            if (needsCopy) {
                try {
                    assets.open(assetName).use { input ->
                        FileOutputStream(target).use { output -> input.copyTo(output, 64 * 1024) }
                    }
                } catch (t: Throwable) {
                    target.delete()
                    throw OcrDataException(t)
                }
            }
        }
        if (targetLength(dataDir) == 0L) throw OcrDataException()
        marker.writeText(MARKER_VERSION)
        return root
    }

    /** Сколько занимают языковые данные на устройстве. */
    fun sizeBytes(context: Context): Long = targetLength(File(File(context.filesDir, ROOT), DATA_SUBDIR))

    private fun targetLength(dataDir: File): Long =
        LANGUAGES.sumOf { language ->
            val file = File(dataDir, "$language.traineddata")
            if (file.isFile) file.length() else 0L
        }
}
