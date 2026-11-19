package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.i18n.AppLanguage
import com.example.domain.DateFormatOption
import com.example.domain.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.financeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "my_finance_prefs"
)

data class LocalAppPreferences(
    val language: AppLanguage = AppLanguage.EN,
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val dateFormat: DateFormatOption = DateFormatOption.DD_MM_YYYY,
    val hideBalances: Boolean = false,
    val pinEnabled: Boolean = false,
    val pinCode: String = ""
)

class LocalPreferencesRepository(private val context: Context) {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val THEME = stringPreferencesKey("theme")
        val DATE_FORMAT = stringPreferencesKey("date_format")
        val HIDE_BALANCES = booleanPreferencesKey("hide_balances")
        val PIN_ENABLED = booleanPreferencesKey("pin_enabled")
        val PIN_CODE = stringPreferencesKey("pin_code")
    }

    val preferencesFlow: Flow<LocalAppPreferences> = context.financeDataStore.data.map { prefs ->
        LocalAppPreferences(
            language = AppLanguage.fromCode(prefs[Keys.LANGUAGE]),
            theme = ThemePreference.fromKey(prefs[Keys.THEME]),
            dateFormat = DateFormatOption.fromKey(prefs[Keys.DATE_FORMAT]),
            hideBalances = prefs[Keys.HIDE_BALANCES] ?: false,
            pinEnabled = prefs[Keys.PIN_ENABLED] ?: false,
            pinCode = prefs[Keys.PIN_CODE] ?: ""
        )
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.financeDataStore.edit { it[Keys.LANGUAGE] = language.code }
    }

    suspend fun setTheme(theme: ThemePreference) {
        context.financeDataStore.edit { it[Keys.THEME] = theme.key }
    }

    suspend fun setDateFormat(dateFormat: DateFormatOption) {
        context.financeDataStore.edit { it[Keys.DATE_FORMAT] = dateFormat.key }
    }

    suspend fun setHideBalances(hide: Boolean) {
        context.financeDataStore.edit { it[Keys.HIDE_BALANCES] = hide }
    }

    suspend fun setPinLock(enabled: Boolean, pinCode: String) {
        context.financeDataStore.edit {
            it[Keys.PIN_ENABLED] = enabled
            it[Keys.PIN_CODE] = if (enabled) pinCode else ""
        }
    }
}
