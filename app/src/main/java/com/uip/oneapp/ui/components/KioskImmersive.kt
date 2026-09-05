package com.uip.oneapp.ui.components

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/**
 * Ist der Lockdown aktiv? Wird von [com.uip.oneapp.MainActivity] aus dem `lockdownActive`-Zustand
 * gespeist (Kette kiosk-pflicht, 03.09.2026: im DIRECT-Modus immer true, ausser waehrend
 * „App verlassen"; der fruehere kiosk_mode-Schalter entfaellt). Dialoge/Popups blenden die
 * System-/Taskbar NUR bei aktivem Lockdown aus — sonst bleiben die Android-Leisten sichtbar
 * (vgl. MainActivity.applySystemBars).
 */
val LocalKioskEnabled = staticCompositionLocalOf { false }

/**
 * Legacy-Immersive-Flags `5894` der Original-App auf JEDES separate Compose-Fenster
 * (Dialog / Popup / Dropdown). Als erste Zeile in den Content jedes Dialogs/Popups setzen.
 *
 * WARUM (On-Device-Befund 0.4.1, ONE/RK3588 + launcher3): Die „untere Leiste" ist die
 * launcher3-Gesten-Taskbar (ITYPE_EXTRA_NAVIGATION_BAR). Jedes NEUE Fenster „unstasht" sie,
 * und die moderne `WindowInsetsController`-API kann sie danach NICHT mehr einziehen
 * (controller.hide() ist ein No-Op — per dumpsys belegt). Die Original-App
 * (`com.bominwell.minipush`) löst das, indem JEDES Fenster sich über die Legacy-Flags SELBST
 * immersiv hält:
 *
 *   BaseDialogFragment.hideBottomUIMenu() (jadx Z.157–165):
 *     decorView.setSystemUiVisibility(5894)
 *     + OnSystemUiVisibilityChangeListener, der bei JEDER Sichtbarkeitsänderung erneut 5894 setzt.
 *   BaseActivity.hideBottomUIMenu() (jadx Z.166–168): setSystemUiVisibility(5894).
 *
 * Genau diese Technik wird hier auf Compose übertragen. Das Re-Hide via Listener fängt auch das
 * Einblenden durch die Soft-Tastatur (IME) im selben Fenster ab — der zuletzt brechende Fall.
 *
 * Fundstellen: C:\Projekte\one-revers\decoded\jadx-v130\sources\com\bominwell\robot\base\
 *   BaseDialogFragment.java (Z.118–165), BaseActivity.java (Z.166–168).
 */
@Composable
fun HideSystemBarsInDialog() {
    if (!LocalKioskEnabled.current) return
    val view = LocalView.current
    DisposableEffect(view) {
        // Dialog → eigenes Fenster über DialogWindowProvider; Popup/Dropdown haben kein
        // erreichbares Window-Objekt → dann direkt auf der rootView des Popup-Fensters arbeiten.
        val decor: View = (view.parent as? DialogWindowProvider)?.window?.decorView ?: view.rootView

        @Suppress("DEPRECATION")
        val immersive = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or       // 4096
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or                    //    2
            View.SYSTEM_UI_FLAG_FULLSCREEN or                         //    4
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or             //  512
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or                  // 1024
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE                         //  256  = 5894

        @Suppress("DEPRECATION")
        decor.systemUiVisibility = immersive
        @Suppress("DEPRECATION")
        val listener = View.OnSystemUiVisibilityChangeListener {
            // Bei JEDER Sichtbarkeitsänderung (Tastatur, transientes Wischen, fremde Insets)
            // sofort wieder immersiv setzen — hält dieses Fenster selbst streifenfrei.
            @Suppress("DEPRECATION")
            decor.systemUiVisibility = immersive
        }
        @Suppress("DEPRECATION")
        decor.setOnSystemUiVisibilityChangeListener(listener)

        onDispose {
            @Suppress("DEPRECATION")
            decor.setOnSystemUiVisibilityChangeListener(null)
        }
    }
}
