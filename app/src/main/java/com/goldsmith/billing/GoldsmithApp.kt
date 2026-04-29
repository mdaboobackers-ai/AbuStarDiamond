package com.goldsmith.billing

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.*
import com.goldsmith.billing.data.repository.SettingsRepository
import com.goldsmith.billing.worker.DailyBackupWorker
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
                } else {
                    cancelDailyBackup()
                }
            }
        }
    }

    private fun scheduleDailyBackup() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val backupRequest = PeriodicWorkRequestBuilder<DailyBackupWorker>(
            repeatInterval = 24,
            repeatIntervalTimeUnit = TimeUnit.HOURS,
            flexTimeInterval = 1,
            flexTimeIntervalUnit = TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .addTag("daily_backup")
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "goldsmith_daily_backup",
            ExistingPeriodicWorkPolicy.KEEP,
            backupRequest
        )
    }

    private fun cancelDailyBackup() {
        WorkManager.getInstance(this).cancelUniqueWork("goldsmith_daily_backup")
    }
}
