package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.i18n.AppLanguage
import com.example.core.i18n.AppStrings
import com.example.core.i18n.stringsFor
import com.example.data.BackupAndExportManager
import com.example.data.FinanceRepository
import com.example.data.LocalAppPreferences
import com.example.data.LocalFinanceStore
import com.example.data.LocalPreferencesRepository
import com.example.domain.BudgetUsageStatus
import com.example.domain.CategoryBudget
import com.example.domain.CurrencyCode
import com.example.domain.DashboardSummary
import com.example.domain.DateFormatOption
import com.example.domain.DateUtils
import com.example.domain.DebtRecord
import com.example.domain.ExchangeRates
import com.example.domain.ExpenseCategory
import com.example.domain.ExpenseRecord
import com.example.domain.FinanceCalculator
import com.example.domain.RecurringExpense
import com.example.domain.ReportPeriod
import com.example.domain.ReportSummary
import com.example.domain.SavingsGoal
import com.example.domain.SavingsTransfer
import com.example.domain.ThemePreference
import com.example.domain.UserProfile
import com.example.domain.WorkShift
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.OutputStream

sealed interface DataStatus {
    data object Loading : DataStatus
    data object Ready : DataStatus
    data class Error(val message: String) : DataStatus
}

data class FinanceUiState(
    val status: DataStatus = DataStatus.Loading,
    val isSignedIn: Boolean = false,
    val profile: UserProfile? = null,
    val preferences: LocalAppPreferences = LocalAppPreferences(),
    val primaryCurrency: CurrencyCode = CurrencyCode.TRY,
    val exchangeRates: ExchangeRates = ExchangeRates(),
    val shifts: List<WorkShift> = emptyList(),
    val expenses: List<ExpenseRecord> = emptyList(),
    val categories: List<ExpenseCategory> = emptyList(),
    val savingsGoals: List<SavingsGoal> = emptyList(),
    val savingsTransfers: List<SavingsTransfer> = emptyList(),
    val debts: List<DebtRecord> = emptyList(),
    val recurringExpenses: List<RecurringExpense> = emptyList(),
    val categoryBudgets: List<CategoryBudget> = emptyList(),
    val dashboard: DashboardSummary = DashboardSummary(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0L, 0L, 0L),
    val budgetUsages: List<BudgetUsageStatus> = emptyList(),
    val overallBudgetSpent: Double = 0.0,
    val overallBudgetLimit: Double = 25000.0,
    val selectedDateIso: String = DateUtils.todayIso(),
    val reportPeriod: ReportPeriod = ReportPeriod.MONTH,
    val reportAnchorIso: String = DateUtils.todayIso(),
    val customStartIso: String = DateUtils.addDays(DateUtils.todayIso(), -30),
    val customEndIso: String = DateUtils.todayIso(),
    val reportSummary: ReportSummary? = null,
    val isBusy: Boolean = false
)

