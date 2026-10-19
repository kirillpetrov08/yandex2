package com.copilka.app.engine.finance

import kotlin.math.absoluteValue

/** Деньги храним в копейках как Long, чтобы не ловить ошибки float. */
@JvmInline
value class Money(val cents: Long) : Comparable<Money> {
    operator fun plus(other: Money) = Money(cents + other.cents)
    operator fun minus(other: Money) = Money(cents - other.cents)
    operator fun times(factor: Double) = Money((cents * factor).toLong())
    operator fun unaryMinus() = Money(-cents)
    override fun compareTo(other: Money) = cents.compareTo(other.cents)
    val isNegative: Boolean get() = cents < 0
    val isZero: Boolean get() = cents == 0L
    val absolute: Money get() = Money(cents.absoluteValue)
    fun toDoubleRub(): Double = cents / 100.0
}

fun Long.toMoneyCents(): Money = Money(this)

fun Money.formatRub(): String {
    val sign = if (cents < 0) "−" else ""
    val a = cents.absoluteValue
    val rub = a / 100
    val kop = a % 100
    val rubStr = rub.toString().reversed().chunked(3).joinToString(" ").reversed()
    return if (kop == 0L) "$sign$rubStr ₽" else "$sign$rubStr,${kop.toString().padStart(2, '0')} ₽"
}

fun Money.formatCompact(): String {
    val a = cents.absoluteValue
    return when {
        a >= 1_000_000_00 -> String.format("%.1f млн ₽", a / 1_000_000_00.0)
        a >= 100_000_00 -> String.format("%.0f тыс ₽", a / 100_000.0)
        else -> formatRub()
    }
}
