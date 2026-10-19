package com.copilka.app.data

import android.content.Context
import com.copilka.app.engine.finance.Money
import com.copilka.app.engine.finance.RepeatPeriod
import com.copilka.app.engine.finance.Transaction
import com.copilka.app.engine.finance.TransactionType
import java.io.File
import java.util.UUID

/**
 * Хранилище операций. Формат TSV в приватной папке приложения, как HistoryStore в Конвертере+.
 * Строка: id, type, amountCents, categoryId, accountId, toAccountId, comment, receiptPath, createdAt, repeat
 * Без Room/KSP/Serialization — только чистые функции и атомарная запись.
 */
object FinanceTsv {
    fun serialize(t: Transaction): String = listOf(
        esc(t.id),
        esc(t.type.name),
        t.amount.cents.toString(),
        esc(t.categoryId),
        esc(t.accountId),
        esc(t.toAccountId ?: ""),
        esc(t.comment.replace("\n", " ").replace("\t", " ")),
        esc(t.receiptPath ?: ""),
        t.createdAt.toString(),
        esc(t.repeat.name)
    ).joinToString("\t")

    fun deserialize(line: String): Transaction? {
        if (line.isBlank()) return null
        val p = line.split("\t")
        if (p.size < 10) return null
        return try {
            Transaction(
                id = unesc(p[0]),
                type = TransactionType.valueOf(unesc(p[1])),
                amount = Money(p[2].toLong()),
                categoryId = unesc(p[3]),
                accountId = unesc(p[4]),
                toAccountId = unesc(p[5]).takeIf { it.isNotEmpty() },
                comment = unesc(p[6]),
                receiptPath = unesc(p[7]).takeIf { it.isNotEmpty() },
                createdAt = p[8].toLong(),
                repeat = runCatching { RepeatPeriod.valueOf(unesc(p[9])) }.getOrDefault(RepeatPeriod.NEVER)
            )
        } catch (_: Exception) { null }
    }

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")
    private fun unesc(s: String) = s.replace("\\t", "\t").replace("\\n", "\n").replace("\\\\", "\\")
}

class FinanceStore(private val context: Context) {
    private val file: File get() = File(context.filesDir, "transactions.tsv")
    private val lock = Any()

    fun loadAll(): List<Transaction> = synchronized(lock) {
        if (!file.exists()) return emptyList()
        file.readLines().mapNotNull { FinanceTsv.deserialize(it) }.sortedByDescending { it.createdAt }
    }

    fun saveAll(list: List<Transaction>) = synchronized(lock) {
        val tmp = File(context.filesDir, "transactions.tsv.tmp")
        tmp.writeText(list.joinToString("\n") { FinanceTsv.serialize(it) })
        if (file.exists()) file.delete()
        tmp.renameTo(file)
    }

    fun add(t: Transaction) {
        val cur = loadAll().toMutableList()
        cur.add(0, t)
        if (cur.size > 2000) cur.subList(2000, cur.size).clear()
        saveAll(cur)
    }

    fun update(id: String, updater: (Transaction) -> Transaction) {
        val cur = loadAll().toMutableList()
        val idx = cur.indexOfFirst { it.id == id }
        if (idx >= 0) { cur[idx] = updater(cur[idx]); saveAll(cur) }
    }

    fun delete(id: String) {
        val cur = loadAll().filterNot { it.id == id }
        saveAll(cur)
    }

    fun clear() = synchronized(lock) { if (file.exists()) file.delete() }

    companion object {
        fun default(context: Context) = FinanceStore(context.applicationContext)
        fun newId(): String = UUID.randomUUID().toString()
    }
}

object FinanceGrouping {
    fun group(transactions: List<Transaction>): Map<String, List<Transaction>> {
        val now = System.currentTimeMillis()
        val cal = java.util.Calendar.getInstance()
        val today = cal.apply { timeInMillis = now }.let { it.get(java.util.Calendar.YEAR) to it.get(java.util.Calendar.DAY_OF_YEAR) }
        return transactions.groupBy { t ->
            val c = java.util.Calendar.getInstance().apply { timeInMillis = t.createdAt }
            val y = c.get(java.util.Calendar.YEAR)
            val d = c.get(java.util.Calendar.DAY_OF_YEAR)
            when {
                y == today.first && d == today.second -> "today"
                else -> {
                    val yesterday = java.util.Calendar.getInstance().apply { timeInMillis = now; add(java.util.Calendar.DAY_OF_YEAR, -1) }
                    if (y == yesterday.get(java.util.Calendar.YEAR) && d == yesterday.get(java.util.Calendar.DAY_OF_YEAR)) "yesterday" else "earlier"
                }
            }
        }
    }
}
