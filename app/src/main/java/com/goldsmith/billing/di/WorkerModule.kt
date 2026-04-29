package com.goldsmith.billing.di

import android.content.Context
import androidx.work.WorkerFactory
import com.goldsmith.billing.worker.DailyBackupWorker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import androidx.hilt.work.HiltWorkerFactory
import javax.inject.Singleton

// Hilt auto-wires HiltWorkerFactory — just need the @AndroidEntryPoint + @HiltAndroidApp
// WorkManager is configured in GoldsmithApp via initialize call
