package com.example.domain

import java.util.Calendar
import java.util.Locale

object DateUtils {

    fun todayIso(calendar: Calendar = Calendar.getInstance()): String {
        val y = calendar.get(Calendar.YEAR)
        val m = calendar.get(Calendar.MONTH) + 1
        val d = calendar.get(Calendar.DAY_OF_MONTH)
        return String.format(Locale.US, "%04d-%02d-%02d", y, m, d)
    }

    fun formatIso(year: Int, month1to12: Int, day: Int): String =
        String.format(Locale.US, "%04d-%02d-%02d", year, month1to12, day)

    fun parseIso(dateIso: String): Triple<Int, Int, Int>? {
        val parts = dateIso.trim().split("-")
        if (parts.size != 3) return null
        val y = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val d = parts[2].toIntOrNull() ?: return null
        if (m !in 1..12 || d !in 1..31) return null
        return Triple(y, m, d)
    }

    fun parseUserInputToIso(input: String): String? {
        val clean = input.trim()
        if (clean.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) {
            return if (parseIso(clean) != null) clean else null
        }
        val dmy = clean.split(".")
        if (dmy.size == 3) {
            val d = dmy[0].toIntOrNull() ?: return null
            val m = dmy[1].toIntOrNull() ?: return null
            val y = dmy[2].toIntOrNull() ?: return null
            if (y in 1900..2100 && m in 1..12 && d in 1..daysInMonth(y, m)) {
                return formatIso(y, m, d)
            }
        }
        return null
    }

    fun formatDisplay(dateIso: String, format: DateFormatOption = DateFormatOption.DD_MM_YYYY): String {
        val (y, m, d) = parseIso(dateIso) ?: return dateIso
        return when (format) {
            DateFormatOption.DD_MM_YYYY -> String.format(Locale.US, "%02d.%02d.%04d", d, m, y)
            DateFormatOption.YYYY_MM_DD -> String.format(Locale.US, "%04d-%02d-%02d", y, m, d)
        }
    }

    fun monthKey(dateIso: String): String =
        if (dateIso.length >= 7) dateIso.substring(0, 7) else dateIso

    fun yearKey(dateIso: String): String =
        if (dateIso.length >= 4) dateIso.substring(0, 4) else dateIso

    fun daysInMonth(year: Int, month1to12: Int): Int {
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, month1to12 - 1)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    /** Returns 0 = Monday .. 6 = Sunday */
    fun dayOfWeekMondayFirst(year: Int, month1to12: Int, day: Int): Int {
        val cal = Calendar.getInstance()
        cal.set(year, month1to12 - 1, day)
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            Calendar.SUNDAY -> 6
            else -> 0
        }
    }

    fun addDays(dateIso: String, days: Int): String {
        val (y, m, d) = parseIso(dateIso) ?: return dateIso
        val cal = Calendar.getInstance()
        cal.set(y, m - 1, d, 12, 0, 0)
        cal.add(Calendar.DAY_OF_MONTH, days)
        return todayIso(cal)
    }

    fun addMonths(dateIso: String, months: Int): String {
        val (y, m, d) = parseIso(dateIso) ?: return dateIso
        val cal = Calendar.getInstance()
        cal.set(y, m - 1, d, 12, 0, 0)
        cal.add(Calendar.MONTH, months)
        return todayIso(cal)
    }

    fun addYears(dateIso: String, years: Int): String {
        val (y, m, d) = parseIso(dateIso) ?: return dateIso
        val cal = Calendar.getInstance()
        cal.set(y, m - 1, d, 12, 0, 0)
        cal.add(Calendar.YEAR, years)
        return todayIso(cal)
    }

    fun startOfWeekIso(anchorIso: String): String {
        val (y, m, d) = parseIso(anchorIso) ?: return anchorIso
        val offset = dayOfWeekMondayFirst(y, m, d)
        return addDays(anchorIso, -offset)
    }

    fun endOfWeekIso(anchorIso: String): String {
        val start = startOfWeekIso(anchorIso)
        return addDays(start, 6)
    }

    fun nextRecurringDate(currentIso: String, frequency: RecurringFrequency): String = when (frequency) {
        RecurringFrequency.WEEKLY -> addDays(currentIso, 7)
        RecurringFrequency.MONTHLY -> addMonths(currentIso, 1)
        RecurringFrequency.YEARLY -> addYears(currentIso, 1)
    }

    fun isValidTimeHHmm(value: String): Boolean {
        val parts = value.trim().split(":")
        if (parts.size != 2) return false
        val h = parts[0].toIntOrNull() ?: return false
        val m = parts[1].toIntOrNull() ?: return false
        return h in 0..23 && m in 0..59
    }

    fun normalizeTimeHHmm(hour: Int, minute: Int): String =
        String.format(Locale.US, "%02d:%02d", hour.coerceIn(0, 23), minute.coerceIn(0, 59))
}
