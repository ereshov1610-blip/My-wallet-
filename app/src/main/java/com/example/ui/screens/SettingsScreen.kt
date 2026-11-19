package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.core.i18n.AppLanguage
import com.example.core.i18n.LocalAppStrings
import com.example.domain.CurrencyCode
import com.example.domain.DateFormatOption
import com.example.domain.ThemePreference
import com.example.ui.components.CurrencySelectorRow
import com.example.ui.components.FinanceCard
import com.example.ui.theme.LocalFinanceColors
import com.example.ui.viewmodel.FinanceUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: FinanceUiState,
    userEmail: String?,
    onBack: () -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    onSelectTheme: (ThemePreference) -> Unit,
    onSelectDateFormat: (DateFormatOption) -> Unit,
    onSaveFinancialPrefs: (CurrencyCode, Double, Double, Double, Double, Double) -> Unit,
    onExportJsonBackup: () -> Unit,
    onImportJsonBackup: () -> Unit,
    onExportCsv: () -> Unit,
    onExportPdf: () -> Unit,
    onSetHideBalances: (Boolean) -> Unit,
    onSetPinLock: (Boolean, String) -> Unit,
    onLoadDemoData: () -> Unit,
    onClearAllData: () -> Unit,
    onOpenLogIn: () -> Unit,
    onSignOut: () -> Unit
) {
    val strings = LocalAppStrings.current
    val financeColors = LocalFinanceColors.current

    val profile = state.profile
    var selectedCurrency by remember(profile?.primaryCurrency) {
        mutableStateOf(state.primaryCurrency)
    }
    var incomeGoalText by remember(profile?.monthlyIncomeGoal) {
        mutableStateOf((profile?.monthlyIncomeGoal ?: 45000.0).toString())
    }
    var budgetLimitText by remember(profile?.monthlyBudgetLimit) {
        mutableStateOf((profile?.monthlyBudgetLimit ?: 25000.0).toString())
    }
    var tryRateText by remember(profile?.rateTryToUsd) {
        mutableStateOf((profile?.rateTryToUsd ?: 34.5).toString())
    }
    var tmtRateText by remember(profile?.rateTmtToUsd) {
        mutableStateOf((profile?.rateTmtToUsd ?: 3.5).toString())
    }
    var eurRateText by remember(profile?.rateEurToUsd) {
        mutableStateOf((profile?.rateEurToUsd ?: 0.92).toString())
    }

    var showPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var showClearConfirm by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = strings.settingsTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Language Selection (RU, EN, TR, TK)
        item {
            FinanceCard {
                Text(strings.languageLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AppLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = state.preferences.language == lang,
                            onClick = { onSelectLanguage(lang) },
                            label = { Text(lang.nativeName) },
                            modifier = Modifier.testTag("settings_lang_${lang.code}")
                        )
                    }
                }
            }
        }

        // Appearance & Date Format
        item {
            FinanceCard {
                Text(strings.appearanceTheme, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.preferences.theme == ThemePreference.SYSTEM,
                        onClick = { onSelectTheme(ThemePreference.SYSTEM) },
                        label = { Text(strings.themeSystem) }
                    )
                    FilterChip(
                        selected = state.preferences.theme == ThemePreference.LIGHT,
                        onClick = { onSelectTheme(ThemePreference.LIGHT) },
                        label = { Text(strings.themeLight) }
                    )
                    FilterChip(
                        selected = state.preferences.theme == ThemePreference.DARK,
                        onClick = { onSelectTheme(ThemePreference.DARK) },
                        label = { Text(strings.themeDark) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(strings.dateFormatLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateFormatOption.entries.forEach { fmt ->
                        FilterChip(
                            selected = state.preferences.dateFormat == fmt,
                            onClick = { onSelectDateFormat(fmt) },
                            label = { Text(fmt.patternLabel) }
                        )
                    }
                }
            }
        }

        // Currency, Goals & Exchange Rates
        item {
            FinanceCard {
                Text(strings.primaryCurrency, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                CurrencySelectorRow(selected = selectedCurrency, onSelect = { selectedCurrency = it })

                Spacer(modifier = Modifier.height(12.dp))
                Text(strings.financialPreferences, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = incomeGoalText,
                        onValueChange = { incomeGoalText = it },
                        label = { Text(strings.monthlyIncomeGoal) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = budgetLimitText,
                        onValueChange = { budgetLimitText = it },
                        label = { Text(strings.overallMonthlyBudget) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(strings.exchangeRatesTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(strings.exchangeRatesSubtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = tryRateText,
                        onValueChange = { tryRateText = it },
                        label = { Text("TRY") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = tmtRateText,
                        onValueChange = { tmtRateText = it },
                        label = { Text("TMT") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = eurRateText,
                        onValueChange = { eurRateText = it },
                        label = { Text("EUR") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        val goal = incomeGoalText.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0) ?: 45000.0
                        val budget = budgetLimitText.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0) ?: 25000.0
                        val tryR = tryRateText.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 } ?: 34.5
                        val tmtR = tmtRateText.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 } ?: 3.5
                        val eurR = eurRateText.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 } ?: 0.92
                        onSaveFinancialPrefs(selectedCurrency, goal, budget, tryR, tmtR, eurR)
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_save_financial_btn")
                ) {
                    Text(strings.save)
                }
            }
        }

        // Backup, Restore & Export
        item {
            FinanceCard {
                Text(strings.backupRestoreTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = onExportJsonBackup,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_export_json")
                    ) {
                        Icon(Icons.Rounded.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.exportJsonBackup)
                    }
                    OutlinedButton(
                        onClick = onImportJsonBackup,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_import_json")
                    ) {
                        Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.importJsonBackup)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onExportCsv,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.exportExcelCsv, maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = onExportPdf,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.exportPdfReport, maxLines = 1)
                        }
                    }
                }
            }
        }

        // Privacy & Security
        item {
            FinanceCard {
                Text(strings.privacySecurityTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(strings.hideBalancesLabel, style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = state.preferences.hideBalances,
                        onCheckedChange = onSetHideBalances
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(strings.appPinLockLabel, style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = state.preferences.pinEnabled,
                        onCheckedChange = { enable ->
                            if (enable) {
                                pinInput = ""
                                showPinDialog = true
                            } else {
                                onSetPinLock(false, "")
                            }
                        }
                    )
                }
            }
        }

        // Demo Data & Reset
        item {
            FinanceCard {
                Text(strings.dataManagementTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = onLoadDemoData,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.loadDemoData, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { showClearConfirm = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = financeColors.expense),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.clearAllData, maxLines = 1)
                    }
                }
            }
        }

        // Account & Cloud Sync Status (Optional)
        item {
            FinanceCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.isSignedIn) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.signedInAs,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = userEmail ?: state.profile?.displayName ?: "Google Account",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        FilledTonalButton(
                            onClick = onSignOut,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = financeColors.expenseBg,
                                contentColor = financeColors.expense
                            ),
                            modifier = Modifier.testTag("settings_sign_out_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.menuLogOut)
                        }
                    } else {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = strings.guestModeLabel,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = strings.localDataPreservedNote,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(
                            onClick = onOpenLogIn,
                            modifier = Modifier.testTag("settings_log_in_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.menuLogIn)
                        }
                    }
                }
            }
        }
    }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text(strings.setPinPrompt, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { if (it.length <= 4 && it.all { ch -> ch.isDigit() }) pinInput = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    enabled = pinInput.length == 4,
                    onClick = {
                        onSetPinLock(true, pinInput)
                        showPinDialog = false
                    }
                ) { Text(strings.save) }
            },
            dismissButton = { TextButton(onClick = { showPinDialog = false }) { Text(strings.cancel) } }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(strings.clearAllData, fontWeight = FontWeight.Bold) },
            text = { Text(strings.clearAllConfirm) },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = financeColors.expense),
                    onClick = {
                        onClearAllData()
                        showClearConfirm = false
                    }
                ) { Text(strings.delete) }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text(strings.cancel) } }
        )
    }
}
