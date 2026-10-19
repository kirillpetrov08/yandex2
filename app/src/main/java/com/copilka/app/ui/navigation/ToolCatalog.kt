package com.copilka.app.ui.navigation

import com.copilka.app.R
import com.copilka.app.engine.ToolKind
import com.copilka.app.ui.theme.AccentAmber
import com.copilka.app.ui.theme.AccentBlue
import com.copilka.app.ui.theme.AccentCoral
import com.copilka.app.ui.theme.AccentTeal

data class ToolUi(
    val kind: ToolKind,
    val titleRes: Int,
    val subtitleRes: Int,
    val iconRes: Int,
    val accent: androidx.compose.ui.graphics.Color,
    val available: Boolean = true,
)

object ToolCatalog {
    val all: List<ToolUi> = listOf(
        ToolUi(ToolKind.IMAGES, R.string.tool_images_title, R.string.tool_images_subtitle, R.drawable.ic_tool_image, AccentBlue),
        ToolUi(ToolKind.PDF_TO_IMAGES, R.string.tool_pdf_to_images_title, R.string.tool_pdf_to_images_subtitle, R.drawable.ic_tool_pdf, AccentTeal),
        ToolUi(ToolKind.IMAGES_TO_PDF, R.string.tool_images_to_pdf_title, R.string.tool_images_to_pdf_subtitle, R.drawable.ic_tool_images_to_pdf, AccentAmber),
        ToolUi(ToolKind.OCR, R.string.tool_ocr_title, R.string.tool_ocr_subtitle, R.drawable.ic_tool_ocr, AccentBlue),
        ToolUi(ToolKind.AUDIO, R.string.tool_audio_title, R.string.tool_audio_subtitle, R.drawable.ic_tool_audio, AccentCoral),
        ToolUi(ToolKind.VIDEO, R.string.tool_video_title, R.string.tool_video_subtitle, R.drawable.ic_tool_video, AccentTeal),
    )
    val quickStart: List<ToolUi> = all.take(3)
    fun byId(id: String): ToolUi? = all.find { it.kind.id == id }
}
