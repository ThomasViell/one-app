package com.uip.oneapp

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.uip.oneapp.bootstrap.AndroidDevicePolicyGateway
import com.uip.oneapp.bootstrap.DeviceFilePermissionBootstrap
import com.uip.oneapp.bootstrap.DeviceOwnerLocationProvisioner
import com.uip.oneapp.di.appModule
import com.uip.oneapp.maps.OfflineMapRenderer
import com.uip.oneapp.network.HardwareMode
import com.uip.oneapp.network.OneRemoteServer
import com.uip.oneapp.network.video.OneVideoServer
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

        // Dual-Modus W3a: Standort-Auto-Grant auf der Kiosk-ONE. NUR wenn die App
        // Geräteeigentümer ist, gewährt sie sich selbst ACCESS_FINE_LOCATION und aktiviert die
        // Standortdienste — damit kommt der LOHS-Rückfall des Tablet-Hotspots OHNE adb/UI hoch und
        // der GPS-Knopf funktioniert auf dem Kiosk. Kein Device-Owner → no-op (normales Verhalten).
        val loc = DeviceOwnerLocationProvisioner.provision(AndroidDevicePolicyGateway(this))
        if (loc.deviceOwner) {
            Log.i("OneApp", "Standort-Auto-Grant (Device-Owner): granted=${loc.locationGranted}, enabled=${loc.locationEnabled}")
        }

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
            // Welle 3c: zusätzlich den RTSP/H.264-Video-Server starten (Live-Feed fürs Tablet).
            // Reiner Konsument des V4L2-Fan-outs (CameraFrameBus); öffnet /dev/video0 nicht selbst.
            koin.get<OneVideoServer>().start()
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
