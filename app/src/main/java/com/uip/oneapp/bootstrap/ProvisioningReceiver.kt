package com.uip.oneapp.bootstrap

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.uip.oneapp.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Werkseinrichtung (30.07.2026, `tools/werkseinrichtung/`): startet die App nach der
 * Einrichtung per `adb shell am broadcast -n com.uip.drainq.one/.bootstrap.ProvisioningReceiver`
 * neu — ohne Koordinaten-Tap auf die Compose-Oberfläche, der pro Displaygröße/Sprache neu
 * vermessen werden müsste.
 *
 * Kette kiosk-pflicht, 03.09.2026 (E1): Der Receiver schreibt KEINEN DataStore-Schluessel mehr —
 * der fruehere kiosk_mode-Schreibzugriff entfaellt, weil der Kiosk im DIRECT-Modus Pflicht und
 * Konstante ist (siehe [com.uip.oneapp.kiosk.KioskPolicy]). Der Receiver bleibt als reiner
 * Start-Ausloeser der Werkseinrichtung bestehen.
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
                Log.i(TAG, "Werkseinrichtung: App-Neustart ausgeloest (Kiosk ist Pflicht, kein Schalter)")
                appContext.startActivity(
                    Intent(appContext, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
            } catch (e: Exception) {
                Log.w(TAG, "App-Neustart per Werkseinrichtung fehlgeschlagen", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "ProvisioningReceiver"
        const val ACTION_PROVISION_KIOSK_ON = "com.uip.drainq.one.action.PROVISION_KIOSK_ON"
    }
}
