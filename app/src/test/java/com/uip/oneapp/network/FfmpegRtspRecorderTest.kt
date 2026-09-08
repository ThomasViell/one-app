package com.uip.oneapp.network

import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.uip.oneapp.export.OsdBackground
import com.uip.oneapp.export.OsdColor
import com.uip.oneapp.export.OsdFontSize
import com.uip.oneapp.export.OsdSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class FfmpegRtspRecorderTest {

    private val defaultSettings = OsdSettings(enableOsdBurnIn = true)

    private fun drawtext(
        l1: String = "/tmp/l1.txt",
        l2: String = "/tmp/l2.txt",
        finding: String = "/tmp/finding.txt",
        settings: OsdSettings = defaultSettings
    ) = FfmpegRtspRecorder.buildDrawtextFilter(l1, l2, finding, settings)

    private fun cmd(
        rtsp: String = "rtsp://1.2.3.4:554/stream",
        out: String = "/out/rec.mp4",
        l1: String = "/tmp/l1.txt",
        l2: String = "/tmp/l2.txt",
        finding: String = "/tmp/finding.txt",
        settings: OsdSettings = defaultSettings
    ) = FfmpegRtspRecorder.buildFullCommand(rtsp, out, l1, l2, finding, settings)

    // ── buildDrawtextFilter ──────────────────────────────────────────────────

    @Test
    fun `buildDrawtextFilter contains reload=1 for dynamic meter updates`() {
        assertTrue("drawtext must reload each frame", drawtext().contains("reload=1"))
    }

    @Test
    fun `buildDrawtextFilter contains both textfile references`() {
        val f = drawtext()
        assertTrue(f.contains("l1.txt"))
        assertTrue(f.contains("l2.txt"))
    }

    @Test
    fun `buildDrawtextFilter uses green fontcolor for OsdColor_Green`() {
        assertTrue(drawtext(settings = defaultSettings.copy(fontColor = OsdColor.Green)).contains("0x64FF64FF"))
    }

    @Test
    fun `buildDrawtextFilter uses white fontcolor for OsdColor_White`() {
        assertTrue(drawtext(settings = defaultSettings.copy(fontColor = OsdColor.White)).contains("0xFFFFFFFF"))
    }

    @Test
    fun `buildDrawtextFilter uses yellow fontcolor for OsdColor_Yellow`() {
        assertTrue(drawtext(settings = defaultSettings.copy(fontColor = OsdColor.Yellow)).contains("0xFAE164FF"))
    }

    @Test
    fun `buildDrawtextFilter boxAlpha is 00 for Transparent`() {
        assertTrue(drawtext(settings = defaultSettings.copy(background = OsdBackground.Transparent)).contains("0x00000000"))
    }

    @Test
    fun `buildDrawtextFilter boxAlpha is 80 for SemiTransparent`() {
        assertTrue(drawtext(settings = defaultSettings.copy(background = OsdBackground.SemiTransparent)).contains("0x00000080"))
    }

    @Test
    fun `buildDrawtextFilter fontsize grows with OsdFontSize`() {
        fun firstFontSize(f: String): Int =
            Regex("fontsize=(\\d+)").find(f)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val small = drawtext(settings = defaultSettings.copy(fontSize = OsdFontSize.Small))
        val large = drawtext(settings = defaultSettings.copy(fontSize = OsdFontSize.Large))
        assertTrue(firstFontSize(large) > firstFontSize(small))
    }

    @Test
    fun `buildDrawtextFilter escapes colon in file path for FFmpeg filter`() {
        val pathWithColon = "C:/cache/osd_line1.txt"
        val f = drawtext(l1 = pathWithColon)
        assertFalse("unescaped colon breaks ffmpeg filter", f.contains("C:/cache/osd_line1.txt"))
        assertTrue("colon must be escaped with backslash", f.contains("C\\:/cache/osd_line1.txt"))
    }

    // ── finding-flash layer ──────────────────────────────────────────────────
    // Bug fix follow-up: damage observations (e.g. "Risse") were shown in the live
    // overlay but not burned into the video. The fix adds a third drawtext layer
    // backed by osd_rec_finding.txt that drawtext re-reads each frame.

    @Test
    fun `buildDrawtextFilter includes finding textfile reference`() {
        val f = drawtext(finding = "/tmp/my_finding.txt")
        assertTrue("finding layer must reference the file", f.contains("my_finding.txt"))
    }

    @Test
    fun `buildDrawtextFilter uses three drawtext layers when all flags on`() {
        val f = drawtext()
        val matches = Regex("drawtext=fontfile=").findAll(f).count()
        assertTrue("expected 3 drawtext layers (line1, line2, finding) but got $matches", matches == 3)
    }

    @Test
    fun `buildDrawtextFilter renders finding only when osd bars are off`() {
        val s = defaultSettings.copy(enableOsdBurnIn = false, enableFindingBurnIn = true)
        val f = drawtext(settings = s)
        val matches = Regex("drawtext=fontfile=").findAll(f).count()
        assertTrue("expected 1 drawtext layer (finding only) but got $matches", matches == 1)
        assertTrue(f.contains("finding"))
    }

    @Test
    fun `buildDrawtextFilter renders osd bars only when finding off`() {
        val s = defaultSettings.copy(enableOsdBurnIn = true, enableFindingBurnIn = false)
        val f = drawtext(settings = s)
        val matches = Regex("drawtext=fontfile=").findAll(f).count()
        assertTrue("expected 2 drawtext layers (line1+line2 only) but got $matches", matches == 2)
        assertFalse("finding layer must not be present", f.contains("finding"))
    }

    @Test
    fun `buildDrawtextFilter sets explicit fontfile on every drawtext layer (Android has no fontconfig)`() {
        // Without fontfile= ffmpeg fails with "Cannot find a valid font for the family Sans"
        // on Android (no fontconfig DB present) and the whole encoding aborts.
        val f = drawtext()
        val drawtextCount = Regex("drawtext=").findAll(f).count()
        val fontfileCount = Regex("fontfile=").findAll(f).count()
        assertTrue(
            "every drawtext layer needs an explicit fontfile (have $drawtextCount drawtext, $fontfileCount fontfile)",
            fontfileCount == drawtextCount
        )
        assertTrue("fontfile must point to a known Android system font",
                   f.contains("/system/fonts/Roboto-Regular.ttf"))
    }

    @Test
    fun `buildDrawtextFilter returns empty string when both layers off`() {
        val s = defaultSettings.copy(enableOsdBurnIn = false, enableFindingBurnIn = false)
        val f = drawtext(settings = s)
        assertTrue("empty filter must allow caller to drop -vf altogether", f.isEmpty())
    }

    @Test
    fun `buildDrawtextFilter finding layer is centered horizontally`() {
        val f = drawtext()
        assertTrue("finding must be centered with x=(w-text_w)/2", f.contains("x=(w-text_w)/2"))
    }

    @Test
    fun `buildDrawtextFilter finding layer uses prominent red box`() {
        val f = drawtext()
        // Solid-ish red box (with alpha 0xE0) makes the flash stand out from the
        // static dark bars on line1/line2.
        assertTrue("finding needs red highlight box", f.contains("0xCC0000"))
    }

    @Test
    fun `buildDrawtextFilter escapes colon in finding path too`() {
        val f = drawtext(finding = "C:/cache/finding.txt")
        assertTrue("finding path must escape colon", f.contains("C\\:/cache/finding.txt"))
    }

    // ── buildFullCommand ─────────────────────────────────────────────────────

    @Test
    fun `buildFullCommand uses rtsp_transport tcp`() {
        assertTrue(cmd().contains("-rtsp_transport tcp"))
    }

    @Test
    fun `buildFullCommand includes output path`() {
        assertTrue(cmd(out = "/out/rec.mp4").contains("/out/rec.mp4"))
    }

    @Test
    fun `buildFullCommand contains vf when enableOsdBurnIn is true`() {
        assertTrue(cmd(settings = defaultSettings.copy(enableOsdBurnIn = true)).contains("-vf"))
    }

    @Test
    fun `buildFullCommand contains vf for finding layer even when OSD bars are off (hardware OSD case)`() {
        val s = defaultSettings.copy(enableOsdBurnIn = false, enableFindingBurnIn = true)
        val c = cmd(settings = s)
        assertTrue("finding layer must still render when hardware OSD provides the static bars",
                   c.contains("-vf"))
        assertTrue("finding drawtext must be present", c.contains("finding"))
    }

    @Test
    fun `buildFullCommand omits vf only when both layers are off (without_overlay choice)`() {
        val s = defaultSettings.copy(enableOsdBurnIn = false, enableFindingBurnIn = false)
        assertFalse("explicit no-overlay must not add any drawtext",
                    cmd(settings = s).contains("-vf"))
    }

    @Test
    fun `buildFullCommand suppresses audio with -an`() {
        assertTrue("surveillance RTSP has no audio track", cmd().contains("-an"))
    }

    @Test
    fun `buildFullCommand uses libx264 encoder`() {
        assertTrue(cmd().contains("libx264"))
    }

    // ── muxer flags: fragmented MP4 ──────────────────────────────────────────

    @Test
    fun `buildFullCommand uses fragmented MP4 movflags so cancel cannot corrupt output`() {
        val c = cmd()
        assertTrue("frag_keyframe required",        c.contains("frag_keyframe"))
        assertTrue("empty_moov required",           c.contains("empty_moov"))
        assertTrue("default_base_moof recommended", c.contains("default_base_moof"))
    }

    @Test
    fun `buildFullCommand does NOT use faststart (would only matter after trailer)`() {
        assertFalse(
            "+faststart only relocates an existing moov; useless when ffmpeg is cancelled",
            cmd().contains("faststart")
        )
    }

    @Test
    fun `buildFullCommand sets explicit mp4 muxer to avoid format guessing`() {
        assertTrue(cmd().contains("-f mp4"))
    }

    @Test
    fun `buildFullCommand limits fragment duration to bound data loss on crash`() {
        assertTrue("frag_duration must be set", cmd().contains("-frag_duration"))
    }
}

