package com.uip.oneapp.ui.screens.inspection

import com.uip.oneapp.network.RecordingState

/**
 * Auftrag bedienbild Z-2 (F-3): Laufzeit aus Zustandsübergängen, nicht aus Schleifentakt —
 * kein Nachlaufen, keine Drift. Pausiert bei PAUSED (CEO-Entscheid), friert bei FINISHING ein.
 * Reines Kotlin ohne Android-Abhängigkeit, damit ein JVM-Test mit Fake-Uhr möglich ist.
 */
class RecordingClock(private val now: () -> Long = System::currentTimeMillis) {
    private var accumulatedMs = 0L
    private var runningSince: Long? = null

    fun onState(state: RecordingState) {
        when (state) {
            RecordingState.RECORDING -> if (runningSince == null) runningSince = now()
            RecordingState.PAUSED, RecordingState.FINISHING -> {
                runningSince?.let { accumulatedMs += now() - it }
                runningSince = null
            }
            RecordingState.IDLE -> {
                accumulatedMs = 0L
                runningSince = null
            }
        }
    }

    fun elapsedMs(): Long = accumulatedMs + (runningSince?.let { now() - it } ?: 0L)
}

/**
 * Auftrag bedienbild Z-2, Nachbesserung Runde 2 (N-1/N-2): der tatsaechliche Aufnahmezustand,
 * getrennt vom Anzeigezustand (`recIndicator` in InspectionScreen, der IDLE auf `null`
 * abbildet, um die Zeile unsichtbar zu machen). `RecordingClock.onState` muss IMMER mit diesem
 * Wert gefuettert werden, auch mit IDLE — sonst ist der Ruecksetzpfad unerreichbar (N-1).
 *
 * `localState` kommt vom lokalen Recorder (kennt FINISHING/PAUSED/RECORDING/IDLE) und hat
 * Vorrang, solange er nicht IDLE ist. Sonst zaehlt `isRecording` — der RTSP-Pfad kennt nur
 * an/aus (`FfmpegRecordingState.RECORDING`/`IDLE`), der Uebergang true→false muss daher direkt
 * auf IDLE abbilden, ohne FINISHING dazwischen (das dieser Pfad nicht liefert).
 */
fun recordingStateFor(localState: RecordingState, isRecording: Boolean): RecordingState = when {
    localState != RecordingState.IDLE -> localState
    isRecording -> RecordingState.RECORDING
    else -> RecordingState.IDLE
}

fun formatElapsed(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
