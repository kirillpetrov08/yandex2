package com.converterplus.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryGroupingTest {

    private val day = HistoryGrouping.DAY_MS

    private val noon = 1_700_000_000_000L

    private fun entry(id: String, timestamp: Long) = HistoryEntry(
        id = id,
        timestamp = timestamp,
        fileName = "$id.jpg",
        fromFormat = "PNG",
        toFormat = "JPG",
        sizeBytes = 1024,
        uri = null,
        status = HistoryStatus.READY,
        localPath = "/cache/$id.jpg",
    )

    @Test
    fun `empty history has no sections`() {
        assertTrue(HistoryGrouping.group(emptyList(), noon).isEmpty())
    }

    @Test
    fun `entries are split into today yesterday and earlier`() {
        val todayStart = HistoryGrouping.dayStart(noon)
        val sections = HistoryGrouping.group(
            entries = listOf(
                entry("today", todayStart),
                entry("yesterday", todayStart - day),
                entry("beforeYesterday", todayStart - day - 1),
            ),
            now = noon,
        )

        assertEquals(
            listOf(HistoryBucket.TODAY, HistoryBucket.YESTERDAY, HistoryBucket.EARLIER),
            sections.map { it.bucket },
        )
        assertEquals(listOf("today"), sections[0].entries.map { it.id })
        assertEquals(listOf("yesterday"), sections[1].entries.map { it.id })
        assertEquals(listOf("beforeYesterday"), sections[2].entries.map { it.id })
    }

    @Test
    fun `missing day is not shown as an empty section`() {
        val todayStart = HistoryGrouping.dayStart(noon)
        val sections = HistoryGrouping.group(
            entries = listOf(entry("today", todayStart), entry("old", todayStart - 5 * day)),
            now = noon,
        )
        assertEquals(listOf(HistoryBucket.TODAY, HistoryBucket.EARLIER), sections.map { it.bucket })
    }

    @Test
    fun `midnight splits days`() {
        val todayStart = HistoryGrouping.dayStart(noon)
        val sections = HistoryGrouping.group(
            entries = listOf(
                entry("late", todayStart - 1),
                entry("early", todayStart),
            ),
            now = todayStart + 600_000L,
        )
        assertEquals(HistoryBucket.YESTERDAY, sections[0].bucket)
        assertEquals(HistoryBucket.TODAY, sections[1].bucket)
    }

    @Test
    fun `grouping respects the local time zone`() {
        // 03:00 UTC 17-го числа в поясе +03:00 — это уже 06:00 тех же суток,
        // но сутки начинаются по местному времени, а не по UTC.
        val time = 1_700_031_600_000L
        val utcStart = HistoryGrouping.dayStart(time, zoneOffsetMs = 0)
        val localStart = HistoryGrouping.dayStart(time, zoneOffsetMs = 3 * 60 * 60 * 1000)
        assertEquals(3 * 60 * 60 * 1000L, utcStart - localStart)
    }

    @Test
    fun `zeros are handled`() {
        assertEquals(0L, HistoryGrouping.dayStart(0))
        assertEquals(0L, HistoryGrouping.dayStart(day - 1))
    }
}
