package com.uip.oneapp.di

import android.app.Application
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
import com.uip.oneapp.network.HardwareMode
import com.uip.oneapp.network.HardwareModeDetector
import com.uip.oneapp.network.HardwareService
import com.uip.oneapp.network.OneHardwareConfig
import com.uip.oneapp.network.OneHardwareService
import com.uip.oneapp.network.OneRemoteServer
import com.uip.oneapp.network.LocationService
import com.uip.oneapp.network.NetworkDiscoveryService
import com.uip.oneapp.network.NominatimService
import com.uip.oneapp.network.internal.OneInternalHardwareService
import com.uip.oneapp.network.OsmStaticMapService
import com.uip.oneapp.network.RtspStreamTester
import com.uip.oneapp.network.WeatherApiService
import com.uip.oneapp.cloud.CloudAccountStore
import com.uip.oneapp.network.ConnectivityMonitor
import com.uip.oneapp.network.WifiController
import com.uip.oneapp.ui.screens.connection.ConnectionViewModel
import com.uip.oneapp.ui.screens.network.NetworkViewModel
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { NetworkDiscoveryService(androidContext()) }
    single { RtspStreamTester() }
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
            return OneHardwareService(OneHardwareConfig(targetIp = targetIp))
        }
        when (transport) {
            "internal" -> OneInternalHardwareService()
            "remote" -> remoteService()
            else -> {
                // "auto" (und unbekannte Werte): Detektor entscheidet.
                val decision = HardwareModeDetector().detectVerbose()
                Log.i("HardwareModeDetector", "auto-detect: ${decision.reason}")
                when (decision.mode) {
                    HardwareMode.DIRECT -> OneInternalHardwareService()
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

    viewModel { ConnectionViewModel(get(), get(), get(), androidContext()) }
    viewModel { NetworkViewModel(get(), get()) }
    viewModel { SettingsViewModel(androidContext(), get(), get(), get()) }
    viewModel { ProjectFormViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { ProjectsViewModel(get()) }
    viewModel { ProjectDetailViewModel(get(), get(), get(), get(), androidContext() as Application) }
}
