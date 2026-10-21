package com.converterplus.app.storage

import android.content.Context
import java.io.File

/**
 * Уборка кэша: входные копии и несохранённые результаты старше суток.
 * Вызывается при старте приложения, работает в фоне и ничего не бросает наружу.
 */
object CacheCleaner {

    private const val MAX_AGE_MILLIS = 24L * 60L * 60L * 1000L

    fun cleanOldFiles(context: Context) {
        val thread = Thread {
            runCatching {
                deleteOlderThan(File(context.cacheDir, "inputs"), MAX_AGE_MILLIS)
                deleteOlderThan(File(context.cacheDir, "results"), MAX_AGE_MILLIS)
            }
        }
        thread.isDaemon = true
        thread.name = "cache-cleaner"
        thread.start()
    }

    private fun deleteOlderThan(dir: File, maxAgeMillis: Long) {
        if (!dir.exists()) return
        val threshold = System.currentTimeMillis() - maxAgeMillis
        dir.listFiles()?.forEach { child ->
            if (child.lastModified() < threshold) {
                if (child.isDirectory) deleteOlderThan(child, maxAgeMillis)
                child.delete()
            }
        }
        if (dir.listFiles()?.isEmpty() == true) dir.delete()
    }
}
