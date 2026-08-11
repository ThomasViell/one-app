package com.uip.oneapp

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.os.Bundle
import android.util.Log
import android.view.View
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
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.lifecycle.lifecycleScope
import com.uip.oneapp.bootstrap.OneDeviceAdminReceiver
import com.uip.oneapp.ui.components.LocalKioskEnabled
import com.uip.oneapp.ui.components.TaskbarRestash
import com.uip.oneapp.ui.hardware.HardwareKeyBus
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

class MainActivity : ComponentActivity() {

    // Kiosk-Modus: vom Setting gesteuert (Default AUS). Solange AUS, bleiben die
    // Android-System-Bars sichtbar — Entwicklung/Service kommt immer auf die
    // Android-Ebene. AN = Vollbild fürs Feldgerät (Feedback #5).
    // Compose-beobachtbar, damit Dialoge/Popups via LocalKioskEnabled reagieren (nur dann
    // tragen sie die Legacy-Immersive-Flags). Alle Zugriffe laufen auf dem Main-Thread
    // (Settings-Collector, Lifecycle-Wächter, Fokus-/UI-Callbacks).
    private val kioskState = mutableStateOf(false)
    private val kioskEnabled: Boolean get() = kioskState.value

    // Wächter: blendet die System-Bars bei aktivem Kiosk wieder aus, falls sie auftauchen
    // (Neustart nach Self-Update, transientes Einwischen, Systemdialog). Befund 0.4.1:
    // Nach dem Update kam die Leiste hoch und blieb, bis der Kiosk-Schalter neu gesetzt wurde.
    private val reHideBarsRunnable = Runnable { if (kioskEnabled) applySystemBars() }

    // Stash-Impuls (Kette taskbar-balken, 10.08.2026): Fremde Systemfenster (Power-Dialog,
    // Berechtigungsdialog, Leiste, IME) „unstashen" die launcher3-Taskbar; hide()/Flags heilen
    // nicht, ein App-Fenster-auf/zu heilt (siehe TaskbarRestash). Auslöser: Fokus-Rückkehr nach
    // FREMDEM Fokusverlust (Merker lostWindowFocus; der vom eigenen Impuls verursachte
    // Fokusverlust setzt ihn nicht — s. onWindowFocusChanged) + IME-Flanke (unten im
    // Insets-Listener). Der Impuls feuert verzögert, damit der Unstash des Systems abgeschlossen
    // ist, bevor er stasht, und wird bei stehender Tastatur unterdrückt (Eingabeschutz).
    private lateinit var taskbarRestash: TaskbarRestash
    private var lostWindowFocus = false
    private var imeWasVisible = false

    private fun scheduleRestashImpulse() {
        window.decorView.postDelayed({
            if (!kioskEnabled) return@postDelayed
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

        // Original-App-Technik (BaseActivity/BaseDialogFragment): Re-Hide-Listener auf der
        // Activity-decorView. Sobald irgendetwas die System-UI dieses Fensters sichtbar macht
        // (transientes Wischen, IME/Tastatur, fremde Insets), setzen wir bei aktivem Kiosk sofort
        // die Legacy-Immersive-Flags (5894) erneut. Die moderne WindowInsetsController-API ist
        // gegen die launcher3-Gesten-Taskbar wirkungslos — die Legacy-Flags sind der Träger.
        @Suppress("DEPRECATION")
        window.decorView.setOnSystemUiVisibilityChangeListener {
            if (kioskEnabled) applyLegacyImmersive()
        }

        // Kiosk-Härtung: Wird die System-Leiste sichtbar, obwohl Kiosk an ist, ziehen wir sie
        // verzögert wieder ein. Der Fokuswechsel allein greift beim Update-Neustart nicht,
        // weil der Kiosk-Wert erst asynchron geladen wird (Befund 0.4.1, ONE-Gerät).
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { v, insets ->
            if (kioskEnabled && insets.isVisible(WindowInsetsCompat.Type.systemBars())) {
                v.removeCallbacks(reHideBarsRunnable)
                v.postDelayed(reHideBarsRunnable, 1500L)
            }
            // IME-Flanke: Die Soft-Tastatur wechselt den Fensterfokus NICHT, unstasht die
            // Taskbar aber (Befund 0.4.1). Beim Schließen der Tastatur (sichtbar → unsichtbar)
            // einen Stash-Impuls auslösen — der Fokus-Auslöser unten greift hier nicht.
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            if (kioskEnabled && imeWasVisible && !imeVisible) scheduleRestashImpulse()
            imeWasVisible = imeVisible
            insets
        }

        // Backstop-Wächter: Manche Übergänge blenden eine System-Leiste ein, OHNE eine
        // Insets-Meldung an decorView zu schicken — der Listener oben feuert dann nicht.
        // Dieser Lebenszyklus-Wächter prüft bei aktivem Kiosk regelmäßig den tatsächlichen
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
        // Insets-Listener oben). Dieser Wächter bleibt als günstige Absicherung für vom Fenster
        // kontrollierbare Fälle (z. B. transient eingeblendete Status-/Nav-Bar).
        // Nur aktiv, solange die App im Vordergrund ist.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive) {
                    delay(1000L)
                    if (kioskEnabled) {
                        val visible = ViewCompat.getRootWindowInsets(window.decorView)
                            ?.isVisible(WindowInsetsCompat.Type.systemBars()) ?: false
                        if (visible) applySystemBars()
                        // Gesten-Taskbar dauerhaft aus: navigation_mode wird beim Boot von SystemUI
                        // wieder auf 2 gesetzt, nachdem MainActivity es früh auf 0 gesetzt hat —
                        // hier nachziehen (schreibt nur bei Abweichung, s. applyNavigationMode).
                        applyNavigationMode()
                    }
                }
            }
        }

