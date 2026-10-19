package com.copilka.app

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.copilka.app.ads.Ads
import com.copilka.app.data.SettingsStore
import com.copilka.app.data.ThemeMode
import com.copilka.app.data.UriAccess
import com.copilka.app.ui.navigation.AppNavHost
import com.copilka.app.ui.navigation.Routes
import com.copilka.app.ui.theme.CopilkaTheme
import com.copilka.app.ui.theme.ThemeController
import com.copilka.app.work.Notifications

class MainActivity : AppCompatActivity() {
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) SettingsStore.showProgressNotification = false
        }
    private var notificationPermissionRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        SettingsStore.init(this)
        applyNightMode()
        super.onCreate(savedInstanceState)
        UriAccess.init(this)
        ThemeController.init(this)
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
            CopilkaTheme(darkTheme = darkTheme) {
                AppNavHost(
                    startDestination = startDestination,
                    requestNotificationPermission = { maybeRequestNotificationPermission() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent) }
    override fun onResume() { super.onResume(); Ads.refreshAdsAllowed(this) }

    private fun applyNightMode() {
        val mode = SettingsStore.themeMode
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                ThemeMode.LIGHT -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                ThemeMode.DARK -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                ThemeMode.SYSTEM -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    private fun maybeRequestNotificationPermission() {
        if (notificationPermissionRequested) return
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) return
        notificationPermissionRequested = true
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
