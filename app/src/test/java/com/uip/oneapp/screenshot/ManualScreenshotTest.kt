package com.uip.oneapp.screenshot

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.compose.rememberNavController
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.uip.oneapp.data.local.entity.DamageEntity
import com.uip.oneapp.data.local.entity.NoteEntity
import com.uip.oneapp.data.local.entity.ProjectEntity
import com.uip.oneapp.debugrig.buildSeedData
import com.uip.oneapp.maps.OfflineMapManager
import com.uip.oneapp.maps.OfflineMapRenderer
import com.uip.oneapp.ui.localization.LocalizationManager
import com.uip.oneapp.ui.screens.connection.ConnectionScreen
import com.uip.oneapp.ui.screens.home.HomeScreen
import com.uip.oneapp.ui.screens.home.VolumeUsage
import com.uip.oneapp.ui.screens.inspection.DamageDialog
import com.uip.oneapp.ui.screens.inspection.InspectionScreen
import com.uip.oneapp.ui.screens.inspection.NoteDialog
import com.uip.oneapp.ui.screens.network.CloudLoginScreen
import com.uip.oneapp.ui.screens.network.NetworkScreen
import com.uip.oneapp.ui.screens.offlinemaps.OfflineMapsScreen
import com.uip.oneapp.ui.screens.pairing.PairingScreen
import com.uip.oneapp.ui.screens.projectdetail.ProjectDetailScreen
import com.uip.oneapp.ui.screens.projectdetail.UsbExportDialog
import com.uip.oneapp.ui.screens.projects.MapPickerDialog
import com.uip.oneapp.ui.screens.projects.ProjectFormScreen
import com.uip.oneapp.ui.screens.projects.ProjectsScreen
import com.uip.oneapp.ui.screens.reports.ReportsScreen
import com.uip.oneapp.ui.screens.settings.SettingsScreen
import com.uip.oneapp.ui.theme.DrainQTheme
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.koin.android.ext.koin.androidContext
import org.koin.compose.KoinContext
import org.koin.dsl.koinApplication

/**
 * W-H4: Synthetische Handbuch-Screenshots — alle 20 Szenen für jede Sprache.
 *
 * Paparazzi rendert via layoutlib (reines Java) — kein native DLL, läuft auf Windows + CI.
 * FakeHardwareService liefert VideoSource.LocalBitmap(pipe_frame). InspectionScreen wählt
 * darauf LocalBitmapVideoPlayer — kein ExoPlayer, kein AndroidView.
 *
 * Jeder Snapshot erhält einen eigenen KoinApplication-Scope (kein globales startKoin/stopKoin)
 * → keine ClosedScopeException zwischen Test-Methoden.
 *
 * Aufruf via render.ps1 oder direkt:
 *   ./gradlew :app:recordPaparazziDebug -Dscreenshot.lang=de
 *
 * PNGs landen in app/src/test/snapshots/images/ und werden von render.ps1 nach
 * docs/manual/screenshots_synth/<lang>/ kopiert.
 */
class ManualScreenshotTest {

