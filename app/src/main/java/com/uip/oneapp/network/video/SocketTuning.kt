package com.uip.oneapp.network.video

import android.util.Log
import java.net.Socket

private const val TAG = "SocketTuning"

/**
 * Stellt einen TCP-Socket auf niedrige Latenz: **Nagle aus** (`TCP_NODELAY`). Für den
 * RTP-über-TCP-Pfad ([RtspVideoServer]) entscheidend — die einzelnen RTP-Pakete (FU-A-Fragmente,
 * kleine NALs) sollen sofort raus, statt vom Kernel zu größeren Segmenten gebündelt zu werden
 * (Nagle-Algorithmus), was sonst pro Paket bis ~40 ms Verzögerung addiert.
 *
 * Bewusst tolerant: schlägt das Setzen der Option auf einer exotischen Plattform fehl, bleibt der
 * Stream nutzbar (nur ohne die Latenz-Optimierung). Ausgelagert als reine Funktion, damit die
 * Garantie „NODELAY gesetzt" ohne Server-Setup testbar ist (siehe `SocketTuningTest`).
 */
internal fun tuneLowLatencySocket(socket: Socket) {
    try {
        socket.tcpNoDelay = true
    } catch (e: Exception) {
        Log.w(TAG, "tcpNoDelay konnte nicht gesetzt werden: ${e.message}")
    }
}
