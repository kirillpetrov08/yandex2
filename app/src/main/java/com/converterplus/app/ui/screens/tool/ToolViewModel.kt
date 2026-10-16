package com.converterplus.app.ui.screens.tool

import android.app.Application
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.converterplus.app.Config
import com.converterplus.app.R
import com.converterplus.app.ads.Ads
import com.converterplus.app.data.HistoryStatus
import com.converterplus.app.data.HistoryStore
import com.converterplus.app.data.SettingsStore
import com.converterplus.app.data.UriAccess
import com.converterplus.app.engine.Capabilities
import com.converterplus.app.engine.ConversionRequest
import com.converterplus.app.engine.DeviceInfo
import com.converterplus.app.engine.FormatOption
import com.converterplus.app.engine.OutputArtifact
import com.converterplus.app.engine.PdfPageSize
import com.converterplus.app.engine.PickedFile
import com.converterplus.app.engine.QualityPreset
import com.converterplus.app.engine.ToolKind
import com.converterplus.app.engine.audio.AudioConverter
import com.converterplus.app.engine.media.MediaProbe
import com.converterplus.app.engine.pdf.PdfProbe
import com.converterplus.app.engine.toMessageRes
import com.converterplus.app.storage.FileSaver
import com.converterplus.app.storage.SaveOutcome
import com.converterplus.app.work.ActiveConversions
import com.converterplus.app.work.CancelConversionReceiver
import com.converterplus.app.work.ConversionWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class PagesMode { ALL, SELECTED }

sealed interface ProgressState {
    data object Idle : ProgressState
    data class Running(val percent: Int, val startedAt: Long) : ProgressState
}

data class ResultUiState(
    val requestId: String,
    val artifacts: List<OutputArtifact>,
    val inputBytes: Long,
    val format: String,
    val savedUri: String?,
) {
    val outputBytes: Long get() = artifacts.sumOf { it.bytes }
    val isSaved: Boolean get() = !savedUri.isNullOrBlank()
}

data class ToolUiState(
    val tool: ToolKind,
    val available: Boolean = true,
    val inputs: List<PickedFile> = emptyList(),
    val formats: List<FormatOption> = emptyList(),
    val selectedFormat: String? = null,
    val quality: QualityPreset = QualityPreset.BALANCED,
    val pagesMode: PagesMode = PagesMode.ALL,
    val selectedPages: Set<Int> = emptySet(),
    val pageCount: Int? = null,
    val pdfPageSize: PdfPageSize = PdfPageSize.A4,
    val dpi: Int = ConversionRequest.DPI_LOW,
    val progress: ProgressState = ProgressState.Idle,
    val result: ResultUiState? = null,
    val errorRes: Int? = null,
    val showSameFormatDialog: Boolean = false,
    val showBigFileDialog: Boolean = false,
    val showHugeFileDialog: Boolean = false,
    val busy: Boolean = false,
    val watermark: Boolean = true,
    /** Длительность выбранного аудио — показывается под превью. */
    val audioDurationMs: Long? = null,
) {
    val totalInputBytes: Long get() = inputs.sumOf { it.sizeBytes }
    val isRunning: Boolean get() = progress is ProgressState.Running
    val canConvert: Boolean get() = available && inputs.isNotEmpty() && !isRunning && result == null
    val isMultiFile: Boolean get() = Capabilities.supportsMultipleFiles(tool)
}

sealed interface ToolEvent {
    data class Snackbar(val messageRes: Int) : ToolEvent
    data class Share(val path: String, val name: String) : ToolEvent
    data class CreateDocument(val artifact: OutputArtifact) : ToolEvent
    data class OpenSaved(val uri: String, val isFolder: Boolean) : ToolEvent
    data class ShowRewarded(val reason: RewardedReason) : ToolEvent
    data object SuccessHaptic : ToolEvent
}

enum class RewardedReason { WATERMARK, HUGE_FILE, NO_ADS, OCR_PRECISION }

