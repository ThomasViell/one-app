package com.uip.oneapp.network

import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraction layer for different camera hardware (ONE, TWO).
 * Each device type implements this interface with its own communication protocol.
 */
interface HardwareService {
    val hardwareState: StateFlow<OneHardwareState>
    val logMessages: StateFlow<List<String>>
    val isConnected: Boolean

    /**
     * Convenience-Property für netzwerkbasierte Implementierungen (ONE-Remote, TWO).
     * Wer das setzt, soll konsistent dazu `videoSource` als [VideoSource.Rtsp] published
     * halten. Direkt-lokale Implementierungen (V4L2) ignorieren dieses Property; sie
     * nutzen stattdessen [VideoSource.LocalBitmap] in `videoSource`.
     */
    var lastRtspUrl: String

    /**
     * Aktuelle Video-Quelle für die UI. Die Implementierung published [VideoSource.Rtsp]
     * für Netzwerk-Streams oder [VideoSource.LocalBitmap] für direkt-lokale V4L2-Frames.
     * UI wickelt das in einen `VideoView`-Composable, der je nach Sub-Typ auf den
     * passenden Player dispatcht (siehe Phase P4).
     *
     * Bezug: `docs/PLAN_INTERNAL_HARDWARE_INTEGRATION.md`, Phase P1.
     */
    val videoSource: StateFlow<VideoSource>

    /** Discover and test connectivity to the hardware controller. */
    suspend fun probeEndpoints(): HardwareConnectionStatus

    /** Start continuous polling/communication with the controller. */
    fun startPolling()

    /** Stop polling and disconnect. */
    fun stopPolling()

    /** Release all resources. */
    fun destroy()

    // --- Common controls ---

    /** Cycle through light power levels. */
    fun cycleLightPower()

    /** Set light power directly (0 = OFF, 1-100 = brightness). */
    fun sendLightPower(power: Int)

    /** Cycle through sonde/frequency settings. */
    fun cycleFrequency()

    /** Set sonde frequency directly. */
    fun sendFrequency(frequency: Int)

    /** Reset absolute meter display to 0. */
    fun resetMeterAbsolute()

    /** Reset relative distance counter to 0. */
    fun resetMeterRelative()

    /** Send video overlay text to the camera OSD. */
    fun sendVideoOverlay(text: String?)
}
