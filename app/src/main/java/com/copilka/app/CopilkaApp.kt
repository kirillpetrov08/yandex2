package com.copilka.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.Configuration
import com.copilka.app.ads.Ads
import com.copilka.app.data.SettingsStore
import com.copilka.app.storage.CacheCleaner

class CopilkaApp : Application(), Configuration.Provider {
    override fun onCreate() {
        super.onCreate()
        SettingsStore.init(this)
        createNotificationChannel()
        CacheCleaner.cleanOldFiles(this)
        Ads.initIfConsented(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.INFO)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            "copilka_ops",
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = getString(R.string.notif_channel_description) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        fun onConsentChanged(app: Application) {
            Ads.initIfConsented(app)
        }
    }
}
