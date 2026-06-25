package com.uip.oneapp.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.security.SecureRandom
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.asKotlinRandom

/**
 * Android-/SharedPreferences-Implementierung von [SoftApCredentialStore] (Dual-Modus, Welle 3a):
 *  - **SSID** = `DrainQ-ONE-<serial>` aus der Geräte-Seriennummer — auf der privilegierten
 *    Werks-ONE über [Build.getSerial] (echte Seriennummer), sonst [Settings.Secure.ANDROID_ID]
 *    als stabiler Fallback. Immer aus dem Serial neu abgeleitet ([SoftApSpec.buildSsid]).
 *  - **Passphrase** = einmalig mit [SecureRandom] erzeugt und in den SharedPreferences gehalten;
 *    bei Folgeaufrufen unverändert übernommen ([SoftApCredentialProvisioner]).
 *
 * Die Zugangsdaten werden **nicht geloggt**. SSID ist unkritisch (steht ohnehin im WLAN-Beacon).
 */
class AndroidSoftApCredentialStore(context: Context) : SoftApCredentialStore {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun loadOrCreate(): SoftApCredentials {
        val existing = prefs.getString(KEY_PASS, null)
        val creds = SoftApCredentialProvisioner.provision(
            serial = deviceSerial(),
            existingPassphrase = existing,
            random = SecureRandom().asKotlinRandom(),
        )
        if (creds.passphrase != existing) {
            prefs.edit().putString(KEY_PASS, creds.passphrase).apply()
        }
        return creds
    }

    /** Echte Seriennummer (privilegiert) → ANDROID_ID-Fallback → "device". Nie ein Crash. */
    @SuppressLint("HardwareIds")
    private fun deviceSerial(): String {
        val serial = try { Build.getSerial() } catch (_: Throwable) { null }
        if (!serial.isNullOrBlank() && !serial.equals("unknown", ignoreCase = true)) return serial
        val androidId = try {
            Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (_: Throwable) { null }
        return androidId?.takeIf { it.isNotBlank() } ?: "device"
    }

    private companion object {
        const val PREFS = "softap_pairing"
        const val KEY_PASS = "passphrase"
    }
}

/**
 * **Privilegierter SoftAP-Starter** (Dual-Modus, Welle 3a) — bringt den gebrandeten Tablet-
 * Hotspot über den @SystemApi-Tethering-Pfad hoch, **ohne Standortberechtigung** und mit
 * **fester, gebrandeter SSID** (Kunden-Anforderung: kein Standort-Prompt, wiedererkennbares Netz).
 *
 * Ersetzt den früheren `startLocalOnlyHotspot`-Pfad (der ACCESS_FINE_LOCATION verlangte und nur
 * eine plattform-gewürfelte SSID liefern konnte). Möglich ist das, weil die ONE-App im Werks-
 * Image **privilegiert** ist (priv-app-Allowlist NETWORK_SETTINGS/TETHER_PRIVILEGED **oder**
 * Plattform-Signatur — s. `docs/SOFTAP_WERKS_PRIVILEG.md`).
 *
 * **Warum Reflection:** [android.net.wifi.SoftApConfiguration] (+ `Builder`),
 * [WifiManager.setSoftApConfiguration], `startTetheredHotspot`, `stopSoftAp` und
 * `registerSoftApCallback` liegen NICHT im öffentlichen SDK (@SystemApi) — gegen `android.jar`
 * also nicht kompilierbar. Für die privilegierte App greift kein Hidden-API-Block, die Aufrufe
 * gelingen zur Laufzeit. Auf einem nicht-privilegierten (Debug-)Gerät wirft die Plattform
 * `SecurityException` (bzw. die Hidden-API-Policy `NoSuchMethodException`) → der Starter meldet
 * sauber [REASON_PRIVILEGE] (UI: „Hotspot benötigt Werks-Image-Privileg") statt zu crashen.
 *
 * **Zustands-Beobachtung:** Eine — best-effort per Reflection-Proxy registrierte —
 * `SoftApCallback` treibt die echten Übergänge (ENABLED→aktiv, FAILED→fehlgeschlagen,
 * DISABLED-nach-Hochlauf→gestoppt). Bleibt sie auf einem Image stumm, meldet ein Fallback-Timer
 * den Hotspot best-effort als aktiv (die Zugangsdaten kennen wir vorab). Der Plattform-Round-Trip
 * (Tablet joint) ist Geräte-Test (Welle 5).
 */
class AndroidSoftApStarter(
    context: Context,
    private val credentialStore: SoftApCredentialStore = AndroidSoftApCredentialStore(context),
) : HotspotStarter {

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
        // SoftApConfiguration + der Tethering-Pfad existieren erst ab Android 11 (API 30).
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onFailed("requires-android-11")
            return HotspotSession { }
        }
        val creds = try {
            credentialStore.loadOrCreate()
        } catch (t: Throwable) {
            onFailed("credentials:${t.javaClass.simpleName}")
            return HotspotSession { }
        }

