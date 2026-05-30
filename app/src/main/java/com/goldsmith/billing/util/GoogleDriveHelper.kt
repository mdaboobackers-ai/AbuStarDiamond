package com.goldsmith.billing.util

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.util.Collections

object DriveBackupConfig {
    const val REMOTE_FILE       = "goldsmith_sync_v3.enc"
    const val LEGACY_REMOTE_FILE = "goldsmith_sync_v2.json"
    // DRIVE_FILE avoids restricted full-Drive consent and still lets the app
    // create/reuse its ASD backup file after the user selects an account.
    const val SCOPE             = DriveScopes.DRIVE_FILE
    // ASD folder name as required by the client
    const val ASD_FOLDER_NAME   = "ASD"

    fun normalizeEmail(email: String?): String = email?.trim()?.lowercase().orEmpty()
    fun resolveActiveAccountEmail(pickerEmail: String?, lastSignedInEmail: String?): String =
        normalizeEmail(pickerEmail).ifBlank { normalizeEmail(lastSignedInEmail) }
    fun hasActiveDriveAccount(activeEmail: String?, savedEmail: String?): Boolean =
        normalizeEmail(activeEmail).isNotBlank()
    fun displayEmail(activeEmail: String?, savedEmail: String?): String =
        normalizeEmail(activeEmail).ifBlank { normalizeEmail(savedEmail) }
}

class DriveBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

class GoogleDriveHelper(
    private val context: Context,
    private val signedInAccount: GoogleSignInAccount? = null
) {

    fun currentAccountEmail(): String =
        (signedInAccount ?: GoogleSignIn.getLastSignedInAccount(context))?.email.orEmpty()

    private fun driveService(): Drive {
        val account = signedInAccount ?: GoogleSignIn.getLastSignedInAccount(context)
            ?: throw DriveBackupException("Select a Google account to continue backup.")
        val email = DriveBackupConfig.normalizeEmail(account.email)
        if (email.isBlank()) {
            throw DriveBackupException("Selected Google account has no email. Please choose the account again.")
        }
        if (!GoogleSignIn.hasPermissions(account, Scope(DriveBackupConfig.SCOPE))) {
            throw DriveBackupException("Google Drive permission is missing. Please choose the account again and allow Drive backup access.")
        }
        val credential = GoogleAccountCredential.usingOAuth2(
            context, Collections.singleton(DriveBackupConfig.SCOPE)
        )
        credential.selectedAccountName = email

        return Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("Abu Star Diamonds")
            .build()
    }

    /**
     * FIX: Find or create the "ASD" folder in Drive root.
     * Returns the folder id.
     */
    private fun getOrCreateAsdFolder(service: Drive): String {
        val query = "name = '${DriveBackupConfig.ASD_FOLDER_NAME}' " +
                "and mimeType = 'application/vnd.google-apps.folder' " +
                "and trashed = false"
        val result = service.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id, name)")
            .execute()
        val existing = result.files.firstOrNull()
        if (existing != null) return existing.id

        // Create the ASD folder
        val folderMeta = File().apply {
            name = DriveBackupConfig.ASD_FOLDER_NAME
            mimeType = "application/vnd.google-apps.folder"
        }
        val created = service.files().create(folderMeta)
            .setFields("id")
            .execute()
        return created.id
    }

    suspend fun uploadFile(localFile: java.io.File, remoteName: String): String? = withContext(Dispatchers.IO) {
        val service = driveService()
        val folderId = getOrCreateAsdFolder(service)

        val existingFile = findFileInFolder(service, folderId, remoteName)
        val content = FileContent("application/octet-stream", localFile)

        return@withContext if (existingFile != null) {
            // Update existing file — no parent needed for update
            service.files().update(existingFile.id, File().setName(remoteName), content)
                .setFields("id, modifiedTime")
                .execute()
                .id
        } else {
            val metadata = File().apply {
                name = remoteName
                parents = listOf(folderId)
            }
            service.files().create(metadata, content)
                .setFields("id, modifiedTime")
                .execute()
                .id
        }
    }

    suspend fun downloadFile(remoteName: String, targetFile: java.io.File): Boolean = withContext(Dispatchers.IO) {
        val service = driveService()
        val folderId = getOrCreateAsdFolder(service)
        val file = findFileInFolder(service, folderId, remoteName) ?: return@withContext false

        FileOutputStream(targetFile).use { outputStream ->
            service.files().get(file.id).executeMediaAndDownloadTo(outputStream)
        }
        true
    }

    private fun findFileInFolder(service: Drive, folderId: String, name: String): File? {
        val escapedName = name.replace("'", "\\'")
        val result = service.files().list()
            .setQ("name = '$escapedName' and '$folderId' in parents and trashed = false")
            .setSpaces("drive")
            .setOrderBy("modifiedTime desc")
            .setFields("files(id, name, modifiedTime)")
            .execute()
        return result.files.firstOrNull()
    }

    suspend fun latestBackupModifiedTime(): Long? = withContext(Dispatchers.IO) {
        val service = driveService()
        val folderId = getOrCreateAsdFolder(service)
        findFileInFolder(service, folderId, DriveBackupConfig.REMOTE_FILE)?.modifiedTime?.value
            ?: findFileInFolder(service, folderId, DriveBackupConfig.LEGACY_REMOTE_FILE)?.modifiedTime?.value
    }
}
