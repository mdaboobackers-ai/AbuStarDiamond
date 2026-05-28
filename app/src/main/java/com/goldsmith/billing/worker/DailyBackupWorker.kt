package com.goldsmith.billing.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.goldsmith.billing.data.repository.SettingsRepository
import com.goldsmith.billing.util.BackupFileConfig
import com.goldsmith.billing.util.DataSyncManager
import com.goldsmith.billing.util.LocalBackupStore
import com.google.android.gms.auth.api.signin.GoogleSignIn
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@HiltWorker
class DailyBackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val settingsRepo: SettingsRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val settings = settingsRepo.settingsFlow.first()
            if (!settings.autoBackupEnabled) {
                return@withContext Result.success()
            }

            // Step 1: Always create a local backup file first
            val syncManager = DataSyncManager(applicationContext)
            val fileName = BackupFileConfig.dailyAutoFileName()
            val localFile = syncManager.createLocalBackupFile(fileName)

            if (localFile == null) {
                // Local backup failed — retry up to 3 times
                return@withContext if (runAttemptCount < 3) Result.retry() else Result.failure()
            }

            // Step 2: Try to upload to Google Drive (ASD folder) if account is signed in
            val account = GoogleSignIn.getLastSignedInAccount(applicationContext)
            if (account != null) {
                try {
                    val driveSync = DataSyncManager(applicationContext, account)
                    driveSync.uploadLocalBackupToDrive(localFile)
                    // Drive upload failure is non-fatal — local backup already succeeded
                } catch (_: Exception) {
                    // Do not fail the worker if Drive upload fails; local backup is already saved
                }
            }

            settingsRepo.updateLastBackupTime(System.currentTimeMillis())
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
