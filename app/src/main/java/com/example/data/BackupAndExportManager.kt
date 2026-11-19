package com.example.data

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.example.core.i18n.AppStrings
import com.example.domain.CategoryBudget
import com.example.domain.CurrencyCode
import com.example.domain.DateFormatOption
import com.example.domain.DateUtils
import com.example.domain.DebtRecord
import com.example.domain.ExpenseCategory
import com.example.domain.ExpenseRecord
import com.example.domain.FinanceCalculator
import com.example.domain.RecurringExpense
import com.example.domain.ReportSummary
import com.example.domain.SavingsGoal
import com.example.domain.SavingsTransfer
import com.example.domain.UserProfile
import com.example.domain.WorkShift
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream

data class BackupPayload(
    val profile: UserProfile?,
    val categories: List<Map<String, Any>>,
    val workShifts: List<Map<String, Any>>,
    val expenses: List<Map<String, Any>>,
    val savingsGoals: List<Map<String, Any>>,
    val savingsTransfers: List<Map<String, Any>>,
    val debts: List<Map<String, Any>>,
    val recurringExpenses: List<Map<String, Any>>,
    val categoryBudgets: List<Map<String, Any>>
)

object BackupAndExportManager {

    fun buildBackupJson(
        profile: UserProfile?,
        categories: List<ExpenseCategory>,
        shifts: List<WorkShift>,
        expenses: List<ExpenseRecord>,
        goals: List<SavingsGoal>,
        transfers: List<SavingsTransfer>,
        debts: List<DebtRecord>,
        recurring: List<RecurringExpense>,
        budgets: List<CategoryBudget>
    ): String {
        val root = JSONObject()
        root.put("app", "My Finance Premium")
        root.put("version", 1)
        root.put("exportedAtIso", DateUtils.todayIso())

        profile?.let { p ->
            root.put("profile", JSONObject().apply {
                put("displayName", p.displayName)
                put("primaryCurrency", p.primaryCurrency)
                put("monthlyIncomeGoal", p.monthlyIncomeGoal)
                put("monthlyBudgetLimit", p.monthlyBudgetLimit)
                put("rateTryToUsd", p.rateTryToUsd)
                put("rateTmtToUsd", p.rateTmtToUsd)
                put("rateEurToUsd", p.rateEurToUsd)
            })
        }

        root.put("categories", JSONArray().apply {
            categories.forEach { c ->
                put(JSONObject().apply {
                    put("name", c.name)
                    put("iconKey", c.iconKey)
                    put("colorHex", c.colorHex)
                    c.defaultKey?.let { put("defaultKey", it) }
                })
            }
        })

        root.put("workShifts", JSONArray().apply {
            shifts.forEach { s ->
                put(JSONObject().apply {
                    put("dateIso", s.dateIso)
                    put("companyName", s.companyName)
                    put("startTime", s.startTime)
                    put("endTime", s.endTime)
                    put("overnight", s.overnight)
                    put("durationMinutes", s.durationMinutes)
                    put("payType", s.payType)
                    put("rateOrFixedAmount", s.rateOrFixedAmount)
                    put("totalEarnings", s.totalEarnings)
                    put("currency", s.currency)
                    s.notes?.let { put("notes", it) }
                })
            }
        })

        root.put("expenses", JSONArray().apply {
            expenses.forEach { e ->
                put(JSONObject().apply {
                    put("dateIso", e.dateIso)
                    put("amount", e.amount)
                    put("currency", e.currency)
                    put("categoryId", e.categoryId)
                    put("categoryName", e.categoryName)
                    e.description?.let { put("description", it) }
                    e.recurringId?.let { put("recurringId", it) }
                })
            }
        })

        root.put("savingsGoals", JSONArray().apply {
            goals.forEach { g ->
                put(JSONObject().apply {
                    put("title", g.title)
                    put("targetAmount", g.targetAmount)
                    put("currentAmount", g.currentAmount)
                    put("currency", g.currency)
                    g.targetDateIso?.let { put("targetDateIso", it) }
                })
            }
        })

        root.put("savingsTransfers", JSONArray().apply {
            transfers.forEach { t ->
                put(JSONObject().apply {
                    put("goalId", t.goalId)
                    put("goalTitle", t.goalTitle)
                    put("amount", t.amount)
                    put("currency", t.currency)
                    put("dateIso", t.dateIso)
                    t.note?.let { put("note", it) }
                })
            }
        })

        root.put("debts", JSONArray().apply {
            debts.forEach { d ->
                put(JSONObject().apply {
                    put("direction", d.direction)
                    put("personName", d.personName)
                    put("totalAmount", d.totalAmount)
                    put("paidAmount", d.paidAmount)
                    put("currency", d.currency)
                    put("dateIso", d.dateIso)
                    d.dueDateIso?.let { put("dueDateIso", it) }
                    put("status", d.status)
                    d.note?.let { put("note", it) }
                })
            }
        })

        root.put("recurringExpenses", JSONArray().apply {
            recurring.forEach { r ->
                put(JSONObject().apply {
                    put("title", r.title)
                    put("amount", r.amount)
                    put("currency", r.currency)
                    put("categoryId", r.categoryId)
                    put("categoryName", r.categoryName)
                    put("frequency", r.frequency)
                    put("nextDueDateIso", r.nextDueDateIso)
                    put("active", r.active)
                    r.note?.let { put("note", it) }
                })
            }
        })

        root.put("categoryBudgets", JSONArray().apply {
            budgets.forEach { b ->
                put(JSONObject().apply {
                    put("categoryId", b.categoryId)
                    put("categoryName", b.categoryName)
                    put("limitAmount", b.limitAmount)
                    put("currency", b.currency)
                    put("monthKey", b.monthKey)
                })
            }
        })

        return root.toString(2)
    }

