package com.converterplus.app.ui.navigation

import androidx.compose.ui.graphics.Color
import com.converterplus.app.R
import com.converterplus.app.engine.Capabilities
import com.converterplus.app.engine.ToolKind
import com.converterplus.app.ui.theme.AccentAmber
import com.converterplus.app.ui.theme.AccentBlue
import com.converterplus.app.ui.theme.AccentTeal

/** Описание инструмента для главного экрана. */
data class ToolDef(
    val kind: ToolKind,
    val titleRes: Int,
    val subtitleRes: Int,
    val iconRes: Int,
    val accent: Color,
) {
    val available: Boolean get() = Capabilities.isToolAvailable(kind)
}

/**
 * Каталог инструментов: лента «Быстрый старт» (первые три) и сетка со всеми.
 * Недоступные инструменты не прячутся, а помечаются «Скоро» (см. [Capabilities]).
 */
object ToolCatalog {

    val all: List<ToolDef> = listOf(
        ToolDef(ToolKind.IMAGES, R.string.tool_images_title, R.string.tool_images_subtitle, R.drawable.ic_tool_image, AccentBlue),
        ToolDef(ToolKind.PDF_TO_IMAGES, R.string.tool_pdf_to_images_title, R.string.tool_pdf_to_images_subtitle, R.drawable.ic_tool_pdf, AccentTeal),
        ToolDef(ToolKind.OCR, R.string.tool_ocr_title, R.string.tool_ocr_subtitle, R.drawable.ic_tool_ocr, AccentAmber),
        ToolDef(ToolKind.AUDIO, R.string.tool_audio_title, R.string.tool_audio_subtitle, R.drawable.ic_tool_audio, AccentTeal),
        ToolDef(ToolKind.VIDEO, R.string.tool_video_title, R.string.tool_video_subtitle, R.drawable.ic_tool_video, AccentBlue),
    )

    val quickStart: List<ToolDef> get() = all.take(3)

    fun byId(toolId: String): ToolDef? = all.firstOrNull { it.kind.id == toolId }
}

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val REMOVE_ADS = "remove_ads"

    const val TOOL = "tool/{toolId}"
    const val ARG_TOOL_ID = "toolId"

    fun tool(tool: ToolKind): String = "tool/${tool.id}"
}
