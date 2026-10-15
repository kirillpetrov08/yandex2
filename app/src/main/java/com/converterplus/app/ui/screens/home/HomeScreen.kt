package com.converterplus.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
        Column(modifier = Modifier.padding(horizontal = AppDimens.ScreenPadding)) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(R.string.app_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(20.dp))
        SectionHeader(
            text = stringResource(R.string.home_quick_start),
            modifier = Modifier.padding(horizontal = AppDimens.ScreenPadding),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = AppDimens.ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(ToolCatalog.quickStart, key = { _, tool -> tool.kind.id }) { index, tool ->
                QuickStartCard(
                    title = stringResource(id = tool.titleRes),
                    subtitle = stringResource(id = tool.subtitleRes),
                    gradient = QuickStartGradients[index % QuickStartGradients.size],
                    index = index,
                    onClick = { onOpenTool(tool.kind) },
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        SectionHeader(
            text = stringResource(R.string.home_all_tools),
            modifier = Modifier.padding(horizontal = AppDimens.ScreenPadding),
        )
        ToolsGrid(onOpenTool = onOpenTool)
    }
}

/** Сетка 2×3: ряды по две карточки. */
@Composable
private fun ToolsGrid(onOpenTool: (ToolKind) -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = AppDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ToolCatalog.all.chunked(2).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
        Spacer(Modifier.height(4.dp))
    }
}
