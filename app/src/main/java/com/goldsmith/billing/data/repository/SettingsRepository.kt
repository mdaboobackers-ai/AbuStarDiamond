package com.goldsmith.billing.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "goldsmith_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val LANGUAGE = stringPreferencesKey("language")         // "en" | "ta"
        val THEME = stringPreferencesKey("theme")               // "dark" | "light"
        val USER_PREFIX = stringPreferencesKey("user_prefix")   // "F", "Y", "B"
        val GOLD_RATE_24K = doublePreferencesKey("gold_rate_24k")
        val GOLD_RATE_22K = doublePreferencesKey("gold_rate_22k")
        val GOLD_RATE_20K = doublePreferencesKey("gold_rate_20k")
        val GOLD_RATE_18K = doublePreferencesKey("gold_rate_18k")
        val LAST_BACKUP_TIME = longPreferencesKey("last_backup_time")
        val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        val INVOICE_COUNTER = intPreferencesKey("invoice_counter")
        val GST_PERCENT = doublePreferencesKey("gst_percent")
        val INACTIVITY_LOCK_SECS = intPreferencesKey("inactivity_lock_secs")
        val SELECTED_ICON = stringPreferencesKey("selected_app_icon")
        val CUSTOM_ICON_URI = stringPreferencesKey("custom_icon_uri")
        val SELECTED_BACKUP_EMAIL = stringPreferencesKey("selected_backup_email")
        val LAST_BACKUP_ACCOUNT_EMAIL = stringPreferencesKey("last_backup_account_email")
        val LAST_RESTORE_ACCOUNT_EMAIL = stringPreferencesKey("last_restore_account_email")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            AppSettings(
                language = prefs[LANGUAGE] ?: "en",
                isDarkTheme = (prefs[THEME] ?: "dark") == "dark",
                userPrefix = prefs[USER_PREFIX] ?: "",
                goldRate24K = prefs[GOLD_RATE_24K] ?: 7245.0,
                goldRate22K = prefs[GOLD_RATE_22K] ?: 6641.0,
                goldRate20K = prefs[GOLD_RATE_20K] ?: 6158.0,
                goldRate18K = prefs[GOLD_RATE_18K] ?: 5434.0,
                lastBackupTime = prefs[LAST_BACKUP_TIME] ?: 0L,
                autoBackupEnabled = prefs[AUTO_BACKUP_ENABLED] ?: true,
                invoiceCounter = prefs[INVOICE_COUNTER] ?: 8829,
                gstPercent = prefs[GST_PERCENT] ?: 3.0,
                inactivityLockSecs = prefs[INACTIVITY_LOCK_SECS] ?: 30,
                selectedIcon = prefs[SELECTED_ICON] ?: "default",
                customIconUri = prefs[CUSTOM_ICON_URI] ?: "",
                selectedBackupEmail = prefs[SELECTED_BACKUP_EMAIL] ?: "",
                lastBackupAccountEmail = prefs[LAST_BACKUP_ACCOUNT_EMAIL] ?: "",
                lastRestoreAccountEmail = prefs[LAST_RESTORE_ACCOUNT_EMAIL] ?: ""
            )
        }

    suspend fun updateLanguage(lang: String) {
        context.dataStore.edit { it[LANGUAGE] = lang }
        context.getSharedPreferences("goldsmith_settings", Context.MODE_PRIVATE)
            .edit()
            .putString("language", lang)
            .apply()
    }

    suspend fun updateTheme(isDark: Boolean) {
        context.dataStore.edit { it[THEME] = if (isDark) "dark" else "light" }
    }

    suspend fun updateUserPrefix(prefix: String) {
        context.dataStore.edit { it[USER_PREFIX] = prefix }
    }

    suspend fun updateGoldRates(rate24K: Double) {
        context.dataStore.edit {
            it[GOLD_RATE_24K] = rate24K
            it[GOLD_RATE_22K] = rate24K * 0.916
            it[GOLD_RATE_20K] = rate24K * 0.85
            it[GOLD_RATE_18K] = rate24K * 0.75
        }
    }

    suspend fun updateGoldRatesManual(rate24K: Double, rate22K: Double, rate20K: Double, rate18K: Double) {
        context.dataStore.edit {
            it[GOLD_RATE_24K] = rate24K
            it[GOLD_RATE_22K] = rate22K
            it[GOLD_RATE_20K] = rate20K
            it[GOLD_RATE_18K] = rate18K
        }
    }

    suspend fun updateLastBackupTime(time: Long) {
        context.dataStore.edit { it[LAST_BACKUP_TIME] = time }
    }

    suspend fun setAutoBackupEnabled(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_BACKUP_ENABLED] = enabled }
    }

    suspend fun incrementInvoiceCounter(): Int {
        var next = 0
        context.dataStore.edit { prefs ->
            next = (prefs[INVOICE_COUNTER] ?: 8829) + 1
            prefs[INVOICE_COUNTER] = next
        }
        return next
    }

    suspend fun updateSelectedIcon(icon: String) {
        context.dataStore.edit { it[SELECTED_ICON] = icon }
    }

    suspend fun updateCustomIconUri(uri: String) {
        context.dataStore.edit { it[CUSTOM_ICON_URI] = uri }
    }

    suspend fun updateGstPercent(percent: Double) {
        context.dataStore.edit { it[GST_PERCENT] = percent }
    }

    suspend fun updateInactivityLockSecs(secs: Int) {
        context.dataStore.edit { it[INACTIVITY_LOCK_SECS] = secs }
    }

    suspend fun updateSelectedBackupEmail(email: String) {
        context.dataStore.edit { it[SELECTED_BACKUP_EMAIL] = email }
    }

    suspend fun updateLastBackupAccountEmail(email: String) {
        context.dataStore.edit { it[LAST_BACKUP_ACCOUNT_EMAIL] = email }
    }

    suspend fun updateLastRestoreAccountEmail(email: String) {
        context.dataStore.edit { it[LAST_RESTORE_ACCOUNT_EMAIL] = email }
    }
}

data class AppSettings(
    val language: String = "en",
    val isDarkTheme: Boolean = true,
    val userPrefix: String = "",
    val goldRate24K: Double = 7245.0,
    val goldRate22K: Double = 6641.0,
    val goldRate20K: Double = 6158.0,
    val goldRate18K: Double = 5434.0,
    val lastBackupTime: Long = 0L,
    val autoBackupEnabled: Boolean = true,
    val invoiceCounter: Int = 8829,
    val gstPercent: Double = 3.0,
    val inactivityLockSecs: Int = 30,
    val selectedIcon: String = "default",
    val customIconUri: String = "",
    val selectedBackupEmail: String = "",
    val lastBackupAccountEmail: String = "",
    val lastRestoreAccountEmail: String = ""
)
