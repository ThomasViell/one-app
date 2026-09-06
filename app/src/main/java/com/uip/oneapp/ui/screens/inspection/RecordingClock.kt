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
