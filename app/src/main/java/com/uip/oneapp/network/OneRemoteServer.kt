package com.uip.oneapp.network

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * **ONE-Remote-Server** (Dual-Modus, Welle 3b) — die Kehrseite von [OneHardwareService].
 * Läuft **nur im DIRECT-Modus** (App auf der ONE-Hardware) und macht die lokal seriell
 * angebundene Hardware ([com.uip.oneapp.network.internal.OneInternalHardwareService]) über
 * WLAN für ein Tablet erreichbar, indem die ONE selbst den Bominwell-`DeviceService`
 * nachbildet:
 *   - **TCP :12345** — pusht Telemetrie ([OneHardwareState] → `SdkSendData{miniPushInfo}`)
 *     und empfängt Steuerbefehle (`sendCommand` / `miniPushInfo` / `videoOverlay`) im exakt
 *     gleichen Wire-Format wie der [OneHardwareService]-Client sie sendet, und übersetzt sie
 *     auf die [HardwareService]-Methoden (Licht / Sonde / Meter-Null / OSD).
 *   - **UDP :8555** — periodischer Discovery-Broadcast der erreichbaren IP (Fallback; der
 *     Client testet primär direkt `config.targetIp`).
 *
 * Die Übersetzungs- und Serialisierungslogik ist in **reine, ohne Socket testbare** Methoden
 * ausgelagert ([applyClientCommand], [currentTelemetryJson]); die Wire-Inverse (Header/
 * Checksumme, Telemetrie-/Frequenz-Mapping, Discovery-Nutzlast) liegt in [OneRemoteProtocol].
 * Die eigentlichen Socket-Schleifen sind dünne Wrapper darüber. Der echte Netz-Round-Trip
 * Tablet↔ONE ist **Geräte-Test (Welle 5)** — hier nur Build + Unit-Tests mit Mock-
 * [HardwareService].
 *
 * **Bewusst NICHT enthalten:**
 *  - Video: der RTSP-Video-Server aus V4L2 ist Welle 3c (eigener Spike, hohes Latenz-Risiko).
 *  - Arbitrierung lokale-UI ↔ Tablet auf EINEM seriellen Bus ist Welle 3d. Der Server setzt
 *    voraus, dass die lokale Hardware bereits pollt (Serial offen) — sonst sind die
 *    übersetzten Befehle stille No-ops (`OneInternalHardwareService.sendBase`: fd<0 → return).
 *    OPEN DECISION: Der 2-Hz-Keepalive des Clients trägt seinen Licht-/Frequenz-Spiegel; beim
 *    ersten Verbinden setzt das den lokalen Stand auf den Tablet-Stand (typ. Licht=0). Echte
 *    Master/Slave-Arbitrierung folgt mit Welle 3d.
 */
