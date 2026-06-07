package com.uip.oneapp

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.os.Bundle
import android.util.Log
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
    @Volatile private var kioskEnabled = false

    // Wächter: blendet die System-Bars bei aktivem Kiosk wieder aus, falls sie auftauchen
    // (Neustart nach Self-Update, transientes Einwischen, Systemdialog). Befund 0.4.1:
    // Nach dem Update kam die Leiste hoch und blieb, bis der Kiosk-Schalter neu gesetzt wurde.
    private val reHideBarsRunnable = Runnable { if (kioskEnabled) applySystemBars() }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Kiosk-Härtung: Wird die System-Leiste sichtbar, obwohl Kiosk an ist, ziehen wir sie
        // verzögert wieder ein. Der Fokuswechsel allein greift beim Update-Neustart nicht,
        // weil der Kiosk-Wert erst asynchron geladen wird (Befund 0.4.1, ONE-Gerät).
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { v, insets ->
            if (kioskEnabled && insets.isVisible(WindowInsetsCompat.Type.systemBars())) {
                v.removeCallbacks(reHideBarsRunnable)
                v.postDelayed(reHideBarsRunnable, 1500L)
            }
            insets
        }

        // Backstop-Wächter: Manche Übergänge blenden eine System-Leiste ein, OHNE eine
        // Insets-Meldung an decorView zu schicken — der Listener oben feuert dann nicht.
        // Dieser Lebenszyklus-Wächter prüft bei aktivem Kiosk regelmäßig den tatsächlichen
        // Sichtbarkeitszustand und zieht eine vom App-Fenster kontrollierbare Leiste wieder ein.
        // WICHTIG (On-Device-Befund 0.4.1, ONE/RK3588 + launcher3): Die eigentliche
        // „Navigationsleiste" der ONE ist die launcher3-System-Taskbar (ITYPE_EXTRA_NAVIGATION_BAR).
        // Sie wird von jedem SEPARATEN Fenster (Compose-Dialog/-Popup) „unstashed" und lässt sich
        // danach per WindowInsetsController NICHT mehr einziehen (das App-Fenster fordert sie laut
        // dumpsys längst als unsichtbar an — controller.hide() ist dann ein No-Op). Deshalb ist der
        // eigentliche Fix das Vermeiden zusätzlicher Fenster: der Aufnahme-Dialog ist jetzt ein
        // In-Window-Overlay (siehe InspectionScreen). Dieser Wächter bleibt als günstige Absicherung
        // für vom Fenster kontrollierbare Fälle (z. B. transient eingeblendete Status-/Nav-Bar).
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
                kioskEnabled = prefs[booleanPreferencesKey("kiosk_mode")] ?: false
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
                    CompositionLocalProvider(LocalWindowSizeClass provides windowSizeClass) {
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
        }
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
    }
}