        // Genau EIN Endzustand (aktiv/fehlgeschlagen) wird gemeldet; danach nur noch onStopped.
        val settled = AtomicBoolean(false)
        // Hat der AP den Hochlauf (ENABLING/ENABLED) erreicht? Erst danach gilt DISABLED als
        // echter Stopp — der Initial-Snapshot „aktuell aus" der frisch registrierten Callback
        // darf den Start nicht sofort wieder auf Idle ziehen.
        val enabledSeen = AtomicBoolean(false)
        val handler = Handler(Looper.getMainLooper())

        // Fallback: meldet den Hotspot nach Ablauf best-effort aktiv, falls die SoftApCallback
        // auf diesem Image nie ENABLED liefert. Wird von jedem echten Endzustand entschärft.
        val fallback = Runnable {
            if (settled.compareAndSet(false, true)) {
                Log.i(TAG, "SoftApCallback stumm — melde Hotspot best-effort aktiv (SSID=${creds.ssid})")
                onActive(creds.ssid, creds.passphrase)
            }
        }

        var callbackHandle: Any? = null
        try {
            val config = buildSoftApConfig(creds.ssid, creds.passphrase)
            applySoftApConfig(wifi, config) // best-effort: persistiert die gebrandete Config
            callbackHandle = registerSoftApCallback(
                wifi = wifi,
                creds = creds,
                settled = settled,
                enabledSeen = enabledSeen,
                handler = handler,
                fallback = fallback,
                onActive = onActive,
                onFailed = onFailed,
                onStopped = onStopped,
            )
            handler.postDelayed(fallback, FALLBACK_ACTIVE_MS)

            val ok = startTetheredHotspot(wifi, config)
            if (!ok) {
                // Synchrone Ablehnung: inline aufräumen und eine No-op-Session zurückgeben (genau
                // wie der catch-Pfad). Andernfalls bliebe die SoftApCallback registriert und der
                // Controller würde die ins Leere laufende Session-Referenz behalten.
                handler.removeCallbacks(fallback)
                unregisterSoftApCallback(wifi, callbackHandle)
                if (settled.compareAndSet(false, true)) onFailed("start-rejected")
                return HotspotSession { }
            }
        } catch (t: Throwable) {
            handler.removeCallbacks(fallback)
            val cause = t.unwrap()
            val reason = if (isPrivilegeError(t)) REASON_PRIVILEGE else cause.javaClass.simpleName
            Log.w(TAG, "SoftAP-Start fehlgeschlagen: $reason (${cause.message})")
            if (settled.compareAndSet(false, true)) onFailed(reason)
            unregisterSoftApCallback(wifi, callbackHandle)
            return HotspotSession { }
        }

