package com.uip.oneapp.network.video

import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Sichert ab, dass [tuneLowLatencySocket] tatsächlich `TCP_NODELAY` (Nagle aus) setzt — der
 * Latenz-Hebel auf dem RTP-über-TCP-Pfad ([RtspVideoServer]). Reines java.net über Loopback,
 * kein Android/Robolectric nötig.
 */
class SocketTuningTest {

    @Test
    fun `tuneLowLatencySocket enables tcpNoDelay on a connected socket`() {
        ServerSocket(0).use { server ->
            Socket().use { client ->
                client.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), server.localPort), 1_000)
                server.accept().use { accepted ->
                    // Default-Zustand ist Nagle AN (tcpNoDelay=false), bis wir es umschalten.
                    tuneLowLatencySocket(accepted)
                    assertTrue("Server-Socket muss TCP_NODELAY gesetzt haben", accepted.tcpNoDelay)
                }
            }
        }
    }

    @Test
    fun `tuneLowLatencySocket is idempotent`() {
        ServerSocket(0).use { server ->
            Socket().use { client ->
                client.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), server.localPort), 1_000)
                server.accept().use { accepted ->
                    tuneLowLatencySocket(accepted)
                    tuneLowLatencySocket(accepted)
                    assertTrue(accepted.tcpNoDelay)
                }
            }
        }
    }
}
