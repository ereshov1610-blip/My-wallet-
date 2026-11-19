package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.i18n.LocalAppStrings
import com.example.domain.DateUtils
import com.example.domain.FinanceCalculator
import com.example.ui.components.FinanceCard
import com.example.ui.components.IncomeExpenseBarChart
import com.example.ui.components.MonoAmountText
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.LocalFinanceColors
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavyHeader
import com.example.ui.viewmodel.FinanceUiState

@Composable
fun HomeDashboardScreen(
    state: FinanceUiState,
    onAddShift: () -> Unit,
    onAddExpense: () -> Unit,
    onToggleHideBalances: () -> Unit,
    onLoadDemoData: () -> Unit,
    onOpenSavings: () -> Unit,
    onOpenDebts: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCalendar: () -> Unit
) {
    val strings = LocalAppStrings.current
    val financeColors = LocalFinanceColors.current
    val cur = state.primaryCurrency
    val hide = state.preferences.hideBalances
    val d = state.dashboard
    val todayFormatted = DateUtils.formatDisplay(DateUtils.todayIso(), state.preferences.dateFormat)

    val goalTarget = state.profile?.monthlyIncomeGoal ?: 45000.0
    val goalProgress = if (goalTarget > 0) (d.monthIncome / goalTarget).toFloat().coerceIn(0f, 1f) else 0f
    val goalPercent = if (goalTarget > 0) ((d.monthIncome / goalTarget) * 100).toInt() else 0

    val activeWarnings = state.budgetUsages.filter { it.isApproaching || it.isExceeded }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Header Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.navHome,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${strings.today}: $todayFormatted • ${cur.code}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row {
                    IconButton(
                        onClick = onToggleHideBalances,
                        modifier = Modifier.testTag("toggle_hide_balances")
                    ) {
                        Icon(
                            imageVector = if (hide) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = strings.hideBalancesLabel
                        )
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("home_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = strings.sectionSettings
                        )
                    }
                }
            }
        }

        // Apple-Inspired Hero Net Balance Card
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(listOf(NavyHeader, NavyDeep))
                        )
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = strings.netBalance,
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = BrandBlue.copy(alpha = 0.25f)
                            ) {
                                Text(
                                    text = "${cur.code} ${cur.symbol}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        MonoAmountText(
                            text = FinanceCalculator.formatCurrency(d.netBalance, cur, hide),
                            color = if (d.netBalance >= 0) Color.White else Color(0xFFFCA5A5),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                color = Color.White.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.TrendingUp,
                                        contentDescription = null,
                                        tint = Color(0xFF34D399),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = strings.totalIncome,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.72f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        MonoAmountText(
                                            text = FinanceCalculator.formatCurrency(d.totalIncome, cur, hide),
                                            color = Color(0xFF34D399),
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                    }
                                }
                            }

                            Surface(
                                color = Color.White.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.TrendingDown,
                                        contentDescription = null,
                                        tint = Color(0xFFF87171),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = strings.totalExpenses,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.72f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        MonoAmountText(
                                            text = FinanceCalculator.formatCurrency(d.totalExpenses, cur, hide),
                                            color = Color(0xFFF87171),
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Fast Action Buttons
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onAddShift,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("home_add_shift_button")
                ) {
                    Icon(Icons.Rounded.Work, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.addShift, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                FilledTonalButton(
                    onClick = onAddExpense,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("home_add_expense_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.addExpense, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // Sample Data Prompt when empty
        if (state.shifts.isEmpty() && state.expenses.isEmpty()) {
            item {
                FinanceCard(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = strings.loadDemoData,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        TextButton(
                            onClick = onLoadDemoData,
                            modifier = Modifier.testTag("home_load_demo_button")
                        ) {
                            Text(strings.add)
                        }
                    }
                }
            }
        }

        // Budget Warning Banner
        if (activeWarnings.isNotEmpty() || (state.overallBudgetLimit > 0 && state.overallBudgetSpent >= state.overallBudgetLimit * 0.8)) {
            item {
                val isOver = activeWarnings.any { it.isExceeded } ||
                    (state.overallBudgetLimit > 0 && state.overallBudgetSpent >= state.overallBudgetLimit)
                FinanceCard(
                    containerColor = if (isOver) financeColors.expenseBg else financeColors.warningBg
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = strings.budgetWarningTitle,
                            tint = if (isOver) financeColors.expense else financeColors.warning
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.budgetWarningTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            val detail = if (activeWarnings.isNotEmpty()) {
                                activeWarnings.joinToString(" • ") {
                                    "${it.categoryName}: ${(it.ratio * 100).toInt()}%"
                                }
                            } else {
                                if (isOver) strings.budgetExceededMsg else strings.budgetApproachingMsg
                            }
                            Text(
                                text = detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = onOpenRecurring) {
                            Text(strings.budgetsTab)
                        }
                    }
                }
            }
        }

        // Today's Snapshot Card
        item {
            FinanceCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.todaysSummary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onOpenCalendar) {
                        Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(todayFormatted)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricMiniCell(
                        label = strings.todaysIncome,
                        value = FinanceCalculator.formatCurrency(d.todayIncome, cur, hide),
                        valueColor = financeColors.income,
                        bgColor = financeColors.incomeBg,
                        modifier = Modifier.weight(1f)
                    )
                    MetricMiniCell(
                        label = strings.todaysExpenses,
                        value = FinanceCalculator.formatCurrency(d.todayExpenses, cur, hide),
                        valueColor = financeColors.expense,
                        bgColor = financeColors.expenseBg,
                        modifier = Modifier.weight(1f)
                    )
                    MetricMiniCell(
                        label = strings.todaysNet,
                        value = FinanceCalculator.formatCurrency(d.todayNet, cur, hide),
                        valueColor = if (d.todayNet >= 0) financeColors.income else financeColors.expense,
                        bgColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Period Metrics Grid (Week Income, Month Income, Month Expenses, Total Savings)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DashboardStatCard(
                        title = strings.currentWeekIncome,
                        value = FinanceCalculator.formatCurrency(d.weekIncome, cur, hide),
                        valueColor = financeColors.income,
                        modifier = Modifier.weight(1f)
                    )
                    DashboardStatCard(
                        title = strings.currentMonthIncome,
                        value = FinanceCalculator.formatCurrency(d.monthIncome, cur, hide),
                        valueColor = financeColors.income,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DashboardStatCard(
                        title = strings.currentMonthExpenses,
                        value = FinanceCalculator.formatCurrency(d.monthExpenses, cur, hide),
                        valueColor = financeColors.expense,
                        modifier = Modifier.weight(1f)
                    )
                    DashboardStatCard(
                        title = strings.totalSavings,
                        value = FinanceCalculator.formatCurrency(d.totalSavings, cur, hide),
                        valueColor = financeColors.savings,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenSavings
                    )
                }
            }
        }

        // Monthly Income Goal Progress Card
        item {
            FinanceCard(onClick = onOpenSavings) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.monthlyGoalProgress,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "$goalPercent%",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { goalProgress },
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
                        text = FinanceCalculator.formatCurrency(d.monthIncome, cur, hide),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = FinanceCalculator.formatCurrency(goalTarget, cur, hide),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Recent Trend Chart on Home
        state.reportSummary?.let { report ->
            if (report.chartPoints.isNotEmpty()) {
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
            }
        }

        // More Financial Tools Section (Savings, Debts, Recurring & Budgets, Settings)
        item {
            Text(
                text = strings.moreTools,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ToolNavigationTile(
                        title = strings.sectionSavings,
                        icon = Icons.Rounded.Savings,
                        tint = financeColors.savings,
                        onClick = onOpenSavings,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tool_savings")
                    )
                    ToolNavigationTile(
                        title = strings.sectionDebts,
                        icon = Icons.Rounded.CreditCard,
                        tint = financeColors.expense,
                        onClick = onOpenDebts,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tool_debts")
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ToolNavigationTile(
                        title = strings.sectionRecurringBudgets,
                        icon = Icons.Rounded.Repeat,
                        tint = BrandBlue,
                        onClick = onOpenRecurring,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tool_recurring")
                    )
                    ToolNavigationTile(
                        title = strings.sectionSettings,
                        icon = Icons.Rounded.Settings,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tool_settings")
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricMiniCell(
    label: String,
    value: String,
    valueColor: Color,
    bgColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            MonoAmountText(
                text = value,
                color = valueColor,
                style = MaterialTheme.typography.titleSmall
            )
        }
    }
}

@Composable
private fun DashboardStatCard(
    title: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    FinanceCard(modifier = modifier, onClick = onClick) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(6.dp))
        MonoAmountText(
            text = value,
            color = valueColor,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun ToolNavigationTile(
    title: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FinanceCard(onClick = onClick, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = tint.copy(alpha = 0.14f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = tint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
