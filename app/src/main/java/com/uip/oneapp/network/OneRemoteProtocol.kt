package com.uip.oneapp.network

/**
 * Reines (Android-freies) Protokoll für den **ONE-Remote**-Transport (Tablet-über-WiFi):
 * TCP/JSON zum Bominwell `DeviceService` (Port 12345). Aus [OneHardwareService] heraus-
 * gelöst, damit die heiklen Teile — Paketbau (Header/Checksumme), TCP-Reassembly
 * (Brace-Matching auf zerstückelten Reads) und Telemetrie-Mapping — **ohne Socket und
 * ohne `android.util.Log` unit-testbar** sind. Pendant zu [com.uip.oneapp.network.internal.OneFrameCodec]
 * für den Direkt-Modus.
 *
 * Byte-Layout 1:1 aus dem dekompilierten Hersteller-SDK (`MiniPushControlHelper`),
 * wiederhergestellt aus Commit `8da3b98` (vor der Migration-A-Leerung in `13b1384`).
 */
object OneRemoteProtocol {

    // SDK-Header (FA AF 00 10 00 01) — Präfix jedes Steuerpakets.
    val HEADER: ByteArray = byteArrayOf(
        0xFA.toByte(), 0xAF.toByte(), 0x00, 0x10, 0x00, 0x01
    )

    /** Lichtstufen-Zyklus wie im SDK (`changeLightPower`): 0 → 30 → 60 → 90 → 0. */
    val LIGHT_POWER_CYCLE: IntArray = intArrayOf(0, 30, 60, 90)

    /** Sonde-Frequenz-Zyklus: 0=OFF, 1=33kHz, 2=640Hz, 3=512Hz. */
    val FREQUENCY_CYCLE: IntArray = intArrayOf(0, 1, 2, 3)

    /** Anzeige-Label für eine Sonde-Frequenz (null = AUS). */
    fun freqLabel(frequency: Int): String? = when (frequency) {
        1 -> "33kHz"
        2 -> "640Hz"
        3 -> "512Hz"
        else -> null
    }

    /** Nächste Lichtstufe im Zyklus (unbekannter Ausgangswert → erste ON-Stufe). */
    fun nextLightPower(current: Int): Int {
        val i = LIGHT_POWER_CYCLE.indexOf(current)
        val next = if (i == -1) 1 else (i + 1) % LIGHT_POWER_CYCLE.size
        return LIGHT_POWER_CYCLE[next]
    }

    /** Nächste Sonde-Frequenz im Zyklus 0→1→2→3→0. */
    fun nextFrequency(current: Int): Int = (current + 1) % FREQUENCY_CYCLE.size

    /**
     * 12-Byte BaseCommand mit Layout `len, 0x01, 0x00, light, freq` gefolgt von 7× `0x00`.
     * Das len-Byte (Index 0) wird in [packet] final gesetzt.
     */
    fun baseCommand(lightPower: Int, frequency: Int): ByteArray {
        val cmd = ByteArray(12)
        cmd[0] = 0x0D            // wird in packet() überschrieben
        cmd[1] = 0x01
        cmd[2] = 0x00
        cmd[3] = lightPower.coerceIn(0, 100).toByte()
        cmd[4] = frequency.coerceIn(0, 3).toByte()
        // cmd[5..11] = 0x00 (bereits null-initialisiert)
        return cmd
    }

    /**
     * Header + Command (len-Byte = `command.size + 1`, XOR-Checksumme über alle
     * Command-Bytes) → vollständiges Wire-Paket. Entspricht SDK `sendCommandWithChecksum`.
     * **Achtung:** mutiert das erste Command-Byte (Index 0).
     */
    fun packet(command: ByteArray): ByteArray {
        command[0] = (command.size + 1).toByte()
        var checksum = 0
        for (b in command) checksum = checksum xor (b.toInt() and 0xFF)
        return HEADER + command + checksum.toByte()
    }

    /** Bequemlichkeit: BaseCommand direkt als vollständiges Wire-Paket. */
    fun baseCommandPacket(lightPower: Int, frequency: Int): ByteArray =
        packet(baseCommand(lightPower, frequency))