/**
 * Kette ausstiegsmeldung (E-3 + Auflage des Beraters, 08.09.2026): Rueckgabekanal
 * stopRecording(onFinalized). Die echte FFmpegKit-Nativlast ist unter Robolectric nicht
 * ladbar (UnsatisfiedLinkError beim Klassenladen, gemessen 08.09.2026) — der Completion-
 * Ablauf wird deshalb ueber den injizierten RtspSessionRunner nachgestellt, ohne dass
 * FFmpegKit-Code laeuft. Der Remux laeuft ueber den injizierten RemuxDelegate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = android.app.Application::class)
class FfmpegRtspRecorderStopTest {

    private class FakeRunner : RtspSessionRunner {
        val completed = mutableListOf<(Int) -> Unit>()
        val cancelled = mutableListOf<Long>()
        private var nextId = 42L
        override fun executeAsync(command: String, onComplete: (Int) -> Unit, onLog: (String) -> Unit): Long {
            completed += onComplete
            return nextId++
        }
        override fun cancel(sessionId: Long) { cancelled += sessionId }
    }

    private val settings = OsdSettings(enableOsdBurnIn = true)

    private fun recorder(runner: FakeRunner, remux: RemuxDelegate): FfmpegRtspRecorder =
        FfmpegRtspRecorder(
            ApplicationProvider.getApplicationContext(),
            remuxDelegate = remux,
            sessionRunner = runner,
        )

    private fun tmpDir(name: String): File =
        File(System.getProperty("java.io.tmpdir"), "ausstieg_${name}_${System.nanoTime()}").apply { mkdirs() }

    @Test
    fun `Auflage Berater - Stopp-Taste dann sofort Zurueck - kein onFinalized trotz Callback unterwegs`() {
        val runner = FakeRunner()
        val remuxDone = CountDownLatch(1)
        val rec = recorder(runner) { s, d -> s.copyTo(d, overwrite = true); remuxDone.countDown(); true }
        val out = File(tmpDir("a"), "a.mp4")
        rec.startRecording("rtsp://1.2.3.4/stream", out, settings, "l1", "l2")
        assertEquals(FfmpegRecordingState.RECORDING, rec.state.value)
        // Live-Frag wie vom echten Encode: Datei existiert und traegt Daten.
        File(out.absolutePath + FRAG_SUFFIX).writeBytes(byteArrayOf(1, 2, 3, 4))

        // 1) Stopp-Taste: stopRecording() OHNE Callback — Session wird gecancelt und genullt.
        rec.stopRecording()
        assertEquals(listOf(42L), runner.cancelled)

        // 2) Sofort Zurueck: state steht noch auf RECORDING (Completion-Callback der ersten
        //    Session ist unterwegs), onDispose ruft stopRecording(melden) — s == null, es
        //    wird NICHTS gecancelt und die Absicht NICHT gesetzt (Auflage des Beraters).
        val meldungen = CopyOnWriteArrayList<String?>()
        rec.stopRecording { meldungen += it }
        assertEquals(1, runner.cancelled.size)
        assertEquals(FfmpegRecordingState.RECORDING, rec.state.value)

        // 3) Completion-Callback der ersten Session kommt (rc=255: gewollter Cancel).
        runner.completed.single().invoke(255)
        assertTrue("Remux der Stopp-Taste-Aufnahme muss zu Ende laufen", remuxDone.await(5, TimeUnit.SECONDS))

        // Bedingung 2: KEINE Meldung — die Aufnahme wurde per Stopp-Taste beendet.
        assertTrue("keine Meldung erwartet, war: $meldungen", meldungen.isEmpty())
        // Die MP4 der Stopp-Taste-Aufnahme ist trotzdem fertig (Finalisierung unveraendert).
        assertTrue(out.exists())
    }

    @Test
    fun `Zurueck mit laufender Aufnahme meldet NACH dem Remux mit dem finalen Pfad`() {
        val runner = FakeRunner()
        val remuxDone = CountDownLatch(1)
        val rec = recorder(runner) { s, d -> s.copyTo(d, overwrite = true); remuxDone.countDown(); true }
        val out = File(tmpDir("b"), "b.mp4")
        rec.startRecording("rtsp://1.2.3.4/stream", out, settings, "l1", "l2")
        File(out.absolutePath + FRAG_SUFFIX).writeBytes(byteArrayOf(1, 2, 3, 4))

        val meldungen = CopyOnWriteArrayList<String?>()
        rec.stopRecording { meldungen += it }
        assertEquals(listOf(42L), runner.cancelled)

        // Meldung darf erst nach der Finalisierung fallen (Bedingung 4) — der Remux-Delegat
        // hat beim Eintreffen der Meldung bereits gezahlt.
        assertEquals("vor dem Session-Ende darf nichts gemeldet sein", 0, meldungen.size)
        runner.completed.single().invoke(255)
        assertTrue(remuxDone.await(5, TimeUnit.SECONDS))
        val deadline = System.currentTimeMillis() + 2000
        while (meldungen.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertEquals(listOf(out.absolutePath), meldungen)
        assertEquals(FfmpegRecordingState.IDLE, rec.state.value)
    }

    @Test
    fun `Stale Callback aelterer Session feuert keine neuere Absicht (Epochen-Riegel)`() {
        val runner = FakeRunner()
        // Remux blockiert am Gate, bis BEIDE Completion-Callbacks gefeuert haben — so laufen
        // beide Abschluss-Pfade parallel, wie „Callback unterwegs" es verlangt.
        val gate = CountDownLatch(1)
        val remuxesDone = CountDownLatch(2)
        val rec = recorder(runner) { s, d ->
            gate.await()
            s.copyTo(d, overwrite = true)
            remuxesDone.countDown()
            true
        }
        val out1 = File(tmpDir("s1"), "s1.mp4")
        val out2 = File(tmpDir("s2"), "s2.mp4")

        rec.startRecording("rtsp://1.2.3.4/stream", out1, settings, "l1", "l2")
        File(out1.absolutePath + FRAG_SUFFIX).writeBytes(byteArrayOf(1))
        rec.stopRecording()                        // Stopp-Taste: S1 gecancelt, KEINE Absicht
        runner.completed[0].invoke(255)            // C1: state=IDLE, Remux R1 haengt am Gate
        assertEquals(FfmpegRecordingState.IDLE, rec.state.value)

        rec.startRecording("rtsp://1.2.3.4/stream", out2, settings, "l1", "l2")
        File(out2.absolutePath + FRAG_SUFFIX).writeBytes(byteArrayOf(2))
        val meldungen = CopyOnWriteArrayList<String?>()
        rec.stopRecording { meldungen += it }      // Zurueck: S2 gecancelt, Absicht Epoche 2
        runner.completed[1].invoke(255)            // C2: Remux R2 haengt am Gate

        gate.countDown()                           // beide Remuxe laufen los
        assertTrue(remuxesDone.await(5, TimeUnit.SECONDS))
        val deadline = System.currentTimeMillis() + 2000
        while (meldungen.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        // NUR die Aufnahme der Epoche 2 darf melden; R1 (Epoche 1, Stopp-Taste) sieht die
        // Absicht nicht — ohne Epochen-Riegel wuerden hier ZWEI Meldungen fallen.
        assertEquals(listOf(out2.absolutePath), meldungen)
    }

    @Test
    fun `harter Fehler meldet null (NOT_SAVED-Pfad)`() {
        val runner = FakeRunner()
        val rec = recorder(runner) { _, _ -> true }
        val out = File(tmpDir("e"), "e.mp4")
        rec.startRecording("rtsp://1.2.3.4/stream", out, settings, "l1", "l2")
        // Keine Frag-Datei, rc=1 (harter Fehler) → Fehlerzweig → onFinalized(null), synchron.
        val meldungen = CopyOnWriteArrayList<String?>()
        rec.stopRecording { meldungen += it }
        runner.completed.single().invoke(1)
        assertEquals(listOf(null), meldungen)
        assertEquals(FfmpegRecordingState.ERROR, rec.state.value)
    }

    @Test
    fun `stopRecording ohne Session ruft onFinalized nicht auf`() {
        val runner = FakeRunner()
        val rec = recorder(runner) { _, _ -> true }
        val meldungen = CopyOnWriteArrayList<String?>()
        rec.stopRecording { meldungen += it }
        assertTrue(runner.cancelled.isEmpty())
        assertTrue(meldungen.isEmpty())
    }
}
