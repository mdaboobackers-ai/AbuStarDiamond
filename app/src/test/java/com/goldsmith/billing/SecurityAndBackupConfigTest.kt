package com.goldsmith.billing

import androidx.biometric.BiometricManager
import com.goldsmith.billing.ui.auth.MobileSecurityAuth
import com.goldsmith.billing.util.DriveBackupConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityAndBackupConfigTest {

    @Test
    fun `mobile security allows biometric or device credential`() {
        val expected = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

        assertEquals(expected, MobileSecurityAuth.allowedAuthenticators)
        assertTrue(MobileSecurityAuth.isAvailable(BiometricManager.BIOMETRIC_SUCCESS))
        assertFalse(MobileSecurityAuth.isAvailable(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED))
    }

    @Test
    fun `drive backup uses hidden app data folder like app backups`() {
        assertEquals("appDataFolder", DriveBackupConfig.SPACE)
        assertEquals("appDataFolder", DriveBackupConfig.PARENT)
        assertEquals("goldsmith_sync_v3.enc", DriveBackupConfig.REMOTE_FILE)
        assertEquals("mdaboobackers19@gmail.com", DriveBackupConfig.SERVER_BACKUP_EMAIL)
        assertTrue(DriveBackupConfig.FILE_QUERY.contains("appDataFolder"))
        assertFalse(DriveBackupConfig.FILE_QUERY.contains("'root'"))
        assertTrue(DriveBackupConfig.isServerAccount(" MDABOOBACKERS19@gmail.com "))
        assertFalse(DriveBackupConfig.isServerAccount("shop@example.com"))
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
}