    /** Wire-Paket als (signierte) Int-Liste — SDK-Gson-Format für `sendCommand`. */
    fun packetAsIntList(packet: ByteArray): List<Int> = packet.map { it.toInt() }

    /**
     * Zieht **alle vollständigen** JSON-Objekte aus [buffer]. TCP liefert die JSON-
     * Push-Frames des DeviceService zerstückelt oder mehrere am Stück; Brace-Matching
     * trennt sie. Ein unvollständiger Rest bleibt im Buffer und wird beim nächsten Read
     * fortgesetzt. Reine Variante von `OneHardwareService.processJsonBuffer`.
     *
     * Limitation (1:1 aus dem Original übernommen): das Matching zählt rohe `{`/`}` ohne
     * String-/Escape-Kontext. Eine geschweifte Klammer **innerhalb** eines JSON-String-
     * Werts würde die Tiefenzählung verfälschen. Real unkritisch, weil die eingehende
     * Telemetrie rein numerisch ist (siehe `OneRemoteProtocolTest`-Charakterisierung).
     */
    fun drainJsonObjects(buffer: StringBuilder): List<String> {
        val out = ArrayList<String>()
        while (buffer.isNotEmpty()) {
            val start = buffer.indexOf('{')
            if (start == -1) {
                buffer.clear()
                break
            }
            if (start > 0) buffer.delete(0, start)

            var depth = 0
            var end = -1
            for (i in buffer.indices) {
                when (buffer[i]) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            end = i
                            break
                        }
                    }
                }
            }

            if (end == -1) break // unvollständig — auf mehr Daten warten

            out.add(buffer.substring(0, end + 1))
            buffer.delete(0, end + 1)
        }
        return out
    }

    /**
     * Offset-freies Telemetrie-Delta aus einem dekodierten SDK-Push. Der absolute
     * Distanz-Offset (Software-Reset) wird bewusst **nicht** hier, sondern im
     * [OneHardwareService] angewandt (zustandsbehaftet) — diese Funktion bleibt rein.
     */
    data class Telemetry(
        val rawDistance: Float,
        val currentDistance: Float,
        val battery: Int,
        val frequency: Int
    ) {
        val freqLabel: String? get() = freqLabel(frequency)
    }

    /** Mappt `miniPushInfo` auf [Telemetry]; null wenn kein `miniPushInfo` enthalten ist. */
    fun telemetryFrom(info: SdkMiniPushInfo?): Telemetry? {
        if (info == null) return null
        return Telemetry(
            rawDistance = info.distance,
            currentDistance = info.currentDistance,
            battery = info.battery,
            frequency = info.frequency
        )
    }

    // ===== Absolut-Distanz (Software-Reset) — rein, damit die heikle Akkumulations-
    // Arithmetik (zwei Resets hintereinander) ohne Socket/Log getestet werden kann. =====

    /** Anzeige-Distanz nach Abzug des Software-Offsets. */
    fun applyAbsoluteOffset(rawDistance: Float, offset: Float): Float = rawDistance - offset

    /**
     * Neuer Offset nach einem Absolut-Reset: der aktuelle Roh-Stand wird zum Nullpunkt.
     * Aus der Anzeige rekonstruiert (`roh = anzeige + alter Offset`), da die Hardware
     * keinen Absolut-Reset kennt und ihren Roh-Wert weiterzählt.
     */
    fun offsetForAbsoluteReset(currentDisplay: Float, currentOffset: Float): Float =
        currentDisplay + currentOffset

    // ===== Server-Seite (Welle 3b) — exakte Inverse der Client-Kodierung, damit die ONE
    // selbst als Bominwell-`DeviceService` (TCP :12345 / Discovery UDP :8555) auftreten und
    // ein WiFi-Tablet bedienen kann. Rein/ohne Socket testbar, spiegelbildlich zu
    // baseCommandPacket()/telemetryFrom()/freqLabel(). Konsumiert von [OneRemoteServer]. =====

    /** Vollständige Wire-Paketgröße: SDK-Header (6) + 12-Byte-Command + XOR-Checksumme (1). */
    private const val PACKET_SIZE = 6 + 12 + 1

    /** Reset-Signal des Relativ-Meters (SDK `setJiMi(CLEAR_DISTANCE_ON)`), Wert 1.0f. */
    const val CLEAR_DISTANCE_ON: Float = 1.0f

    /** Server-Sicht eines dekodierten BaseCommands: die vom Client gesetzten Werte. */
    data class BaseCommand(val light: Int, val frequency: Int)

    /**
     * Inverse zu [baseCommandPacket] + [packetAsIntList]: liest Licht/Frequenz aus einem über
     * `sendCommand` empfangenen Wire-Paket. Validiert Länge, SDK-Header **und** XOR-Checksumme;
     * gibt `null` zurück, wenn das Paket kein gültiges BaseCommand ist (fremdes Schema, Müll,
     * abweichende Länge). Robust gegen signierte Ints — Gson liefert Bytes als -128..127, daher
     * überall `and 0xFF`.
     */
    fun decodeBaseCommand(sendCommand: List<Int>?): BaseCommand? {
        if (sendCommand == null || sendCommand.size != PACKET_SIZE) return null
        for (i in HEADER.indices) {
            if ((sendCommand[i] and 0xFF) != (HEADER[i].toInt() and 0xFF)) return null
        }
        val command = IntArray(12) { sendCommand[HEADER.size + it] and 0xFF }
        var xor = 0
        for (b in command) xor = xor xor b
        if (xor != (sendCommand[PACKET_SIZE - 1] and 0xFF)) return null
        // command[3] = Licht, command[4] = Frequenz (siehe baseCommand-Layout).
        return BaseCommand(light = command[3], frequency = command[4])
    }

    /** True, wenn der eingehende Client-Push das Relativ-Meter-Reset-Signal trägt. */
    fun isRelativeMeterReset(data: SdkSendData): Boolean =
        data.miniPushInfo?.currentDistance == CLEAR_DISTANCE_ON

    /**
     * Inverse zu [freqLabel]: Anzeige-Label → SDK-Frequenz-Code. Normalisiert (Leerzeichen
     * raus, lowercase), damit sowohl das Remote-Label ("33kHz") als auch das abweichende
     * Direkt-Label der internen Hardware ("33 kHz", siehe
     * `com.uip.oneapp.network.internal.SondeFrequency.name`) korrekt auf 1/2/3 abbilden;
     * alles andere (null, "Off", "Unknown (…)") → 0.
     */
    fun freqValue(label: String?): Int = when (label?.replace(" ", "")?.lowercase()) {
        "33khz" -> 1
        "640hz" -> 2
        "512hz" -> 3
        else -> 0
    }

    /**
     * Inverse zu [telemetryFrom]: baut den `miniPushInfo`-Push aus dem [OneHardwareState] der
     * lokalen Hardware (Server→Tablet). Sentinel-Semantik wie der Client: fehlende Werte → 0.
     * Distanz wird in Metern weitergereicht (Wire-Feld `distance`/`currentDistance`) — genau die
     * Einheit, die der Client wieder ausliest. `light` ist informativ (der Client hält den
     * Lichtstatus lokal und ignoriert das Feld); `battery` stammt aus dem State und ist im
     * Direkt-Modus oft 0 (die interne Hardware schreibt den Akku NICHT in den State — er kommt
     * dort aus dem Android-System; Remote-Akku-Telemetrie = TODO(device), Welle 5).
     */
    fun miniPushFrom(state: OneHardwareState): SdkMiniPushInfo {
        val cable = state.cableController
        val crawler = state.crawlerController
        return SdkMiniPushInfo(
            distance = cable.meterReading ?: 0f,
            currentDistance = cable.currentDistance ?: 0f,
            battery = cable.batteryLevel ?: 0,
            cameraID = cable.cameraId ?: 0,
            light = crawler.frontLightPower ?: 0,
            frequency = freqValue(crawler.sondeFrequency)
        )
    }

    /**
     * Discovery-Nutzlast (UDP :8555): die erreichbare Server-IP als UTF-8-String — exakt das
     * Format, das [OneHardwareService.discoverViaUdpBroadcast] empfängt und per [isValidIp]
     * validiert.
     */
    fun discoveryPayload(ip: String): ByteArray = ip.toByteArray(Charsets.UTF_8)
}
