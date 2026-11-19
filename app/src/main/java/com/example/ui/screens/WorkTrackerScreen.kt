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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.i18n.LocalAppStrings
import com.example.domain.DateUtils
import com.example.domain.FinanceCalculator
import com.example.domain.WorkShift
import com.example.ui.components.EmptyStateBox
import com.example.ui.components.FinanceCard
import com.example.ui.components.MonoAmountText
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.LocalFinanceColors
import com.example.ui.viewmodel.FinanceUiState

@Composable
fun WorkTrackerScreen(
    state: FinanceUiState,
    onAddShift: () -> Unit,
    onEditShift: (WorkShift) -> Unit,
    onDuplicateShift: (WorkShift) -> Unit,
    onDeleteShift: (WorkShift) -> Unit
) {
    val strings = LocalAppStrings.current
    val financeColors = LocalFinanceColors.current
    val cur = state.primaryCurrency
    val hide = state.preferences.hideBalances
    val d = state.dashboard

    val groupedByDate = remember(state.shifts) {
        state.shifts.groupBy { it.dateIso }
    }

    val avgHourlyAllTime = remember(d.totalIncome, d.totalMinutesWorkedAllTime) {
        val hrs = d.totalMinutesWorkedAllTime / 60.0
        if (hrs > 0) d.totalIncome / hrs else 0.0
    }

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
                    text = strings.workTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = onAddShift,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("work_add_shift_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.addShift)
                }
            }
        }

        // Work Earnings Summary Card
        item {
            FinanceCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(strings.weeklyIncome, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MonoAmountText(
                            text = FinanceCalculator.formatCurrency(d.weekIncome, cur, hide),
                            color = financeColors.income
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(strings.monthlyIncome, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MonoAmountText(
                            text = FinanceCalculator.formatCurrency(d.monthIncome, cur, hide),
                            color = financeColors.income
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(strings.annualIncome, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MonoAmountText(
                            text = FinanceCalculator.formatCurrency(d.annualIncome, cur, hide),
                            color = BrandBlue
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(strings.avgHourlyEarnings, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MonoAmountText(
                            text = "${FinanceCalculator.formatCurrency(avgHourlyAllTime, cur, hide)} ${strings.perHourLabel}",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        if (groupedByDate.isEmpty()) {
            item {
                EmptyStateBox(
                    icon = Icons.Rounded.Work,
                    message = strings.noDataYet
                )
            }
        } else {
            groupedByDate.forEach { (dateIso, dayShifts) ->
                val daySum = dayShifts.sumOf {
                    FinanceCalculator.convertCurrency(it.totalEarnings, it.currencyEnum(), cur, state.exchangeRates)
                }
                val dayMins = dayShifts.sumOf { it.durationMinutes }

                item(key = "header_$dateIso") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = DateUtils.formatDisplay(dateIso, state.preferences.dateFormat),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${FinanceCalculator.formatHours(dayMins)} • +${FinanceCalculator.formatCurrency(daySum, cur, hide)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = financeColors.income,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                items(dayShifts, key = { it.id }) { shift ->
                    val hourlyEff = if (shift.durationMinutes > 0) {
                        shift.totalEarnings / (shift.durationMinutes / 60.0)
                    } else 0.0

                    FinanceCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = shift.companyName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (shift.overnight) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = BrandBlue.copy(alpha = 0.14f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.NightsStay,
                                                    contentDescription = strings.overnightShift,
                                                    tint = BrandBlue,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    text = strings.overnightNextDayBadge,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = BrandBlue
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${shift.startTime} → ${shift.endTime}  •  ${FinanceCalculator.formatHours(shift.durationMinutes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${FinanceCalculator.formatCurrency(hourlyEff, shift.currencyEnum(), hide)}${strings.perHourLabel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (!shift.notes.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = shift.notes,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            MonoAmountText(
                                text = "+${FinanceCalculator.formatCurrency(shift.totalEarnings, shift.currencyEnum(), hide)}",
                                color = financeColors.income,
                                style = MaterialTheme.typography.titleMedium
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
        }
    }
}
