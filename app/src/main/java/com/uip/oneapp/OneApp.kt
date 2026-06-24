package com.uip.oneapp

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.uip.oneapp.bootstrap.DeviceFilePermissionBootstrap
import com.uip.oneapp.di.appModule
import com.uip.oneapp.maps.OfflineMapRenderer
import com.uip.oneapp.network.HardwareMode
import com.uip.oneapp.network.OneRemoteServer
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

        val koin = startKoin {
            androidLogger(Level.DEBUG)
            androidContext(this@OneApp)
            modules(appModule)
        }.koin

        // Dual-Modus W3b: Im DIRECT-Modus (App läuft auf der ONE-Hardware) wird die ONE selbst
        // zum Server für ein WiFi-Tablet (Telemetrie/Steuerung über :12345 + Discovery :8555).
        // Gate = der HardwareMode-Single (abgeleitet aus dem aufgelösten HardwareService, spiegelt
        // die volle Detektor-Entscheidung inkl. `one_transport`-Override). Im WiFi-/Tablet-Modus
        // wird nichts gestartet. Stop = Prozessende: das Feldgerät läuft die App dauerhaft (Kiosk);
        // ein zuverlässiger Application-Teardown-Hook existiert nicht. Echter Socket-Round-Trip
        // Tablet↔ONE = Geräte-Test (Welle 5).
        if (koin.get<HardwareMode>() == HardwareMode.DIRECT) {
            koin.get<OneRemoteServer>().start()
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
