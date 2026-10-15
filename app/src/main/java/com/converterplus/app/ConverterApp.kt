package com.converterplus.app

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.converterplus.app.ads.Ads
import com.converterplus.app.ads.ConsentManager
import com.converterplus.app.data.SettingsStore
import com.converterplus.app.storage.CacheCleaner
import com.converterplus.app.work.Notifications

class ConverterApp : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.ERROR)
            .build()

    override fun onCreate() {
        super.onCreate()
        SettingsStore.init(this)
        Notifications.ensureChannel(this)
        CacheCleaner.cleanOldFiles(this)
        // Реклама инициализируется ТОЛЬКО если согласие уже получено (после онбординга).
        // На первом запуске согласия нет — SDK не стартует.
        Ads.initIfConsented(this)
    }

    companion object {
        /** Согласие получено/изменено — (пере)инициализируем SDK с актуальным значением. */
        fun onConsentChanged(app: Application) {
            ConsentManager.persistConsentTimestamp(app)
            Ads.initIfConsented(app)
        }
    }
}
