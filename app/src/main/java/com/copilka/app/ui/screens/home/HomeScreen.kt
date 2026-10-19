package com.copilka.app.ui.screens.home

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.copilka.app.R
import com.copilka.app.data.FinanceStore
import com.copilka.app.engine.finance.Accounts
import com.copilka.app.engine.finance.Budget
import com.copilka.app.engine.finance.Categories
import com.copilka.app.engine.finance.FinanceCalculator
import com.copilka.app.engine.finance.Money
import com.copilka.app.engine.finance.Transaction
import com.copilka.app.engine.finance.TransactionType
import com.copilka.app.engine.finance.formatRub
import com.copilka.app.ui.components.SectionHeader
import com.copilka.app.ui.theme.AppDimens
import com.copilka.app.ui.theme.SuccessGreen

@Composable
fun HomeScreen(
    onAddExpense: () -> Unit,
    onAddIncome: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var transactions by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    LaunchedEffect(Unit) {
        transactions = FinanceStore.default(context).loadAll()
    }
    val now = System.currentTimeMillis()
    val (monthStart, monthEnd) = FinanceCalculator.monthBounds(now)
    val monthTx = FinanceCalculator.filterByPeriod(transactions, monthStart, monthEnd)
    val balance = FinanceCalculator.totalBalance(transactions)
    val spent = FinanceCalculator.totalByType(monthTx, TransactionType.EXPENSE)
    val income = FinanceCalculator.totalByType(monthTx, TransactionType.INCOME)
    val defaultBudgets = mapOf(
        "food" to Money(18_500_00),
        "transport" to Money(5_000_00),
        "housing" to Money(25_000_00),
        "fun" to Money(7_000_00),
    )
    val budgets = FinanceCalculator.budgetsForMonth(transactions, defaultBudgets, now)
    val recent = transactions.take(5)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(bottom = AppDimens.BannerSafeGap),
    ) {
        Column(modifier = Modifier.padding(horizontal = AppDimens.ScreenPadding)) {
            Spacer(Modifier.height(AppDimens.SpaceM))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Text(
                text = stringResource(R.string.app_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(AppDimens.BlockGap))
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Column(Modifier.padding(AppDimens.CardPadding).fillMaxWidth()) {
                    Text(stringResource(R.string.home_total_balance), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.bodyMedium)
                    Text(balance.formatRub(), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(AppDimens.SpaceM))
                    Text(
                        text = stringResource(R.string.home_month_spent) + ": " + spent.formatRub(),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = stringResource(R.string.home_month_income) + ": " + income.formatRub(),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(Modifier.height(AppDimens.SpaceM))
            Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.SpaceM), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onAddExpense, modifier = Modifier.weight(1f).height(AppDimens.MinTouchTarget)) {
                    Text(stringResource(R.string.home_add_expense))
                }
                OutlinedButton(onClick = onAddIncome, modifier = Modifier.weight(1f).height(AppDimens.MinTouchTarget)) {
                    Text(stringResource(R.string.home_add_income))
                }
            }
            Spacer(Modifier.height(AppDimens.SpaceXl))
            SectionHeader(text = stringResource(R.string.home_quick_start))
            Spacer(Modifier.height(AppDimens.SpaceS))
            SectionHeader(text = stringResource(R.string.home_budgets))
            if (budgets.isEmpty()) {
                Text(stringResource(R.string.finance_no_budgets), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                budgets.forEach { BudgetRow(it) }
            }
            Spacer(Modifier.height(AppDimens.SpaceXl))
            SectionHeader(text = stringResource(R.string.home_recent))
            if (recent.isEmpty()) {
                Text(
                    text = stringResource(R.string.home_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = AppDimens.SpaceM),
                )
            } else {
                recent.forEach { tx ->
                    TransactionRow(tx, modifier = Modifier.clickable(onClick = onOpenHistory))
                }
            }
            Spacer(Modifier.height(AppDimens.SpaceXl))
            SectionHeader(text = stringResource(R.string.home_all_tools))
            Categories.expense.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.SpaceM), modifier = Modifier.fillMaxWidth().padding(bottom = AppDimens.SpaceM)) {
                    row.forEach { cat ->
                        Card(
                            modifier = Modifier.weight(1f).clickable(onClick = onAddExpense),
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = AppDimens.CardElevation),
                        ) {
                            Column(Modifier.padding(AppDimens.CardPadding)) {
                                Text(stringResource(cat.nameRes), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = FinanceCalculator.spentByCategory(transactions, cat.id, monthStart, monthEnd).formatRub(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(AppDimens.SpaceXs))
        }
    }
}

@Composable
private fun BudgetRow(budget: Budget) {
    val cat = Categories.byId(budget.categoryId)
    val title = cat?.nameRes?.let { stringResource(it) } ?: budget.categoryId
    val hint = if (budget.isOver) {
        stringResource(R.string.finance_budget_over, budget.spent.minus(budget.limit).formatRub())
    } else {
        stringResource(R.string.finance_budget_left, budget.left.formatRub())
    }
    Column(Modifier.fillMaxWidth().padding(vertical = AppDimens.SpaceS)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = if (budget.isOver) MaterialTheme.colorScheme.error else SuccessGreen)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(progress = { budget.progress }, modifier = Modifier.fillMaxWidth().height(8.dp))
    }
}

@Composable
private fun TransactionRow(tx: Transaction, modifier: Modifier = Modifier) {
    val cat = Categories.byId(tx.categoryId)
    val account = Accounts.byId(tx.accountId)
    val amountColor = when (tx.type) {
        TransactionType.EXPENSE -> MaterialTheme.colorScheme.error
        TransactionType.INCOME -> SuccessGreen
        TransactionType.TRANSFER -> MaterialTheme.colorScheme.onSurface
    }
    val prefix = when (tx.type) {
        TransactionType.EXPENSE -> "−"
        TransactionType.INCOME -> "+"
        TransactionType.TRANSFER -> ""
    }
    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = AppDimens.SpaceXs),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = AppDimens.CardElevation),
    ) {
        Row(
            modifier = Modifier.padding(AppDimens.CardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(tx.comment.ifBlank { cat?.let { stringResource(it.nameRes) } ?: tx.categoryId }, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(account.nameRes) + " · " + (cat?.let { stringResource(it.nameRes) } ?: tx.categoryId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(prefix + tx.amount.formatRub(), color = amountColor, style = MaterialTheme.typography.titleMedium)
        }
    }
}
