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
}
