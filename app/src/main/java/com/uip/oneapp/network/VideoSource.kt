package com.uip.oneapp.network

import android.graphics.Bitmap
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstrakter Video-Pfad. Wird vom HardwareService gepublished — UI dispatcht je nach
 * Sub-Typ auf den passenden Player (ExoPlayer/Media3 für RTSP, Compose-Image für Bitmap).
 *
 * Hintergrund: Bisher war die Video-Source ein einzelner RTSP-URL-String (`lastRtspUrl`).
 * Mit der direkten V4L2-Anbindung im ONE-Local-Mode kommt der Stream nicht mehr als
 * Netzwerk-URL, sondern als kontinuierlicher Bitmap-Flow aus dem Kernel. Diese Sealed
 * Class macht beide Pfade uniform aufrufbar.
 *
 * Bezug: siehe `docs/PLAN_INTERNAL_HARDWARE_INTEGRATION.md`, Phase P1.
 */
sealed class VideoSource {
    /** Kein Video aktiv (HardwareService nicht verbunden oder kein Setup). */
    data object None : VideoSource()

    /** Netzwerk-Video. URL wird an FfmpegVideoPlayer (ExoPlayer/Media3) übergeben. */
    data class Rtsp(val url: String) : VideoSource()

    /** Lokaler Bitmap-Stream aus der Kamera-Frame-Quelle. UI rendert das jeweils aktuelle Bitmap. */
    data class LocalBitmap(val flow: StateFlow<Bitmap?>) : VideoSource()

    /**
     * Camera2-Umbau 2026-07-29 (AP-2-Krücke): lokale Kamera-Hardware erkannt, aber
     * [com.uip.oneapp.bootstrap.CameraServiceSelfStarter] konnte den Provider-Dienst oder die
     * CAMERA-Berechtigung nicht sicherstellen. [reason] ist die Kurzbegründung (Log-Text),
     * NICHT direkt für den Nutzer formatiert — die UI zeigt eine feste, verständliche Meldung
     * und nutzt [reason] nur für Diagnose/Screenshot-Beweis. Unterscheidet sich bewusst von
     * [None] (dort ist z. B. kein Netzwerk-Ziel bekannt — kein Fehler, sondern Ausgangszustand).
     */
    data class Unavailable(val reason: String) : VideoSource()
}
