package com.uip.oneapp.screenshot

import android.app.Application
import android.content.Context
import com.uip.oneapp.cloud.CloudAccountStore
import com.uip.oneapp.data.local.dao.DamageDao
import com.uip.oneapp.data.local.dao.NoteDao
import com.uip.oneapp.data.local.dao.ProjectDao
import com.uip.oneapp.data.local.dao.UpdateEventDao
import com.uip.oneapp.data.local.entity.DamageEntity
import com.uip.oneapp.data.local.entity.NoteEntity
import com.uip.oneapp.data.local.entity.ProjectEntity
import com.uip.oneapp.data.local.entity.UpdateEventEntity
import com.uip.oneapp.data.local.entity.UpdateEventType
import com.uip.oneapp.data.repository.DamagePresetRepository
import com.uip.oneapp.data.repository.DamageRepository
import com.uip.oneapp.data.repository.NoteRepository
import com.uip.oneapp.data.repository.ProjectRepository
import com.uip.oneapp.data.repository.UpdateEventRepository
import com.uip.oneapp.data.repository.WeatherPresetRepository
import com.uip.oneapp.export.ProjectExportService
import com.uip.oneapp.maps.OfflineMapManager
import com.uip.oneapp.maps.OfflineMapRenderer
import com.uip.oneapp.network.AccessPointController
import com.uip.oneapp.network.CameraEncoderArbiter
import com.uip.oneapp.network.ConnectivityMonitor
import com.uip.oneapp.network.HardwareMode
import com.uip.oneapp.network.HardwareService
import com.uip.oneapp.network.HotspotSession
import com.uip.oneapp.network.HotspotStarter
import com.uip.oneapp.network.KnownOneStore
import com.uip.oneapp.network.LocationService
import com.uip.oneapp.network.NetworkDiscoveryService
import com.uip.oneapp.network.NominatimService
import com.uip.oneapp.network.OneAutoConnector
import com.uip.oneapp.network.OsmStaticMapService
import com.uip.oneapp.network.RtspStreamTester
import com.uip.oneapp.network.SecretKeyValueStore
import com.uip.oneapp.network.WeatherApiService
import com.uip.oneapp.network.WifiController
import com.uip.oneapp.update.ReleaseInfo
import com.uip.oneapp.update.UpdateCheckResult
import com.uip.oneapp.update.UpdateConfig
import com.uip.oneapp.update.UpdateInstaller
import com.uip.oneapp.update.UpdateService
import com.uip.oneapp.ui.screens.connection.ConnectionViewModel
import com.uip.oneapp.ui.screens.network.NetworkViewModel
import com.uip.oneapp.ui.screens.pairing.PairingViewModel
import com.uip.oneapp.ui.screens.projectdetail.ProjectDetailViewModel
import com.uip.oneapp.ui.screens.projects.ProjectFormViewModel
import com.uip.oneapp.ui.screens.projects.ProjectsViewModel
import com.uip.oneapp.ui.screens.offlinemaps.OfflineMapsViewModel
import com.uip.oneapp.ui.screens.settings.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

// ── Fake DAOs (kein SQLite — Paparazzi hat kein natives SQLite) ─────────────

class FakeProjectDao : ProjectDao {
    private var nextId = 1L
    private val projects = mutableListOf<ProjectEntity>()
    private val flow = MutableStateFlow<List<ProjectEntity>>(emptyList())

    override suspend fun insert(project: ProjectEntity): Long {
        val id = nextId++
        val withId = project.copy(id = id)
        projects.add(withId)
        flow.value = projects.toList()
        return id
    }
    override suspend fun update(project: ProjectEntity) {
        val idx = projects.indexOfFirst { it.id == project.id }
        if (idx >= 0) { projects[idx] = project; flow.value = projects.toList() }
    }
    override suspend fun delete(project: ProjectEntity) {
        projects.removeAll { it.id == project.id }
        flow.value = projects.toList()
    }
    override fun getAllProjects(): Flow<List<ProjectEntity>> = flow
    override suspend fun getById(id: Long): ProjectEntity? = projects.firstOrNull { it.id == id }
    override suspend fun getByProjectNumber(number: String): ProjectEntity? =
        projects.firstOrNull { it.projectNumber == number }
    override fun getByIdFlow(id: Long): Flow<ProjectEntity?> = flow.map { it.firstOrNull { p -> p.id == id } }
    override suspend fun countProjectsOnDate(dateStr: String): Int = 0
}

