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
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.i18n.LocalAppStrings
import com.example.domain.DateUtils
import com.example.domain.FinanceCalculator
import com.example.domain.ReportPeriod
import com.example.ui.components.DatePickerField
import com.example.ui.components.ExpenseDonutChart
import com.example.ui.components.FinanceCard
import com.example.ui.components.IncomeExpenseBarChart
import com.example.ui.components.MonoAmountText
import com.example.ui.components.WorkHoursBarChart
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.LocalFinanceColors
import com.example.ui.viewmodel.FinanceUiState

@Composable
fun ReportsScreen(
    state: FinanceUiState,
    onSelectPeriod: (ReportPeriod) -> Unit,
    onSelectAnchorDate: (String) -> Unit,
    onSelectCustomRange: (String, String) -> Unit,
    onExportCsv: () -> Unit,
    onExportPdf: () -> Unit
) {
    val strings = LocalAppStrings.current
    val financeColors = LocalFinanceColors.current
    val cur = state.primaryCurrency
    val hide = state.preferences.hideBalances
    val report = state.reportSummary

    val periodLabels = listOf(
        ReportPeriod.DAY to strings.periodDay,
        ReportPeriod.WEEK to strings.periodWeek,
        ReportPeriod.MONTH to strings.periodMonth,
        ReportPeriod.YEAR to strings.periodYear,
        ReportPeriod.CUSTOM to strings.periodCustom,
        ReportPeriod.ALL_TIME to strings.periodAllTime
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = strings.reportsTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        // Period Filter Chips
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(periodLabels) { (period, label) ->
                    FilterChip(
                        selected = state.reportPeriod == period,
                        onClick = { onSelectPeriod(period) },
                        label = { Text(label) },
                        modifier = Modifier.testTag("report_period_${period.name}")
                    )
                }
            }
        }

        // Date Range Picker Controls
        item {
            if (state.reportPeriod == ReportPeriod.CUSTOM) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DatePickerField(
                        label = strings.fromDate,
                        dateIso = state.customStartIso,
                        dateFormat = state.preferences.dateFormat,
                        onDateSelected = { onSelectCustomRange(it, state.customEndIso) },
                        modifier = Modifier.weight(1f)
                    )
                    DatePickerField(
                        label = strings.toDate,
                        dateIso = state.customEndIso,
                        dateFormat = state.preferences.dateFormat,
                        onDateSelected = { onSelectCustomRange(state.customStartIso, it) },
                        modifier = Modifier.weight(1f)
                    )
                }
            } else if (state.reportPeriod != ReportPeriod.ALL_TIME) {
                DatePickerField(
                    label = strings.dateLabel,
                    dateIso = state.reportAnchorIso,
                    dateFormat = state.preferences.dateFormat,
                    onDateSelected = onSelectAnchorDate,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (report != null) {
            // Range Banner & Export Buttons
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${DateUtils.formatDisplay(report.startDateIso, state.preferences.dateFormat)} – ${DateUtils.formatDisplay(report.endDateIso, state.preferences.dateFormat)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = onExportCsv,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_csv_button")
                    ) {
                        Icon(Icons.Rounded.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.exportExcelCsv, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    FilledTonalButton(
                        onClick = onExportPdf,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_pdf_button")
                    ) {
                        Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.exportPdfReport, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            // Primary KPI Summary
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ReportMetricTile(
                            label = strings.totalEarnings,
                            value = FinanceCalculator.formatCurrency(report.totalEarnings, cur, hide),
                            color = financeColors.income,
                            modifier = Modifier.weight(1f)
                        )
                        ReportMetricTile(
                            label = strings.totalExpenses,
                            value = FinanceCalculator.formatCurrency(report.totalExpenses, cur, hide),
                            color = financeColors.expense,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ReportMetricTile(
                            label = strings.netEarnings,
                            value = FinanceCalculator.formatCurrency(report.netEarnings, cur, hide),
                            color = if (report.netEarnings >= 0) financeColors.income else financeColors.expense,
                            modifier = Modifier.weight(1f)
                        )
                        ReportMetricTile(
                            label = strings.totalWorkingHours,
                            value = FinanceCalculator.formatHours(report.totalMinutes),
                            color = BrandBlue,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ReportMetricTile(
                            label = strings.avgDailyEarnings,
                            value = FinanceCalculator.formatCurrency(report.averageDailyEarnings, cur, hide),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        ReportMetricTile(
                            label = strings.avgHourlyEarnings,
                            value = "${FinanceCalculator.formatCurrency(report.averageHourlyEarnings, cur, hide)}${strings.perHourLabel}",
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Highlights Card (Best Day, Top Job, Highest Expense Category)
            item {
                FinanceCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        HighlightRow(
                            title = strings.bestEarningDay,
                            detail = report.bestEarningDayIso?.let {
                                DateUtils.formatDisplay(it, state.preferences.dateFormat)
                            } ?: "—",
                            amount = FinanceCalculator.formatCurrency(report.bestEarningDayAmount, cur, hide),
                            color = financeColors.income
                        )
                        HighlightRow(
                            title = strings.mostProfitableJob,
                            detail = report.mostProfitableJobName ?: "—",
                            amount = FinanceCalculator.formatCurrency(report.mostProfitableJobAmount, cur, hide),
                            color = BrandBlue
                        )
                        HighlightRow(
                            title = strings.highestExpenseCategory,
                            detail = report.highestExpenseCategoryName ?: "—",
                            amount = FinanceCalculator.formatCurrency(report.highestExpenseCategoryAmount, cur, hide),
                            color = financeColors.expense
                        )
                    }
                }
            }

            // Interactive Income vs Expense Comparison Chart
            item {
                FinanceCard {
                    Text(
                        text = strings.chartIncomeVsExpense,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    IncomeExpenseBarChart(
                        points = report.chartPoints,
                        currency = cur,
                        hideBalances = hide
                    )
                }
            }

            // Expense Category Breakdown Donut
            item {
                FinanceCard {
                    Text(
                        text = strings.chartExpenseBreakdown,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ExpenseDonutChart(
                        items = report.categoryBreakdown,
                        currency = cur,
                        hideBalances = hide
                    )
                }
            }

            // Working Hours Statistics Chart
            item {
                FinanceCard {
                    Text(
                        text = strings.chartWorkHours,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    WorkHoursBarChart(points = report.chartPoints)
                }
            }
        }
    }
}

@Composable
private fun ReportMetricTile(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    FinanceCard(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        MonoAmountText(text = value, color = color)
    }
}

@Composable
private fun HighlightRow(
    title: String,
    detail: String,
    amount: String,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        MonoAmountText(text = amount, color = color)
    }
}
