package com.uip.oneapp.di

import android.app.Application
import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.uip.oneapp.data.local.AppDatabase
import com.uip.oneapp.data.repository.DamageRepository
import com.uip.oneapp.data.repository.NoteRepository
import com.uip.oneapp.data.repository.ProjectRepository
import com.uip.oneapp.data.repository.DamagePresetRepository
import com.uip.oneapp.data.repository.UpdateEventRepository
import com.uip.oneapp.data.repository.WeatherPresetRepository
import com.uip.oneapp.export.ProjectExportService
import com.uip.oneapp.maps.OfflineMapManager
import com.uip.oneapp.maps.OfflineMapRenderer
import com.uip.oneapp.network.AccessPointController
import com.uip.oneapp.network.AndroidLohsStarter
import com.uip.oneapp.network.AndroidSoftApStarter
import com.uip.oneapp.network.CameraEncoderArbiter
import com.uip.oneapp.network.FallbackHotspotStarter
import com.uip.oneapp.network.HardwareMode
import com.uip.oneapp.network.HardwareModeDetector
import com.uip.oneapp.network.HardwareService
import com.uip.oneapp.network.OneHardwareConfig
import com.uip.oneapp.network.OneHardwareService
import com.uip.oneapp.network.OneRemoteServer
import com.uip.oneapp.network.LocationService
import com.uip.oneapp.network.NetworkDiscoveryService
import com.uip.oneapp.network.NominatimService
import com.uip.oneapp.network.internal.Camera2FrameSource
import com.uip.oneapp.network.internal.CameraFrameBus
import com.uip.oneapp.network.internal.OneInternalHardwareService
import com.uip.oneapp.network.video.OneVideoServer
import com.uip.oneapp.network.OsmStaticMapService
import com.uip.oneapp.network.RtspStreamTester
import com.uip.oneapp.network.WeatherApiService
import com.uip.oneapp.cloud.CloudAccountStore
import com.uip.oneapp.network.AndroidEncryptedStorage
import com.uip.oneapp.network.ConnectivityMonitor
import com.uip.oneapp.network.KnownOneStore
import com.uip.oneapp.network.OneAutoConnector
import com.uip.oneapp.network.WifiController
import com.uip.oneapp.ui.screens.connection.ConnectionViewModel
import com.uip.oneapp.ui.screens.network.NetworkViewModel
import com.uip.oneapp.ui.screens.pairing.PairingViewModel
import com.uip.oneapp.ui.screens.projectdetail.ProjectDetailViewModel
import com.uip.oneapp.ui.screens.projects.ProjectFormViewModel
import com.uip.oneapp.ui.screens.projects.ProjectsViewModel
import com.uip.oneapp.ui.screens.settings.SettingsViewModel
import com.uip.oneapp.ui.screens.settings.settingsStore
import com.uip.oneapp.update.HttpUpdateService
import com.uip.oneapp.update.UpdateConfig
import com.uip.oneapp.update.UpdateInstaller
import com.uip.oneapp.update.UpdateService
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // Injizierbare Systemuhr: Vorgabe ist die echte Systemuhr (java.time.Clock.systemDefaultZone()),
    // damit LocalDate.now(clock) im Betrieb identisch zu LocalDate.now() bleibt. Screenshot-Tests
    // (ScreenshotTestModule) überschreiben diesen Single mit einem festen Clock — kein Sonderpfad
    // in der App selbst, nur eine andere Koin-Modulwahl im Test.
    single<java.time.Clock> { java.time.Clock.systemDefaultZone() }
    single { NetworkDiscoveryService(androidContext()) }
    single { RtspStreamTester() }

    // Dual-Modus W3d-Video: Kamera-Frame-Fan-out — EINE geteilte Quelle für lokale Anzeige
    // (OneInternalHardwareService) UND RTSP-Encoder (OneVideoServer).
    // Lazy: wird nur im DIRECT-Modus aufgelöst (von der internen HardwareService-Impl bzw. dem
    // Video-Server) — im WiFi-/Tablet-Modus nie konstruiert.
    //
    // Camera2-Umbau 2026-07-29 (CEO-Entscheid, siehe UMBAU_CAMERA2_PROMPT.md AP-1): Quelle ist
    // Camera2FrameSource (regulärer Android-Weg, LENS_FACING_EXTERNAL). Der frühere direkte
    // V4L2Camera-Zugriff auf /dev/video0 ist mit AP-5 vollständig entfernt.
    single { CameraFrameBus(Camera2FrameSource(androidContext())) }
    // Welle 5 (ADR 0002 B1): Ein-Encoder-Ausschluss zwischen RTSP (OneVideoServer) und lokaler
    // Aufnahme (HardwareBitmapRecorder) auf dem einzigen HW-AVC-Codec der RK3588. Geteilte Instanz.
    single { CameraEncoderArbiter() }
    // Dual-Modus: NUR ONE — „TWO" ist ein anderes Produkt und wurde entfernt (Welle 4).
    // Migration A (2026-05-19): WLAN-Pfad raus, ONE läuft direkt auf der BWELL-Hardware
    // (Serial /dev/ttyS5 + V4L2 /dev/video0). Bezug: docs/PLAN_INTERNAL_HARDWARE_INTEGRATION.md, P5.
    //
    // Transport-Auswahl über die Pref `one_transport` (kein UI-Selektor; per DevTools/adb):
    //   "auto"     (DEFAULT) → HardwareModeDetector: ONE-Hardware (Board rk3588_s/rk30sdk + ttyS5)
    //                          → DIRECT (OneInternalHardwareService); sonst WiFi-Client.
    //   "internal"           → erzwingt Direkt-Modus.
    //   "remote"             → erzwingt WiFi-Client (ONE-Remote).
    // Ziel-IP des WiFi-Clients via `one_remote_ip` (Default 192.168.43.1).
    single<HardwareService> {
        val context = androidContext()
        val prefs = runBlocking { context.settingsStore.data.first() }
        val transport = prefs[stringPreferencesKey("one_transport")] ?: "auto"
        fun remoteService(): HardwareService {
            val targetIp = prefs[stringPreferencesKey("one_remote_ip")]
                ?.takeIf { it.isNotBlank() } ?: OneHardwareConfig().targetIp
            // F1-Fix: Gateway des aktuellen WLANs als ersten Probe-Kandidaten liefern —
            // im ONE-Hotspot IST das Gateway die ONE. dhcpInfo ist deprecated, aber die
            // einzige Quelle, die auch für ein internetloses (nicht-Default-)WLAN greift.
            val gatewayProvider = {
                try {
                    @Suppress("DEPRECATION")
                    val gw = (context.applicationContext
                        .getSystemService(Context.WIFI_SERVICE) as? WifiManager)
                        ?.dhcpInfo?.gateway ?: 0
                    if (gw == 0) null
                    else "%d.%d.%d.%d".format(
                        gw and 0xFF, (gw shr 8) and 0xFF, (gw shr 16) and 0xFF, (gw shr 24) and 0xFF
                    )
                } catch (_: Exception) {
                    null
                }
            }
            return OneHardwareService(OneHardwareConfig(targetIp = targetIp), gatewayProvider)
        }
        when (transport) {
            "internal" -> OneInternalHardwareService(cameraBus = get())
            "remote" -> remoteService()
            else -> {
                // "auto" (und unbekannte Werte): Detektor entscheidet.
                val decision = HardwareModeDetector().detectVerbose()
                Log.i("HardwareModeDetector", "auto-detect: ${decision.reason}")
                when (decision.mode) {
                    HardwareMode.DIRECT -> OneInternalHardwareService(cameraBus = get())
                    HardwareMode.WIFI -> remoteService()
                }
            }
        }
    }

    // Dual-Modus W4: aktiver Laufzeit-Modus, abgeleitet aus dem AUFGELÖSTEN HardwareService
    // (spiegelt die volle Detektor-Entscheidung inkl. `one_transport`-Override). EINE Quelle
    // der Wahrheit für die modusabhängige UI (Settings) und den OneRemoteServer-Start (OneApp).
    single {
        if (get<HardwareService>() is OneInternalHardwareService) HardwareMode.DIRECT else HardwareMode.WIFI
    }

    // Dual-Modus W3b: ONE-Remote-Server (DIRECT-Modus). Bekommt dieselbe HardwareService-
    // Instanz wie die App injiziert und spiegelt sie über WLAN. Gestartet wird er NUR im
    // DIRECT-Modus durch OneApp (Gate = HardwareMode-Single); im WiFi-/Tablet-Modus bleibt
    // das Single ungenutzt (kein Socket gebunden).
    single { OneRemoteServer(get()) }

    // Dual-Modus W3c: ONE-Video-Server (DIRECT-Modus) — serviert den V4L2-Feed als RTSP/H.264
    // (:8554/1234, konsistent zu OneHardwareConfig.buildRtspUrl). Reiner Konsument des
    // CameraFrameBus-Fan-outs (öffnet /dev/video0 NICHT selbst). Gestartet NUR im DIRECT-Modus
    // durch OneApp, parallel zum OneRemoteServer; im WiFi-/Tablet-Modus nie aufgelöst.
    single { OneVideoServer(get(), arbiter = get()) }

    // Dual-Modus W3a: Tablet-Hotspot-Host (DIRECT-Modus) — spannt on-demand (Pairing-Screen-
    // Schalter) den WLAN-Hotspot auf, dem ein Tablet ohne Büro-WLAN beitritt. SSID/Passphrase
    // werden per WIFI-QR gekoppelt. Lazy: nur im DIRECT-Modus vom PairingViewModel aufgelöst —
    // KEIN Auto-Start (CEO-Entscheid: per Schalter).
    //
    // EINE APK für beide Image-Varianten via FallbackHotspotStarter:
    //  - bevorzugt PRIVILEGIERTER SoftAP (AndroidSoftApStarter via Reflection: feste, gebrandete
    //    SSID DrainQ-ONE-<serial> + persistentes Geheimnis, OHNE Standortberechtigung) — möglich,
    //    weil die ONE im Werks-Image privilegiert ist (docs/SOFTAP_WERKS_PRIVILEG.md);
    //  - fehlt das Privileg (REASON_PRIVILEGE), Rückfall auf den öffentlichen LocalOnlyHotspot
    //    (AndroidLohsStarter) — so kommt der Hotspot auch auf Louis' nicht privilegierter Kiosk-ONE
    //    hoch. Den Standort, den LOHS verlangt, stellt der Device-Owner-Auto-Grant beim Start her.
    single {
        AccessPointController(
            get(),
            FallbackHotspotStarter(
                primary = AndroidSoftApStarter(androidContext()),
                fallback = AndroidLohsStarter(androidContext()),
            ),
        )
    }

    // Database
    single { AppDatabase.create(androidContext()) }
    single { get<AppDatabase>().projectDao() }
    single { get<AppDatabase>().damageDao() }
    single { get<AppDatabase>().noteDao() }
    single { get<AppDatabase>().updateEventDao() }
    single { ProjectRepository(get()) }
    single { DamageRepository(get()) }
    single { NoteRepository(get()) }
    single { UpdateEventRepository(get()) }

    // Update
    single { UpdateConfig(androidContext()) }
    single { UpdateInstaller(androidContext()) }
    single<UpdateService> { HttpUpdateService(androidContext(), get(), get(), get()) }

    // Export
    single { ProjectExportService(androidContext()) }

    // Weather & Damage Presets
    single { WeatherPresetRepository(androidContext()) }
    single { DamagePresetRepository(androidContext()) }
    single { WeatherApiService() }
    single { LocationService(androidContext()) }
    single { NominatimService() }
    single { OfflineMapManager(androidContext()) }
    single { OfflineMapRenderer(androidContext()) }
    single { OsmStaticMapService(get<OfflineMapManager>(), get<OfflineMapRenderer>()) }

    // Netzwerk & Verbindung
    single { ConnectivityMonitor(androidContext()) }
    single { WifiController(androidContext()) }
    single { CloudAccountStore(androidContext()) }

    // Auto-Reconnect W1 (Tablet): bekannte ONEs verschlüsselt persistieren (Keystore) …
    single { KnownOneStore(AndroidEncryptedStorage(androidContext())) }
    // … und automatisch wiederverbinden. Gestartet NUR im WiFi-/Tablet-Modus durch OneApp
    // (Spiegelbild des OneRemoteServer-Starts im DIRECT-Modus). Main.immediate serialisiert
    // die Zustandsmaschine; Plattform-Callbacks hoppen dorthin.
    single {
        OneAutoConnector(
            wifi = get<WifiController>(),
            store = get(),
            mode = get(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            autoConnectEnabled = {
                androidContext().settingsStore.data.first()[SettingsViewModel.KEY_AUTO_CONNECT_ONE] ?: true
            },
            startHardwareChain = {
                // Nach jedem erfolgreichen Join: Discovery + Polling starten (die RTSP-
                // VideoSource published der OneHardwareService bei Discovery selbst).
                val hardware = get<HardwareService>()
                if (!hardware.isConnected) {
                    val status = hardware.probeEndpoints()
                    if (status.cableControllerReachable || status.crawlerControllerReachable) {
                        hardware.startPolling()
                    }
                }
            },
            log = { Log.i("OneAutoConnector", it) },
        )
    }

    viewModel { ConnectionViewModel(get(), get(), get(), androidContext()) }
    viewModel { NetworkViewModel(get(), get(), get(), get(), get()) }
    // Dual-Modus W3a: Pairing-Screen (DIRECT) — Tablet-Hotspot an/aus + WIFI-QR.
    viewModel { PairingViewModel(get()) }
    viewModel { SettingsViewModel(androidContext(), get(), get(), get()) }
    viewModel { ProjectFormViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { ProjectsViewModel(get()) }
    viewModel { ProjectDetailViewModel(get(), get(), get(), get(), androidContext() as Application) }
}
