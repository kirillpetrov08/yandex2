package com.copilka.app.engine.image

/**
 * Как вписывать картинку в выбранный размер.
 * Обе настройки сохраняют пропорции — искажений «сплющенного» фото не бывает.
 */
enum class FitMode {
    /** Вписать целиком: картинка помещается в рамку, ничего не обрезается, не увеличиваем. */
    FIT,

    /** Заполнить рамку: картинка закрывает весь кадр, лишние края обрезаются по центру. */
    CROP,
}

/** Группа шаблонов — по ней чипы в интерфейсе разложены под общими подписями. */
enum class SizeGroup { SCREEN, SOCIAL, PRINT }

/**
 * Готовые шаблоны размера результата в пикселях.
 *
 * Печатные размеры даны для 300 dpi: A4 — 210×297 мм, A5 — 148×210 мм.
 * `caption` нужен только там, где по числам не понять формат (A4/A5).
 */
enum class SizePreset(
    val id: String,
    val width: Int,
    val height: Int,
    val group: SizeGroup,
    val caption: String? = null,
) {
    HD("hd", 1280, 720, SizeGroup.SCREEN),
    FULL_HD("fhd", 1920, 1080, SizeGroup.SCREEN),
    QHD("qhd", 2560, 1440, SizeGroup.SCREEN),
    UHD("uhd", 3840, 2160, SizeGroup.SCREEN),

    SQUARE("square", 1080, 1080, SizeGroup.SOCIAL),
    PORTRAIT("portrait", 1080, 1350, SizeGroup.SOCIAL),
    STORY("story", 1080, 1920, SizeGroup.SOCIAL),
    LINK("link", 1200, 630, SizeGroup.SOCIAL),

    A4_300("a4_300", 2480, 3508, SizeGroup.PRINT, caption = "A4"),
    A5_300("a5_300", 1748, 2480, SizeGroup.PRINT, caption = "A5");

    val label: String
        get() = caption?.let { "$it · $width × $height" } ?: "$width × $height"

    companion object {
        fun byId(id: String?): SizePreset? = entries.firstOrNull { it.id == id }
    }
}

/** Размер в пикселях. */
data class ImageSize(val width: Int, val height: Int)

/**
 * Чистый расчёт итоговых размеров: без Android-зависимостей, покрыт юнит-тестами.
 * Именно эти функции решают, что реально произойдёт с картинкой, — интерфейс
 * показывает пользователю ровно тот же результат.
 */
object ImageSizing {

    /** Меньше 16 px по стороне результат не имеет смысла. */
    const val MIN_SIDE = 16

    /** Больше 8K по стороне не нужно: это уже десятки мегабайт на кадр. */
    const val MAX_SIDE = 8192

    /** Проверка ручного ввода: null — значение недопустимо. */
    fun normalize(width: Int?, height: Int?): ImageSize? {
        val w = width ?: return null
        val h = height ?: return null
        if (w < MIN_SIDE || h < MIN_SIDE) return null
        if (w > MAX_SIDE || h > MAX_SIDE) return null
        return ImageSize(w, h)
    }

    /**
     * Итоговый размер картинки.
     *
     * [FitMode.FIT] никогда не увеличивает изображение: маленькое фото останется своего размера,
     * иначе получилась бы каша из пикселей вместо качества.
     * [FitMode.CROP] всегда отдаёт ровно рамку — иначе «обрезка» теряла бы смысл.
     */
    fun resolve(source: ImageSize, target: ImageSize?, mode: FitMode): ImageSize {
        if (target == null || source.width <= 0 || source.height <= 0) return source
        return when (mode) {
            FitMode.FIT -> {
                val scale = minOf(
                    target.width.toDouble() / source.width,
                    target.height.toDouble() / source.height,
                )
                if (scale >= 1.0) {
                    source
                } else {
                    ImageSize(
                        width = (source.width * scale).toInt().coerceAtLeast(1),
                        height = (source.height * scale).toInt().coerceAtLeast(1),
                    )
                }
            }

            FitMode.CROP -> target
        }
    }

    /**
     * Во сколько раз уменьшится картинка по длинной стороне.
     * Для подписи «меньше в 2,1 раза»; 1.0 — размер не меняется или картинка растягивается.
     */
    fun shrinkRatio(source: ImageSize, result: ImageSize): Double {
        val sourceLong = maxOf(source.width, source.height).toDouble()
        val resultLong = maxOf(result.width, result.height).toDouble()
        if (sourceLong <= 0.0 || resultLong <= 0.0) return 1.0
        return sourceLong / resultLong
    }
}
