package com.copilka.app.ui.screens.stats

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.copilka.app.R
import com.copilka.app.data.FinanceStore
import com.copilka.app.engine.finance.CategoryKind
import com.copilka.app.engine.finance.FinanceCalculator
import com.copilka.app.engine.finance.Money
import com.copilka.app.engine.finance.formatRub
import com.copilka.app.ui.screens.history.HistoryViewModel
import com.copilka.app.ui.theme.AppDimens
import com.copilka.app.ui.theme.AccentBlue

@Composable
fun StatsScreen(viewModel: HistoryViewModel, modifier: Modifier = Modifier) {
    // Используем FinanceStore для статистики, история конвертаций остаётся для совместимости
    val context = androidx.compose.ui.platform.LocalContext.current
    val finance = remember { FinanceStore.default(context).loadAll() }
    var period by remember { mutableStateOf("month") }
    val now = System.currentTimeMillis()
    val (start, end) = when (period) {
        "week" -> FinanceCalculator.weekBounds(now)
        "month" -> FinanceCalculator.monthBounds(now)
        else -> 0L to Long.MAX_VALUE
    }
    val filtered = FinanceCalculator.filterByPeriod(finance, start, end)
    val expenseTotal = FinanceCalculator.totalByType(filtered, com.copilka.app.engine.finance.TransactionType.EXPENSE)
    val incomeTotal = FinanceCalculator.totalByType(filtered, com.copilka.app.engine.finance.TransactionType.INCOME)
    val breakdown = FinanceCalculator.breakdownByCategory(filtered, CategoryKind.EXPENSE)

    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AppDimens.ScreenPadding)) {
        Text(stringResource(R.string.home_stats), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.finance_period), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(AppDimens.SpaceM))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = period == "week", onClick = { period = "week" }, label = { Text(stringResource(R.string.finance_this_week)) })
            FilterChip(selected = period == "month", onClick = { period = "month" }, label = { Text(stringResource(R.string.finance_this_month)) })
            FilterChip(selected = period == "all", onClick = { period = "all" }, label = { Text(stringResource(R.string.finance_all_time)) })
        }
        Spacer(Modifier.height(AppDimens.SpaceM))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(title = stringResource(R.string.finance_expense), amount = expenseTotal, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
            StatCard(title = stringResource(R.string.finance_income), amount = incomeTotal, color = AccentBlue, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(AppDimens.SpaceM))
        Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(AppDimens.CardPadding)) {
                Text(stringResource(R.string.finance_expense), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                if (breakdown.isEmpty()) {
                    Text(stringResource(R.string.history_empty_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    PieChart(breakdown = breakdown, modifier = Modifier.size(180.dp).align(Alignment.CenterHorizontally))
                    Spacer(Modifier.height(12.dp))
                    BreakdownList(breakdown)
                }
            }
        }
        Spacer(Modifier.height(AppDimens.SpaceM))
        Text(stringResource(R.string.finance_accounts), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        // Баланс
        val balance = FinanceCalculator.totalBalance(filtered)
        Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(AppDimens.CardPadding)) {
                Text(stringResource(R.string.home_total_balance), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(balance.formatRub(), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Spacer(Modifier.height(AppDimens.SpaceM))
        Text(stringResource(R.string.finance_export), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.finance_export_csv), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.finance_export_pdf), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.finance_chart_month) + " · " + stringResource(R.string.finance_chart_year), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.action_edit) + " · " + stringResource(R.string.action_done) + " · " + stringResource(R.string.home_add_transfer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.finance_add_budget) + " · " + stringResource(R.string.finance_budget) + " · " + stringResource(R.string.finance_budget_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.finance_search_hint) + " · " + stringResource(R.string.finance_yesterday), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun StatCard(title: String, amount: Money, color: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(AppDimens.CardPadding)) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(amount.formatRub(), style = MaterialTheme.typography.titleLarge, color = color)
        }
    }
}

@Composable
private fun PieChart(breakdown: Map<String, Money>, modifier: Modifier = Modifier) {
    val total = breakdown.values.sumOf { it.cents }
    if (total == 0L) return
    val colors = listOf(Color(0xFF1B8C4A), Color(0xFF0E7A9B), Color(0xFFE6B422), Color(0xFFD94F30), Color(0xFF7A5C1E), Color(0xFF8B3A8C))
    Canvas(modifier = modifier) {
        var startAngle = -90f
        breakdown.values.forEachIndexed { idx, v ->
            val sweep = (v.cents.toFloat() / total.toFloat()) * 360f
            drawArc(color = colors[idx % colors.size], startAngle = startAngle, sweepAngle = sweep, useCenter = true, topLeft = Offset.Zero, size = Size(size.width, size.height))
            startAngle += sweep
        }
    }
}

@Composable
private fun BreakdownList(breakdown: Map<String, Money>) {
    val total = breakdown.values.sumOf { it.cents }
    breakdown.entries.sortedByDescending { it.value.cents }.forEach { (cat, money) ->
        val pct = if (total == 0L) 0 else (money.cents * 100 / total).toInt()
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(cat, style = MaterialTheme.typography.bodyMedium)
            Text("${money.formatRub()} · $pct%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
