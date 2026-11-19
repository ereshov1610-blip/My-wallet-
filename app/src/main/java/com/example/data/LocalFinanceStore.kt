package com.example.data

import android.content.Context
import com.example.domain.CategoryBudget
import com.example.domain.DateUtils
import com.example.domain.DebtDirection
import com.example.domain.DebtRecord
import com.example.domain.DebtStatus
import com.example.domain.ExpenseCategory
import com.example.domain.ExpenseRecord
import com.example.domain.RecurringExpense
import com.example.domain.RecurringFrequency
import com.example.domain.SavingsGoal
import com.example.domain.SavingsTransfer
import com.example.domain.UserProfile
import com.example.domain.WorkPayType
import com.example.domain.WorkShift
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class LocalFinanceSnapshot(
    val initialized: Boolean = false,
    val profile: UserProfile = UserProfile(
        id = "local",
        userId = "local",
        displayName = "Finance User",
        primaryCurrency = "TRY",
        monthlyIncomeGoal = 45000.0,
        monthlyBudgetLimit = 25000.0,
        rateTryToUsd = 34.5,
        rateTmtToUsd = 3.5,
        rateEurToUsd = 0.92
    ),
    val categories: List<ExpenseCategory> = emptyList(),
    val workShifts: List<WorkShift> = emptyList(),
    val expenses: List<ExpenseRecord> = emptyList(),
    val savingsGoals: List<SavingsGoal> = emptyList(),
    val savingsTransfers: List<SavingsTransfer> = emptyList(),
    val debts: List<DebtRecord> = emptyList(),
    val recurringExpenses: List<RecurringExpense> = emptyList(),
    val categoryBudgets: List<CategoryBudget> = emptyList(),
    val hasCustomizedProfile: Boolean = false,
    val syncedUids: Set<String> = emptySet()
) {
    fun hasUserContentOrCustomization(): Boolean =
        hasCustomizedProfile ||
            workShifts.isNotEmpty() ||
            expenses.isNotEmpty() ||
            categories.any { it.defaultKey == null } ||
            savingsGoals.isNotEmpty() ||
            savingsTransfers.isNotEmpty() ||
            debts.isNotEmpty() ||
            recurringExpenses.isNotEmpty() ||
            categoryBudgets.isNotEmpty()
}

class LocalFinanceStore(private val storageFile: File) {

    constructor(context: Context) : this(
        File(context.applicationContext.filesDir, "my_finance_local_v1.json")
    )

    private val mutex = Mutex()
    private val _snapshotFlow = MutableStateFlow(LocalFinanceSnapshot())
    val snapshotFlow: StateFlow<LocalFinanceSnapshot> = _snapshotFlow.asStateFlow()

    private fun newId(): String = UUID.randomUUID().toString()

    private fun defaultCategories(): List<ExpenseCategory> = listOf(
        ExpenseCategory(id = "cat_transport", userId = "local", name = "Transportation / Travel", iconKey = "directions_car", colorHex = 0xFF0A84FF, defaultKey = "transport"),
        ExpenseCategory(id = "cat_food", userId = "local", name = "Food", iconKey = "restaurant", colorHex = 0xFFFF9500, defaultKey = "food"),
        ExpenseCategory(id = "cat_housing", userId = "local", name = "Housing / Rent", iconKey = "home", colorHex = 0xFF5E5CE6, defaultKey = "housing"),
        ExpenseCategory(id = "cat_shopping", userId = "local", name = "Shopping", iconKey = "shopping_bag", colorHex = 0xFFEC4899, defaultKey = "shopping"),
        ExpenseCategory(id = "cat_phone", userId = "local", name = "Phone & Internet", iconKey = "wifi", colorHex = 0xFF06B6D4, defaultKey = "phone"),
        ExpenseCategory(id = "cat_debt", userId = "local", name = "Debt Payments", iconKey = "credit_card", colorHex = 0xFFEF4444, defaultKey = "debt"),
        ExpenseCategory(id = "cat_entertainment", userId = "local", name = "Entertainment", iconKey = "movie", colorHex = 0xFF8B5CF6, defaultKey = "entertainment"),
        ExpenseCategory(id = "cat_health", userId = "local", name = "Health", iconKey = "favorite", colorHex = 0xFF10B981, defaultKey = "health"),
        ExpenseCategory(id = "cat_other", userId = "local", name = "Other Expenses", iconKey = "category", colorHex = 0xFF64748B, defaultKey = "other")
    )

    suspend fun ensureInitialized(): LocalFinanceSnapshot = mutex.withLock {
        val current = _snapshotFlow.value
        if (current.initialized) return current

        val loaded = withContext(Dispatchers.IO) { readFromDisk() }
        val base = loaded ?: LocalFinanceSnapshot(categories = defaultCategories())
        val finalSnap = if (base.categories.isEmpty()) {
            base.copy(initialized = true, categories = defaultCategories())
        } else {
            base.copy(initialized = true)
        }
        _snapshotFlow.value = finalSnap
        if (loaded == null) {
            withContext(Dispatchers.IO) { writeToDisk(finalSnap) }
        }
        finalSnap
    }

