package com.converterplus.app.ui.screens.tool

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.converterplus.app.R
import com.converterplus.app.ads.Ads
import com.converterplus.app.ads.BannerSlot
import com.converterplus.app.ads.RewardedOutcome
import com.converterplus.app.engine.Capabilities
import com.converterplus.app.engine.FileNameFactory
import com.converterplus.app.engine.ConversionRequest
import com.converterplus.app.engine.Formatting
import com.converterplus.app.engine.OutputArtifact
import com.converterplus.app.engine.PdfPageSize
import com.converterplus.app.engine.PickedFile
import com.converterplus.app.engine.QualityPreset
import com.converterplus.app.engine.ToolKind
import com.converterplus.app.engine.image.FitMode
import com.converterplus.app.engine.image.ImageSize
import com.converterplus.app.engine.image.ImageSizing
import com.converterplus.app.engine.image.SizeGroup
import com.converterplus.app.engine.image.SizePreset
import com.converterplus.app.storage.FileSaver
import com.converterplus.app.ui.components.AppBottomSheet
import com.converterplus.app.ui.components.CircleProgress
import com.converterplus.app.ui.components.EmptyState
import com.converterplus.app.ui.components.FileCaption
import com.converterplus.app.ui.components.FilePreview
import com.converterplus.app.ui.components.FormatChipRow
import com.converterplus.app.ui.components.OrderedThumbnails
import com.converterplus.app.ui.components.SectionHeader
import com.converterplus.app.ui.components.SingleChoiceChipRow
import com.converterplus.app.ui.components.SelectableChipRow
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
        try {
            if (tool == ToolKind.IMAGES_TO_PDF) {
                pickMultipleImages.launch(imageOnly)
            } else {
                pickImage.launch(imageOnly)
            }
        } catch (e: Exception) {
            openFilesPicker()
        }
    }

    /** «Из файлов» — системный выбор документов с нужными типами. */
    fun openFilesPicker() {
        openDocument.launch(Capabilities.mimeTypesFor(tool))
    }

    /** Основное действие зоны выбора: сначала пробуем галерею, если она уместна. */
    fun openPicker() {
        val galleryAvailable = Capabilities.hasGallerySource(tool) &&
            ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)
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
                tool = state.tool,
                withWatermark = state.watermark && Capabilities.supportsWatermark(state.tool),
                onRename = viewModel::renameOutput,
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
            .padding(top = AppDimens.SpaceXs, bottom = AppDimens.BannerSafeGap),
        // Единый шаг между блоками: раньше отступы «гуляли» от 8 до 16 dp.
        verticalArrangement = Arrangement.spacedBy(AppDimens.BlockGap),
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
                            modifier = Modifier.padding(top = AppDimens.SpaceS),
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

        if (state.tool == ToolKind.IMAGES) {
            SizeSection(state = state, viewModel = viewModel)
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceButtons(
    tool: ToolKind,
    onGallery: () -> Unit,
    onFiles: () -> Unit,
) {
    // FlowRow, а не Row: на узких экранах две кнопки не влезают в строку и текст в них сжимался.
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpaceM),
        verticalArrangement = Arrangement.spacedBy(AppDimens.SpaceS),
    ) {
        if (Capabilities.hasGallerySource(tool)) {
            OutlinedButton(onClick = onGallery, shape = MaterialTheme.shapes.small) {
                Icon(
                    painter = painterResource(R.drawable.ic_tool_image),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(AppDimens.SpaceS))
                Text(
                    text = stringResource(R.string.action_from_gallery),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        OutlinedButton(onClick = onFiles, shape = MaterialTheme.shapes.small) {
            Icon(
                painter = painterResource(R.drawable.ic_folder_open),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(AppDimens.SpaceS))
            Text(
                text = stringResource(R.string.action_from_files),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
            Spacer(Modifier.height(AppDimens.SpaceM))
            Text(
                text = stringResource(R.string.tool_pick_file_hint),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = AppDimens.SpaceL),
            )
            Spacer(Modifier.height(AppDimens.SpaceXs))
            Text(
                text = stringResource(
                    id = if (tool == ToolKind.PDF_TO_IMAGES) R.string.action_from_files
                    else R.string.action_from_gallery,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = AppDimens.SpaceL),
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
                    .padding(horizontal = AppDimens.CardPadding, vertical = AppDimens.SpaceM),
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
                Spacer(Modifier.size(AppDimens.SpaceM))
                Text(
                    text = recent.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
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

    Column(verticalArrangement = Arrangement.spacedBy(AppDimens.BlockGap)) {
        FilePreview(file = first, tool = state.tool)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Имя и размер — отдельной колонкой, кнопка «Заменить» больше не сжимает текст.
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = first.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = Formatting.formatBytes(first.sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(AppDimens.SpaceM))
            OutlinedButton(
                onClick = onPickFile,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.height(AppDimens.MinTouchTarget),
            ) {
                Text(stringResource(R.string.action_replace), maxLines = 1)
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
            Column(verticalArrangement = Arrangement.spacedBy(AppDimens.SpaceS)) {
                Text(
                    text = stringResource(R.string.tool_files_count, state.inputs.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SectionHeader(text = stringResource(R.string.tool_order_title))
                Text(
                    text = stringResource(R.string.tool_order_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OrderedThumbnails(
                    items = state.inputs,
                    onReorder = viewModel::moveInput,
                    onRemove = viewModel::removeInput,
                )
                TextButton(
                    onClick = onPickFile,
                    modifier = Modifier.height(AppDimens.MinTouchTarget),
                ) {
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
        SingleChoiceChipRow(
            options = listOf(
                PagesMode.ALL to stringResource(R.string.tool_pages_all),
                PagesMode.SELECTED to stringResource(R.string.tool_pages_pick),
            ),
            selected = state.pagesMode,
            onSelect = { mode ->
                if (mode == PagesMode.ALL) viewModel.selectAllPages() else viewModel.selectPagesMode(mode)
            },
        )

        val pageCount = state.pageCount
        if (state.pagesMode == PagesMode.SELECTED && pageCount != null) {
            Column(modifier = Modifier.padding(top = AppDimens.SpaceM)) {
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
            Spacer(Modifier.height(AppDimens.SpaceM))
            SingleChoiceChipRow(
                options = listOf(
                    ConversionRequest.DPI_LOW to "${ConversionRequest.DPI_LOW} dpi",
                    ConversionRequest.DPI_HIGH to "${ConversionRequest.DPI_HIGH} dpi",
                ),
                selected = state.dpi,
                onSelect = viewModel::selectDpi,
            )
        }
    }
}

/** Номера страниц: сетка с переносом, чтобы кнопки не сжимались на узком экране. */
@Composable
private fun PageChips(count: Int, selected: Set<Int>, onToggle: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 168.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        SelectableChipRow(
            options = (1..count).map { page -> page to page.toString() },
            isSelected = { page -> page in selected },
            onSelect = onToggle,
        )
    }
}

@Composable
private fun PageSizeSection(state: ToolUiState, viewModel: ToolViewModel) {
    Column {
        SectionHeader(text = stringResource(R.string.tool_page_size))
        SingleChoiceChipRow(
            options = listOf(
                PdfPageSize.A4 to stringResource(R.string.tool_page_size_a4),
                PdfPageSize.FIT_IMAGE to stringResource(R.string.tool_page_size_fit),
            ),
            selected = state.pdfPageSize,
            onSelect = viewModel::selectPageSize,
        )
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

/**
 * Размер результата: готовые шаблоны по группам, «свой размер» и способ подгонки.
 *
 * Ручной ввод проверяется здесь же, поэтому в запрос никогда не попадёт 0 или 100000 px.
 * Подпись под чипами считает ровно то, что сделает движок, — одна и та же чистая функция
 * [ImageSizing.resolve] используется и в интерфейсе, и при конвертации.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SizeSection(state: ToolUiState, viewModel: ToolViewModel) {
    var showCustomDialog by remember { mutableStateOf(false) }

    val target = ImageSizing.normalize(
        state.customWidth ?: state.sizePreset?.width,
        state.customHeight ?: state.sizePreset?.height,
    )
    val source = if (state.sourceWidth != null && state.sourceHeight != null) {
        ImageSize(state.sourceWidth, state.sourceHeight)
    } else {
        null
    }

    Column {
        SectionHeader(text = stringResource(R.string.tool_size_title))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpaceS),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpaceS),
        ) {
            FilterChip(
                selected = target == null,
                onClick = { viewModel.selectSizePreset(null) },
                label = { Text(stringResource(R.string.tool_size_original)) },
                shape = MaterialTheme.shapes.small,
            )
            FilterChip(
                selected = state.customWidth != null && state.customHeight != null,
                onClick = { showCustomDialog = true },
                label = {
                    Text(
                        text = if (state.customWidth != null && state.customHeight != null) {
                            stringResource(
                                R.string.tool_size_custom_value,
                                state.customWidth ?: 0,
                                state.customHeight ?: 0,
                            )
                        } else {
                            stringResource(R.string.tool_size_custom)
                        },
                    )
                },
                shape = MaterialTheme.shapes.small,
            )
        }

        SizeGroup.entries.forEach { group ->
            val presets = SizePreset.entries.filter { it.group == group }
            if (presets.isNotEmpty()) {
                Text(
                    text = stringResource(sizeGroupTitle(group)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = AppDimens.SpaceM, bottom = AppDimens.SpaceS),
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.SpaceS),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.SpaceS),
                ) {
                    presets.forEach { preset ->
                        FilterChip(
                            selected = state.sizePreset == preset,
                            onClick = { viewModel.selectSizePreset(preset) },
                            label = { Text(preset.label) },
                            shape = MaterialTheme.shapes.small,
                        )
                    }
                }
            }
        }

        if (target != null) {
            Text(
                text = stringResource(R.string.tool_size_fit_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = AppDimens.SpaceL, bottom = AppDimens.SpaceS),
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.SpaceS),
                verticalArrangement = Arrangement.spacedBy(AppDimens.SpaceS),
            ) {
                FitMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.fitMode == mode,
                        onClick = { viewModel.selectFitMode(mode) },
                        label = {
                            Text(
                                stringResource(
                                    id = if (mode == FitMode.FIT) R.string.tool_size_fit else R.string.tool_size_crop,
                                ),
                            )
                        },
                        shape = MaterialTheme.shapes.small,
                    )
                }
            }
            Text(
                text = stringResource(
                    id = if (state.fitMode == FitMode.FIT) R.string.tool_size_fit_hint else R.string.tool_size_crop_hint,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = AppDimens.SpaceS),
            )
        }

        Text(
            text = sizeResultText(state = state, source = source, target = target),
            style = MaterialTheme.typography.bodyMedium,
            color = if (target == null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.padding(top = AppDimens.SpaceM),
        )
    }

    if (showCustomDialog) {
        CustomSizeDialog(
            initialWidth = state.customWidth ?: state.sourceWidth ?: 1920,
            initialHeight = state.customHeight ?: state.sourceHeight ?: 1080,
            onDismiss = { showCustomDialog = false },
            onApply = { width, height ->
                viewModel.applyCustomSize(width, height)
                showCustomDialog = false
            },
        )
    }
}

/** Подпись «что получится» — берётся из того же расчёта, что и сама конвертация. */
@Composable
private fun sizeResultText(state: ToolUiState, source: ImageSize?, target: ImageSize?): String {
    if (target == null) return stringResource(R.string.tool_size_original_hint)
    if (source == null) return stringResource(R.string.tool_size_result, target.width, target.height)

    val result = ImageSizing.resolve(source, target, state.fitMode)
    val ratio = Formatting.formatRatio(ImageSizing.shrinkRatio(source, result))
    return when {
        state.fitMode == FitMode.CROP -> stringResource(R.string.tool_size_result, result.width, result.height)
        ratio != null -> stringResource(
            R.string.tool_size_result_smaller,
            result.width,
            result.height,
            ratio,
        )

        result == source && state.sizePreset != null -> stringResource(R.string.tool_size_no_upscale)
        else -> stringResource(R.string.tool_size_result, result.width, result.height)
    }
}

private fun sizeGroupTitle(group: SizeGroup): Int = when (group) {
    SizeGroup.SCREEN -> R.string.tool_size_group_screen
    SizeGroup.SOCIAL -> R.string.tool_size_group_social
    SizeGroup.PRINT -> R.string.tool_size_group_print
}

/** Диалог ручного размера: принимает только числа и сразу проверяет диапазон. */
@Composable
private fun CustomSizeDialog(
    initialWidth: Int,
    initialHeight: Int,
    onDismiss: () -> Unit,
    onApply: (Int, Int) -> Unit,
) {
    var widthText by remember { mutableStateOf(initialWidth.toString()) }
    var heightText by remember { mutableStateOf(initialHeight.toString()) }
    val width = widthText.toIntOrNull()
    val height = heightText.toIntOrNull()
    val valid = ImageSizing.normalize(width, height) != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tool_size_custom_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppDimens.SpaceM)) {
                OutlinedTextField(
                    value = widthText,
                    onValueChange = { widthText = it.filter(Char::isDigit).take(5) },
                    label = { Text(stringResource(R.string.tool_size_width)) },
                    singleLine = true,
                    isError = !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = heightText,
                    onValueChange = { heightText = it.filter(Char::isDigit).take(5) },
                    label = { Text(stringResource(R.string.tool_size_height)) },
                    singleLine = true,
                    isError = !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!valid) {
                    Text(
                        text = stringResource(R.string.tool_size_range_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (width != null && height != null) onApply(width, height) },
                enabled = valid,
            ) {
                Text(stringResource(R.string.action_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun QualitySection(state: ToolUiState, viewModel: ToolViewModel) {
    Column {
        SectionHeader(text = stringResource(R.string.tool_quality))
        SingleChoiceChipRow(
            options = QualityPreset.entries.map { preset ->
                preset to stringResource(
                    id = when (preset) {
                        QualityPreset.MAX -> R.string.settings_quality_max
                        QualityPreset.BALANCED -> R.string.settings_quality_balanced
                        QualityPreset.MIN_SIZE -> R.string.settings_quality_min
                    },
                )
            },
            selected = state.quality,
            onSelect = viewModel::selectQuality,
        )
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
                CircleProgress(percent = percent, modifier = Modifier.padding(vertical = AppDimens.SpaceS))
                Spacer(Modifier.height(AppDimens.SpaceM))
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
    tool: ToolKind,
    withWatermark: Boolean,
    onRename: (String) -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onOpen: () -> Unit,
    onRemoveWatermark: () -> Unit,
    onNewConversion: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppDimens.BlockGap)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Галочка появляется с лёгким «пружинным» масштабом — результат не выскакивает
            // мгновенно, а как будто подтверждается.
            var appeared by remember { mutableStateOf(false) }
            val checkScale by animateFloatAsState(
                targetValue = if (appeared) 1f else 0.4f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
                label = "resultCheck",
            )
            LaunchedEffect(Unit) { appeared = true }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .graphicsLayer {
                        scaleX = checkScale
                        scaleY = checkScale
                        alpha = checkScale.coerceIn(0f, 1f)
                    }
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
            Spacer(Modifier.size(AppDimens.SpaceM))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.tool_result_title),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.tool_result_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        val first = result.artifacts.firstOrNull()
        if (first != null) {
            Column(verticalArrangement = Arrangement.spacedBy(AppDimens.SpaceS)) {
                FilePreview(
                    file = PickedFile(
                        localPath = first.path,
                        displayName = first.name,
                        sizeBytes = first.bytes,
                        mimeType = FileSaver.mimeTypeOf(first.name),
                    ),
                    // Настоящий инструмент и расширение файла: превью больше не «слепое».
                    tool = tool,
                    modifier = Modifier.heightIn(min = 150.dp, max = 240.dp),
                )
                if (result.isSaved) {
                    FileCaption(
                        name = first.name,
                        size = Formatting.formatBytes(first.bytes),
                    )
                } else {
                    val initialBase = FileNameFactory.editableBaseName(first.name, result.artifacts.size)
                    var draft by remember(result.requestId) {
                        mutableStateOf(TextFieldValue(initialBase, TextRange(0, initialBase.length)))
                    }
                    val extension = first.name.substringAfterLast('.', result.format)
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { value ->
                            draft = value
                            onRename(value.text)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.tool_result_filename)) },
                        supportingText = { Text(stringResource(R.string.tool_result_filename_hint)) },
                        suffix = { Text(".$extension") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    )
                    Text(
                        text = Formatting.formatBytes(first.bytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (result.artifacts.size > 1) {
                    Text(
                        text = stringResource(R.string.tool_result_files, result.artifacts.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(horizontal = AppDimens.CardPadding, vertical = AppDimens.SpaceM),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpaceXs),
        ) {
            StatRow(
                label = stringResource(R.string.tool_result_before),
                value = Formatting.formatBytes(result.inputBytes),
            )
            StatRow(
                label = stringResource(R.string.tool_result_after),
                value = Formatting.formatBytes(result.outputBytes),
            )
            val saved = Formatting.savedPercent(result.inputBytes, result.outputBytes)
            if (saved != null) {
                val grew = saved < 0
                StatRow(
                    label = stringResource(
                        id = if (grew) R.string.tool_result_grew else R.string.tool_result_saved,
                    ),
                    value = if (grew) Formatting.formatGrew(saved) else Formatting.formatSaved(saved),
                    accent = !grew,
                )
            }
        }

        if (withWatermark) {
            Column(verticalArrangement = Arrangement.spacedBy(AppDimens.SpaceS)) {
                Text(
                    text = stringResource(R.string.tool_result_watermark_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = onRemoveWatermark,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_play),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(AppDimens.SpaceS))
                    Text(stringResource(R.string.action_remove_watermark))
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(AppDimens.SpaceM)) {
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
                Spacer(Modifier.size(AppDimens.SpaceS))
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
                Spacer(Modifier.size(AppDimens.SpaceS))
                Text(stringResource(R.string.action_share))
            }

            TextButton(
                onClick = onOpen,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AppDimens.MinTouchTarget),
            ) {
                Text(
                    stringResource(
                        id = if (result.isSaved) R.string.action_open_file else R.string.action_open_folder,
                    ),
                )
            }

            TextButton(
                onClick = onNewConversion,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AppDimens.MinTouchTarget),
            ) {
                Text(stringResource(R.string.tool_pick_file_title))
            }
        }
    }
}

@Composable
private fun SoonContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(AppDimens.SpaceXl),
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
                android.provider.DocumentsContract.Document.MIME_TYPE_DIR,
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
