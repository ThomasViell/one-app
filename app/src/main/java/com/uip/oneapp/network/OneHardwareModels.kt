package com.uip.oneapp.network

data class OneHardwareState(
    val cableController: CableControllerState = CableControllerState(),
    val crawlerController: CrawlerControllerState = CrawlerControllerState(),
    val connectionStatus: HardwareConnectionStatus = HardwareConnectionStatus()
)

data class CableControllerState(
    val meterReading: Float? = null,       // distance (absolute)
    val currentDistance: Float? = null,     // currentDistance (relative/since last reset)
    val batteryLevel: Int? = null,
    val rawDistanceValue: Int? = null,
    val lastUpdateMs: Long = 0L
)

data class CrawlerControllerState(
    val lightOn: Boolean? = null,
    val lightAvailable: Boolean = false,  // false = ONE protocol doesn't report light status
    val laserOn: Boolean? = null,
    val frontLightPower: Int? = null,
    val sondeFrequency: String? = null,
    val lastUpdateMs: Long = 0L
)

data class HardwareConnectionStatus(
    val cableControllerReachable: Boolean = false,
    val crawlerControllerReachable: Boolean = false,
    val cableControllerIp: String = "",
    val crawlerControllerIp: String = "",
    val lastProbeAttemptMs: Long = 0L,
    val probeCompleted: Boolean = false,
    val tcpConnected: Boolean = false,
    val discoveredIp: String = ""
)

data class OneHardwareConfig(
    val broadcastPort: Int = 8555,
    val tcpPort: Int = 12345,
    val discoveryTimeoutMs: Int = 5000,
    val tcpReadTimeoutMs: Int = 5000,
    val fallbackIp: String = "192.168.82.22"
)

/** SDK JSON model: SendData wraps MiniPushInfo + VideoOverlay + sendCommand */
data class SdkSendData(
    val miniPushInfo: SdkMiniPushInfo? = null,
    val videoOverlay: SdkVideoOverlay? = null,
    val sendCommand: List<Int>? = null
)

data class SdkMiniPushInfo(
    val distance: Float = 0f,
    val battery: Int = 0,
    val electric: Float = 0f,
    val cameraID: Int = 0,
    val currentDistance: Float = 0f,
    val light: Int = 0,         // Light power (0-100)
    val frequency: Int = 0,     // Sonde frequency (0=off, 1=33kHz, 2=640Hz, 3=512Hz)
    val power: Int = 0,
    val btn1: Int = 0,
    val btn2: Int = 0,
    val btn3: Int = 0,
    val btn4: Int = 0,
    val btn5: Int = 0,
    val btn6: Int = 0,
    val version: String = ""
)

data class SdkVideoOverlay(
    val text: String? = null
)

