package com.uip.oneapp.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import android.widget.Toast
import com.uip.oneapp.ui.localization.LocalizationManager

/**
 * Empfängt die PackageInstaller-Session-Status der Self-Update-Installation (B3).
 *
 * Ohne diesen Receiver lief die Installation nach dem Download ins Leere: `session.commit()`
 * liefert das Ergebnis — insbesondere STATUS_PENDING_USER_ACTION mit dem System-Bestätigungs-
 * Intent — ausschließlich an den ACTION_INSTALL_STATUS-Broadcast. Wird der nicht empfangen,
 * erscheint nie der Installdialog und das Update wird nie installiert.
 *
 * Im Manifest auf [UpdateInstaller.ACTION_INSTALL_STATUS] registriert (exported=false; der
 * PendingIntent adressiert via setPackage gezielt das eigene Paket).
 */
class UpdateInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Zwingend (non-silent Install): den vom System gelieferten Bestätigungs-Intent starten.
                val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                }
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(confirm) }
                        .onFailure { Log.e(TAG, "Installdialog konnte nicht gestartet werden", it) }
                } else {
                    Log.w(TAG, "STATUS_PENDING_USER_ACTION ohne EXTRA_INTENT")
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Log.i(TAG, "Self-Update erfolgreich installiert")
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Log.w(TAG, "Self-Update-Installation fehlgeschlagen: status=$status msg=$msg")
                runCatching {
                    Toast.makeText(
                        context,
                        LocalizationManager.getString("update_error_install_failed"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    companion object { private const val TAG = "UpdateInstallReceiver" }
}
