package com.converterplus.app.ads

import android.util.Log

/**
 * Логирование рекламных событий и ошибок.
 * Держит последнее сообщение в памяти, чтобы его можно было показать в отладочном экране.
 */
object AdsLog {

    private const val TAG = "ConverterAds"
    private const val BUFFER_SIZE = 20

    private val buffer = ArrayDeque<String>()

    @Volatile
    var lastMessage: String? = null
        private set

    fun info(format: String, message: String) {
        record("INFO $format: $message")
        Log.i(TAG, "$format: $message")
    }

    /** code + description приходят из AdRequestError / AdFailureError. */
    fun warn(format: String, code: Int, description: String) {
        record("WARN $format: code=$code $description")
        Log.w(TAG, "$format: code=$code $description")
    }

    fun error(format: String, message: String, throwable: Throwable? = null) {
        record("ERROR $format: $message")
        Log.e(TAG, "$format: $message", throwable)
    }

    fun recentMessages(): List<String> = synchronized(buffer) { buffer.toList() }

    private fun record(line: String) {
        lastMessage = line
        synchronized(buffer) {
            buffer.addLast(line)
            while (buffer.size > BUFFER_SIZE) buffer.removeFirst()
        }
    }
}
