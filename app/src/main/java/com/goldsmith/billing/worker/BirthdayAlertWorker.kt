package com.goldsmith.billing.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.goldsmith.billing.R
import com.goldsmith.billing.data.dao.CustomerDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit

@HiltWorker
class BirthdayAlertWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val customerDao: CustomerDao
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val CHANNEL_ID = "abu_star_customer_alerts"
        private const val WORK_NAME = "abu_star_birthday_anniversary_alerts"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<BirthdayAlertWorker>(1, TimeUnit.DAYS)
                .addTag(WORK_NAME)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        createChannel()
        val customers = customerDao.getAllCustomers().first()
        val birthdays = customers.filter { isToday(it.dob) }.map { it.name }
        val anniversaries = customers.filter { isToday(it.anniversary) }.map { it.name }
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (birthdays.isNotEmpty()) {
            manager.notify(2101, buildNotification("Birthday today", birthdays))
        }
        if (anniversaries.isNotEmpty()) {
            manager.notify(2102, buildNotification("Anniversary today", anniversaries))
        }
        Result.success()
    }

    private fun isToday(date: Date?): Boolean {
        if (date == null) return false
        val today = Calendar.getInstance()
        val candidate = Calendar.getInstance().apply { time = date }
        return today.get(Calendar.MONTH) == candidate.get(Calendar.MONTH) &&
            today.get(Calendar.DAY_OF_MONTH) == candidate.get(Calendar.DAY_OF_MONTH)
    }

    private fun buildNotification(title: String, names: List<String>) =
        NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_splash_logo)
            .setContentTitle(title)
            .setContentText(names.take(3).joinToString(", ") + if (names.size > 3) " +${names.size - 3}" else "")
            .setStyle(NotificationCompat.BigTextStyle().bigText(names.joinToString("\n")))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

    private fun createChannel() {
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Customer Alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Birthday and anniversary reminders"
            }
        )
    }
}
