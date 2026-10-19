package com.copilka.app.ui.navigation

import android.content.Context
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.copilka.app.CopilkaApp
import com.copilka.app.R
import com.copilka.app.ads.Ads
import com.copilka.app.ads.BannerSlot
import com.copilka.app.ads.ConsentManager
import com.copilka.app.data.HistoryEntry
import com.copilka.app.data.HistoryStatus
import com.copilka.app.data.HistoryStore
import com.copilka.app.data.SettingsStore
import com.copilka.app.engine.ToolKind
import com.copilka.app.storage.FileSaver
import com.copilka.app.ui.screens.history.HistoryScreen
import com.copilka.app.ui.screens.history.HistoryViewModel
import com.copilka.app.engine.finance.TransactionType
import com.copilka.app.ui.screens.add.AddScreen
import com.copilka.app.ui.screens.home.HomeScreen
import com.copilka.app.ui.screens.onboarding.OnboardingScreen
import com.copilka.app.ui.screens.removeads.RemoveAdsScreen
import com.copilka.app.ui.screens.settings.SettingsScreen
import com.copilka.app.ui.screens.settings.SettingsViewModel
import com.copilka.app.ui.screens.tool.ToolScreen
import com.copilka.app.ui.screens.tool.ToolViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class BottomTab(val route: String, val labelRes: Int, val iconRes: Int)

private val bottomTabs = listOf(
    BottomTab(Routes.HOME, R.string.app_name, R.drawable.ic_home),
    BottomTab(Routes.HISTORY, R.string.history_title, R.drawable.ic_history),
    BottomTab(Routes.STATS, R.string.home_stats, R.drawable.ic_search),
    BottomTab(Routes.SETTINGS, R.string.settings_title, R.drawable.ic_settings),
)

@Composable
fun AppNavHost(startDestination: String, requestNotificationPermission: () -> Unit, modifier: Modifier = Modifier) {
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
                val bannerVisible = adsAllowed && (currentRoute == Routes.HOME || currentRoute == Routes.HISTORY || currentRoute == Routes.STATS)
                Column {
                    if (bannerVisible) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        BannerSlot(visible = true)
                    }
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        bottomTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            val iconScale by animateFloatAsState(targetValue = if (selected) 1.12f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "tabScale")
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(navController, tab.route) },
                                icon = { Icon(painterResource(tab.iconRes), stringResource(tab.labelRes), modifier = Modifier.graphicsLayer { scaleX = iconScale; scaleY = iconScale }) },
                                label = { Text(stringResource(tab.labelRes)) },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(navController = navController, startDestination = startDestination, modifier = Modifier.padding(padding),
            enterTransition = { slideInHorizontally(tween(250)) { it / 4 } + fadeIn(tween(250)) },
            exitTransition = { fadeOut(tween(150)) },
            popEnterTransition = { fadeIn(tween(200)) },
            popExitTransition = { slideOutHorizontally(tween(250)) { it / 4 } + fadeOut(tween(250)) },
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(onFinish = { personalized ->
                    val app = context.applicationContext
                    ConsentManager.setConsentGiven(app, true)
                    ConsentManager.setPersonalizedAds(app, personalized)
                    SettingsStore.onboardingDone = true
                    CopilkaApp.onConsentChanged(app as android.app.Application)
                    navController.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                })
            }
            composable(Routes.HOME) {
                HomeScreen(
                    onAddExpense = { navController.navigate(Routes.add(TransactionType.EXPENSE.name)) },
                    onAddIncome = { navController.navigate(Routes.add(TransactionType.INCOME.name)) },
                    onOpenHistory = { navigateToTab(navController, Routes.HISTORY) },
                )
            }
            composable(Routes.HISTORY) { entry ->
                val vm: HistoryViewModel = viewModel(viewModelStoreOwner = entry) { HistoryViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!) }
                HistoryScreen(viewModel = vm, onSaveRequest = { he -> saveFromHistory(context, scope, he, { r -> scope.launch { snackbarHostState.showSnackbar(context.getString(r)) } }, { vm.refresh() }) })
            }
            composable(Routes.STATS) {
                // Заглушка статистики — использует те же данные, что и история, но с графиками
                val vm: HistoryViewModel = viewModel { HistoryViewModel(context.applicationContext as android.app.Application) }
                com.copilka.app.ui.screens.stats.StatsScreen(viewModel = vm)
            }
            composable(Routes.SETTINGS) { entry ->
                val vm: SettingsViewModel = viewModel(viewModelStoreOwner = entry) { SettingsViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!) }
                SettingsScreen(viewModel = vm, onOpenRemoveAds = { navController.navigate(Routes.REMOVE_ADS) })
            }
            composable(Routes.REMOVE_ADS) { RemoveAdsScreen(onBack = { navController.popBackStack() }) }
            composable(
                route = Routes.ADD,
                arguments = listOf(navArgument(Routes.ARG_TYPE) { type = NavType.StringType; defaultValue = TransactionType.EXPENSE.name }),
            ) { entry ->
                val raw = entry.arguments?.getString(Routes.ARG_TYPE).orEmpty()
                val type = runCatching { TransactionType.valueOf(raw) }.getOrDefault(TransactionType.EXPENSE)
                AddScreen(
                    initialType = type,
                    onBack = { navController.popBackStack() },
                    requestNotificationPermission = requestNotificationPermission,
                )
            }
            composable(route = Routes.TOOL, arguments = listOf(navArgument(Routes.ARG_TOOL_ID) { type = NavType.StringType })) { entry ->
                val toolId = entry.arguments?.getString(Routes.ARG_TOOL_ID).orEmpty()
                val tool = ToolKind.fromId(toolId)
                if (tool == null) { navController.popBackStack(); return@composable }
                val vm: ToolViewModel = viewModel(viewModelStoreOwner = entry) { ToolViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!, tool) }
                ToolScreen(tool = tool, viewModel = vm, adsAllowed = adsAllowed, onBack = { navController.popBackStack() }, requestNotificationPermission = requestNotificationPermission)
            }
        }
    }
}

private fun saveFromHistory(context: Context, scope: kotlinx.coroutines.CoroutineScope, entry: HistoryEntry, onMessage: (Int) -> Unit, onSaved: () -> Unit) {
    val path = entry.localPath
    if (path == null || !File(path).exists()) { onMessage(R.string.history_no_file); return }
    val treeUri = SettingsStore.saveFolderUri
    val saver = FileSaver(context)
    if (saver.needsDocumentPicker(treeUri)) { onMessage(R.string.settings_folder_default); return }
    scope.launch {
        val result = withContext(Dispatchers.IO) { runCatching { saver.save(File(path), entry.fileName, treeUri) } }
        result.onSuccess { outcome ->
            val uri = when (outcome) { is com.copilka.app.storage.SaveOutcome.Content -> outcome.uri; is com.copilka.app.storage.SaveOutcome.Path -> outcome.path }
            withContext(Dispatchers.IO) { HistoryStore.default(context).update(entry.copy(status = HistoryStatus.SAVED, uri = uri)) }
            onSaved()
            onMessage(if (treeUri.isNullOrBlank()) R.string.tool_saved_to_downloads else R.string.tool_saved_to_folder)
        }.onFailure { onMessage(R.string.err_generic) }
    }
}

private fun navigateToTab(navController: NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true; restoreState = true
    }
}
