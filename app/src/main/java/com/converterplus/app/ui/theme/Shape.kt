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

    /**
     * Единый шаг отступов: 4 / 8 / 12 / 16 / 24.
     * Все экраны используют только эти значения, поэтому поля выглядят одинаково.
     */
    val SpaceXs = 4.dp
    val SpaceS = 8.dp
    val SpaceM = 12.dp
    val SpaceL = 16.dp
    val SpaceXl = 24.dp

    /** Расстояние между крупными блоками одного экрана (заголовок, превью, настройки). */
    val BlockGap = 20.dp

    /** Внутренние поля карточек и строк. */
    val CardPadding = 16.dp
    val RowPadding = 12.dp

    /** Между подписью и значением в строке статистики. */
    val StatGap = 6.dp

    /** Минимальный отступ баннера от интерактивных элементов (требование РСЯ — не менее 12dp). */
    val BannerSafeGap = 16.dp
    val MinTouchTarget = 48.dp
}
