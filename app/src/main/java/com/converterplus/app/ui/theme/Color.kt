package com.converterplus.app.ui.theme

import androidx.compose.ui.graphics.Color

// --- Светлая тема ---
val BackgroundLight = Color(0xFFF8F9FB)
val SurfaceLight = Color(0xFFFFFFFF)
val TextPrimaryLight = Color(0xFF0F172A)
val TextSecondaryLight = Color(0xFF5B6472)

// --- Тёмная тема ---
val BackgroundDark = Color(0xFF0E1116)
val SurfaceDark = Color(0xFF161A21)
val TextPrimaryDark = Color(0xFFE8EAED)
val TextSecondaryDark = Color(0xFF9AA3AF)

// --- Акценты (общие для обеих тем) ---
val AccentBlue = Color(0xFF2E5BFF)
val AccentTeal = Color(0xFF19B5A5)
val AccentCoral = Color(0xFFFF6B6B)
val AccentAmber = Color(0xFFF5A524)

val SuccessGreen = Color(0xFF22A06B)
val DisabledFillLight = Color(0xFFEDEFF3)
val DisabledFillDark = Color(0xFF232830)
val DividerLight = Color(0xFFE7EAF0)
val DividerDark = Color(0xFF232830)

/** Градиенты карточек «Быстрого старта». */
val QuickStartGradients: List<List<Color>> = listOf(
    listOf(Color(0xFF2E5BFF), Color(0xFF5B8CFF)),
    listOf(Color(0xFF19B5A5), Color(0xFF4BD3C4)),
    listOf(Color(0xFFFF6B6B), Color(0xFFFF9A76)),
)
