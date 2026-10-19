package com.copilka.app.ui.screens.add

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.copilka.app.R
import com.copilka.app.ads.Ads
import com.copilka.app.data.FinanceStore
import com.copilka.app.engine.finance.Accounts
import com.copilka.app.engine.finance.Categories
import com.copilka.app.engine.finance.Money
import com.copilka.app.engine.finance.RepeatPeriod
import com.copilka.app.engine.finance.Transaction
import com.copilka.app.engine.finance.TransactionType
import com.copilka.app.ui.theme.AppDimens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddScreen(
    initialType: TransactionType,
    onBack: () -> Unit,
    requestNotificationPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var type by remember { mutableStateOf(initialType) }
    var amountText by remember { mutableStateOf("") }
    var categoryId by remember {
        mutableStateOf(if (initialType == TransactionType.INCOME) Categories.defaultIncome().id else Categories.defaultExpense().id)
    }
    var accountId by remember { mutableStateOf(Accounts.default().id) }
    var comment by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf(RepeatPeriod.NEVER) }
    val categories = if (type == TransactionType.INCOME) Categories.income else Categories.expense

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_add)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.cd_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimens.ScreenPadding),
        ) {
            Spacer(Modifier.height(AppDimens.SpaceM))
            Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.SpaceS)) {
                FilterChip(selected = type == TransactionType.EXPENSE, onClick = { type = TransactionType.EXPENSE; categoryId = Categories.defaultExpense().id }, label = { Text(stringResource(R.string.finance_expense)) })
                FilterChip(selected = type == TransactionType.INCOME, onClick = { type = TransactionType.INCOME; categoryId = Categories.defaultIncome().id }, label = { Text(stringResource(R.string.finance_income)) })
                FilterChip(selected = type == TransactionType.TRANSFER, onClick = { type = TransactionType.TRANSFER }, label = { Text(stringResource(R.string.finance_transfer)) })
            }
            Spacer(Modifier.height(AppDimens.SpaceL))
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' } },
                label = { Text(stringResource(R.string.finance_amount)) },
                placeholder = { Text(stringResource(R.string.finance_amount_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(AppDimens.SpaceM))
            Text(stringResource(R.string.finance_category), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(AppDimens.SpaceS))
            Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.SpaceS), modifier = Modifier.fillMaxWidth()) {
                categories.take(3).forEach { cat ->
                    FilterChip(selected = categoryId == cat.id, onClick = { categoryId = cat.id }, label = { Text(stringResource(cat.nameRes)) })
                }
            }
            Spacer(Modifier.height(AppDimens.SpaceS))
            Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.SpaceS), modifier = Modifier.fillMaxWidth()) {
                categories.drop(3).forEach { cat ->
                    FilterChip(selected = categoryId == cat.id, onClick = { categoryId = cat.id }, label = { Text(stringResource(cat.nameRes)) })
                }
            }
            Spacer(Modifier.height(AppDimens.SpaceM))
            Text(stringResource(R.string.finance_account), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(AppDimens.SpaceS))
            Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.SpaceS)) {
                Accounts.all.forEach { acc ->
                    FilterChip(selected = accountId == acc.id, onClick = { accountId = acc.id }, label = { Text(stringResource(acc.nameRes)) })
                }
            }
            Spacer(Modifier.height(AppDimens.SpaceM))
            Text(stringResource(R.string.finance_date) + " · " + stringResource(R.string.finance_today), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.finance_receipt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(AppDimens.SpaceM))
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                label = { Text(stringResource(R.string.finance_comment)) },
                placeholder = { Text(stringResource(R.string.finance_comment_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(AppDimens.SpaceM))
            Text(stringResource(R.string.finance_repeat), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(AppDimens.SpaceS))
            Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.SpaceS)) {
                FilterChip(selected = repeat == RepeatPeriod.NEVER, onClick = { repeat = RepeatPeriod.NEVER }, label = { Text(stringResource(R.string.finance_repeat_never)) })
                FilterChip(selected = repeat == RepeatPeriod.MONTHLY, onClick = { repeat = RepeatPeriod.MONTHLY }, label = { Text(stringResource(R.string.finance_repeat_monthly)) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.SpaceS)) {
                FilterChip(selected = repeat == RepeatPeriod.WEEKLY, onClick = { repeat = RepeatPeriod.WEEKLY }, label = { Text(stringResource(R.string.finance_repeat_weekly)) })
                FilterChip(selected = repeat == RepeatPeriod.DAILY, onClick = { repeat = RepeatPeriod.DAILY }, label = { Text(stringResource(R.string.finance_repeat_daily)) })
            }
            Spacer(Modifier.height(AppDimens.SpaceXl))
            Button(
                onClick = {
                    val cents = parseAmount(amountText)
                    if (cents == null || cents <= 0L) {
                        scope.launch { snackbar.showSnackbar(context.getString(R.string.err_no_input)) }
                        return@Button
                    }
                    requestNotificationPermission()
                    val tx = Transaction(
                        id = FinanceStore.newId(),
                        type = type,
                        amount = Money(cents),
                        categoryId = categoryId,
                        accountId = accountId,
                        comment = comment.trim(),
                        createdAt = System.currentTimeMillis(),
                        repeat = repeat,
                    )
                    scope.launch {
                        withContext(Dispatchers.IO) { FinanceStore.default(context).add(tx) }
                        Ads.policy(context).noteConversionFinished()
                        val activity = context as? Activity
                        if (activity != null && Ads.policy(context).canShowInterstitial()) {
                            val shown = Ads.interstitial().showIfReady(activity) {
                                Ads.policy(context).noteInterstitialShown()
                                Ads.refreshAdsAllowed(context)
                            }
                            if (!shown) Ads.maybePreloadInterstitial(context)
                        }
                        snackbar.showSnackbar(context.getString(R.string.tool_result_title))
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(AppDimens.MinTouchTarget),
            ) {
                Text(stringResource(R.string.action_save))
            }
            Spacer(Modifier.height(AppDimens.SpaceXl))
        }
    }
}

private fun parseAmount(raw: String): Long? {
    val normalized = raw.trim().replace(" ", "").replace(',', '.')
    if (normalized.isEmpty()) return null
    val value = normalized.toDoubleOrNull() ?: return null
    return (value * 100.0).toLong()
}
