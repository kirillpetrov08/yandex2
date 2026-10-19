package com.copilka.app.engine.finance

import org.junit.Assert.*
import org.junit.Test

class MoneyTest {
    @Test fun formatRub_zero() { assertEquals("0 ₽", Money(0).formatRub()) }
    @Test fun formatRub_kop() { assertEquals("1 234,56 ₽", Money(123456).formatRub()) }
    @Test fun formatRub_noKop() { assertEquals("1 000 ₽", Money(100000).formatRub()) }
    @Test fun formatRub_negative() { assertEquals("−500 ₽", Money(-50000).formatRub()) }
    @Test fun sum() { assertEquals(Money(300), Money(100) + Money(200)) }
    @Test fun minus() { assertEquals(Money(50), Money(200) - Money(150)) }
    @Test fun absolute() { assertEquals(Money(500), Money(-500).absolute) }
    @Test fun centsAccess() { assertEquals(12345L, Money(12345).cents) }
}

class FinanceCalculatorTest {
    private val now = 1_700_000_000_000L
    @Test fun totalBalance() {
        val tx = listOf(
            Transaction("1", TransactionType.EXPENSE, Money(10000), "food", "cash", createdAt = now),
            Transaction("2", TransactionType.INCOME, Money(50000), "salary", "card", createdAt = now),
        )
        assertEquals(Money(40000), FinanceCalculator.totalBalance(tx))
    }
    @Test fun budgetsProgress() {
        val (s, e) = FinanceCalculator.monthBounds(now)
        val tx = listOf(Transaction("1", TransactionType.EXPENSE, Money(5000), "food", "cash", createdAt = s+1000))
        val budgets = mapOf("food" to Money(10000))
        val res = FinanceCalculator.budgetsForMonth(tx, budgets, now)
        assertEquals(1, res.size)
        assertEquals(0.5f, res[0].progress, 0.01f)
    }
    @Test fun breakdown() {
        val tx = listOf(
            Transaction("1", TransactionType.EXPENSE, Money(1000), "food", "cash", createdAt = now),
            Transaction("2", TransactionType.EXPENSE, Money(2000), "food", "cash", createdAt = now),
            Transaction("3", TransactionType.INCOME, Money(5000), "salary", "card", createdAt = now),
        )
        val b = FinanceCalculator.breakdownByCategory(tx, CategoryKind.EXPENSE)
        assertEquals(Money(3000), b["food"])
    }
    @Test fun weekBounds_valid() {
        val (s,e) = FinanceCalculator.weekBounds(now)
        assertTrue(e > s)
    }
    @Test fun monthBounds_valid() {
        val (s,e) = FinanceCalculator.monthBounds(now)
        assertTrue(e > s)
    }
}

class FinanceTsvTest {
    @Test fun roundTrip() {
        val t = Transaction("id1", TransactionType.EXPENSE, Money(12345), "food", "cash", comment = "test", createdAt = 1000L)
        val line = com.copilka.app.data.FinanceTsv.serialize(t)
        val decoded = com.copilka.app.data.FinanceTsv.deserialize(line)
        assertNotNull(decoded)
        assertEquals(t.id, decoded!!.id)
        assertEquals(t.amount, decoded.amount)
        assertEquals(t.comment, decoded.comment)
    }
    @Test fun transferWithToAccount() {
        val t = Transaction("id2", TransactionType.TRANSFER, Money(5000), "food", "cash", toAccountId = "card", createdAt = 2000L)
        val line = com.copilka.app.data.FinanceTsv.serialize(t)
        val d = com.copilka.app.data.FinanceTsv.deserialize(line)!!
        assertEquals("card", d.toAccountId)
    }
}
