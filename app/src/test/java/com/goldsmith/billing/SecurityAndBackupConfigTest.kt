package com.goldsmith.billing

import androidx.biometric.BiometricManager
import com.goldsmith.billing.ui.auth.MobileSecurityAuth
import com.goldsmith.billing.util.BackupFileConfig
import com.goldsmith.billing.util.BackupSchedule
import com.goldsmith.billing.util.DriveBackupConfig
import com.goldsmith.billing.util.LocalBackupStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

class SecurityAndBackupConfigTest {

    @Test
    fun `mobile security allows biometric or device credential`() {
        val expected = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

        assertEquals(expected, MobileSecurityAuth.allowedAuthenticators)
        assertEquals(BiometricManager.Authenticators.BIOMETRIC_STRONG, MobileSecurityAuth.loginPromptAuthenticators)
        assertTrue(MobileSecurityAuth.isAvailable(BiometricManager.BIOMETRIC_SUCCESS))
        assertFalse(MobileSecurityAuth.isAvailable(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED))
    }

    @Test
    fun `login biometric falls back to app pin after three biometric failures`() {
        assertFalse(MobileSecurityAuth.shouldFallbackToPinAfterFailures(2))
        assertTrue(MobileSecurityAuth.shouldFallbackToPinAfterFailures(3))
        assertTrue(MobileSecurityAuth.shouldFallbackToPinAfterFailures(4))
    }

    @Test
    fun `drive backup uses hidden app data folder like app backups`() {
        assertEquals("appDataFolder", DriveBackupConfig.SPACE)
        assertEquals("appDataFolder", DriveBackupConfig.PARENT)
        assertEquals("goldsmith_sync_v3.enc", DriveBackupConfig.REMOTE_FILE)
        assertTrue(DriveBackupConfig.FILE_QUERY.contains("appDataFolder"))
        assertFalse(DriveBackupConfig.FILE_QUERY.contains("'root'"))
    }

    @Test
    fun `backup account resolution retries with selected picker email`() {
        assertEquals(
            "owner@example.com",
            DriveBackupConfig.resolveActiveAccountEmail(
                pickerEmail = " owner@example.com ",
                lastSignedInEmail = "old@example.com"
            )
        )
        assertEquals(
            "fallback@example.com",
            DriveBackupConfig.resolveActiveAccountEmail(
                pickerEmail = "",
                lastSignedInEmail = "fallback@example.com"
            )
        )
    }

    @Test
    fun `saved backup email is display only and does not replace active sign in`() {
        assertFalse(
            DriveBackupConfig.hasActiveDriveAccount(
                activeEmail = "",
                savedEmail = "saved@example.com"
            )
        )
        assertTrue(
            DriveBackupConfig.hasActiveDriveAccount(
                activeEmail = "active@example.com",
                savedEmail = ""
            )
        )
    }

    @Test
    fun `auto backup creates timestamped retained backup file names`() {
        val calendar = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 11, 1, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }

        assertEquals("abu_star_auto_20260511_0100.asdb", BackupFileConfig.dailyAutoFileName(Date(calendar.timeInMillis)))
    }

    @Test
    fun `auto backup uses fixed app media backup folder name`() {
        assertEquals("Backups", LocalBackupStore.FOLDER_NAME)
    }

    @Test
    fun `daily backup schedule targets next one am`() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 11, 0, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val afterOne = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 11, 1, 5, 0)
            set(Calendar.MILLISECOND, 0)
        }

        assertEquals(30 * 60 * 1000L, BackupSchedule.initialDelayToNextDailyBackup(now.timeInMillis))
        assertEquals(23 * 60 * 60 * 1000L + 55 * 60 * 1000L, BackupSchedule.initialDelayToNextDailyBackup(afterOne.timeInMillis))
    }
}
