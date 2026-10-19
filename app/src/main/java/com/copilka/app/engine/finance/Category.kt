package com.copilka.app.engine.finance

import androidx.annotation.DrawableRes
import com.copilka.app.R

enum class CategoryKind { EXPENSE, INCOME }

data class Category(
    val id: String,
    val nameRes: Int,
    val kind: CategoryKind,
    @DrawableRes val iconRes: Int,
    val color: Long,
)

object Categories {
    val expense = listOf(
        Category("food", R.string.tool_images_title, CategoryKind.EXPENSE, R.drawable.ic_tool_image, 0xFF1B8C4AL),
        Category("transport", R.string.tool_pdf_to_images_title, CategoryKind.EXPENSE, R.drawable.ic_tool_pdf, 0xFF0E7A9BL),
        Category("housing", R.string.tool_images_to_pdf_title, CategoryKind.EXPENSE, R.drawable.ic_tool_images_to_pdf, 0xFF7A5C1EL),
        Category("subs", R.string.tool_ocr_title, CategoryKind.EXPENSE, R.drawable.ic_tool_ocr, 0xFF8B3A8CL),
        Category("fun", R.string.tool_audio_title, CategoryKind.EXPENSE, R.drawable.ic_tool_audio, 0xFFD94F30L),
        Category("health", R.string.tool_video_title, CategoryKind.EXPENSE, R.drawable.ic_tool_video, 0xFF1B8C7AL),
    )
    val income = listOf(
        Category("salary", R.string.finance_income, CategoryKind.INCOME, R.drawable.ic_add, 0xFF1B8C4AL),
        Category("other_income", R.string.finance_income, CategoryKind.INCOME, R.drawable.ic_add, 0xFF2E7BFFL),
    )
    val all get() = expense + income
    fun byId(id: String): Category? = all.find { it.id == id }
    fun defaultExpense(): Category = expense.first()
    fun defaultIncome(): Category = income.first()
}
