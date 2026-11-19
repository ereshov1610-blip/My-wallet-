package com.example

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.core.i18n.LocalAppStrings
import com.example.core.i18n.stringsFor
import com.example.data.FinanceRepository
import com.example.data.LocalAppPreferences
import com.example.data.LocalFinanceStore
import com.example.data.LocalPreferencesRepository
import com.example.domain.DateUtils
import com.example.domain.ExpenseRecord
import com.example.domain.WorkShift
import com.example.ui.auth.AccountAuthModalDialog
import com.example.ui.auth.AccountMenuDropdown
import com.example.ui.auth.AuthModalMode
import com.example.ui.auth.ProfileAccountButton
import com.example.ui.auth.ProfileSettingsDialog
import com.example.ui.auth.signOutUser
import com.example.ui.components.ExpenseEditorDialog
import com.example.ui.components.PinLockOverlay
import com.example.ui.components.WorkShiftEditorDialog
import com.example.ui.screens.CalendarScreen
import com.example.ui.screens.DebtManagementScreen
import com.example.ui.screens.ExpenseTrackerScreen
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.RecurringBudgetsScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.SavingsGoalsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WorkTrackerScreen
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.LocalFinanceColors
import com.example.ui.theme.MyFinanceTheme
import com.example.ui.viewmodel.DataStatus
import com.example.ui.viewmodel.FinanceViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val CALENDAR = "calendar"
    const val WORK = "work"
    const val EXPENSES = "expenses"
    const val REPORTS = "reports"
    const val SAVINGS = "savings"
    const val DEBTS = "debts"
    const val RECURRING = "recurring"
    const val SETTINGS = "settings"
}

private data class BottomNavTab(
    val route: String,
    val label: String,
    val icon: ImageVector
)

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyFinanceAppRoot()
        }
    }
}

@Composable
fun MyFinanceAppRoot() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val prefsRepo = remember(appContext) { LocalPreferencesRepository(appContext) }
    val localStore = remember(appContext) { LocalFinanceStore(appContext) }
    val localPrefs by prefsRepo.preferencesFlow.collectAsStateWithLifecycle(initialValue = LocalAppPreferences())
    val strings = remember(localPrefs.language) { stringsFor(localPrefs.language) }

    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    CompositionLocalProvider(LocalAppStrings provides strings) {
        MyFinanceTheme(themePreference = localPrefs.theme) {
            val uid = currentUser?.uid
            val sessionKey = uid ?: "local"
            key(sessionKey) {
                val vm: FinanceViewModel = viewModel(
                    key = sessionKey,
                    factory = viewModelFactory {
                        initializer {
                            val app = checkNotNull(this[APPLICATION_KEY])
                            val dbId = app.getString(R.string.firestore_database_id)
                            val db = FirebaseFirestore.getInstance(dbId)
                            FinanceViewModel(
                                localStore = localStore,
                                repository = FinanceRepository(db),
                                prefsRepository = LocalPreferencesRepository(app),
                                currentUserId = uid,
                                userDisplayName = currentUser?.displayName ?: "Finance User",
                                userEmail = currentUser?.email
                            )
                        }
                    }
                )
                MyFinanceAppShell(
                    viewModel = vm,
                    userEmail = currentUser?.email
                )
            }
        }
    }
}