class OneRemoteServer(
    private val hardwareService: HardwareService,
    private val config: OneHardwareConfig = OneHardwareConfig()
) {
    companion object {
        private const val TAG = "OneRemoteServer"

        // Telemetrie-Push-Takt. Bewusst < dem client-seitigen 5-s-Stillstands-Timeout
        // (OneHardwareConfig.tcpReadTimeoutMs), damit der Client auch bei statischem State
        // nicht in eine Reconnect-Schleife läuft.
        private const val PUSH_INTERVAL_MS = 500L

        // Discovery-Broadcast-Takt (Fallback-Pfad; Primärpfad des Clients ist direkter TCP-Test).
        private const val DISCOVERY_INTERVAL_MS = 2000L

        // Re-Bind-Backoff, falls der TCP-Port noch belegt ist (z. B. kurz nach stop()/start()).
        private const val REBIND_BACKOFF_MS = 1000L

        private const val ACCEPT_BACKLOG = 1
        private const val BROADCAST_ADDRESS = "255.255.255.255"
    }

    private val gson = Gson()

    private var scopeJob = SupervisorJob()
    private var scope = CoroutineScope(Dispatchers.IO + scopeJob)

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var running = false

    // Letzte an die Hardware angewandten Werte — entprellt den 2-Hz-Keepalive des Clients
    // (jeder Keepalive trägt Licht/Frequenz; nur echte Änderungen sollen auf den seriellen
    // Bus gehen). Sentinel -1 = "noch nichts angewandt".
    @Volatile
    private var lastLight = -1

    @Volatile
    private var lastFreq = -1

    val isRunning: Boolean get() = running

    // ===== Lebenszyklus =====

    /**
     * Startet TCP-Server (:tcpPort) und Discovery-Broadcast (:broadcastPort). Idempotent.
     * Gating auf DIRECT-Modus erfolgt am Aufrufer (`di/AppModule` + `OneApp`: nur gestartet,
     * wenn der aufgelöste [HardwareService] die interne Direkt-Impl ist).
     */
    fun start() {
        if (running) {
            Log.i(TAG, "bereits aktiv — start() ignoriert")
            return
        }
        ensureScope()
        running = true
        lastLight = -1
        lastFreq = -1
        Log.i(TAG, "Start: TCP :${config.tcpPort}, Discovery UDP :${config.broadcastPort}")
        scope.launch { acceptLoop() }
        scope.launch { discoveryLoop() }
    }

    /** Stoppt Server + Discovery und gibt die Sockets frei. */
    fun stop() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        scopeJob.cancel()
    }

    // ===== Reine, ohne Socket testbare Logik =====

    /** Aktuelle Telemetrie als SDK-`SendData`-JSON (Server→Tablet). */
    fun currentTelemetryJson(): String =
        gson.toJson(SdkSendData(miniPushInfo = OneRemoteProtocol.miniPushFrom(hardwareService.hardwareState.value)))

    /**
     * Übersetzt EINEN eingehenden Client-Push auf [HardwareService]-Aufrufe. Die drei Felder
     * sind unabhängig; Licht/Frequenz werden nur bei **Änderung** gesetzt (Keepalive-Entprellung).
     *  - `videoOverlay` → [HardwareService.sendVideoOverlay] (`isShowOSD=true` → OSD AN/`null`,
     *    `false` → OSD AUS/`""` — exakt die Legacy-Abbildung des Clients).
     *  - `miniPushInfo.currentDistance == 1.0f` → [HardwareService.resetMeterRelative].
     *  - `sendCommand` (BaseCommand) → Licht/Frequenz via [HardwareService.sendLightPower] /
     *    [HardwareService.sendFrequency].
     */
    fun applyClientCommand(data: SdkSendData) {
        data.videoOverlay?.let { overlay ->
            hardwareService.sendVideoOverlay(if (overlay.isShowOSD) null else "")
        }
        if (OneRemoteProtocol.isRelativeMeterReset(data)) {
            hardwareService.resetMeterRelative()
        }
        OneRemoteProtocol.decodeBaseCommand(data.sendCommand)?.let { cmd ->
            if (cmd.light != lastLight) {
                hardwareService.sendLightPower(cmd.light)
                lastLight = cmd.light
            }
            if (cmd.frequency != lastFreq) {
                hardwareService.sendFrequency(cmd.frequency)
                lastFreq = cmd.frequency
            }
        }
    }

    // ===== Socket-Schleifen (Geräte-Round-Trip = Welle 5; hier dünne Wrapper über die
    // reine Logik oben) =====

    private suspend fun acceptLoop() {
        while (scope.isActive && running) {
            try {
                val server = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(config.tcpPort), ACCEPT_BACKLOG)
                }
                serverSocket = server
                Log.i(TAG, "TCP-Server lauscht auf :${config.tcpPort}")
                while (scope.isActive && running) {
                    val client = server.accept()
                    // DeviceService bedient real einen Tablet-Client; pro Verbindung ein Handler.
                    scope.launch { handleClient(client) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (running) Log.w(TAG, "TCP-Accept-Fehler: ${e.message}")
            } finally {
                try { serverSocket?.close() } catch (_: Exception) {}
                serverSocket = null
            }
            if (scope.isActive && running) delay(REBIND_BACKOFF_MS)
        }
    }

    private suspend fun handleClient(socket: Socket) {
        Log.i(TAG, "Client verbunden: ${socket.inetAddress?.hostAddress}")
        var pushJob: Job? = null
        try {
            socket.tcpNoDelay = true
            val output = socket.getOutputStream()
            val input = socket.getInputStream()

            // Telemetrie-Push-Schleife.
            pushJob = scope.launch {
                while (scope.isActive && !socket.isClosed) {
                    try {
                        output.write(currentTelemetryJson().toByteArray(Charsets.UTF_8))
                        output.flush()
                    } catch (_: Exception) {
                        break
                    }
                    delay(PUSH_INTERVAL_MS)
                }
            }

            // Empfangs-/Befehls-Schleife: TCP liefert JSON zerstückelt/zusammengeklebt →
            // Brace-Matching (OneRemoteProtocol.drainJsonObjects) trennt die Frames.
            // Bekannte Eigenschaft (OPEN, Welle 5): Der W1-Client schickt seinen Keepalive
            // ABWECHSELND roh-binär und JSON (plus einen rohen Init-Befehl). Wir verarbeiten nur
            // JSON-`SdkSendData` (das kanonische DeviceService-Protokoll); rohe Pakete tragen kein
            // '{' und werden von drainJsonObjects verworfen. Unkritisch, weil JEDE echte
            // Steueränderung (Licht/Sonde/Meter/OSD) als JSON gesendet wird und der Keepalive nur
            // den unveränderten Stand wiederholt. Härtung (SDK-Header-Framing statt Brace-Matching)
            // erst nach echtem Byte-Strom-Mitschnitt am Gerät.
            val buffer = ByteArray(1024)
            val jsonBuffer = StringBuilder()
            while (scope.isActive && !socket.isClosed) {
                val n = input.read(buffer)
                if (n == -1) break
                if (n > 0) {
                    jsonBuffer.append(String(buffer, 0, n, Charsets.UTF_8))
                    for (json in OneRemoteProtocol.drainJsonObjects(jsonBuffer)) {
                        try {
                            gson.fromJson(json, SdkSendData::class.java)?.let { applyClientCommand(it) }
                        } catch (e: Exception) {
                            Log.w(TAG, "Befehl-Parse-Fehler: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Client-Fehler: ${e.message}")
        } finally {
            pushJob?.cancel()
            try { socket.close() } catch (_: Exception) {}
            Log.i(TAG, "Client getrennt")
        }
    }

    private suspend fun discoveryLoop() {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket().apply { broadcast = true }
            val broadcast = InetAddress.getByName(BROADCAST_ADDRESS)
            while (scope.isActive && running) {
                try {
                    val payload = OneRemoteProtocol.discoveryPayload(localServerIp())
                    socket.send(DatagramPacket(payload, payload.size, broadcast, config.broadcastPort))
                } catch (e: Exception) {
                    if (running) Log.w(TAG, "Discovery-Broadcast-Fehler: ${e.message}")
                }
                delay(DISCOVERY_INTERVAL_MS)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (running) Log.w(TAG, "Discovery-Socket-Fehler: ${e.message}")
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Erreichbare Server-IP für die Discovery-Nutzlast. Im ONE-als-AP-Setup ist das das
     * AP-Gateway (= [OneHardwareConfig.targetIp], Default 192.168.43.1). TODO(device): echtes
     * AP-Interface enumerieren, falls das Werks-Image eine andere Gateway-IP vergibt.
     */
    private fun localServerIp(): String = config.targetIp

    private fun ensureScope() {
        if (scopeJob.isCancelled) {
            scopeJob = SupervisorJob()
            scope = CoroutineScope(Dispatchers.IO + scopeJob)
        }
    }
}