class FakeDamageDao : DamageDao {
    private var nextId = 1L
    private val damages = mutableListOf<DamageEntity>()
    private fun flowFor(projectId: Long): Flow<List<DamageEntity>> =
        flowOf(damages.filter { it.projectId == projectId })
    private val allFlow = MutableStateFlow<List<DamageEntity>>(emptyList())

    override suspend fun insert(damage: DamageEntity): Long {
        val id = nextId++
        damages.add(damage.copy(id = id))
        allFlow.value = damages.toList()
        return id
    }
    override suspend fun update(damage: DamageEntity) {
        val idx = damages.indexOfFirst { it.id == damage.id }
        if (idx >= 0) { damages[idx] = damage; allFlow.value = damages.toList() }
    }
    override suspend fun delete(damage: DamageEntity) {
        damages.removeAll { it.id == damage.id }
        allFlow.value = damages.toList()
    }
    override fun getByProjectId(projectId: Long): Flow<List<DamageEntity>> =
        allFlow.map { it.filter { d -> d.projectId == projectId } }
    override suspend fun countByProjectId(projectId: Long): Int =
        damages.count { it.projectId == projectId }
    override suspend fun getById(id: Long): DamageEntity? = damages.firstOrNull { it.id == id }
}

class FakeNoteDao : NoteDao {
    private var nextId = 1L
    private val notes = mutableListOf<NoteEntity>()
    private val allFlow = MutableStateFlow<List<NoteEntity>>(emptyList())

    override suspend fun insert(note: NoteEntity): Long {
        val id = nextId++
        notes.add(note.copy(id = id))
        allFlow.value = notes.toList()
        return id
    }
    override suspend fun update(note: NoteEntity) {
        val idx = notes.indexOfFirst { it.id == note.id }
        if (idx >= 0) { notes[idx] = note; allFlow.value = notes.toList() }
    }
    override suspend fun delete(note: NoteEntity) {
        notes.removeAll { it.id == note.id }
        allFlow.value = notes.toList()
    }
    override fun getByProjectId(projectId: Long): Flow<List<NoteEntity>> =
        allFlow.map { it.filter { n -> n.projectId == projectId } }
}

class FakeUpdateEventDao : UpdateEventDao {
    override suspend fun insert(event: UpdateEventEntity): Long = 0L
    override fun getAllFlow(): Flow<List<UpdateEventEntity>> = flowOf(emptyList())
    override suspend fun getRecent(limit: Int): List<UpdateEventEntity> = emptyList()
    override suspend fun deleteOlderThan(cutoffMs: Long) {}
}

// ── Weitere Fakes ──────────────────────────────────────────────────────────

class FakeSecretKeyValueStore : SecretKeyValueStore {
    private val map = mutableMapOf<String, String>()
    override fun put(key: String, value: String) { map[key] = value }
    override fun get(key: String): String? = map[key]
    override fun remove(key: String) { map.remove(key) }
    override fun keys(): Set<String> = map.keys.toSet()
}

class FakeHotspotStarter : HotspotStarter {
    override fun start(
        onActive: (ssid: String, passphrase: String) -> Unit,
        onFailed: (reason: String) -> Unit,
        onStopped: () -> Unit,
    ): HotspotSession = HotspotSession { }
}

class FakeUpdateService : UpdateService {
    override suspend fun checkForUpdate(): UpdateCheckResult = UpdateCheckResult.NoUpdate
    override suspend fun downloadAndInstall(release: ReleaseInfo) {}
}

// ── Fake Application für AndroidViewModel-Tests ──────────────────────────

/**
 * Minimale Application-Subklasse für Paparazzi-Tests.
 * attachBaseContext() ist in ContextWrapper protected — der Subklassen-Aufruf
 * via initContext() ist der sauberste Weg ohne Reflection.
 */
class ScreenshotFakeApplication : Application() {
    fun initContext(ctx: Context) = attachBaseContext(ctx)
}

// ── Koin-Modul-Factory ────────────────────────────────────────────────────

/**
 * W-H4: Erzeugt ein isoliertes Koin-Modul pro Paparazzi-Snapshot.
 * Nimmt alle pre-erstellten Fake-Instanzen entgegen, damit DAOs
 * zwischen setUp() und dem Snapshot-Render dieselben In-Memory-Daten halten.
 *
 * Wird aus KoinApplication { modules(buildSnapshotModule(...)) } aufgerufen —
 * jeder Snapshot erhält einen eigenen, vollständig isolierten Koin-Scope.
 */
