package com.converterplus.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.converterplus.app.ads.Ads
import com.converterplus.app.data.SettingsStore
import com.converterplus.app.data.ThemeMode
import com.converterplus.app.data.UriAccess
import com.converterplus.app.ui.navigation.AppNavHost
import com.converterplus.app.ui.navigation.Routes
import com.converterplus.app.ui.theme.ConverterPlusTheme
import com.converterplus.app.ui.theme.ThemeController
import com.converterplus.app.work.Notifications

/**
 * Единственная Activity. Портретная ориентация, весь интерфейс на Compose.
 */
class MainActivity : AppCompatActivity() {

    // Регистрация до onCreate завершён — иначе поздняя регистрация приводит к падению.
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                // Без разрешения конвертация работает, прогресс в шторке просто не виден.
                SettingsStore.showProgressNotification = false
            }
        }

    private var notificationPermissionRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        SettingsStore.init(this)
        ThemeController.init(this)
        super.onCreate(savedInstanceState)

        UriAccess.init(this)
        Notifications.ensureChannel(this)
        Ads.refreshAdsAllowed(this)

        val startDestination = when {
            !SettingsStore.onboardingDone -> Routes.ONBOARDING
            intent.getBooleanExtra(Notifications.EXTRA_OPEN_HISTORY, false) -> Routes.HISTORY
            else -> Routes.HOME
        }

        setContent {
            val themeMode by ThemeController.mode.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            ConverterPlusTheme(darkTheme = darkTheme) {
                AppNavHost(
                    startDestination = startDestination,
                    requestNotificationPermission = { maybeRequestNotificationPermission() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        applyNightMode()
        Ads.refreshAdsAllowed(this)
        UriAccess.pruneInaccessible(this)
    }

    /**
     * Разрешение на уведомления спрашиваем в момент первой конвертации, а не при запуске.
     * Отказ не мешает работе.
     */
    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (notificationPermissionRequested) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return

        notificationPermissionRequested = true
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun applyNightMode() {
        ThemeController.applyNightMode(SettingsStore.themeMode)
    }
}
