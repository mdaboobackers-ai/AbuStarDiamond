package com.goldsmith.billing.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.goldsmith.billing.data.dao.GoldRateDao
import com.goldsmith.billing.data.model.GoldRate
import com.goldsmith.billing.data.remote.GoldRateService
import com.goldsmith.billing.data.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class GoldRateRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val goldRateService: GoldRateService,
    private val settingsRepo: SettingsRepository,
    private val goldRateDao: GoldRateDao
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val latestRates = goldRateService.fetchLatestGoldRates()
                ?: return@withContext if (runAttemptCount < 3) Result.retry() else Result.failure()
            val rate24K = latestRates.rate24K
            if (rate24K !in 10_000.0..25_000.0) {
                return@withContext if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
            settingsRepo.updateGoldRatesManual(
                rate24K = rate24K,
                rate22K = latestRates.rate22K ?: rate24K * 0.916,
                rate20K = latestRates.rate20K ?: rate24K * (20.0 / 24.0),
                rate18K = latestRates.rate18K ?: rate24K * 0.75
            )
            goldRateDao.insertRate(GoldRate(rate24K = rate24K, isManualOverride = false))
            Result.success()
        } catch (_: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
