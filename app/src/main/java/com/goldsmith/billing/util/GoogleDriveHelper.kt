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

class GoogleDriveHelper(private val context: Context) {

    private val driveService: Drive? by lazy {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return@lazy null
        val credential = GoogleAccountCredential.usingOAuth2(context, Collections.singleton(DriveScopes.DRIVE_FILE))
        credential.selectedAccount = account.account
        
        Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("Goldsmith Billing")
            .build()
    }

    suspend fun uploadFile(localFile: java.io.File, remoteName: String): String? = withContext(Dispatchers.IO) {
        val service = driveService ?: return@withContext null
        
        val existingFile = findFile(remoteName)
        
        val metadata = File().apply {
            name = remoteName
            parents = listOf("root")
        }
        
        val content = FileContent("application/octet-stream", localFile)
        
        return@withContext if (existingFile != null) {
            service.files().update(existingFile.id, null, content).execute().id
        } else {
            service.files().create(metadata, content).execute().id
        }
    }

    suspend fun downloadFile(remoteName: String, targetFile: java.io.File): Boolean = withContext(Dispatchers.IO) {
        val service = driveService ?: return@withContext false
        val file = findFile(remoteName) ?: return@withContext false
        
        FileOutputStream(targetFile).use { outputStream ->
            service.files().get(file.id).executeMediaAndDownloadTo(outputStream)
        }
        true
    }

    private fun findFile(name: String): File? {
        val service = driveService ?: return null
        val result = service.files().list()
            .setQ("name = '$name' and trashed = false")
            .setSpaces("drive")
            .execute()
        return result.files.firstOrNull()
    }
}
