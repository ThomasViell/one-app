package com.uip.oneapp.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * **Prozessweiter Aufnahmezustand (Kette kiosk-pflicht, Runde 5, P-1; CEO-Entscheid
 * 04.09.2026, Variante A).** Der Ausstieg aus der App ist gesperrt, solange eine
 * Aufzeichnung läuft — sonst verwirft `InspectionScreen.onDispose` → `Recorder.cancel()`
 * die laufende Aufnahme (Journal + Meter-Spur gelöscht, nie finalisiert; Befund
 * KLICKDURCHGANG_CEO_20260904.md Punkt 6).
 *
 * Getrieben von `FallbackRecorder.mirror` — damit deckt das Signal beide lokalen
 * Aufnahmepfade ab (HardwareBitmapRecorder UND den LocalBitmapRecorder-Rückfall) in allen
 * Zuständen ungleich IDLE (RECORDING, PAUSED, FINISHING). Der RTSP/Ffmpeg-Pfad läuft nur
 * auf Tablets (kein Kiosk, keine „App verlassen"-Zeile) und ist dort über das
 * `recordingActive`-Gate des Power-Dialogs im InspectionScreen abgedeckt.
 *
 * Leser: `SettingsScreen` (Knopf „Beenden" ausgegraut + Hinweis) und
 * `MainActivity.leaveApp()` (harte Sperre als letzte Ebene, falls ein künftiger Ausstiegsweg
 * das UI-Gate umgeht). Koin-Single — EINE Instanz pro Prozess.
 *
 * Reine Zustandslogik ohne Android-Abhängigkeit → JVM-unit-testbar.
 */
class RecordingStateBus {

    private val _active = MutableStateFlow(false)

    /** true solange irgendein lokaler Aufnahmepfad einen Zustand ungleich IDLE hält. */
    val active: StateFlow<Boolean> = _active.asStateFlow()

    /** Nur vom Recorder-Spiegel (FallbackRecorder) gesetzt. */
    fun setActive(active: Boolean) {
        _active.value = active
    }
}
