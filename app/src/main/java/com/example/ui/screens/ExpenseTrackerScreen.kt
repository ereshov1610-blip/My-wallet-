package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.i18n.LocalAppStrings
import com.example.domain.DateUtils
import com.example.domain.ExpenseCategory
import com.example.domain.ExpenseRecord
import com.example.domain.FinanceCalculator
import com.example.ui.components.CategoryEditorDialog
import com.example.ui.components.CategoryIconBadge
import com.example.ui.components.EmptyStateBox
import com.example.ui.components.FinanceCard
import com.example.ui.components.MonoAmountText
import com.example.ui.theme.LocalFinanceColors
import com.example.ui.viewmodel.FinanceUiState

@Composable
fun ExpenseTrackerScreen(
    state: FinanceUiState,
    onAddExpense: () -> Unit,
    onEditExpense: (ExpenseRecord) -> Unit,
    onDeleteExpense: (ExpenseRecord) -> Unit,
    onSaveCategory: (ExpenseCategory) -> Unit,
    onDeleteCategory: (ExpenseCategory) -> Unit
) {
    val strings = LocalAppStrings.current
    val financeColors = LocalFinanceColors.current
    val cur = state.primaryCurrency
    val hide = state.preferences.hideBalances

    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var showManageCategories by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<ExpenseCategory?>(null) }
    var showCategoryEditor by remember { mutableStateOf(false) }

    val filteredExpenses = remember(state.expenses, selectedCategoryId) {
        if (selectedCategoryId == null) state.expenses
        else state.expenses.filter { it.categoryId == selectedCategoryId }
    }
    val grouped = remember(filteredExpenses) { filteredExpenses.groupBy { it.dateIso } }
    val filteredTotal = remember(filteredExpenses, cur, state.exchangeRates) {
        filteredExpenses.sumOf {
            FinanceCalculator.convertCurrency(it.amount, it.currencyEnum(), cur, state.exchangeRates)
        }
    }

    val catColorMap = remember(state.categories) { state.categories.associate { it.id to it.colorHex } }
    val catIconMap = remember(state.categories) { state.categories.associate { it.id to it.iconKey } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.expensesTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalButton(
                        onClick = { showManageCategories = true },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("manage_categories_btn")
                    ) {
                        Icon(Icons.Rounded.Category, contentDescription = strings.manageCategories, modifier = Modifier.size(18.dp))
                    }
                    Button(
                        onClick = onAddExpense,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("expenses_add_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.add)
                    }
                }
            }
        }

        // Expense Summary
        item {
            FinanceCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = strings.totalExpenses,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        MonoAmountText(
                            text = FinanceCalculator.formatCurrency(filteredTotal, cur, hide),
                            color = financeColors.expense,
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = strings.currentMonthExpenses,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        MonoAmountText(
                            text = FinanceCalculator.formatCurrency(state.dashboard.monthExpenses, cur, hide),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Category filter chips
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedCategoryId == null,
                        onClick = { selectedCategoryId = null },
                        label = { Text(strings.all) }
                    )
                }
                items(state.categories, key = { it.id }) { cat ->
                    FilterChip(
                        selected = selectedCategoryId == cat.id,
                        onClick = { selectedCategoryId = if (selectedCategoryId == cat.id) null else cat.id },
                        label = { Text(cat.name) }
                    )
                }
            }
        }

        if (grouped.isEmpty()) {
            item {
                EmptyStateBox(
                    icon = Icons.Rounded.ReceiptLong,
                    message = strings.noDataYet
                )
            }
        } else {
            grouped.forEach { (dateIso, dayList) ->
                val daySum = dayList.sumOf {
                    FinanceCalculator.convertCurrency(it.amount, it.currencyEnum(), cur, state.exchangeRates)
                }
                item(key = "exp_header_$dateIso") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = DateUtils.formatDisplay(dateIso, state.preferences.dateFormat),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "-${FinanceCalculator.formatCurrency(daySum, cur, hide)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = financeColors.expense,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                items(dayList, key = { it.id }) { exp ->
                    FinanceCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CategoryIconBadge(
                                iconKey = catIconMap[exp.categoryId] ?: "category",
                                colorHex = catColorMap[exp.categoryId] ?: 0xFF0A84FF
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = exp.categoryName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                if (!exp.description.isNullOrBlank()) {
                                    Text(
                                        text = exp.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            MonoAmountText(
                                text = "-${FinanceCalculator.formatCurrency(exp.amount, exp.currencyEnum(), hide)}",
                                color = financeColors.expense
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            IconButton(onClick = { onEditExpense(exp) }) {
                                Icon(Icons.Rounded.Edit, contentDescription = strings.edit, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { onDeleteExpense(exp) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = strings.delete, tint = financeColors.expense, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showManageCategories) {
        AlertDialog(
            onDismissRequest = { showManageCategories = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(strings.manageCategories, fontWeight = FontWeight.Bold)
                    TextButton(onClick = {
                        editingCategory = null
                        showCategoryEditor = true
                    }) {
                        Text("+ ${strings.add}")
                    }
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier.height(340.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.categories, key = { it.id }) { cat ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CategoryIconBadge(iconKey = cat.iconKey, colorHex = cat.colorHex, size = 34.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = cat.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                editingCategory = cat
                                showCategoryEditor = true
                            }) {
                                Icon(Icons.Rounded.Edit, contentDescription = strings.edit, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { onDeleteCategory(cat) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = strings.delete, tint = financeColors.expense, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showManageCategories = false }) {
                    Text(strings.close)
                }
            }
        )
    }

    if (showCategoryEditor) {
        CategoryEditorDialog(
            initialCategory = editingCategory,
            onDismiss = { showCategoryEditor = false },
            onSave = { cat ->
                onSaveCategory(cat)
                showCategoryEditor = false
            }
        )
    }
}
