package com.goldsmith.billing.util

import java.util.Calendar
import java.util.concurrent.TimeUnit

object BackupSchedule {
    const val DAILY_BACKUP_HOUR = 1
    const val DAILY_BACKUP_MINUTE = 0

    fun initialDelayToNextDailyBackup(nowMillis: Long = System.currentTimeMillis()): Long {
        val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val next = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, DAILY_BACKUP_HOUR)
            set(Calendar.MINUTE, DAILY_BACKUP_MINUTE)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        return (next.timeInMillis - nowMillis).coerceAtLeast(TimeUnit.MINUTES.toMillis(1))
    }
}
