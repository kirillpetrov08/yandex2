package com.converterplus.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class HistoryTsvTest {

    private fun entry(
        id: String = "id-1",
        timestamp: Long = 1_700_000_000_000L,
        name: String = "photo.png",
        from: String = "jpg",
        to: String = "png",
        size: Long = 2048,
        uri: String? = null,
        status: HistoryStatus = HistoryStatus.READY,
        localPath: String? = "/cache/photo.png",
    ) = HistoryEntry(id, timestamp, name, from, to, size, uri, status, localPath)

    @Test
    fun `encode and decode roundtrip`() {
        val original = entry(uri = "content://downloads/1", status = HistoryStatus.SAVED)
        val decoded = HistoryTsv.decode(HistoryTsv.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun `null fields survive roundtrip`() {
        val original = entry(uri = null, localPath = null, status = HistoryStatus.FAILED)
        val decoded = HistoryTsv.decode(HistoryTsv.encode(original))
        assertEquals(original, decoded)
        assertNull(decoded?.uri)
        assertNull(decoded?.localPath)
    }

    @Test
    fun `tabs and newlines in file name do not break parsing`() {
        val tricky = entry(name = "my\tweird\nname.png")
        val decoded = HistoryTsv.decode(HistoryTsv.encode(tricky))
        assertEquals("my weird name.png", decoded?.fileName)
    }

    @Test
    fun `malformed lines are ignored`() {
        assertNull(HistoryTsv.decode(""))
        assertNull(HistoryTsv.decode("only\ttwo"))
        assertNull(HistoryTsv.decode("id\tnot-a-number\tname\tjpg\tpng\t1\t-\tREADY\t-"))
    }

    @Test
    fun `list roundtrip keeps order`() {
        val entries = listOf(
            entry(id = "a", timestamp = 3),
            entry(id = "b", timestamp = 2),
            entry(id = "c", timestamp = 1),
        )
        val decoded = HistoryTsv.decodeAll(HistoryTsv.encodeAll(entries))
        assertEquals(entries.map { it.id }, decoded.map { it.id })
    }

    @Test
    fun `limit of 500 entries is enforced`() {
        val many = (1..600).map { entry(id = "id-$it", timestamp = it.toLong()) }
        val limited = HistoryTsv.enforceLimit(many)
        assertEquals(500, limited.size)
        assertEquals(600L, limited.first().timestamp)
        assertEquals(101L, limited.last().timestamp)
    }

    @Test
    fun `entries are sorted newest first`() {
        val entries = listOf(
            entry(id = "old", timestamp = 10),
            entry(id = "new", timestamp = 30),
            entry(id = "mid", timestamp = 20),
        )
        assertEquals(
            listOf("new", "mid", "old"),
            HistoryTsv.enforceLimit(entries).map { it.id },
        )
    }

    @Test
    fun `search by file name is case insensitive`() {
        val entries = listOf(
            entry(id = "1", name = "Отчёт.pdf"),
            entry(id = "2", name = "photo.png"),
        )
        assertEquals(1, HistoryTsv.filter(entries, "отч", null).size)
        assertEquals(1, HistoryTsv.filter(entries, "PHOTO", null).size)
        assertEquals(2, HistoryTsv.filter(entries, "", null).size)
    }

    @Test
    fun `filter by output format`() {
        val entries = listOf(
            entry(id = "1", to = "png"),
            entry(id = "2", to = "pdf"),
        )
        assertEquals(listOf("2"), HistoryTsv.filter(entries, "", "PDF").map { it.id })
        assertEquals(2, HistoryTsv.filter(entries, "", null).size)
    }

    @Test
    fun `available formats are distinct and uppercase`() {
        val entries = listOf(entry(to = "png"), entry(to = "png"), entry(to = "pdf"))
        assertEquals(listOf("PDF", "PNG"), HistoryTsv.availableFormats(entries))
    }

    @Test
    fun `format pair is readable`() {
        assertEquals("JPG → PNG", entry(from = "jpg", to = "png").formatPair)
    }
}

class HistoryStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `store writes and reads back`() {
        val store = HistoryStore(File(folder.root, "history.tsv"))
        assertTrue(store.load().isEmpty())

        val entry = HistoryEntry(
            id = "req-1-0",
            timestamp = 1000,
            fileName = "photo.png",
            fromFormat = "jpg",
            toFormat = "png",
            sizeBytes = 10,
            uri = null,
            status = HistoryStatus.READY,
            localPath = "/tmp/photo.png",
        )
        store.add(entry)

        val loaded = store.load()
        assertEquals(1, loaded.size)
        assertEquals(entry, loaded.first())
    }

    @Test
    fun `update replaces entry by id`() {
        val file = File(folder.root, "history.tsv")
        val store = HistoryStore(file)
        val entry = HistoryEntry("a", 1, "f.png", "jpg", "png", 1, null, HistoryStatus.READY, "/tmp/f.png")
        store.add(entry)
        store.update(entry.copy(status = HistoryStatus.SAVED, uri = "content://x"))

        val loaded = store.load()
        assertEquals(1, loaded.size)
        assertEquals(HistoryStatus.SAVED, loaded.first().status)
        assertEquals("content://x", loaded.first().uri)
    }

    @Test
    fun `delete removes entry`() {
        val store = HistoryStore(File(folder.root, "history.tsv"))
        store.add(HistoryEntry("a", 1, "a.png", "jpg", "png", 1, null, HistoryStatus.READY, null))
        store.add(HistoryEntry("b", 2, "b.png", "jpg", "png", 1, null, HistoryStatus.READY, null))
        store.delete("a")

        assertEquals(listOf("b"), store.load().map { it.id })
    }

    @Test
    fun `no temporary file is left behind`() {
        val store = HistoryStore(File(folder.root, "history.tsv"))
        store.add(HistoryEntry("a", 1, "a.png", "jpg", "png", 1, null, HistoryStatus.READY, null))
        val names = folder.root.list()?.toList().orEmpty()
        assertFalse(names.contains("history.tsv.tmp"))
        assertTrue(names.contains("history.tsv"))
    }

    @Test
    fun `limit is applied on write`() {
        val store = HistoryStore(File(folder.root, "history.tsv"))
        val entries = (1..520).map {
            HistoryEntry("id-$it", it.toLong(), "f$it.png", "jpg", "png", 1, null, HistoryStatus.READY, null)
        }
        store.addAll(entries)
        assertEquals(HistoryTsv.MAX_ENTRIES, store.load().size)
    }
}
