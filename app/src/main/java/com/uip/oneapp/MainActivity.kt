package com.uip.oneapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
// import androidx.activity.enableEdgeToEdge  // raus: reserviert Inset-Bereiche
                                              // auch wenn System-Bars versteckt
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
import android.view.KeyEvent
import com.uip.oneapp.hardware.HardwareKeyBus
import com.uip.oneapp.ui.navigation.NavGraph
import com.uip.oneapp.ui.screens.splash.SplashScreen
import com.uip.oneapp.ui.theme.OneAppTheme
import com.uip.oneapp.ui.utils.LocalWindowSizeClass

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()

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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Falls Android die System-Bars zeigt (z.B. nach Dialog-Schliessen oder
        // Wisch-Geste), beim Re-Focus wieder verstecken.
        if (hasFocus) hideSystemBars()
    }

    /**
     * Immersive-Mode: System-Bars (Status + Navigation) komplett verstecken.
     * Wischen vom Bildschirmrand zeigt sie kurz transient wieder.
     * Macht DrainQ.ONE zur echten Vollbild-Inspektions-App â€” ohne Android-UI-
     * Elemente die das OSD verdecken koennten.
     */
    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val long = event?.isLongPress == true
        val action = when (keyCode) {
            131 -> if (long) HardwareKeyBus.Action.LIGHT_LONG else HardwareKeyBus.Action.LIGHT
            132 -> if (long) HardwareKeyBus.Action.SONDE_LONG else HardwareKeyBus.Action.SONDE
            133 -> HardwareKeyBus.Action.REC_START
            134 -> HardwareKeyBus.Action.REC_STOP
            135 -> HardwareKeyBus.Action.PHOTO
            136 -> HardwareKeyBus.Action.GALLERY
            137 -> HardwareKeyBus.Action.DAY_NIGHT
            138 -> HardwareKeyBus.Action.SETTINGS
            else -> null
        }
        return if (action != null) {
            HardwareKeyBus.emit(action)
            true
        } else super.onKeyDown(keyCode, event)
    }
}