    // Landscape 960dp × 600dp @ 2x = 1920 × 1200 px (identisch mit Geräte-Screenshots W-H3)
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig(
            screenWidth = 1920,
            screenHeight = 1200,
            xdpi = 320,
            ydpi = 320,
            orientation = com.android.resources.ScreenOrientation.LANDSCAPE,
            density = com.android.resources.Density.XHIGH,
            ratio = com.android.resources.ScreenRatio.LONG,
            size = com.android.resources.ScreenSize.XLARGE,
            keyboard = com.android.resources.Keyboard.NOKEY,
            touchScreen = com.android.resources.TouchScreen.FINGER,
            keyboardState = com.android.resources.KeyboardState.HIDDEN,
            softButtons = true,
            navigation = com.android.resources.Navigation.NONAV,
        ),
        showSystemUi = false,
        maxPercentDifference = 0.0,
    )

    // Pre-erstellte Fakes — direkt instanziiert, kein globaler Koin-Kontext in setUp()
    private lateinit var fakeProjectDao: FakeProjectDao
    private lateinit var fakeDamageDao: FakeDamageDao
    private lateinit var fakeNoteDao: FakeNoteDao
    private lateinit var fakeUpdateEventDao: FakeUpdateEventDao
    private lateinit var fakeHardwareService: FakeHardwareService
    private lateinit var offlineMapManager: OfflineMapManager
    private lateinit var offlineMapRenderer: OfflineMapRenderer
    private lateinit var fakeApp: ScreenshotFakeApplication

    private var demoProjectId: Long = -1L
    private lateinit var demoProject: ProjectEntity

    val lang: String get() = System.getProperty("screenshot.lang") ?: "de"

    @Before
    fun setUp() {
        // ViewModels use viewModelScope backed by Dispatchers.Main.immediate.
        // Without this, koinApplication creates ViewModels whose init coroutines crash.
        Dispatchers.setMain(UnconfinedTestDispatcher())

        fakeProjectDao = FakeProjectDao()
        fakeDamageDao = FakeDamageDao()
        fakeNoteDao = FakeNoteDao()
        fakeUpdateEventDao = FakeUpdateEventDao()
        fakeHardwareService = FakeHardwareService()
        fakeApp = ScreenshotFakeApplication().also { it.initContext(paparazzi.context) }
        offlineMapManager = OfflineMapManager(paparazzi.context)
        offlineMapRenderer = OfflineMapRenderer(paparazzi.context)

        // W-H4b: init() NICHT aufrufen — init() startet einen IO-Coroutine, der den DataStore
        // liest und _currentLanguage zurück auf "de" setzt (Race gegen setLanguage). Im Test
        // genügt der direkte setLanguage()-Aufruf, der den StateFlow synchron setzt.
        LocalizationManager.setLanguage(paparazzi.context, lang)

        // Portal-Weg: optionale Überschreibung via -Dscreenshot.translationJson=<Pfad>
        // Das JSON enthält UI-Strings im Portal-Exportformat {key:value,...}.
        // Fehlende Keys fallen auf DE zurück (implementiert in LocalizationManager.getString).
        System.getProperty("screenshot.translationJson")?.let { jsonPath ->
            val file = java.io.File(jsonPath)
            if (file.exists()) {
                val obj = JSONObject(file.readText())
                val map = mutableMapOf<String, String>()
                val keys = obj.keys()
                while (keys.hasNext()) { val k = keys.next(); map[k] = obj.getString(k) }
                LocalizationManager.injectLanguage(lang, map)
                // WARN-Liste: bekannte Pflicht-Keys, die im Portal-JSON fehlen
                val sampleKeys = listOf("quick_capture", "gallery", "appearance", "settings")
                val warnMissing = sampleKeys.filter { !map.containsKey(it) }
                if (warnMissing.isNotEmpty()) {
                    System.err.println("W-H4 WARN [translationJson $lang]: fehlende Keys (DE-Fallback aktiv): $warnMissing")
                }
            } else {
                System.err.println("W-H4 WARN: translationJson nicht gefunden: $jsonPath")
            }
        }

        demoProjectId = runBlocking { seedDemoData() }
        demoProject = runBlocking { fakeProjectDao.getByProjectNumber("DEMO_160726_0900_01")!! }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        LocalizationManager.clearInjectedLanguage(lang)
    }

    // ── Hilfsfunktion: Composable rendern + Screenshot speichern ──────────────

    private fun screenshot(sceneName: String, content: @Composable () -> Unit) {
        // Isolierter Koin-Scope: koinApplication{} startet NICHT global (kein startKoin →
        // kein KoinAppAlreadyStartedException bei parallelen Test-Methoden im JVM-Prozess).
        val koinApp = koinApplication {
            androidContext(paparazzi.context)
            modules(buildSnapshotModule(
                projectDao = fakeProjectDao,
                damageDao = fakeDamageDao,
                noteDao = fakeNoteDao,
                updateEventDao = fakeUpdateEventDao,
                hardwareService = fakeHardwareService,
                offlineMapManager = offlineMapManager,
                offlineMapRenderer = offlineMapRenderer,
                fakeApp = fakeApp,
            ))
        }
        val fakeRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) {}
        }
        try {
            paparazzi.snapshot(name = "${lang}_${sceneName}") {
                // KoinContext wraps den isolierten Koin — kein startKoin, kein globaler State
                KoinContext(context = koinApp.koin) {
                    CompositionLocalProvider(
                        // Frischer ViewModelStore pro Snapshot (kein ViewModel-Zustand-Leak)
                        LocalViewModelStoreOwner provides object : ViewModelStoreOwner {
                            override val viewModelStore = ViewModelStore()
                        },
                        // Fake-Registry für rememberLauncherForActivityResult() (NoteDialog, ProjectDetailScreen)
                        LocalActivityResultRegistryOwner provides object : ActivityResultRegistryOwner {
                            override val activityResultRegistry = fakeRegistry
                        },
                    ) {
                        DrainQTheme { content() }
                    }
                }
            }
        } finally {
            koinApp.close()
        }
    }

    // ── Szenen (20 Stück, entsprechend scenes.json) ───────────────────────────

    @Test fun scr02_home() = screenshot("scr02_home") {
        HomeScreen(navController = rememberNavController())
    }

    @Test fun scr03_connection() = screenshot("scr03_connection") {
        ConnectionScreen()
    }

    @Test fun scr04_projects() = screenshot("scr04_projects") {
        ProjectsScreen(navController = rememberNavController())
    }

    @Test fun scr05_project_form_new() = screenshot("scr05_project_form_new") {
        ProjectFormScreen(navController = rememberNavController(), editProjectId = null)
    }

    @Test fun scr05b_project_form_edit() = screenshot("scr05b_project_form_edit") {
        ProjectFormScreen(navController = rememberNavController(), editProjectId = demoProjectId)
    }

    @Test fun scr06_project_detail() = screenshot("scr06_project_detail") {
        ProjectDetailScreen(navController = rememberNavController(), projectId = demoProjectId)
    }

    @Test fun scr07_inspection_live() = screenshot("scr07_inspection_live") {
        InspectionScreen(
            navController = rememberNavController(),
            projectId = demoProjectId,
            previewShowBottomBar = true,
        )
    }

    @Test fun scr07b_inspection_recording() {
        screenshot("scr07b_inspection_recording") {
            InspectionScreen(
                navController = rememberNavController(),
                projectId = demoProjectId,
                previewRecordingActive = true,
                previewShowBottomBar = true,
            )
        }
    }

    @Test fun scr08_reports() = screenshot("scr08_reports") {
        ReportsScreen(navController = rememberNavController())
    }

    @Test fun scr09_settings() = screenshot("scr09_settings") {
        SettingsScreen(navController = rememberNavController())
    }

    @Test fun scr10_network() = screenshot("scr10_network") {
        NetworkScreen(navController = rememberNavController())
    }

    @Test fun scr11_cloud_login() = screenshot("scr11_cloud_login") {
        CloudLoginScreen(navController = rememberNavController())
    }

    @Test fun scr12_offline_maps() = screenshot("scr12_offline_maps") {
        OfflineMapsScreen(navController = rememberNavController())
    }

    @Test fun scr13_pairing() = screenshot("scr13_pairing") {
        PairingScreen(navController = rememberNavController())
    }

    @Test fun scr02_home_storage_usb() = screenshot("scr02_home_storage_usb") {
        HomeScreen(
            navController = rememberNavController(),
            previewUsbStorage = Pair(
                "USB-Stick",
                VolumeUsage(freeBytes = 12_000_000_000L, totalBytes = 32_000_000_000L),
            ),
        )
    }

    // ── Dialog-Szenen ─────────────────────────────────────────────────────────

    @Test fun dlg_damage_dialog() = screenshot("dlg_damage_dialog") {
        DamageDialog(
            photoPath = "",
            currentMeter = 0.72f,
            projectId = demoProjectId,
            onSave = {},
            onDismiss = {},
        )
    }

    @Test fun dlg_note_dialog() = screenshot("dlg_note_dialog") {
        NoteDialog(
            currentMeter = 0.72f,
            projectId = demoProjectId,
            onSave = {},
            onDismiss = {},
        )
    }

    @Test fun dlg_pdf_preview() {
        // PdfRenderer ist in layoutlib nicht gemockt → Fake-Bitmap als Seite 1 injizieren.
        // initialBitmaps-Parameter (State-Hoisting) umgeht den LaunchedEffect mit PdfRenderer.
        val fakePageBitmap = android.graphics.Bitmap.createBitmap(595, 842, android.graphics.Bitmap.Config.ARGB_8888).also {
            android.graphics.Canvas(it).drawColor(android.graphics.Color.WHITE)
        }
        val dummyPdf = java.io.File(paparazzi.context.cacheDir, "_dummy.pdf")
        dummyPdf.createNewFile()
        screenshot("dlg_pdf_preview") {
            com.uip.oneapp.ui.screens.projectdetail.PdfPreviewDialog(
                pdfFile = dummyPdf,
                onDismiss = {},
                onExport = {},
                initialBitmaps = listOf(fakePageBitmap),
                renderInline = true,
            )
        }
        dummyPdf.delete()
        fakePageBitmap.recycle()
    }

    @Test fun dlg_usb_export() = screenshot("dlg_usb_export") {
        UsbExportDialog(
            project = demoProject,
            onDismiss = {},
        )
    }

    @Test fun dlg_map_picker() = screenshot("dlg_map_picker") {
        MapPickerDialog(
            initialLat = 48.137154,
            initialLon = 11.576124,
            offlineManager = offlineMapManager,
            offlineRenderer = offlineMapRenderer,
            onDismiss = {},
            onConfirm = { _, _ -> },
        )
    }

    // ── Demo-Seed ─────────────────────────────────────────────────────────────

    private suspend fun seedDemoData(): Long {
        fakeProjectDao.getByProjectNumber("DEMO_160726_0900_01")?.let { fakeProjectDao.delete(it) }

        val sd = buildSeedData()
        val sp = sd.project

        val projectId = fakeProjectDao.insert(
            ProjectEntity(
                projectNumber = sp.projectNumber,
                auftraggeber = sp.auftraggeber,
                standortAdresse = sp.standortAdresse,
                inspektor = sp.inspektor,
                kameratyp = sp.kameratyp,
                material = sp.material,
                durchmesser = sp.durchmesser,
                leitungstyp = sp.leitungstyp,
                inspektionsdatum = sp.inspektionsdatum,
                inspektionslaenge = sp.inspektionslaenge,
                formVideo = true,
                formFoto = true,
                formVisuell = false,
                videoQuality = "HD",
                videoOverlay = true,
                status = "OPEN"
            )
        )
        for (d in sd.damages) {
            fakeDamageDao.insert(
                DamageEntity(
                    projectId = projectId,
                    position = d.position,
                    damageType = d.damageType,
                    description = d.description
                )
            )
        }
        for (n in sd.notes) {
            fakeNoteDao.insert(
                NoteEntity(
                    projectId = projectId,
                    position = n.position,
                    text = n.text
                )
            )
        }
        return projectId
    }
}
