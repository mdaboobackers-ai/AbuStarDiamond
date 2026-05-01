package com.goldsmith.billing.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
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
        val LANGUAGE               = stringPreferencesKey("language")
        val THEME                  = stringPreferencesKey("theme")
        val GOLD_RATE_24K          = doublePreferencesKey("gold_rate_24k")
        val GOLD_RATE_22K          = doublePreferencesKey("gold_rate_22k")
        val GOLD_RATE_20K          = doublePreferencesKey("gold_rate_20k")
        val GOLD_RATE_18K          = doublePreferencesKey("gold_rate_18k")
        val LAST_BACKUP_TIME       = longPreferencesKey("last_backup_time")
        val AUTO_BACKUP_ENABLED    = booleanPreferencesKey("auto_backup_enabled")
        val INVOICE_COUNTER        = intPreferencesKey("invoice_counter")
        val GST_PERCENT            = doublePreferencesKey("gst_percent")
        val INACTIVITY_LOCK_SECS   = intPreferencesKey("inactivity_lock_secs")
        val SELECTED_ICON          = stringPreferencesKey("selected_app_icon")
        val CUSTOM_ICON_URI        = stringPreferencesKey("custom_icon_uri")
        // FIX: Multi-user device prefix
        val DEVICE_PREFIX          = stringPreferencesKey("device_prefix")
        val DEVICE_OWNER_NAME      = stringPreferencesKey("device_owner_name")
        // FIX: First launch flag
        val FIRST_LAUNCH           = booleanPreferencesKey("first_launch")
        // FIX: Sync last timestamp per device
        val LAST_SYNC_TIME         = longPreferencesKey("last_sync_time")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            AppSettings(
                language            = prefs[LANGUAGE] ?: "en",
                isDarkTheme         = (prefs[THEME] ?: "dark") == "dark",
                goldRate24K         = prefs[GOLD_RATE_24K] ?: 7245.0,
                goldRate22K         = prefs[GOLD_RATE_22K] ?: 6636.42,
                goldRate20K         = prefs[GOLD_RATE_20K] ?: 6158.25,
                goldRate18K         = prefs[GOLD_RATE_18K] ?: 5433.75,
                lastBackupTime      = prefs[LAST_BACKUP_TIME] ?: 0L,
                autoBackupEnabled   = prefs[AUTO_BACKUP_ENABLED] ?: true,
                invoiceCounter      = prefs[INVOICE_COUNTER] ?: 1000,
                gstPercent          = prefs[GST_PERCENT] ?: 3.0,
                inactivityLockSecs  = prefs[INACTIVITY_LOCK_SECS] ?: 120,
                selectedIcon        = prefs[SELECTED_ICON] ?: "default",
                customIconUri       = prefs[CUSTOM_ICON_URI] ?: "",
                devicePrefix        = prefs[DEVICE_PREFIX] ?: "",
                deviceOwnerName     = prefs[DEVICE_OWNER_NAME] ?: "",
                isFirstLaunch       = prefs[FIRST_LAUNCH] ?: true,
                lastSyncTime        = prefs[LAST_SYNC_TIME] ?: 0L
            )
        }

    // FIX: synchronous read for locale application before UI renders
    suspend fun getSettingsSync(): AppSettings = settingsFlow.first()

    suspend fun updateLanguage(lang: String) =
        context.dataStore.edit { it[LANGUAGE] = lang }

    suspend fun updateTheme(isDark: Boolean) =
        context.dataStore.edit { it[THEME] = if (isDark) "dark" else "light" }

    suspend fun updateGoldRates(rate24K: Double) =
        context.dataStore.edit {
            it[GOLD_RATE_24K] = rate24K
            it[GOLD_RATE_22K] = rate24K * 0.916
            it[GOLD_RATE_20K] = rate24K * 0.85
            it[GOLD_RATE_18K] = rate24K * 0.75
        }

    suspend fun updateGoldRatesManual(r24: Double, r22: Double, r20: Double, r18: Double) =
        context.dataStore.edit {
            it[GOLD_RATE_24K] = r24
            it[GOLD_RATE_22K] = r22
            it[GOLD_RATE_20K] = r20
            it[GOLD_RATE_18K] = r18
        }

    suspend fun updateLastBackupTime(time: Long) =
        context.dataStore.edit { it[LAST_BACKUP_TIME] = time }

    suspend fun setAutoBackupEnabled(enabled: Boolean) =
        context.dataStore.edit { it[AUTO_BACKUP_ENABLED] = enabled }

    suspend fun updateGstPercent(pct: Double) =
        context.dataStore.edit { it[GST_PERCENT] = pct }

    suspend fun updateInactivityLock(secs: Int) =
        context.dataStore.edit { it[INACTIVITY_LOCK_SECS] = secs }

    suspend fun incrementInvoiceCounter(): Int {
        var next = 0
        context.dataStore.edit { prefs ->
            next = (prefs[INVOICE_COUNTER] ?: 1000) + 1
            prefs[INVOICE_COUNTER] = next
        }
        return next
    }

    suspend fun updateSelectedIcon(icon: String) =
        context.dataStore.edit { it[SELECTED_ICON] = icon }

    suspend fun updateCustomIconUri(uri: String) =
        context.dataStore.edit { it[CUSTOM_ICON_URI] = uri }

    suspend fun updateDevicePrefix(prefix: String, ownerName: String) =
        context.dataStore.edit {
            it[DEVICE_PREFIX] = prefix
            it[DEVICE_OWNER_NAME] = ownerName
        }

    suspend fun setFirstLaunchDone() =
        context.dataStore.edit { it[FIRST_LAUNCH] = false }

    suspend fun updateLastSyncTime(time: Long) =
        context.dataStore.edit { it[LAST_SYNC_TIME] = time }
}

data class AppSettings(
    val language: String = "en",
    val isDarkTheme: Boolean = true,
    val goldRate24K: Double = 7245.0,
    val goldRate22K: Double = 6636.42,
    val goldRate20K: Double = 6158.25,
    val goldRate18K: Double = 5433.75,
    val lastBackupTime: Long = 0L,
    val autoBackupEnabled: Boolean = true,
    val invoiceCounter: Int = 1000,
    val gstPercent: Double = 3.0,
    val inactivityLockSecs: Int = 120,
    val selectedIcon: String = "default",
    val customIconUri: String = "",
    val devicePrefix: String = "",
    val deviceOwnerName: String = "",
    val isFirstLaunch: Boolean = true,
    val lastSyncTime: Long = 0L
)
