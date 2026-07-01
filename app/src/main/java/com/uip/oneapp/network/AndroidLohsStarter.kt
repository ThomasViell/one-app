package com.uip.oneapp.network

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * **Öffentlicher LocalOnlyHotspot-Starter** (Dual-Modus, Welle 3a) — bringt den Tablet-Hotspot
 * über die **öffentliche** API [WifiManager.startLocalOnlyHotspot] hoch (Android 8+, **kein**
 * System-Privileg). Die Plattform generiert SSID + WPA2-Passphrase und gibt sie über die
 * Reservation zurück; wir lesen sie aus und reichen sie als WIFI-QR weiter.
 *
 * **Rolle im Dual-Modus:** der Rückfall-Pfad des [FallbackHotspotStarter]. Der bevorzugte Pfad
 * ist der gebrandete, standortfreie [AndroidSoftApStarter]; scheitert der mangels
 * Werks-Image-Privileg ([REASON_PRIVILEGE]), übernimmt dieser Starter, sodass der Hotspot auch
 * auf einem **nicht privilegierten** Image (z. B. Louis' Kiosk-ONE) hochkommt — eine einzige APK
 * für ONE wie Tablet.
 *
 * **Standort-Voraussetzung:** [WifiManager.startLocalOnlyHotspot] verlangt
 * `ACCESS_FINE_LOCATION` **und** aktivierte Standortdienste. Auf der Kiosk-ONE wird beides beim
 * App-Start automatisch hergestellt — aber **nur**, wenn die App Geräteeigentümer (Device-Owner)
 * ist (s. `DeviceOwnerLocationProvisioner`). Fehlt das (kein Device-Owner, Standort verweigert),
 * wirft die Plattform [SecurityException] → der Starter meldet sauber `onFailed` statt zu crashen.
 *
 * Die heikle Zustandslogik (Idempotenz, Gate, Generationen) liegt Android-frei im
 * [AccessPointController]; dieser Starter ist die dünne, Android-/Reservation-behaftete Schicht
 * (Geräte-Test).
 */
class AndroidLohsStarter(context: Context) : HotspotStarter {

    private companion object { const val TAG = "AndroidLohsStarter" }

    private val appContext = context.applicationContext

    override fun start(
        onActive: (ssid: String, passphrase: String) -> Unit,
        onFailed: (reason: String) -> Unit,
        onStopped: () -> Unit,
    ): HotspotSession {
        val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wifi == null) {
            onFailed("no-wifi-service")
            return HotspotSession { }
        }

        // Reservation thread-sicher festhalten (Callbacks laufen auf dem Main-Looper).
        val reservationHolder = arrayOfNulls<WifiManager.LocalOnlyHotspotReservation>(1)
        // Wurde stop() gerufen, BEVOR der (asynchrone) onStarted ankam? Dann gibt es noch keine
        // Reservation zum Schließen — wir merken uns den Abbruch und schließen sie nach, sobald
        // sie eintrifft (sonst bliebe der Hotspot „hängen", da der einzige Handle verloren ginge).
        val cancelled = AtomicBoolean(false)

        val callback = object : WifiManager.LocalOnlyHotspotCallback() {
            override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation) {
                if (cancelled.get()) {
                    Log.i(TAG, "Hotspot hochgefahren, aber bereits abgebrochen — Reservation schließen")
                    try { reservation.close() } catch (_: Throwable) {}
                    onStopped()
                    return
                }
                val (ssid, passphrase) = readCredentials(reservation)
                if (!ssid.isNullOrEmpty()) {
                    reservationHolder[0] = reservation
                    Log.i(TAG, "Hotspot aktiv (SSID=$ssid)") // Passphrase NICHT loggen.
                    onActive(ssid, passphrase.orEmpty())
                } else {
                    // Hotspot LÄUFT an dieser Stelle bereits — Reservation sofort schließen,
                    // sonst bliebe ein offener Hotspot ohne Handle zurück (onFailed räumt im
                    // Controller nur die Session-Referenz, ruft aber kein stop()).
                    Log.w(TAG, "Hotspot gestartet, aber keine Zugangsdaten lesbar — schließe wieder")
                    try { reservation.close() } catch (_: Throwable) {}
                    onFailed("no-credentials")
                }
            }

            override fun onFailed(reason: Int) {
                Log.w(TAG, "Hotspot-Start fehlgeschlagen: ${failureName(reason)}")
                onFailed(failureName(reason))
            }

            override fun onStopped() {
                Log.i(TAG, "Hotspot von der Plattform gestoppt")
                onStopped()
            }
        }

        return try {
            Log.i(TAG, "Starte LocalOnlyHotspot …")
            wifi.startLocalOnlyHotspot(callback, Handler(Looper.getMainLooper()))
            HotspotSession {
                cancelled.set(true)
                try { reservationHolder[0]?.close() } catch (_: Throwable) {}
                reservationHolder[0] = null
            }
        } catch (e: Throwable) {
            // SecurityException (fehlende Standortberechtigung) / IllegalStateException etc.
            Log.w(TAG, "startLocalOnlyHotspot warf ${e.javaClass.simpleName}: ${e.message}")
            onFailed(e.javaClass.simpleName)
            HotspotSession { }
        }
    }

    /** Liest SSID + Passphrase aus der Reservation — API-Level-abhängig. */
    @Suppress("DEPRECATION")
    private fun readCredentials(
        reservation: WifiManager.LocalOnlyHotspotReservation,
    ): Pair<String?, String?> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val soft = try { reservation.softApConfiguration } catch (_: Throwable) { null }
            if (soft != null) {
                val ssid = try { soft.ssid } catch (_: Throwable) { null }
                val pass = try { soft.passphrase } catch (_: Throwable) { null }
                if (!ssid.isNullOrEmpty()) return ssid to pass
            }
        }
        // API 26–29 (und Fallback): WifiConfiguration trägt SSID/PSK in Anführungszeichen.
        val cfg = try { reservation.wifiConfiguration } catch (_: Throwable) { null }
        if (cfg != null) {
            val ssid = cfg.SSID?.removeSurrounding("\"")
            val pass = cfg.preSharedKey?.removeSurrounding("\"")
            if (!ssid.isNullOrEmpty()) return ssid to pass
        }
        return null to null
    }

    private fun failureName(reason: Int): String = when (reason) {
        WifiManager.LocalOnlyHotspotCallback.ERROR_NO_CHANNEL -> "ERROR_NO_CHANNEL"
        WifiManager.LocalOnlyHotspotCallback.ERROR_GENERIC -> "ERROR_GENERIC"
        WifiManager.LocalOnlyHotspotCallback.ERROR_INCOMPATIBLE_MODE -> "ERROR_INCOMPATIBLE_MODE"
        WifiManager.LocalOnlyHotspotCallback.ERROR_TETHERING_DISALLOWED -> "ERROR_TETHERING_DISALLOWED"
        else -> "ERROR_$reason"
    }
}
