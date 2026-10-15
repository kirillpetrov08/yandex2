package com.converterplus.app.work

import android.content.Context
import android.os.SystemClock
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.converterplus.app.R
import com.converterplus.app.ads.Ads
import com.converterplus.app.data.HistoryEntry
import com.converterplus.app.data.HistoryStatus
import com.converterplus.app.data.HistoryStore
import com.converterplus.app.engine.ConversionEngine
import com.converterplus.app.engine.toMessageRes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Конвертация в фоне.
 *
 * Запускается как long-running работа: [getForegroundInfo] + [setForeground],
 * иначе система убьёт задачу при сворачивании приложения.
 * Прогресс отдаётся через [setProgressAsync], UI подписывается на WorkInfo.
 */
class ConversionWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo =
        Notifications.progressInfo(applicationContext, "", 0)

    override suspend fun doWork(): Result {
        val request = ConversionRequestCodec.decode(inputData.getString(KEY_REQUEST))
            ?: return Result.failure(workDataOf(KEY_ERROR_RES to R.string.err_generic))

        val displayName = request.inputs.firstOrNull()?.displayName.orEmpty()
        enterForeground(displayName)

        val engine = ConversionEngine(applicationContext)
        var lastEmitAt = 0L
        var lastPercent = -1

        val onProgress: (Int) -> Unit = { percent ->
            val now = SystemClock.elapsedRealtime()
            val isFinal = percent >= 100
            if (percent != lastPercent && (isFinal || now - lastEmitAt >= PROGRESS_THROTTLE_MS)) {
                lastEmitAt = now
                lastPercent = percent
                setProgressAsync(workDataOf(KEY_PROGRESS to percent.coerceIn(0, 100)))
                enterForeground(displayName, percent.coerceIn(0, 100))
            }
        }

        val result = try {
            engine.convert(request, onProgress)
        } catch (cancellation: CancellationException) {
            onWorkCancelled()
            throw cancellation
        } catch (t: Throwable) {
            return onWorkFailed(request.id, t)
        }

        return withContext(Dispatchers.IO) {
            val history = HistoryStore.default(applicationContext)
            val now = System.currentTimeMillis()
            val inputFormat = request.inputs.firstOrNull()?.extension?.ifBlank { null } ?: "file"
            val entries = result.artifacts.mapIndexed { index, artifact ->
                HistoryEntry(
                    id = "${request.id}-$index",
                    // Минус index мс: записи одной конвертации получают разное время и стабильный порядок.
                    timestamp = now - index,
                    fileName = artifact.name,
                    fromFormat = inputFormat,
                    toFormat = result.outputFormat,
                    sizeBytes = artifact.bytes,
                    uri = null,
                    status = HistoryStatus.READY,
                    localPath = artifact.path,
                )
            }
            history.addAll(entries)

            // Входные копии НЕ удаляем сразу: они нужны для повторного прогона
            // (например, «убрать водяной знак»). Кэш чистится при старте приложения.

            Ads.policy(applicationContext).noteConversionFinished()
            Notifications.cancelProgress(applicationContext)
            Notifications.notifyDone(applicationContext, displayName, request.id)

            Result.success(
                workDataOf(
                    KEY_PROGRESS to 100,
                    KEY_STATE to STATE_DONE,
                    KEY_REQUEST_ID to request.id,
                    KEY_FILE_COUNT to result.artifacts.size,
                    KEY_INPUT_BYTES to result.inputBytes,
                    KEY_OUTPUT_BYTES to result.outputBytes,
                    KEY_DURATION to result.durationMillis,
                ),
            )
        }
    }

    private fun enterForeground(fileName: String, percent: Int = 0) {
        val info = Notifications.progressInfo(applicationContext, fileName, percent)
        try {
            setForegroundAsync(info)
        } catch (e: IllegalStateException) {
            // Работа уже завершилась или система не разрешила foreground — продолжаем без него.
        }
    }

    private fun onWorkCancelled() {
        Notifications.cancelProgress(applicationContext)
        Ads.policy(applicationContext).noteConversionFinished()
    }

    private fun onWorkFailed(requestId: String, throwable: Throwable): Result {
        val messageRes = throwable.toMessageRes()
        val message = applicationContext.getString(messageRes)
        Notifications.cancelProgress(applicationContext)
        Notifications.notifyFailed(applicationContext, message)
        Ads.policy(applicationContext).noteConversionFinished()
        return Result.failure(
            workDataOf(
                KEY_STATE to STATE_FAILED,
                KEY_ERROR_RES to messageRes,
                KEY_REQUEST_ID to requestId,
            ),
        )
    }

    companion object {
        const val KEY_REQUEST = "request"
        const val KEY_PROGRESS = "progress"
        const val KEY_STATE = "state"
        const val KEY_ERROR_RES = "error_res"
        const val KEY_REQUEST_ID = "request_id"
        const val KEY_FILE_COUNT = "file_count"
        const val KEY_INPUT_BYTES = "input_bytes"
        const val KEY_OUTPUT_BYTES = "output_bytes"
        const val KEY_DURATION = "duration_ms"

        const val STATE_RUNNING = "running"
        const val STATE_DONE = "done"
        const val STATE_FAILED = "failed"
        const val STATE_CANCELLED = "cancelled"

        /** Не чаще одного обновления прогресса в 200 мс. */
        private const val PROGRESS_THROTTLE_MS = 200L

        fun inputData(request: com.converterplus.app.engine.ConversionRequest): Data = workDataOf(
            KEY_REQUEST to ConversionRequestCodec.encode(request),
        )

        fun cancel(context: Context, workName: String) {
            WorkManager.getInstance(context).cancelUniqueWork(workName)
        }

        fun newRequestId(): String = UUID.randomUUID().toString()
    }
}
