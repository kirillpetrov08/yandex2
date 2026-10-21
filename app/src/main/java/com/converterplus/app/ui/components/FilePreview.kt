package com.converterplus.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.converterplus.app.R
import com.converterplus.app.engine.Formatting
import com.converterplus.app.engine.PickedFile
import com.converterplus.app.engine.ToolKind
import com.converterplus.app.engine.image.ImageConverter
import com.converterplus.app.engine.media.MediaProbe
import com.converterplus.app.engine.pdf.PdfToImages
import com.converterplus.app.ui.theme.AppDimens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Превью файла. Что показывать, решает расширение файла, а не инструмент:
 * картинка — миниатюра, PDF — первая страница, видео — первый кадр, TXT — начало текста,
 * аудио — карточка со значком (рисовать «настоящую» волну без декодирования всего файла нельзя,
 * а выдуманная картинка врала бы пользователю).
 *
 * Битмап грузится в уменьшенном виде — полноразмерный в память не берём.
 */
@Composable
fun FilePreview(
    file: PickedFile,
    tool: ToolKind,
    modifier: Modifier = Modifier,
) {
    val kind = remember(file.localPath, tool) { previewKindOf(file, tool) }

    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = file.localPath, key2 = kind) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val source = File(file.localPath)
                when (kind) {
                    PreviewKind.IMAGE -> ImageConverter.decodePreview(source, PREVIEW_SIDE)
                    PreviewKind.PDF -> PdfToImages.renderFirstPage(source, PREVIEW_SIDE)
                    PreviewKind.VIDEO -> MediaProbe.firstFrame(source, PREVIEW_SIDE)
                    else -> null
                }
            }.getOrNull()
        }
    }

    val text by produceState<String?>(initialValue = null, key1 = file.localPath, key2 = kind) {
        value = if (kind == PreviewKind.TEXT) {
            withContext(Dispatchers.IO) { readTextHead(File(file.localPath)) }
        } else {
            null
        }
    }

    val frame = modifier
        .fillMaxWidth()
        .clip(MaterialTheme.shapes.large)
        .background(MaterialTheme.colorScheme.surfaceVariant)

    val currentBitmap = bitmap
    val currentText = text
    when {
        kind == PreviewKind.TEXT && !currentText.isNullOrBlank() ->
            TextPreview(text = currentText.orEmpty(), modifier = frame)

        currentBitmap != null -> Box(
            modifier = frame.height(PREVIEW_HEIGHT),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = currentBitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.cd_file_preview),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(AppDimens.SpaceS),
            )
        }

        kind == PreviewKind.AUDIO -> InfoPreview(
            iconRes = R.drawable.ic_tool_audio,
            title = file.displayName,
            caption = stringResource(R.string.cd_audio_preview) + " · " + Formatting.formatBytes(file.sizeBytes),
            modifier = frame,
        )

        else -> InfoPreview(
            iconRes = toolIcon(tool),
            title = file.displayName,
            caption = Formatting.formatBytes(file.sizeBytes),
            modifier = frame,
        )
    }
}

/** Карточка-заглушка: значок, имя файла и размер — вместо пустой тёмной рамки. */
@Composable
private fun InfoPreview(
    iconRes: Int,
    title: String,
    caption: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .heightIn(min = PREVIEW_HEIGHT)
            .padding(AppDimens.SpaceL),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(AppDimens.SpaceM))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(AppDimens.SpaceXs))
        Text(
            text = caption,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Первые строки текстового файла — так видно, что именно получилось. */
@Composable
private fun TextPreview(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .heightIn(min = PREVIEW_HEIGHT)
            .padding(AppDimens.SpaceL),
        verticalArrangement = Arrangement.spacedBy(AppDimens.SpaceS),
    ) {
        Text(
            text = stringResource(R.string.tool_result_text_preview),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = TEXT_PREVIEW_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 24.dp),
        )
    }
}

/** Подпись под превью: имя файла и размер. */
@Composable
fun FileCaption(name: String, size: String, modifier: Modifier = Modifier) {
    Text(
        text = "$name · $size",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.fillMaxWidth(),
    )
}

private fun previewKindOf(file: PickedFile, tool: ToolKind): PreviewKind =
    when (file.extension.lowercase()) {
        "jpg", "jpeg", "png", "webp", "heic", "heif", "avif", "bmp" -> PreviewKind.IMAGE
        "pdf" -> PreviewKind.PDF
        "mp4" -> PreviewKind.VIDEO
        "wav", "m4a", "ogg" -> PreviewKind.AUDIO
        "txt" -> PreviewKind.TEXT
        else -> when (tool) {
            ToolKind.PDF_TO_IMAGES, ToolKind.IMAGES_TO_PDF, ToolKind.OCR -> PreviewKind.IMAGE
            ToolKind.VIDEO -> PreviewKind.VIDEO
            ToolKind.AUDIO -> PreviewKind.AUDIO
            ToolKind.IMAGES -> PreviewKind.IMAGE
        }
    }

private fun readTextHead(file: File, limit: Int = TEXT_PREVIEW_CHARS): String? {
    if (!file.exists() || file.length() == 0L) return null
    return try {
        file.bufferedReader(Charsets.UTF_8).use { reader ->
            val buffer = CharArray(limit)
            val read = reader.read(buffer)
            if (read <= 0) null else String(buffer, 0, read).trim()
        }
    } catch (e: Exception) {
        null
    }
}

private fun toolIcon(tool: ToolKind): Int = when (tool) {
    ToolKind.IMAGES -> R.drawable.ic_tool_image
    ToolKind.PDF_TO_IMAGES -> R.drawable.ic_tool_pdf
    ToolKind.IMAGES_TO_PDF -> R.drawable.ic_tool_images_to_pdf
    ToolKind.OCR -> R.drawable.ic_tool_ocr
    ToolKind.AUDIO -> R.drawable.ic_tool_audio
    ToolKind.VIDEO -> R.drawable.ic_tool_video
}

private enum class PreviewKind { IMAGE, PDF, VIDEO, AUDIO, TEXT }

private val PREVIEW_HEIGHT = 200.dp
private const val PREVIEW_SIDE = 900
private const val TEXT_PREVIEW_CHARS = 480
private const val TEXT_PREVIEW_LINES = 8