class FinanceViewModel(
    private val localStore: LocalFinanceStore,
    private val repository: FinanceRepository,
    private val prefsRepository: LocalPreferencesRepository,
    private val currentUserId: String?,
    private val userDisplayName: String,
    private val userEmail: String?
) : ViewModel() {

    private val _selectedDateIso = MutableStateFlow(DateUtils.todayIso())
    private val _reportPeriod = MutableStateFlow(ReportPeriod.MONTH)
    private val _reportAnchorIso = MutableStateFlow(DateUtils.todayIso())
    private val _customStartIso = MutableStateFlow(DateUtils.addDays(DateUtils.todayIso(), -30))
    private val _customEndIso = MutableStateFlow(DateUtils.todayIso())
    private val _isBusy = MutableStateFlow(false)
    private val _streamError = MutableStateFlow<String?>(null)
    private val _initialCloudSyncDone = MutableStateFlow(currentUserId == null)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            val localSnap = localStore.ensureInitialized()
            val uid = currentUserId
            if (uid != null) {
                repository.ensureUserInitialized(uid, userDisplayName, userEmail)
                if (uid !in localSnap.syncedUids && localSnap.hasUserContentOrCustomization()) {
                    val res = repository.mergeLocalSnapshotToCloud(localSnap)
                    res.onSuccess { migratedCount ->
                        localStore.markSynced(uid)
                        if (migratedCount > 0) {
                            val lang = runCatching { prefsRepository.preferencesFlow.first().language }
                                .getOrDefault(AppLanguage.EN)
                            _messages.tryEmit(stringsFor(lang).accountSyncedMsg)
                        }
                    }
                } else if (uid !in localSnap.syncedUids) {
                    localStore.markSynced(uid)
                }
                _initialCloudSyncDone.value = true
            }
        }
    }

    private fun <T> Flow<T>.safeFirestoreCatch(label: String, fallback: T): Flow<T> =
        catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing $label", rootError)
            _streamError.value = rootError.message ?: "Failed to load $label"
            emit(fallback)
        }

    private data class CoreCollections(
        val initialized: Boolean,
        val profile: UserProfile?,
        val shifts: List<WorkShift>,
        val expenses: List<ExpenseRecord>,
        val categories: List<ExpenseCategory>
    )

    private data class SecondaryCollections(
        val goals: List<SavingsGoal>,
        val transfers: List<SavingsTransfer>,
        val debts: List<DebtRecord>,
        val recurring: List<RecurringExpense>,
        val budgets: List<CategoryBudget>
    )

    private data class FilterSelection(
        val selectedDateIso: String,
        val period: ReportPeriod,
        val anchorIso: String,
        val customStartIso: String,
        val customEndIso: String,
        val isBusy: Boolean,
        val streamError: String?
    )

    private val coreFlow: Flow<CoreCollections> = if (currentUserId != null) {
        combine(
            repository.observeProfile(currentUserId).safeFirestoreCatch("profile", null),
            repository.observeWorkShifts(currentUserId).safeFirestoreCatch("workShifts", emptyList()),
            repository.observeExpenses(currentUserId).safeFirestoreCatch("expenses", emptyList()),
            repository.observeCategories(currentUserId).safeFirestoreCatch("categories", emptyList())
        ) { profile, shifts, expenses, categories ->
            CoreCollections(
                initialized = profile != null || _streamError.value != null,
                profile = profile,
                shifts = shifts,
                expenses = expenses,
                categories = categories
            )
        }
    } else {
        localStore.snapshotFlow.map { snap ->
            CoreCollections(
                initialized = snap.initialized,
                profile = snap.profile,
                shifts = snap.workShifts,
                expenses = snap.expenses,
                categories = snap.categories
            )
        }
    }

    private val secondaryFlow: Flow<SecondaryCollections> = if (currentUserId != null) {
        combine(
            repository.observeSavingsGoals(currentUserId).safeFirestoreCatch("savingsGoals", emptyList()),
            repository.observeSavingsTransfers(currentUserId).safeFirestoreCatch("savingsTransfers", emptyList()),
            repository.observeDebts(currentUserId).safeFirestoreCatch("debts", emptyList()),
            repository.observeRecurringExpenses(currentUserId).safeFirestoreCatch("recurringExpenses", emptyList()),
            repository.observeCategoryBudgets(currentUserId).safeFirestoreCatch("categoryBudgets", emptyList())
        ) { goals, transfers, debts, recurring, budgets ->
            SecondaryCollections(goals, transfers, debts, recurring, budgets)
        }
    } else {
        localStore.snapshotFlow.map { snap ->
            SecondaryCollections(
                goals = snap.savingsGoals,
                transfers = snap.savingsTransfers,
                debts = snap.debts,
                recurring = snap.recurringExpenses,
                budgets = snap.categoryBudgets
            )
        }
    }

    init {
        if (currentUserId != null) {
            viewModelScope.launch {
                combine(coreFlow, secondaryFlow, _initialCloudSyncDone) { core, sec, syncReady ->
                    Triple(core, sec, syncReady)
                }.collectLatest { (core, sec, syncReady) ->
                    if (syncReady && core.initialized && core.profile != null) {
                        localStore.mirrorFromCloud(
                            uid = currentUserId,
                            profile = core.profile,
                            categories = core.categories,
                            shifts = core.shifts,
                            expenses = core.expenses,
                            goals = sec.goals,
                            transfers = sec.transfers,
                            debts = sec.debts,
                            recurring = sec.recurring,
                            budgets = sec.budgets
                        )
                    }
                }
            }
        }
    }

    private val filterFlow = combine(
        _selectedDateIso,
        _reportPeriod,
        _reportAnchorIso,
        _customStartIso,
        _customEndIso
    ) { selDate, period, anchor, cStart, cEnd ->
        FilterSelection(selDate, period, anchor, cStart, cEnd, false, null)
    }.combine(_isBusy) { f, busy -> f.copy(isBusy = busy) }
        .combine(_streamError) { f, err -> f.copy(streamError = err) }

    val uiState: StateFlow<FinanceUiState> = combine(
        coreFlow,
        secondaryFlow,
        prefsRepository.preferencesFlow,
        filterFlow
    ) { core, sec, prefs, filter ->
        val primaryCurrency = core.profile?.currencyEnum() ?: CurrencyCode.TRY
        val rates = core.profile?.exchangeRates() ?: ExchangeRates()
        val strings = stringsFor(prefs.language)

        val localizedCategories = core.categories.map { cat ->
            val localizedName = localizeCategoryName(cat, strings)
            cat.copy(name = localizedName)
        }.sortedBy { it.name }

        val catNameById = localizedCategories.associate { it.id to it.name }
        val localizedExpenses = core.expenses.map { exp ->
            val resolvedName = catNameById[exp.categoryId] ?: exp.categoryName
            exp.copy(categoryName = resolvedName)
        }.sortedByDescending { it.dateIso }

        val sortedShifts = core.shifts.sortedWith(
            compareByDescending<WorkShift> { it.dateIso }.thenByDescending { it.startTime }
        )

        val todayIso = DateUtils.todayIso()
        val dashboard = FinanceCalculator.computeDashboardSummary(
            shifts = sortedShifts,
            expenses = localizedExpenses,
            savingsGoals = sec.goals,
            primaryCurrency = primaryCurrency,
            rates = rates,
            todayIso = todayIso
        )

        val localizedBudgets = sec.budgets.map { b ->
            b.copy(categoryName = catNameById[b.categoryId] ?: b.categoryName)
        }
        val budgetUsages = FinanceCalculator.computeBudgetUsages(
            budgets = localizedBudgets,
            expenses = localizedExpenses,
            monthKey = DateUtils.monthKey(todayIso),
            primaryCurrency = primaryCurrency,
            rates = rates
        )

        val report = FinanceCalculator.computeReportSummary(
            period = filter.period,
            anchorIso = filter.anchorIso,
            customStartIso = filter.customStartIso,
            customEndIso = filter.customEndIso,
            shifts = sortedShifts,
            expenses = localizedExpenses,
            categories = localizedCategories,
            primaryCurrency = primaryCurrency,
            rates = rates,
            dateFormat = prefs.dateFormat
        )

        val overallLimit = core.profile?.let {
            FinanceCalculator.convertCurrency(it.monthlyBudgetLimit, it.currencyEnum(), primaryCurrency, rates)
        } ?: 25000.0

        val status = when {
            !core.initialized -> DataStatus.Loading
            filter.streamError != null && core.profile == null -> DataStatus.Error(filter.streamError)
            else -> DataStatus.Ready
        }

        FinanceUiState(
            status = status,
            isSignedIn = currentUserId != null,
            profile = core.profile,
            preferences = prefs,
            primaryCurrency = primaryCurrency,
            exchangeRates = rates,
            shifts = sortedShifts,
            expenses = localizedExpenses,
            categories = localizedCategories,
            savingsGoals = sec.goals,
            savingsTransfers = sec.transfers.sortedByDescending { it.dateIso },
            debts = sec.debts.sortedByDescending { it.dateIso },
            recurringExpenses = sec.recurring.map { r ->
                r.copy(categoryName = catNameById[r.categoryId] ?: r.categoryName)
            }.sortedBy { it.nextDueDateIso },
            categoryBudgets = localizedBudgets,
            dashboard = dashboard,
            budgetUsages = budgetUsages,
            overallBudgetSpent = dashboard.monthExpenses,
            overallBudgetLimit = overallLimit,
            selectedDateIso = filter.selectedDateIso,
            reportPeriod = filter.period,
            reportAnchorIso = filter.anchorIso,
            customStartIso = filter.customStartIso,
            customEndIso = filter.customEndIso,
            reportSummary = report,
            isBusy = filter.isBusy
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = FinanceUiState(status = DataStatus.Loading, isSignedIn = currentUserId != null)
    )

    private fun localizeCategoryName(cat: ExpenseCategory, strings: AppStrings): String =
        when (cat.defaultKey) {
            "transport" -> strings.catTransport
            "food" -> strings.catFood
            "housing" -> strings.catHousing
            "shopping" -> strings.catShopping
            "phone" -> strings.catPhoneInternet
            "debt" -> strings.catDebtPayments
            "entertainment" -> strings.catEntertainment
            "health" -> strings.catHealth
            "other" -> strings.catOther
            else -> cat.name
        }

    fun selectDate(dateIso: String) {
        _selectedDateIso.value = dateIso
    }

    fun setReportPeriod(period: ReportPeriod) {
        _reportPeriod.value = period
    }

    fun setReportAnchorDate(dateIso: String) {
        _reportAnchorIso.value = dateIso
    }

    fun setCustomDateRange(startIso: String, endIso: String) {
        _customStartIso.value = startIso
        _customEndIso.value = endIso
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { prefsRepository.setLanguage(language) }
    }

    fun setTheme(theme: ThemePreference) {
        viewModelScope.launch { prefsRepository.setTheme(theme) }
    }

    fun setDateFormat(format: DateFormatOption) {
        viewModelScope.launch { prefsRepository.setDateFormat(format) }
    }

    fun setHideBalances(hide: Boolean) {
        viewModelScope.launch { prefsRepository.setHideBalances(hide) }
    }

    fun setPinLock(enabled: Boolean, pin: String) {
        viewModelScope.launch { prefsRepository.setPinLock(enabled, pin) }
    }

    private fun runMutation(successMsg: String? = null, block: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = block()
            _isBusy.value = false
            res.onSuccess {
                if (successMsg != null) _messages.tryEmit(successMsg)
            }.onFailure { err ->
                _messages.tryEmit(err.localizedMessage ?: "Operation failed")
            }
        }
    }

    fun saveWorkShift(shift: WorkShift, successMsg: String) =
        runMutation(successMsg) {
            if (currentUserId != null) repository.saveWorkShift(shift)
            else localStore.saveWorkShift(shift)
        }

    fun duplicateWorkShift(shift: WorkShift, successMsg: String) {
        val copy = shift.copy(id = "")
        runMutation(successMsg) {
            if (currentUserId != null) repository.saveWorkShift(copy)
            else localStore.saveWorkShift(copy)
        }
    }

    fun deleteWorkShift(shiftId: String, successMsg: String) =
        runMutation(successMsg) {
            if (currentUserId != null) repository.deleteDocument("workShifts", shiftId)
            else localStore.deleteDocument("workShifts", shiftId)
        }

    fun saveExpense(expense: ExpenseRecord, successMsg: String) =
        runMutation(successMsg) {
            if (currentUserId != null) repository.saveExpense(expense)
            else localStore.saveExpense(expense)
        }

    fun deleteExpense(expenseId: String, successMsg: String) =
        runMutation(successMsg) {
            if (currentUserId != null) repository.deleteDocument("expenses", expenseId)
            else localStore.deleteDocument("expenses", expenseId)
        }

    fun saveCategory(category: ExpenseCategory) =
        runMutation {
            if (currentUserId != null) repository.saveCategory(category)
            else localStore.saveCategory(category)
        }

    fun deleteCategory(categoryId: String) =
        runMutation {
            if (currentUserId != null) repository.deleteDocument("categories", categoryId)
            else localStore.deleteDocument("categories", categoryId)
        }

    fun saveSavingsGoal(goal: SavingsGoal) =
        runMutation {
            if (currentUserId != null) repository.saveSavingsGoal(goal)
            else localStore.saveSavingsGoal(goal)
        }

    fun deleteSavingsGoal(goalId: String) =
        runMutation {
            if (currentUserId != null) repository.deleteDocument("savingsGoals", goalId)
            else localStore.deleteDocument("savingsGoals", goalId)
        }

    fun transferToSavings(goal: SavingsGoal, amount: Double, dateIso: String, note: String?) =
        runMutation {
            if (currentUserId != null) repository.transferToSavingsGoal(goal, amount, dateIso, note)
            else localStore.transferToSavingsGoal(goal, amount, dateIso, note)
        }

    fun saveDebt(debt: DebtRecord) =
        runMutation {
            if (currentUserId != null) repository.saveDebt(debt)
            else localStore.saveDebt(debt)
        }

    fun deleteDebt(debtId: String) =
        runMutation {
            if (currentUserId != null) repository.deleteDocument("debts", debtId)
            else localStore.deleteDocument("debts", debtId)
        }

    fun saveRecurringExpense(item: RecurringExpense) =
        runMutation {
            if (currentUserId != null) repository.saveRecurringExpense(item)
            else localStore.saveRecurringExpense(item)
        }

    fun logRecurringNow(item: RecurringExpense) =
        runMutation {
            val today = DateUtils.todayIso()
            if (currentUserId != null) repository.logRecurringExpenseNow(item, today)
            else localStore.logRecurringExpenseNow(item, today)
        }

    fun deleteRecurringExpense(id: String) =
        runMutation {
            if (currentUserId != null) repository.deleteDocument("recurringExpenses", id)
            else localStore.deleteDocument("recurringExpenses", id)
        }

    fun saveCategoryBudget(budget: CategoryBudget) =
        runMutation {
            if (currentUserId != null) repository.saveCategoryBudget(budget)
            else localStore.saveCategoryBudget(budget)
        }

    fun deleteCategoryBudget(id: String) =
        runMutation {
            if (currentUserId != null) repository.deleteDocument("categoryBudgets", id)
            else localStore.deleteDocument("categoryBudgets", id)
        }

    fun updateFinancialPreferences(
        primaryCurrency: CurrencyCode,
        monthlyIncomeGoal: Double,
        monthlyBudgetLimit: Double,
        rateTryToUsd: Double,
        rateTmtToUsd: Double,
        rateEurToUsd: Double,
        savedMsg: String
    ) = runMutation(savedMsg) {
        if (currentUserId != null) {
            repository.updateProfilePreferences(
                primaryCurrency = primaryCurrency.code,
                monthlyIncomeGoal = monthlyIncomeGoal,
                monthlyBudgetLimit = monthlyBudgetLimit,
                rateTryToUsd = rateTryToUsd,
                rateTmtToUsd = rateTmtToUsd,
                rateEurToUsd = rateEurToUsd
            )
        } else {
            localStore.updateProfilePreferences(
                primaryCurrency = primaryCurrency.code,
                monthlyIncomeGoal = monthlyIncomeGoal,
                monthlyBudgetLimit = monthlyBudgetLimit,
                rateTryToUsd = rateTryToUsd,
                rateTmtToUsd = rateTmtToUsd,
                rateEurToUsd = rateEurToUsd
            )
        }
    }

    fun updateDisplayName(displayName: String, savedMsg: String) =
        runMutation(savedMsg) {
            if (currentUserId != null) repository.updateDisplayName(displayName)
            else localStore.updateDisplayName(displayName)
        }

    fun syncLocalToCloudNow(syncedMsg: String) {
        val uid = currentUserId ?: return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val snap = localStore.ensureInitialized()
                val res = repository.mergeLocalSnapshotToCloud(snap)
                res.onSuccess {
                    localStore.markSynced(uid)
                    _messages.tryEmit(syncedMsg)
                }.onFailure { err ->
                    _messages.tryEmit(err.localizedMessage ?: "Sync failed")
                }
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun showMessage(msg: String) {
        _messages.tryEmit(msg)
    }

    fun loadDemoData() {
        val code = uiState.value.primaryCurrency.code
        runMutation {
            if (currentUserId != null) repository.seedSampleData(code)
            else localStore.seedSampleData(code)
        }
    }

    fun clearAllData() {
        runMutation {
            if (currentUserId != null) {
                localStore.clearAllUserData()
                repository.clearAllUserData()
            } else {
                localStore.clearAllUserData()
            }
        }
    }

    fun buildBackupJson(): String {
        val s = uiState.value
        return BackupAndExportManager.buildBackupJson(
            profile = s.profile,
            categories = s.categories,
            shifts = s.shifts,
            expenses = s.expenses,
            goals = s.savingsGoals,
            transfers = s.savingsTransfers,
            debts = s.debts,
            recurring = s.recurringExpenses,
            budgets = s.categoryBudgets
        )
    }

    fun restoreFromBackupJson(jsonText: String, restoredMsg: String) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val payload = BackupAndExportManager.parseBackupJson(jsonText)
                val res = if (currentUserId != null) {
                    repository.restoreFromBackup(payload)
                } else {
                    localStore.restoreFromBackup(payload)
                }
                res.onSuccess { _messages.tryEmit(restoredMsg) }
                    .onFailure { _messages.tryEmit(it.localizedMessage ?: "Restore failed") }
            } catch (e: Exception) {
                _messages.tryEmit(e.localizedMessage ?: "Invalid backup JSON")
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun buildReportCsv(strings: AppStrings): String {
        val s = uiState.value
        val report = s.reportSummary ?: return ""
        return BackupAndExportManager.buildExcelCsv(
            report = report,
            shifts = s.shifts,
            expenses = s.expenses,
            primaryCurrency = s.primaryCurrency,
            dateFormat = s.preferences.dateFormat,
            strings = strings
        )
    }

    fun writeReportPdf(outputStream: OutputStream, strings: AppStrings) {
        val s = uiState.value
        val report = s.reportSummary ?: return
        BackupAndExportManager.writePdfReport(
            outputStream = outputStream,
            report = report,
            shifts = s.shifts,
            expenses = s.expenses,
            primaryCurrency = s.primaryCurrency,
            dateFormat = s.preferences.dateFormat,
            strings = strings
        )
    }

    companion object {
        private const val TAG = "FinanceVM"
    }
}
