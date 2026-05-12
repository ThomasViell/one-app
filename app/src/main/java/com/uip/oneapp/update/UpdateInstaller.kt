package com.uip.oneapp.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File

class UpdateInstaller(private val context: Context) {

    companion object {
        const val AUTHORITY = "com.uip.drainq.one.fileprovider"
        const val ACTION_INSTALL_STATUS = "com.uip.drainq.one.UPDATE_INSTALL_STATUS"
    }

    fun install(apkFile: File) {
        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)

        val sessionId = packageInstaller.createSession(params)
        val session = packageInstaller.openSession(sessionId)

        try {
            session.openWrite("drainq-one.apk", 0, apkFile.length()).use { out ->
                apkFile.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }

            val intent = Intent(ACTION_INSTALL_STATUS).apply {
                setPackage(context.packageName)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pendingIntent.intentSender)
        } catch (e: Exception) {
            session.abandon()
            throw e
        }
    }

    fun apkUri(apkFile: File) = FileProvider.getUriForFile(context, AUTHORITY, apkFile)

    fun cacheDir(): File {
        val dir = File(context.cacheDir, "updates")
        dir.mkdirs()
        return dir
    }
}
