package com.converterplus.app.ui.navigation

import android.content.Context
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.converterplus.app.R
import com.converterplus.app.ads.Ads
import com.converterplus.app.ads.BannerSlot
import com.converterplus.app.ads.ConsentManager
import com.converterplus.app.data.HistoryEntry
import com.converterplus.app.data.HistoryStatus
import com.converterplus.app.data.HistoryStore
import com.converterplus.app.data.SettingsStore
import com.converterplus.app.engine.ToolKind
import com.converterplus.app.storage.FileSaver
import com.converterplus.app.ui.screens.history.HistoryScreen
import com.converterplus.app.ui.screens.history.HistoryViewModel
import com.converterplus.app.ui.screens.home.HomeScreen
import com.converterplus.app.ui.screens.onboarding.OnboardingScreen
import com.converterplus.app.ui.screens.removeads.RemoveAdsScreen
import com.converterplus.app.ui.screens.settings.SettingsScreen
import com.converterplus.app.ui.screens.settings.SettingsViewModel
import com.converterplus.app.ui.screens.tool.ToolScreen
import com.converterplus.app.ui.screens.tool.ToolViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class BottomTab(
    val route: String,
    val labelRes: Int,
    val iconRes: Int,
)

private val bottomTabs = listOf(
    BottomTab(Routes.HOME, R.string.app_name, R.drawable.ic_home),
    BottomTab(Routes.HISTORY, R.string.history_title, R.drawable.ic_history),
    BottomTab(Routes.SETTINGS, R.string.settings_title, R.drawable.ic_settings),
)

/**
 * Навигация: три вкладки внизу, экраны инструментов — поверх.
 * Баннер показывается на «Главной» и в «Истории» (над нижней навигацией, отделён линией).
 */
@Composable
fun AppNavHost(
    startDestination: String,
    requestNotificationPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val adsAllowed by Ads.adsAllowed.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            val isTabRoute = bottomTabs.any { it.route == currentRoute }
            if (isTabRoute) {
                val bannerVisible = adsAllowed &&
                    (currentRoute == Routes.HOME || currentRoute == Routes.HISTORY)
                Column {
                    if (bannerVisible) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        BannerSlot(visible = true)
                    }
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        bottomTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            // Иконка выбранного пункта слегка «подпрыгивает» — переход
                            // по меню становится заметным даже без анимации экрана.
                            val iconScale by animateFloatAsState(
                                targetValue = if (selected) 1.12f else 1f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow,
                                ),
                                label = "tabScale",
                            )
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(navController, tab.route) },
                                icon = {
                                    Icon(
                                        painter = painterResource(tab.iconRes),
                                        contentDescription = stringResource(tab.labelRes),
                                        modifier = Modifier.graphicsLayer {
                                            scaleX = iconScale
                                            scaleY = iconScale
                                        },
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding),
            enterTransition = {
                slideInHorizontally(tween(250)) { it / 4 } + fadeIn(tween(250))
            },
            exitTransition = { fadeOut(tween(150)) },
            popEnterTransition = { fadeIn(tween(200)) },
            popExitTransition = {
                slideOutHorizontally(tween(250)) { it / 4 } + fadeOut(tween(250))
            },
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onFinish = { personalized ->
                        val app = context.applicationContext
                        ConsentManager.setConsentGiven(app, true)
                        ConsentManager.setPersonalizedAds(app, personalized)
                        SettingsStore.onboardingDone = true
                        com.converterplus.app.ConverterApp.onConsentChanged(
                            app as android.app.Application,
                        )
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    },
                )
            }

            composable(Routes.HOME) {
                HomeScreen(
                    onOpenTool = { tool -> navController.navigate(Routes.tool(tool)) },
                )
            }

            composable(Routes.HISTORY) { entry ->
                val viewModel: HistoryViewModel = viewModel(viewModelStoreOwner = entry) {
                    HistoryViewModel(
                        this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!,
                    )
                }
                HistoryScreen(
                    viewModel = viewModel,
                    onSaveRequest = { historyEntry ->
                        saveFromHistory(
                            context = context,
                            scope = scope,
                            entry = historyEntry,
                            onMessage = { messageRes ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(context.getString(messageRes))
                                }
                            },
                            onSaved = { viewModel.refresh() },
                        )
                    },
                )
            }

            composable(Routes.SETTINGS) { entry ->
                val viewModel: SettingsViewModel = viewModel(viewModelStoreOwner = entry) {
                    SettingsViewModel(
                        this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!,
                    )
                }
                SettingsScreen(
                    viewModel = viewModel,
                    onOpenRemoveAds = { navController.navigate(Routes.REMOVE_ADS) },
                )
            }

            composable(Routes.REMOVE_ADS) {
                RemoveAdsScreen(onBack = { navController.popBackStack() })
            }

            composable(
                route = Routes.TOOL,
                arguments = listOf(navArgument(Routes.ARG_TOOL_ID) { type = NavType.StringType }),
            ) { entry ->
                val toolId = entry.arguments?.getString(Routes.ARG_TOOL_ID).orEmpty()
                val tool = ToolKind.fromId(toolId)
                if (tool == null) {
                    navController.popBackStack()
                    return@composable
                }
                val viewModel: ToolViewModel = viewModel(viewModelStoreOwner = entry) {
                    ToolViewModel(
                        this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!,
                        tool,
                    )
                }
                ToolScreen(
                    tool = tool,
                    viewModel = viewModel,
                    adsAllowed = adsAllowed,
                    onBack = { navController.popBackStack() },
                    requestNotificationPermission = requestNotificationPermission,
                )
            }
        }
    }
}

/** Сохранение записи из истории в «Загрузки» (или в выбранную SAF-папку). */
private fun saveFromHistory(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    entry: HistoryEntry,
    onMessage: (Int) -> Unit,
    onSaved: () -> Unit,
) {
    val path = entry.localPath
    if (path == null || !File(path).exists()) {
        onMessage(R.string.history_no_file)
        return
    }
    val treeUri = SettingsStore.saveFolderUri
    val saver = FileSaver(context)
    if (saver.needsDocumentPicker(treeUri)) {
        // API 26–28: без выбранной папки сохранение идёт из экрана инструмента через SAF.
        onMessage(R.string.settings_folder_default)
        return
    }
    scope.launch {
        val result = withContext(Dispatchers.IO) {
            runCatching { saver.save(File(path), entry.fileName, treeUri) }
        }
        result.onSuccess { outcome ->
            val uri = when (outcome) {
                is com.converterplus.app.storage.SaveOutcome.Content -> outcome.uri
                is com.converterplus.app.storage.SaveOutcome.Path -> outcome.path
            }
            withContext(Dispatchers.IO) {
                HistoryStore.default(context)
                    .update(entry.copy(status = HistoryStatus.SAVED, uri = uri))
            }
            onSaved()
            onMessage(
                if (treeUri.isNullOrBlank()) R.string.tool_saved_to_downloads
                else R.string.tool_saved_to_folder,
            )
        }.onFailure {
            onMessage(R.string.err_generic)
        }
    }
}

private fun navigateToTab(navController: NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
