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
    var lastRtspUrl: String

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
