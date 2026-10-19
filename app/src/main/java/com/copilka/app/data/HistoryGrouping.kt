package com.copilka.app.data

/** Крупная группа истории: свежие записи отделены от старых. */
enum class HistoryBucket { TODAY, YESTERDAY, EARLIER }

/** Группа записей одного дня с сохранённым порядком. */
data class HistorySection(
    val bucket: HistoryBucket,
    val entries: List<HistoryEntry>,
)

/**
 * Группировка истории по датам. Чистая функция: «сейчас» передаётся снаружи,
 * поэтому расчёт проверяется тестами без привязки к системным часам.
 *
 * Границы считаются по календарным суткам в часовом поясе телефона: запись в 23:59
 * и запись в 00:01 следующего дня попадут в разные группы, хотя между ними две минуты.
 */
object HistoryGrouping {

    const val DAY_MS = 24L * 60L * 60L * 1000L

    /**
     * @param now текущее время в миллисекундах.
     * @param zoneOffsetMs смещение часового пояса от UTC в этот момент.
     */
    fun group(
        entries: List<HistoryEntry>,
        now: Long,
        zoneOffsetMs: Int = 0,
    ): List<HistorySection> {
        if (entries.isEmpty()) return emptyList()

        val todayStart = dayStart(now, zoneOffsetMs)
        val yesterdayStart = todayStart - DAY_MS

        val grouped = entries.groupBy { entry ->
            when {
                entry.timestamp >= todayStart -> HistoryBucket.TODAY
                entry.timestamp >= yesterdayStart -> HistoryBucket.YESTERDAY
                else -> HistoryBucket.EARLIER
            }
        }

        // Порядок групп фиксирован, даже если какой-то день пуст.
        return listOf(HistoryBucket.TODAY, HistoryBucket.YESTERDAY, HistoryBucket.EARLIER)
            .mapNotNull { bucket ->
                grouped[bucket]?.takeIf { it.isNotEmpty() }?.let { HistorySection(bucket, it) }
            }
    }

    /** Начало календарных суток, содержащих [time]. */
    fun dayStart(time: Long, zoneOffsetMs: Int = 0): Long {
        val shifted = time + zoneOffsetMs
        return shifted - Math.floorMod(shifted, DAY_MS) - zoneOffsetMs
    }
}
