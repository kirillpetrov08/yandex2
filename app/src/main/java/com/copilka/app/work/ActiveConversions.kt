package com.copilka.app.work

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Текущая активная конвертация. Нужна, чтобы при возврате на экран инструмента
 * снова подписаться на прогресс: уход с экрана работу не прерывает.
 */
object ActiveConversions {

    data class Active(val workName: String, val requestId: String)

    private val _current = MutableStateFlow<Active?>(null)
    val current: StateFlow<Active?> = _current.asStateFlow()

    fun start(workName: String, requestId: String) {
        _current.value = Active(workName, requestId)
    }

    fun finish(requestId: String) {
        if (_current.value?.requestId == requestId) _current.value = null
    }

    fun reset() {
        _current.value = null
    }

    fun uniqueWorkName(requestId: String): String = "conversion_$requestId"
}
