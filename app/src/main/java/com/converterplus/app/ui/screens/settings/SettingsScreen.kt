package com.converterplus.app.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.converterplus.app.Config
import com.converterplus.app.R
import com.converterplus.app.data.ThemeMode
import com.converterplus.app.engine.QualityPreset
import com.converterplus.app.ui.components.SectionHeader
import com.converterplus.app.ui.theme.AppDimens

/**
 * Настройки. Рекламы здесь нет намеренно — экран чистый.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenRemoveAds: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            viewModel.setSaveFolder(uri.toString())
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppDimens.ScreenPadding)
            .padding(bottom = 24.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
        )

        Section(text = stringResource(R.string.settings_section_appearance)) {
            OptionRow(
                title = stringResource(R.string.settings_theme),
                subtitle = stringResource(
                    id = when (state.themeMode) {
                        ThemeMode.LIGHT -> R.string.settings_theme_light
                        ThemeMode.DARK -> R.string.settings_theme_dark
                        ThemeMode.SYSTEM -> R.string.settings_theme_system
                    },
                ),
                options = listOf(
                    ThemeMode.LIGHT to R.string.settings_theme_light,
                    ThemeMode.DARK to R.string.settings_theme_dark,
                    ThemeMode.SYSTEM to R.string.settings_theme_system,
                ),
                selected = state.themeMode,
                onSelect = viewModel::setTheme,
            )
            ToggleRow(
                title = stringResource(R.string.settings_haptics),
                checked = state.hapticsEnabled,
                onChange = viewModel::setHaptics,
            )
        }

        Section(text = stringResource(R.string.settings_section_files)) {
            OptionRow(
                title = stringResource(R.string.settings_quality),
                subtitle = "",
                options = listOf(
                    QualityPreset.MAX to R.string.settings_quality_max,
                    QualityPreset.BALANCED to R.string.settings_quality_balanced,
                    QualityPreset.MIN_SIZE to R.string.settings_quality_min,
                ),
                selected = state.quality,
                onSelect = viewModel::setQuality,
            )
            ClickableRow(
                title = stringResource(R.string.settings_folder),
                subtitle = if (state.saveFolderUri == null) {
                    stringResource(R.string.settings_folder_default)
                } else {
                    stringResource(R.string.settings_folder_chosen)
                },
                onClick = { pickFolder.launch(null) },
            )
            if (state.saveFolderUri != null) {
                TextButton(onClick = { viewModel.setSaveFolder(null) }) {
                    Text(stringResource(R.string.settings_folder_reset))
                }
            }
            ToggleRow(
                title = stringResource(R.string.settings_notifications),
                checked = state.showProgressNotification,
                onChange = viewModel::setShowProgressNotification,
            )
        }

        Section(text = stringResource(R.string.settings_section_ads)) {
            ClickableRow(
                title = stringResource(R.string.settings_remove_ads),
                subtitle = if (state.adsFreeMinutesLeft > 0) {
                    stringResource(R.string.remove_ads_active, formatMinutes(state.adsFreeMinutesLeft))
                } else {
                    stringResource(R.string.remove_ads_text)
                },
                onClick = onOpenRemoveAds,
            )
            ToggleRow(
                title = stringResource(R.string.settings_personalized),
                checked = state.personalizedAds,
                onChange = viewModel::setPersonalizedAds,
            )
        }

        Section(text = stringResource(R.string.settings_section_about)) {
            ClickableRow(
                title = stringResource(R.string.settings_version, state.versionName),
                subtitle = stringResource(R.string.app_subtitle),
                onClick = { openUrl(context, Config.STORE_URL) },
            )
            ClickableRow(
                title = stringResource(R.string.settings_privacy),
                subtitle = Config.PRIVACY_POLICY_URL,
                onClick = { openUrl(context, Config.PRIVACY_POLICY_URL) },
            )
            ClickableRow(
                title = stringResource(R.string.settings_write_developer),
                subtitle = Config.SUPPORT_EMAIL,
                onClick = { writeEmail(context) },
            )
        }
    }
}

private fun formatMinutes(minutes: Int): String = "$minutes мин"

@Composable
private fun Section(text: String, content: @Composable () -> Unit) {
    Column {
        SectionHeader(text = text, modifier = Modifier.padding(top = 22.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 6.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun ClickableRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun <T> OptionRow(
    title: String,
    subtitle: String,
    options: List<Pair<T, Int>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                val isSelected = option.first == selected
                Text(
                    text = stringResource(id = option.second),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(
                            if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        )
                        .clickable { onSelect(option.first) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        // Нет приложения для открытия ссылок — молча игнорируем.
    }
}

private fun writeEmail(context: Context) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:${Config.SUPPORT_EMAIL}")
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.app_name))
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // Нет почтового клиента.
    }
}
