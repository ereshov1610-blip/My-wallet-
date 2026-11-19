package com.example

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.core.i18n.AppLanguage
import com.example.core.i18n.LocalAppStrings
import com.example.core.i18n.stringsFor
import com.example.data.BackupAndExportManager
import com.example.data.LocalFinanceStore
import com.example.domain.CategoryBudget
import com.example.domain.CurrencyCode
import com.example.domain.DateFormatOption
import com.example.domain.DateUtils
import com.example.domain.DebtDirection
import com.example.domain.DebtRecord
import com.example.domain.DebtStatus
import com.example.domain.ExchangeRates
import com.example.domain.ExpenseCategory
import com.example.domain.ExpenseRecord
import com.example.domain.FinanceCalculator
import com.example.domain.RecurringExpense
import com.example.domain.RecurringFrequency
import com.example.domain.ReportPeriod
import com.example.domain.SavingsGoal
import com.example.domain.SavingsTransfer
import com.example.domain.UserProfile
import com.example.domain.WorkPayType
import com.example.domain.WorkShift
import com.example.ui.auth.AccountMenuDropdown
import com.example.ui.auth.ProfileAccountButton
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.theme.MyFinanceTheme
import com.example.ui.viewmodel.DataStatus
import com.example.ui.viewmodel.FinanceUiState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FinanceSuiteTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `date formatting uses DD MM YYYY by default and parses round trip`() {
        val iso = "2026-11-18"
        assertEquals("18.11.2026", DateUtils.formatDisplay(iso, DateFormatOption.DD_MM_YYYY))
        assertEquals("2026-11-18", DateUtils.formatDisplay(iso, DateFormatOption.YYYY_MM_DD))
        assertEquals("2026-11-18", DateUtils.parseUserInputToIso("18.11.2026"))
        assertEquals("2026-12-01", DateUtils.parseUserInputToIso("01.12.2026"))
        assertEquals(null, DateUtils.parseUserInputToIso("18.13.2026"))
        assertEquals("2026-11-25", DateUtils.nextRecurringDate(iso, RecurringFrequency.WEEKLY))
        assertEquals("2026-12-18", DateUtils.nextRecurringDate(iso, RecurringFrequency.MONTHLY))
        assertEquals("2027-11-18", DateUtils.nextRecurringDate(iso, RecurringFrequency.YEARLY))
    }

    @Test
    fun `overnight shift duration and hourly pay calculation`() {
        val (durationMin, overnight) = FinanceCalculator.calculateShiftDurationMinutes("21:00", "03:00")
        assertTrue(overnight)
        assertEquals(360L, durationMin)

        val earnings = FinanceCalculator.calculateShiftEarnings(WorkPayType.HOURLY, 250.0, durationMin)
        assertEquals(1500.0, earnings, 0.001)

        val (dayMin, dayOvernight) = FinanceCalculator.calculateShiftDurationMinutes("08:00", "17:00")
        assertFalse(dayOvernight)
        assertEquals(540L, dayMin)
    }

    @Test
    fun `multiple jobs on same day aggregate accurately for 18 11 2026`() {
        val dateIso = "2026-11-18"
        val shifts = listOf(
            WorkShift(
                id = "s1",
                dateIso = dateIso,
                companyName = "Main Job",
                startTime = "08:00",
                endTime = "17:00",
                overnight = false,
                durationMinutes = 540L,
                payType = WorkPayType.FIXED.name,
                rateOrFixedAmount = 1500.0,
                totalEarnings = 1500.0,
                currency = CurrencyCode.TRY.code
            ),
            WorkShift(
                id = "s2",
                dateIso = dateIso,
                companyName = "Evening Job",
                startTime = "19:00",
                endTime = "23:00",
                overnight = false,
                durationMinutes = 240L,
                payType = WorkPayType.FIXED.name,
                rateOrFixedAmount = 1000.0,
                totalEarnings = 1000.0,
                currency = CurrencyCode.TRY.code
            )
        )

        val dashboard = FinanceCalculator.computeDashboardSummary(
            shifts = shifts,
            expenses = emptyList(),
            savingsGoals = emptyList(),
            primaryCurrency = CurrencyCode.TRY,
            rates = ExchangeRates(),
            todayIso = dateIso
        )

        assertEquals(2500.0, dashboard.todayIncome, 0.001)
        assertEquals(780L, dashboard.todayMinutesWorked)
        assertEquals(2500.0, dashboard.netBalance, 0.001)

        val report = FinanceCalculator.computeReportSummary(
            period = ReportPeriod.DAY,
            anchorIso = dateIso,
            customStartIso = dateIso,
            customEndIso = dateIso,
            shifts = shifts,
            expenses = emptyList(),
            categories = emptyList(),
            primaryCurrency = CurrencyCode.TRY,
            rates = ExchangeRates(),
            dateFormat = DateFormatOption.DD_MM_YYYY
        )

        assertEquals(2500.0, report.totalEarnings, 0.001)
        assertEquals(780L, report.totalMinutes)
        assertEquals("Main Job", report.mostProfitableJobName)
        assertEquals(2500.0 / 13.0, report.averageHourlyEarnings, 0.01)
    }

    @Test
    fun `net balance and multi-currency conversions work without cross-currency errors`() {
        val rates = ExchangeRates(rateTryToUsd = 30.0, rateTmtToUsd = 3.5, rateEurToUsd = 0.90)
        val convertedUsdToTry = FinanceCalculator.convertCurrency(100.0, CurrencyCode.USD, CurrencyCode.TRY, rates)
        assertEquals(3000.0, convertedUsdToTry, 0.001)

        val shifts = listOf(
            WorkShift(id = "1", dateIso = "2026-11-18", companyName = "Client US", totalEarnings = 100.0, currency = "USD", durationMinutes = 240),
            WorkShift(id = "2", dateIso = "2026-11-18", companyName = "Local", totalEarnings = 1500.0, currency = "TRY", durationMinutes = 300)
        )
        val expenses = listOf(
            ExpenseRecord(id = "e1", dateIso = "2026-11-18", amount = 500.0, currency = "TRY", categoryId = "cat_food", categoryName = "Food")
        )
        val goals = listOf(
            SavingsGoal(id = "g1", title = "Reserve", targetAmount = 10000.0, currentAmount = 2000.0, currency = "TRY")
        )

        val summary = FinanceCalculator.computeDashboardSummary(
            shifts = shifts,
            expenses = expenses,
            savingsGoals = goals,
            primaryCurrency = CurrencyCode.TRY,
            rates = rates,
            todayIso = "2026-11-18"
        )
        assertEquals(4500.0, summary.totalIncome, 0.001)
        assertEquals(500.0, summary.totalExpenses, 0.001)
        assertEquals(4000.0, summary.netBalance, 0.001)
        assertEquals(2000.0, summary.totalSavings, 0.001)
    }

    @Test
    fun `budget status flags approaching and exceeded limits`() {
        val budgets = listOf(
            CategoryBudget(id = "b1", categoryId = "cat_food", categoryName = "Food", limitAmount = 1000.0, currency = "TRY", monthKey = "ALL"),
            CategoryBudget(id = "b2", categoryId = "cat_rent", categoryName = "Housing", limitAmount = 5000.0, currency = "TRY", monthKey = "ALL")
        )
        val expenses = listOf(
            ExpenseRecord(id = "e1", dateIso = "2026-11-10", amount = 850.0, currency = "TRY", categoryId = "cat_food", categoryName = "Food"),
            ExpenseRecord(id = "e2", dateIso = "2026-11-05", amount = 5500.0, currency = "TRY", categoryId = "cat_rent", categoryName = "Housing")
        )

        val statuses = FinanceCalculator.computeBudgetUsages(
            budgets = budgets,
            expenses = expenses,
            monthKey = "2026-11",
            primaryCurrency = CurrencyCode.TRY,
            rates = ExchangeRates()
        )
        val food = statuses.first { it.categoryId == "cat_food" }
        val housing = statuses.first { it.categoryId == "cat_rent" }

        assertTrue(food.isApproaching)
        assertFalse(food.isExceeded)
        assertTrue(housing.isExceeded)
        assertFalse(housing.isApproaching)
    }

    @Test
    fun `all four languages provide complete non-blank translations including account menu`() {
        val packs = AppLanguage.entries.map { it to stringsFor(it) }
        packs.forEach { (lang, s) ->
            assertTrue("${lang.code} appTitle", s.appTitle.isNotBlank())
            assertTrue("${lang.code} navHome", s.navHome.isNotBlank())
            assertTrue("${lang.code} navCalendar", s.navCalendar.isNotBlank())
            assertTrue("${lang.code} navWork", s.navWork.isNotBlank())
            assertTrue("${lang.code} navExpenses", s.navExpenses.isNotBlank())
            assertTrue("${lang.code} navReports", s.navReports.isNotBlank())
            assertTrue("${lang.code} catTransport", s.catTransport.isNotBlank())
            assertTrue("${lang.code} catFood", s.catFood.isNotBlank())
            assertTrue("${lang.code} catHousing", s.catHousing.isNotBlank())
            assertTrue("${lang.code} signInWithGoogle", s.signInWithGoogle.isNotBlank())
            assertTrue("${lang.code} menuLogIn", s.menuLogIn.isNotBlank())
            assertTrue("${lang.code} menuCreateAccount", s.menuCreateAccount.isNotBlank())
            assertTrue("${lang.code} menuProfileSettings", s.menuProfileSettings.isNotBlank())
            assertTrue("${lang.code} menuLogOut", s.menuLogOut.isNotBlank())
            assertTrue("${lang.code} guestModeLabel", s.guestModeLabel.isNotBlank())
        }
        val homeLabels = packs.map { it.second.navHome }.toSet()
        assertEquals(4, homeLabels.size)
        val loginLabels = packs.map { it.second.menuLogIn }.toSet()
        assertEquals(4, loginLabels.size)
    }

    @Test
    fun `json backup round-trip preserves all data and export CSV succeeds`() {
        val profile = UserProfile(
            userId = "u1",
            displayName = "Ali",
            primaryCurrency = "TRY",
            monthlyIncomeGoal = 45000.0,
            monthlyBudgetLimit = 25000.0
        )
        val categories = listOf(
            ExpenseCategory(id = "cat_food", name = "Food", iconKey = "restaurant", colorHex = 0xFFFF9500, defaultKey = "food")
        )
        val shifts = listOf(
            WorkShift(
                id = "s1",
                dateIso = "2026-11-18",
                companyName = "Main Job",
                startTime = "08:00",
                endTime = "17:00",
                overnight = false,
                durationMinutes = 540L,
                payType = WorkPayType.FIXED.name,
                rateOrFixedAmount = 1500.0,
                totalEarnings = 1500.0,
                currency = "TRY",
                notes = "Shift note"
            )
        )
        val expenses = listOf(
            ExpenseRecord(
                id = "e1",
                dateIso = "2026-11-18",
                amount = 250.0,
                currency = "TRY",
                categoryId = "cat_food",
                categoryName = "Food",
                description = "Lunch"
            )
        )
        val goals = listOf(SavingsGoal(id = "g1", title = "Emergency", targetAmount = 10000.0, currentAmount = 3000.0, currency = "TRY"))
        val transfers = listOf(SavingsTransfer(id = "t1", goalId = "g1", goalTitle = "Emergency", amount = 1000.0, currency = "TRY", dateIso = "2026-11-18"))
        val debts = listOf(DebtRecord(id = "d1", direction = DebtDirection.OWED_TO_ME.name, personName = "Murat", totalAmount = 1000.0, paidAmount = 400.0, currency = "TRY", dateIso = "2026-11-18", status = DebtStatus.PARTIAL.name))
        val recurring = listOf(RecurringExpense(id = "r1", title = "Internet", amount = 450.0, currency = "TRY", categoryId = "cat_phone", categoryName = "Phone", frequency = RecurringFrequency.MONTHLY.name, nextDueDateIso = "2026-12-01", active = true))
        val budgets = listOf(CategoryBudget(id = "b1", categoryId = "cat_food", categoryName = "Food", limitAmount = 3000.0, currency = "TRY", monthKey = "ALL"))

        val json = BackupAndExportManager.buildBackupJson(
            profile = profile,
            categories = categories,
            shifts = shifts,
            expenses = expenses,
            goals = goals,
            transfers = transfers,
            debts = debts,
            recurring = recurring,
            budgets = budgets
        )
        val parsed = BackupAndExportManager.parseBackupJson(json)
        assertEquals("Ali", parsed.profile?.displayName)
        assertEquals(1, parsed.categories.size)
        assertEquals(0xFFFF9500L, parsed.categories.first()["colorHex"])
        assertEquals(1, parsed.workShifts.size)
        assertEquals(540L, parsed.workShifts.first()["durationMinutes"])
        assertEquals(1, parsed.expenses.size)
        assertEquals(1, parsed.savingsGoals.size)
        assertEquals(1, parsed.savingsTransfers.size)
        assertEquals(1, parsed.debts.size)
        assertEquals(1, parsed.recurringExpenses.size)
        assertEquals(1, parsed.categoryBudgets.size)

        val report = FinanceCalculator.computeReportSummary(
            period = ReportPeriod.ALL_TIME,
            anchorIso = "2026-11-18",
            customStartIso = "2026-11-01",
            customEndIso = "2026-11-30",
            shifts = shifts,
            expenses = expenses,
            categories = categories,
            primaryCurrency = CurrencyCode.TRY,
            rates = ExchangeRates(),
            dateFormat = DateFormatOption.DD_MM_YYYY
        )

        val csv = BackupAndExportManager.buildExcelCsv(
            report = report,
            shifts = shifts,
            expenses = expenses,
            primaryCurrency = CurrencyCode.TRY,
            dateFormat = DateFormatOption.DD_MM_YYYY,
            strings = stringsFor(AppLanguage.EN)
        )
        assertTrue(csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("18.11.2026"))
        assertTrue(csv.contains("Main Job"))
        assertTrue(csv.contains("Food"))
        assertTrue(csv.contains("250.0"))
    }

    @Test
    fun `local finance store persists all features without account and survives reload`() = runBlocking {
        val tempFile = File.createTempFile("local_finance_test", ".json")
        tempFile.delete()
        try {
            val store1 = LocalFinanceStore(tempFile)
            val initSnap = store1.ensureInitialized()
            assertTrue(initSnap.initialized)
            assertEquals(9, initSnap.categories.size)

            store1.updateDisplayName("Kerem")
            store1.saveWorkShift(
                WorkShift(
                    dateIso = "2026-11-18",
                    companyName = "Local Job",
                    startTime = "09:00",
                    endTime = "18:00",
                    overnight = false,
                    durationMinutes = 540L,
                    payType = WorkPayType.FIXED.name,
                    rateOrFixedAmount = 2000.0,
                    totalEarnings = 2000.0,
                    currency = "TRY"
                )
            )
            store1.saveExpense(
                ExpenseRecord(
                    dateIso = "2026-11-18",
                    amount = 300.0,
                    currency = "TRY",
                    categoryId = "cat_food",
                    categoryName = "Food"
                )
            )
            store1.saveSavingsGoal(
                SavingsGoal(title = "Car Fund", targetAmount = 10000.0, currentAmount = 1000.0, currency = "TRY")
            )
            val savedGoal = store1.snapshotFlow.value.savingsGoals.first()
            store1.transferToSavingsGoal(savedGoal, 500.0, "2026-11-18", "Bonus")

            // Reload from disk in fresh LocalFinanceStore instance
            val store2 = LocalFinanceStore(tempFile)
            val reloaded = store2.ensureInitialized()
            assertEquals("Kerem", reloaded.profile.displayName)
            assertTrue(reloaded.hasCustomizedProfile)
            assertTrue(reloaded.hasUserContentOrCustomization())
            assertEquals(1, reloaded.workShifts.size)
            assertEquals(1, reloaded.expenses.size)
            assertEquals(1, reloaded.savingsGoals.size)
            assertEquals(1500.0, reloaded.savingsGoals.first().currentAmount, 0.001)
            assertEquals(1, reloaded.savingsTransfers.size)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `dashboard loads without login and profile menu toggles logout visibility by auth state`() {
        var expanded by mutableStateOf(false)
        var signedIn by mutableStateOf(false)

        composeRule.setContent {
            CompositionLocalProvider(LocalAppStrings provides stringsFor(AppLanguage.EN)) {
                MyFinanceTheme {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box {
                                ProfileAccountButton(
                                    isSignedIn = signedIn,
                                    displayName = "Ali",
                                    onClick = { expanded = true }
                                )
                                AccountMenuDropdown(
                                    expanded = expanded,
                                    onDismiss = { expanded = false },
                                    isSignedIn = signedIn,
                                    displayName = "Ali",
                                    userEmail = "ali@example.com",
                                    onLogInClick = {},
                                    onCreateAccountClick = {},
                                    onProfileSettingsClick = {},
                                    onLogOutClick = {}
                                )
                            }
                        }
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            HomeDashboardScreen(
                                state = FinanceUiState(status = DataStatus.Ready, isSignedIn = signedIn),
                                onAddShift = {},
                                onAddExpense = {},
                                onToggleHideBalances = {},
                                onLoadDemoData = {},
                                onOpenSavings = {},
                                onOpenDebts = {},
                                onOpenRecurring = {},
                                onOpenSettings = {},
                                onOpenCalendar = {}
                            )
                        }
                    }
                }
            }
        }

        // Main dashboard quick actions & top profile button visible directly without login
        composeRule.onNodeWithTag("home_add_shift_button").assertIsDisplayed()
        composeRule.onNodeWithTag("home_add_expense_button").assertIsDisplayed()
        composeRule.onNodeWithTag("top_profile_button").assertIsDisplayed()

        // Open Account Menu while logged out -> Log In, Create Account, Profile Settings shown; Log Out hidden
        composeRule.onNodeWithTag("top_profile_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("account_menu_login").assertIsDisplayed()
        composeRule.onNodeWithTag("account_menu_create_account").assertIsDisplayed()
        composeRule.onNodeWithTag("account_menu_profile_settings").assertIsDisplayed()
        composeRule.onNodeWithTag("account_menu_logout").assertDoesNotExist()

        // Simulate logged in state -> Log Out becomes visible
        composeRule.runOnUiThread { signedIn = true }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("account_menu_logout").assertIsDisplayed()
    }
}
