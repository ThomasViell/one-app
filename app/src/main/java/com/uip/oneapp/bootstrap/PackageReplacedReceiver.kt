package com.uip.oneapp.bootstrap

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.uip.oneapp.MainActivity

/**
 * Startet die App nach einem Self-Update automatisch neu.
 *
 * Hintergrund (Self-Update-Test 2026-06-07): Beim Update über den PackageInstaller
 * endet der LockTask-Kiosk, Android zeigt den System-Sperrbildschirm (Uhr) und der
 * Bediener musste das Gerät manuell neu starten. MY_PACKAGE_REPLACED wird an die
 * frisch installierte Version gesendet — wir starten die MainActivity direkt wieder.
 *
 * Background-Activity-Start ist hier erlaubt, weil die provisionierte ONE als
 * Device Owner läuft (BAL-Ausnahme); zusätzlich ist die App HOME-Activity.
 */
class PackageReplacedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        Log.i(TAG, "Self-Update abgeschlossen — starte App neu")
        try {
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        } catch (e: Exception) {
            // Ohne Device-Owner (z.B. Sideload-Testgerät) kann der Background-Start
            // blockiert sein — dann bringt der nächste HOME-Druck die App zurück.
            Log.w(TAG, "Auto-Restart nach Update nicht möglich", e)
        }
    }

    private companion object {
        const val TAG = "PackageReplacedReceiver"
    }
}
