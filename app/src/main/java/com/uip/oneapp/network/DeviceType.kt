package com.uip.oneapp.network

// Rebranding (2026-06-07): Produktname ist DrainQ — der displayName steht sichtbar
// in OSD-Zeile 1 und wird damit in JEDES Foto und Video eingebrannt.
enum class DeviceType(val displayName: String) {
    ONE("DrainQ ONE"),
    TWO("DrainQ TWO")
}

data class TwoHardwareConfig(
    val cameraIp: String = "172.169.10.65",
    val cameraPort: Int = 554,
    val cameraUser: String = "admin",
    val cameraPassword: String = "bmw12345",
    val mqttBrokerIp: String = "172.169.10.11",
    val mqttBrokerPort: Int = 1883,
    val rtspMainPath: String = "/snl/live/1/1",
    val rtspSubPath: String = "/snl/live/1/2"
) {
    fun buildRtspUrl(): String =
        "rtsp://$cameraUser:$cameraPassword@$cameraIp:$cameraPort$rtspMainPath"

    fun buildSubStreamUrl(): String =
        "rtsp://$cameraUser:$cameraPassword@$cameraIp:$cameraPort$rtspSubPath"

    fun buildCgiBaseUrl(): String =
        "http://$cameraIp"
}
