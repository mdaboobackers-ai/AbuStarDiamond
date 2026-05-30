package com.goldsmith.billing

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.*
import com.goldsmith.billing.data.repository.SettingsRepository
import com.goldsmith.billing.util.BackupSchedule
import com.goldsmith.billing.util.LocalBackupStore
import com.goldsmith.billing.util.RateRefreshSchedule
import com.goldsmith.billing.worker.BirthdayAlertWorker
import com.goldsmith.billing.worker.DailyBackupWorker
import com.goldsmith.billing.worker.GoldRateRefreshWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class GoldsmithApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    
    @Inject
    lateinit var settingsRepo: SettingsRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        BirthdayAlertWorker.schedule(this)
        scheduleGoldRateRefreshes()
        
        MainScope().launch {
            settingsRepo.settingsFlow.collect { settings ->
                // Apply language
                val locale = java.util.Locale(settings.language)
                java.util.Locale.setDefault(locale)
                
                // Apply Theme
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                    if (settings.isDarkTheme) androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                    else androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                )

                if (settings.autoBackupEnabled) {
                    scheduleDailyBackup()
                    scheduleMissedBackupIfNeeded()
                } else {
                    cancelDailyBackup()
                }
            }
        }
    }

    private fun scheduleDailyBackup() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        val backupRequest = PeriodicWorkRequestBuilder<DailyBackupWorker>(
            repeatInterval = 24,
            repeatIntervalTimeUnit = TimeUnit.HOURS,
            flexTimeInterval = 1,
            flexTimeIntervalUnit = TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setInitialDelay(BackupSchedule.initialDelayToNextDailyBackup(), TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .addTag("daily_backup")
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "goldsmith_daily_backup",
            ExistingPeriodicWorkPolicy.UPDATE,
            backupRequest
        )
    }

    private fun scheduleMissedBackupIfNeeded() {
        if (!BackupSchedule.shouldRunCatchUp(LocalBackupStore.latestBackupFile(this)?.lastModified())) return

        val catchUpRequest = OneTimeWorkRequestBuilder<DailyBackupWorker>()
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .addTag("daily_backup_catchup")
            .build()

        WorkManager.getInstance(this).enqueueUniqueWork(
            "abustar_daily_backup_catchup",
            ExistingWorkPolicy.KEEP,
            catchUpRequest
        )
    }

    private fun cancelDailyBackup() {
        WorkManager.getInstance(this).cancelUniqueWork("goldsmith_daily_backup")
    }

    private fun scheduleGoldRateRefreshes() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        RateRefreshSchedule.DAILY_SLOTS.forEach { slot ->
            val request = PeriodicWorkRequestBuilder<GoldRateRefreshWorker>(
                repeatInterval = 24,
                repeatIntervalTimeUnit = TimeUnit.HOURS,
                flexTimeInterval = 15,
                flexTimeIntervalUnit = TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setInitialDelay(RateRefreshSchedule.initialDelayToNext(slot), TimeUnit.MILLISECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .addTag("gold_rate_refresh")
                .build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                slot.workName,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
