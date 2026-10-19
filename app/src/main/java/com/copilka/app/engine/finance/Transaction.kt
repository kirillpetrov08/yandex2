package com.copilka.app.engine.finance

enum class TransactionType { EXPENSE, INCOME, TRANSFER }
enum class RepeatPeriod { NEVER, DAILY, WEEKLY, MONTHLY }

data class Transaction(
    val id: String,
    val type: TransactionType,
    val amount: Money, // всегда положительная, знак определяется type
    val categoryId: String,
    val accountId: String,
    val toAccountId: String? = null, // для перевода
    val comment: String = "",
    val receiptPath: String? = null,
    val createdAt: Long, // epoch millis
    val repeat: RepeatPeriod = RepeatPeriod.NEVER,
) {
    val isExpense: Boolean get() = type == TransactionType.EXPENSE
    val isIncome: Boolean get() = type == TransactionType.INCOME
    val signedAmount: Money
        get() = when (type) {
            TransactionType.EXPENSE -> -amount
            TransactionType.INCOME -> amount
            TransactionType.TRANSFER -> Money(0)
        }
}

data class Budget(
    val categoryId: String,
    val limit: Money,
    val spent: Money = Money(0),
) {
    val left: Money get() = limit - spent
    val isOver: Boolean get() = spent.cents > limit.cents
    val progress: Float get() = if (limit.isZero) 0f else (spent.cents.toFloat() / limit.cents.toFloat()).coerceIn(0f, 1f)
}
