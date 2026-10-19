package com.copilka.app.engine.finance

import java.util.Calendar

object FinanceCalculator {
    fun totalBalance(transactions: List<Transaction>): Money {
        var r = 0L
        for (t in transactions) r += t.signedAmount.cents
        return Money(r)
    }

    fun totalByType(transactions: List<Transaction>, type: TransactionType): Money {
        var r = 0L
        for (t in transactions) if (t.type == type) r += t.amount.cents
        return Money(r)
    }

    fun spentByCategory(
        transactions: List<Transaction>,
        categoryId: String,
        monthStart: Long,
        monthEnd: Long,
    ): Money {
        var r = 0L
        for (t in transactions) {
            if (t.type != TransactionType.EXPENSE) continue
            if (t.categoryId != categoryId) continue
            if (t.createdAt !in monthStart until monthEnd) continue
            r += t.amount.cents
        }
        return Money(r)
    }

    fun filterByPeriod(transactions: List<Transaction>, start: Long, end: Long): List<Transaction> =
        transactions.filter { it.createdAt in start until end }

    fun monthBounds(now: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        return start to cal.timeInMillis
    }

    fun weekBounds(now: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        val start = cal.timeInMillis
        cal.add(Calendar.WEEK_OF_YEAR, 1)
        return start to cal.timeInMillis
    }

    fun budgetsForMonth(
        transactions: List<Transaction>,
        budgets: Map<String, Money>,
        now: Long,
    ): List<Budget> {
        val (start, end) = monthBounds(now)
        return budgets.map { (cat, limit) ->
            Budget(categoryId = cat, limit = limit, spent = spentByCategory(transactions, cat, start, end))
        }
    }

    fun breakdownByCategory(transactions: List<Transaction>, kind: CategoryKind): Map<String, Money> {
        val expected = if (kind == CategoryKind.EXPENSE) TransactionType.EXPENSE else TransactionType.INCOME
        val map = mutableMapOf<String, Long>()
        for (t in transactions) {
            if (t.type != expected) continue
            map[t.categoryId] = (map[t.categoryId] ?: 0L) + t.amount.cents
        }
        return map.mapValues { Money(it.value) }
    }
}
