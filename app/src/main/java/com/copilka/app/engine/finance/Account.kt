package com.copilka.app.engine.finance

import androidx.annotation.DrawableRes
import com.copilka.app.R

data class Account(
    val id: String,
    val nameRes: Int,
    @DrawableRes val iconRes: Int,
)

object Accounts {
    val all = listOf(
        Account("cash", R.string.finance_cash, R.drawable.ic_folder_open),
        Account("card", R.string.finance_card, R.drawable.ic_folder_open),
        Account("savings", R.string.finance_savings, R.drawable.ic_folder_open),
    )
    fun byId(id: String) = all.find { it.id == id } ?: all.first()
    fun default() = all.first()
}