fun buildSnapshotModule(
    projectDao: FakeProjectDao,
    damageDao: FakeDamageDao,
    noteDao: FakeNoteDao,
    updateEventDao: FakeUpdateEventDao,
    hardwareService: FakeHardwareService,
    offlineMapManager: OfflineMapManager,
    offlineMapRenderer: OfflineMapRenderer,
    fakeApp: ScreenshotFakeApplication,
) = module {

    // Fester Kalendertag für alle Screenshots — sonst driftet jedes Datumsfeld mit dem
    // tatsächlichen Aufnahmetag vom eingefrorenen Golden ab (siehe OFFENE_PUNKTE.md,
    // "Datum im Vergleichsbild wandert"). Datum beliebig, aber fix; 2026-07-17 gewählt,
    // weil es bereits als Referenzdatum in mehreren Handbuch-Screenshots dokumentiert war.
    single<java.time.Clock> {
        java.time.Clock.fixed(
            java.time.LocalDate.of(2026, 7, 17).atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
            java.time.ZoneOffset.UTC
        )
    }

    // Application für AndroidViewModel-Subklassen
    single<Application> { fakeApp }

    // FakeHardwareService mit VideoSource.LocalBitmap(pipe_frame)
    single<HardwareService> { hardwareService }
    single { HardwareMode.WIFI }
    single { CameraEncoderArbiter() }

    // Pre-seeded In-Memory Fake DAOs
    single<ProjectDao> { projectDao }
    single<DamageDao> { damageDao }
    single<NoteDao> { noteDao }
    single<UpdateEventDao> { updateEventDao }

    // Repositories mit Fake DAOs
    single { ProjectRepository(get()) }
    single { DamageRepository(get()) }
    single { NoteRepository(get()) }
    single { UpdateEventRepository(get()) }

    // Preset-Repositories (SharedPreferences-backed via Paparazzi-Context)
    single { DamagePresetRepository(androidContext()) }
    single { WeatherPresetRepository(androidContext()) }

    // Kein Netzwerk-IO in Tests
    single { NetworkDiscoveryService(androidContext()) }
    single { RtspStreamTester() }
    single { WeatherApiService() }
    single { LocationService(androidContext()) }
    single { NominatimService() }

    // ConnectivityMonitor + WifiController (safe-cast gegen BridgeContext-AssertionError)
    single { ConnectivityMonitor(androidContext()) }
    single { WifiController(androidContext()) }
    single { CloudAccountStore(androidContext()) }

    // KnownOneStore mit In-Memory-Fake (kein Android-Keystore)
    single<SecretKeyValueStore> { FakeSecretKeyValueStore() }
    single { KnownOneStore(get()) }

    // OneAutoConnector — Scope mit Unconfined
    single {
        OneAutoConnector(
            wifi = get<WifiController>(),
            store = get(),
            mode = HardwareMode.WIFI,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            autoConnectEnabled = { false },
            startHardwareChain = {},
        )
    }

    // AccessPointController mit No-op Starter
    single {
        AccessPointController(
            mode = HardwareMode.WIFI,
            starter = FakeHotspotStarter(),
        )
    }

    // Export-Service
    single { ProjectExportService(androidContext()) }

    // Maps — pre-erstellte Instanzen (dlg_map_picker nutzt dieselben Objekte direkt)
    single { offlineMapManager }
    single { offlineMapRenderer }
    single { OsmStaticMapService(get(), get()) }

    // Update-Stubs
    single { UpdateConfig(androidContext()) }
    single { UpdateInstaller(androidContext()) }
    single<UpdateService> { FakeUpdateService() }

    // ViewModels
    viewModel { ConnectionViewModel(get(), get(), get(), androidContext()) }
    viewModel { NetworkViewModel(get(), get(), get(), get(), get()) }
    viewModel { PairingViewModel(get()) }
    viewModel { SettingsViewModel(androidContext(), get(), get(), get()) }
    viewModel { ProjectFormViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { ProjectsViewModel(get()) }
    viewModel { ProjectDetailViewModel(get(), get(), get(), get(), get<Application>()) }
    viewModel { OfflineMapsViewModel(get<Application>()) }
}