        // Kiosk- und Helligkeits-Setting reaktiv beobachten und anwenden.
        lifecycleScope.launch {
            settingsStore.data.collect { prefs ->
                kioskState.value = prefs[booleanPreferencesKey("kiosk_mode")] ?: false
                applyKiosk()
                applyNavigationMode()
                // Bildschirmhelligkeit (CEO-Beschluss 2026-06-07): Window-Brightness —
                // wirkt ohne WRITE_SETTINGS-Permission; im Kiosk-Betrieb ist die App
                // ohnehin permanent im Vordergrund. -1 = System/automatisch.
                val brightness = prefs[androidx.datastore.preferences.core.intPreferencesKey("screen_brightness")] ?: -1
                val lp = window.attributes
                lp.screenBrightness = if (brightness < 0)
                    android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                else brightness.coerceIn(5, 100) / 100f
                window.attributes = lp
            }
        }

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
                        // Dialoge/Popups tragen die Legacy-Immersive-Flags nur bei aktivem Kiosk.
                        LocalKioskEnabled provides kioskState.value
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
        // Bei Fokus-Rückkehr (Dialoge, IME, transientes Einwischen) erneut anwenden,
        // damit der Kiosk-Vollbildzustand + LockTask erhalten bleiben. Bei Kiosk=AUS werden
        // die Bars wieder eingeblendet und LockTask beendet (siehe applyKiosk).
        if (hasFocus) {
            applyKiosk()
            applyNavigationMode()
            // Stash-Impuls nur nach tatsächlichem Fokusverlust (fremdes Systemfenster war da),
            // nicht bei jedem Fokus-Ereignis (z. B. App-Start).
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
     * Setzt die Android-System-Bars je nach Kiosk-Setting:
     * - Kiosk AN: System-Bars ausblenden, Wischen nur transient
     *   (BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE) → kein versehentliches Verlassen
     *   zum Android-Homescreen am Feldgerät (Feedback #5).
     * - Kiosk AUS (Default): System-Bars sichtbar → Entwicklung/Service kommt
     *   immer auf die Android-Ebene.
     *
     * Hinweis: Die App-eigene Navigation (Bottom-Bar/Rail) bleibt in beiden Fällen
     * sichtbar — der Kiosk-Schalter ist also auch bei AN über die Einstellungen
     * wieder erreichbar.
     *
     * Echtes Sperren von Home/Recents übernimmt applyLockTask() (LockTask): als
     * Device-Owner nahtlos, sonst Screen-Pinning-Fallback. Provisionierung der ONE als
     * Device-Owner: docs/PROVISIONING_GOLDEN_IMAGE.md.
     */
    private fun applySystemBars() {
        // An das bereite Fenster posten: Beim Update-Neustart wird applySystemBars() aus dem
        // asynchronen Settings-Collector aufgerufen, evtl. bevor decorView bereit ist — ein
        // direkter hide()-Aufruf verpufft dann. Post stellt sicher, dass es nach dem Layout läuft.
        val decor = window.decorView
        decor.post {
            val controller = WindowInsetsControllerCompat(window, decor)
            if (kioskEnabled) {
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
     * tatsächlich ein. Bei Kiosk AUS auf SYSTEM_UI_FLAG_VISIBLE zurück (Service/Entwicklung).
     */
    private fun applyLegacyImmersive() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = if (kioskEnabled) {
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

    /**
     * Schaltet die launcher3-Gesten-Taskbar (ITYPE_EXTRA_NAVIGATION_BAR) im Kiosk ab, indem
     * der System-Navigationsmodus auf 3-Button (navigation_mode=0) gesetzt wird — in diesem Modus
     * existiert die Gesten-Taskbar nicht; die 3-Button-Leiste selbst ist auf der ONE über
     * qemu.hw.mainkeys=1 bzw. persist.sys.navigationbar.enable=false unterdrückt.
     *
     * WARUM (On-Device-Befund 0.4.1, RK3588 + launcher3): Die Gesten-Taskbar wird von JEDEM
     * separaten Kindfenster (Compose-Dialog/-Popup, ExposedDropdown UND der Soft-Tastatur/IME)
     * beim Fenster-Übergang „unstashed" und lässt sich danach vom App-Fenster NICHT mehr einziehen
     * (das Fenster fordert sie laut dumpsys schon als unsichtbar an — controller.hide() ist ein
     * No-Op; auch Legacy-Immersive-Flags greifen nicht). In-Window-Overlays beseitigen die
     * Dialog-Auslöser, aber die unvermeidbare Tastatur bliebe ein Auslöser — daher dieser
     * System-Schalter als eigentliche, vollständige Lösung.
     *
     * navigation_mode ist NICHT reboot-persistent (OEM-Default = 2/Gesten), daher bei jedem
     * Start/Kiosk-Wechsel neu setzen. Benötigt WRITE_SECURE_SETTINGS (Provisionierung:
     * `adb shell pm grant com.uip.drainq.one android.permission.WRITE_SECURE_SETTINGS`).
     * Bei Kiosk AUS wird der Gesten-Modus wiederhergestellt (Service/Entwicklung).
     */
    private fun applyNavigationMode() {
        try {
            val target = if (kioskEnabled) 0 else 2 // 0 = 3-Button (keine Taskbar), 2 = Gesten
            val current = android.provider.Settings.Secure.getInt(contentResolver, "navigation_mode", 2)
            if (current != target) {
                android.provider.Settings.Secure.putInt(contentResolver, "navigation_mode", target)
                Log.d(TAG, "navigation_mode $current -> $target")
            }
        } catch (e: Exception) {
            Log.w(TAG, "navigation_mode nicht setzbar (WRITE_SECURE_SETTINGS fehlt?): ${e.message}")
        }
    }

    /** Wendet den kompletten Kiosk-Zustand an: System-Bars + LockTask. */
    private fun applyKiosk() {
        applySystemBars()
        applyLockTask()
    }

    /**
     * Echter Kiosk via LockTask (B4/M14):
     * - Kiosk AN + Device-Owner: eigenes Paket whitelisten + startLockTask() → Home/Recents/
     *   Wischen vollständig gesperrt. Ohne Device-Owner startet startLockTask() das normale
     *   Screen-Pinning (Fallback, manuell verlassbar).
     * - Kiosk AUS: LockTask beenden, falls aktiv.
     * Robust gegen frühe Aufrufe (vor onResume): Fehler werden geloggt, onWindowFocusChanged
     * wendet den Zustand bei Fokus erneut an.
     */
    private fun applyLockTask() {
        val am = getSystemService(ActivityManager::class.java)
        val inLockTask = am?.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
        try {
            if (kioskEnabled) {
                if (!inLockTask) {
                    val dpm = getSystemService(DevicePolicyManager::class.java)
                    if (dpm?.isDeviceOwnerApp(packageName) == true) {
                        val admin = ComponentName(this, OneDeviceAdminReceiver::class.java)
                        dpm.setLockTaskPackages(admin, arrayOf(packageName))
                    }
                    startLockTask()
                }
            } else if (inLockTask) {
                stopLockTask()
            }
        } catch (e: Exception) {
            Log.w(TAG, "applyLockTask failed (kiosk=$kioskEnabled): ${e.message}")
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
