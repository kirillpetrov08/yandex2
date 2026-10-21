package com.converterplus.app.ui.screens.onboarding

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.converterplus.app.ui.theme.AppDimens
import com.converterplus.app.ui.theme.QuickStartGradients

private data class OnboardingPage(
    val iconRes: Int,
    val titleRes: Int,
    val textRes: Int,
)

private val pages = listOf(
    OnboardingPage(R.drawable.ic_tool_image, R.string.onboarding_title_1, R.string.onboarding_text_1),
    OnboardingPage(R.drawable.ic_folder_open, R.string.onboarding_title_2, R.string.onboarding_text_2),
    OnboardingPage(R.drawable.ic_play, R.string.onboarding_title_3, R.string.onboarding_text_3),
)

/**
 * Онбординг первого запуска. Рекламы здесь нет вообще.
 * На третьем экране — согласие на данные для рекламы: SDK инициализируется только после него.
 */
@Composable
fun OnboardingScreen(
    onFinish: (personalizedAds: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var page by remember { mutableIntStateOf(0) }
    var personalized by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Box(modifier = modifier.fillMaxSize()) {
        TextButton(
            onClick = { onFinish(false) },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(AppDimens.SpaceM),
        ) {
            Text(text = stringResource(R.string.action_skip))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AppDimens.SpaceXl)
                .padding(top = 96.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    (slideInHorizontally(tween(250)) { it / 3 } + fadeIn(tween(250))) togetherWith
                        (slideOutHorizontally(tween(250)) { -it / 3 } + fadeOut(tween(250)))
                },
                label = "onboardingPage",
            ) { currentPage ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(132.dp)
                            .clip(MaterialTheme.shapes.extraLarge)
                            .background(
                                androidx.compose.ui.graphics.Brush.linearGradient(
                                    QuickStartGradients[currentPage % QuickStartGradients.size],
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(id = pages[currentPage].iconRes),
                            contentDescription = stringResource(R.string.cd_app_logo),
                            tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.size(56.dp),
                        )
                    }
                    Spacer(Modifier.height(28.dp))
                    Text(
                        text = stringResource(id = pages[currentPage].titleRes),
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(id = pages[currentPage].textRes),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(Modifier.height(36.dp))

            if (page == pages.lastIndex) {
                ConsentBlock(
                    personalized = personalized,
                    onPersonalizedChange = { personalized = it },
                    onOpenPrivacy = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(Config.PRIVACY_POLICY_URL)),
                            )
                        }
                    },
                )
                Spacer(Modifier.height(20.dp))
            }

            Button(
                onClick = {
                    if (page < pages.lastIndex) page++ else onFinish(personalized)
                },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                Text(
                    text = if (page < pages.lastIndex) {
                        stringResource(R.string.action_next)
                    } else {
                        stringResource(R.string.onboarding_finish)
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpaceS),
        ) {
            pages.forEachIndexed { index, _ ->
                Box(
                    modifier = Modifier
                        .size(if (index == page) 10.dp else 8.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(
                            if (index == page) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                        ),
                )
            }
        }
    }
}

@Composable
private fun ConsentBlock(
    personalized: Boolean,
    onPersonalizedChange: (Boolean) -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface)
            .padding(AppDimens.CardPadding),
    ) {
        Text(
            text = stringResource(R.string.onboarding_consent_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(AppDimens.SpaceXs))
        Text(
            text = stringResource(R.string.onboarding_consent_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(AppDimens.SpaceM))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // weight — иначе длинная подпись сжимает переключатель на узком экране.
            Text(
                text = stringResource(R.string.onboarding_consent_switch),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(AppDimens.SpaceM))
            Switch(checked = personalized, onCheckedChange = onPersonalizedChange)
        }
        TextButton(onClick = onOpenPrivacy) {
            Text(text = stringResource(R.string.onboarding_privacy_link))
        }
    }
}
