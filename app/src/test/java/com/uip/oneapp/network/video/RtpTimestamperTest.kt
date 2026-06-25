package com.uip.oneapp.network.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sichert die RTP-Zeitstempel-Umrechnung ([usToRtpTicks], [RtpTimestamper]) ab — der Kern des
 * Glass-to-Glass-Latenz-Fixes (W3c-Video). Reines Kotlin, kein Socket/Android.
 *
 * Vorbefund: Frames trugen absolute PTS "seit Encoder-Start"; der bei `PLAY` gemeldete
 * `RTP-Info: rtptime=0` passte nicht zum ersten gesendeten Frame → ~60 s Start-Latenz im
 * ExoPlayer. Diese Tests fixieren: (1) korrekte 90-kHz-Schrittweite, (2) erster Frame ⇒ Tick 0
 * unabhängig von der absoluten PTS, (3) Monotonie + Erhalt der echten Frame-Abstände.
 */
class RtpTimestamperTest {

    private val clock = 90_000L // 90 kHz

    @Test
    fun `usToRtpTicks maps one second to 90000 ticks`() {
        assertEquals(clock.toInt(), usToRtpTicks(1_000_000L))
    }

    @Test
    fun `usToRtpTicks maps zero to zero`() {
        assertEquals(0, usToRtpTicks(0L))
    }

    @Test
    fun `usToRtpTicks maps one 30fps frame to about 3000 ticks`() {
        // 1/30 s = 33333 µs → 33333*9/100 = 2999 (Trunkierung, < 1 Tick Abweichung)
        assertEquals(2999, usToRtpTicks(33_333L))
    }

    @Test
    fun `first access unit rebases to tick zero regardless of absolute pts`() {
        // Der eigentliche Bug: Client verbindet sich 60 s nach Encoder-Start.
        val ts = RtpTimestamper()
        assertEquals(0, ts.toRtpTicks(60_000_000L))
    }

    @Test
    fun `subsequent ticks are relative to the first frame`() {
        val ts = RtpTimestamper()
        ts.toRtpTicks(60_000_000L)                 // Basis: 60 s
        // Nächster Frame 1/30 s später → ~3000 Ticks relativ, NICHT 5.4M (absolut).
        val second = ts.toRtpTicks(60_033_333L)
        assertEquals(2999, second)
    }

    @Test
    fun `ticks increase monotonically for monotonic pts`() {
        val ts = RtpTimestamper()
        var prev = ts.toRtpTicks(5_000_000L) // Start mit großer absoluter PTS
        var pts = 5_000_000L
        repeat(300) { // 10 s @ 30 fps
            pts += 33_333L
            val tick = ts.toRtpTicks(pts)
            assertTrue("tick $tick muss > prev $prev sein", tick > prev)
            prev = tick
        }
    }

    @Test
    fun `inter-frame spacing is preserved by rebasing`() {
        // Rebasing verschiebt nur den Nullpunkt, nicht die Abstände.
        val ts = RtpTimestamper()
        val base = 12_345_678L
        val t0 = ts.toRtpTicks(base)
        val t1 = ts.toRtpTicks(base + 1_000_000L) // +1 s
        assertEquals(0, t0)
        assertEquals(clock.toInt(), t1 - t0)
    }
}
