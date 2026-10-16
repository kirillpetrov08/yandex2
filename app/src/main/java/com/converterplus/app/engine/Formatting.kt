package com.converterplus.app.engine

import kotlin.math.abs

/**
 * Человекочитаемые числа. Чистые функции — покрыты юнит-тестами.
 * Русская типографика: десятичный разделитель — запятая, перед единицей измерения неразрывный пробел.
 */
object Formatting {

    private const val KB = 1024L
    private const val MB = 1024L * 1024L
    private const val GB = 1024L * 1024L * 1024L

    /** «512 Б», «1,5 КБ», «12,3 МБ», «2,1 ГБ». */
    fun formatBytes(bytes: Long): String {
        if (bytes < 0) return "0 Б"
        return when {
            bytes < KB -> "$bytes Б"
            bytes < MB -> decimal(bytes.toDouble() / KB) + " КБ"
            bytes < GB -> decimal(bytes.toDouble() / MB) + " МБ"
            else -> decimal(bytes.toDouble() / GB) + " ГБ"
        }
    }

    /**
     * Экономия в процентах. Отрицательное значение — файл вырос.
     * null, если размер до конвертации неизвестен.
     */
    fun savedPercent(before: Long, after: Long): Int? {
        if (before <= 0L) return null
        val percent = ((before - after).toDouble() / before.toDouble() * 100.0)
        return Math.round(percent).toInt()
    }

    /** «37 %» или «−12 %». */
    fun formatPercent(percent: Int): String = if (percent < 0) "−${abs(percent)} %" else "$percent %"

    /**
     * Экономия для карточки результата: «37 %».
     * Файл никогда не бывает нулевого размера, поэтому «100 %» не пишем — это выглядело бы
     * как ошибка округления, показываем «>99 %».
     */
    fun formatSaved(percent: Int): String = when {
        percent >= 100 -> ">99 %"
        percent < 0 -> formatPercent(percent)
        else -> "$percent %"
    }

    /** Насколько файл вырос: «на 435 %». */
    fun formatGrew(percent: Int): String = "на ${abs(percent)} %"

    /** «45 сек», «3 мин 20 сек», «1 ч 05 мин». */
    fun formatDuration(millis: Long): String {
        if (millis < 0) return "0 сек"
        val totalSeconds = millis / 1000L
        if (totalSeconds < 60) return "$totalSeconds сек"
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        if (minutes < 60) {
            return if (seconds == 0L) "$minutes мин" else "$minutes мин $seconds сек"
        }
        val hours = minutes / 60
        val restMinutes = minutes % 60
        return "$hours ч ${pad(restMinutes)} мин"
    }

    private fun pad(value: Long): String = if (value < 10) "0$value" else value.toString()

    /** Одна цифра после запятой, «,0» отбрасывается. */
    private fun decimal(value: Double): String {
        val rounded = Math.round(value * 10.0) / 10.0
        val intPart = rounded.toLong()
        return if (abs(rounded - intPart) < 0.05) {
            intPart.toString()
        } else {
            val fraction = Math.round((rounded - intPart) * 10.0).toInt().let { abs(it) }
            "$intPart,$fraction"
        }
    }
}
