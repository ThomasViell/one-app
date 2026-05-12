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

/**
 * JSON-Modell fuer den OSD-Push an den ONE-Controller (DeviceService:Port 12345).
 *
 * Schema rueckwirkend aus dem BWELL DeviceService dekompiliert:
 * com.bominwell.robot.services.VideoOverlay (data class, GSON-serialisiert).
 *
 * Wichtig: das **boolean isShowOSD** triggert die LIVE-Wirkung
 * (BitmapOsdUtil.setShowHeadOsd() / setNonRecordOsd()).
 * modeON_OFF ist 0 = ON, 1 = OFF — wird nur als Pref gespeichert,
 * setzt aber nicht aktiv die Rendering-Pipeline um.
 *
 * Die String-Listen (osdHeadStrArr / osdNormalStrArr) sind Vector<String>
 * im Hersteller-Code; mit Gson serialisiert das identisch zu einem JSON-Array.
 */
data class SdkVideoOverlay(
    val isShowOSD: Boolean = false,
    val modeON_OFF: Int = 0,
    val colorFont: Int = 0,
    val colorHig: Int = 0,
    val sizeFont: Int = 0,
    val pos1: Int = 0,
    val pos2: Int = 1,
    val pos3: Int = 2,
    val osdHeadStrArr: List<String>? = null,
    val osdNormalStrArr: List<String>? = null
)

