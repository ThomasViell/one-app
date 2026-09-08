package com.uip.oneapp.ui.screens.inspection

/**
 * Kette ausstiegsmeldung (Z-1): Entscheidung, ob beim Verlassen des Inspektionsbildschirms
 * eine Meldung „beendet + gespeichert" gezeigt wird. Reine Funktion, damit die Bedingungen
 * 1–4 des Auftrags unit-testbar sind (ExitRecordingNoticeTest).
 */
enum class ExitRecordingNotice { SAVED, NOT_SAVED }

/**
 * [wasRecording] = Recorder-Wahrheit VOR dem Stopp (in onDispose eingefangen, nicht die
 * UI-Variable isRecording — die kann kurz von der Recorder-Wahrheit abweichen).
 * [result] = Pfad aus dem onDone-Callback des Recorders (fertige MP4) oder null,
 * wenn nichts Spielbares entstanden ist. Ein leerer Pfad zaehlt als Fehlschlag:
 * onDone liefert ihn nie, aber „gespeichert" darf nie vor einer fertigen Datei stehen.
 *
 * @return null = keine Meldung; SAVED = gespeichert; NOT_SAVED = beendet, aber kein Video.
 */
fun exitRecordingNotice(wasRecording: Boolean, result: String?): ExitRecordingNotice? {
    if (!wasRecording) return null
    return if (result.isNullOrEmpty()) ExitRecordingNotice.NOT_SAVED else ExitRecordingNotice.SAVED
}
