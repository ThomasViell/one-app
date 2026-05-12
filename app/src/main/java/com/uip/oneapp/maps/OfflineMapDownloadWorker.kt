package com.uip.oneapp.maps

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.uip.oneapp.MainActivity
import com.uip.oneapp.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "MapDownloadWorker"
private const val CHANNEL_ID = "offline_map_download"
private const val NOTIF_ID_BASE = 4711

/**
 * Downloads a single MapsForge .map file in the background.
 * Streams to a .part file and renames on success so an aborted run can't
 * leave a corrupt .map that the manager would happily list.
 *
 * Progress is published two ways:
 *  - WorkManager setProgress (UI reads via WorkInfo)
 *  - Foreground notification (visible while the app is closed)
 */
class OfflineMapDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val entryId    = inputData.getString(KEY_ENTRY_ID) ?: return@withContext Result.failure()
        val urlStr     = inputData.getString(KEY_URL)      ?: return@withContext Result.failure()
        val destPath   = inputData.getString(KEY_DEST)     ?: return@withContext Result.failure()
        val displayName= inputData.getString(KEY_DISPLAY)  ?: entryId

        val dest = File(destPath)
        val part = File("${destPath}.part")
        dest.parentFile?.mkdirs()
        if (part.exists()) part.delete()

        setForeground(makeForegroundInfo(displayName, 0, indeterminate = true))

        try {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "DrainQ ONE/1.0 (offline-map-downloader)")
            }
            val rc = conn.responseCode
            if (rc !in 200..299) {
                Log.e(TAG, "HTTP $rc for $urlStr")
                conn.disconnect()
                return@withContext Result.failure(workDataOf(KEY_ERROR to "HTTP $rc"))
            }
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: -1L

            conn.inputStream.use { input ->
                FileOutputStream(part).use { output ->
                    val buf = ByteArray(64 * 1024)
                    var read: Int
                    var transferred = 0L
                    var lastProgress = -1
                    while (input.read(buf).also { read = it } > 0) {
                        if (isStopped) {
                            output.flush()
                            return@withContext Result.failure(workDataOf(KEY_ERROR to "cancelled"))
                        }
                        output.write(buf, 0, read)
                        transferred += read
                        if (total > 0) {
                            val pct = ((transferred * 100) / total).toInt()
                            if (pct != lastProgress) {
                                lastProgress = pct
                                setProgress(workDataOf(KEY_PROGRESS to pct, KEY_BYTES to transferred, KEY_TOTAL to total))
                                setForeground(makeForegroundInfo(displayName, pct, indeterminate = false))
                            }
                        }
                    }
                }
            }
            conn.disconnect()

            if (!part.renameTo(dest)) {
                Log.e(TAG, "Could not rename ${part.name} -> ${dest.name}")
                return@withContext Result.failure(workDataOf(KEY_ERROR to "rename failed"))
            }
            setProgress(workDataOf(KEY_PROGRESS to 100))
            Log.i(TAG, "Downloaded ${dest.name} (${dest.length() / 1024} KB)")
            Result.success(workDataOf(KEY_ENTRY_ID to entryId))
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e.message}", e)
            if (part.exists()) part.delete()
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: "unknown")))
        }
    }

    private fun makeForegroundInfo(displayName: String, percent: Int, indeterminate: Boolean): ForegroundInfo {
        val ctx = applicationContext
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, "Offline-Karten-Download", NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Lädt Kartenmaterial im Hintergrund herunter" }
            )
        }
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getActivity(ctx, 0, intent, flags)

        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setContentTitle("Karte: $displayName")
            .setContentText(if (indeterminate) "Verbindung…" else "$percent %")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent, indeterminate)
            .setContentIntent(pi)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(NOTIF_ID_BASE, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIF_ID_BASE, notif)
        }
    }

    companion object {
        const val KEY_ENTRY_ID = "entryId"
        const val KEY_URL      = "url"
        const val KEY_DEST     = "dest"
        const val KEY_DISPLAY  = "displayName"
        const val KEY_PROGRESS = "progress"
        const val KEY_BYTES    = "bytes"
        const val KEY_TOTAL    = "total"
        const val KEY_ERROR    = "error"

        const val WORK_TAG_PREFIX = "offline_map_dl_"

        fun enqueue(ctx: Context, entry: OfflineMapCatalog.Entry, destFile: File) {
            val req = OneTimeWorkRequestBuilder<OfflineMapDownloadWorker>()
                .addTag(WORK_TAG_PREFIX + entry.id)
                .setInputData(
                    Data.Builder()
                        .putString(KEY_ENTRY_ID, entry.id)
                        .putString(KEY_URL, OfflineMapCatalog.url(entry))
                        .putString(KEY_DEST, destFile.absolutePath)
                        .putString(KEY_DISPLAY, entry.displayName)
                        .build()
                )
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                WORK_TAG_PREFIX + entry.id,
                androidx.work.ExistingWorkPolicy.KEEP,
                req
            )
        }

        fun cancel(ctx: Context, entry: OfflineMapCatalog.Entry) {
            WorkManager.getInstance(ctx).cancelUniqueWork(WORK_TAG_PREFIX + entry.id)
        }
    }
}
