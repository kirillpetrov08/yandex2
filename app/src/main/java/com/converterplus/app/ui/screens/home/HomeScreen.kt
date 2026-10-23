package com.converterplus.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.converterplus.app.R
import com.converterplus.app.engine.ToolKind
import com.converterplus.app.ui.components.QuickStartCard
import com.converterplus.app.ui.components.SectionHeader
import com.converterplus.app.ui.components.ToolCard
import com.converterplus.app.ui.navigation.ToolCatalog
import com.converterplus.app.ui.theme.AppDimens
import com.converterplus.app.ui.theme.QuickStartGradients

/**
 * Главный экран: заголовок, лента «Быстрый старт» (3 карточки), сетка 2×3 со всеми инструментами.
 */
@Composable
fun HomeScreen(
    onOpenTool: (ToolKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(bottom = AppDimens.BannerSafeGap),
    ) {
        Spacer(Modifier.height(AppDimens.SpaceM))
        Row(
            modifier = Modifier.padding(horizontal = AppDimens.ScreenPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(AppDimens.ButtonMedium)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_tool_image),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(AppDimens.IconXLarge),
                )
            }
            Spacer(Modifier.width(AppDimens.SpaceM))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1,
                )
                Text(
                    text = stringResource(R.string.app_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }

        Spacer(Modifier.height(AppDimens.BlockGap))
        SectionHeader(
            text = stringResource(R.string.home_quick_start),
            modifier = Modifier.padding(horizontal = AppDimens.ScreenPadding),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = AppDimens.ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpaceM),
        ) {
            itemsIndexed(ToolCatalog.quickStart, key = { _, tool -> tool.kind.id }) { index, tool ->
                QuickStartCard(
                    title = stringResource(id = tool.titleRes),
                    subtitle = stringResource(id = tool.subtitleRes),
                    iconRes = tool.iconRes,
                    gradient = QuickStartGradients[index % QuickStartGradients.size],
                    index = index,
                    onClick = { onOpenTool(tool.kind) },
                )
            }
        }

        Spacer(Modifier.height(AppDimens.SpaceXl))
        SectionHeader(
            text = stringResource(R.string.home_all_tools),
            modifier = Modifier.padding(horizontal = AppDimens.ScreenPadding),
        )
        ToolsGrid(onOpenTool = onOpenTool)
    }
}

/** Сетка: ряды по две карточки. */
@Composable
private fun ToolsGrid(onOpenTool: (ToolKind) -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = AppDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(AppDimens.SpaceM),
    ) {
        ToolCatalog.all.chunked(2).forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.SpaceM),
            ) {
                row.forEachIndexed { columnIndex, tool ->
                    ToolCard(
                        title = stringResource(id = tool.titleRes),
                        subtitle = stringResource(id = tool.subtitleRes),
                        iconRes = tool.iconRes,
                        accent = tool.accent,
                        available = tool.available,
                        index = rowIndex * 2 + columnIndex,
                        onClick = { onOpenTool(tool.kind) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.width(0.dp).weight(1f))
            }
        }
        Spacer(Modifier.height(AppDimens.SpaceXs))
    }
}
