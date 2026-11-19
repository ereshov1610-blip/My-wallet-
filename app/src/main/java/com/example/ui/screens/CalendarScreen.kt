package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventNote
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.core.i18n.LocalAppStrings
import com.example.domain.DateUtils
import com.example.domain.ExpenseRecord
import com.example.domain.FinanceCalculator
import com.example.domain.WorkShift
import com.example.ui.components.CategoryIconBadge
import com.example.ui.components.EmptyStateBox
import com.example.ui.components.FinanceCard
import com.example.ui.components.MonoAmountText
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.LocalFinanceColors
import com.example.ui.viewmodel.FinanceUiState

@Composable
fun CalendarScreen(
    state: FinanceUiState,
    onSelectDate: (String) -> Unit,
    onAddShiftForDate: (String) -> Unit,
    onEditShift: (WorkShift) -> Unit,
    onDuplicateShift: (WorkShift) -> Unit,
    onDeleteShift: (WorkShift) -> Unit,
    onAddExpenseForDate: (String) -> Unit,
    onEditExpense: (ExpenseRecord) -> Unit,
    onDeleteExpense: (ExpenseRecord) -> Unit
) {
    val strings = LocalAppStrings.current
    val financeColors = LocalFinanceColors.current
    val cur = state.primaryCurrency
    val hide = state.preferences.hideBalances

    val initialParsed = remember(state.selectedDateIso) {
        DateUtils.parseIso(state.selectedDateIso) ?: Triple(2026, 11, 18)
    }
    var viewYear by remember { mutableIntStateOf(initialParsed.first) }
    var viewMonth by remember { mutableIntStateOf(initialParsed.second) }

    val shiftDates = remember(state.shifts) { state.shifts.map { it.dateIso }.toSet() }
    val expenseDates = remember(state.expenses) { state.expenses.map { it.dateIso }.toSet() }

    val dayShifts = remember(state.shifts, state.selectedDateIso) {
        state.shifts.filter { it.dateIso == state.selectedDateIso }
    }
    val dayExpenses = remember(state.expenses, state.selectedDateIso) {
        state.expenses.filter { it.dateIso == state.selectedDateIso }
    }

    val dayIncome = remember(dayShifts, cur, state.exchangeRates) {
        dayShifts.sumOf {
            FinanceCalculator.convertCurrency(it.totalEarnings, it.currencyEnum(), cur, state.exchangeRates)
        }
    }
    val dayExpenseTotal = remember(dayExpenses, cur, state.exchangeRates) {
        dayExpenses.sumOf {
            FinanceCalculator.convertCurrency(it.amount, it.currencyEnum(), cur, state.exchangeRates)
        }
    }
    val dayMinutes = remember(dayShifts) { dayShifts.sumOf { it.durationMinutes } }

    val daysInMonth = remember(viewYear, viewMonth) { DateUtils.daysInMonth(viewYear, viewMonth) }
    val firstWeekdayOffset = remember(viewYear, viewMonth) { DateUtils.dayOfWeekMondayFirst(viewYear, viewMonth, 1) }

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
                    text = strings.calendarTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                TextButton(
                    onClick = {
                        val today = DateUtils.todayIso()
                        DateUtils.parseIso(today)?.let { (y, m, _) ->
                            viewYear = y
                            viewMonth = m
                        }
                        onSelectDate(today)
                    },
                    modifier = Modifier.testTag("calendar_today_btn")
                ) {
                    Text(strings.today)
                }
            }
        }

        // Monthly Calendar Card
        item {
            FinanceCard {
                // Month & Year Switcher
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                if (viewMonth == 1) {
                                    viewMonth = 12
                                    viewYear -= 1
                                } else viewMonth -= 1
                            },
                            modifier = Modifier.testTag("calendar_prev_month")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous month")
                        }
                        Text(
                            text = "${strings.monthNames[(viewMonth - 1).coerceIn(0, 11)]} $viewYear",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = {
                                if (viewMonth == 12) {
                                    viewMonth = 1
                                    viewYear += 1
                                } else viewMonth += 1
                            },
                            modifier = Modifier.testTag("calendar_next_month")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next month")
                        }
                    }

                    // Year Quick Jump
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewYear -= 1 }) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous year", modifier = Modifier.size(18.dp))
                        }
                        Text(
                            text = viewYear.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = { viewYear += 1 }) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next year", modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Weekday headers
                Row(modifier = Modifier.fillMaxWidth()) {
                    strings.weekdayShort.forEach { dayName ->
                        Text(
                            text = dayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Calendar Grid
                val totalCells = firstWeekdayOffset + daysInMonth
                val rows = (totalCells + 6) / 7
                val todayIso = DateUtils.todayIso()

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (r in 0 until rows) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            for (c in 0..6) {
                                val cellIdx = r * 7 + c
                                val dayNum = cellIdx - firstWeekdayOffset + 1
                                if (dayNum in 1..daysInMonth) {
                                    val cellIso = DateUtils.formatIso(viewYear, viewMonth, dayNum)
                                    val isSelected = cellIso == state.selectedDateIso
                                    val isToday = cellIso == todayIso
                                    val hasIncome = cellIso in shiftDates
                                    val hasExpense = cellIso in expenseDates

                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                when {
                                                    isSelected -> BrandBlue
                                                    isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                                    else -> androidx.compose.ui.graphics.Color.Transparent
                                                }
                                            )
                                            .then(
                                                if (isToday && !isSelected)
                                                    Modifier.border(1.dp, BrandBlue, RoundedCornerShape(12.dp))
                                                else Modifier
                                            )
                                            .clickable { onSelectDate(cellIso) }
                                            .testTag("calendar_day_$dayNum")
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = dayNum.toString(),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) androidx.compose.ui.graphics.Color.White
                                                else MaterialTheme.colorScheme.onSurface
                                            )
                                            if (hasIncome || hasExpense) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                                    if (hasIncome) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(5.dp)
                                                                .clip(CircleShape)
                                                                .background(
                                                                    if (isSelected) androidx.compose.ui.graphics.Color.White
                                                                    else financeColors.income
                                                                )
                                                        )
                                                    }
                                                    if (hasExpense) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(5.dp)
                                                                .clip(CircleShape)
                                                                .background(
                                                                    if (isSelected) androidx.compose.ui.graphics.Color(0xFFFCA5A5)
                                                                    else financeColors.expense
                                                                )
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Selected Day Summary & Quick Add
        item {
            FinanceCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = strings.selectedDayActivity,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = DateUtils.formatDisplay(state.selectedDateIso, state.preferences.dateFormat),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = FinanceCalculator.formatHours(dayMinutes),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = financeColors.incomeBg,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(strings.totalIncome, style = MaterialTheme.typography.labelSmall)
                            MonoAmountText(
                                text = FinanceCalculator.formatCurrency(dayIncome, cur, hide),
                                color = financeColors.income
                            )
                        }
                    }
                    Surface(
                        color = financeColors.expenseBg,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(strings.totalExpenses, style = MaterialTheme.typography.labelSmall)
                            MonoAmountText(
                                text = FinanceCalculator.formatCurrency(dayExpenseTotal, cur, hide),
                                color = financeColors.expense
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onAddShiftForDate(state.selectedDateIso) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("calendar_add_shift")
                    ) {
                        Icon(Icons.Rounded.Work, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.addShift)
                    }
                    FilledTonalButton(
                        onClick = { onAddExpenseForDate(state.selectedDateIso) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("calendar_add_expense")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.addExpense)
                    }
                }
            }
        }

        if (dayShifts.isEmpty() && dayExpenses.isEmpty()) {
            item {
                EmptyStateBox(
                    icon = Icons.Rounded.EventNote,
                    message = strings.noActivityForDate
                )
            }
        }

        if (dayShifts.isNotEmpty()) {
            item {
                Text(
                    text = "${strings.shiftsCountLabel} (${dayShifts.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            items(dayShifts, key = { "shift_${it.id}" }) { shift ->
                FinanceCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = shift.companyName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${shift.startTime} - ${shift.endTime}" +
                                    (if (shift.overnight) " (${strings.overnightNextDayBadge})" else "") +
                                    " • ${FinanceCalculator.formatHours(shift.durationMinutes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        MonoAmountText(
                            text = "+${FinanceCalculator.formatCurrency(shift.totalEarnings, shift.currencyEnum(), hide)}",
                            color = financeColors.income
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = { onDuplicateShift(shift) }) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = strings.duplicate, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { onEditShift(shift) }) {
                            Icon(Icons.Rounded.Edit, contentDescription = strings.edit, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { onDeleteShift(shift) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = strings.delete, tint = financeColors.expense, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        if (dayExpenses.isNotEmpty()) {
            item {
                Text(
                    text = "${strings.expensesCountLabel} (${dayExpenses.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            items(dayExpenses, key = { "exp_${it.id}" }) { exp ->
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