class ToolViewModel(
    application: Application,
    private val tool: ToolKind,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(
        ToolUiState(
            tool = tool,
            available = Capabilities.isToolAvailable(tool),
            quality = SettingsStore.qualityPreset,
            pdfPageSize = SettingsStore.pdfPageSize,
        ),
    )
    val state: StateFlow<ToolUiState> = _state.asStateFlow()

    private val _events = Channel<ToolEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val fileSaver = FileSaver(application)
    private var requestId: String = ConversionWorker.newRequestId()
    private var observeJob: Job? = null

    init {
        if (Capabilities.isToolAvailable(tool)) {
            _state.update { it.copy(selectedFormat = Capabilities.defaultFormat(tool)) }
        }
        // Возврат на экран во время идущей конвертации: подписываемся заново.
        ActiveConversions.current.value?.let { active -> observeWork(active.workName) }
    }

    // ------------------------------------------------------------------ выбор файлов

    fun onFilesPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val limited = uris.take(Capabilities.MAX_FILES_PER_CONVERSION)
        viewModelScope.launch {
            _state.update { it.copy(busy = true, errorRes = null, result = null) }
            val app = getApplication<Application>()
            val picked = withContext(Dispatchers.IO) {
                limited.mapNotNull { uri ->
                    runCatching { UriAccess.copyToCache(app, uri, requestId) }
                        .onSuccess { file ->
                            UriAccess.rememberRecent(app, uri.toString(), file.displayName)
                        }
                        .getOrNull()
                }
            }
            if (picked.isEmpty()) {
                _state.update { it.copy(busy = false) }
                _events.send(ToolEvent.Snackbar(R.string.err_pick_failed))
                return@launch
            }

            _state.update { current ->
                val merged = if (current.isMultiFile) {
                    (current.inputs + picked).take(Capabilities.MAX_FILES_PER_CONVERSION)
                } else {
                    listOf(picked.first())
                }
                current.copy(inputs = merged, busy = false)
            }
            refreshFormats()
        }
    }

    fun removeInput(index: Int) {
        _state.update { current ->
            current.copy(inputs = current.inputs.filterIndexed { i, _ -> i != index })
        }
        refreshFormats()
    }

    fun moveInput(from: Int, to: Int) {
        _state.update { current ->
            val items = current.inputs.toMutableList()
            if (from !in items.indices || to !in items.indices) return@update current
            items.add(to, items.removeAt(from))
            current.copy(inputs = items)
        }
    }

    // ------------------------------------------------------------------ настройки

    fun selectFormat(id: String) {
        _state.update { it.copy(selectedFormat = id, errorRes = null) }
    }

    fun selectQuality(quality: QualityPreset) {
        _state.update { it.copy(quality = quality) }
        SettingsStore.qualityPreset = quality
    }

    fun selectPagesMode(mode: PagesMode) {
        _state.update { it.copy(pagesMode = mode) }
    }

    fun togglePage(page: Int) {
        _state.update { current ->
            val pages = current.selectedPages.toMutableSet()
            if (!pages.remove(page)) pages.add(page)
            current.copy(selectedPages = pages)
        }
    }

    fun selectAllPages() {
        _state.update { current ->
            val count = current.pageCount ?: return@update current
            current.copy(selectedPages = (1..count).toSet(), pagesMode = PagesMode.ALL)
        }
    }

    fun selectPageSize(size: PdfPageSize) {
        _state.update { it.copy(pdfPageSize = size) }
        SettingsStore.pdfPageSize = size
    }

    fun selectDpi(dpi: Int) {
        _state.update { it.copy(dpi = dpi) }
    }

    // ------------------------------------------------------------------ запуск

    fun startConversion() {
        val current = _state.value
        if (!current.available) {
            _events.trySend(ToolEvent.Snackbar(R.string.reason_tool_soon))
            return
        }
        if (current.inputs.isEmpty() || current.isRunning) {
            _events.trySend(ToolEvent.Snackbar(R.string.err_no_input))
            return
        }
        val format = current.selectedFormat ?: Capabilities.defaultFormat(tool)
        val input = current.inputs.first().toInputInfo()
        val reason = Capabilities.reason(tool, input, format, deviceInfo())
        if (reason != null) {
            _events.trySend(ToolEvent.Snackbar(reason))
            return
        }
        if (tool == ToolKind.PDF_TO_IMAGES &&
            current.pagesMode == PagesMode.SELECTED &&
            current.selectedPages.isEmpty()
        ) {
            _events.trySend(ToolEvent.Snackbar(R.string.err_no_pages_selected))
            return
        }
        if (tool != ToolKind.IMAGES_TO_PDF && Capabilities.isAlreadyInFormat(input, format)) {
            _state.update { it.copy(showSameFormatDialog = true) }
            return
        }
        if (current.totalInputBytes > Config.LARGE_FILE_BYTES) {
            _state.update { it.copy(showBigFileDialog = true) }
            return
        }
        if (current.watermark && current.totalInputBytes > Config.HUGE_FILE_BYTES) {
            _state.update { it.copy(showHugeFileDialog = true) }
            return
        }
        enqueue(watermark = current.watermark)
    }

    fun dismissDialogs() {
        _state.update {
            it.copy(
                showSameFormatDialog = false,
                showBigFileDialog = false,
                showHugeFileDialog = false,
            )
        }
    }

    /** «Всё равно конвертировать» в диалоге про одинаковый формат. */
    fun convertAnyway() {
        dismissDialogs()
        enqueue(watermark = _state.value.watermark)
    }

    /** Продолжить без просмотра рекламы (большой файл). */
    fun convertWithoutReward() {
        dismissDialogs()
        enqueue(watermark = _state.value.watermark)
    }

    /** Награда выдаётся строго из onAdRewarded — здесь только применяем её. */
    fun onRewardedGranted(reason: RewardedReason) {
        when (reason) {
            RewardedReason.WATERMARK, RewardedReason.HUGE_FILE -> {
                dismissDialogs()
                requestId = ConversionWorker.newRequestId()
                enqueue(watermark = false)
                _events.trySend(ToolEvent.Snackbar(R.string.tool_watermark_removed))
            }

            RewardedReason.NO_ADS -> {
                Ads.policy(getApplication()).activateAdsFreePeriod()
                Ads.refreshAdsAllowed(getApplication())
                _events.trySend(ToolEvent.Snackbar(R.string.remove_ads_thanks))
            }

            RewardedReason.OCR_PRECISION -> {
                // Точное распознавание = рендер PDF и подготовка картинки в большем разрешении.
                _state.update { it.copy(dpi = ConversionRequest.DPI_HIGH) }
                _events.trySend(ToolEvent.Snackbar(R.string.tool_ocr_precision_on))
            }
        }
    }

    /** «Попробовать снова» после ошибки — новая работа с новым id. */
    fun retryAfterError() {
        observeJob?.cancel()
        observeJob = null
        requestId = ConversionWorker.newRequestId()
        _state.update { it.copy(errorRes = null, result = null, progress = ProgressState.Idle) }
        startConversion()
    }

    fun cancelConversion() {
        ActiveConversions.current.value?.let { active ->
            ConversionWorker.cancel(getApplication(), active.workName)
        }
        _state.update { it.copy(progress = ProgressState.Idle, errorRes = R.string.err_cancelled) }
    }

    fun closeResult() {
        _state.update { it.copy(result = null) }
    }

    /** Новая конвертация после закрытия результата. */
    fun resetForNewConversion() {
        observeJob?.cancel()
        observeJob = null
        requestId = ConversionWorker.newRequestId()
        _state.update { it.copy(result = null, errorRes = null, progress = ProgressState.Idle) }
    }

    // ------------------------------------------------------------------ результат

    fun saveResult() {
        val result = _state.value.result ?: return
        val treeUri = SettingsStore.saveFolderUri
        val first = result.artifacts.firstOrNull() ?: return

        // API 26–28 без выбранной папки: сохранение через системный диалог (без разрешений).
        if (fileSaver.needsDocumentPicker(treeUri)) {
            _events.trySend(ToolEvent.CreateDocument(first))
            return
        }

        viewModelScope.launch {
            val app = getApplication<Application>()
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    result.artifacts.map { artifact ->
                        fileSaver.save(File(artifact.path), artifact.name, treeUri)
                    }
                }
            }
            saved.onSuccess { outcomes ->
                val uri = outcomes.firstOrNull()?.let { uriOf(it) }
                withContext(Dispatchers.IO) { markSaved(result.requestId, uri) }
                _state.update { current ->
                    current.copy(result = current.result?.copy(savedUri = uri))
                }
                _events.send(
                    ToolEvent.Snackbar(
                        if (treeUri.isNullOrBlank()) R.string.tool_saved_to_downloads
                        else R.string.tool_saved_to_folder,
                    ),
                )
            }.onFailure { error ->
                _events.send(ToolEvent.Snackbar(error.toMessageRes()))
            }
        }
    }

    /** Системный диалог сохранения вернул URI — пишем файл туда. */
    fun onDocumentCreated(uri: Uri?, artifact: OutputArtifact) {
        if (uri == null) return
        val requestIdOfResult = _state.value.result?.requestId ?: return
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching { fileSaver.saveToUri(File(artifact.path), uri) }
            }
            saved.onSuccess { outcome ->
                val uriString = uriOf(outcome)
                withContext(Dispatchers.IO) { markSaved(requestIdOfResult, uriString) }
                _state.update { current -> current.copy(result = current.result?.copy(savedUri = uriString)) }
                _events.send(ToolEvent.Snackbar(R.string.tool_saved_to_folder))
            }.onFailure {
                _events.send(ToolEvent.Snackbar(R.string.err_generic))
            }
        }
    }

    fun shareResult() {
        val artifact = _state.value.result?.artifacts?.firstOrNull() ?: return
        _events.trySend(ToolEvent.Share(artifact.path, artifact.name))
    }

    fun openSavedLocation() {
        val treeUri = SettingsStore.saveFolderUri
        if (!treeUri.isNullOrBlank()) {
            _events.trySend(ToolEvent.OpenSaved(treeUri, isFolder = true))
            return
        }
        val saved = _state.value.result?.savedUri
        if (!saved.isNullOrBlank()) {
            _events.trySend(ToolEvent.OpenSaved(saved, isFolder = false))
            return
        }
        _events.trySend(ToolEvent.Snackbar(R.string.err_no_input))
    }

    // ------------------------------------------------------------------ внутреннее

    private fun enqueue(watermark: Boolean) {
        val current = _state.value
        // Новый id на каждый запуск. Раньше id жил до «новой конвертации», поэтому повторный
        // прогон с теми же файлами писал в историю записи с уже занятыми id — список истории
        // падал на одинаковых ключах LazyColumn.
        requestId = ConversionWorker.newRequestId()
        val request = ConversionRequest(
            id = requestId,
            tool = tool,
            inputs = current.inputs,
            outputFormat = current.selectedFormat ?: Capabilities.defaultFormat(tool),
            quality = current.quality,
            watermark = watermark,
            selectedPages = if (current.pagesMode == PagesMode.SELECTED) {
                current.selectedPages.toList().sorted()
            } else {
                null
            },
            pdfDpi = current.dpi,
            pdfPageSize = current.pdfPageSize,
        )

        val workName = ActiveConversions.uniqueWorkName(request.id)
        val workRequest = OneTimeWorkRequestBuilder<ConversionWorker>()
            .setInputData(ConversionWorker.inputData(request))
            .addTag(CancelConversionReceiver.TAG_CONVERSION)
            .build()

        WorkManager.getInstance(getApplication())
            .enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, workRequest)
        ActiveConversions.start(workName, request.id)

        _state.update {
            it.copy(
                progress = ProgressState.Running(percent = 0, startedAt = SystemClock.elapsedRealtime()),
                result = null,
                errorRes = null,
                watermark = watermark,
            )
        }
        observeWork(workName)
    }

    private fun observeWork(workName: String) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            WorkManager.getInstance(getApplication())
                .getWorkInfosForUniqueWorkFlow(workName)
                .collect { infos ->
                    val info = infos.firstOrNull() ?: return@collect
                    when (info.state) {
                        WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED -> {
                            val percent = info.progress.getInt(ConversionWorker.KEY_PROGRESS, 0)
                            _state.update { current ->
                                val startedAt = (current.progress as? ProgressState.Running)?.startedAt
                                    ?: SystemClock.elapsedRealtime()
                                current.copy(progress = ProgressState.Running(percent, startedAt))
                            }
                        }

                        WorkInfo.State.SUCCEEDED -> onWorkSucceeded(info)
                        WorkInfo.State.FAILED -> onWorkFailed(info)
                        WorkInfo.State.CANCELLED -> onWorkCancelled()
                        else -> Unit
                    }
                }
        }
    }

    private suspend fun onWorkSucceeded(info: WorkInfo) {
        val finishedRequestId = info.outputData.getString(ConversionWorker.KEY_REQUEST_ID) ?: requestId
        val inputBytes = info.outputData.getLong(ConversionWorker.KEY_INPUT_BYTES, 0L)
        val artifacts = withContext(Dispatchers.IO) {
            HistoryStore.default(getApplication())
                .load()
                .filter { it.id.startsWith("$finishedRequestId-") }
                .mapNotNull { entry ->
                    val path = entry.localPath ?: return@mapNotNull null
                    OutputArtifact(path = path, name = entry.fileName, bytes = entry.sizeBytes)
                }
        }
        ActiveConversions.finish(finishedRequestId)
        observeJob?.cancel()
        observeJob = null

        _state.update { current ->
            current.copy(
                progress = ProgressState.Idle,
                errorRes = null,
                result = ResultUiState(
                    requestId = finishedRequestId,
                    artifacts = artifacts,
                    inputBytes = inputBytes,
                    format = current.selectedFormat.orEmpty(),
                    savedUri = null,
                ),
            )
        }
        if (SettingsStore.hapticsEnabled) {
            _events.send(ToolEvent.SuccessHaptic)
        }
    }

    private fun onWorkFailed(info: WorkInfo) {
        val res = info.outputData.getInt(ConversionWorker.KEY_ERROR_RES, R.string.err_generic)
        ActiveConversions.current.value?.let { ActiveConversions.finish(it.requestId) }
        observeJob?.cancel()
        observeJob = null
        _state.update { it.copy(progress = ProgressState.Idle, errorRes = res) }
    }

    private fun onWorkCancelled() {
        ActiveConversions.current.value?.let { ActiveConversions.finish(it.requestId) }
        observeJob?.cancel()
        observeJob = null
        _state.update { it.copy(progress = ProgressState.Idle, errorRes = R.string.err_cancelled) }
    }

    private fun markSaved(historyIdPrefix: String, uri: String?) {
        if (uri == null) return
        val history = HistoryStore.default(getApplication())
        history.load()
            .filter { it.id.startsWith("$historyIdPrefix-") }
            .forEach { entry ->
                history.update(entry.copy(status = HistoryStatus.SAVED, uri = uri))
            }
    }

    private fun uriOf(outcome: SaveOutcome): String = when (outcome) {
        is SaveOutcome.Content -> outcome.uri
        is SaveOutcome.Path -> Uri.fromFile(File(outcome.path)).toString()
    }

    private fun refreshFormats() {
        val input = _state.value.inputs.firstOrNull()?.toInputInfo()
        val options = Capabilities.optionsFor(tool, input, deviceInfo())
        _state.update { current ->
            val selected = current.selectedFormat
                ?.takeIf { id -> options.any { it.id == id && it.enabled } }
                ?: options.firstOrNull { it.enabled }?.id
                ?: Capabilities.defaultFormat(tool)
            current.copy(formats = options, selectedFormat = selected)
        }
        val firstFile = _state.value.inputs.firstOrNull()?.let { File(it.localPath) }
        val isPdfInput = firstFile?.extension.equals("pdf", ignoreCase = true)
        val pageCount = if (Capabilities.supportsPageSelection(tool) && isPdfInput) {
            firstFile?.let { PdfProbe.pageCount(it) }
        } else {
            null
        }
        val audioDuration = if (tool == ToolKind.AUDIO) {
            firstFile?.let { AudioConverter.audioDurationMs(it) }
        } else {
            null
        }
        _state.update { it.copy(pageCount = pageCount, audioDurationMs = audioDuration) }
    }

    /**
     * Что известно об устройстве и выбранном файле. Проверки идут по-разному для PDF и медиа:
     * у PDF важно, не защищён ли он паролем, у аудио и видео — есть ли нужные дорожки.
     */
    private fun deviceInfo(): DeviceInfo {
        val input = _state.value.inputs.firstOrNull()
        val file = input?.let { File(it.localPath) }
        var canRead = true
        var passwordProtected = false
        var hasVideoTrack = true
        var hasAudioTrack = true

        if (file != null) {
            when {
                Capabilities.supportsPageSelection(tool) && file.extension.equals("pdf", ignoreCase = true) -> {
                    when (PdfProbe.probe(file)) {
                        is PdfProbe.Result.PasswordProtected -> {
                            canRead = false
                            passwordProtected = true
                        }

                        is PdfProbe.Result.Unreadable -> canRead = false
                        else -> Unit
                    }
                }

                tool == ToolKind.AUDIO -> {
                    hasAudioTrack = AudioConverter.hasAudioTrack(file)
                    canRead = hasAudioTrack
                }

                tool == ToolKind.VIDEO -> {
                    hasVideoTrack = MediaProbe.hasVideoTrack(file)
                    hasAudioTrack = MediaProbe.hasAudioTrack(file)
                    canRead = hasVideoTrack
                }
            }
        }

        return DeviceInfo(
            sdkInt = Build.VERSION.SDK_INT,
            hasOpusEncoder = AudioConverter.hasOpusEncoder(),
            canReadInput = canRead,
            inputPasswordProtected = passwordProtected,
            hasVideoTrack = hasVideoTrack,
            hasAudioTrack = hasAudioTrack,
        )
    }

    override fun onCleared() {
        observeJob?.cancel()
        super.onCleared()
    }
}
