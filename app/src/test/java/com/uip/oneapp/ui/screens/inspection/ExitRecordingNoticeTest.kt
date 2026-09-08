package com.uip.oneapp.ui.screens.inspection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Kette ausstiegsmeldung (E-8): Wahrheitstafel der Ausstiegs-Meldung.
 * wasRecording = Recorder-Wahrheit VOR dem Stopp (onDispose), result = onDone-Pfad.
 */
class ExitRecordingNoticeTest {

    // Bedingung 1: Kein Verlassen ohne Aufnahme loest eine Meldung aus.
    @Test fun keine_aufnahme_mit_pfad_keine_meldung() {
        assertNull(exitRecordingNotice(wasRecording = false, result = "/tmp/video.mp4"))
    }

    @Test fun keine_aufnahme_ohne_pfad_keine_meldung() {
        assertNull(exitRecordingNotice(wasRecording = false, result = null))
    }

    // Erfolgsfall: Aufnahme lief und die MP4 ist fertig → gespeichert.
    @Test fun aufnahme_mit_pfad_saved() {
        assertEquals(ExitRecordingNotice.SAVED, exitRecordingNotice(true, "/tmp/video.mp4"))
    }

    // Fehlerfall: onDone(null) = nichts Spielbares entstanden → nie „gespeichert" melden.
    @Test fun aufnahme_ohne_pfad_not_saved() {
        assertEquals(ExitRecordingNotice.NOT_SAVED, exitRecordingNotice(true, null))
    }

    // Leerer Pfad darf nicht als Erfolg zaehlen (onDone liefert ihn nie, aber die Funktion
    // darf ihn nicht als Erfolg werten).
    @Test fun aufnahme_mit_leerem_pfad_not_saved() {
        assertEquals(ExitRecordingNotice.NOT_SAVED, exitRecordingNotice(true, ""))
    }
}
