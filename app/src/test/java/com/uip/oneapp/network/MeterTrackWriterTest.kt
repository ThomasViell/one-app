package com.uip.oneapp.network

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * Welle 4b — Writer→Reader-Roundtrip auf der JVM (MeterTrackWriter nutzt nur java.io, kein Android).
 * Prüft: Kopfzeile mit tatsächlicher fps, Frame 0 IMMER gesampelt, Dezimierung, Locale-Unabhängigkeit.
 */
class MeterTrackWriterTest {

    private lateinit var videoFile: File
    private lateinit var sidecar: File
    private var defaultLocale: Locale = Locale.getDefault()

    @Before
    fun setup() {
        videoFile = File.createTempFile("meter_track_test", ".mp4")
        sidecar = File(videoFile.absolutePath + METER_SIDECAR_SUFFIX)
    }

    @After
    fun teardown() {
        Locale.setDefault(defaultLocale)
        videoFile.delete()
        sidecar.delete()
    }

    @Test
    fun frame_zero_is_always_sampled_and_header_has_actual_fps() {
        val w = MeterTrackWriter(videoFile)
        w.start(12)                       // fps=12 → sampleEvery=2
        w.onFrame(0, 0.00f)               // Frame 0 MUSS gesampelt werden
        w.onFrame(1, 0.10f)               // ungerade → dezimiert weg
        w.onFrame(2, 0.20f)
        w.stop()

        val track = MeterTrackReader.read(videoFile)
        assertEquals(12, track.fps)
        assertTrue("Frame 0 muss enthalten sein", track.samples.any { it.frameIndex == 0 })
        // Dezimierung: nur gerade Frames (0, 2), Frame 1 fehlt.
        assertEquals(listOf(0, 2), track.samples.map { it.frameIndex })
    }

    @Test
    fun meter_formatting_is_locale_independent() {
        // Deutsches Locale nutzt normalerweise ',' als Dezimaltrenner → würde JSON zerbrechen.
        Locale.setDefault(Locale.GERMANY)
        val w = MeterTrackWriter(videoFile)
        w.start(5)                        // sampleEvery=1 → jeder Frame
        w.onFrame(0, 12.34f)
        w.stop()

        // Rohtext muss '.' enthalten, nicht ','.
        val raw = sidecar.readText()
        assertTrue("JSON muss '.' als Dezimaltrenner nutzen", raw.contains("12.34"))
        assertFalse("Kein Komma-Dezimaltrenner", raw.contains("12,34"))

        // Und der Reader liest den Wert korrekt zurück.
        val track = MeterTrackReader.read(videoFile)
        assertEquals(12.34f, track.samples.first().meter, 0.001f)
    }

    @Test
    fun start_with_invalid_fps_writes_nothing() {
        val w = MeterTrackWriter(videoFile)
        w.start(0)
        w.onFrame(0, 1.0f)
        w.stop()
        // Kein gültiger Header → Reader liefert EMPTY (Stufe-1-Fallback).
        assertEquals(MeterTrack.EMPTY, MeterTrackReader.read(videoFile))
    }

    @Test
    fun stop_is_idempotent() {
        val w = MeterTrackWriter(videoFile)
        w.start(15)
        w.onFrame(0, 1.0f)
        w.stop()
        w.stop()   // darf nicht werfen
        assertEquals(15, MeterTrackReader.read(videoFile).fps)
    }
}
