package com.uip.oneapp

import android.os.Bundle
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.lifecycle.lifecycleScope
import com.uip.oneapp.ui.hardware.HardwareKeyBus
import com.uip.oneapp.ui.navigation.NavGraph
import com.uip.oneapp.ui.screens.settings.settingsStore
import com.uip.oneapp.ui.screens.splash.SplashScreen
import com.uip.oneapp.ui.theme.OneAppTheme
import com.uip.oneapp.ui.utils.LocalWindowSizeClass
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // Kiosk-Modus: vom Setting gesteuert (Default AUS). Solange AUS, bleiben die
    // Android-System-Bars sichtbar — Entwicklung/Service kommt immer auf die
    // Android-Ebene. AN = Vollbild fürs Feldgerät (Feedback #5).
    @Volatile private var kioskEnabled = false

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Kiosk-Setting reaktiv beobachten und System-Bars entsprechend setzen.
        lifecycleScope.launch {
            settingsStore.data.collect { prefs ->
                kioskEnabled = prefs[booleanPreferencesKey("kiosk_mode")] ?: false
                applySystemBars()
            }
        }

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            var showSplash by rememberSaveable { mutableStateOf(true) }

            if (showSplash) {
                SplashScreen(onDismiss = { showSplash = false })
            } else {
                OneAppTheme {
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
        // damit der Kiosk-Vollbildzustand erhalten bleibt. Bei Kiosk=AUS werden die
        // Bars hier wieder eingeblendet (siehe applySystemBars).
        if (hasFocus) applySystemBars()
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
     * Echtes Screen-Pinning/LockTask (Home/Recents komplett sperren) erfordert
     * Device-Owner-Provisionierung der ONE per ADB — separater Ops-Schritt, siehe
     * FEEDBACK_Jakob_2026-06-02_Analyse.md (Querschnitt B).
     */
    private fun applySystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
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
