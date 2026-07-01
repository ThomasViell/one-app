package com.uip.oneapp.network

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
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

        // SoftAP-Gateway-Subnetz (Android-Tethering-Konvention 192.168.43.1/24, identisch zu
        // OneHardwareConfig.targetIp) — Welle 3a, privilegierter Hotspot statt LocalOnlyHotspot.
        private const val SOFTAP_SUBNET_PREFIX = "192.168.43."
    }

    private val gson = Gson()

    private var scopeJob = SupervisorJob()
    private var scope = CoroutineScope(Dispatchers.IO + scopeJob)

    @Volatile
    private var serverSocket: ServerSocket? = null

    // Offene Client-Verbindungen — stop() muss sie aktiv schließen: handleClient blockiert in
    // input.read() (plain blocking I/O), Coroutine-Cancel allein unterbricht das nicht.
    private val clientSockets = ConcurrentHashMap.newKeySet<Socket>()

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
        // Client-Sockets aktiv schließen — löst die in input.read() blockierten Handler.
        clientSockets.forEach { try { it.close() } catch (_: Exception) {} }
        clientSockets.clear()
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
        // Eigenen Scope kapseln, NICHT die mutable Property `scope` in den Schleifen lesen:
        // nach stop()+start() würde eine alte, gecancelte Schleife sonst den NEUEN Scope sehen
        // (isActive=true) und weiterlaufen.
        val myScope = scope
        while (currentCoroutineContext().isActive && running) {
            var server: ServerSocket? = null
            try {
                server = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(config.tcpPort), ACCEPT_BACKLOG)
                }
                serverSocket = server
                Log.i(TAG, "TCP-Server lauscht auf :${config.tcpPort}")
                while (currentCoroutineContext().isActive && running) {
                    val client = server.accept()
                    // DeviceService bedient real einen Tablet-Client; pro Verbindung ein Handler.
                    myScope.launch { handleClient(client) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (running) Log.w(TAG, "TCP-Accept-Fehler: ${e.message}")
            } finally {
                // Nur das EIGENE Server-Socket schließen — nach stop()+start() zeigt die
                // Property bereits auf das Socket der neuen Instanz.
                try { server?.close() } catch (_: Exception) {}
                if (serverSocket === server) serverSocket = null
            }
            if (currentCoroutineContext().isActive && running) delay(REBIND_BACKOFF_MS)
        }
    }

    private suspend fun handleClient(socket: Socket) {
        Log.i(TAG, "Client verbunden: ${socket.inetAddress?.hostAddress}")
        clientSockets.add(socket)
        val myScope = scope
        var pushJob: Job? = null
        try {
            socket.tcpNoDelay = true
            val output = socket.getOutputStream()
            val input = socket.getInputStream()

            // Telemetrie-Push-Schleife.
            pushJob = myScope.launch {
                while (currentCoroutineContext().isActive && !socket.isClosed) {
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
            while (currentCoroutineContext().isActive && !socket.isClosed) {
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
            clientSockets.remove(socket)
            try { socket.close() } catch (_: Exception) {}
            Log.i(TAG, "Client getrennt")
        }
    }

    private suspend fun discoveryLoop() {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket().apply { broadcast = true }
            while (currentCoroutineContext().isActive && running) {
                try {
                    val payload = OneRemoteProtocol.discoveryPayload(localServerIp())
                    // Pro aktivem Interface den GERICHTETEN Broadcast (z. B. 192.168.43.255 des
                    // SoftAP) plus den limitierten 255.255.255.255 senden. Der gerichtete
                    // Broadcast erreicht das Tablet auf dem AP-Subnetz auch dann, wenn die ONE
                    // mehrhomed ist (Office-STA + AP) oder das AP-Interface keine Default-Route hat
                    // — ein unbound-Socket würde 255.255.255.255 sonst nur über die Default-Route
                    // ausgeben und das AP-Subnetz verfehlen (Welle 3a).
                    for (target in broadcastTargets()) {
                        try {
                            socket.send(DatagramPacket(payload, payload.size, target, config.broadcastPort))
                        } catch (_: Exception) { /* einzelnes Ziel nicht erreichbar → nächstes */ }
                    }
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
     * Broadcast-Ziele der Discovery: die gerichteten Broadcast-Adressen aller aktiven
     * Nicht-Loopback-Interfaces ([java.net.InterfaceAddress.getBroadcast], z. B. `192.168.43.255`
     * für den SoftAP) **plus** die limitierte Broadcast-Adresse `255.255.255.255` als universeller
     * Fallback. So erreicht die Discovery das Tablet auch in Mehrhomed-/AP-only-Topologien
     * (siehe [localServerIp]). Best-effort — Enumerationsfehler werden geschluckt.
     */
    private fun broadcastTargets(): List<InetAddress> {
        val targets = LinkedHashSet<InetAddress>()
        try {
            NetworkInterface.getNetworkInterfaces()?.asSequence()
                ?.filter { !it.isLoopback && it.isUp }
                ?.forEach { iface ->
                    iface.interfaceAddresses.forEach { ia -> ia.broadcast?.let { targets.add(it) } }
                }
        } catch (_: Exception) { /* Enumeration best-effort */ }
        try { targets.add(InetAddress.getByName(BROADCAST_ADDRESS)) } catch (_: Exception) {}
        return targets.toList()
    }

    /**
     * Erreichbare Server-IP für die Discovery-Nutzlast: enumeriert die aktiven
     * Nicht-Loopback-IPv4-Adressen und wählt in dieser Reihenfolge:
     *  1. Eine Adresse im **SoftAP-Subnetz `192.168.43.0/24`** (Gateway-Interface des
     *     Tablet-Hotspots, Welle 3a). Das AP-Interface heißt je nach OEM unterschiedlich
     *     (`wlan0`, `ap0`, `swlan0`, `wlan1` …) — der Subnetz-Treffer ist daher robuster als der
     *     Name; bei aktivem Hotspot ist genau das die IP, unter der das Tablet die ONE erreicht.
     *  2. `wlan0`/`ap0` per Name (Office-WLAN-Test im DIRECT-Modus ohne aktiven Hotspot).
     *  3. Erste beliebige Nicht-Loopback-IPv4.
     *  4. [OneHardwareConfig.targetIp], wenn gar kein Interface gefunden wird.
     *
     * Hinweis: Das ist die **Payload**-IP; der Client ([OneHardwareService.discoverViaUdpBroadcast]
     * → [OneRemoteProtocol.resolveDiscoveryIp]) bevorzugt ohnehin die echte **Quelladresse** des
     * Broadcast-Pakets, sodass die Kopplung auch bei suboptimaler Payload greift.
     *
     * [interfaceProvider] ist testbar injizierbar (Default = [activeInterfaceIps]).
     */
    internal fun localServerIp(
        interfaceProvider: () -> List<Pair<String, String>> = ::activeInterfaceIps
    ): String {
        val ifaces = interfaceProvider()
        val preferredNames = setOf("wlan0", "ap0")
        return ifaces.firstOrNull { (_, ip) -> ip.startsWith(SOFTAP_SUBNET_PREFIX) }?.second
            ?: ifaces.firstOrNull { (name, _) -> name in preferredNames }?.second
            ?: ifaces.firstOrNull()?.second
            ?: config.targetIp
    }

    private fun activeInterfaceIps(): List<Pair<String, String>> = try {
        NetworkInterface.getNetworkInterfaces()
            ?.asSequence()
            ?.filter { !it.isLoopback && it.isUp }
            ?.flatMap { iface ->
                iface.inetAddresses.asSequence()
                    .filterIsInstance<Inet4Address>()
                    .filter { !it.isLoopbackAddress }
                    .map { iface.name to (it.hostAddress ?: "") }
            }
            ?.filter { (_, ip) -> ip.isNotEmpty() }
            ?.toList() ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    private fun ensureScope() {
        if (scopeJob.isCancelled) {
            scopeJob = SupervisorJob()
            scope = CoroutineScope(Dispatchers.IO + scopeJob)
        }
    }
}
