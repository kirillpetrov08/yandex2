package com.converterplus.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import com.converterplus.app.R
import com.converterplus.app.engine.PickedFile
import com.converterplus.app.engine.ToolKind
import com.converterplus.app.engine.image.ImageConverter
import com.converterplus.app.engine.pdf.PdfToImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Превью выбранного файла: миниатюра изображения или первая страница PDF.
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
            if (tool == ToolKind.PDF_TO_IMAGES) {
                PdfToImages.renderFirstPage(File(path), 900)
            } else {
                ImageConverter.decodePreview(File(path), 900)
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
        if (current != null) {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = stringResource(R.string.cd_file_preview),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                painter = painterResource(id = R.drawable.ic_tool_image),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
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