@Composable
private fun MyFinanceAppShell(
    viewModel: FinanceViewModel,
    userEmail: String?
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current
    val financeColors = LocalFinanceColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var pinUnlocked by remember(state.preferences.pinEnabled) {
        mutableStateOf(!state.preferences.pinEnabled)
    }

    // Account Menu & Modal States
    var showAccountMenu by remember { mutableStateOf(false) }
    var authModalMode by remember { mutableStateOf<AuthModalMode?>(null) }
    var showProfileSettings by remember { mutableStateOf(false) }

    // Global Editor Modals
    var showShiftDialog by remember { mutableStateOf(false) }
    var editingShift by remember { mutableStateOf<WorkShift?>(null) }
    var shiftDefaultDate by remember { mutableStateOf(DateUtils.todayIso()) }

    var showExpenseDialog by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<ExpenseRecord?>(null) }
    var expenseDefaultDate by remember { mutableStateOf(DateUtils.todayIso()) }

    // SAF Document Launchers for Backup & Exports (Zero Storage Permissions)
    val exportJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val json = viewModel.buildBackupJson()
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    }
                }.onSuccess {
                    snackbarHostState.showSnackbar(strings.exportSuccess)
                }.onFailure {
                    snackbarHostState.showSnackbar(it.localizedMessage ?: strings.errorGeneric)
                }
            }
        }
    }

    val importJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                }.onSuccess { jsonText ->
                    if (jsonText.isNotBlank()) {
                        viewModel.restoreFromBackupJson(jsonText, strings.backupRestoredMsg)
                    }
                }.onFailure {
                    snackbarHostState.showSnackbar(it.localizedMessage ?: strings.errorGeneric)
                }
            }
        }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val csv = viewModel.buildReportCsv(strings)
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(csv.toByteArray(Charsets.UTF_8))
                    }
                }.onSuccess {
                    snackbarHostState.showSnackbar(strings.exportSuccess)
                }.onFailure {
                    snackbarHostState.showSnackbar(it.localizedMessage ?: strings.errorGeneric)
                }
            }
        }
    }

    val exportPdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        viewModel.writeReportPdf(out, strings)
                    }
                }.onSuccess {
                    snackbarHostState.showSnackbar(strings.exportSuccess)
                }.onFailure {
                    snackbarHostState.showSnackbar(it.localizedMessage ?: strings.errorGeneric)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val bottomTabs = remember(strings) {
        listOf(
            BottomNavTab(Routes.HOME, strings.navHome, Icons.Rounded.Home),
            BottomNavTab(Routes.CALENDAR, strings.navCalendar, Icons.Rounded.CalendarMonth),
            BottomNavTab(Routes.WORK, strings.navWork, Icons.Rounded.Work),
            BottomNavTab(Routes.EXPENSES, strings.navExpenses, Icons.Rounded.ReceiptLong),
            BottomNavTab(Routes.REPORTS, strings.navReports, Icons.Rounded.Assessment)
        )
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Routes.HOME
    val primaryRoutes = setOf(Routes.HOME, Routes.CALENDAR, Routes.WORK, Routes.EXPENSES, Routes.REPORTS)
    val showPrimaryNav = currentRoute in primaryRoutes

    fun navigateTopTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    if (state.preferences.pinEnabled && !pinUnlocked && state.preferences.pinCode.length == 4) {
        PinLockOverlay(
            expectedPin = state.preferences.pinCode,
            onUnlocked = { pinUnlocked = true }
        )
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                // Sleek Top Application Bar with Top-Right Profile / Account Icon
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = BrandBlue,
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.AccountBalanceWallet,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = strings.appTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (state.isSignedIn) {
                                        financeColors.incomeBg
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (state.isSignedIn) financeColors.income else BrandBlue
                                                )
                                        )
                                        Text(
                                            text = if (state.isSignedIn) strings.syncedBadgeLabel else strings.localBadgeLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (state.isSignedIn) {
                                                financeColors.income
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                }
                            }

                            Box {
                                ProfileAccountButton(
                                    isSignedIn = state.isSignedIn,
                                    displayName = state.profile?.displayName ?: "",
                                    onClick = { showAccountMenu = true }
                                )

                                AccountMenuDropdown(
                                    expanded = showAccountMenu,
                                    onDismiss = { showAccountMenu = false },
                                    isSignedIn = state.isSignedIn,
                                    displayName = state.profile?.displayName ?: strings.profileMenuTitle,
                                    userEmail = userEmail,
                                    onLogInClick = { authModalMode = AuthModalMode.LOG_IN },
                                    onCreateAccountClick = { authModalMode = AuthModalMode.CREATE_ACCOUNT },
                                    onProfileSettingsClick = { showProfileSettings = true },
                                    onLogOutClick = {
                                        signOutUser(credentialManager, scope) {
                                            viewModel.showMessage(strings.signedOutLocalMsg)
                                        }
                                    }
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                    }
                }
            },
            bottomBar = {
                if (!useNavRail && showPrimaryNav) {
                    NavigationBar {
                        bottomTabs.forEach { tab ->
                            NavigationBarItem(
                                selected = currentRoute == tab.route,
                                onClick = { navigateTopTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = {
                                    Text(
                                        text = tab.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier.testTag("nav_${tab.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail && showPrimaryNav) {
                    NavigationRail {
                        bottomTabs.forEach { tab ->
                            NavigationRailItem(
                                selected = currentRoute == tab.route,
                                onClick = { navigateTopTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label, maxLines = 1) }
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    if (state.status is DataStatus.Loading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        NavHost(
                            navController = navController,
                            startDestination = Routes.HOME,
                            modifier = Modifier.widthIn(max = 860.dp)
                        ) {
                            composable(Routes.HOME) {
                                HomeDashboardScreen(
                                    state = state,
                                    onAddShift = {
                                        editingShift = null
                                        shiftDefaultDate = DateUtils.todayIso()
                                        showShiftDialog = true
                                    },
                                    onAddExpense = {
                                        editingExpense = null
                                        expenseDefaultDate = DateUtils.todayIso()
                                        showExpenseDialog = true
                                    },
                                    onToggleHideBalances = {
                                        viewModel.setHideBalances(!state.preferences.hideBalances)
                                    },
                                    onLoadDemoData = { viewModel.loadDemoData() },
                                    onOpenSavings = { navController.navigate(Routes.SAVINGS) },
                                    onOpenDebts = { navController.navigate(Routes.DEBTS) },
                                    onOpenRecurring = { navController.navigate(Routes.RECURRING) },
                                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                                    onOpenCalendar = { navigateTopTab(Routes.CALENDAR) }
                                )
                            }

                            composable(Routes.CALENDAR) {
                                CalendarScreen(
                                    state = state,
                                    onSelectDate = { viewModel.selectDate(it) },
                                    onAddShiftForDate = { dIso ->
                                        editingShift = null
                                        shiftDefaultDate = dIso
                                        showShiftDialog = true
                                    },
                                    onEditShift = { s ->
                                        editingShift = s
                                        shiftDefaultDate = s.dateIso
                                        showShiftDialog = true
                                    },
                                    onDuplicateShift = { s ->
                                        viewModel.duplicateWorkShift(s, strings.shiftDuplicated)
                                    },
                                    onDeleteShift = { s ->
                                        viewModel.deleteWorkShift(s.id, strings.shiftDeleted)
                                    },
                                    onAddExpenseForDate = { dIso ->
                                        editingExpense = null
                                        expenseDefaultDate = dIso
                                        showExpenseDialog = true
                                    },
                                    onEditExpense = { e ->
                                        editingExpense = e
                                        expenseDefaultDate = e.dateIso
                                        showExpenseDialog = true
                                    },
                                    onDeleteExpense = { e ->
                                        viewModel.deleteExpense(e.id, strings.expenseDeleted)
                                    }
                                )
                            }

                            composable(Routes.WORK) {
                                WorkTrackerScreen(
                                    state = state,
                                    onAddShift = {
                                        editingShift = null
                                        shiftDefaultDate = DateUtils.todayIso()
                                        showShiftDialog = true
                                    },
                                    onEditShift = { s ->
                                        editingShift = s
                                        shiftDefaultDate = s.dateIso
                                        showShiftDialog = true
                                    },
                                    onDuplicateShift = { s ->
                                        viewModel.duplicateWorkShift(s, strings.shiftDuplicated)
                                    },
                                    onDeleteShift = { s ->
                                        viewModel.deleteWorkShift(s.id, strings.shiftDeleted)
                                    }
                                )
                            }

                            composable(Routes.EXPENSES) {
                                ExpenseTrackerScreen(
                                    state = state,
                                    onAddExpense = {
                                        editingExpense = null
                                        expenseDefaultDate = DateUtils.todayIso()
                                        showExpenseDialog = true
                                    },
                                    onEditExpense = { e ->
                                        editingExpense = e
                                        expenseDefaultDate = e.dateIso
                                        showExpenseDialog = true
                                    },
                                    onDeleteExpense = { e ->
                                        viewModel.deleteExpense(e.id, strings.expenseDeleted)
                                    },
                                    onSaveCategory = { c -> viewModel.saveCategory(c) },
                                    onDeleteCategory = { c -> viewModel.deleteCategory(c.id) }
                                )
                            }

                            composable(Routes.REPORTS) {
                                ReportsScreen(
                                    state = state,
                                    onSelectPeriod = { viewModel.setReportPeriod(it) },
                                    onSelectAnchorDate = { viewModel.setReportAnchorDate(it) },
                                    onSelectCustomRange = { s, e -> viewModel.setCustomDateRange(s, e) },
                                    onExportCsv = {
                                        exportCsvLauncher.launch("my_finance_report_${DateUtils.todayIso()}.csv")
                                    },
                                    onExportPdf = {
                                        exportPdfLauncher.launch("my_finance_report_${DateUtils.todayIso()}.pdf")
                                    }
                                )
                            }

                            composable(Routes.SAVINGS) {
                                BackHandler { navController.popBackStack() }
                                SavingsGoalsScreen(
                                    state = state,
                                    onBack = { navController.popBackStack() },
                                    onSaveGoal = { viewModel.saveSavingsGoal(it) },
                                    onDeleteGoal = { viewModel.deleteSavingsGoal(it.id) },
                                    onTransferToGoal = { goal, amt, dIso, note ->
                                        viewModel.transferToSavings(goal, amt, dIso, note)
                                    }
                                )
                            }

                            composable(Routes.DEBTS) {
                                BackHandler { navController.popBackStack() }
                                DebtManagementScreen(
                                    state = state,
                                    onBack = { navController.popBackStack() },
                                    onSaveDebt = { viewModel.saveDebt(it) },
                                    onDeleteDebt = { viewModel.deleteDebt(it.id) }
                                )
                            }

                            composable(Routes.RECURRING) {
                                BackHandler { navController.popBackStack() }
                                RecurringBudgetsScreen(
                                    state = state,
                                    onBack = { navController.popBackStack() },
                                    onSaveRecurring = { viewModel.saveRecurringExpense(it) },
                                    onLogRecurringNow = { viewModel.logRecurringNow(it) },
                                    onDeleteRecurring = { viewModel.deleteRecurringExpense(it.id) },
                                    onSaveBudget = { viewModel.saveCategoryBudget(it) },
                                    onDeleteBudget = { viewModel.deleteCategoryBudget(it) }
                                )
                            }

                            composable(Routes.SETTINGS) {
                                BackHandler { navController.popBackStack() }
                                SettingsScreen(
                                    state = state,
                                    userEmail = userEmail,
                                    onBack = { navController.popBackStack() },
                                    onSelectLanguage = { viewModel.setLanguage(it) },
                                    onSelectTheme = { viewModel.setTheme(it) },
                                    onSelectDateFormat = { viewModel.setDateFormat(it) },
                                    onSaveFinancialPrefs = { cur, goal, budget, tryR, tmtR, eurR ->
                                        viewModel.updateFinancialPreferences(
                                            primaryCurrency = cur,
                                            monthlyIncomeGoal = goal,
                                            monthlyBudgetLimit = budget,
                                            rateTryToUsd = tryR,
                                            rateTmtToUsd = tmtR,
                                            rateEurToUsd = eurR,
                                            savedMsg = strings.save
                                        )
                                    },
                                    onExportJsonBackup = {
                                        exportJsonLauncher.launch("my_finance_backup_${DateUtils.todayIso()}.json")
                                    },
                                    onImportJsonBackup = {
                                        importJsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                                    },
                                    onExportCsv = {
                                        exportCsvLauncher.launch("my_finance_report_${DateUtils.todayIso()}.csv")
                                    },
                                    onExportPdf = {
                                        exportPdfLauncher.launch("my_finance_report_${DateUtils.todayIso()}.pdf")
                                    },
                                    onSetHideBalances = { viewModel.setHideBalances(it) },
                                    onSetPinLock = { en, pin -> viewModel.setPinLock(en, pin) },
                                    onLoadDemoData = { viewModel.loadDemoData() },
                                    onClearAllData = { viewModel.clearAllData() },
                                    onOpenLogIn = { authModalMode = AuthModalMode.LOG_IN },
                                    onSignOut = {
                                        signOutUser(credentialManager, scope) {
                                            viewModel.showMessage(strings.signedOutLocalMsg)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    authModalMode?.let { mode ->
        AccountAuthModalDialog(
            mode = mode,
            isSignedIn = state.isSignedIn,
            userEmail = userEmail,
            onDismiss = { authModalMode = null },
            onAuthSuccess = {
                viewModel.showMessage(strings.accountSyncedMsg)
            }
        )
    }

    if (showProfileSettings) {
        ProfileSettingsDialog(
            isSignedIn = state.isSignedIn,
            displayName = state.profile?.displayName ?: "Finance User",
            userEmail = userEmail,
            onDismiss = { showProfileSettings = false },
            onSaveDisplayName = { newName ->
                viewModel.updateDisplayName(newName, strings.profileSavedMsg)
            },
            onOpenLogIn = { authModalMode = AuthModalMode.LOG_IN },
            onOpenCreateAccount = { authModalMode = AuthModalMode.CREATE_ACCOUNT },
            onSyncNow = {
                viewModel.syncLocalToCloudNow(strings.syncCompletedMsg)
            },
            onOpenAppSettings = {
                navController.navigate(Routes.SETTINGS)
            },
            onLogOut = {
                signOutUser(credentialManager, scope) {
                    viewModel.showMessage(strings.signedOutLocalMsg)
                }
            }
        )
    }

    if (showShiftDialog) {
        WorkShiftEditorDialog(
            initialShift = editingShift,
            defaultDateIso = shiftDefaultDate,
            defaultCurrency = state.primaryCurrency,
            dateFormat = state.preferences.dateFormat,
            onDismiss = { showShiftDialog = false },
            onSave = { shift ->
                viewModel.saveWorkShift(shift, strings.shiftSaved)
                showShiftDialog = false
            }
        )
    }

    if (showExpenseDialog) {
        ExpenseEditorDialog(
            initialExpense = editingExpense,
            categories = state.categories,
            defaultDateIso = expenseDefaultDate,
            defaultCurrency = state.primaryCurrency,
            dateFormat = state.preferences.dateFormat,
            onDismiss = { showExpenseDialog = false },
            onSave = { exp ->
                viewModel.saveExpense(exp, strings.expenseSaved)
                showExpenseDialog = false
            }
        )
    }
}
