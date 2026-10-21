package com.converterplus.app.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager

/** Кнопка «Отменить» в уведомлении прогресса. */
class CancelConversionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CANCEL) return
        val workName = intent.getStringExtra(EXTRA_WORK_NAME)
        if (workName.isNullOrBlank()) {
            WorkManager.getInstance(context).cancelAllWorkByTag(TAG_CONVERSION)
        } else {
            WorkManager.getInstance(context).cancelUniqueWork(workName)
        }
        Notifications.cancelProgress(context)
    }

    companion object {
        const val ACTION_CANCEL = "com.converterplus.app.action.CANCEL_CONVERSION"
        const val EXTRA_WORK_NAME = "work_name"
        const val TAG_CONVERSION = "conversion"
    }
}
