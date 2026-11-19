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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.domain.CurrencyCode
import com.example.domain.DateUtils
import com.example.domain.FinanceCalculator
import com.example.domain.SavingsGoal
import com.example.ui.components.CurrencySelectorRow
import com.example.ui.components.DatePickerField
import com.example.ui.components.EmptyStateBox
import com.example.ui.components.FinanceCard
import com.example.ui.components.MonoAmountText
import com.example.ui.theme.LocalFinanceColors
import com.example.ui.viewmodel.FinanceUiState

@Composable
fun SavingsGoalsScreen(
    state: FinanceUiState,
    onBack: () -> Unit,
    onSaveGoal: (SavingsGoal) -> Unit,
    onDeleteGoal: (SavingsGoal) -> Unit,
    onTransferToGoal: (SavingsGoal, Double, String, String?) -> Unit
) {
    val strings = LocalAppStrings.current
    val financeColors = LocalFinanceColors.current
    val cur = state.primaryCurrency
    val hide = state.preferences.hideBalances

    var showGoalDialog by remember { mutableStateOf(false) }
    var editingGoal by remember { mutableStateOf<SavingsGoal?>(null) }
    var transferTargetGoal by remember { mutableStateOf<SavingsGoal?>(null) }

    val monthlyGoal = state.profile?.monthlyIncomeGoal ?: 45000.0
    val monthIncome = state.dashboard.monthIncome
    val monthlyProgress = if (monthlyGoal > 0) (monthIncome / monthlyGoal).toFloat().coerceIn(0f, 1f) else 0f
    val monthlyPercent = if (monthlyGoal > 0) ((monthIncome / monthlyGoal) * 100).toInt() else 0

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
                        text = strings.savingsTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Button(
                    onClick = { editingGoal = null; showGoalDialog = true },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("add_savings_goal_btn")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(strings.add)
                }
            }
        }

        // Monthly Income Goal Card
        item {
            FinanceCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(strings.monthlyIncomeGoal, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = "$monthlyPercent%",
                        style = MaterialTheme.typography.titleMedium,
                        color = financeColors.income,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { monthlyProgress },
                    strokeCap = StrokeCap.Round,
                    color = financeColors.income,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = FinanceCalculator.formatCurrency(monthIncome, cur, hide),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "${strings.remainingToGoal}: ${FinanceCalculator.formatCurrency((monthlyGoal - monthIncome).coerceAtLeast(0.0), cur, hide)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (state.savingsGoals.isEmpty()) {
            item {
                EmptyStateBox(icon = Icons.Rounded.Savings, message = strings.noDataYet)
            }
        } else {
            items(state.savingsGoals, key = { it.id }) { goal ->
                val ratio = if (goal.targetAmount > 0) (goal.currentAmount / goal.targetAmount).toFloat().coerceIn(0f, 1f) else 0f
                val pct = if (goal.targetAmount > 0) ((goal.currentAmount / goal.targetAmount) * 100).toInt() else 0
                val remaining = (goal.targetAmount - goal.currentAmount).coerceAtLeast(0.0)

                FinanceCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(goal.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            if (!goal.targetDateIso.isNullOrBlank()) {
                                Text(
                                    text = DateUtils.formatDisplay(goal.targetDateIso, state.preferences.dateFormat),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            text = "$pct%",
                            style = MaterialTheme.typography.titleMedium,
                            color = financeColors.savings,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { ratio },
                        strokeCap = StrokeCap.Round,
                        color = financeColors.savings,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MonoAmountText(
                            text = "${FinanceCalculator.formatCurrency(goal.currentAmount, goal.currencyEnum(), hide)} / ${FinanceCalculator.formatCurrency(goal.targetAmount, goal.currencyEnum(), hide)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "${strings.remainingToGoal}: ${FinanceCalculator.formatCurrency(remaining, goal.currencyEnum(), hide)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = { transferTargetGoal = goal },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(strings.transferToSavings)
                        }
                        Row {
                            IconButton(onClick = { editingGoal = goal; showGoalDialog = true }) {
                                Icon(Icons.Rounded.Edit, contentDescription = strings.edit, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { onDeleteGoal(goal) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = strings.delete, tint = financeColors.expense, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        if (state.savingsTransfers.isNotEmpty()) {
            item {
                Text(
                    text = strings.transferHistory,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            items(state.savingsTransfers, key = { it.id }) { transfer ->
                FinanceCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(transfer.goalTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = DateUtils.formatDisplay(transfer.dateIso, state.preferences.dateFormat) +
                                    if (!transfer.note.isNullOrBlank()) " • ${transfer.note}" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        MonoAmountText(
                            text = "+${FinanceCalculator.formatCurrency(transfer.amount, transfer.currencyEnum(), hide)}",
                            color = financeColors.savings
                        )
                    }
                }
            }
        }
    }

    if (showGoalDialog) {
        SavingsGoalEditorDialog(
            initialGoal = editingGoal,
            defaultCurrency = cur,
            dateFormat = state.preferences.dateFormat,
            onDismiss = { showGoalDialog = false },
            onSave = { g ->
                onSaveGoal(g)
                showGoalDialog = false
            }
        )
    }

    transferTargetGoal?.let { goal ->
        SavingsTransferDialog(
            goal = goal,
            dateFormat = state.preferences.dateFormat,
            onDismiss = { transferTargetGoal = null },
            onTransfer = { amt, dateIso, note ->
                onTransferToGoal(goal, amt, dateIso, note)
                transferTargetGoal = null
            }
        )
    }
}

@Composable
private fun SavingsGoalEditorDialog(
    initialGoal: SavingsGoal?,
    defaultCurrency: CurrencyCode,
    dateFormat: com.example.domain.DateFormatOption,
    onDismiss: () -> Unit,
    onSave: (SavingsGoal) -> Unit
) {
    val strings = LocalAppStrings.current
    var title by remember { mutableStateOf(initialGoal?.title ?: "") }
    var targetText by remember { mutableStateOf(if (initialGoal != null) initialGoal.targetAmount.toString() else "") }
    var savedText by remember { mutableStateOf(if (initialGoal != null) initialGoal.currentAmount.toString() else "0") }
    var currency by remember { mutableStateOf(initialGoal?.currencyEnum() ?: defaultCurrency) }
    var useTargetDate by remember { mutableStateOf(!initialGoal?.targetDateIso.isNullOrBlank()) }
    var targetDateIso by remember { mutableStateOf(initialGoal?.targetDateIso ?: DateUtils.addMonths(DateUtils.todayIso(), 6)) }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialGoal == null) strings.addSavingsGoal else strings.editSavingsGoal, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; err = null },
                    label = { Text(strings.goalTitle) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = targetText,
                    onValueChange = { targetText = it; err = null },
                    label = { Text(strings.targetAmount) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = savedText,
                    onValueChange = { savedText = it; err = null },
                    label = { Text(strings.currentSaved) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                CurrencySelectorRow(selected = currency, onSelect = { currency = it })
                if (useTargetDate) {
                    DatePickerField(
                        label = strings.targetDateOptional,
                        dateIso = targetDateIso,
                        dateFormat = dateFormat,
                        onDateSelected = { targetDateIso = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    TextButton(onClick = { useTargetDate = true }) {
                        Text("+ ${strings.targetDateOptional}")
                    }
                }
                if (err != null) {
                    Text(err ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val t = targetText.replace(',', '.').toDoubleOrNull()
                val s = savedText.replace(',', '.').toDoubleOrNull() ?: 0.0
                if (title.isBlank()) {
                    err = strings.validationRequired; return@Button
                }
                if (t == null || t <= 0.0 || s < 0.0) {
                    err = strings.validationPositiveNumber; return@Button
                }
                onSave(
                    SavingsGoal(
                        id = initialGoal?.id ?: "",
                        title = title.trim(),
                        targetAmount = t,
                        currentAmount = s,
                        currency = currency.code,
                        targetDateIso = if (useTargetDate) targetDateIso else null
                    )
                )
            }) {
                Text(strings.save)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}

@Composable
private fun SavingsTransferDialog(
    goal: SavingsGoal,
    dateFormat: com.example.domain.DateFormatOption,
    onDismiss: () -> Unit,
    onTransfer: (Double, String, String?) -> Unit
) {
    val strings = LocalAppStrings.current
    var amountText by remember { mutableStateOf("") }
    var dateIso by remember { mutableStateOf(DateUtils.todayIso()) }
    var note by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${strings.transferToSavings}: ${goal.title}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DatePickerField(
                    label = strings.dateLabel,
                    dateIso = dateIso,
                    dateFormat = dateFormat,
                    onDateSelected = { dateIso = it },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; err = null },
                    label = { Text("${strings.transferAmount} (${goal.currencyEnum().symbol})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(strings.notesOptional) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (err != null) {
                    Text(err ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val amt = amountText.replace(',', '.').toDoubleOrNull()
                if (amt == null || amt <= 0.0) {
                    err = strings.validationPositiveNumber; return@Button
                }
                onTransfer(amt, dateIso, note.trim().ifEmpty { null })
            }) {
                Text(strings.confirm)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}