    fun parseBackupJson(jsonText: String): BackupPayload {
        val root = JSONObject(jsonText)
        val pObj = root.optJSONObject("profile")
        val profile = pObj?.let {
            UserProfile(
                displayName = it.optString("displayName", "Finance User"),
                primaryCurrency = it.optString("primaryCurrency", "TRY"),
                monthlyIncomeGoal = it.optDouble("monthlyIncomeGoal", 45000.0),
                monthlyBudgetLimit = it.optDouble("monthlyBudgetLimit", 25000.0),
                rateTryToUsd = it.optDouble("rateTryToUsd", 34.5),
                rateTmtToUsd = it.optDouble("rateTmtToUsd", 3.5),
                rateEurToUsd = it.optDouble("rateEurToUsd", 0.92)
            )
        }

        fun jsonArrayToMaps(key: String, intLongKeys: Set<String> = emptySet()): List<Map<String, Any>> {
            val arr = root.optJSONArray(key) ?: return emptyList()
            val out = mutableListOf<Map<String, Any>>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val map = mutableMapOf<String, Any>()
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = obj.opt(k)
                    if (v != null && v != JSONObject.NULL) {
                        map[k] = when {
                            k in intLongKeys && v is Number -> v.toLong()
                            v is Int -> v.toDouble()
                            else -> v
                        }
                    }
                }
                out.add(map)
            }
            return out
        }

        return BackupPayload(
            profile = profile,
            categories = jsonArrayToMaps("categories", setOf("colorHex")),
            workShifts = jsonArrayToMaps("workShifts", setOf("durationMinutes")),
            expenses = jsonArrayToMaps("expenses"),
            savingsGoals = jsonArrayToMaps("savingsGoals"),
            savingsTransfers = jsonArrayToMaps("savingsTransfers"),
            debts = jsonArrayToMaps("debts"),
            recurringExpenses = jsonArrayToMaps("recurringExpenses"),
            categoryBudgets = jsonArrayToMaps("categoryBudgets")
        )
    }

    fun buildExcelCsv(
        report: ReportSummary,
        shifts: List<WorkShift>,
        expenses: List<ExpenseRecord>,
        primaryCurrency: CurrencyCode,
        dateFormat: DateFormatOption,
        strings: AppStrings
    ): String {
        val sb = StringBuilder()
        // UTF-8 BOM for seamless Microsoft Excel opening
        sb.append('\uFEFF')
        sb.appendLine("${strings.appTitle} - ${strings.reportsTitle}")
        sb.appendLine("${strings.fromDate},${DateUtils.formatDisplay(report.startDateIso, dateFormat)},${strings.toDate},${DateUtils.formatDisplay(report.endDateIso, dateFormat)}")
        sb.appendLine("${strings.totalEarnings},${report.totalEarnings},${primaryCurrency.code}")
        sb.appendLine("${strings.totalExpenses},${report.totalExpenses},${primaryCurrency.code}")
        sb.appendLine("${strings.netEarnings},${report.netEarnings},${primaryCurrency.code}")
        sb.appendLine("${strings.totalWorkingHours},${FinanceCalculator.formatDecimalHours(report.totalMinutes)}")
        sb.appendLine()

        sb.appendLine("--- ${strings.workTitle} ---")
        sb.appendLine("${strings.dateLabel},${strings.companyOrJobName},${strings.startTime},${strings.endTime},${strings.totalHours},${strings.totalEarnings},${strings.currencyLabel},${strings.notesOptional}")
        shifts.filter { it.dateIso in report.startDateIso..report.endDateIso }
            .sortedByDescending { it.dateIso }
            .forEach { s ->
                sb.appendLine(
                    listOf(
                        DateUtils.formatDisplay(s.dateIso, dateFormat),
                        csvEscape(s.companyName),
                        s.startTime,
                        s.endTime,
                        FinanceCalculator.formatDecimalHours(s.durationMinutes),
                        s.totalEarnings.toString(),
                        s.currency,
                        csvEscape(s.notes ?: "")
                    ).joinToString(",")
                )
            }

        sb.appendLine()
        sb.appendLine("--- ${strings.expensesTitle} ---")
        sb.appendLine("${strings.dateLabel},${strings.categoryLabel},${strings.amountLabel},${strings.currencyLabel},${strings.notesOptional}")
        expenses.filter { it.dateIso in report.startDateIso..report.endDateIso }
            .sortedByDescending { it.dateIso }
            .forEach { e ->
                sb.appendLine(
                    listOf(
                        DateUtils.formatDisplay(e.dateIso, dateFormat),
                        csvEscape(e.categoryName),
                        e.amount.toString(),
                        e.currency,
                        csvEscape(e.description ?: "")
                    ).joinToString(",")
                )
            }
        return sb.toString()
    }

    private fun csvEscape(value: String): String {
        val clean = value.replace("\"", "\"\"")
        return "\"$clean\""
    }

    fun writePdfReport(
        outputStream: OutputStream,
        report: ReportSummary,
        shifts: List<WorkShift>,
        expenses: List<ExpenseRecord>,
        primaryCurrency: CurrencyCode,
        dateFormat: DateFormatOption,
        strings: AppStrings
    ) {
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = doc.startPage(pageInfo)
        var canvas: Canvas = page.canvas

        val titlePaint = Paint().apply {
            color = Color.rgb(10, 54, 91)
            textSize = 20f
            isFakeBoldText = true
        }
        val subtitlePaint = Paint().apply {
            color = Color.rgb(100, 116, 139)
            textSize = 11f
        }
        val sectionPaint = Paint().apply {
            color = Color.rgb(10, 132, 255)
            textSize = 13f
            isFakeBoldText = true
        }
        val bodyPaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 10.5f
        }
        val linePaint = Paint().apply {
            color = Color.rgb(220, 227, 236)
            strokeWidth = 1f
        }

        var y = 48f
        fun nextPageIfNeeded(needed: Float = 24f) {
            if (y + needed > pageHeight - 40f) {
                doc.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = doc.startPage(pageInfo)
                canvas = page.canvas
                y = 48f
            }
        }

        canvas.drawText(strings.appTitle, 40f, y, titlePaint)
        y += 18f
        val rangeText = "${DateUtils.formatDisplay(report.startDateIso, dateFormat)} - ${DateUtils.formatDisplay(report.endDateIso, dateFormat)}"
        canvas.drawText("${strings.reportsTitle} ($rangeText)", 40f, y, subtitlePaint)
        y += 12f
        canvas.drawLine(40f, y, pageWidth - 40f, y, linePaint)
        y += 22f

        val summaryLines = listOf(
            "${strings.totalEarnings}: ${FinanceCalculator.formatCurrency(report.totalEarnings, primaryCurrency)}",
            "${strings.totalExpenses}: ${FinanceCalculator.formatCurrency(report.totalExpenses, primaryCurrency)}",
            "${strings.netEarnings}: ${FinanceCalculator.formatCurrency(report.netEarnings, primaryCurrency)}",
            "${strings.totalWorkingHours}: ${FinanceCalculator.formatHours(report.totalMinutes)}",
            "${strings.avgDailyEarnings}: ${FinanceCalculator.formatCurrency(report.averageDailyEarnings, primaryCurrency)}",
            "${strings.avgHourlyEarnings}: ${FinanceCalculator.formatCurrency(report.averageHourlyEarnings, primaryCurrency)}"
        )
        summaryLines.forEach { line ->
            nextPageIfNeeded(18f)
            canvas.drawText(line, 40f, y, bodyPaint)
            y += 16f
        }

        y += 12f
        nextPageIfNeeded(24f)
        canvas.drawText(strings.workTitle, 40f, y, sectionPaint)
        y += 16f
        shifts.filter { it.dateIso in report.startDateIso..report.endDateIso }
            .sortedByDescending { it.dateIso }
            .take(50)
            .forEach { s ->
                nextPageIfNeeded(16f)
                val row = "${DateUtils.formatDisplay(s.dateIso, dateFormat)}  |  ${s.companyName.take(24)}  |  ${s.startTime}-${s.endTime} (${FinanceCalculator.formatHours(s.durationMinutes)})  |  ${FinanceCalculator.formatCurrency(s.totalEarnings, s.currencyEnum())}"
                canvas.drawText(row, 40f, y, bodyPaint)
                y += 15f
            }

        y += 12f
        nextPageIfNeeded(24f)
        canvas.drawText(strings.expensesTitle, 40f, y, sectionPaint)
        y += 16f
        expenses.filter { it.dateIso in report.startDateIso..report.endDateIso }
            .sortedByDescending { it.dateIso }
            .take(50)
            .forEach { e ->
                nextPageIfNeeded(16f)
                val notePart = if (!e.description.isNullOrBlank()) " (${e.description.take(20)})" else ""
                val row = "${DateUtils.formatDisplay(e.dateIso, dateFormat)}  |  ${e.categoryName.take(22)}$notePart  |  ${FinanceCalculator.formatCurrency(e.amount, e.currencyEnum())}"
                canvas.drawText(row, 40f, y, bodyPaint)
                y += 15f
            }

        doc.finishPage(page)
        doc.writeTo(outputStream)
        doc.close()
    }
}
