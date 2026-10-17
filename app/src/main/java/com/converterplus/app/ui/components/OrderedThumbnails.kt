package com.converterplus.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.converterplus.app.R
import com.converterplus.app.ui.theme.AppDimens
import com.converterplus.app.engine.Formatting
import com.converterplus.app.engine.PickedFile
import com.converterplus.app.engine.image.ImageConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Порядок страниц в «Фото → PDF»: перетаскивание долгим нажатием плюс кнопки-стрелки
 * (стрелки нужны для доступности и точной настройки порядка).
 */
@Composable
fun OrderedThumbnails(
    items: List<PickedFile>,
    onReorder: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowHeight = 64.dp
    val rowHeightPx = with(LocalDensity.current) { rowHeight.toPx() }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppDimens.SpaceS),
    ) {
        itemsIndexed(items, key = { _, item -> item.localPath }) { index, item ->
            val translation = when {
                draggedIndex == index -> dragOffset
                draggedIndex == index + 1 && dragOffset > rowHeightPx / 2 -> -rowHeightPx
                draggedIndex == index - 1 && dragOffset < -rowHeightPx / 2 -> rowHeightPx
                else -> 0f
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .graphicsLayer { translationY = translation }
                    .pointerInput(index) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggedIndex = index
                                dragOffset = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                                if (dragOffset > rowHeightPx / 2 && index < items.lastIndex) {
                                    onReorder(index, index + 1)
                                    draggedIndex = index + 1
                                    dragOffset -= rowHeightPx
                                } else if (dragOffset < -rowHeightPx / 2 && index > 0) {
                                    onReorder(index, index - 1)
                                    draggedIndex = index - 1
                                    dragOffset += rowHeightPx
                                }
                            },
                            onDragEnd = {
                                draggedIndex = -1
                                dragOffset = 0f
                            },
                            onDragCancel = {
                                draggedIndex = -1
                                dragOffset = 0f
                            },
                        )
                    }
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surface),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Thumb(path = item.localPath)
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp),
                )
                Text(
                    text = Formatting.formatBytes(item.sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(
                    onClick = { onReorder(index, index - 1) },
                    enabled = index > 0,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_up),
                        contentDescription = stringResource(R.string.action_move_up),
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(
                    onClick = { onReorder(index, index + 1) },
                    enabled = index < items.lastIndex,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_down),
                        contentDescription = stringResource(R.string.action_move_down),
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(
                    onClick = { onRemove(index) },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.cd_remove_file),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Thumb(path: String) {
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = path) {
        value = withContext(Dispatchers.IO) { ImageConverter.decodePreview(File(path), 160) }
    }
    Box(
        modifier = Modifier
            .size(48.dp)
            .padding(6.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.width(36.dp),
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_tool_image),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
