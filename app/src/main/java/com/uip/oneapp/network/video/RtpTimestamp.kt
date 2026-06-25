package com.uip.oneapp.network.video

/**
 * RTP-Zeitstempel-Umrechnung (90-kHz-Clock, RFC 3550) für den H.264-Stream — **relativ zur
 * ersten Access-Unit einer RTSP-Session**.
 *
 * **Warum relativ (= der Latenz-Fix):** Der [H264Encoder] läuft ab App-Start; seine PTS ist
 * "µs seit Encoder-Start". Verbindet sich ein Client erst Sekunden später, hätte die erste an
 * ihn gesendete AU eine große absolute PTS. Die `PLAY`-Antwort von [RtspVideoServer] meldet aber
 * `RTP-Info: …;rtptime=0`, d. h. der Client (ExoPlayer) verankert die Wiedergabe bei RTP-Tick 0.
 * Die Differenz "absolute PTS bei Verbindungsaufbau" landet damit 1:1 als Start-Latenz —
 * gemessener Vorbefund: ~60 s. Rebasing auf die erste **gesendete** AU bringt den ersten Frame
 * auf ~0 und deckungsgleich mit `rtptime=0`, **ohne** die echten Frame-Abstände
 * (Inter-Frame-Timing) zu verändern. Robust unabhängig davon, ob der Client an `RTP-Info` oder
 * am ersten empfangenen Paket verankert.
 *
 * Zustandsbehaftet: eine Instanz pro Session ([RtspVideoServer.Session]).
 */
internal class RtpTimestamper {
    private var basePtsUs = 0L
    private var hasBase = false

    /**
     * Liefert den 32-Bit-RTP-Tick (90 kHz) für [ptsUs] (µs, monoton ab Encoder-Start),
     * relativ zur ersten Access-Unit dieser Session. Der erste Aufruf setzt die Basis und
     * gibt 0 zurück; monoton steigende [ptsUs] ergeben monoton steigende Ticks.
     */
    fun toRtpTicks(ptsUs: Long): Int {
        if (!hasBase) {
            basePtsUs = ptsUs
            hasBase = true
        }
        return usToRtpTicks(ptsUs - basePtsUs)
    }
}

/**
 * Mikrosekunden → 90-kHz-RTP-Ticks (RFC 3550), abgeschnitten auf 32 Bit (Wrap-around per Spec).
 * `1 s → 90000 Ticks`, `1/30 s ≈ 3000 Ticks`. Bewusst `*9/100` wie im bewiesenen Spike, damit
 * das Inter-Frame-Timing bit-identisch bleibt; da pro Frame aus der absoluten (Delta-)PTS
 * gerechnet wird, akkumuliert die Trunkierung nicht.
 */
internal fun usToRtpTicks(deltaUs: Long): Int = (deltaUs * 9 / 100).toInt()
