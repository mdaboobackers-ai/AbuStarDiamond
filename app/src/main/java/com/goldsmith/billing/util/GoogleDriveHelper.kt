package com.goldsmith.billing.util

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
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
    const val REMOTE_FILE = "goldsmith_sync_v3.enc"
    const val LEGACY_REMOTE_FILE = "goldsmith_sync_v2.json"
    const val SPACE = "appDataFolder"
    const val PARENT = "appDataFolder"
    const val SCOPE = DriveScopes.DRIVE_APPDATA
    const val SERVER_BACKUP_EMAIL = "mdaboobackers19@gmail.com"
    const val FILE_QUERY = "name = '$REMOTE_FILE' and '$PARENT' in parents and trashed = false"

    fun normalizeEmail(email: String?): String = email?.trim()?.lowercase().orEmpty()
    fun isServerAccount(email: String?): Boolean = normalizeEmail(email) == SERVER_BACKUP_EMAIL
}

class GoogleDriveHelper(private val context: Context) {

    fun currentAccountEmail(): String =
        GoogleSignIn.getLastSignedInAccount(context)?.email.orEmpty()

    private fun driveService(): Drive? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        val credential = GoogleAccountCredential.usingOAuth2(context, Collections.singleton(DriveBackupConfig.SCOPE))
        credential.selectedAccount = account.account
        
        return Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("Abu Star Diamonds")
            .build()
    }

    suspend fun uploadFile(localFile: java.io.File, remoteName: String): String? = withContext(Dispatchers.IO) {
        val service = driveService() ?: return@withContext null
        
        val existingFile = findFile(remoteName)
        
        val metadata = File().apply {
            name = remoteName
            parents = listOf(DriveBackupConfig.PARENT)
        }
        
        val content = FileContent("application/octet-stream", localFile)
        
        return@withContext if (existingFile != null) {
            service.files().update(existingFile.id, null, content).execute().id
        } else {
            service.files().create(metadata, content).execute().id
        }
    }

    suspend fun downloadFile(remoteName: String, targetFile: java.io.File): Boolean = withContext(Dispatchers.IO) {
        val service = driveService() ?: return@withContext false
        val file = findFile(remoteName) ?: return@withContext false
        
        FileOutputStream(targetFile).use { outputStream ->
            service.files().get(file.id).executeMediaAndDownloadTo(outputStream)
        }
        true
    }

    private fun findFile(name: String): File? {
        val service = driveService() ?: return null
        val escapedName = name.replace("'", "\\'")
        val result = service.files().list()
            .setQ("name = '$escapedName' and '${DriveBackupConfig.PARENT}' in parents and trashed = false")
            .setSpaces(DriveBackupConfig.SPACE)
            .setOrderBy("modifiedTime desc")
            .execute()
        return result.files.firstOrNull()
    }
}
