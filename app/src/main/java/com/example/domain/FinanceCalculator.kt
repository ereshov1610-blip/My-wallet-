package com.example.domain

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.roundToLong

data class DashboardSummary(
    val totalIncome: Double,
    val totalExpenses: Double,
    val netBalance: Double,
    val todayIncome: Double,
    val todayExpenses: Double,
    val todayNet: Double,
    val weekIncome: Double,
    val weekExpenses: Double,
    val monthIncome: Double,
    val monthExpenses: Double,
    val annualIncome: Double,
    val totalSavings: Double,
    val totalMinutesWorkedAllTime: Long,
    val todayMinutesWorked: Long,
    val monthMinutesWorked: Long
)

data class ChartPeriodPoint(
    val label: String,
    val income: Double,
    val expense: Double,
    val workHours: Double
)

data class CategoryBreakdownItem(
    val categoryId: String,
    val categoryName: String,
    val amount: Double,
    val percentage: Float,
    val colorHex: Long
)

data class ReportSummary(
    val period: ReportPeriod,
    val startDateIso: String,
    val endDateIso: String,
    val totalEarnings: Double,
    val totalExpenses: Double,
    val netEarnings: Double,
    val totalMinutes: Long,
    val averageDailyEarnings: Double,
    val averageHourlyEarnings: Double,
    val bestEarningDayIso: String?,
    val bestEarningDayAmount: Double,
    val mostProfitableJobName: String?,
    val mostProfitableJobAmount: Double,
    val highestExpenseCategoryName: String?,
    val highestExpenseCategoryAmount: Double,
    val chartPoints: List<ChartPeriodPoint>,
    val categoryBreakdown: List<CategoryBreakdownItem>
)

data class BudgetUsageStatus(
    val budgetId: String,
    val categoryId: String,
    val categoryName: String,
    val limitAmount: Double,
    val spentAmount: Double,
    val ratio: Float,
    val isApproaching: Boolean,
    val isExceeded: Boolean
)

object FinanceCalculator {

    fun calculateShiftDurationMinutes(
        startTime: String,
        endTime: String,
        forceOvernight: Boolean = false
    ): Pair<Long, Boolean> {
        val startMin = parseTimeMinutes(startTime) ?: return 0L to false
        val endMin = parseTimeMinutes(endTime) ?: return 0L to false
        val isOvernight = forceOvernight || endMin <= startMin
        val duration = if (isOvernight) {
            (24 * 60 - startMin) + endMin
        } else {
            endMin - startMin
        }
        return duration.toLong().coerceIn(1L, 1440L) to isOvernight
    }

