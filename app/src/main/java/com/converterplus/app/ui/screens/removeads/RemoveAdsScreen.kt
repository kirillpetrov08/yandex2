package com.converterplus.app.ui.screens.removeads

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.converterplus.app.Config
import com.converterplus.app.R
import com.converterplus.app.ads.Ads
import com.converterplus.app.ads.RewardedOutcome
import com.converterplus.app.ui.theme.AppDimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * «Убрать рекламу»: 30 минут без рекламы за просмотр Rewarded.
 * Подписки и покупок нет — обещаем ровно то, что делаем.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoveAdsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var minutesLeft by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            minutesLeft = Ads.policy(context).adsFreeMinutesLeft()
            delay(1000)
        }
    }

    val rewardCallback: (RewardedOutcome) -> Unit = { outcome ->
        when (outcome) {
            RewardedOutcome.REWARDED -> {
                Ads.policy(context).activateAdsFreePeriod()
                Ads.refreshAdsAllowed(context)
                minutesLeft = Config.NO_ADS_MINUTES
                scope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.remove_ads_thanks))
                }
            }

            RewardedOutcome.NOT_REWARDED -> scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.remove_ads_no_reward))
            }

            RewardedOutcome.FAILED -> scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.remove_ads_unavailable))
            }
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.remove_ads_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(AppDimens.ScreenPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_play),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(64.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .padding(16.dp),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.remove_ads_text),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))

            if (minutesLeft > 0) {
                Text(
                    text = stringResource(R.string.remove_ads_active, "$minutesLeft мин"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
            }

            Button(
                onClick = {
                    val activity = context as? Activity
                    Ads.maybePreloadRewarded(context)
                    val shown = activity?.let { Ads.rewarded().showIfReady(it, rewardCallback) } ?: false
                    if (!shown) {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                context.getString(R.string.remove_ads_unavailable),
                            )
                        }
                    }
                },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                Text(stringResource(R.string.remove_ads_button))
            }
        }
    }
}
