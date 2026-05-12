package com.uip.oneapp.update

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.uip.oneapp.MainActivity
import com.uip.oneapp.R
import com.uip.oneapp.ui.localization.LocalizationManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

class UpdateWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams), KoinComponent {

    private val updateService: UpdateService by inject()

    override suspend fun doWork(): Result {
        val result = try {
            updateService.checkForUpdate()
        } catch (_: Exception) {
            return Result.success()
        }

        if (result is UpdateCheckResult.Available) {
            postNotification(result.release)
        }

        return Result.success()
    }

    private fun postNotification(release: ReleaseInfo) {
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val launchIntent = appContext.packageManager
            .getLaunchIntentForPackage(appContext.packageName)
            ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
            ?: Intent(appContext, MainActivity::class.java)

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(appContext, 0, launchIntent, flags)

        val title = LocalizationManager.getString("update_notification_title")
        val body = LocalizationManager.getString("update_notification_body")
            .replace("{version}", release.version)

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        nm.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "updates"
        private const val WORK_NAME = "update_daily_check"
        private const val NOTIFICATION_ID = 1001

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .build()

            val request = PeriodicWorkRequestBuilder<UpdateWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
