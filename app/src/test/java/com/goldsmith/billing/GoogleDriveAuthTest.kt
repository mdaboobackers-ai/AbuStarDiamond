package com.goldsmith.billing

import com.goldsmith.billing.util.GoogleDriveAuth
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleDriveAuthTest {

    @Test
    fun `developer error message explains package and sha setup`() {
        val message = GoogleDriveAuth.signInFailureMessage(
            statusCode = 10,
            statusMessage = "DEVELOPER_ERROR",
            applicationId = "com.goldsmith.billing"
        )

        assertTrue(message.contains("Google sign-in is not configured"))
        assertTrue(message.contains("com.goldsmith.billing"))
        assertTrue(message.contains("SHA-1"))
    }

    @Test
    fun `developer error message warns when debug apk package is used`() {
        val message = GoogleDriveAuth.signInFailureMessage(
            statusCode = 10,
            statusMessage = "DEVELOPER_ERROR",
            applicationId = "com.goldsmith.billing.debug"
        )

        assertTrue(message.contains("debug APK"))
        assertTrue(message.contains("upgrade APK"))
    }

    @Test
    fun `cancelled message asks user to choose account again`() {
        val message = GoogleDriveAuth.signInFailureMessage(
            statusCode = 12501,
            statusMessage = "SIGN_IN_CANCELLED",
            applicationId = "com.goldsmith.billing"
        )

        assertTrue(message.contains("No Google account was selected"))
    }
}
