package com.uip.oneapp

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.uip.oneapp.bootstrap.DeviceFilePermissionBootstrap
import com.uip.oneapp.di.appModule
import com.uip.oneapp.maps.OfflineMapRenderer
import com.uip.oneapp.ui.localization.LocalizationManager
import com.uip.oneapp.update.UpdateWorker
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class OneApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Phase P7 — Pilot-Variante 7.3: chmod 666 auf /dev/ttyS5 + /dev/video0
        // via su-Befehl, BEVOR Koin/DI den OneInternalHardwareService instanziiert.
        // Auf Nicht-ONE-Tablets (TWO-Modus) leise no-op.
        DeviceFilePermissionBootstrap.grantIfNeeded()

        LocalizationManager.init(this)

        // MapsForge graphics factory — must run exactly once per process before
        // any .map file is read or rendered. Idempotent inside ensureInitialised().
        OfflineMapRenderer.ensureInitialised(this)

        startKoin {
            androidLogger(Level.DEBUG)
            androidContext(this@OneApp)
            modules(appModule)
        }

        createUpdateNotificationChannel()
        UpdateWorker.schedule(this)
    }

    private fun createUpdateNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                UpdateWorker.CHANNEL_ID,
                LocalizationManager.getString("update_section_title"),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }
}
