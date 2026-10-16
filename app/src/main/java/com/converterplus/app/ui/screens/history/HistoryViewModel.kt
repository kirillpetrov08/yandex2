package com.converterplus.app.ui.screens.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.converterplus.app.R
import com.converterplus.app.data.HistoryEntry
import com.converterplus.app.data.HistoryStore
import com.converterplus.app.data.HistoryTsv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HistoryUiState(
    val query: String = "",
    val formatFilter: String? = null,
    val entries: List<HistoryEntry> = emptyList(),
    val formats: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = entries.isEmpty()
}

sealed interface HistoryEvent {
    data class Snackbar(val messageRes: Int) : HistoryEvent
    data class Share(val path: String, val name: String) : HistoryEvent
    data class Save(val entry: HistoryEntry) : HistoryEvent
}

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val history = HistoryStore.default(application)

    /** Полный список без фильтров — из него пересчитываем выдачу при поиске и смене фильтра. */
    private var allEntries: List<HistoryEntry> = emptyList()

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    private val _events = Channel<HistoryEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            allEntries = withContext(Dispatchers.IO) { history.load() }
            publish()
        }
    }

    fun search(query: String) {
        _state.value = _state.value.copy(query = query)
        publish()
    }

    fun setFormatFilter(format: String?) {
        _state.value = _state.value.copy(formatFilter = format)
        publish()
    }

    private fun publish() {
        val current = _state.value
        // distinctBy — страховка списка: одинаковые ключи в LazyColumn роняют экран истории.
        _state.value = current.copy(
            entries = HistoryTsv.dedupe(HistoryTsv.filter(allEntries, current.query, current.formatFilter)),
            formats = HistoryTsv.availableFormats(allEntries),
        )
    }

    fun delete(entry: HistoryEntry) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                entry.localPath?.let { path -> runCatching { java.io.File(path).delete() } }
                history.delete(entry.id)
            }
            refresh()
            _events.send(HistoryEvent.Snackbar(R.string.history_deleted))
        }
    }

    fun share(entry: HistoryEntry) {
        val path = entry.localPath
        if (path == null || !java.io.File(path).exists()) {
            _events.trySend(HistoryEvent.Snackbar(R.string.history_no_file))
            return
        }
        _events.trySend(HistoryEvent.Share(path, entry.fileName))
    }

    /** Записи со статусом READY лежат во временной папке — сохраняем в «Загрузки». */
    fun save(entry: HistoryEntry) {
        _events.trySend(HistoryEvent.Save(entry))
    }

}
