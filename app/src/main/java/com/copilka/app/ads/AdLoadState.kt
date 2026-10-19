package com.copilka.app.ads

/** Состояние загрузки рекламы. Используется баннером и контроллерами полноэкранных форматов. */
sealed interface AdLoadState {
    data object Idle : AdLoadState
    data object Loading : AdLoadState
    data object Ready : AdLoadState

    /**
     * Загрузка не удалась. Публичного справочника кодов ошибок у РСЯ нет,
     * поэтому код и описание пишутся в лог как есть.
     */
    data class Failed(val code: Int, val description: String) : AdLoadState
}
