package com.uip.oneapp

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.lifecycle.lifecycleScope
import com.uip.oneapp.bootstrap.OneDeviceAdminReceiver
import com.uip.oneapp.kiosk.KioskPolicy
import com.uip.oneapp.kiosk.LeaveAppTarget
import com.uip.oneapp.network.HardwareMode
import com.uip.oneapp.network.HardwareModeDetector
import com.uip.oneapp.ui.components.LocalKioskEnabled
import com.uip.oneapp.ui.components.TaskbarRestash
import com.uip.oneapp.ui.hardware.HardwareKeyBus
import com.uip.oneapp.ui.localization.LocalizationManager
import com.uip.oneapp.ui.navigation.NavGraph
import com.uip.oneapp.ui.screens.settings.settingsStore
import com.uip.oneapp.ui.screens.splash.SplashScreen
import com.uip.oneapp.ui.theme.DrainQTheme
import com.uip.oneapp.ui.theme.isDark
import com.uip.oneapp.ui.theme.rememberThemeMode
import com.uip.oneapp.ui.utils.LocalWindowSizeClass
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    // Kiosk-Pflicht (Kette kiosk-pflicht, 03.09.2026; CEO-Entscheid R1/R2; Runde 2 N-2/N-3):
    // Auf der ONE-Hardware ist der Kiosk der Normalzustand — kein Schalter, kein gespeicherter
    // Wert. lockdownActive steht ab onCreate auf der KioskPolicy (ONE-Geraet = true, sonst
    // false — Geraeteidentitaet, NICHT Transport) und wird EINZIG durch „App verlassen"
    // (leaveApp) auf false gesetzt; der naechste App-Start beginnt wieder im Kiosk. Der
    // Altschluessel kiosk_mode im DataStore wird nicht mehr gelesen und bleibt stehen (N-3).
    private val hardwareMode: HardwareMode by inject()
    // Kette kiosk-pflicht, Runde 5 (P-1): letzte Ebene der Ausstiegssperre — greift auch,
    // wenn ein künftiger Ausstiegsweg die UI-Gates (SettingsScreen, Power-Dialog) umgeht.
    private val recordingBus: com.uip.oneapp.network.RecordingStateBus by inject()
    private val lockdownState = mutableStateOf(false)
    private val lockdownActive: Boolean
        get() = lockdownState.value
    private var lockdownPlan = KioskPolicy.LockdownPlan(immersive = false, lockTask = false)

    // Wächter: blendet die System-Bars bei aktivem Lockdown wieder aus, falls sie auftauchen
    // (Neustart nach Self-Update, transientes Einwischen, Systemdialog). Befund 0.4.1:
    // Nach dem Update kam die Leiste hoch und blieb, bis der Kiosk-Schalter neu gesetzt wurde.
    private val reHideBarsRunnable = Runnable { if (lockdownActive) applySystemBars() }

    // Stash-Impuls (Kette taskbar-balken, 10.08.2026): Fremde Systemfenster (Power-Dialog,
    // Berechtigungsdialog, Leiste, IME) „unstashen" die launcher3-Taskbar; hide()/Flags heilen
    // nicht, ein App-Fenster-auf/zu heilt (siehe TaskbarRestash). Auslöser: Fokus-Rückkehr nach
    // FREMDEM Fokusverlust (Merker lostWindowFocus; der vom eigenen Impuls verursachte
    // Fokusverlust setzt ihn nicht — s. onWindowFocusChanged) + IME-Flanke (unten im
    // Insets-Listener). Der Impuls feuert verzögert, damit der Unstash des Systems abgeschlossen
    // ist, bevor er stasht, und wird bei stehender Tastatur unterdrückt (Eingabeschutz).
    // Kette kiosk-pflicht, 03.09.2026 (C): Die Balken-Behandlung haengt NICHT mehr am
    // Kiosk-Zustand — sie laeuft auch nach „App verlassen" und im WIFI-Modus weiter.
    private lateinit var taskbarRestash: TaskbarRestash
    private var lostWindowFocus = false
    private var imeWasVisible = false

    private fun scheduleRestashImpulse() {
        window.decorView.postDelayed({
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@postDelayed
            // Eingabeschutz: Steht die Soft-Tastatur, läuft eine Eingabe. Der Impuls nähme dem
            // Eingabefeld den Fensterfokus und damit dem Anwender die Tastatur mitten im Tippen.
            // Dann NICHT feuern — die IME-Flanke holt den Impuls nach, sobald die Tastatur zugeht
            // (der Balken bleibt bis dahin stehen; das ist der Preis und bewusst so gewählt).
            val imeUp = ViewCompat.getRootWindowInsets(window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.ime()) ?: false
            if (imeUp) {
                Log.d(TAG, "Stash-Impuls unterdrückt (Tastatur steht)")
                return@postDelayed
            }
            taskbarRestash.fire()
        }, RESTASH_DELAY_MS)
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        taskbarRestash = TaskbarRestash(window.decorView)

        // Kiosk-Pflicht: Politik einmal festlegen und Lockdown sofort anwenden — ohne
        // Warten auf DataStore. Seit Runde 2 (NACHBESSERUNG N-2, Befund B3) haengt der
        // Kiosk an der GERAETEIDENTITAET (ONE-Board-Marker), nicht am Laufzeit-Transport:
        // Fällt ttyS5 aus oder steht `one_transport=remote` (per adb setzbar), läuft die
        // ONE trotzdem im Kiosk — der Transport geht dann auf WiFi, der Kiosk bleibt.
        // Tablets tragen den Board-Marker nie → Tablet-Ausnahme (E2/R1) unveraendert.
        val dpm = getSystemService(DevicePolicyManager::class.java)
        val isOneDevice = HardwareModeDetector.isOneBoardModel(Build.MODEL, Build.BOARD)
        lockdownPlan = KioskPolicy.plan(isOneDevice, dpm?.isDeviceOwnerApp(packageName) == true)
        lockdownState.value = lockdownPlan.immersive
        Log.i(TAG, "Kiosk-Pflicht: oneDevice=$isOneDevice transport=$hardwareMode plan=$lockdownPlan")

        // Original-App-Technik (BaseActivity/BaseDialogFragment): Re-Hide-Listener auf der
        // Activity-decorView. Sobald irgendetwas die System-UI dieses Fensters sichtbar macht
        // (transientes Wischen, IME/Tastatur, fremde Insets), setzen wir bei aktivem Lockdown
        // sofort die Legacy-Immersive-Flags (5894) erneut. Die moderne WindowInsetsController-API
        // ist gegen die launcher3-Gesten-Taskbar wirkungslos — die Legacy-Flags sind der Träger.
        @Suppress("DEPRECATION")
        window.decorView.setOnSystemUiVisibilityChangeListener {
            if (lockdownActive) applyLegacyImmersive()
        }

        // Kiosk-Härtung: Wird die System-Leiste sichtbar, obwohl Lockdown an ist, ziehen wir sie
        // verzögert wieder ein. Der Fokuswechsel allein greift beim Update-Neustart nicht,
        // weil der Zustand erst asynchron anliegt (Befund 0.4.1, ONE-Gerät).
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { v, insets ->
            if (lockdownActive && insets.isVisible(WindowInsetsCompat.Type.systemBars())) {
                v.removeCallbacks(reHideBarsRunnable)
                v.postDelayed(reHideBarsRunnable, 1500L)
            }
            // IME-Flanke: Die Soft-Tastatur wechselt den Fensterfokus NICHT, unstasht die
            // Taskbar aber (Befund 0.4.1). Beim Schließen der Tastatur (sichtbar → unsichtbar)
            // einen Stash-Impuls auslösen — der Fokus-Auslöser unten greift hier nicht.
            // Seit kiosk-pflicht ohne Kiosk-Tor: die Balken-Behandlung laeuft immer (C).
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            if (imeWasVisible && !imeVisible) scheduleRestashImpulse()
            imeWasVisible = imeVisible
            insets
        }

        // Backstop-Wächter: Manche Übergänge blenden eine System-Leiste ein, OHNE eine
        // Insets-Meldung an decorView zu schicken — der Listener oben feuert dann nicht.
        // Dieser Lebenszyklus-Wächter prüft bei aktivem Lockdown regelmäßig den tatsächlichen
        // Sichtbarkeitszustand und zieht eine vom App-Fenster kontrollierbare Leiste wieder ein.
        // WICHTIG (On-Device-Befund 0.4.1, ONE/RK3588 + launcher3): Die eigentliche
        // „Navigationsleiste" der ONE ist die launcher3-System-Taskbar (ITYPE_EXTRA_NAVIGATION_BAR).
        // Sie wird von jedem SEPARATEN Fenster (Compose-Dialog/-Popup, fremde Systemfenster,
        // Soft-Tastatur) „unstashed". Zwei Konsequenzen, beide am 10.08.2026 auf e92df62d per
        // adb belegt (Kette taskbar-balken): (1) Sie ist in den Insets UNSICHTBAR —
        // ITYPE_EXTRA_NAVIGATION_BAR bleibt visible=false, systemBars()/tappableElement() ändern
        // sich nicht; dieser Wächter und der Insets-Listener können sie prinzipiell nicht
        // detektieren. (2) Sie lässt sich per WindowInsetsController NICHT einziehen
        // (controller.hide() ist ein No-Op). Heilung bringt nur der Stash-Impuls
        // (TaskbarRestash): ein kurzlebiges fokussierbares App-Fenster auf/zu stasht sie wieder
        // — ausgelöst bei Fokus-Rückkehr und IME-Flanke (siehe onWindowFocusChanged und der
        // Insets-Listener oben), seit kiosk-pflicht unabhaengig vom Kiosk-Zustand (C).
        // Dieser Wächter bleibt als günstige Absicherung für vom Fenster
        // kontrollierbare Fälle (z. B. transient eingeblendete Status-/Nav-Bar).
        // Nur aktiv, solange die App im Vordergrund ist.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive) {
                    delay(1000L)
                    if (lockdownActive) {
                        val visible = ViewCompat.getRootWindowInsets(window.decorView)
                            ?.isVisible(WindowInsetsCompat.Type.systemBars()) ?: false
                        if (visible) applySystemBars()
                    }
                }
            }
        }

        // N-3 (Kette kiosk-pflicht, Runde 2): Der Altschluessel kiosk_mode bleibt STEHEN —
        // er wird nirgends mehr gelesen, aber auch nicht geloescht. Befund B7 der Pruefer:
        // Das Loeschen hinterliess nach einem Rueckbau auf die Portal-0.9.1 (`install -r`)
        // einen leeren DataStore (0 Byte) und damit ein Geraet ohne Kiosk, weil die alte
        // Fassung den fehlenden Schluessel als AUS las. Ein stehender TRUE-Wert kostet
        // nichts und schliesst genau diese Downgrade-Falle. Hier nur noch Helligkeit
        // beobachten.
        lifecycleScope.launch {
            // Bildschirmhelligkeit (CEO-Beschluss 2026-06-07): Window-Brightness —
            // wirkt ohne WRITE_SETTINGS-Permission; im Kiosk-Betrieb ist die App
            // ohnehin permanent im Vordergrund. -1 = System/automatisch.
            settingsStore.data.collect { prefs ->
                val brightness = prefs[intPreferencesKey("screen_brightness")] ?: -1
                val lp = window.attributes
                lp.screenBrightness = if (brightness < 0)
                    android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                else brightness.coerceIn(5, 100) / 100f
                window.attributes = lp
            }
        }

        // Lockdown direkt beim Start anwenden (nicht erst bei Fokus-Rueckkehr).
        applyLockdown()

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            var showSplash by rememberSaveable { mutableStateOf(true) }

            // Theme-Quelle: themeMode-Pref (System/Dunkel/Hell), in Einstellungen gesetzt.
            val darkTheme = rememberThemeMode(this).isDark()

            DrainQTheme(darkTheme = darkTheme) {
                if (showSplash) {
                    SplashScreen(onDismiss = { showSplash = false })
                } else {
                    CompositionLocalProvider(
                        LocalWindowSizeClass provides windowSizeClass,
                        // Dialoge/Popups tragen die Legacy-Immersive-Flags nur bei aktivem
                        // Lockdown (kiosk-pflicht: DIRECT immer, ausser waehrend „App verlassen").
                        LocalKioskEnabled provides lockdownState.value
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            NavGraph()
                        }
                    }
                }
            }
        }
    }

    // Hardtasten der ONE kommen als F1–F8 (KeyCode 131–138) rein. Wir leiten sie auf
    // den HardwareKeyBus; die Inspektions-Leiste löst dieselbe Aktion aus wie der
    // positionsgleiche Softbutton (Belegung 1:1 wie Original-App).
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        val btn = HardwareKeyBus.fromKeyCode(keyCode)
        if (btn != null) {
            if (event == null || event.repeatCount == 0) HardwareKeyBus.emit(btn)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Bei Fokus-Rückkehr (Dialoge, IME, transientes Einwischen) den Lockdown-Zustand
        // erneut anwenden, damit Vollbild + LockTask erhalten bleiben.
        if (hasFocus) {
            applyLockdown()
            // Stash-Impuls nur nach tatsächlichem Fokusverlust (fremdes Systemfenster war da),
            // nicht bei jedem Fokus-Ereignis (z. B. App-Start). Seit kiosk-pflicht ohne
            // Kiosk-Tor (C): die Balken-Behandlung laeuft auch ausserhalb des Lockdowns.
            if (lostWindowFocus) scheduleRestashImpulse()
            lostWindowFocus = false
        } else {
            // Selbst-Retrigger hart sperren: Der Impuls entzieht der Activity selbst den Fokus.
            // Fällt der Fokusverlust in das eigene Impuls-Fenster, wird er gar nicht erst zum
            // Auslöser — sonst trüge sich der Impuls über seine eigene Fokus-Rückkehr endlos
            // selbst (der Debounce wäre dann die einzige Bremse und hinge an der Systemlast).
            if (taskbarRestash.isImpulseShowing) {
                Log.d(TAG, "Fokusverlust vom eigenen Impuls — kein Auslöser")
            } else {
                lostWindowFocus = true
            }
        }
    }

    override fun onDestroy() {
        // Ausstehendes Schließen des Impuls-Fensters zurücknehmen (sonst läuft es nach dem Abbau).
        if (::taskbarRestash.isInitialized) taskbarRestash.release()
        super.onDestroy()
    }

    /**
     * „App verlassen" (Kette kiosk-pflicht, 03.09.2026, Plan E5): einmalige Handlung, kein
     * Dauerzustand. Der Bediener landet auf der Systemoberflaeche; beim naechsten Start
     * ist der Kiosk wieder aktiv, weil lockdownActive in onCreate neu auf true faellt.
     * Die HOME-Rolle bleibt unangetastet.
     *
     * Rueckweg (Runde 3, M-4, neu gemessen — ersetzt die Runde-2-Aussage):
     * Auf dem Startbildschirm von unten nach oben wischen oeffnet die App-Uebersicht
     * des launcher3; dort steht „DrainQ ONE" und ein Tipp startet die App neu, der
     * Kiosk ist sofort wieder LOCKED. Zweimal hintereinander so gemessen
     * (belege/r3_m4_09..14: Drawer mit Symbol, danach mCurrentFocus=MainActivity,
     * mLockTaskModeState=LOCKED). Nur die Startseite zeigt kein Symbol — dieser Teil
     * der N-1a-Probe bleibt bestehen; die N-1a-Aussage, auch der Drawer biete keinen
     * Weg, ist durch die heutige Messung widerlegt.
     *
     * Ein-/Aus-Taste ist KEIN Rueckweg (PRUEFBERICHT_R2_A.md RA1.4/RA1.5 + eigene
     * Messung 04.09.2026): Kurzdruck ohne Wirkung; Langdruck instabil — einmal
     * Systemdienst-Absturz mit Laufzeit-Neustart (A), einmal Abschalt-Dialog des ROM
     * mit OK/CANCEL (eigene Messung, belege/r3_m4_06/07); OK darauf wuerde das Geraet
     * ausschalten und ist unbelegt. Der Bestaetigungstext (exit_app_confirm_hint)
     * beschreibt daher den Drawer-Weg, nicht die Taste.
     *
     * Reihenfolge (Plan E5): erst Sperre loesen und Leisten zeigen, dann Ziel starten,
     * zuletzt die eigene Task entfernen. Bleibt nach dem Filter kein Ziel (Messung M0:
     * normalerweise genau eines, launcher3), wird abgebrochen — der Kiosk bleibt aktiv
     * (lieber im Kiosk bleiben als halb verlassen).
     *
     * Der Prozess laeuft bewusst weiter (OneRemoteServer/OneVideoServer, OneApp) —
     * „verlassen" heisst fuer den Bediener: die Oberflaeche ist weg, nicht der Dienst.
     */
    fun leaveApp() {
        Log.i(TAG, "App verlassen angefordert")
        // Runde 5 (P-1, CEO-Entscheid 04.09.2026, Variante A): Bei laufender Aufzeichnung
        // wird der Ausstieg HIER verweigert — finishAndRemoveTask() disponiert den
        // InspectionScreen und dessen onDispose würde die Aufnahme über cancel() löschen
        // (Klickdurchgang 04.09.2026, Punkt 6: Datei vollständig verloren). Der Kiosk
        // bleibt dabei unverändert aktiv (lockdownState wird nicht angefasst).
        if (recordingBus.active.value) {
            Log.i(TAG, "App verlassen verweigert: Aufzeichnung läuft")
            Toast.makeText(this, LocalizationManager.getString("exit_app_blocked_recording"), Toast.LENGTH_LONG).show()
            return
        }
        lockdownState.value = false
        applyLockTask()
        applySystemBars()

        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val candidates = packageManager.queryIntentActivities(homeIntent, 0)
            .map { it.activityInfo.packageName to it.activityInfo.name }
        val target = LeaveAppTarget.choose(candidates, packageName)
        if (target == null) {
            Log.w(TAG, "App verlassen: kein HOME-Ziel gefunden (${candidates.size} Kandidaten) — Kiosk bleibt")
            Toast.makeText(this, LocalizationManager.getString("exit_app_no_target"), Toast.LENGTH_LONG).show()
            lockdownState.value = lockdownPlan.immersive
            applyLockdown()
            return
        }
        if (candidates.size > 2) {
            Log.i(TAG, "App verlassen: ${candidates.size} HOME-Kandidaten, gewaehlt: $target")
        }
        try {
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .setComponent(ComponentName(target.first, target.second))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Log.w(TAG, "App verlassen: Ziel $target nicht startbar — Kiosk bleibt", e)
            Toast.makeText(this, LocalizationManager.getString("exit_app_no_target"), Toast.LENGTH_LONG).show()
            lockdownState.value = lockdownPlan.immersive
            applyLockdown()
            return
        }
        finishAndRemoveTask()
    }

    /**
     * Setzt die Android-System-Bars je nach Lockdown-Zustand:
     * - Lockdown AN: System-Bars ausblenden, Wischen nur transient
     *   (BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE) → kein versehentliches Verlassen
     *   zum Android-Homescreen am Feldgerät (Feedback #5).
     * - Lockdown AUS (nur waehrend „App verlassen" bzw. im WIFI-Modus): System-Bars sichtbar.
     *
     * Hinweis: Die App-eigene Navigation (Bottom-Bar/Rail) bleibt in beiden Fällen sichtbar.
     *
     * Echtes Sperren von Home/Recents übernimmt applyLockTask() (LockTask, nur als
     * Geraeteeigentuemer — E4/R2). Provisionierung der ONE als Device-Owner:
     * docs/PROVISIONING_GOLDEN_IMAGE.md.
     */
    private fun applySystemBars() {
        // An das bereite Fenster posten: Beim Update-Neustart wird applySystemBars() ggf.
        // aufgerufen, bevor decorView bereit ist — ein direkter hide()-Aufruf verpufft dann.
        // Post stellt sicher, dass es nach dem Layout läuft.
        val decor = window.decorView
        decor.post {
            val controller = WindowInsetsControllerCompat(window, decor)
            if (lockdownActive) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
            // Zusätzlich die Legacy-Immersive-Flags der Original-App anwenden (eigentlicher
            // Träger gegen die launcher3-Taskbar; moderne API allein reicht auf der ONE nicht).
            applyLegacyImmersive()
        }
    }

    /**
     * Legacy-Immersive-Flags `5894` der Original-App auf das ACTIVITY-Fenster
     * (BaseActivity.hideBottomUIMenu, jadx Z.166–168). Ergänzt die moderne
     * WindowInsetsController-Logik in [applySystemBars]: Auf der ONE (RK3588 + launcher3) ist die
     * moderne API gegen die Gesten-Taskbar wirkungslos; diese Legacy-Flags ziehen die Leiste
     * tatsächlich ein. Bei Lockdown AUS auf SYSTEM_UI_FLAG_VISIBLE zurück.
     */
    private fun applyLegacyImmersive() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = if (lockdownActive) {
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE // = 5894
        } else {
            View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    /** Wendet den kompletten Lockdown-Zustand an: System-Bars + LockTask. */
    private fun applyLockdown() {
        applySystemBars()
        applyLockTask()
    }

    /**
     * Echter Kiosk via LockTask (B4/M14), seit kiosk-pflicht NUR als Geraeteeigentuemer (E4/R2):
     * - Lockdown AN + Device-Owner: eigenes Paket whitelisten + startLockTask() → Home/Recents/
     *   Wischen vollständig gesperrt.
     * - Lockdown AN ohne Device-Owner: KEIN startLockTask() — der System-Anpinn-Dialog ist am
     *   03.09.2026 als Vollbild-Falle gemessen worden (blockiert jede Bedienung, kommt nach
     *   jeder Fokus-Rueckkehr wieder; belege/ma2_anpinn_schleife.txt der Kette). Ohne Owner ist
     *   „Kiosk": Vollbild + Balken-Behandlung + HOME-Rolle.
     * - Lockdown AUS (nur „App verlassen"/WIFI): LockTask beenden, falls aktiv.
     * Robust gegen frühe Aufrufe (vor onResume): Fehler werden geloggt, onWindowFocusChanged
     * wendet den Zustand bei Fokus erneut an.
     */
    private fun applyLockTask() {
        val am = getSystemService(ActivityManager::class.java)
        val inLockTask = am?.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
        try {
            if (lockdownActive && lockdownPlan.lockTask) {
                if (!inLockTask) {
                    val dpm = getSystemService(DevicePolicyManager::class.java)
                    val admin = ComponentName(this, OneDeviceAdminReceiver::class.java)
                    dpm?.setLockTaskPackages(admin, arrayOf(packageName))
                    startLockTask()
                }
            } else if (!lockdownActive && inLockTask) {
                stopLockTask()
            }
        } catch (e: Exception) {
            Log.w(TAG, "applyLockTask failed (lockdown=$lockdownActive): ${e.message}")
        }
    }

    companion object {
        private const val TAG = "MainActivity"

        // Verzögerung zwischen Auslöser (Fokus-Rückkehr/IME-Flanke) und Stash-Impuls:
        // Der Unstash der Taskbar durch das System muss abgeschlossen sein, bevor der
        // Impuls sie wieder einzieht (am Gerät kalibriert, Kette taskbar-balken).
        private const val RESTASH_DELAY_MS = 250L
    }
}
