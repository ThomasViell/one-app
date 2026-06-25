package com.uip.oneapp.network.video

import android.util.Base64
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Eingebetteter RTSP-Server für GENAU einen H.264-Stream, RTP über TCP (interleaved,
 * RFC 2326 §10.12 + RFC 6184 FU-A). TCP-Interleaving ist bewusst gewählt: alles läuft über
 * EINE TCP-Verbindung (kein separater UDP-RTP-Pfad) — robust durch NAT/`adb forward` und
 * ohne externe RTSP-Lib (~250 Zeilen).
 *
 * Produktiv übernommen aus dem bewiesenen Spike (`spike/video-rtsp`, `SpikeRtspServer`);
 * RTP/H.264-Packetisierung unverändert. Produktiv-Anpassung: [port]/[streamPath] kommen aus
 * [com.uip.oneapp.network.OneHardwareConfig] (Default `:8554` / `1234`) — konsistent zur
 * Tablet-seitigen `OneHardwareConfig.buildRtspUrl()` (`rtsp://<ip>:8554/1234`), damit der
 * Client den Stream findet. SDP `Content-Base`/`RTP-Info` werden aus der tatsächlichen
 * Request-URI abgeleitet (statt hartem `127.0.0.1` im Spike), damit ein echter WLAN-Client
 * (nicht nur localhost via adb) die Control-URLs korrekt auflöst.
 *
 * TODO(device): echter Tablet↔ONE-Round-Trip über WLAN (Welle 5).
 * TODO(harden): mehrere gleichzeitige Clients, RTCP, Auth (Spike: eine Session).
 */
