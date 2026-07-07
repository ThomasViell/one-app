package com.uip.oneapp.network

import android.os.Build
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Louis W2 (Video): Zustandslogik des Remux-beim-Stopp ohne echtes FFmpeg.
 * Der Remux-Aufruf ist ein injizierbarer Delegate — hier wird Erfolg/Fehlschlag gefaked und
 * geprüft, dass die fertige Datei am richtigen Pfad landet und die Aufnahme NIE verloren geht.
 *
 * Robolectric nur wegen android.util.Log (die Logik selbst arbeitet auf echten Temp-Dateien).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = android.app.Application::class)
class RecorderRemuxTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun frag(bytes: ByteArray = byteArrayOf(1, 2, 3, 4)): File =
        tmp.newFile("rec.mp4$FRAG_SUFFIX").apply { writeBytes(bytes) }

    private fun finalTarget(): File = File(tmp.root, "rec.mp4")

    // ── Erfolg: remux erzeugt die finale Datei ────────────────────────────────

    @Test
    fun `success returns final path and deletes frag`() = runTest {
        val f = frag()
        val out = finalTarget()
        // Fake-Remux: schreibt eine gültige Zieldatei und meldet Erfolg.
        val remux: RemuxDelegate = { _, dst -> dst.writeBytes(byteArrayOf(9, 9, 9)); true }

        val result = finalizeFragRecording(f, out, remux)

        assertEquals(out.absolutePath, result)
        assertTrue("finale Datei muss existieren", out.exists())
        assertFalse("Frag muss nach Erfolg gelöscht sein", f.exists())
    }

    // ── Fehlschlag: Fallback behält die Aufnahme als finale Datei ─────────────

    @Test
    fun `failure falls back to renamed frag and preserves recording`() = runTest {
        val payload = byteArrayOf(7, 7, 7, 7, 7)
        val f = frag(payload)
        val out = finalTarget()
        // Fake-Remux: meldet Fehlschlag und erzeugt KEINE Zieldatei.
        val remux: RemuxDelegate = { _, _ -> false }

        val result = finalizeFragRecording(f, out, remux)

        assertEquals("Fallback muss den finalen Pfad zurückgeben", out.absolutePath, result)
        assertTrue("Aufnahme darf nicht verloren gehen", out.exists())
        assertArrayEqualsMsg("finale Datei trägt die Frag-Bytes", payload, out.readBytes())
        assertFalse("Frag wurde in die finale Datei umbenannt", f.exists())
    }

    @Test
    fun `remux throwing is treated as failure and still preserves recording`() = runTest {
        val f = frag(byteArrayOf(3, 3, 3))
        val out = finalTarget()
        val remux: RemuxDelegate = { _, _ -> throw RuntimeException("ffmpeg blew up") }

        val result = finalizeFragRecording(f, out, remux)

        assertEquals(out.absolutePath, result)
        assertTrue(out.exists())
        assertFalse(f.exists())
    }

    @Test
    fun `remux reporting success but leaving no file falls back to frag`() = runTest {
        val payload = byteArrayOf(5, 5)
        val f = frag(payload)
        val out = finalTarget()
        // Delegate lügt: true, aber ohne Zieldatei. finalize muss das erkennen und den Fallback nehmen.
        val remux: RemuxDelegate = { _, _ -> true }

        val result = finalizeFragRecording(f, out, remux)

        assertEquals(out.absolutePath, result)
        assertTrue(out.exists())
        assertArrayEqualsMsg("Frag-Bytes bleiben erhalten", payload, out.readBytes())
        assertFalse(f.exists())
    }

    @Test
    fun `fallback overwrites a pre-existing partial final file`() = runTest {
        val payload = byteArrayOf(1, 1, 1, 1)
        val f = frag(payload)
        val out = finalTarget().apply { writeBytes(byteArrayOf(0)) } // altes/leeres Ziel vorhanden
        val remux: RemuxDelegate = { _, _ -> false }

        val result = finalizeFragRecording(f, out, remux)

        assertEquals(out.absolutePath, result)
        assertArrayEqualsMsg("altes Ziel wird durch die Aufnahme ersetzt", payload, out.readBytes())
        assertFalse(f.exists())
    }

    // ── Nichts aufgenommen ────────────────────────────────────────────────────

    @Test
    fun `missing frag returns null`() = runTest {
        val f = File(tmp.root, "does_not_exist.mp4$FRAG_SUFFIX")
        val result = finalizeFragRecording(f, finalTarget()) { _, _ -> true }
        assertNull(result)
    }

    @Test
    fun `empty frag returns null`() = runTest {
        val f = tmp.newFile("empty.mp4$FRAG_SUFFIX") // 0 Bytes
        val result = finalizeFragRecording(f, finalTarget()) { _, _ -> true }
        assertNull(result)
    }

    @Test
    fun `missing frag but existing final returns final path`() = runTest {
        val f = File(tmp.root, "gone.mp4$FRAG_SUFFIX")
        val out = finalTarget().apply { writeBytes(byteArrayOf(4, 4, 4)) }
        val result = finalizeFragRecording(f, out) { _, _ -> true }
        assertEquals(out.absolutePath, result)
    }

    // ── Orphan-Cleanup ────────────────────────────────────────────────────────

    @Test
    fun `cleanupOrphanFrags deletes only frag files`() {
        val orphan1 = tmp.newFile("a.mp4$FRAG_SUFFIX").apply { writeBytes(byteArrayOf(1)) }
        val orphan2 = tmp.newFile("b.mp4$FRAG_SUFFIX").apply { writeBytes(byteArrayOf(1)) }
        val keepVideo = tmp.newFile("c.mp4").apply { writeBytes(byteArrayOf(1)) }
        val keepOther = tmp.newFile("notes.txt").apply { writeBytes(byteArrayOf(1)) }

        cleanupOrphanFrags(tmp.root)

        assertFalse(orphan1.exists())
        assertFalse(orphan2.exists())
        assertTrue("normale Aufnahme bleibt", keepVideo.exists())
        assertTrue("fremde Datei bleibt", keepOther.exists())
    }

    @Test
    fun `cleanupOrphanFrags tolerates null and missing dir`() {
        cleanupOrphanFrags(null)
        cleanupOrphanFrags(File(tmp.root, "no_such_dir"))
        // kein Crash = bestanden
    }

    private fun assertArrayEqualsMsg(msg: String, expected: ByteArray, actual: ByteArray) {
        assertTrue("$msg (len ${expected.size} vs ${actual.size})", expected.size == actual.size)
        for (i in expected.indices) assertEquals(msg, expected[i], actual[i])
    }
}
