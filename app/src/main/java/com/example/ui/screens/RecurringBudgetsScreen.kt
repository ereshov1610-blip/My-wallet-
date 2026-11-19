package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.core.i18n.LocalAppStrings
import com.example.domain.CategoryBudget
import com.example.domain.CurrencyCode
import com.example.domain.DateFormatOption
import com.example.domain.DateUtils
import com.example.domain.ExpenseCategory
import com.example.domain.FinanceCalculator
import com.example.domain.RecurringExpense
import com.example.domain.RecurringFrequency
import com.example.ui.components.CurrencySelectorRow
import com.example.ui.components.DatePickerField
import com.example.ui.components.EmptyStateBox
import com.example.ui.components.FinanceCard
import com.example.ui.components.MonoAmountText
import com.example.ui.theme.LocalFinanceColors
import com.example.ui.viewmodel.FinanceUiState

@Composable
fun RecurringBudgetsScreen(
    state: FinanceUiState,
    onBack: () -> Unit,
    onSaveRecurring: (RecurringExpense) -> Unit,
    onLogRecurringNow: (RecurringExpense) -> Unit,
    onDeleteRecurring: (RecurringExpense) -> Unit,
    onSaveBudget: (CategoryBudget) -> Unit,
    onDeleteBudget: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    val financeColors = LocalFinanceColors.current
    val cur = state.primaryCurrency
    val hide = state.preferences.hideBalances

    var tabIndex by remember { mutableIntStateOf(0) }
    var showRecurringDialog by remember { mutableStateOf(false) }
    var editingRecurring by remember { mutableStateOf<RecurringExpense?>(null) }
    var showBudgetDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text = strings.recurringTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Button(
                    onClick = {
                        if (tabIndex == 0) {
                            editingRecurring = null
                            showRecurringDialog = true
                        } else {
                            showBudgetDialog = true
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("add_recurring_or_budget_btn")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(strings.add)
                }
            }
        }

        item {
            TabRow(selectedTabIndex = tabIndex) {
                Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text(strings.recurringTab) })
                Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text(strings.budgetsTab) })
            }
        }

        if (tabIndex == 0) {
            if (state.recurringExpenses.isEmpty()) {
                item { EmptyStateBox(icon = Icons.Rounded.Repeat, message = strings.noDataYet) }
            } else {
                items(state.recurringExpenses, key = { it.id }) { rec ->
                    val freqLabel = when (rec.frequencyEnum()) {
                        RecurringFrequency.WEEKLY -> strings.freqWeekly
                        RecurringFrequency.MONTHLY -> strings.freqMonthly
                        RecurringFrequency.YEARLY -> strings.freqYearly
                    }
                    FinanceCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(rec.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "${rec.categoryName} • $freqLabel • ${strings.nextDueDate}: ${DateUtils.formatDisplay(rec.nextDueDateIso, state.preferences.dateFormat)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            MonoAmountText(
                                text = FinanceCalculator.formatCurrency(rec.amount, rec.currencyEnum(), hide),
                                color = financeColors.expense
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = rec.active,
                                    onCheckedChange = { onSaveRecurring(rec.copy(active = it)) }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (rec.active) strings.activeStatus else strings.pausedStatus,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilledTonalButton(
                                    onClick = { onLogRecurringNow(rec) },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(strings.logExpenseNow)
                                }
                                IconButton(onClick = { editingRecurring = rec; showRecurringDialog = true }) {
                                    Icon(Icons.Rounded.Edit, contentDescription = strings.edit, modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { onDeleteRecurring(rec) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = strings.delete, tint = financeColors.expense, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Overall Monthly Budget Card
            item {
                val limit = state.overallBudgetLimit.coerceAtLeast(1.0)
                val spent = state.overallBudgetSpent
                val ratio = (spent / limit).toFloat()
                val barColor = when {
                    ratio >= 1.0f -> financeColors.expense
                    ratio >= 0.8f -> financeColors.warning
                    else -> financeColors.income
                }
                FinanceCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(strings.overallMonthlyBudget, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${(ratio * 100).toInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = barColor
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { ratio.coerceIn(0f, 1f) },
                        strokeCap = StrokeCap.Round,
                        color = barColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${strings.budgetUsed}: ${FinanceCalculator.formatCurrency(spent, cur, hide)}", style = MaterialTheme.typography.bodySmall)
                        Text("${strings.budgetLimit}: ${FinanceCalculator.formatCurrency(limit, cur, hide)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (state.budgetUsages.isEmpty()) {
                item { EmptyStateBox(icon = Icons.Rounded.AccountBalanceWallet, message = strings.noDataYet) }
            } else {
                items(state.budgetUsages, key = { it.budgetId }) { usage ->
                    val statusColor = when {
                        usage.isExceeded -> financeColors.expense
                        usage.isApproaching -> financeColors.warning
                        else -> financeColors.income
                    }
                    FinanceCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(usage.categoryName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (usage.isExceeded || usage.isApproaching) {
                                    Surface(
                                        color = statusColor.copy(alpha = 0.14f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = if (usage.isExceeded) strings.budgetExceededBadge else strings.budgetWarningBadge,
                                            color = statusColor,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                IconButton(onClick = { onDeleteBudget(usage.budgetId) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = strings.delete, tint = financeColors.expense, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        LinearProgressIndicator(
                            progress = { usage.ratio.coerceIn(0f, 1f) },
                            strokeCap = StrokeCap.Round,
                            color = statusColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            MonoAmountText(
                                text = "${FinanceCalculator.formatCurrency(usage.spentAmount, cur, hide)} / ${FinanceCalculator.formatCurrency(usage.limitAmount, cur, hide)}",
                                color = statusColor,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text("${(usage.ratio * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = statusColor)
                        }
                    }
                }
            }
        }
    }

    if (showRecurringDialog) {
        RecurringEditorDialog(
            initialItem = editingRecurring,
            categories = state.categories,
            defaultCurrency = cur,
            dateFormat = state.preferences.dateFormat,
            onDismiss = { showRecurringDialog = false },
            onSave = { r ->
                onSaveRecurring(r)
                showRecurringDialog = false
            }
        )
    }

    if (showBudgetDialog) {
        CategoryBudgetDialog(
            categories = state.categories,
            defaultCurrency = cur,
            onDismiss = { showBudgetDialog = false },
            onSave = { b ->
                onSaveBudget(b)
                showBudgetDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecurringEditorDialog(
    initialItem: RecurringExpense?,
    categories: List<ExpenseCategory>,
    defaultCurrency: CurrencyCode,
    dateFormat: DateFormatOption,
    onDismiss: () -> Unit,
    onSave: (RecurringExpense) -> Unit
) {
    val strings = LocalAppStrings.current
    var title by remember { mutableStateOf(initialItem?.title ?: "") }
    var amountText by remember { mutableStateOf(if (initialItem != null) initialItem.amount.toString() else "") }
    var currency by remember { mutableStateOf(initialItem?.currencyEnum() ?: defaultCurrency) }
    var selectedCatId by remember { mutableStateOf(initialItem?.categoryId ?: categories.firstOrNull()?.id ?: "cat_other") }
    var frequency by remember { mutableStateOf(initialItem?.frequencyEnum() ?: RecurringFrequency.MONTHLY) }
    var nextDueIso by remember { mutableStateOf(initialItem?.nextDueDateIso ?: DateUtils.todayIso()) }
    var note by remember { mutableStateOf(initialItem?.note ?: "") }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialItem == null) strings.addRecurring else strings.editRecurring, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; err = null },
                    label = { Text(strings.expenseTitleLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; err = null },
                    label = { Text(strings.amountLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                CurrencySelectorRow(selected = currency, onSelect = { currency = it })
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = frequency == RecurringFrequency.WEEKLY, onClick = { frequency = RecurringFrequency.WEEKLY }, label = { Text(strings.freqWeekly) })
                    FilterChip(selected = frequency == RecurringFrequency.MONTHLY, onClick = { frequency = RecurringFrequency.MONTHLY }, label = { Text(strings.freqMonthly) })
                    FilterChip(selected = frequency == RecurringFrequency.YEARLY, onClick = { frequency = RecurringFrequency.YEARLY }, label = { Text(strings.freqYearly) })
                }
                DatePickerField(
                    label = strings.nextDueDate,
                    dateIso = nextDueIso,
                    dateFormat = dateFormat,
                    onDateSelected = { nextDueIso = it },
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.take(6).forEach { cat ->
                        FilterChip(
                            selected = cat.id == selectedCatId,
                            onClick = { selectedCatId = cat.id },
                            label = { Text(cat.name) }
                        )
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(strings.notesOptional) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (err != null) Text(err ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = {
                val amt = amountText.replace(',', '.').toDoubleOrNull()
                if (title.isBlank()) { err = strings.validationRequired; return@Button }
                if (amt == null || amt <= 0.0) { err = strings.validationPositiveNumber; return@Button }
                val cat = categories.firstOrNull { it.id == selectedCatId }
                onSave(
                    RecurringExpense(
                        id = initialItem?.id ?: "",
                        title = title.trim(),
                        amount = amt,
                        currency = currency.code,
                        categoryId = cat?.id ?: selectedCatId,
                        categoryName = cat?.name ?: strings.catOther,
                        frequency = frequency.name,
                        nextDueDateIso = nextDueIso,
                        active = initialItem?.active ?: true,
                        note = note.trim().ifEmpty { null }
                    )
                )
            }) { Text(strings.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryBudgetDialog(
    categories: List<ExpenseCategory>,
    defaultCurrency: CurrencyCode,
    onDismiss: () -> Unit,
    onSave: (CategoryBudget) -> Unit
) {
    val strings = LocalAppStrings.current
    var selectedCatId by remember { mutableStateOf(categories.firstOrNull()?.id ?: "cat_food") }
    var limitText by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf(defaultCurrency) }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.addCategoryBudget, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = cat.id == selectedCatId,
                            onClick = { selectedCatId = cat.id },
                            label = { Text(cat.name) }
                        )
                    }
                }
                OutlinedTextField(
                    value = limitText,
                    onValueChange = { limitText = it; err = null },
                    label = { Text(strings.budgetLimit) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                CurrencySelectorRow(selected = currency, onSelect = { currency = it })
                if (err != null) Text(err ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = {
                val lim = limitText.replace(',', '.').toDoubleOrNull()
                if (lim == null || lim <= 0.0) { err = strings.validationPositiveNumber; return@Button }
                val cat = categories.firstOrNull { it.id == selectedCatId }
                onSave(
                    CategoryBudget(
                        categoryId = cat?.id ?: selectedCatId,
                        categoryName = cat?.name ?: strings.catOther,
                        limitAmount = lim,
                        currency = currency.code,
                        monthKey = "ALL"
                    )
                )
            }) { Text(strings.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } }
    )
}