class RtspVideoServer(
    private val port: Int = 8554,
    private val streamPath: String = "1234",
) {
    companion object { private const val TAG = "RtspVideoServer" }

    @Volatile private var sps: ByteArray? = null
    @Volatile private var pps: ByteArray? = null
    @Volatile private var running = false
    private var serverSocket: ServerSocket? = null
    @Volatile private var session: Session? = null

    @Volatile var lastStatus: String = "init"; private set

    fun setParameterSets(s: ByteArray, p: ByteArray) {
        sps = s; pps = p
        Log.i(TAG, "Parametersätze gesetzt (SPS ${s.size}B / PPS ${p.size}B)")
    }

    fun isPlaying(): Boolean = session?.playing == true

    fun start() {
        if (running) return
        running = true
        Thread({ acceptLoop() }, "rtsp-video-accept").apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) {}
        session?.close()
    }

    /** Vom Encoder pro Access-Unit aufgerufen. */
    fun onAccessUnit(annexB: ByteArray, ptsUs: Long, keyframe: Boolean) {
        val s = session
        if (s != null && s.playing) s.sendAccessUnit(annexB, ptsUs, keyframe)
    }

    private fun acceptLoop() {
        try {
            val ss = ServerSocket()
            ss.reuseAddress = true
            ss.bind(InetSocketAddress(port))
            serverSocket = ss
            lastStatus = "listening :$port"
            Log.i(TAG, "RTSP lauscht auf :$port  Pfad=/$streamPath")
            while (running) {
                val sock = try {
                    ss.accept()
                } catch (e: Exception) {
                    if (running) Log.w(TAG, "accept: ${e.message}")
                    break
                }
                sock.tcpNoDelay = true
                session?.close()
                val s = Session(sock)
                session = s
                lastStatus = "client ${sock.inetAddress.hostAddress}"
                Thread({ s.serve() }, "rtsp-video-session").apply { isDaemon = true; start() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "acceptLoop fatal: ${e.message}", e)
            lastStatus = "error: ${e.message}"
        }
    }

    // ─────────────────────────── Session ───────────────────────────
    private inner class Session(private val sock: Socket) {
        @Volatile var playing = false
        private val out: OutputStream = sock.getOutputStream()
        private val writeLock = Any()
        // Rebasiert RTP-Zeitstempel auf die erste gesendete AU dieser Session → erster Frame
        // ~Tick 0, deckungsgleich mit dem in PLAY gemeldeten RTP-Info rtptime=0 (Latenz-Fix).
        private val timestamper = RtpTimestamper()
        private var seq = 0
        private val ssrc = 0x13F97E67
        private var ivRtp = 0
        private var ivRtcp = 1
        private val sessionId = "DEADBEEF"

        fun close() {
            playing = false
            try { sock.close() } catch (_: Exception) {}
        }

        fun serve() {
            try {
                val reader = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.US_ASCII))
                while (running && !sock.isClosed) {
                    val requestLine = reader.readLine() ?: break
                    if (requestLine.isBlank()) continue
                    val method = requestLine.substringBefore(' ').uppercase()
                    // "METHOD <uri> RTSP/1.0" → die absolute Request-URI des Clients (für Content-Base).
                    val requestUri = requestLine.substringAfter(' ', "").substringBeforeLast(' ', "").trim()
                    val headers = HashMap<String, String>()
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                        val c = line.indexOf(':')
                        if (c > 0) headers[line.substring(0, c).trim().lowercase()] = line.substring(c + 1).trim()
                    }
                    val cseq = headers["cseq"] ?: "0"
                    Log.i(TAG, "RTSP <- $method (CSeq $cseq)")
                    when (method) {
                        "OPTIONS" -> respond(cseq, "Public: OPTIONS, DESCRIBE, SETUP, PLAY, TEARDOWN, GET_PARAMETER\r\n")
                        "DESCRIBE" -> handleDescribe(cseq, requestUri)
                        "SETUP" -> handleSetup(cseq, headers)
                        "PLAY" -> {
                            respond(
                                cseq,
                                "Session: $sessionId\r\n" +
                                    "RTP-Info: url=${controlUrl(requestUri)};seq=$seq;rtptime=0\r\n"
                            )
                            playing = true
                            Log.i(TAG, "PLAY -> Streaming aktiv")
                        }
                        "GET_PARAMETER" -> respond(cseq, "Session: $sessionId\r\n")
                        "TEARDOWN" -> { respond(cseq, "Session: $sessionId\r\n"); close(); return }
                        else -> respond(cseq, "", status = "501 Not Implemented")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Session beendet: ${e.message}")
            } finally {
                close()
            }
        }

        /** Basis-URL für Content-Base/Control: die Request-URI des Clients, sonst konstruiert. */
        private fun baseUrl(requestUri: String): String =
            if (requestUri.startsWith("rtsp://", ignoreCase = true)) requestUri.trimEnd('/')
            else "rtsp://0.0.0.0:$port/$streamPath"

        private fun controlUrl(requestUri: String): String = "${baseUrl(requestUri)}/streamid=0"

        private fun handleDescribe(cseq: String, requestUri: String) {
            // Auf SPS/PPS warten (Encoder liefert sie kurz nach Start).
            var waited = 0
            while ((sps == null || pps == null) && waited < 3000) {
                Thread.sleep(50); waited += 50
            }
            val s = sps; val p = pps
            if (s == null || p == null) {
                respond(cseq, "", status = "503 Service Unavailable")
                return
            }
            val pli = String.format("%02X%02X%02X", s[1].toInt() and 0xFF, s[2].toInt() and 0xFF, s[3].toInt() and 0xFF)
            val spropS = Base64.encodeToString(s, Base64.NO_WRAP)
            val spropP = Base64.encodeToString(p, Base64.NO_WRAP)
            val sdp = buildString {
                append("v=0\r\n")
                append("o=- 0 0 IN IP4 127.0.0.1\r\n")
                append("s=DrainQ-ONE\r\n")
                append("c=IN IP4 0.0.0.0\r\n")
                append("t=0 0\r\n")
                append("a=control:*\r\n")
                append("m=video 0 RTP/AVP 96\r\n")
                append("b=AS:4000\r\n")
                append("a=rtpmap:96 H264/90000\r\n")
                append("a=fmtp:96 packetization-mode=1;profile-level-id=$pli;sprop-parameter-sets=$spropS,$spropP\r\n")
                append("a=control:streamid=0\r\n")
            }
            respond(
                cseq,
                "Content-Base: ${baseUrl(requestUri)}/\r\n",
                body = sdp
            )
        }

        private fun handleSetup(cseq: String, headers: Map<String, String>) {
            val transport = headers["transport"] ?: ""
            if (!transport.contains("TCP", ignoreCase = true)) {
                // UDP nicht unterstützt — Client soll TCP-Interleaving nutzen.
                respond(cseq, "", status = "461 Unsupported Transport")
                return
            }
            Regex("interleaved=(\\d+)-(\\d+)").find(transport)?.let {
                ivRtp = it.groupValues[1].toInt()
                ivRtcp = it.groupValues[2].toInt()
            }
            respond(
                cseq,
                "Transport: RTP/AVP/TCP;unicast;interleaved=$ivRtp-$ivRtcp\r\n" +
                    "Session: $sessionId\r\n"
            )
        }

        private fun respond(cseq: String, extraHeaders: String, status: String = "200 OK", body: String = "") {
            val sb = StringBuilder()
            sb.append("RTSP/1.0 ").append(status).append("\r\n")
            sb.append("CSeq: ").append(cseq).append("\r\n")
            if (body.isNotEmpty()) {
                sb.append("Content-Type: application/sdp\r\n")
                sb.append("Content-Length: ").append(body.toByteArray(Charsets.US_ASCII).size).append("\r\n")
            }
            sb.append(extraHeaders)
            sb.append("\r\n")
            sb.append(body)
            synchronized(writeLock) {
                out.write(sb.toString().toByteArray(Charsets.US_ASCII))
                out.flush()
            }
        }

        // ──────────────────── RTP / H.264 (RFC 6184) ────────────────────
        fun sendAccessUnit(annexB: ByteArray, ptsUs: Long, keyframe: Boolean) {
            try {
                // Relativ zur ersten AU dieser Session (RTP-Info rtptime=0), 90-kHz-Clock, monoton.
                val rtpTs = timestamper.toRtpTicks(ptsUs)
                val nals = ArrayList<ByteArray>()
                if (keyframe) {
                    // SPS/PPS vor jedem Keyframe inband → robustes (Re-)Join.
                    sps?.let { nals.add(it) }
                    pps?.let { nals.add(it) }
                }
                for (n in splitAnnexB(annexB)) {
                    if (n.isNotEmpty()) {
                        val t = n[0].toInt() and 0x1F
                        if (t == 7 || t == 8 || t == 9) continue // SPS/PPS/AUD nicht doppelt
                        nals.add(n)
                    }
                }
                for (k in nals.indices) {
                    packetizeNal(nals[k], rtpTs, lastNalOfAu = k == nals.size - 1)
                }
            } catch (e: Exception) {
                Log.w(TAG, "sendAccessUnit: ${e.message}")
                close()
            }
        }

        private fun packetizeNal(nal: ByteArray, rtpTs: Int, lastNalOfAu: Boolean) {
            val max = 1400
            if (nal.size <= max) {
                sendRtp(nal, 0, nal.size, rtpTs, marker = lastNalOfAu, singleNal = true, fuHeader = 0)
                return
            }
            val nalHeader = nal[0].toInt()
            val fuIndicator = (nalHeader and 0xE0) or 28
            val type = nalHeader and 0x1F
            var offset = 1
            val payloadMax = max - 2
            while (offset < nal.size) {
                val chunk = minOf(payloadMax, nal.size - offset)
                val start = offset == 1
                val end = offset + chunk >= nal.size
                var fu = type
                if (start) fu = fu or 0x80
                if (end) fu = fu or 0x40
                sendFuA(fuIndicator, fu, nal, offset, chunk, rtpTs, marker = end && lastNalOfAu)
                offset += chunk
            }
        }

        private fun sendFuA(fuIndicator: Int, fuHeader: Int, nal: ByteArray, off: Int, len: Int, rtpTs: Int, marker: Boolean) {
            val payload = ByteArray(2 + len)
            payload[0] = fuIndicator.toByte()
            payload[1] = fuHeader.toByte()
            System.arraycopy(nal, off, payload, 2, len)
            sendRtp(payload, 0, payload.size, rtpTs, marker = marker, singleNal = false, fuHeader = 0)
        }

        private fun sendRtp(src: ByteArray, srcOff: Int, srcLen: Int, rtpTs: Int, marker: Boolean, singleNal: Boolean, fuHeader: Int) {
            val rtp = ByteArray(12 + srcLen)
            rtp[0] = 0x80.toByte() // V=2
            rtp[1] = ((if (marker) 0x80 else 0) or 96).toByte() // M + PT=96
            val s = seq; seq = (seq + 1) and 0xFFFF
            rtp[2] = (s ushr 8).toByte(); rtp[3] = s.toByte()
            rtp[4] = (rtpTs ushr 24).toByte(); rtp[5] = (rtpTs ushr 16).toByte()
            rtp[6] = (rtpTs ushr 8).toByte(); rtp[7] = rtpTs.toByte()
            rtp[8] = (ssrc ushr 24).toByte(); rtp[9] = (ssrc ushr 16).toByte()
            rtp[10] = (ssrc ushr 8).toByte(); rtp[11] = ssrc.toByte()
            System.arraycopy(src, srcOff, rtp, 12, srcLen)

            val frame = ByteArray(4)
            frame[0] = 0x24 // '$'
            frame[1] = ivRtp.toByte()
            frame[2] = (rtp.size ushr 8).toByte()
            frame[3] = rtp.size.toByte()
            synchronized(writeLock) {
                out.write(frame)
                out.write(rtp)
                out.flush()
            }
        }
    }
}
