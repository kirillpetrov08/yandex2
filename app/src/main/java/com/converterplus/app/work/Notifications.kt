package com.converterplus.app.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ForegroundInfo
import com.converterplus.app.MainActivity
import com.converterplus.app.R
import com.converterplus.app.data.SettingsStore

/**
 * Канал и уведомления конвертации: прогресс с кнопкой «Отменить» и отдельное «Готово».
 *
 * Если разрешение POST_NOTIFICATIONS не выдано, отправка просто не происходит:
 * конвертация продолжает работать, ничего не падает.
 */
object Notifications {

    const val CHANNEL_ID = "conversion"
    const val NOTIFICATION_ID_PROGRESS = 1001
    const val NOTIFICATION_ID_DONE = 1002
    const val NOTIFICATION_ID_ERROR = 1003

    const val EXTRA_OPEN_HISTORY = "open_history"
    const val EXTRA_REQUEST_ID = "request_id"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notif_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun progressInfo(context: Context, fileName: String, percent: Int): ForegroundInfo {
        val notification = progressNotification(context, fileName, percent)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID_PROGRESS,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID_PROGRESS, notification)
        }
    }

    fun progressNotification(context: Context, fileName: String, percent: Int): android.app.Notification {
        val cancelIntent = Intent(context, CancelConversionReceiver::class.java).apply {
            action = CancelConversionReceiver.ACTION_CANCEL
        }
        val cancelPending = PendingIntent.getBroadcast(
            context,
            NOTIFICATION_ID_PROGRESS,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_progress_title))
            .setContentText(if (fileName.isBlank()) null else fileName)
            .setProgress(100, percent.coerceIn(0, 100), false)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, context.getString(R.string.notif_action_cancel), cancelPending)
            .build()
    }

    fun notifyDone(context: Context, fileName: String, requestId: String) {
        if (!SettingsStore.showProgressNotification) return
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_HISTORY, true)
            putExtra(EXTRA_REQUEST_ID, requestId)
        }
        val pending = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_DONE,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_check_circle)
            .setContentTitle(context.getString(R.string.notif_done_title))
            .setContentText(context.getString(R.string.notif_done_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(fileName))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        safeNotify(context, NOTIFICATION_ID_DONE, notification)
    }

    fun notifyFailed(context: Context, message: String) {
        if (!SettingsStore.showProgressNotification) return
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_HISTORY, true)
        }
        val pending = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_ERROR,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_close)
            .setContentTitle(context.getString(R.string.notif_failed_title))
            .setContentText(context.getString(R.string.notif_failed_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        safeNotify(context, NOTIFICATION_ID_ERROR, notification)
    }

    fun cancelProgress(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_PROGRESS)
    }

    private fun safeNotify(context: Context, id: Int, notification: android.app.Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Разрешение не выдано — прогресс просто не виден, работа не прерывается.
        }
    }
}
