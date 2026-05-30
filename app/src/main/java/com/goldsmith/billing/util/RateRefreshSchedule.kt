package com.goldsmith.billing.util

import java.util.Calendar
import java.util.concurrent.TimeUnit

object RateRefreshSchedule {
    data class Slot(val hour: Int, val minute: Int) {
        val workName: String = "goldsmith_rate_refresh_${hour}_${minute}"
    }

    val DAILY_SLOTS = listOf(
        Slot(10, 0),
        Slot(15, 0),
        Slot(18, 0)
    )

    fun initialDelayToNext(slot: Slot, nowMillis: Long = System.currentTimeMillis()): Long {
        val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val next = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, slot.hour)
            set(Calendar.MINUTE, slot.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        return (next.timeInMillis - nowMillis).coerceAtLeast(TimeUnit.MINUTES.toMillis(1))
    }
}
