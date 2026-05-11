package com.uip.oneapp.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

data class RtspTestResult(
    val url: String,
    val reachable: Boolean,
    val responseCode: String = "",
    val error: String = ""
)

class RtspStreamTester {

    private val urlPatterns = listOf(
        "rtsp://{ip}:{port}/1234",
        "rtsp://{ip}:{port}/",
        "rtsp://{ip}:{port}/stream",
        "rtsp://{ip}:{port}/live",
        "rtsp://{ip}:{port}/stream1",
        "rtsp://{ip}:{port}/video",
        "rtsp://{ip}:{port}/cam",
        "rtsp://{ip}:{port}/h264",
        "rtsp://{ip}:{port}/media/video1",
        "rtsp://{ip}:{port}/live/ch0",
        "rtsp://{ip}:{port}/Streaming/Channels/101",
    )

    private val rtspPorts = listOf(8554, 554)

    fun generateUrls(ip: String, ports: List<Int> = rtspPorts): List<String> {
        return ports.flatMap { port ->
            urlPatterns.map { pattern ->
                pattern.replace("{ip}", ip).replace("{port}", port.toString())
            }
        }
    }

    suspend fun testUrl(url: String): RtspTestResult = withContext(Dispatchers.IO) {
        try {
            // Parse host and port from RTSP URL
            val withoutScheme = url.removePrefix("rtsp://")
            val hostPort = withoutScheme.substringBefore("/")
            val host = hostPort.substringBefore(":")
            val port = hostPort.substringAfter(":", "554").toIntOrNull() ?: 554

            // First: check TCP connectivity
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 2000)

                // Send RTSP OPTIONS request to verify it's an RTSP server
                val outputStream = socket.getOutputStream()
                val inputStream = socket.getInputStream()

                val request = "OPTIONS $url RTSP/1.0\r\nCSeq: 1\r\nUser-Agent: DrainQ ONE\r\n\r\n"
                outputStream.write(request.toByteArray())
                outputStream.flush()

                // Read response with timeout
                socket.soTimeout = 3000
                val buffer = ByteArray(1024)
                val bytesRead = inputStream.read(buffer)

                if (bytesRead > 0) {
                    val response = String(buffer, 0, bytesRead)
                    val firstLine = response.lines().firstOrNull() ?: ""

                    if (firstLine.contains("RTSP/1.0")) {
                        val code = firstLine.substringAfter("RTSP/1.0 ").substringBefore(" ").trim()
                        RtspTestResult(
                            url = url,
                            reachable = true,
                            responseCode = code
                        )
                    } else {
                        RtspTestResult(
                            url = url,
                            reachable = true,
                            responseCode = "Non-RTSP",
                            error = "Server responded but not RTSP: ${firstLine.take(50)}"
                        )
                    }
                } else {
                    RtspTestResult(
                        url = url,
                        reachable = true,
                        error = "No response from server"
                    )
                }
            }
        } catch (e: Exception) {
            RtspTestResult(
                url = url,
                reachable = false,
                error = e.message ?: "Connection failed"
            )
        }
    }

    suspend fun testAllUrls(ip: String, ports: List<Int> = rtspPorts): List<RtspTestResult> =
        withContext(Dispatchers.IO) {
            val urls = generateUrls(ip, ports)
            urls.map { url -> testUrl(url) }
        }

    suspend fun findWorkingUrl(ip: String, ports: List<Int> = rtspPorts): RtspTestResult? =
        withContext(Dispatchers.IO) {
            val urls = generateUrls(ip, ports)
            for (url in urls) {
                val result = testUrl(url)
                if (result.reachable && (result.responseCode == "200" || result.responseCode.startsWith("2"))) {
                    return@withContext result
                }
            }
            // If no 200, return first reachable one
            val urls2 = generateUrls(ip, ports)
            for (url in urls2) {
                val result = testUrl(url)
                if (result.reachable && result.responseCode.isNotEmpty() && result.responseCode != "Non-RTSP") {
                    return@withContext result
                }
            }
            null
        }
}
