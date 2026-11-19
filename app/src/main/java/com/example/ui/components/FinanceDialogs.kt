package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.core.i18n.LocalAppStrings
import com.example.domain.CurrencyCode
import com.example.domain.DateFormatOption
import com.example.domain.DateUtils
import com.example.domain.ExpenseCategory
import com.example.domain.ExpenseRecord
import com.example.domain.FinanceCalculator
import com.example.domain.WorkPayType
import com.example.domain.WorkShift
import com.example.ui.theme.LocalFinanceColors

@Composable
fun WorkShiftEditorDialog(
    initialShift: WorkShift?,
    defaultDateIso: String,
    defaultCurrency: CurrencyCode,
    dateFormat: DateFormatOption,
    onDismiss: () -> Unit,
    onSave: (WorkShift) -> Unit
) {
    val strings = LocalAppStrings.current
    val financeColors = LocalFinanceColors.current

    var dateIso by remember { mutableStateOf(initialShift?.dateIso ?: defaultDateIso) }
    var companyName by remember { mutableStateOf(initialShift?.companyName ?: "") }
    var startTime by remember { mutableStateOf(initialShift?.startTime ?: "08:00") }
    var endTime by remember { mutableStateOf(initialShift?.endTime ?: "17:00") }
    var forceOvernight by remember { mutableStateOf(initialShift?.overnight ?: false) }
    var payType by remember { mutableStateOf(initialShift?.payTypeEnum() ?: WorkPayType.FIXED) }
    var rateOrFixedText by remember {
        mutableStateOf(
            if (initialShift != null && initialShift.rateOrFixedAmount > 0)
                initialShift.rateOrFixedAmount.toString()
            else ""
        )
    }
    var currency by remember { mutableStateOf(initialShift?.currencyEnum() ?: defaultCurrency) }
    var notes by remember { mutableStateOf(initialShift?.notes ?: "") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val (durationMinutes, isOvernight) = remember(startTime, endTime, forceOvernight) {
        FinanceCalculator.calculateShiftDurationMinutes(startTime, endTime, forceOvernight)
    }
    val rateOrFixed = rateOrFixedText.replace(',', '.').toDoubleOrNull() ?: 0.0
    val calculatedEarnings = remember(payType, rateOrFixed, durationMinutes) {
        FinanceCalculator.calculateShiftEarnings(payType, rateOrFixed, durationMinutes)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialShift == null) strings.newWorkShift else strings.editWorkShift,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DatePickerField(
                    label = strings.dateLabel,
                    dateIso = dateIso,
                    dateFormat = dateFormat,
                    onDateSelected = { dateIso = it },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "shift_date_picker"
                )

                OutlinedTextField(
                    value = companyName,
                    onValueChange = { companyName = it; errorMsg = null },
                    label = { Text(strings.companyOrJobName) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("shift_company_input")
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TimePickerField(
                        label = strings.startTime,
                        timeHHmm = startTime,
                        onTimeSelected = {
                            startTime = it
                            forceOvernight = false
                        },
                        modifier = Modifier.weight(1f),
                        testTag = "shift_start_time"
                    )
                    TimePickerField(
                        label = strings.endTime,
                        timeHHmm = endTime,
                        onTimeSelected = {
                            endTime = it
                            forceOvernight = false
                        },
                        modifier = Modifier.weight(1f),
                        testTag = "shift_end_time"
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = strings.overnightShift,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = isOvernight,
                        onCheckedChange = { forceOvernight = it },
                        modifier = Modifier.testTag("shift_overnight_switch")
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = payType == WorkPayType.FIXED,
                        onClick = { payType = WorkPayType.FIXED },
                        label = { Text(strings.payTypeFixed) },
                        modifier = Modifier.testTag("shift_pay_fixed")
                    )
                    FilterChip(
                        selected = payType == WorkPayType.HOURLY,
                        onClick = { payType = WorkPayType.HOURLY },
                        label = { Text(strings.payTypeHourly) },
                        modifier = Modifier.testTag("shift_pay_hourly")
                    )
                }

                OutlinedTextField(
                    value = rateOrFixedText,
                    onValueChange = { rateOrFixedText = it; errorMsg = null },
                    label = {
                        Text(if (payType == WorkPayType.HOURLY) strings.hourlyRate else strings.fixedPay)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("shift_amount_input")
                )

                CurrencySelectorRow(selected = currency, onSelect = { currency = it })

                Surface(
                    color = financeColors.incomeBg,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = strings.totalHours,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = FinanceCalculator.formatHours(durationMinutes) +
                                    if (isOvernight) " (${strings.overnightNextDayBadge})" else "",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = strings.totalEarnings,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            MonoAmountText(
                                text = FinanceCalculator.formatCurrency(calculatedEarnings, currency),
                                color = financeColors.income
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(strings.notesOptional) },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMsg != null) {
                    Text(
                        text = errorMsg ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedName = companyName.trim()
                    if (trimmedName.isEmpty()) {
                        errorMsg = strings.validationRequired
                        return@Button
                    }
                    if (rateOrFixed < 0.0) {
                        errorMsg = strings.validationPositiveNumber
                        return@Button
                    }
                    onSave(
                        WorkShift(
                            id = initialShift?.id ?: "",
                            dateIso = dateIso,
                            companyName = trimmedName,
                            startTime = startTime,
                            endTime = endTime,
                            overnight = isOvernight,
                            durationMinutes = durationMinutes,
                            payType = payType.name,
                            rateOrFixedAmount = rateOrFixed,
                            totalEarnings = calculatedEarnings,
                            currency = currency.code,
                            notes = notes.trim().ifEmpty { null }
                        )
                    )
                },
                modifier = Modifier.testTag("shift_save_button")
            ) {
                Text(strings.save)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpenseEditorDialog(
    initialExpense: ExpenseRecord?,
    categories: List<ExpenseCategory>,
    defaultDateIso: String,
    defaultCurrency: CurrencyCode,
    dateFormat: DateFormatOption,
    onDismiss: () -> Unit,
    onSave: (ExpenseRecord) -> Unit
) {
    val strings = LocalAppStrings.current
    var dateIso by remember { mutableStateOf(initialExpense?.dateIso ?: defaultDateIso) }
    var amountText by remember {
        mutableStateOf(
            if (initialExpense != null && initialExpense.amount > 0) initialExpense.amount.toString() else ""
        )
    }
    var currency by remember { mutableStateOf(initialExpense?.currencyEnum() ?: defaultCurrency) }
    var selectedCatId by remember {
        mutableStateOf(
            initialExpense?.categoryId ?: categories.firstOrNull()?.id ?: "cat_other"
        )
    }
    var description by remember { mutableStateOf(initialExpense?.description ?: "") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialExpense == null) strings.newExpense else strings.editExpense,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DatePickerField(
                    label = strings.dateLabel,
                    dateIso = dateIso,
                    dateFormat = dateFormat,
                    onDateSelected = { dateIso = it },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "expense_date_picker"
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; errorMsg = null },
                    label = { Text(strings.amountLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("expense_amount_input")
                )

                CurrencySelectorRow(selected = currency, onSelect = { currency = it })

                Text(
                    text = strings.categoryLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = cat.id == selectedCatId,
                            onClick = { selectedCatId = cat.id },
                            label = { Text(cat.name) },
                            leadingIcon = {
                                Icon(
                                    imageVector = categoryIconForKey(cat.iconKey),
                                    contentDescription = null,
                                    tint = Color(cat.colorHex),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(strings.notesOptional) },
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("expense_note_input")
                )

                if (errorMsg != null) {
                    Text(
                        text = errorMsg ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.replace(',', '.').toDoubleOrNull()
                    if (amt == null || amt <= 0.0) {
                        errorMsg = strings.validationPositiveNumber
                        return@Button
                    }
                    val cat = categories.firstOrNull { it.id == selectedCatId }
                    onSave(
                        ExpenseRecord(
                            id = initialExpense?.id ?: "",
                            dateIso = dateIso,
                            amount = amt,
                            currency = currency.code,
                            categoryId = cat?.id ?: selectedCatId,
                            categoryName = cat?.name ?: strings.catOther,
                            description = description.trim().ifEmpty { null },
                            recurringId = initialExpense?.recurringId
                        )
                    )
                },
                modifier = Modifier.testTag("expense_save_button")
            ) {
                Text(strings.save)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryEditorDialog(
    initialCategory: ExpenseCategory?,
    onDismiss: () -> Unit,
    onSave: (ExpenseCategory) -> Unit
) {
    val strings = LocalAppStrings.current
    var name by remember { mutableStateOf(initialCategory?.name ?: "") }
    var iconKey by remember { mutableStateOf(initialCategory?.iconKey ?: "category") }
    var colorHex by remember { mutableLongStateOf(initialCategory?.colorHex ?: 0xFF0A84FF) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialCategory == null) strings.addCategory else strings.editCategory,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorMsg = null },
                    label = { Text(strings.categoryName) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(strings.selectIcon, style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categoryIconChoices.forEach { key ->
                        val selected = key == iconKey
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) Color(colorHex).copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { iconKey = key }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = categoryIconForKey(key),
                                    contentDescription = key,
                                    tint = if (selected) Color(colorHex) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Text(strings.selectColor, style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    categoryColorChoices.forEach { c ->
                        val selected = c == colorHex
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .then(
                                    if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier
                                )
                                .clickable { colorHex = c }
                        )
                    }
                }

                if (errorMsg != null) {
                    Text(
                        text = errorMsg ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val trimmed = name.trim()
                if (trimmed.isEmpty()) {
                    errorMsg = strings.validationRequired
                    return@Button
                }
                onSave(
                    ExpenseCategory(
                        id = initialCategory?.id ?: "",
                        name = trimmed,
                        iconKey = iconKey,
                        colorHex = colorHex
                    )
                )
            }) {
                Text(strings.save)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}
