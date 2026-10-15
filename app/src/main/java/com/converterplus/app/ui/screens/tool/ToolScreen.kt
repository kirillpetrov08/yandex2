package com.converterplus.app.ui.screens.tool

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.converterplus.app.R
import com.converterplus.app.ads.Ads
import com.converterplus.app.ads.BannerSlot
import com.converterplus.app.ads.RewardedOutcome
import com.converterplus.app.engine.Capabilities
import com.converterplus.app.engine.ConversionRequest
import com.converterplus.app.engine.Formatting
import com.converterplus.app.engine.OutputArtifact
import com.converterplus.app.engine.PdfPageSize
import com.converterplus.app.engine.PickedFile
import com.converterplus.app.engine.QualityPreset
import com.converterplus.app.engine.ToolKind
import com.converterplus.app.storage.FileSaver
import com.converterplus.app.ui.components.AppBottomSheet
import com.converterplus.app.ui.components.CircleProgress
import com.converterplus.app.ui.components.EmptyState
import com.converterplus.app.ui.components.FilePreview
import com.converterplus.app.ui.components.FormatChipRow
import com.converterplus.app.ui.components.OrderedThumbnails
import com.converterplus.app.ui.components.SectionHeader
import com.converterplus.app.ui.components.StatRow
import com.converterplus.app.ui.components.pressable
import com.converterplus.app.ui.components.rememberPressableSource
import com.converterplus.app.ui.navigation.ToolCatalog
import com.converterplus.app.ui.theme.AppDimens
import com.converterplus.app.ui.theme.QuickStartGradients
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Экран инструмента: выбор файла → настройка → процесс → результат.
 * Реклама: баннер внизу (скрывается во время конвертации и на шите результата),
 * межстраничная — только после закрытия шита и по решению AdPolicy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolScreen(
    tool: ToolKind,
    viewModel: ToolViewModel,
    adsAllowed: Boolean,
    onBack: () -> Unit,
    requestNotificationPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val toolDef = remember(tool) { ToolCatalog.byId(tool.id) }
    val policy = remember { Ads.policy(context) }

    var pendingReward by remember { mutableStateOf<RewardedReason?>(null) }
    var pendingArtifact by remember { mutableStateOf<OutputArtifact?>(null) }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let { viewModel.onFilesPicked(listOf(it)) } }

    val pickMultipleImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(Capabilities.MAX_FILES_PER_CONVERSION),
    ) { uris -> viewModel.onFilesPicked(uris) }

    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.onFilesPicked(listOf(it)) } }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        pendingArtifact?.let { artifact -> viewModel.onDocumentCreated(uri, artifact) }
        pendingArtifact = null
    }

    val rewardCallback: (RewardedOutcome) -> Unit = { outcome ->
        val reason = pendingReward
        pendingReward = null
        when (outcome) {
            RewardedOutcome.REWARDED -> reason?.let(viewModel::onRewardedGranted)
            RewardedOutcome.NOT_REWARDED -> scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.remove_ads_no_reward))
            }

            RewardedOutcome.FAILED -> scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.remove_ads_unavailable))
            }
        }
    }

    /** «Из галереи» — только картинки. Есть не у всех инструментов (аудио и видео галерея не покажет). */
    fun openGalleryPicker() {
        val imageOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        if (tool == ToolKind.IMAGES_TO_PDF) {
            pickMultipleImages.launch(imageOnly)
        } else {
            pickImage.launch(imageOnly)
        }
    }

    /** «Из файлов» — системный выбор документов с нужными типами. */
    fun openFilesPicker() {
        openDocument.launch(Capabilities.mimeTypesFor(tool))
    }

    /** Основное действие зоны выбора: сначала пробуем галерею, если она уместна. */
    fun openPicker() {
        val galleryAvailable = Capabilities.hasGallerySource(tool) &&
            ActivityResultContracts.PickVisualMedia.isAvailable(context)
        if (galleryAvailable) openGalleryPicker() else openFilesPicker()
    }

    fun requestRewarded(reason: RewardedReason) {
        pendingReward = reason
        Ads.maybePreloadRewarded(context)
        val activity = context as? Activity
        val shown = activity?.let { Ads.rewarded().showIfReady(it, rewardCallback) } ?: false
        if (!shown) {
            pendingReward = null
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.remove_ads_unavailable))
            }
        }
    }

    LaunchedEffect(Unit) {
        Ads.maybePreloadInterstitial(context)
        viewModel.events.collect { event ->
            when (event) {
                is ToolEvent.Snackbar ->
                    snackbarHostState.showSnackbar(context.getString(event.messageRes))

                is ToolEvent.Share -> shareFile(context, event.path, event.name)

                is ToolEvent.CreateDocument -> {
                    pendingArtifact = event.artifact
                    createDocument.launch(event.artifact.name)
                }

                is ToolEvent.OpenSaved -> openSavedLocation(context, event.uri, event.isFolder)

                is ToolEvent.ShowRewarded -> requestRewarded(event.reason)

                ToolEvent.SuccessHaptic ->
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
            }
        }
    }

    val bannerVisible = adsAllowed && !state.isRunning && state.result == null

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(id = toolDef?.titleRes ?: R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (!state.available) {
                    SoonContent()
                } else {
                    ToolContent(
                        state = state,
                        viewModel = viewModel,
                        onPickFile = { openPicker() },
                        onPickFromGallery = { openGalleryPicker() },
                        onPickFromFiles = { openFilesPicker() },
                        onWatchRewarded = { reason -> requestRewarded(reason) },
                        onDisabledFormatClick = { reasonRes ->
                            scope.launch {
                                snackbarHostState.showSnackbar(context.getString(reasonRes))
                            }
                        },
                    )
                }
            }

            if (state.available) {
                ConvertBar(
                    state = state,
                    onConvert = {
                        requestNotificationPermission()
                        viewModel.startConversion()
                    },
                    onRetry = viewModel::retryAfterError,
                    onCancel = viewModel::cancelConversion,
                )
            }

            if (bannerVisible) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                BannerSlot(visible = true)
            }
        }
    }

    // -------------------------------------------------------------- диалоги

    if (state.showSameFormatDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDialogs,
            title = { Text(stringResource(R.string.dialog_same_format_title)) },
            text = { Text(stringResource(R.string.dialog_same_format_text)) },
            confirmButton = {
                TextButton(onClick = viewModel::convertAnyway) {
                    Text(stringResource(R.string.action_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDialogs) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (state.showBigFileDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDialogs,
            title = { Text(stringResource(R.string.dialog_big_file_title)) },
            text = { Text(stringResource(R.string.dialog_big_file_text)) },
            confirmButton = {
                TextButton(onClick = viewModel::convertWithoutReward) {
                    Text(stringResource(R.string.action_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDialogs) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (state.showHugeFileDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDialogs,
            title = { Text(stringResource(R.string.remove_ads_title)) },
            text = { Text(stringResource(R.string.remove_ads_text)) },
            confirmButton = {
                TextButton(onClick = { requestRewarded(RewardedReason.HUGE_FILE) }) {
                    Text(stringResource(R.string.action_watch_short_video))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::convertWithoutReward) {
                    Text(stringResource(R.string.action_continue))
                }
            },
        )
    }

    // -------------------------------------------------------------- результат

    val result = state.result
    if (result != null) {
        AppBottomSheet(
            onDismissRequest = {
                viewModel.closeResult()
                maybeShowInterstitial(context, policy, onFinished = {
                    Ads.maybePreloadInterstitial(context)
                })
            },
        ) {
            ResultSheetContent(
                result = result,
                withWatermark = state.watermark && Capabilities.supportsWatermark(state.tool),
                onSave = viewModel::saveResult,
                onShare = viewModel::shareResult,
                onOpen = viewModel::openSavedLocation,
                onRemoveWatermark = { requestRewarded(RewardedReason.WATERMARK) },
                onNewConversion = viewModel::resetForNewConversion,
            )
        }
    }
}

/** Показ межстраничной после закрытия шита результата — только если политика разрешает. */
private fun maybeShowInterstitial(
    context: Context,
    policy: com.converterplus.app.ads.AdPolicy,
    onFinished: () -> Unit,
) {
    if (!Ads.isInitialized.value) return
    if (!policy.canShowInterstitial()) return
    val activity = context as? Activity ?: return
    val shown = Ads.interstitial().showIfReady(activity) {
        policy.noteInterstitialShown()
        onFinished()
    }
    if (!shown) onFinished()
}

@Composable
private fun ToolContent(
    state: ToolUiState,
    viewModel: ToolViewModel,
    onPickFile: () -> Unit,
    onPickFromGallery: () -> Unit,
    onPickFromFiles: () -> Unit,
    onWatchRewarded: (RewardedReason) -> Unit,
    onDisabledFormatClick: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppDimens.ScreenPadding)
            .padding(bottom = AppDimens.BannerSafeGap),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.inputs.isEmpty()) {
            PickZone(onClick = onPickFile, busy = state.busy, tool = state.tool)
            if (!state.busy) {
                SourceButtons(
                    tool = state.tool,
                    onGallery = onPickFromGallery,
                    onFiles = onPickFromFiles,
                )
            }
            RecentsBlock(
                tool = state.tool,
                onPicked = { uri -> viewModel.onFilesPicked(listOf(Uri.parse(uri))) },
            )
        } else {
            SelectedFilesBlock(state = state, viewModel = viewModel, onPickFile = onPickFile)
        }

        if (state.formats.isNotEmpty()) {
            Column {
                SectionHeader(text = stringResource(R.string.tool_format))
                FormatChipRow(
                    options = state.formats,
                    selectedId = state.selectedFormat,
                    onSelect = viewModel::selectFormat,
                    onDisabledClick = onDisabledFormatClick,
                )
                state.formats.firstOrNull { it.id == state.selectedFormat && !it.enabled }
                    ?.disabledReasonRes
                    ?.let { reason ->
                        Text(
                            text = stringResource(id = reason),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
            }
        }

        if (Capabilities.supportsPageSelection(state.tool)) {
            PagesSection(
                state = state,
                viewModel = viewModel,
                titleRes = if (state.tool == ToolKind.OCR) R.string.tool_pages_ocr else R.string.tool_pages,
            )
        }

        if (state.tool == ToolKind.IMAGES_TO_PDF) {
            PageSizeSection(state = state, viewModel = viewModel)
        }

        if (state.tool == ToolKind.OCR) {
            OcrPrecisionSection(state = state, onWatch = { onWatchRewarded(RewardedReason.OCR_PRECISION) })
        }

        if (Capabilities.hasQualityChoice(state.tool)) {
            QualitySection(state = state, viewModel = viewModel)
        }
    }
}

/**
 * Две маленькие кнопки под зоной выбора: «Из галереи» и «Из файлов».
 * Для PDF, аудио и видео галерея бессмысленна — остаётся только системный выбор файлов.
 */
@Composable
private fun SourceButtons(
    tool: ToolKind,
    onGallery: () -> Unit,
    onFiles: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (Capabilities.hasGallerySource(tool)) {
            OutlinedButton(onClick = onGallery, shape = MaterialTheme.shapes.small) {
                Icon(
                    painter = painterResource(R.drawable.ic_tool_image),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.action_from_gallery))
            }
        }
        OutlinedButton(onClick = onFiles, shape = MaterialTheme.shapes.small) {
            Icon(
                painter = painterResource(R.drawable.ic_folder_open),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.action_from_files))
        }
    }
}

@Composable
private fun PickZone(onClick: () -> Unit, busy: Boolean, tool: ToolKind) {
    val interaction = rememberPressableSource()
    val gradient = remember(tool) {
        val colors = QuickStartGradients.first()
        Brush.linearGradient(listOf(colors.first().copy(alpha = 0.10f), colors.last().copy(alpha = 0.04f)))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .pressable(interaction)
            .clip(MaterialTheme.shapes.large)
            .background(gradient)
            .border(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.large,
            )
            .clickable(interactionSource = interaction, indication = null) { if (!busy) onClick() },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (busy) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        } else {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.tool_pick_file_hint),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(
                    id = if (tool == ToolKind.PDF_TO_IMAGES) R.string.action_from_files
                    else R.string.action_from_gallery,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecentsBlock(tool: ToolKind, onPicked: (String) -> Unit) {
    val recents by com.converterplus.app.data.UriAccess.recents
        .collectAsStateWithLifecycle()
    if (recents.isEmpty()) return

    Column {
        SectionHeader(text = stringResource(R.string.tool_recent_files))
        recents.forEach { recent ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { onPicked(recent.uri) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(
                        id = if (tool == ToolKind.PDF_TO_IMAGES) R.drawable.ic_tool_pdf
                        else R.drawable.ic_tool_image,
                    ),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    text = recent.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SelectedFilesBlock(
    state: ToolUiState,
    viewModel: ToolViewModel,
    onPickFile: () -> Unit,
) {
    val first = state.inputs.first()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FilePreview(file = first, tool = state.tool)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = first.displayName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = Formatting.formatBytes(first.sizeBytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onPickFile,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.padding(start = 10.dp),
            ) {
                Text(stringResource(R.string.action_replace))
            }
        }

        if (state.tool == ToolKind.AUDIO) {
            state.audioDurationMs?.let { duration ->
                Text(
                    text = stringResource(R.string.tool_audio_duration, Formatting.formatDuration(duration)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.isMultiFile) {
            Column {
                Text(
                    text = stringResource(R.string.tool_files_count, state.inputs.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                SectionHeader(text = stringResource(R.string.tool_order_title))
                Text(
                    text = stringResource(R.string.tool_order_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                OrderedThumbnails(
                    items = state.inputs,
                    onReorder = viewModel::moveInput,
                    onRemove = viewModel::removeInput,
                )
                TextButton(onClick = onPickFile) {
                    Text(stringResource(R.string.tool_add_more))
                }
            }
        }
    }
}

@Composable
private fun PagesSection(state: ToolUiState, viewModel: ToolViewModel, titleRes: Int) {
    Column {
        SectionHeader(text = stringResource(titleRes))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.pagesMode == PagesMode.ALL,
                onClick = viewModel::selectAllPages,
                label = { Text(stringResource(R.string.tool_pages_all)) },
                shape = MaterialTheme.shapes.small,
            )
            FilterChip(
                selected = state.pagesMode == PagesMode.SELECTED,
                onClick = { viewModel.selectPagesMode(PagesMode.SELECTED) },
                label = { Text(stringResource(R.string.tool_pages_pick)) },
                shape = MaterialTheme.shapes.small,
            )
        }

        val pageCount = state.pageCount
        if (state.pagesMode == PagesMode.SELECTED && pageCount != null) {
            Column(modifier = Modifier.padding(top = 10.dp)) {
                Text(
                    text = stringResource(R.string.tool_pages_count, state.selectedPages.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                PageChips(count = pageCount, selected = state.selectedPages, onToggle = viewModel::togglePage)
            }
        }

        // У PDF → Изображения разрешение рендера страниц выбирается свободно.
        // У OCR точность повышается только за rewarded-видео (см. OcrPrecisionSection).
        if (state.tool == ToolKind.PDF_TO_IMAGES) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.dpi == ConversionRequest.DPI_LOW,
                    onClick = { viewModel.selectDpi(ConversionRequest.DPI_LOW) },
                    label = { Text("${ConversionRequest.DPI_LOW} dpi") },
                    shape = MaterialTheme.shapes.small,
                )
                FilterChip(
                    selected = state.dpi == ConversionRequest.DPI_HIGH,
                    onClick = { viewModel.selectDpi(ConversionRequest.DPI_HIGH) },
                    label = { Text("${ConversionRequest.DPI_HIGH} dpi") },
                    shape = MaterialTheme.shapes.small,
                )
            }
        }
    }
}

@Composable
private fun PageChips(count: Int, selected: Set<Int>, onToggle: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        (1..count).chunked(5).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { page ->
                    FilterChip(
                        selected = page in selected,
                        onClick = { onToggle(page) },
                        label = { Text(page.toString()) },
                        shape = MaterialTheme.shapes.small,
                    )
                }
            }
        }
    }
}

@Composable
private fun PageSizeSection(state: ToolUiState, viewModel: ToolViewModel) {
    Column {
        SectionHeader(text = stringResource(R.string.tool_page_size))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.pdfPageSize == PdfPageSize.A4,
                onClick = { viewModel.selectPageSize(PdfPageSize.A4) },
                label = { Text(stringResource(R.string.tool_page_size_a4)) },
                shape = MaterialTheme.shapes.small,
            )
            FilterChip(
                selected = state.pdfPageSize == PdfPageSize.FIT_IMAGE,
                onClick = { viewModel.selectPageSize(PdfPageSize.FIT_IMAGE) },
                label = { Text(stringResource(R.string.tool_page_size_fit)) },
                shape = MaterialTheme.shapes.small,
            )
        }
    }
}

/**
 * Точное распознавание (только OCR).
 *
 * Обычная точность бесплатна: картинка готовится с длинной стороной до 2000 px.
 * За просмотр rewarded-видео включается режим повышенного разрешения (300 dpi / до 3200 px) —
 * награда приходит строго из `onAdRewarded` и применяется в `ToolViewModel.onRewardedGranted`.
 */
@Composable
private fun OcrPrecisionSection(state: ToolUiState, onWatch: () -> Unit) {
    val precise = state.dpi == ConversionRequest.DPI_HIGH
    Column {
        SectionHeader(text = stringResource(R.string.tool_ocr_precision_title))
        Text(
            text = stringResource(R.string.tool_ocr_precision_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        if (precise) {
            Text(
                text = stringResource(R.string.tool_ocr_precision_on),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            OutlinedButton(onClick = onWatch) {
                Text(stringResource(R.string.tool_ocr_precision_button))
            }
        }
    }
}

@Composable
private fun QualitySection(state: ToolUiState, viewModel: ToolViewModel) {
    Column {
        SectionHeader(text = stringResource(R.string.tool_quality))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QualityPreset.entries.forEach { preset ->
                FilterChip(
                    selected = state.quality == preset,
                    onClick = { viewModel.selectQuality(preset) },
                    label = {
                        Text(
                            stringResource(
                                id = when (preset) {
                                    QualityPreset.MAX -> R.string.settings_quality_max
                                    QualityPreset.BALANCED -> R.string.settings_quality_balanced
                                    QualityPreset.MIN_SIZE -> R.string.settings_quality_min
                                },
                            ),
                        )
                    },
                    shape = MaterialTheme.shapes.small,
                )
            }
        }
    }
}

@Composable
private fun ConvertBar(
    state: ToolUiState,
    onConvert: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    var secondsRunning by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.isRunning) {
        secondsRunning = 0
        while (state.isRunning) {
            delay(1000)
            secondsRunning++
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.ScreenPadding)
            .padding(bottom = AppDimens.BannerSafeGap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            state.isRunning -> {
                val percent = (state.progress as? ProgressState.Running)?.percent ?: 0
                CircleProgress(percent = percent, modifier = Modifier.padding(vertical = 8.dp))
                Spacer(Modifier.height(10.dp))
                Text(text = stringResource(R.string.tool_converting), style = MaterialTheme.typography.titleMedium)
                if (secondsRunning >= 5) {
                    Text(
                        text = stringResource(R.string.tool_converting_slow),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.action_cancel))
                }
            }

            state.errorRes != null -> {
                Text(
                    text = stringResource(id = state.errorRes!!),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                Button(
                    onClick = onRetry,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                ) {
                    Text(stringResource(R.string.action_retry))
                }
            }

            else -> {
                val interaction = rememberPressableSource()
                Button(
                    onClick = onConvert,
                    enabled = state.canConvert,
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .pressable(interaction),
                ) {
                    Text(
                        text = stringResource(R.string.action_convert),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultSheetContent(
    result: ResultUiState,
    withWatermark: Boolean,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onOpen: () -> Unit,
    onRemoveWatermark: () -> Unit,
    onNewConversion: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(com.converterplus.app.ui.theme.SuccessGreen.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_check_circle),
                    contentDescription = null,
                    tint = com.converterplus.app.ui.theme.SuccessGreen,
                )
            }
            Spacer(Modifier.size(12.dp))
            Text(
                text = stringResource(R.string.tool_result_title),
                style = MaterialTheme.typography.headlineMedium,
            )
        }

        val first = result.artifacts.firstOrNull()
        if (first != null) {
            FilePreview(
                file = PickedFile(
                    localPath = first.path,
                    displayName = first.name,
                    sizeBytes = first.bytes,
                    mimeType = FileSaver.mimeTypeOf(first.name),
                ),
                tool = ToolKind.IMAGES,
                modifier = Modifier.height(180.dp),
            )
        }

        StatRow(
            label = stringResource(R.string.tool_result_before),
            value = Formatting.formatBytes(result.inputBytes),
        )
        StatRow(
            label = stringResource(R.string.tool_result_after),
            value = Formatting.formatBytes(result.outputBytes),
        )
        Formatting.savedPercent(result.inputBytes, result.outputBytes)?.let { percent ->
            StatRow(
                label = stringResource(R.string.tool_result_saved),
                value = Formatting.formatPercent(percent),
                accent = true,
            )
        }
        if (result.artifacts.size > 1) {
            StatRow(
                label = stringResource(R.string.tool_result_files, result.artifacts.size),
                value = "",
            )
        }

        if (withWatermark) {
            Text(
                text = stringResource(R.string.tool_result_watermark_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onRemoveWatermark,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_play),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.action_remove_watermark))
            }
        }

        Button(
            onClick = onSave,
            enabled = !result.isSaved,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_download),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(
                    id = if (result.artifacts.size > 1) R.string.action_save_all else R.string.action_save,
                ),
            )
        }

        OutlinedButton(
            onClick = onShare,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_share),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.action_share))
        }

        TextButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(
                    id = if (result.isSaved) R.string.action_open_file else R.string.action_open_folder,
                ),
            )
        }

        TextButton(onClick = onNewConversion, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.tool_pick_file_title))
        }
    }
}

@Composable
private fun SoonContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyState(
            iconRes = R.drawable.ic_refresh,
            title = stringResource(R.string.tool_soon),
            text = stringResource(R.string.tool_soon_message),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.tool_soon_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// --------------------------------------------------------------------------- вспомогательное

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

private fun openSavedLocation(context: Context, uri: String, isFolder: Boolean) {
    val intent = if (isFolder) {
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(
                Uri.parse(uri),
                android.provider.DocumentsContract.MIME_TYPE_DIR,
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } else {
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(uri), FileSaver.mimeTypeOf(uri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // Проводник не найден — молча игнорируем, файл уже сохранён.
    }
}
