package com.uip.oneapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
// import androidx.activity.enableEdgeToEdge  // raus: reserviert Inset-Bereiche
                                              // auch wenn System-Bars versteckt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
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

    companion object {
        // Wird auf true gesetzt sobald der einmalige Self-Restart in diesem Process
        // erfolgt ist. Verhindert eine Restart-Endlosschleife.
        @Volatile private var hidebarRestartDone = false
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        claimTopAppProperty()

        // sys.status.hidebar_enable muss true sein, damit das Bominwell-ROM den
        // ScreenDecorOverlayBottom-Balken versteckt. Die Property ist nicht
        // persistent — nach Boot ist sie wieder false. SystemUI evaluiert sie
        // beim ersten Activity-Start direkt nach Boot bereits gezeichnet zu spaet;
        // unser setprop kommt zwar an, aber SystemUI prueft die Property nur beim
        // App-Lifecycle-Wechsel erneut. Daher: Wenn die Property beim Eintreten in
        // onCreate noch false war, setzen wir sie und triggern dann einen einmaligen
        // App-Restart -> SystemUI re-evaluiert beim neuen Activity-Resume.
        val hidebarWasAlreadyTrue = isBominwellDecorBarHidden()
        hideBominwellDecorBar()
        if (!hidebarWasAlreadyTrue && !hidebarRestartDone) {
            hidebarRestartDone = true
            android.util.Log.i("MainActivity", "First start after boot: restarting to apply hidebar property")
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(
                    android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                        android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                )
                startActivity(intent)
                finishAndRemoveTask()
                return
            }
        }

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
                            color = Color.Black  // pures Schwarz statt Theme-Background
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
     * REVERSE-ENGINEERED aus Bominwell MiniPush V1.3.0 (com.bominwell.robot.ui.utils.SystemPropertyHelper):
     * Das Bominwell-RK3588-Custom-ROM unterdrueckt LAYOUT_INSET_DECOR (den ~70px grauen Balken
     * am unteren Display-Rand) NUR fuer die App, deren Package-Name in der System-Property
     * `persist.sys.top_app` steht. MiniPush setzt diese Property auf sich selbst — deshalb
     * sieht MiniPush kein grauer Balken, DrainQ.ONE schon.
     */
    private fun claimTopAppProperty() {
        setSystemProperty("persist.sys.top_app", packageName)
    }

    /**
     * Bominwell-RK3588-spezifische Property zum Abschalten des `ScreenDecorOverlayBottom`-
     * SystemUI-Layers (35 px schwarzer Balken am unteren Display-Rand, plus 87 px Taskbar).
     * Property ist NICHT persistent (kein `persist.*`-Prefix), muss bei jedem App-Start neu
     * gesetzt werden. Da DrainQ.ONE der HOME-Launcher ist, passiert das direkt nach Boot
     * bevor der User den Balken zu Gesicht bekommt.
     *
     * Reverse-engineered durch Auswertung von:
     *  - `getprop | grep hidebar` → `sys.status.hidebar_enable=false` als ROM-Default
     *  - `dumpsys window windows` zeigt Window 'ScreenDecorOverlayBottom' (1920x35 @ y=1165)
     *    als SystemUI-eigenes Layer mit IS_ROUNDED_CORNERS_OVERLAY-Flag.
     */
    private fun hideBominwellDecorBar() {
        setSystemProperty("sys.status.hidebar_enable", "true")
    }

    /**
     * Liest den aktuellen Wert der hidebar-Property. True heisst: in diesem Process-
     * Lifecycle wurde sie bereits gesetzt (entweder von uns oder einem frueheren
     * App-Start). Wird im onCreate-Lifecycle genutzt, um zu entscheiden ob ein
     * einmaliger Self-Restart noetig ist.
     */
    private fun isBominwellDecorBarHidden(): Boolean {
        // (1) Reflection auf SystemProperties.get — geht ohne Sonderrechte
        try {
            val sysProps = Class.forName("android.os.SystemProperties")
            val getMethod = sysProps.getMethod("get", String::class.java, String::class.java)
            val value = getMethod.invoke(null, "sys.status.hidebar_enable", "false") as? String
            return value == "true"
        } catch (e: Throwable) {
            android.util.Log.w("MainActivity", "isBominwellDecorBarHidden: reflection failed: ${e.message}")
        }
        // (2) getprop als Subprocess
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("getprop", "sys.status.hidebar_enable"))
            val value = p.inputStream.bufferedReader().readText().trim()
            p.waitFor()
            value == "true"
        } catch (e: Throwable) {
            android.util.Log.w("MainActivity", "isBominwellDecorBarHidden: getprop failed: ${e.message}")
            false
        }
    }

    /**
     * Setzt eine Android-System-Property ueber drei Fallbacks:
     *  1) Reflection auf android.os.SystemProperties.set() — funktioniert nur mit
     *     Plattform-Cert (zukuenftiges Ziel, Task #36).
     *  2) Runtime.exec("setprop ...") — auf dem ONE-Tablet als adbd-root oft moeglich.
     *  3) Runtime.exec("su -c setprop ...") — Fallback falls Root da ist.
     */
    private fun setSystemProperty(key: String, value: String) {
        // (1) Reflection — geht nur mit Plattform-Cert
        try {
            val sysProps = Class.forName("android.os.SystemProperties")
            val setMethod = sysProps.getMethod("set", String::class.java, String::class.java)
            setMethod.invoke(null, key, value)
            android.util.Log.i("MainActivity", "setSystemProperty($key=$value): SystemProperties.set OK")
            return
        } catch (e: Throwable) {
            android.util.Log.w("MainActivity", "setSystemProperty($key): reflection failed: ${e.message}")
        }
        // (2) setprop ohne su
        try {
            val p = Runtime.getRuntime().exec(arrayOf("setprop", key, value))
            val rc = p.waitFor()
            android.util.Log.i("MainActivity", "setSystemProperty($key=$value): setprop rc=$rc")
            if (rc == 0) return
        } catch (e: Throwable) {
            android.util.Log.w("MainActivity", "setSystemProperty($key): setprop direct failed: ${e.message}")
        }
        // (3) su -c setprop
        try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "setprop $key $value"))
            val rc = p.waitFor()
            android.util.Log.i("MainActivity", "setSystemProperty($key=$value): su setprop rc=$rc")
        } catch (e: Throwable) {
            android.util.Log.w("MainActivity", "setSystemProperty($key): su setprop failed: ${e.message}")
        }
    }

    /**
     * Immersive-Mode: System-Bars (Status + Navigation) komplett verstecken.
     * Wischen vom Bildschirmrand zeigt sie kurz transient wieder.
     * Macht DrainQ.ONE zur echten Vollbild-Inspektions-App â€” ohne Android-UI-
     * Elemente die das OSD verdecken koennten.
     */
    private fun hideSystemBars() {
        // Display-Cutout-Mode: App nutzt die ganze Bildschirmflaeche.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        // BRUTE-FORCE: LAYOUT_INSET_DECOR Flag direkt clearen — wird vom System
        // hartnaeckig gesetzt obwohl wir es nicht anfordern, und reserviert
        // 60-80px am unteren Display-Rand. FLAG_LAYOUT_INSET_DECOR = 0x00010000.
        val params = window.attributes
        @Suppress("DEPRECATION")
        val flagInsetDecor = android.view.WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
        params.flags = params.flags and flagInsetDecor.inv()
        window.attributes = params

        // Deprecated systemUiVisibility-Flags — auf RK3588-Bominwell-ROM die
        // einzigen die wirklich greifen. Plus LAYOUT_NO_LIMITS für volle Höhe.
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
            or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        // Zusaetzlich FLAG_LAYOUT_NO_LIMITS: App-Window erstreckt sich ueber
        // die normalen Display-Grenzen hinaus, ignoriert reservierte Bereiche.
        @Suppress("DEPRECATION")
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        // BOMINWELL-TRICK aus MiniPush.MainActivity.setupEnhancedKeyboardListener():
        // systemUiVisibility=5894 direkt ins WindowManager.LayoutParams Hidden-Field
        // schreiben (per Reflection). Wert 0x1706 = LOW_PROFILE | HIDE_NAVIGATION |
        // FULLSCREEN | LAYOUT_STABLE | LAYOUT_HIDE_NAVIGATION | LAYOUT_FULLSCREEN |
        // IMMERSIVE_STICKY. Macht das Bominwell-ROM offenbar zufrieden, auch ohne
        // dass wir die Window-API "richtig" aufrufen.
        try {
            val attrs = window.attributes
            val field = attrs.javaClass.getDeclaredField("systemUiVisibility")
            field.isAccessible = true
            field.setInt(attrs, 5894)
            window.attributes = attrs
        } catch (e: Throwable) {
            android.util.Log.w("MainActivity", "systemUiVisibility reflection failed: ${e.message}")
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val long = event?.isLongPress == true
        // Pure-Cinema-Mapping:
        //  131 Licht, 132 Sonde, 133 REC-Toggle, 134 Foto, 135 Galerie,
        //  136 Tag/Nacht, 137 Schaden, 138 Settings
        val action = when (keyCode) {
            131 -> if (long) HardwareKeyBus.Action.LIGHT_LONG else HardwareKeyBus.Action.LIGHT
            132 -> if (long) HardwareKeyBus.Action.SONDE_LONG else HardwareKeyBus.Action.SONDE
            133 -> HardwareKeyBus.Action.REC_TOGGLE
            134 -> HardwareKeyBus.Action.PHOTO
            135 -> HardwareKeyBus.Action.GALLERY
            136 -> HardwareKeyBus.Action.DAY_NIGHT
            137 -> HardwareKeyBus.Action.DAMAGE
            138 -> HardwareKeyBus.Action.SETTINGS
            else -> null
        }
        return if (action != null) {
            HardwareKeyBus.emit(action)
            true
        } else super.onKeyDown(keyCode, event)
    }
}

