package com.example.domain

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

enum class CurrencyCode(val code: String, val symbol: String) {
    TRY("TRY", "₺"),
    USD("USD", "$"),
    TMT("TMT", "m"),
    EUR("EUR", "€");

    companion object {
        fun fromCode(raw: String?): CurrencyCode =
            entries.firstOrNull { it.code.equals(raw, ignoreCase = true) } ?: TRY
    }
}

enum class DateFormatOption(val key: String, val patternLabel: String) {
    DD_MM_YYYY("DD.MM.YYYY", "DD.MM.YYYY"),
    YYYY_MM_DD("YYYY-MM-DD", "YYYY-MM-DD");

    companion object {
        fun fromKey(raw: String?): DateFormatOption =
            entries.firstOrNull { it.key == raw } ?: DD_MM_YYYY
    }
}

enum class ThemePreference(val key: String) {
    SYSTEM("SYSTEM"),
    LIGHT("LIGHT"),
    DARK("DARK");

    companion object {
        fun fromKey(raw: String?): ThemePreference =
            entries.firstOrNull { it.key == raw } ?: SYSTEM
    }
}

enum class WorkPayType {
    HOURLY, FIXED;

    companion object {
        fun fromRaw(raw: String?): WorkPayType =
            entries.firstOrNull { it.name == raw } ?: FIXED
    }
}

enum class DebtDirection {
    I_OWE, OWED_TO_ME;

    companion object {
        fun fromRaw(raw: String?): DebtDirection =
            entries.firstOrNull { it.name == raw } ?: I_OWE
    }
}

enum class DebtStatus {
    UNPAID, PARTIAL, PAID;

    companion object {
        fun fromRaw(raw: String?): DebtStatus =
            entries.firstOrNull { it.name == raw } ?: UNPAID
    }
}

enum class RecurringFrequency {
    WEEKLY, MONTHLY, YEARLY;

    companion object {
        fun fromRaw(raw: String?): RecurringFrequency =
            entries.firstOrNull { it.name == raw } ?: MONTHLY
    }
}

enum class ReportPeriod {
    DAY, WEEK, MONTH, YEAR, CUSTOM, ALL_TIME
}

data class ExchangeRates(
    val rateTryToUsd: Double = 34.5,
    val rateTmtToUsd: Double = 3.5,
    val rateEurToUsd: Double = 0.92
)

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val primaryCurrency: String = "TRY",
    val monthlyIncomeGoal: Double = 45000.0,
    val monthlyBudgetLimit: Double = 25000.0,
    val rateTryToUsd: Double = 34.5,
    val rateTmtToUsd: Double = 3.5,
    val rateEurToUsd: Double = 0.92,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun currencyEnum(): CurrencyCode = CurrencyCode.fromCode(primaryCurrency)
    fun exchangeRates(): ExchangeRates = ExchangeRates(
        rateTryToUsd = if (rateTryToUsd > 0) rateTryToUsd else 34.5,
        rateTmtToUsd = if (rateTmtToUsd > 0) rateTmtToUsd else 3.5,
        rateEurToUsd = if (rateEurToUsd > 0) rateEurToUsd else 0.92
    )
}

@Keep
@IgnoreExtraProperties
data class WorkShift(
    @DocumentId val id: String = "",
    val userId: String = "",
    val dateIso: String = "",
    val companyName: String = "",
    val startTime: String = "08:00",
    val endTime: String = "17:00",
    val overnight: Boolean = false,
    val durationMinutes: Long = 540L,
    val payType: String = WorkPayType.FIXED.name,
    val rateOrFixedAmount: Double = 0.0,
    val totalEarnings: Double = 0.0,
    val currency: String = "TRY",
    val notes: String? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun payTypeEnum(): WorkPayType = WorkPayType.fromRaw(payType)
    fun currencyEnum(): CurrencyCode = CurrencyCode.fromCode(currency)
}

@Keep
@IgnoreExtraProperties
data class ExpenseRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val dateIso: String = "",
    val amount: Double = 0.0,
    val currency: String = "TRY",
    val categoryId: String = "",
    val categoryName: String = "",
    val description: String? = null,
    val recurringId: String? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun currencyEnum(): CurrencyCode = CurrencyCode.fromCode(currency)
}

@Keep
@IgnoreExtraProperties
data class ExpenseCategory(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val iconKey: String = "category",
    val colorHex: Long = 0xFF0A84FF,
    val defaultKey: String? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SavingsGoal(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val targetAmount: Double = 0.0,
    val currentAmount: Double = 0.0,
    val currency: String = "TRY",
    val targetDateIso: String? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun currencyEnum(): CurrencyCode = CurrencyCode.fromCode(currency)
}

@Keep
@IgnoreExtraProperties
data class SavingsTransfer(
    @DocumentId val id: String = "",
    val userId: String = "",
    val goalId: String = "",
    val goalTitle: String = "",
    val amount: Double = 0.0,
    val currency: String = "TRY",
    val dateIso: String = "",
    val note: String? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun currencyEnum(): CurrencyCode = CurrencyCode.fromCode(currency)
}

@Keep
@IgnoreExtraProperties
data class DebtRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val direction: String = DebtDirection.I_OWE.name,
    val personName: String = "",
    val totalAmount: Double = 0.0,
    val paidAmount: Double = 0.0,
    val currency: String = "TRY",
    val dateIso: String = "",
    val dueDateIso: String? = null,
    val status: String = DebtStatus.UNPAID.name,
    val note: String? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun directionEnum(): DebtDirection = DebtDirection.fromRaw(direction)
    fun statusEnum(): DebtStatus = DebtStatus.fromRaw(status)
    fun currencyEnum(): CurrencyCode = CurrencyCode.fromCode(currency)
    val remainingAmount: Double get() = (totalAmount - paidAmount).coerceAtLeast(0.0)
}

@Keep
@IgnoreExtraProperties
data class RecurringExpense(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val amount: Double = 0.0,
    val currency: String = "TRY",
    val categoryId: String = "",
    val categoryName: String = "",
    val frequency: String = RecurringFrequency.MONTHLY.name,
    val nextDueDateIso: String = "",
    val active: Boolean = true,
    val note: String? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun frequencyEnum(): RecurringFrequency = RecurringFrequency.fromRaw(frequency)
    fun currencyEnum(): CurrencyCode = CurrencyCode.fromCode(currency)
}

@Keep
@IgnoreExtraProperties
data class CategoryBudget(
    @DocumentId val id: String = "",
    val userId: String = "",
    val categoryId: String = "",
    val categoryName: String = "",
    val limitAmount: Double = 0.0,
    val currency: String = "TRY",
    val monthKey: String = "ALL",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun currencyEnum(): CurrencyCode = CurrencyCode.fromCode(currency)
}