        val handle = callbackHandle
        return HotspotSession {
            handler.removeCallbacks(fallback)
            unregisterSoftApCallback(wifi, handle)
            stopSoftAp(wifi)
        }
    }

    // ===== Reflection: SoftApConfiguration bauen =====

    /** Baut eine `SoftApConfiguration` (WPA2-PSK, feste SSID, kein Auto-Shutdown) per Reflection. */
    private fun buildSoftApConfig(ssid: String, passphrase: String): Any {
        val builderCls = Class.forName("$SOFT_AP_CONFIG\$Builder")
        val configCls = Class.forName(SOFT_AP_CONFIG)
        val builder = builderCls.getConstructor().newInstance()
        builderCls.getMethod("setSsid", String::class.java).invoke(builder, ssid)
        val wpa2 = runCatching { configCls.getField("SECURITY_TYPE_WPA2_PSK").getInt(null) }
            .getOrDefault(SECURITY_TYPE_WPA2_PSK)
        builderCls.getMethod("setPassphrase", String::class.java, Int::class.javaPrimitiveType)
            .invoke(builder, passphrase, wpa2)
        // Hotspot soll nicht von selbst abschalten, während der Operator (ohne verbundenes Tablet)
        // in anderen Screens arbeitet — best-effort, Methode existiert erst ab API 30.
        runCatching {
            builderCls.getMethod("setAutoShutdownEnabled", Boolean::class.javaPrimitiveType)
                .invoke(builder, false)
        }
        return builderCls.getMethod("build").invoke(builder)
            ?: throw IllegalStateException("SoftApConfiguration.build() == null")
    }

    // ===== Reflection: privilegierte WifiManager-Aufrufe =====

    private fun applySoftApConfig(wifi: WifiManager, config: Any) {
        runCatching {
            val configCls = Class.forName(SOFT_AP_CONFIG)
            WifiManager::class.java.getMethod("setSoftApConfiguration", configCls).invoke(wifi, config)
        }.onFailure { Log.i(TAG, "setSoftApConfiguration übersprungen: ${it.unwrap().message}") }
    }

    /** `WifiManager.startTetheredHotspot(SoftApConfiguration)` → true bei Annahme. Wirft bei Privileg-Mangel. */
    private fun startTetheredHotspot(wifi: WifiManager, config: Any): Boolean {
        val configCls = Class.forName(SOFT_AP_CONFIG)
        val m = WifiManager::class.java.getMethod("startTetheredHotspot", configCls)
        return (m.invoke(wifi, config) as? Boolean) ?: true
    }

    private fun stopSoftAp(wifi: WifiManager) {
        runCatching { WifiManager::class.java.getMethod("stopSoftAp").invoke(wifi) }
            .onFailure { Log.w(TAG, "stopSoftAp: ${it.unwrap().message}") }
    }

    // ===== Reflection: SoftApCallback als dynamischer Proxy =====

    private fun registerSoftApCallback(
        wifi: WifiManager,
        creds: SoftApCredentials,
        settled: AtomicBoolean,
        enabledSeen: AtomicBoolean,
        handler: Handler,
        fallback: Runnable,
        onActive: (String, String) -> Unit,
        onFailed: (String) -> Unit,
        onStopped: () -> Unit,
    ): Any? = try {
        val cbCls = Class.forName("$WIFI_MANAGER\$SoftApCallback")
        val proxy = Proxy.newProxyInstance(cbCls.classLoader, arrayOf(cbCls)) { proxy, method, args ->
            when (method.name) {
                "onStateChanged" -> {
                    handleStateChanged(
                        state = extractApState(args),
                        creds = creds,
                        settled = settled,
                        enabledSeen = enabledSeen,
                        handler = handler,
                        fallback = fallback,
                        onActive = onActive,
                        onFailed = onFailed,
                        onStopped = onStopped,
                    )
                    null
                }
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "SoftApCallbackProxy@${System.identityHashCode(proxy)}"
                else -> defaultReturn(method) // alle übrigen Callback-Methoden ignorieren
            }
        }
        val executor = Executor { it.run() }
        WifiManager::class.java
            .getMethod("registerSoftApCallback", Executor::class.java, cbCls)
            .invoke(wifi, executor, proxy)
        proxy
    } catch (t: Throwable) {
        Log.i(TAG, "registerSoftApCallback n/v (${t.unwrap().javaClass.simpleName}) — Fallback-Aktiv greift")
        null
    }

    private fun unregisterSoftApCallback(wifi: WifiManager, handle: Any?) {
        if (handle == null) return
        runCatching {
            val cbCls = Class.forName("$WIFI_MANAGER\$SoftApCallback")
            WifiManager::class.java.getMethod("unregisterSoftApCallback", cbCls).invoke(wifi, handle)
        }
    }

    private fun handleStateChanged(
        state: Int,
        creds: SoftApCredentials,
        settled: AtomicBoolean,
        enabledSeen: AtomicBoolean,
        handler: Handler,
        fallback: Runnable,
        onActive: (String, String) -> Unit,
        onFailed: (String) -> Unit,
        onStopped: () -> Unit,
    ) {
        when (state) {
            WIFI_AP_STATE_ENABLING -> enabledSeen.set(true)
            WIFI_AP_STATE_ENABLED -> {
                enabledSeen.set(true)
                if (settled.compareAndSet(false, true)) {
                    handler.removeCallbacks(fallback)
                    Log.i(TAG, "SoftAP aktiv (SSID=${creds.ssid})") // Passphrase NICHT loggen.
                    onActive(creds.ssid, creds.passphrase)
                }
            }
            WIFI_AP_STATE_FAILED -> {
                if (settled.compareAndSet(false, true)) {
                    handler.removeCallbacks(fallback)
                    onFailed("ap-failed")
                }
            }
            WIFI_AP_STATE_DISABLED -> {
                // Initial-Snapshot „aktuell aus" (vor jedem Hochlauf) ignorieren.
                if (enabledSeen.get()) onStopped()
            }
        }
    }

    /**
     * Liest den AP-State aus den Callback-Argumenten. Zwei Signaturen über die API-Level:
     *  - `onStateChanged(int state, int failureReason)` (API 30..34),
     *  - `onStateChanged(SoftApState state)` (API 35+) — dann reflektiv `getState()`.
     */
    private fun extractApState(args: Array<Any?>?): Int {
        val first = args?.getOrNull(0) ?: return -1
        if (first is Int) return first
        return runCatching { first.javaClass.getMethod("getState").invoke(first) as Int }
            .getOrDefault(-1)
    }

    private fun defaultReturn(method: Method): Any? = when (method.returnType) {
        java.lang.Boolean.TYPE -> false
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Void.TYPE -> null
        else -> null
    }

    // ===== Fehler-Klassifikation =====

    /**
     * Privileg-Mangel? Ein nicht-privilegierter Aufruf der @SystemApi-Methoden wirft
     * [SecurityException] (in [InvocationTargetException] verpackt); die Hidden-API-Policy
     * kann die Reflection-Auflösung als [NoSuchMethodException]/[IllegalAccessException]/
     * [ClassNotFoundException] blocken. All das = „Werks-Image-Privileg fehlt".
     */
    private fun isPrivilegeError(t: Throwable): Boolean {
        val c = t.unwrap()
        return c is SecurityException ||
            c is NoSuchMethodException ||
            c is IllegalAccessException ||
            c is ClassNotFoundException
    }

    /** Entpackt die echte Ursache eines reflektierten Aufrufs. */
    private fun Throwable.unwrap(): Throwable =
        (this as? InvocationTargetException)?.targetException ?: this

    private companion object {
        const val TAG = "AndroidSoftApStarter"

        const val SOFT_AP_CONFIG = "android.net.wifi.SoftApConfiguration"
        const val WIFI_MANAGER = "android.net.wifi.WifiManager"

        // SoftApConfiguration.SECURITY_TYPE_WPA2_PSK (Fallback, falls das Feld reflektiv blockt).
        const val SECURITY_TYPE_WPA2_PSK = 1

        // WifiManager.WIFI_AP_STATE_* — seit jeher stabile Hidden-Konstanten (hier hartkodiert,
        // da das reflektive Feld-Lesen unter der Hidden-API-Policy blocken kann).
        const val WIFI_AP_STATE_DISABLED = 11
        const val WIFI_AP_STATE_ENABLING = 12
        const val WIFI_AP_STATE_ENABLED = 13
        const val WIFI_AP_STATE_FAILED = 14

        // Best-effort-Aktiv, falls die SoftApCallback auf dem Image stumm bleibt. Großzügig
        // gewählt, damit ein echtes (schnelles) FAILED den Fallback noch entschärfen kann.
        const val FALLBACK_ACTIVE_MS = 6000L
    }
}
