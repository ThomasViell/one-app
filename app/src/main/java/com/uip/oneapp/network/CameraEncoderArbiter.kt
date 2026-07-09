package com.uip.oneapp.network

/**
 * **Ein-Encoder-Ausschluss auf `c2.rk.avc.encoder` (Welle 5, ADR 0002 B1).**
 *
 * Im DIRECT-Modus läuft `OneVideoServer` (RTSP-H.264) mit einem Encoder auf dem einzigen
 * HW-AVC-Codec der RK3588. Die lokale Aufnahme (`HardwareBitmapRecorder`) braucht denselben Codec.
 * Zwei gleichzeitige `MediaCodec`-Encoder-Instanzen sind unsicher (zweites `configure()` wirft/
 * verhungert; `release()` des einen kann den anderen korrumpieren).
 *
 * Dieser Arbiter koordiniert **gegenseitigen Ausschluss**: Der Recorder setzt beim Start das Flag
 * und wartet, bis `OneVideoServer` seinen Codec **freigegeben** hat; danach besitzt der Recorder den
 * Codec allein. Beim Stopp gibt er ihn zurück, `OneVideoServer` legt seinen Encoder neu an.
 *
 * Reine Zustands-/Handshake-Logik (keine Android-Abhängigkeit) → JVM-unit-testbar. Zeit/Sleep sind
 * für den Test injizierbar.
 */
class CameraEncoderArbiter {

    // OneVideoServer läuft (registriert sich bei start(), meldet sich bei stop() ab).
    @Volatile private var rtspPresent = false
    // OneVideoServer hält seinen HW-Codec gerade NICHT (true = frei für den Recorder).
    @Volatile private var rtspReleased = true
    // Der Recorder will/hat den Codec.
    @Volatile private var recording = false

    /** Vom `OneVideoServer.encodeLoop` je Iteration abgefragt: soll der RTSP-Encoder pausieren? */
    val isRecordingActive: Boolean get() = recording

    /** OneVideoServer meldet Präsenz. Bei Abmeldung gilt sein Codec als freigegeben. */
    fun setRtspPresent(present: Boolean) {
        rtspPresent = present
        if (!present) rtspReleased = true
    }

    /** OneVideoServer meldet, ob es seinen HW-Codec gerade freigegeben (true) oder belegt (false) hat. */
    fun setRtspEncoderReleased(released: Boolean) {
        rtspReleased = released
    }

    /**
     * Recorder fordert den einzigen HW-Encoder an. Setzt das Flag und wartet, bis der RTSP-Encoder
     * freigegeben ist (oder kein RTSP präsent ist / Timeout). Erst `true` ⇒ der Recorder darf
     * `configure()`.
     */
    fun acquireForRecording(
        timeoutMs: Long,
        pollMs: Long,
        nowMs: () -> Long,
        sleep: (Long) -> Unit,
    ): Boolean {
        recording = true
        if (!rtspPresent) return true          // kein RTSP-Encoder aktiv → sofort frei
        val deadline = nowMs() + timeoutMs
        while (nowMs() < deadline) {
            if (rtspReleased) return true
            sleep(pollMs)
        }
        return rtspReleased
    }

    /** Produktions-Bequemlichkeit (Wall-Clock + Thread.sleep). */
    fun acquireForRecording(timeoutMs: Long = 2000L): Boolean =
        acquireForRecording(timeoutMs, 20L, { System.currentTimeMillis() }, {
            // Interrupt-Flag wiederherstellen, damit ein cancel()-getriebener Interrupt während des
            // Wartens nicht verschluckt wird (Hygiene).
            try { Thread.sleep(it) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        })

    /** Recorder gibt den Codec zurück → OneVideoServer darf seinen Encoder wieder anlegen. */
    fun release() {
        recording = false
    }
}
