package com.goldsmith.billing.util

import java.util.Calendar
import java.util.concurrent.TimeUnit

object BackupSchedule {
    // FIX: Changed from 1:00 AM to 1:15 AM as required
    const val DAILY_BACKUP_HOUR   = 1
    const val DAILY_BACKUP_MINUTE = 15

    fun initialDelayToNextDailyBackup(nowMillis: Long = System.currentTimeMillis()): Long {
        val now  = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val next = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, DAILY_BACKUP_HOUR)
            set(Calendar.MINUTE,      DAILY_BACKUP_MINUTE)
            set(Calendar.SECOND,      0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        return (next.timeInMillis - nowMillis).coerceAtLeast(TimeUnit.MINUTES.toMillis(1))
    }

    fun shouldRunCatchUp(lastBackupMillis: Long?, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val now        = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val todayTarget = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, DAILY_BACKUP_HOUR)
            set(Calendar.MINUTE,      DAILY_BACKUP_MINUTE)
            set(Calendar.SECOND,      0)
            set(Calendar.MILLISECOND, 0)
        }
        if (now.before(todayTarget)) return false
        return lastBackupMillis == null || lastBackupMillis < todayTarget.timeInMillis
    }
}
