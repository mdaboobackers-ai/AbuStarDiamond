package com.goldsmith.billing.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.goldsmith.billing.data.repository.SettingsRepository
import com.goldsmith.billing.security.KeystoreManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@HiltWorker
class DailyBackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val settingsRepo: SettingsRepository,
    private val keystoreManager: KeystoreManager
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Get database file
            val dbFile = applicationContext.getDatabasePath("goldsmith_vault.db")

            if (!dbFile.exists()) return@withContext Result.failure()

            // Read and encrypt DB bytes
            val dbBytes = dbFile.readBytes()
            val encrypted = keystoreManager.encryptBytes(dbBytes)

            // Write encrypted file to cache dir for Drive upload
            val backupDir = File(applicationContext.cacheDir, "backups").also { it.mkdirs() }
            val backupFile = File(backupDir, "goldsmith_backup_${System.currentTimeMillis()}.enc")
            backupFile.writeBytes(encrypted)

            // TODO: Upload to Google Drive using Drive API
            // For now, update the last backup timestamp
            settingsRepo.updateLastBackupTime(System.currentTimeMillis())

            // Clean up old backup files (keep last 3)
            backupDir.listFiles()
                ?.sortedByDescending { it.lastModified() }
                ?.drop(3)
                ?.forEach { it.delete() }

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