    private suspend fun mutate(
        markLocalDirty: Boolean = true,
        transform: (LocalFinanceSnapshot) -> LocalFinanceSnapshot
    ): Result<Unit> {
        return try {
            mutex.withLock {
                if (!_snapshotFlow.value.initialized) {
                    val loaded = withContext(Dispatchers.IO) { readFromDisk() }
                    val initSnap = (loaded ?: LocalFinanceSnapshot(categories = defaultCategories())).copy(initialized = true)
                    _snapshotFlow.value = initSnap
                }
                val updated = transform(_snapshotFlow.value).let { next ->
                    if (markLocalDirty) next.copy(syncedUids = emptySet()) else next
                }
                _snapshotFlow.value = updated
                withContext(Dispatchers.IO) { writeToDisk(updated) }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateDisplayName(displayName: String): Result<Unit> {
        val safeName = displayName.trim().ifBlank { "Finance User" }.take(100)
        return mutate { snap ->
            snap.copy(
                profile = snap.profile.copy(displayName = safeName),
                hasCustomizedProfile = true
            )
        }
    }

    suspend fun updateProfilePreferences(
        primaryCurrency: String,
        monthlyIncomeGoal: Double,
        monthlyBudgetLimit: Double,
        rateTryToUsd: Double,
        rateTmtToUsd: Double,
        rateEurToUsd: Double
    ): Result<Unit> {
        require(monthlyIncomeGoal >= 0.0 && monthlyBudgetLimit >= 0.0)
        require(rateTryToUsd > 0.0 && rateTmtToUsd > 0.0 && rateEurToUsd > 0.0)
        return mutate { snap ->
            snap.copy(
                profile = snap.profile.copy(
                    primaryCurrency = primaryCurrency,
                    monthlyIncomeGoal = monthlyIncomeGoal,
                    monthlyBudgetLimit = monthlyBudgetLimit,
                    rateTryToUsd = rateTryToUsd,
                    rateTmtToUsd = rateTmtToUsd,
                    rateEurToUsd = rateEurToUsd
                ),
                hasCustomizedProfile = true
            )
        }
    }

    suspend fun saveWorkShift(shift: WorkShift): Result<Unit> {
        val company = shift.companyName.trim().take(100)
        require(company.isNotEmpty())
        require(shift.durationMinutes in 1L..1440L)
        require(shift.rateOrFixedAmount >= 0.0 && shift.totalEarnings >= 0.0)
        val notes = shift.notes?.trim()?.take(500)?.ifEmpty { null }
        val cleanShift = shift.copy(
            id = shift.id.ifBlank { newId() },
            userId = "local",
            companyName = company,
            notes = notes
        )
        return mutate { snap ->
            val list = snap.workShifts.toMutableList()
            val idx = list.indexOfFirst { it.id == cleanShift.id }
            if (idx >= 0) list[idx] = cleanShift else list.add(cleanShift)
            snap.copy(workShifts = list)
        }
    }

    suspend fun saveExpense(expense: ExpenseRecord): Result<Unit> {
        require(expense.amount > 0.0)
        val catName = expense.categoryName.trim().take(60).ifEmpty { "Other" }
        val desc = expense.description?.trim()?.take(500)?.ifEmpty { null }
        val recId = expense.recurringId?.trim()?.take(64)?.ifEmpty { null }
        val clean = expense.copy(
            id = expense.id.ifBlank { newId() },
            userId = "local",
            categoryId = expense.categoryId.take(64),
            categoryName = catName,
            description = desc,
            recurringId = recId
        )
        return mutate { snap ->
            val list = snap.expenses.toMutableList()
            val idx = list.indexOfFirst { it.id == clean.id }
            if (idx >= 0) list[idx] = clean else list.add(clean)
            snap.copy(expenses = list)
        }
    }

    suspend fun saveCategory(category: ExpenseCategory): Result<Unit> {
        val name = category.name.trim().take(60)
        require(name.isNotEmpty())
        val isNew = category.id.isBlank()
        val clean = category.copy(
            id = if (isNew) newId() else category.id,
            userId = "local",
            name = name,
            iconKey = category.iconKey.take(40),
            defaultKey = if (isNew) null else null
        )
        return mutate { snap ->
            val list = snap.categories.toMutableList()
            val idx = list.indexOfFirst { it.id == clean.id }
            if (idx >= 0) list[idx] = clean else list.add(clean)
            snap.copy(categories = list)
        }
    }

    suspend fun saveSavingsGoal(goal: SavingsGoal): Result<Unit> {
        val title = goal.title.trim().take(80)
        require(title.isNotEmpty() && goal.targetAmount > 0.0 && goal.currentAmount >= 0.0)
        val targetDate = goal.targetDateIso?.trim()?.take(10)?.ifEmpty { null }
        val clean = goal.copy(
            id = goal.id.ifBlank { newId() },
            userId = "local",
            title = title,
            targetDateIso = targetDate
        )
        return mutate { snap ->
            val list = snap.savingsGoals.toMutableList()
            val idx = list.indexOfFirst { it.id == clean.id }
            if (idx >= 0) list[idx] = clean else list.add(clean)
            snap.copy(savingsGoals = list)
        }
    }

    suspend fun transferToSavingsGoal(
        goal: SavingsGoal,
        amount: Double,
        dateIso: String,
        note: String?
    ): Result<Unit> {
        require(goal.id.isNotBlank() && amount > 0.0)
        val cleanNote = note?.trim()?.take(300)?.ifEmpty { null }
        val transfer = SavingsTransfer(
            id = newId(),
            userId = "local",
            goalId = goal.id,
            goalTitle = goal.title.take(80),
            amount = amount,
            currency = goal.currency,
            dateIso = dateIso,
            note = cleanNote
        )
        return mutate { snap ->
            val updatedGoals = snap.savingsGoals.map {
                if (it.id == goal.id) it.copy(currentAmount = it.currentAmount + amount) else it
            }
            snap.copy(
                savingsGoals = updatedGoals,
                savingsTransfers = snap.savingsTransfers + transfer
            )
        }
    }

    suspend fun saveDebt(debt: DebtRecord): Result<Unit> {
        val person = debt.personName.trim().take(100)
        require(person.isNotEmpty() && debt.totalAmount > 0.0)
        val safePaid = debt.paidAmount.coerceIn(0.0, debt.totalAmount)
        val computedStatus = when {
            safePaid >= debt.totalAmount - 0.005 -> DebtStatus.PAID.name
            safePaid > 0.005 -> DebtStatus.PARTIAL.name
            else -> DebtStatus.UNPAID.name
        }
        val due = debt.dueDateIso?.trim()?.take(10)?.ifEmpty { null }
        val note = debt.note?.trim()?.take(500)?.ifEmpty { null }
        val clean = debt.copy(
            id = debt.id.ifBlank { newId() },
            userId = "local",
            personName = person,
            paidAmount = safePaid,
            dueDateIso = due,
            status = computedStatus,
            note = note
        )
        return mutate { snap ->
            val list = snap.debts.toMutableList()
            val idx = list.indexOfFirst { it.id == clean.id }
            if (idx >= 0) list[idx] = clean else list.add(clean)
            snap.copy(debts = list)
        }
    }

    suspend fun saveRecurringExpense(item: RecurringExpense): Result<Unit> {
        val title = item.title.trim().take(100)
        require(title.isNotEmpty() && item.amount > 0.0)
        val note = item.note?.trim()?.take(300)?.ifEmpty { null }
        val clean = item.copy(
            id = item.id.ifBlank { newId() },
            userId = "local",
            title = title,
            categoryId = item.categoryId.take(64),
            categoryName = item.categoryName.take(60),
            note = note
        )
        return mutate { snap ->
            val list = snap.recurringExpenses.toMutableList()
            val idx = list.indexOfFirst { it.id == clean.id }
            if (idx >= 0) list[idx] = clean else list.add(clean)
            snap.copy(recurringExpenses = list)
        }
    }

    suspend fun logRecurringExpenseNow(item: RecurringExpense, dateIso: String): Result<Unit> {
        require(item.id.isNotBlank())
        val nextDate = DateUtils.nextRecurringDate(item.nextDueDateIso, item.frequencyEnum())
        val expense = ExpenseRecord(
            id = newId(),
            userId = "local",
            dateIso = dateIso,
            amount = item.amount,
            currency = item.currency,
            categoryId = item.categoryId.take(64),
            categoryName = item.categoryName.take(60),
            description = item.title.take(500),
            recurringId = item.id.take(64)
        )
        return mutate { snap ->
            val updatedRec = snap.recurringExpenses.map {
                if (it.id == item.id) it.copy(nextDueDateIso = nextDate) else it
            }
            snap.copy(
                expenses = snap.expenses + expense,
                recurringExpenses = updatedRec
            )
        }
    }

    suspend fun saveCategoryBudget(budget: CategoryBudget): Result<Unit> {
        require(budget.limitAmount > 0.0)
        val clean = budget.copy(
            id = budget.id.ifBlank { newId() },
            userId = "local",
            categoryId = budget.categoryId.take(64),
            categoryName = budget.categoryName.take(60)
        )
        return mutate { snap ->
            val list = snap.categoryBudgets.toMutableList()
            val idx = list.indexOfFirst { it.id == clean.id }
            if (idx >= 0) list[idx] = clean else list.add(clean)
            snap.copy(categoryBudgets = list)
        }
    }

    suspend fun deleteDocument(collectionName: String, docId: String): Result<Unit> {
        require(docId.isNotBlank())
        return mutate { snap ->
            when (collectionName) {
                "workShifts" -> snap.copy(workShifts = snap.workShifts.filterNot { it.id == docId })
                "expenses" -> snap.copy(expenses = snap.expenses.filterNot { it.id == docId })
                "categories" -> snap.copy(categories = snap.categories.filterNot { it.id == docId })
                "savingsGoals" -> snap.copy(savingsGoals = snap.savingsGoals.filterNot { it.id == docId })
                "savingsTransfers" -> snap.copy(savingsTransfers = snap.savingsTransfers.filterNot { it.id == docId })
                "debts" -> snap.copy(debts = snap.debts.filterNot { it.id == docId })
                "recurringExpenses" -> snap.copy(recurringExpenses = snap.recurringExpenses.filterNot { it.id == docId })
                "categoryBudgets" -> snap.copy(categoryBudgets = snap.categoryBudgets.filterNot { it.id == docId })
                else -> snap
            }
        }
    }

    suspend fun seedSampleData(currencyCode: String): Result<Unit> {
        val today = DateUtils.todayIso()
        val yesterday = DateUtils.addDays(today, -1)
        val twoDaysAgo = DateUtils.addDays(today, -2)
        val fourDaysAgo = DateUtils.addDays(today, -4)

        val demoShifts = listOf(
            WorkShift(
                id = newId(), userId = "local", dateIso = today, companyName = "Main Job",
                startTime = "08:00", endTime = "17:00", overnight = false, durationMinutes = 540L,
                payType = WorkPayType.FIXED.name, rateOrFixedAmount = 1500.0, totalEarnings = 1500.0,
                currency = currencyCode, notes = "Regular weekday shift"
            ),
            WorkShift(
                id = newId(), userId = "local", dateIso = today, companyName = "Evening Job",
                startTime = "19:00", endTime = "23:00", overnight = false, durationMinutes = 240L,
                payType = WorkPayType.FIXED.name, rateOrFixedAmount = 1000.0, totalEarnings = 1000.0,
                currency = currencyCode, notes = "Second job same day"
            ),
            WorkShift(
                id = newId(), userId = "local", dateIso = yesterday, companyName = "Night Logistics",
                startTime = "21:00", endTime = "03:00", overnight = true, durationMinutes = 360L,
                payType = WorkPayType.HOURLY.name, rateOrFixedAmount = 250.0, totalEarnings = 1500.0,
                currency = currencyCode, notes = "Overnight shift"
            ),
            WorkShift(
                id = newId(), userId = "local", dateIso = twoDaysAgo, companyName = "Main Job",
                startTime = "08:30", endTime = "17:30", overnight = false, durationMinutes = 540L,
                payType = WorkPayType.FIXED.name, rateOrFixedAmount = 1500.0, totalEarnings = 1500.0,
                currency = currencyCode
            )
        )
        val demoExpenses = listOf(
            ExpenseRecord(
                id = newId(), userId = "local", dateIso = today, amount = 280.0,
                currency = currencyCode, categoryId = "cat_food", categoryName = "Food",
                description = "Lunch & coffee"
            ),
            ExpenseRecord(
                id = newId(), userId = "local", dateIso = yesterday, amount = 140.0,
                currency = currencyCode, categoryId = "cat_transport", categoryName = "Transportation / Travel",
                description = "Transit pass"
            ),
            ExpenseRecord(
                id = newId(), userId = "local", dateIso = fourDaysAgo, amount = 4500.0,
                currency = currencyCode, categoryId = "cat_housing", categoryName = "Housing / Rent",
                description = "Monthly apartment rent"
            )
        )
        val demoGoals = listOf(
            SavingsGoal(
                id = newId(), userId = "local", title = "Emergency Reserve",
                targetAmount = 50000.0, currentAmount = 18500.0, currency = currencyCode,
                targetDateIso = DateUtils.addMonths(today, 6)
            ),
            SavingsGoal(
                id = newId(), userId = "local", title = "New Laptop",
                targetAmount = 30000.0, currentAmount = 12000.0, currency = currencyCode
            )
        )
        val demoDebts = listOf(
            DebtRecord(
                id = newId(), userId = "local", direction = DebtDirection.OWED_TO_ME.name,
                personName = "Murat K.", totalAmount = 3000.0, paidAmount = 1000.0,
                currency = currencyCode, dateIso = fourDaysAgo,
                dueDateIso = DateUtils.addDays(today, 14), status = DebtStatus.PARTIAL.name,
                note = "Shared equipment split"
            ),
            DebtRecord(
                id = newId(), userId = "local", direction = DebtDirection.I_OWE.name,
                personName = "City Credit", totalAmount = 6000.0, paidAmount = 0.0,
                currency = currencyCode, dateIso = fourDaysAgo,
                dueDateIso = DateUtils.addDays(today, 20), status = DebtStatus.UNPAID.name
            )
        )
        val demoRecurring = listOf(
            RecurringExpense(
                id = newId(), userId = "local", title = "Home Fiber Internet",
                amount = 450.0, currency = currencyCode, categoryId = "cat_phone",
                categoryName = "Phone & Internet", frequency = RecurringFrequency.MONTHLY.name,
                nextDueDateIso = DateUtils.addDays(today, 5), active = true
            )
        )
        val demoBudgets = listOf(
            CategoryBudget(
                id = newId(), userId = "local", categoryId = "cat_food", categoryName = "Food",
                limitAmount = 4000.0, currency = currencyCode, monthKey = "ALL"
            ),
            CategoryBudget(
                id = newId(), userId = "local", categoryId = "cat_housing", categoryName = "Housing / Rent",
                limitAmount = 5000.0, currency = currencyCode, monthKey = "ALL"
            )
        )
        return mutate { snap ->
            snap.copy(
                workShifts = snap.workShifts + demoShifts,
                expenses = snap.expenses + demoExpenses,
                savingsGoals = snap.savingsGoals + demoGoals,
                debts = snap.debts + demoDebts,
                recurringExpenses = snap.recurringExpenses + demoRecurring,
                categoryBudgets = snap.categoryBudgets + demoBudgets
            )
        }
    }

    suspend fun clearAllUserData(): Result<Unit> = mutate { snap ->
        snap.copy(
            workShifts = emptyList(),
            expenses = emptyList(),
            savingsGoals = emptyList(),
            savingsTransfers = emptyList(),
            debts = emptyList(),
            recurringExpenses = emptyList(),
            categoryBudgets = emptyList()
        )
    }

    suspend fun restoreFromBackup(backup: BackupPayload): Result<Int> {
        return try {
            var count = 0
            mutate { snap ->
                val newProfile = backup.profile?.let { p ->
                    snap.profile.copy(
                        displayName = p.displayName.ifBlank { snap.profile.displayName },
                        primaryCurrency = p.primaryCurrency,
                        monthlyIncomeGoal = p.monthlyIncomeGoal,
                        monthlyBudgetLimit = p.monthlyBudgetLimit,
                        rateTryToUsd = p.rateTryToUsd,
                        rateTmtToUsd = p.rateTmtToUsd,
                        rateEurToUsd = p.rateEurToUsd
                    )
                } ?: snap.profile

                val restoredCats = backup.categories.mapNotNull { m ->
                    val name = (m["name"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    count++
                    val defKey = m["defaultKey"] as? String
                    val id = if (defKey != null) "cat_$defKey" else newId()
                    ExpenseCategory(
                        id = id,
                        userId = "local",
                        name = name,
                        iconKey = (m["iconKey"] as? String) ?: "category",
                        colorHex = (m["colorHex"] as? Number)?.toLong() ?: 0xFF0A84FF,
                        defaultKey = defKey
                    )
                }

                val restoredShifts = backup.workShifts.map { m ->
                    count++
                    WorkShift(
                        id = newId(),
                        userId = "local",
                        dateIso = (m["dateIso"] as? String) ?: DateUtils.todayIso(),
                        companyName = (m["companyName"] as? String) ?: "Job",
                        startTime = (m["startTime"] as? String) ?: "08:00",
                        endTime = (m["endTime"] as? String) ?: "17:00",
                        overnight = (m["overnight"] as? Boolean) ?: false,
                        durationMinutes = (m["durationMinutes"] as? Number)?.toLong() ?: 60L,
                        payType = (m["payType"] as? String) ?: WorkPayType.FIXED.name,
                        rateOrFixedAmount = (m["rateOrFixedAmount"] as? Number)?.toDouble() ?: 0.0,
                        totalEarnings = (m["totalEarnings"] as? Number)?.toDouble() ?: 0.0,
                        currency = (m["currency"] as? String) ?: "TRY",
                        notes = m["notes"] as? String
                    )
                }

                val restoredExpenses = backup.expenses.map { m ->
                    count++
                    ExpenseRecord(
                        id = newId(),
                        userId = "local",
                        dateIso = (m["dateIso"] as? String) ?: DateUtils.todayIso(),
                        amount = (m["amount"] as? Number)?.toDouble() ?: 0.0,
                        currency = (m["currency"] as? String) ?: "TRY",
                        categoryId = (m["categoryId"] as? String) ?: "cat_other",
                        categoryName = (m["categoryName"] as? String) ?: "Other Expenses",
                        description = m["description"] as? String,
                        recurringId = m["recurringId"] as? String
                    )
                }

                val restoredGoals = backup.savingsGoals.map { m ->
                    count++
                    SavingsGoal(
                        id = newId(),
                        userId = "local",
                        title = (m["title"] as? String) ?: "Goal",
                        targetAmount = (m["targetAmount"] as? Number)?.toDouble() ?: 1000.0,
                        currentAmount = (m["currentAmount"] as? Number)?.toDouble() ?: 0.0,
                        currency = (m["currency"] as? String) ?: "TRY",
                        targetDateIso = m["targetDateIso"] as? String
                    )
                }

                val restoredTransfers = backup.savingsTransfers.map { m ->
                    count++
                    SavingsTransfer(
                        id = newId(),
                        userId = "local",
                        goalId = (m["goalId"] as? String) ?: "",
                        goalTitle = (m["goalTitle"] as? String) ?: "Goal",
                        amount = (m["amount"] as? Number)?.toDouble() ?: 0.0,
                        currency = (m["currency"] as? String) ?: "TRY",
                        dateIso = (m["dateIso"] as? String) ?: DateUtils.todayIso(),
                        note = m["note"] as? String
                    )
                }

                val restoredDebts = backup.debts.map { m ->
                    count++
                    DebtRecord(
                        id = newId(),
                        userId = "local",
                        direction = (m["direction"] as? String) ?: DebtDirection.I_OWE.name,
                        personName = (m["personName"] as? String) ?: "Person",
                        totalAmount = (m["totalAmount"] as? Number)?.toDouble() ?: 1.0,
                        paidAmount = (m["paidAmount"] as? Number)?.toDouble() ?: 0.0,
                        currency = (m["currency"] as? String) ?: "TRY",
                        dateIso = (m["dateIso"] as? String) ?: DateUtils.todayIso(),
                        dueDateIso = m["dueDateIso"] as? String,
                        status = (m["status"] as? String) ?: DebtStatus.UNPAID.name,
                        note = m["note"] as? String
                    )
                }

                val restoredRec = backup.recurringExpenses.map { m ->
                    count++
                    RecurringExpense(
                        id = newId(),
                        userId = "local",
                        title = (m["title"] as? String) ?: "Bill",
                        amount = (m["amount"] as? Number)?.toDouble() ?: 1.0,
                        currency = (m["currency"] as? String) ?: "TRY",
                        categoryId = (m["categoryId"] as? String) ?: "cat_other",
                        categoryName = (m["categoryName"] as? String) ?: "Other Expenses",
                        frequency = (m["frequency"] as? String) ?: RecurringFrequency.MONTHLY.name,
                        nextDueDateIso = (m["nextDueDateIso"] as? String) ?: DateUtils.todayIso(),
                        active = (m["active"] as? Boolean) ?: true,
                        note = m["note"] as? String
                    )
                }

                val restoredBudgets = backup.categoryBudgets.map { m ->
                    count++
                    CategoryBudget(
                        id = newId(),
                        userId = "local",
                        categoryId = (m["categoryId"] as? String) ?: "cat_other",
                        categoryName = (m["categoryName"] as? String) ?: "Other Expenses",
                        limitAmount = (m["limitAmount"] as? Number)?.toDouble() ?: 1.0,
                        currency = (m["currency"] as? String) ?: "TRY",
                        monthKey = (m["monthKey"] as? String) ?: "ALL"
                    )
                }

                val mergedCats = (snap.categories + restoredCats).distinctBy { it.id }

                snap.copy(
                    profile = newProfile,
                    categories = mergedCats,
                    workShifts = snap.workShifts + restoredShifts,
                    expenses = snap.expenses + restoredExpenses,
                    savingsGoals = snap.savingsGoals + restoredGoals,
                    savingsTransfers = snap.savingsTransfers + restoredTransfers,
                    debts = snap.debts + restoredDebts,
                    recurringExpenses = snap.recurringExpenses + restoredRec,
                    categoryBudgets = snap.categoryBudgets + restoredBudgets,
                    hasCustomizedProfile = snap.hasCustomizedProfile || backup.profile != null
                )
            }.getOrThrow()
            Result.success(count)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markSynced(uid: String) {
        mutate(markLocalDirty = false) { snap ->
            snap.copy(
                hasCustomizedProfile = false,
                syncedUids = snap.syncedUids + uid
            )
        }
    }

    suspend fun mirrorFromCloud(
        uid: String,
        profile: UserProfile?,
        categories: List<ExpenseCategory>,
        shifts: List<WorkShift>,
        expenses: List<ExpenseRecord>,
        goals: List<SavingsGoal>,
        transfers: List<SavingsTransfer>,
        debts: List<DebtRecord>,
        recurring: List<RecurringExpense>,
        budgets: List<CategoryBudget>
    ) {
        mutate(markLocalDirty = false) { snap ->
            snap.copy(
                initialized = true,
                profile = profile ?: snap.profile,
                categories = if (categories.isNotEmpty()) categories else snap.categories,
                workShifts = shifts,
                expenses = expenses,
                savingsGoals = goals,
                savingsTransfers = transfers,
                debts = debts,
                recurringExpenses = recurring,
                categoryBudgets = budgets,
                syncedUids = snap.syncedUids + uid
            )
        }
    }

    private fun readFromDisk(): LocalFinanceSnapshot? {
        if (!storageFile.exists()) return null
        return runCatching {
            val text = storageFile.readText(Charsets.UTF_8)
            if (text.isBlank()) return null
            val root = JSONObject(text)
            val pObj = root.optJSONObject("profile")
            val profile = if (pObj != null) {
                UserProfile(
                    id = "local",
                    userId = pObj.optString("userId", "local"),
                    displayName = pObj.optString("displayName", "Finance User"),
                    email = pObj.optString("email", "").ifBlank { null },
                    primaryCurrency = pObj.optString("primaryCurrency", "TRY"),
                    monthlyIncomeGoal = pObj.optDouble("monthlyIncomeGoal", 45000.0),
                    monthlyBudgetLimit = pObj.optDouble("monthlyBudgetLimit", 25000.0),
                    rateTryToUsd = pObj.optDouble("rateTryToUsd", 34.5),
                    rateTmtToUsd = pObj.optDouble("rateTmtToUsd", 3.5),
                    rateEurToUsd = pObj.optDouble("rateEurToUsd", 0.92)
                )
            } else {
                LocalFinanceSnapshot().profile
            }

            fun optStr(obj: JSONObject, key: String): String? =
                if (obj.has(key) && !obj.isNull(key)) obj.optString(key).ifBlank { null } else null

            val cats = mutableListOf<ExpenseCategory>()
            root.optJSONArray("categories")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    cats += ExpenseCategory(
                        id = o.optString("id", newId()),
                        userId = "local",
                        name = o.optString("name", "Category"),
                        iconKey = o.optString("iconKey", "category"),
                        colorHex = o.optLong("colorHex", 0xFF0A84FF),
                        defaultKey = optStr(o, "defaultKey")
                    )
                }
            }

            val shifts = mutableListOf<WorkShift>()
            root.optJSONArray("workShifts")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    shifts += WorkShift(
                        id = o.optString("id", newId()),
                        userId = "local",
                        dateIso = o.optString("dateIso", DateUtils.todayIso()),
                        companyName = o.optString("companyName", "Job"),
                        startTime = o.optString("startTime", "08:00"),
                        endTime = o.optString("endTime", "17:00"),
                        overnight = o.optBoolean("overnight", false),
                        durationMinutes = o.optLong("durationMinutes", 60L),
                        payType = o.optString("payType", WorkPayType.FIXED.name),
                        rateOrFixedAmount = o.optDouble("rateOrFixedAmount", 0.0),
                        totalEarnings = o.optDouble("totalEarnings", 0.0),
                        currency = o.optString("currency", "TRY"),
                        notes = optStr(o, "notes")
                    )
                }
            }

            val expenses = mutableListOf<ExpenseRecord>()
            root.optJSONArray("expenses")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    expenses += ExpenseRecord(
                        id = o.optString("id", newId()),
                        userId = "local",
                        dateIso = o.optString("dateIso", DateUtils.todayIso()),
                        amount = o.optDouble("amount", 0.0),
                        currency = o.optString("currency", "TRY"),
                        categoryId = o.optString("categoryId", "cat_other"),
                        categoryName = o.optString("categoryName", "Other Expenses"),
                        description = optStr(o, "description"),
                        recurringId = optStr(o, "recurringId")
                    )
                }
            }

            val goals = mutableListOf<SavingsGoal>()
            root.optJSONArray("savingsGoals")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    goals += SavingsGoal(
                        id = o.optString("id", newId()),
                        userId = "local",
                        title = o.optString("title", "Goal"),
                        targetAmount = o.optDouble("targetAmount", 1000.0),
                        currentAmount = o.optDouble("currentAmount", 0.0),
                        currency = o.optString("currency", "TRY"),
                        targetDateIso = optStr(o, "targetDateIso")
                    )
                }
            }

            val transfers = mutableListOf<SavingsTransfer>()
            root.optJSONArray("savingsTransfers")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    transfers += SavingsTransfer(
                        id = o.optString("id", newId()),
                        userId = "local",
                        goalId = o.optString("goalId", ""),
                        goalTitle = o.optString("goalTitle", "Goal"),
                        amount = o.optDouble("amount", 0.0),
                        currency = o.optString("currency", "TRY"),
                        dateIso = o.optString("dateIso", DateUtils.todayIso()),
                        note = optStr(o, "note")
                    )
                }
            }

            val debts = mutableListOf<DebtRecord>()
            root.optJSONArray("debts")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    debts += DebtRecord(
                        id = o.optString("id", newId()),
                        userId = "local",
                        direction = o.optString("direction", DebtDirection.I_OWE.name),
                        personName = o.optString("personName", "Person"),
                        totalAmount = o.optDouble("totalAmount", 0.0),
                        paidAmount = o.optDouble("paidAmount", 0.0),
                        currency = o.optString("currency", "TRY"),
                        dateIso = o.optString("dateIso", DateUtils.todayIso()),
                        dueDateIso = optStr(o, "dueDateIso"),
                        status = o.optString("status", DebtStatus.UNPAID.name),
                        note = optStr(o, "note")
                    )
                }
            }

            val recurring = mutableListOf<RecurringExpense>()
            root.optJSONArray("recurringExpenses")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    recurring += RecurringExpense(
                        id = o.optString("id", newId()),
                        userId = "local",
                        title = o.optString("title", "Bill"),
                        amount = o.optDouble("amount", 0.0),
                        currency = o.optString("currency", "TRY"),
                        categoryId = o.optString("categoryId", "cat_other"),
                        categoryName = o.optString("categoryName", "Other Expenses"),
                        frequency = o.optString("frequency", RecurringFrequency.MONTHLY.name),
                        nextDueDateIso = o.optString("nextDueDateIso", DateUtils.todayIso()),
                        active = o.optBoolean("active", true),
                        note = optStr(o, "note")
                    )
                }
            }

            val budgets = mutableListOf<CategoryBudget>()
            root.optJSONArray("categoryBudgets")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    budgets += CategoryBudget(
                        id = o.optString("id", newId()),
                        userId = "local",
                        categoryId = o.optString("categoryId", "cat_other"),
                        categoryName = o.optString("categoryName", "Other Expenses"),
                        limitAmount = o.optDouble("limitAmount", 0.0),
                        currency = o.optString("currency", "TRY"),
                        monthKey = o.optString("monthKey", "ALL")
                    )
                }
            }

            val synced = mutableSetOf<String>()
            root.optJSONArray("syncedUids")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optString(i).takeIf { it.isNotBlank() }?.let { synced.add(it) }
                }
            }

            LocalFinanceSnapshot(
                initialized = true,
                profile = profile,
                categories = cats,
                workShifts = shifts,
                expenses = expenses,
                savingsGoals = goals,
                savingsTransfers = transfers,
                debts = debts,
                recurringExpenses = recurring,
                categoryBudgets = budgets,
                hasCustomizedProfile = root.optBoolean("hasCustomizedProfile", false),
                syncedUids = synced
            )
        }.getOrNull()
    }

    private fun writeToDisk(snap: LocalFinanceSnapshot) {
        val root = JSONObject()
        root.put("version", 1)
        root.put("hasCustomizedProfile", snap.hasCustomizedProfile)
        root.put("syncedUids", JSONArray(snap.syncedUids.toList()))

        root.put("profile", JSONObject().apply {
            put("userId", snap.profile.userId)
            put("displayName", snap.profile.displayName)
            snap.profile.email?.let { put("email", it) }
            put("primaryCurrency", snap.profile.primaryCurrency)
            put("monthlyIncomeGoal", snap.profile.monthlyIncomeGoal)
            put("monthlyBudgetLimit", snap.profile.monthlyBudgetLimit)
            put("rateTryToUsd", snap.profile.rateTryToUsd)
            put("rateTmtToUsd", snap.profile.rateTmtToUsd)
            put("rateEurToUsd", snap.profile.rateEurToUsd)
        })

        root.put("categories", JSONArray().apply {
            snap.categories.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("iconKey", c.iconKey)
                    put("colorHex", c.colorHex)
                    c.defaultKey?.let { put("defaultKey", it) }
                })
            }
        })

        root.put("workShifts", JSONArray().apply {
            snap.workShifts.forEach { s ->
                put(JSONObject().apply {
                    put("id", s.id)
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
            snap.expenses.forEach { e ->
                put(JSONObject().apply {
                    put("id", e.id)
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
            snap.savingsGoals.forEach { g ->
                put(JSONObject().apply {
                    put("id", g.id)
                    put("title", g.title)
                    put("targetAmount", g.targetAmount)
                    put("currentAmount", g.currentAmount)
                    put("currency", g.currency)
                    g.targetDateIso?.let { put("targetDateIso", it) }
                })
            }
        })

        root.put("savingsTransfers", JSONArray().apply {
            snap.savingsTransfers.forEach { t ->
                put(JSONObject().apply {
                    put("id", t.id)
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
            snap.debts.forEach { d ->
                put(JSONObject().apply {
                    put("id", d.id)
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
            snap.recurringExpenses.forEach { r ->
                put(JSONObject().apply {
                    put("id", r.id)
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
            snap.categoryBudgets.forEach { b ->
                put(JSONObject().apply {
                    put("id", b.id)
                    put("categoryId", b.categoryId)
                    put("categoryName", b.categoryName)
                    put("limitAmount", b.limitAmount)
                    put("currency", b.currency)
                    put("monthKey", b.monthKey)
                })
            }
        })

        storageFile.parentFile?.mkdirs()
        val temp = File(storageFile.parentFile, "${storageFile.name}.tmp")
        temp.writeText(root.toString(), Charsets.UTF_8)
        if (!temp.renameTo(storageFile)) {
            temp.copyTo(storageFile, overwrite = true)
            temp.delete()
        }
    }
}
