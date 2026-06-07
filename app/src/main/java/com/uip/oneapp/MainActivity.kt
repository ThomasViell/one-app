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

        // Kiosk- und Helligkeits-Setting reaktiv beobachten und anwenden.
        lifecycleScope.launch {
            settingsStore.data.collect { prefs ->
                kioskEnabled = prefs[booleanPreferencesKey("kiosk_mode")] ?: false
                applyKiosk()
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
        if (hasFocus) applyKiosk()
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
