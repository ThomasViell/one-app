package com.uip.oneapp.network.video

import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Sichert die Encoder-[MediaFormat]-Konfiguration ([buildAvcFormat]) ab — die Server-seitigen
 * Latenz-Hebel (W3c-Video): kurze GOP (häufige IDR) und die Low-Latency-Encoder-Schlüssel.
 * MediaFormat ist unter Robolectric echtes AOSP (reine Java-Map), daher ohne Gerät prüfbar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = android.app.Application::class)
class H264EncoderFormatTest {

    private fun format(
        iFrameIntervalSec: Float = 0.5f,
        lowLatency: Boolean = true,
    ): MediaFormat = buildAvcFormat(
        width = 1280,
        height = 720,
        frameRate = 30,
        bitRate = 4_000_000,
        iFrameIntervalSec = iFrameIntervalSec,
        enableLowLatencyKeys = lowLatency,
    )

    @Test
    fun `basic video parameters are set`() {
        val f = format()
        assertEquals(MediaFormat.MIMETYPE_VIDEO_AVC, f.getString(MediaFormat.KEY_MIME))
        assertEquals(1280, f.getInteger(MediaFormat.KEY_WIDTH))
        assertEquals(720, f.getInteger(MediaFormat.KEY_HEIGHT))
        assertEquals(30, f.getInteger(MediaFormat.KEY_FRAME_RATE))
        assertEquals(4_000_000, f.getInteger(MediaFormat.KEY_BIT_RATE))
    }

    @Test
    fun `gop is sub-second and stored as float`() {
        // Latenz-Hebel 1: 0.5 s GOP statt 1 s. Muss als Float landen (setInteger würde 0.5 → 0
        // abschneiden = jeder Frame IDR), damit der Encoder die Sub-Sekunden-GOP ehren kann.
        val f = format(iFrameIntervalSec = 0.5f)
        assertEquals(0.5f, f.getFloat(MediaFormat.KEY_I_FRAME_INTERVAL), 0.0001f)
    }

    @Test
    fun `quarter-second gop is honoured by the builder`() {
        val f = format(iFrameIntervalSec = 0.25f)
        assertEquals(0.25f, f.getFloat(MediaFormat.KEY_I_FRAME_INTERVAL), 0.0001f)
    }

    @Test
    fun `no b-frames to avoid decoder reorder delay`() {
        // Latenz-Hebel 4: B-Frames würden eine Frame-Dauer Reorder-Delay kosten.
        val f = format()
        assertEquals(0, f.getInteger(MediaFormat.KEY_MAX_B_FRAMES))
    }

    @Test
    fun `cbr rate control for constant live bandwidth`() {
        val f = format()
        assertEquals(
            MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR,
            f.getInteger(MediaFormat.KEY_BITRATE_MODE)
        )
    }

    @Test
    fun `low-latency keys present on api 30 plus`() {
        // Latenz-Hebel 4: realtime, minimale Encoder-Ausgabepufferung.
        val f = format(lowLatency = true)
        assertEquals(1, f.getInteger(MediaFormat.KEY_LATENCY))
        assertEquals(0, f.getInteger(MediaFormat.KEY_PRIORITY)) // realtime
    }

    @Test
    fun `low-latency keys absent below api 30`() {
        val f = format(lowLatency = false)
        assertFalse(f.containsKey(MediaFormat.KEY_LATENCY))
        assertFalse(f.containsKey(MediaFormat.KEY_PRIORITY))
    }

    @Test
    fun `color format is yuv420 flexible`() {
        val f = format()
        assertTrue(f.containsKey(MediaFormat.KEY_COLOR_FORMAT))
        assertEquals(
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
            f.getInteger(MediaFormat.KEY_COLOR_FORMAT)
        )
    }
}
