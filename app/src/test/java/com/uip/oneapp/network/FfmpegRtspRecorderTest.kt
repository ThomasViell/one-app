package com.uip.oneapp.network

import com.uip.oneapp.export.OsdBackground
import com.uip.oneapp.export.OsdColor
import com.uip.oneapp.export.OsdFontSize
import com.uip.oneapp.export.OsdSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FfmpegRtspRecorderTest {

    private val defaultSettings = OsdSettings(enableOsdBurnIn = true)

    // ── buildDrawtextFilter ──────────────────────────────────────────────────

    @Test
    fun `buildDrawtextFilter contains reload=1 for dynamic meter updates`() {
        val f = FfmpegRtspRecorder.buildDrawtextFilter("/tmp/l1.txt", "/tmp/l2.txt", defaultSettings)
        assertTrue("drawtext must reload each frame", f.contains("reload=1"))
    }

    @Test
    fun `buildDrawtextFilter contains both textfile references`() {
        val f = FfmpegRtspRecorder.buildDrawtextFilter("/tmp/l1.txt", "/tmp/l2.txt", defaultSettings)
        assertTrue(f.contains("l1.txt"))
        assertTrue(f.contains("l2.txt"))
    }

    @Test
    fun `buildDrawtextFilter uses green fontcolor for OsdColor_Green`() {
        val settings = defaultSettings.copy(fontColor = OsdColor.Green)
        val f = FfmpegRtspRecorder.buildDrawtextFilter("/tmp/l1.txt", "/tmp/l2.txt", settings)
        assertTrue(f.contains("0x64FF64FF"))
    }

    @Test
    fun `buildDrawtextFilter uses white fontcolor for OsdColor_White`() {
        val settings = defaultSettings.copy(fontColor = OsdColor.White)
        val f = FfmpegRtspRecorder.buildDrawtextFilter("/tmp/l1.txt", "/tmp/l2.txt", settings)
        assertTrue(f.contains("0xFFFFFFFF"))
    }

    @Test
    fun `buildDrawtextFilter uses yellow fontcolor for OsdColor_Yellow`() {
        val settings = defaultSettings.copy(fontColor = OsdColor.Yellow)
        val f = FfmpegRtspRecorder.buildDrawtextFilter("/tmp/l1.txt", "/tmp/l2.txt", settings)
        assertTrue(f.contains("0xFAE164FF"))
    }

    @Test
    fun `buildDrawtextFilter boxAlpha is 00 for Transparent`() {
        val settings = defaultSettings.copy(background = OsdBackground.Transparent)
        val f = FfmpegRtspRecorder.buildDrawtextFilter("/tmp/l1.txt", "/tmp/l2.txt", settings)
        assertTrue(f.contains("0x00000000"))
    }

    @Test
    fun `buildDrawtextFilter boxAlpha is 80 for SemiTransparent`() {
        val settings = defaultSettings.copy(background = OsdBackground.SemiTransparent)
        val f = FfmpegRtspRecorder.buildDrawtextFilter("/tmp/l1.txt", "/tmp/l2.txt", settings)
        assertTrue(f.contains("0x00000080"))
    }

    @Test
    fun `buildDrawtextFilter fontsize grows with OsdFontSize`() {
        val small = FfmpegRtspRecorder.buildDrawtextFilter("/tmp/l1.txt", "/tmp/l2.txt", defaultSettings.copy(fontSize = OsdFontSize.Small))
        val large = FfmpegRtspRecorder.buildDrawtextFilter("/tmp/l1.txt", "/tmp/l2.txt", defaultSettings.copy(fontSize = OsdFontSize.Large))
        // Extract first fontsize= value and compare
        fun extractFirstFontSize(f: String): Int =
            Regex("fontsize=(\\d+)").find(f)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        assertTrue(extractFirstFontSize(large) > extractFirstFontSize(small))
    }

    @Test
    fun `buildDrawtextFilter escapes colon in file path for FFmpeg filter`() {
        // Simulate a path containing a colon (e.g. Windows dev environment C:\path)
        val pathWithColon = "C:/cache/osd_line1.txt"
        val f = FfmpegRtspRecorder.buildDrawtextFilter(pathWithColon, "/tmp/l2.txt", defaultSettings)
        // Colon must be escaped as \: for FFmpeg filter graph — raw colon would terminate parameter
        assertFalse("unescaped colon breaks ffmpeg filter", f.contains("C:/cache/osd_line1.txt"))
        assertTrue("colon must be escaped with backslash", f.contains("C\\:/cache/osd_line1.txt"))
    }

    // ── buildFullCommand ─────────────────────────────────────────────────────

    @Test
    fun `buildFullCommand uses rtsp_transport tcp`() {
        val cmd = FfmpegRtspRecorder.buildFullCommand(
            "rtsp://1.2.3.4:554/stream", "/out/rec.mp4",
            "/tmp/l1.txt", "/tmp/l2.txt", defaultSettings
        )
        assertTrue(cmd.contains("-rtsp_transport tcp"))
    }

    @Test
    fun `buildFullCommand includes output path`() {
        val cmd = FfmpegRtspRecorder.buildFullCommand(
            "rtsp://1.2.3.4:554/stream", "/out/rec.mp4",
            "/tmp/l1.txt", "/tmp/l2.txt", defaultSettings
        )
        assertTrue(cmd.contains("/out/rec.mp4"))
    }

    @Test
    fun `buildFullCommand contains vf when enableOsdBurnIn is true`() {
        val cmd = FfmpegRtspRecorder.buildFullCommand(
            "rtsp://1.2.3.4:554/stream", "/out/rec.mp4",
            "/tmp/l1.txt", "/tmp/l2.txt", defaultSettings.copy(enableOsdBurnIn = true)
        )
        assertTrue(cmd.contains("-vf"))
    }

    @Test
    fun `buildFullCommand omits vf when enableOsdBurnIn is false`() {
        val cmd = FfmpegRtspRecorder.buildFullCommand(
            "rtsp://1.2.3.4:554/stream", "/out/rec.mp4",
            "/tmp/l1.txt", "/tmp/l2.txt", defaultSettings.copy(enableOsdBurnIn = false)
        )
        assertFalse(cmd.contains("-vf"))
    }

    @Test
    fun `buildFullCommand suppresses audio with -an`() {
        val cmd = FfmpegRtspRecorder.buildFullCommand(
            "rtsp://1.2.3.4:554/stream", "/out/rec.mp4",
            "/tmp/l1.txt", "/tmp/l2.txt", defaultSettings
        )
        assertTrue("surveillance RTSP has no audio track", cmd.contains("-an"))
    }

    @Test
    fun `buildFullCommand uses libx264 encoder`() {
        val cmd = FfmpegRtspRecorder.buildFullCommand(
            "rtsp://1.2.3.4:554/stream", "/out/rec.mp4",
            "/tmp/l1.txt", "/tmp/l2.txt", defaultSettings
        )
        assertTrue(cmd.contains("libx264"))
    }
}
