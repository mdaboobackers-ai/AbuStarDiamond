package com.goldsmith.billing.util

import android.content.Intent
import com.goldsmith.billing.BuildConfig
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope

object GoogleDriveAuth {
    val driveScope: Scope
        get() = Scope(DriveBackupConfig.SCOPE)

    fun accountSignInOptions(): GoogleSignInOptions =
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()

    fun driveSignInOptions(): GoogleSignInOptions =
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(driveScope)
            .build()

    fun hasDrivePermission(account: GoogleSignInAccount?): Boolean =
        account != null && GoogleSignIn.hasPermissions(account, driveScope)

    fun accountEmailMatches(account: GoogleSignInAccount?, selectedEmail: String): Boolean {
        val normalizedSelected = DriveBackupConfig.normalizeEmail(selectedEmail)
        if (normalizedSelected.isBlank()) return account?.email?.isNotBlank() == true
        return DriveBackupConfig.normalizeEmail(account?.email) == normalizedSelected
    }

    fun accountFromIntent(data: Intent?): GoogleSignInAccount? =
        runCatching {
            GoogleSignIn.getSignedInAccountFromIntent(data)
                .getResult(ApiException::class.java)
        }.getOrNull()

    fun failureMessageFromIntent(
        data: Intent?,
        applicationId: String = BuildConfig.APPLICATION_ID
    ): String {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        return try {
            task.getResult(ApiException::class.java)
            "Google account was not selected. Please try again."
        } catch (error: ApiException) {
            signInFailureMessage(
                statusCode = error.statusCode,
                statusMessage = GoogleSignInStatusCodes.getStatusCodeString(error.statusCode),
                applicationId = applicationId
            )
        } catch (_: Exception) {
            "Google account was not selected. Please check Google Play Services and try again."
        }
    }

    fun signInFailureMessage(
        statusCode: Int?,
        statusMessage: String?,
        applicationId: String = BuildConfig.APPLICATION_ID
    ): String =
        when (statusCode) {
            10 -> if (applicationId.endsWith(".debug")) {
                "Google sign-in is not configured for the debug APK package $applicationId. Install the latest upgrade APK, or add this package name and SHA-1 fingerprint in Google Cloud Console > Credentials > Android OAuth client."
            } else {
                "Google sign-in is not configured for $applicationId. Add this package name and SHA-1 fingerprint in Google Cloud Console > Credentials > Android OAuth client, then try again."
            }
            GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> "No Google account was selected. Please choose the account used for backup."
            GoogleSignInStatusCodes.SIGN_IN_FAILED -> "Google sign-in failed. Update Google Play Services and try again."
            GoogleSignInStatusCodes.SIGN_IN_CURRENTLY_IN_PROGRESS -> "Google sign-in is already open. Finish that prompt and try again."
            else -> {
                val suffix = statusMessage?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
                "Google account could not be selected$suffix. Please check Google Play Services and try again."
            }
        }
}
