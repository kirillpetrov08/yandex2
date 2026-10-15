package com.converterplus.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.converterplus.app.R
import com.converterplus.app.engine.PickedFile
import com.converterplus.app.engine.ToolKind
import com.converterplus.app.engine.image.ImageConverter
import com.converterplus.app.engine.media.MediaProbe
import com.converterplus.app.engine.pdf.PdfToImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Превью выбранного файла: миниатюра изображения, первая страница PDF, первый кадр видео.
 * Для аудио показываем значок и длительность — рисовать «настоящую» волну без декодирования
 * всего файла нельзя, а выдуманная картинка врала бы пользователю.
 *
 * Битмап грузится в уменьшенном виде — полноразмерный в память не берём.
 */
@Composable
fun FilePreview(
    file: PickedFile,
    tool: ToolKind,
    modifier: Modifier = Modifier,
) {
    val path = file.localPath
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = path, key2 = tool) {
        value = withContext(Dispatchers.IO) {
            val source = File(path)
            when {
                tool == ToolKind.PDF_TO_IMAGES -> PdfToImages.renderFirstPage(source, PREVIEW_SIDE)
                tool == ToolKind.OCR && file.extension == "pdf" -> PdfToImages.renderFirstPage(source, PREVIEW_SIDE)
                tool == ToolKind.VIDEO -> MediaProbe.firstFrame(source, PREVIEW_SIDE)
                tool == ToolKind.AUDIO -> null
                else -> ImageConverter.decodePreview(source, PREVIEW_SIDE)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val current = bitmap
        when {
            current != null -> Image(
                bitmap = current.asImageBitmap(),
                contentDescription = stringResource(R.string.cd_file_preview),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )

            tool == ToolKind.AUDIO -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painter = painterResource(R.drawable.ic_tool_audio),
                    contentDescription = stringResource(R.string.cd_audio_preview),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = file.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }

            else -> Icon(
                painter = painterResource(id = toolIcon(tool)),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(48.dp),
            )
        }
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

/** Подпись под превью: имя файла и размер. */
@Composable
fun FileCaption(name: String, size: String, modifier: Modifier = Modifier) {
    Text(
        text = "$name · $size",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = modifier,
    )
}

private const val PREVIEW_SIDE = 900
