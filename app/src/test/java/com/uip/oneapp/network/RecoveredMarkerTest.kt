package com.uip.oneapp.network

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Welle 5a (Befund 3): ein nach Absturz wiederhergestelltes Video trägt einen dauerhaften Marker
 * `<video>.recovered`. [RecorderJournalMuxer.isRecovered] erkennt ihn (Liste/Bericht machen ihn sichtbar).
 * Reine Datei-Logik (kein MediaMuxer nötig).
 */
class RecoveredMarkerTest {

    @Test
    fun isRecovered_reflects_marker_presence() {
        val video = File.createTempFile("recovered_marker_test", ".mp4")
        val marker = File(video.absolutePath + RECOVERED_SUFFIX)
        try {
            assertFalse("ohne Marker nicht als wiederhergestellt gelten", RecorderJournalMuxer.isRecovered(video))
            marker.writeText("recovered\n")
            assertTrue("mit Marker als wiederhergestellt erkannt", RecorderJournalMuxer.isRecovered(video))
        } finally {
            video.delete()
            marker.delete()
        }
    }
}
