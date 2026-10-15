package com.converterplus.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Скругления дизайн-системы: карточки 20dp, кнопки 16dp, чипы 14dp, верх bottom sheet 28dp. */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

object AppDimens {
    val CardCorner = 20.dp
    val ButtonCorner = 16.dp
    val ChipCorner = 14.dp
    val SheetCorner = 28.dp
    val ScreenPadding = 16.dp
    val CardElevation = 3.dp

    /** Минимальный отступ баннера от интерактивных элементов (требование РСЯ — не менее 12dp). */
    val BannerSafeGap = 16.dp
    val MinTouchTarget = 48.dp
}
