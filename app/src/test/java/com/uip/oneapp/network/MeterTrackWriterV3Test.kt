package com.uip.oneapp.network

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * Welle 5 — v3-Writer→Reader-Roundtrip auf der JVM (nur java.io, kein Android).
 * Prüft: v3-Kopf, tUs-Samples, Locale-Unabhängigkeit, idempotenter stop, ungültige Sidecar → EMPTY.
 */
class MeterTrackWriterV3Test {

    private lateinit var videoFile: File
    private lateinit var sidecar: File
    private var defaultLocale: Locale = Locale.getDefault()

    @Before
    fun setup() {
        videoFile = File.createTempFile("meter_v3_test", ".mp4")
        sidecar = File(videoFile.absolutePath + METER_SIDECAR_SUFFIX)
    }

    @After
    fun teardown() {
        Locale.setDefault(defaultLocale)
        videoFile.delete()
        sidecar.delete()
    }

    @Test
    fun roundtrip_header_v3_and_time_samples() {
        val w = MeterTrackWriterV3(videoFile)
        w.start()
        w.onSample(0, 0.00f)
        w.onSample(1_000_000, 1.25f)
        w.onSample(2_500_000, 2.50f)
        w.stop()

        // Kopf ist v3.
        assertTrue(sidecar.readText().startsWith("{\"v\":3}"))

        val track = MeterTrackReaderV3.read(videoFile)
        assertEquals(3, track.samples.size)
        assertEquals(0L, track.samples[0].tUs)
        assertEquals(1_000_000L, track.samples[1].tUs)
        assertEquals(2.50f, track.samples[2].meter, 0.001f)
    }

    @Test
    fun meter_formatting_is_locale_independent() {
        Locale.setDefault(Locale.GERMANY)
        val w = MeterTrackWriterV3(videoFile)
        w.start()
        w.onSample(0, 12.34f)
        w.stop()

        val raw = sidecar.readText()
        assertTrue("JSON muss '.' als Dezimaltrenner nutzen", raw.contains("12.34"))
        assertFalse("Kein Komma-Dezimaltrenner", raw.contains("12,34"))

        assertEquals(12.34f, MeterTrackReaderV3.read(videoFile).samples.first().meter, 0.001f)
    }

    @Test
    fun stop_is_idempotent() {
        val w = MeterTrackWriterV3(videoFile)
        w.start()
        w.onSample(0, 1.0f)
        w.stop()
        w.stop()   // darf nicht werfen
        assertEquals(1, MeterTrackReaderV3.read(videoFile).samples.size)
    }

    @Test
    fun no_sidecar_reads_empty() {
        // Video ohne Sidecar → EMPTY (Stufe-1-Fallback).
        assertEquals(MeterTrackV3.EMPTY, MeterTrackReaderV3.read(videoFile))
    }
}
