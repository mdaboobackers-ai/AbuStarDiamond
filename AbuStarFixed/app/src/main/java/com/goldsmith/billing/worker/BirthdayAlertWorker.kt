package com.goldsmith.billing.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.goldsmith.billing.R
import com.goldsmith.billing.data.dao.CustomerDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.TimeUnit

@HiltWorker
class BirthdayAlertWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val customerDao: CustomerDao
) : CoroutineWorker(appContext, params) {

    companion object {
        const val CHANNEL_ID = "abu_star_alerts"
        const val WORK_NAME  = "birthday_anniversary_daily"

        fun schedule(context: Context) {
            // Calculate delay until 9:00 AM tomorrow
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
            }
            val delay = target.timeInMillis - now.timeInMillis

            val req = PeriodicWorkRequestBuilder<BirthdayAlertWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .addTag("birthday_alerts")
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        createChannel()
        val today = Calendar.getInstance()
        val todayMonth = today.get(Calendar.MONTH)
        val todayDay   = today.get(Calendar.DAY_OF_MONTH)

        val birthdayCustomers  = mutableListOf<String>()
        val anniversaryCustomers = mutableListOf<String>()

        // Check birthdays
        customerDao.getAllWithDob().forEach { c ->
            c.dateOfBirth?.let { dob ->
                val cal = Calendar.getInstance().apply { timeInMillis = dob }
                if (cal.get(Calendar.MONTH) == todayMonth && cal.get(Calendar.DAY_OF_MONTH) == todayDay) {
                    birthdayCustomers.add(c.name)
                }
            }
        }

        // Check anniversaries
        customerDao.getAllWithAnniversary().forEach { c ->
            c.anniversary?.let { ann ->
                val cal = Calendar.getInstance().apply { timeInMillis = ann }
                if (cal.get(Calendar.MONTH) == todayMonth && cal.get(Calendar.DAY_OF_MONTH) == todayDay) {
                    anniversaryCustomers.add(c.name)
                }
            }
        }

        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (birthdayCustomers.isNotEmpty()) {
            val names = if (birthdayCustomers.size <= 3)
                birthdayCustomers.joinToString(", ")
            else
                "${birthdayCustomers.take(2).joinToString(", ")} +${birthdayCustomers.size - 2} more"

            nm.notify(1001, NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_splash_logo)
                .setContentTitle("🎂 Birthday Today!")
                .setContentText(names)
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText("${birthdayCustomers.size} customer(s) have birthdays today:\n$names\n\nConsider sending a special offer!"))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build())
        }

        if (anniversaryCustomers.isNotEmpty()) {
            val names = if (anniversaryCustomers.size <= 3)
                anniversaryCustomers.joinToString(", ")
            else
                "${anniversaryCustomers.take(2).joinToString(", ")} +${anniversaryCustomers.size - 2} more"

            nm.notify(1002, NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_splash_logo)
                .setContentTitle("💍 Anniversary Today!")
                .setContentText(names)
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText("${anniversaryCustomers.size} customer(s) have anniversaries today:\n$names\n\nPerfect time to reach out!"))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build())
        }

        Result.success()
    }

    private fun createChannel() {
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Abu Star Alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Birthday and anniversary reminders for customers"
            }
        )
    }
}
