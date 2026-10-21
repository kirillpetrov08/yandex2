package com.converterplus.app.ui.screens.history

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.converterplus.app.R
import com.converterplus.app.data.HistoryBucket
import com.converterplus.app.data.HistoryEntry
import com.converterplus.app.data.HistoryGrouping
import com.converterplus.app.data.HistoryStatus
import com.converterplus.app.engine.Formatting
import com.converterplus.app.storage.FileSaver
import com.converterplus.app.ui.components.EmptyState
import com.converterplus.app.ui.theme.AppDimens
import com.converterplus.app.ui.theme.SuccessGreen
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * История конвертаций: поиск, фильтр по типу, свайп влево — удалить, вправо — поделиться.
 * Баннер живёт в нижнем баре Scaffold и карточки не перекрывает.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onSaveRequest: (HistoryEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.refresh()
        viewModel.events.collect { event ->
            when (event) {
                is HistoryEvent.Snackbar ->
                    snackbarHostState.showSnackbar(context.getString(event.messageRes))

                is HistoryEvent.Share -> shareFile(context, event.path, event.name)
                is HistoryEvent.Save -> onSaveRequest(event.entry)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::search,
                placeholder = { Text(stringResource(R.string.history_search_hint)) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_search),
                        contentDescription = null,
                    )
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppDimens.ScreenPadding, vertical = AppDimens.SpaceM),
            )

            if (state.query.isNotBlank() || state.formatFilter != null) {
                Text(
                    text = stringResource(R.string.history_count, state.entries.size, state.totalCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = AppDimens.ScreenPadding),
                )
            }

            if (state.formats.isNotEmpty()) {
                // Перенос строк вместо горизонтальной прокрутки: крайние фильтры
                // больше не обрезаны краем экрана.
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppDimens.ScreenPadding),
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.SpaceS),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.SpaceS),
                ) {
                    FilterChip(
                        selected = state.formatFilter == null,
                        onClick = { viewModel.setFormatFilter(null) },
                        label = { Text(stringResource(R.string.history_filter_all)) },
                        shape = MaterialTheme.shapes.small,
                    )
                    state.formats.forEach { format ->
                        FilterChip(
                            selected = state.formatFilter == format,
                            onClick = {
                                viewModel.setFormatFilter(
                                    if (state.formatFilter == format) null else format,
                                )
                            },
                            label = { Text(format) },
                            shape = MaterialTheme.shapes.small,
                        )
                    }
                }
            }

            if (state.isEmpty) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    EmptyState(
                        iconRes = R.drawable.ic_empty_history,
                        title = stringResource(R.string.history_empty_title),
                        text = stringResource(R.string.history_empty_text),
                    )
                }
            } else {
                val sections = remember(state.entries) {
                    // Смещение пояса нужно для календарных суток: без него «Сегодня»
                    // начиналось бы по UTC и в Москве съезжало бы на 3 часа.
                    val offset = TimeZone.getDefault().getOffset(System.currentTimeMillis())
                    HistoryGrouping.group(state.entries, System.currentTimeMillis(), offset)
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        horizontal = AppDimens.ScreenPadding,
                        vertical = AppDimens.SpaceM,
                    ),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.SpaceM),
                ) {
                    sections.forEach { section ->
                        item(key = "header-${section.bucket.name}") {
                            Text(
                                text = stringResource(groupTitle(section.bucket)),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = AppDimens.SpaceXs, top = AppDimens.SpaceS),
                            )
                        }
                        itemsIndexed(section.entries, key = { _, entry -> entry.id }) { _, entry ->
                            SwipeableHistoryCard(
                                entry = entry,
                                onDelete = { viewModel.delete(entry) },
                                onShare = { viewModel.share(entry) },
                                onSave = { viewModel.save(entry) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeableHistoryCard(
    entry: HistoryEntry,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val threshold = with(density) { 110.dp.toPx() }
    var offset by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large),
    ) {
        // Подложка: слева «поделиться», справа «удалить».
        Row(
            modifier = Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_share),
                contentDescription = stringResource(R.string.history_share),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 18.dp),
            )
            Icon(
                painter = painterResource(R.drawable.ic_delete),
                contentDescription = stringResource(R.string.action_delete),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(end = 18.dp),
            )
        }

        HistoryCard(
            entry = entry,
            onSave = onSave,
            modifier = Modifier
                .offset { IntOffset(offset.toInt(), 0) }
                .pointerInput(entry.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            when {
                                offset < -threshold -> onDelete()
                                offset > threshold -> onShare()
                            }
                            offset = 0f
                        },
                        onDragCancel = { offset = 0f },
                    ) { change, amount ->
                        change.consume()
                        offset += amount
                    }
                },
        )
    }
}

@Composable
private fun HistoryCard(
    entry: HistoryEntry,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = AppDimens.CardElevation),
    ) {
        Row(
            modifier = Modifier.padding(AppDimens.CardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = iconFor(entry.toFormat)),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = AppDimens.SpaceM),
            ) {
                Text(
                    text = entry.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entry.formatPair,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${formatDate(entry.timestamp)} · ${Formatting.formatBytes(entry.sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StatusBadge(status = entry.status)
            }
            // Кэш результатов чистится через сутки: если файла уже нет,
            // кнопка «Сохранить» только вводила бы в заблуждение.
            val fileAvailable = remember(entry.localPath, entry.status) {
                entry.status != HistoryStatus.READY || entry.localPath?.let { File(it).exists() } == true
            }
            if (entry.status == HistoryStatus.READY && fileAvailable) {
                androidx.compose.material3.TextButton(
                    onClick = onSave,
                    modifier = Modifier.height(AppDimens.MinTouchTarget),
                ) {
                    Text(stringResource(R.string.action_save), maxLines = 1)
                }
            }
        }
    }
}

/** Компактный бейдж состояния: цвет и текст вместо служебной подписи. */
@Composable
private fun StatusBadge(status: HistoryStatus) {
    val (labelRes, color) = when (status) {
        HistoryStatus.READY -> R.string.history_status_ready to MaterialTheme.colorScheme.tertiary
        HistoryStatus.SAVED -> R.string.history_status_saved to SuccessGreen
        HistoryStatus.FAILED -> R.string.history_status_failed to MaterialTheme.colorScheme.error
    }
    val description = if (status == HistoryStatus.READY) {
        stringResource(R.string.history_status_ready_hint)
    } else {
        stringResource(labelRes)
    }
    Text(
        text = stringResource(labelRes),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .semantics { contentDescription = description }
            .padding(top = AppDimens.SpaceXs)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = AppDimens.SpaceS, vertical = 2.dp),
    )
}

private fun groupTitle(bucket: HistoryBucket): Int = when (bucket) {
    HistoryBucket.TODAY -> R.string.history_group_today
    HistoryBucket.YESTERDAY -> R.string.history_group_yesterday
    HistoryBucket.EARLIER -> R.string.history_group_earlier
}

private fun iconFor(format: String): Int = when (format.lowercase()) {
    "pdf" -> R.drawable.ic_tool_pdf
    "txt" -> R.drawable.ic_tool_ocr
    "wav", "m4a", "ogg" -> R.drawable.ic_tool_audio
    "mp4" -> R.drawable.ic_tool_video
    else -> R.drawable.ic_tool_image
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun shareFile(context: Context, path: String, name: String) {
    val file = File(path)
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = FileSaver.mimeTypeOf(name)
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, name)) }
}