    private fun parseTimeMinutes(hhMm: String): Int? {
        val parts = hhMm.trim().split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    fun calculateShiftEarnings(
        payType: WorkPayType,
        rateOrFixedAmount: Double,
        durationMinutes: Long
    ): Double {
        val safeRate = rateOrFixedAmount.coerceAtLeast(0.0)
        val raw = when (payType) {
            WorkPayType.HOURLY -> safeRate * (durationMinutes.coerceAtLeast(0L) / 60.0)
            WorkPayType.FIXED -> safeRate
        }
        return (raw * 100.0).roundToLong() / 100.0
    }

    fun convertCurrency(
        amount: Double,
        from: CurrencyCode,
        to: CurrencyCode,
        rates: ExchangeRates
    ): Double {
        if (from == to) return amount
        val tryPerUsd = if (rates.rateTryToUsd > 0) rates.rateTryToUsd else 34.5
        val tmtPerUsd = if (rates.rateTmtToUsd > 0) rates.rateTmtToUsd else 3.5
        val eurPerUsd = if (rates.rateEurToUsd > 0) rates.rateEurToUsd else 0.92

        val usd = when (from) {
            CurrencyCode.USD -> amount
            CurrencyCode.TRY -> amount / tryPerUsd
            CurrencyCode.TMT -> amount / tmtPerUsd
            CurrencyCode.EUR -> amount / eurPerUsd
        }
        return when (to) {
            CurrencyCode.USD -> usd
            CurrencyCode.TRY -> usd * tryPerUsd
            CurrencyCode.TMT -> usd * tmtPerUsd
            CurrencyCode.EUR -> usd * eurPerUsd
        }
    }

    fun formatCurrency(
        amount: Double,
        currency: CurrencyCode,
        hidden: Boolean = false
    ): String {
        if (hidden) return "${currency.symbol} •••••"
        val symbols = DecimalFormatSymbols(Locale.US).apply {
            groupingSeparator = ','
            decimalSeparator = '.'
        }
        val hasCents = kotlin.math.abs(amount - amount.roundToLong()) > 0.005
        val pattern = if (hasCents) "#,##0.00" else "#,##0"
        val formatted = DecimalFormat(pattern, symbols).format(kotlin.math.abs(amount))
        val sign = if (amount < -0.005) "-" else ""
        return when (currency) {
            CurrencyCode.TMT -> "$sign$formatted ${currency.symbol}"
            else -> "$sign${currency.symbol}$formatted"
        }
    }

    fun formatHours(minutes: Long): String {
        val safe = minutes.coerceAtLeast(0L)
        val h = safe / 60
        val m = safe % 60
        return if (m == 0L) "${h}h" else "${h}h ${m}m"
    }

    fun formatDecimalHours(minutes: Long): String {
        val hours = minutes.coerceAtLeast(0L) / 60.0
        return String.format(Locale.US, "%.1f", hours)
    }

    fun computeDashboardSummary(
        shifts: List<WorkShift>,
        expenses: List<ExpenseRecord>,
        savingsGoals: List<SavingsGoal>,
        primaryCurrency: CurrencyCode,
        rates: ExchangeRates,
        todayIso: String = DateUtils.todayIso()
    ): DashboardSummary {
        val weekStart = DateUtils.startOfWeekIso(todayIso)
        val weekEnd = DateUtils.endOfWeekIso(todayIso)
        val currentMonthKey = DateUtils.monthKey(todayIso)
        val currentYearKey = DateUtils.yearKey(todayIso)

        var totalIncome = 0.0
        var todayIncome = 0.0
        var weekIncome = 0.0
        var monthIncome = 0.0
        var annualIncome = 0.0
        var totalMinutesAllTime = 0L
        var todayMinutes = 0L
        var monthMinutes = 0L

        for (s in shifts) {
            val amount = convertCurrency(s.totalEarnings, s.currencyEnum(), primaryCurrency, rates)
            totalIncome += amount
            totalMinutesAllTime += s.durationMinutes
            if (s.dateIso == todayIso) {
                todayIncome += amount
                todayMinutes += s.durationMinutes
            }
            if (s.dateIso in weekStart..weekEnd) {
                weekIncome += amount
            }
            if (DateUtils.monthKey(s.dateIso) == currentMonthKey) {
                monthIncome += amount
                monthMinutes += s.durationMinutes
            }
            if (DateUtils.yearKey(s.dateIso) == currentYearKey) {
                annualIncome += amount
            }
        }

        var totalExpenses = 0.0
        var todayExpenses = 0.0
        var weekExpenses = 0.0
        var monthExpenses = 0.0

        for (e in expenses) {
            val amount = convertCurrency(e.amount, e.currencyEnum(), primaryCurrency, rates)
            totalExpenses += amount
            if (e.dateIso == todayIso) {
                todayExpenses += amount
            }
            if (e.dateIso in weekStart..weekEnd) {
                weekExpenses += amount
            }
            if (DateUtils.monthKey(e.dateIso) == currentMonthKey) {
                monthExpenses += amount
            }
        }

        val totalSavings = savingsGoals.sumOf {
            convertCurrency(it.currentAmount, it.currencyEnum(), primaryCurrency, rates)
        }

        return DashboardSummary(
            totalIncome = totalIncome,
            totalExpenses = totalExpenses,
            netBalance = totalIncome - totalExpenses,
            todayIncome = todayIncome,
            todayExpenses = todayExpenses,
            todayNet = todayIncome - todayExpenses,
            weekIncome = weekIncome,
            weekExpenses = weekExpenses,
            monthIncome = monthIncome,
            monthExpenses = monthExpenses,
            annualIncome = annualIncome,
            totalSavings = totalSavings,
            totalMinutesWorkedAllTime = totalMinutesAllTime,
            todayMinutesWorked = todayMinutes,
            monthMinutesWorked = monthMinutes
        )
    }

    fun resolvePeriodRange(
        period: ReportPeriod,
        anchorIso: String,
        customStartIso: String,
        customEndIso: String,
        allDates: List<String>
    ): Pair<String, String> = when (period) {
        ReportPeriod.DAY -> anchorIso to anchorIso
        ReportPeriod.WEEK -> DateUtils.startOfWeekIso(anchorIso) to DateUtils.endOfWeekIso(anchorIso)
        ReportPeriod.MONTH -> {
            val mk = DateUtils.monthKey(anchorIso)
            val parsed = DateUtils.parseIso(anchorIso)
            val days = if (parsed != null) DateUtils.daysInMonth(parsed.first, parsed.second) else 30
            "$mk-01" to String.format(Locale.US, "%s-%02d", mk, days)
        }
        ReportPeriod.YEAR -> {
            val yk = DateUtils.yearKey(anchorIso)
            "$yk-01-01" to "$yk-12-31"
        }
        ReportPeriod.CUSTOM -> {
            val s = if (customStartIso <= customEndIso) customStartIso else customEndIso
            val e = if (customStartIso <= customEndIso) customEndIso else customStartIso
            s to e
        }
        ReportPeriod.ALL_TIME -> {
            val minD = allDates.minOrNull() ?: anchorIso
            val maxD = allDates.maxOrNull() ?: anchorIso
            minD to maxD
        }
    }

    fun computeReportSummary(
        period: ReportPeriod,
        anchorIso: String,
        customStartIso: String,
        customEndIso: String,
        shifts: List<WorkShift>,
        expenses: List<ExpenseRecord>,
        categories: List<ExpenseCategory>,
        primaryCurrency: CurrencyCode,
        rates: ExchangeRates,
        dateFormat: DateFormatOption
    ): ReportSummary {
        val allDates = (shifts.map { it.dateIso } + expenses.map { it.dateIso }).filter { it.isNotBlank() }
        val (startIso, endIso) = resolvePeriodRange(period, anchorIso, customStartIso, customEndIso, allDates)

        val filteredShifts = shifts.filter { it.dateIso in startIso..endIso }
        val filteredExpenses = expenses.filter { it.dateIso in startIso..endIso }

        var totalEarnings = 0.0
        var totalMinutes = 0L
        val earningsByDay = mutableMapOf<String, Double>()
        val earningsByJob = mutableMapOf<String, Double>()

        for (s in filteredShifts) {
            val amt = convertCurrency(s.totalEarnings, s.currencyEnum(), primaryCurrency, rates)
            totalEarnings += amt
            totalMinutes += s.durationMinutes
            earningsByDay[s.dateIso] = (earningsByDay[s.dateIso] ?: 0.0) + amt
            val jobKey = s.companyName.trim().ifEmpty { "Job" }
            earningsByJob[jobKey] = (earningsByJob[jobKey] ?: 0.0) + amt
        }

        var totalExpenses = 0.0
        val expensesByCatId = mutableMapOf<String, Double>()
        val catNameById = mutableMapOf<String, String>()
        for (e in filteredExpenses) {
            val amt = convertCurrency(e.amount, e.currencyEnum(), primaryCurrency, rates)
            totalExpenses += amt
            expensesByCatId[e.categoryId] = (expensesByCatId[e.categoryId] ?: 0.0) + amt
            catNameById[e.categoryId] = e.categoryName
        }

        val activeDayCount = (filteredShifts.map { it.dateIso } + filteredExpenses.map { it.dateIso })
            .distinct().size.coerceAtLeast(1)
        val avgDaily = totalEarnings / activeDayCount
        val totalHours = totalMinutes / 60.0
        val avgHourly = if (totalHours > 0.0) totalEarnings / totalHours else 0.0

        val bestDay = earningsByDay.maxByOrNull { it.value }
        val bestJob = earningsByJob.maxByOrNull { it.value }
        val topCat = expensesByCatId.maxByOrNull { it.value }
        val topCatName = topCat?.let { entry ->
            categories.firstOrNull { it.id == entry.key }?.name ?: catNameById[entry.key]
        }

        val colorMap = categories.associate { it.id to it.colorHex }
        val nameMap = categories.associate { it.id to it.name }

        val breakdown = expensesByCatId.entries
            .sortedByDescending { it.value }
            .map { (catId, amt) ->
                val pct = if (totalExpenses > 0) (amt / totalExpenses).toFloat() else 0f
                CategoryBreakdownItem(
                    categoryId = catId,
                    categoryName = nameMap[catId] ?: catNameById[catId] ?: catId,
                    amount = amt,
                    percentage = pct,
                    colorHex = colorMap[catId] ?: 0xFF0A84FF
                )
            }

        val chartPoints = buildChartPoints(
            period = period,
            filteredShifts = filteredShifts,
            filteredExpenses = filteredExpenses,
            primaryCurrency = primaryCurrency,
            rates = rates,
            dateFormat = dateFormat
        )

        return ReportSummary(
            period = period,
            startDateIso = startIso,
            endDateIso = endIso,
            totalEarnings = totalEarnings,
            totalExpenses = totalExpenses,
            netEarnings = totalEarnings - totalExpenses,
            totalMinutes = totalMinutes,
            averageDailyEarnings = avgDaily,
            averageHourlyEarnings = avgHourly,
            bestEarningDayIso = bestDay?.key,
            bestEarningDayAmount = bestDay?.value ?: 0.0,
            mostProfitableJobName = bestJob?.key,
            mostProfitableJobAmount = bestJob?.value ?: 0.0,
            highestExpenseCategoryName = topCatName,
            highestExpenseCategoryAmount = topCat?.value ?: 0.0,
            chartPoints = chartPoints,
            categoryBreakdown = breakdown
        )
    }

    private fun buildChartPoints(
        period: ReportPeriod,
        filteredShifts: List<WorkShift>,
        filteredExpenses: List<ExpenseRecord>,
        primaryCurrency: CurrencyCode,
        rates: ExchangeRates,
        dateFormat: DateFormatOption
    ): List<ChartPeriodPoint> {
        val groupByMonth = period == ReportPeriod.YEAR || period == ReportPeriod.ALL_TIME
        val incomeMap = linkedMapOf<String, Double>()
        val expenseMap = linkedMapOf<String, Double>()
        val hoursMap = linkedMapOf<String, Double>()

        val allKeys = sortedSetOf<String>()
        filteredShifts.forEach {
            val key = if (groupByMonth) DateUtils.monthKey(it.dateIso) else it.dateIso
            allKeys.add(key)
            val amt = convertCurrency(it.totalEarnings, it.currencyEnum(), primaryCurrency, rates)
            incomeMap[key] = (incomeMap[key] ?: 0.0) + amt
            hoursMap[key] = (hoursMap[key] ?: 0.0) + (it.durationMinutes / 60.0)
        }
        filteredExpenses.forEach {
            val key = if (groupByMonth) DateUtils.monthKey(it.dateIso) else it.dateIso
            allKeys.add(key)
            val amt = convertCurrency(it.amount, it.currencyEnum(), primaryCurrency, rates)
            expenseMap[key] = (expenseMap[key] ?: 0.0) + amt
        }

        return allKeys.toList().takeLast(14).map { key ->
            val label = if (groupByMonth) {
                val parts = key.split("-")
                if (parts.size == 2) "${parts[1]}.${parts[0].takeLast(2)}" else key
            } else {
                val disp = DateUtils.formatDisplay(key, dateFormat)
                if (disp.length >= 5) disp.substring(0, 5) else disp
            }
            ChartPeriodPoint(
                label = label,
                income = incomeMap[key] ?: 0.0,
                expense = expenseMap[key] ?: 0.0,
                workHours = hoursMap[key] ?: 0.0
            )
        }
    }

    fun computeBudgetUsages(
        budgets: List<CategoryBudget>,
        expenses: List<ExpenseRecord>,
        monthKey: String,
        primaryCurrency: CurrencyCode,
        rates: ExchangeRates
    ): List<BudgetUsageStatus> {
        val monthExpenses = expenses.filter { DateUtils.monthKey(it.dateIso) == monthKey }
        return budgets.filter { it.monthKey == "ALL" || it.monthKey == monthKey }.map { budget ->
            val limit = convertCurrency(budget.limitAmount, budget.currencyEnum(), primaryCurrency, rates)
            val spent = monthExpenses
                .filter { it.categoryId == budget.categoryId }
                .sumOf { convertCurrency(it.amount, it.currencyEnum(), primaryCurrency, rates) }
            val ratio = if (limit > 0) (spent / limit).toFloat() else 0f
            BudgetUsageStatus(
                budgetId = budget.id,
                categoryId = budget.categoryId,
                categoryName = budget.categoryName,
                limitAmount = limit,
                spentAmount = spent,
                ratio = ratio,
                isApproaching = ratio in 0.8f..0.9999f,
                isExceeded = ratio >= 1.0f
            )
        }
    }
}
