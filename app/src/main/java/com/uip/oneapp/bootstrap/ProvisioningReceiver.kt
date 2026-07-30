package com.uip.oneapp.bootstrap

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.uip.oneapp.MainActivity
import com.uip.oneapp.ui.screens.settings.settingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Werkseinrichtung (30.07.2026, `tools/werkseinrichtung/`): schaltet den Kiosk-Modus per
 * `adb shell am broadcast -n com.uip.drainq.one/.bootstrap.ProvisioningReceiver` ein — ohne
 * Koordinaten-Tap auf die Compose-Oberfläche, der pro Displaygröße/Sprache neu vermessen werden
 * müsste. Schreibt denselben DataStore-Schlüssel wie der Kiosk-Schalter in den Einstellungen
 * ([com.uip.oneapp.ui.screens.settings.SettingsViewModel]), startet danach die App neu, damit
 * [MainActivity.applyLockTask] den Zustand sofort anwendet.
 *
 * `exported=false`: Drittapps auf dem Gerät erreichen die Komponente nicht. adb (Shell/root)
 * kann exported=false-Komponenten trotzdem über den expliziten Komponentennamen ansprechen —
 * das ist eine dokumentierte, gezielt genutzte Eigenschaft von ADB, keine Lücke.
 */
class ProvisioningReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PROVISION_KIOSK_ON) return
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                appContext.settingsStore.edit { it[KEY_KIOSK_MODE] = true }
                Log.i(TAG, "Kiosk-Modus per Werkseinrichtung aktiviert")
                appContext.startActivity(
                    Intent(appContext, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
            } catch (e: Exception) {
                Log.w(TAG, "Kiosk-Aktivierung per Werkseinrichtung fehlgeschlagen", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "ProvisioningReceiver"
        const val ACTION_PROVISION_KIOSK_ON = "com.uip.drainq.one.action.PROVISION_KIOSK_ON"
        val KEY_KIOSK_MODE = booleanPreferencesKey("kiosk_mode")
    }
}
