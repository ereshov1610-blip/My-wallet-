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
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import com.example.domain.CurrencyCode
import com.example.domain.DateFormatOption
import com.example.domain.DateUtils
import com.example.domain.DebtDirection
import com.example.domain.DebtRecord
import com.example.domain.DebtStatus
import com.example.domain.FinanceCalculator
import com.example.ui.components.CurrencySelectorRow
import com.example.ui.components.DatePickerField
import com.example.ui.components.EmptyStateBox
import com.example.ui.components.FinanceCard
import com.example.ui.components.MonoAmountText
import com.example.ui.theme.LocalFinanceColors
import com.example.ui.viewmodel.FinanceUiState

@Composable
fun DebtManagementScreen(
    state: FinanceUiState,
    onBack: () -> Unit,
    onSaveDebt: (DebtRecord) -> Unit,
    onDeleteDebt: (DebtRecord) -> Unit
) {
    val strings = LocalAppStrings.current
    val financeColors = LocalFinanceColors.current
    val cur = state.primaryCurrency
    val hide = state.preferences.hideBalances

    var selectedTab by remember { mutableIntStateOf(0) }
    val activeDirection = if (selectedTab == 0) DebtDirection.I_OWE else DebtDirection.OWED_TO_ME

    var showEditor by remember { mutableStateOf(false) }
    var editingDebt by remember { mutableStateOf<DebtRecord?>(null) }
    var paymentTarget by remember { mutableStateOf<DebtRecord?>(null) }

    val filtered = remember(state.debts, activeDirection) {
        state.debts.filter { it.directionEnum() == activeDirection }
    }
    val totalRemaining = remember(filtered, cur, state.exchangeRates) {
        filtered.sumOf {
            FinanceCalculator.convertCurrency(it.remainingAmount, it.currencyEnum(), cur, state.exchangeRates)
        }
    }

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
                        text = strings.debtsTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Button(
                    onClick = { editingDebt = null; showEditor = true },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("add_debt_btn")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(strings.add)
                }
            }
        }

        item {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text(strings.iOweTab) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text(strings.owedToMeTab) })
            }
        }

        item {
            FinanceCard {
                Text(
                    text = strings.remainingDebt,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                MonoAmountText(
                    text = FinanceCalculator.formatCurrency(totalRemaining, cur, hide),
                    color = if (activeDirection == DebtDirection.I_OWE) financeColors.expense else financeColors.income,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        }

        if (filtered.isEmpty()) {
            item {
                EmptyStateBox(icon = Icons.Rounded.CreditCard, message = strings.noDataYet)
            }
        } else {
            items(filtered, key = { it.id }) { debt ->
                val statusEnum = debt.statusEnum()
                val statusLabel = when (statusEnum) {
                    DebtStatus.UNPAID -> strings.statusUnpaid
                    DebtStatus.PARTIAL -> strings.statusPartial
                    DebtStatus.PAID -> strings.statusPaid
                }
                val statusColor = when (statusEnum) {
                    DebtStatus.UNPAID -> financeColors.expense
                    DebtStatus.PARTIAL -> financeColors.warning
                    DebtStatus.PAID -> financeColors.income
                }
                val progress = if (debt.totalAmount > 0) (debt.paidAmount / debt.totalAmount).toFloat().coerceIn(0f, 1f) else 0f

                FinanceCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(debt.personName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            val dueStr = debt.dueDateIso?.takeIf { it.isNotBlank() }?.let {
                                " • ${strings.paymentDeadline}: ${DateUtils.formatDisplay(it, state.preferences.dateFormat)}"
                            } ?: ""
                            Text(
                                text = DateUtils.formatDisplay(debt.dateIso, state.preferences.dateFormat) + dueStr,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            color = statusColor.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = statusLabel,
                                color = statusColor,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        strokeCap = StrokeCap.Round,
                        color = statusColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${strings.paidAmount}: ${FinanceCalculator.formatCurrency(debt.paidAmount, debt.currencyEnum(), hide)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        MonoAmountText(
                            text = "${strings.remainingDebt}: ${FinanceCalculator.formatCurrency(debt.remainingAmount, debt.currencyEnum(), hide)}",
                            color = statusColor,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (!debt.note.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(debt.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (statusEnum != DebtStatus.PAID) {
                            FilledTonalButton(
                                onClick = { paymentTarget = debt },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(strings.recordPayment)
                            }
                        } else {
                            Spacer(Modifier.width(1.dp))
                        }
                        Row {
                            IconButton(onClick = { editingDebt = debt; showEditor = true }) {
                                Icon(Icons.Rounded.Edit, contentDescription = strings.edit, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { onDeleteDebt(debt) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = strings.delete, tint = financeColors.expense, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        DebtEditorDialog(
            initialDebt = editingDebt,
            defaultDirection = activeDirection,
            defaultCurrency = cur,
            dateFormat = state.preferences.dateFormat,
            onDismiss = { showEditor = false },
            onSave = { d ->
                onSaveDebt(d)
                showEditor = false
            }
        )
    }

    paymentTarget?.let { debt ->
        DebtPaymentDialog(
            debt = debt,
            onDismiss = { paymentTarget = null },
            onApplyPayment = { addedPayment ->
                val newPaid = (debt.paidAmount + addedPayment).coerceAtMost(debt.totalAmount)
                onSaveDebt(debt.copy(paidAmount = newPaid))
                paymentTarget = null
            }
        )
    }
}

@Composable
private fun DebtEditorDialog(
    initialDebt: DebtRecord?,
    defaultDirection: DebtDirection,
    defaultCurrency: CurrencyCode,
    dateFormat: DateFormatOption,
    onDismiss: () -> Unit,
    onSave: (DebtRecord) -> Unit
) {
    val strings = LocalAppStrings.current
    var direction by remember { mutableStateOf(initialDebt?.directionEnum() ?: defaultDirection) }
    var personName by remember { mutableStateOf(initialDebt?.personName ?: "") }
    var totalText by remember { mutableStateOf(if (initialDebt != null) initialDebt.totalAmount.toString() else "") }
    var paidText by remember { mutableStateOf(if (initialDebt != null) initialDebt.paidAmount.toString() else "0") }
    var currency by remember { mutableStateOf(initialDebt?.currencyEnum() ?: defaultCurrency) }
    var dateIso by remember { mutableStateOf(initialDebt?.dateIso ?: DateUtils.todayIso()) }
    var useDue by remember { mutableStateOf(!initialDebt?.dueDateIso.isNullOrBlank()) }
    var dueDateIso by remember { mutableStateOf(initialDebt?.dueDateIso ?: DateUtils.addDays(DateUtils.todayIso(), 14)) }
    var note by remember { mutableStateOf(initialDebt?.note ?: "") }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialDebt == null) strings.addDebt else strings.editDebt, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.FilterChip(
                        selected = direction == DebtDirection.I_OWE,
                        onClick = { direction = DebtDirection.I_OWE },
                        label = { Text(strings.iOweTab) }
                    )
                    androidx.compose.material3.FilterChip(
                        selected = direction == DebtDirection.OWED_TO_ME,
                        onClick = { direction = DebtDirection.OWED_TO_ME },
                        label = { Text(strings.owedToMeTab) }
                    )
                }
                OutlinedTextField(
                    value = personName,
                    onValueChange = { personName = it; err = null },
                    label = { Text(strings.personOrOrg) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = totalText,
                    onValueChange = { totalText = it; err = null },
                    label = { Text(strings.amountLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = paidText,
                    onValueChange = { paidText = it; err = null },
                    label = { Text(strings.paidAmount) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                CurrencySelectorRow(selected = currency, onSelect = { currency = it })
                DatePickerField(
                    label = strings.dateLabel,
                    dateIso = dateIso,
                    dateFormat = dateFormat,
                    onDateSelected = { dateIso = it },
                    modifier = Modifier.fillMaxWidth()
                )
                if (useDue) {
                    DatePickerField(
                        label = strings.paymentDeadline,
                        dateIso = dueDateIso,
                        dateFormat = dateFormat,
                        onDateSelected = { dueDateIso = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    TextButton(onClick = { useDue = true }) { Text("+ ${strings.paymentDeadline}") }
                }
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
                val total = totalText.replace(',', '.').toDoubleOrNull()
                val paid = paidText.replace(',', '.').toDoubleOrNull() ?: 0.0
                if (personName.isBlank()) {
                    err = strings.validationRequired; return@Button
                }
                if (total == null || total <= 0.0 || paid < 0.0) {
                    err = strings.validationPositiveNumber; return@Button
                }
                onSave(
                    DebtRecord(
                        id = initialDebt?.id ?: "",
                        direction = direction.name,
                        personName = personName.trim(),
                        totalAmount = total,
                        paidAmount = paid.coerceAtMost(total),
                        currency = currency.code,
                        dateIso = dateIso,
                        dueDateIso = if (useDue) dueDateIso else null,
                        note = note.trim().ifEmpty { null }
                    )
                )
            }) { Text(strings.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } }
    )
}

@Composable
private fun DebtPaymentDialog(
    debt: DebtRecord,
    onDismiss: () -> Unit,
    onApplyPayment: (Double) -> Unit
) {
    val strings = LocalAppStrings.current
    var amountText by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${strings.recordPayment}: ${debt.personName}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "${strings.remainingDebt}: ${FinanceCalculator.formatCurrency(debt.remainingAmount, debt.currencyEnum())}",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; err = null },
                    label = { Text(strings.paymentAmount) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = { amountText = debt.remainingAmount.toString() }) {
                    Text(strings.markFullyPaid)
                }
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
                onApplyPayment(amt)
            }) { Text(strings.confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } }
    )
}
