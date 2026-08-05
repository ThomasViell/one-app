/clean
/goal: 0.5.14 — Kameratyp bei Kopfwechsel akkumulieren + Projektliste zeigt Auftraggeber + HW-Recorder-Wahlschalter raus (unsichtbarer Auto-Rückfall)
/model: sonnet
/effort: mittel

# Sammel-Auftrag 0.5.14 — drei freigegebene Änderungen

Quelle: Gerätetests + CEO-Entscheide 13.07.2026. Branch `feature/dual-mode`. Kein Merge, kein Tag.
Drei unabhängige Teile. Nur die je genannten Dateien anfassen. Am Ende zusammen committen.
Line-Nummern sind Anhaltspunkte (können minimal abweichen) — im Zweifel per Inhalt/Muster lokalisieren.

---

## TEIL A — Kameratyp bei Kopfwechsel akkumulieren

Befund: Wird während eines Projekts der Kopf gewechselt (C18→C10), bleibt `kameratyp` auf C18 eingefroren (Override-Schutz „nur wenn leer"). Die manuelle Auswahl wurde in 0.5.9 entfernt → es gibt keinen manuellen Wert mehr zu schützen. Entscheidung: **akkumulieren** — Feld/PDF zeigt alle genutzten Köpfe „C18, C10".

### A1 — `ui/screens/projects/CameraTypePrefill.kt`: neue reine Funktion + Tests
Neben `cameraTypePrefill` ergänzen:
```kotlin
/**
 * Akkumuliert genutzte Kameraköpfe: fügt das Label des erkannten Kopfes hinzu, wenn es noch nicht
 * in [currentValue] (komma-separiert) steht. Gibt den NEUEN Gesamtwert zurück, oder null, wenn
 * nichts zu ändern ist (UNKNOWN oder Kopf bereits gelistet).
 */
fun cameraTypeAccumulate(
    detectedHead: CameraHead,
    currentValue: String,
    c10Label: String,
    c18Label: String,
): String? {
    val label = when (detectedHead) {
        CameraHead.C10 -> c10Label
        CameraHead.C18 -> c18Label
        CameraHead.UNKNOWN -> return null
    }
    val tokens = currentValue.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    if (tokens.contains(label)) return null
    return if (tokens.isEmpty()) label else (tokens + label).joinToString(", ")
}
```
Unit-Tests: leer+C18→„C18"; „C18"+C10→„C18, C10"; „C18, C10"+C18→null; „C18, C10"+C10→null; UNKNOWN→null. `cameraTypePrefill` bleibt (falls andere Nutzer) oder entfernen, wenn nach A2 ungenutzt.

### A2 — Aufrufer auf Akkumulieren umstellen
- `ui/screens/projects/ProjectFormScreen.kt`, `LaunchedEffect(detectedHead, editProjectId)` (~Z.193-198): `cameraTypePrefill(detectedHead, viewModel.kameratyp, …)` → `cameraTypeAccumulate(detectedHead, viewModel.kameratyp, …)`.
- `ui/screens/inspection/InspectionScreen.kt`, der M2-Nachtrag-Effekt (`… collect { head -> val proj = projectRepository.getProject(pid) …; cameraTypePrefill(head, proj.kameratyp, …)?.let { updateProject(proj.copy(kameratyp = it)) } }`, ~Z.143-149): `cameraTypePrefill(head, proj.kameratyp, …)` → `cameraTypeAccumulate(head, proj.kameratyp, …)`.
- Die Initial-Zeile beim Quick-Bucket-Anlegen (`cameraTypePrefill(head, "", …) ?: ""`, ~Z.134) darf bleiben (currentValue="" → identisches Verhalten) oder ebenfalls auf `cameraTypeAccumulate` wechseln.

Ergebnis: PDF-Zeile „Kameratyp" zeigt alle genutzten Köpfe. Kein Override-Schutz mehr (gewollt).

---

## TEIL B — Projektliste zeigt den Auftraggeber

Befund: Der Listen-Titel nutzt nur `standortAdresse + projectNumber`, nicht `auftraggeber` — „Projekt/Auftraggeber" taucht in der Liste nicht auf. Zwei identische Stellen; die Suche in ProjectsScreen schließt `auftraggeber` bereits ein, nur die Anzeige nicht.

Fix an BEIDEN Stellen — `auftraggeber` vorne aufnehmen:
- `ui/screens/home/HomeScreen.kt`, `private fun projectTitle(project)`:
  `listOf(project.standortAdresse, project.projectNumber)` → `listOf(project.auftraggeber, project.standortAdresse, project.projectNumber)` (Rest unverändert: `.filter { it.isNotBlank() }.joinToString(" — ").ifEmpty { "---" }`).
- `ui/screens/projects/ProjectsScreen.kt`, `ProjectCard`, der Titel-`Text`: dieselbe Liste um `project.auftraggeber` vorne ergänzen.

Ergebnis: „testneu — hier — 130726_0606_006".

---

## TEIL C — HW-Recorder-Wahlschalter entfernen, unsichtbarer Auto-Rückfall

Entscheidung: Der Aufnahmeweg-Schalter kommt aus den Einstellungen (Kunden-Vereinfachung). Immer HW-Encoder; nur falls dessen Start scheitert, springt der alte `LocalBitmapRecorder` **automatisch und unsichtbar** ein.

### C1 — NEU `network/FallbackRecorder.kt` (implementiert `Recorder`)
```kotlin
package com.uip.oneapp.network

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
import android.util.Log
import com.uip.oneapp.export.OsdSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Immer HW-Encoder ([HardwareBitmapRecorder]); scheitert dessen start(), unsichtbarer
 * automatischer Rückfall auf [LocalBitmapRecorder]. Für die UI EIN Recorder (delegiert an den
 * aktiven), mit gespiegeltem [state]-Flow.
 */
class FallbackRecorder(
    private val context: Context,
    arbiter: CameraEncoderArbiter,
) : Recorder {
    private val primary: Recorder = HardwareBitmapRecorder(context, arbiter)
    private var active: Recorder = primary
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val _state = MutableStateFlow(RecordingState.IDLE)
    override val state: StateFlow<RecordingState> = _state.asStateFlow()
    private var mirrorJob: Job? = null

    private fun mirror(r: Recorder) {
        mirrorJob?.cancel()
        mirrorJob = scope.launch { r.state.collect { _state.value = it } }
    }
    init { mirror(primary) }

    override val isRecording: Boolean get() = active.isRecording
    override val isPaused: Boolean get() = active.isPaused

    override fun start(
        outputPath: String, frameFlow: StateFlow<Bitmap?>, fps: Int, sdResolution: Boolean,
        osdSettings: OsdSettings?, typeface: Typeface?, osdLine1Provider: () -> String,
        osdLine2Provider: () -> String, findingProvider: () -> String?, meterProvider: (() -> Float)?,
    ): Boolean {
        if (primary.start(outputPath, frameFlow, fps, sdResolution, osdSettings, typeface,
                osdLine1Provider, osdLine2Provider, findingProvider, meterProvider)) {
            active = primary; mirror(primary); return true
        }
        Log.w(TAG, "HW-Encoder-Start fehlgeschlagen — unsichtbarer Rückfall auf LocalBitmapRecorder")
        val fb = LocalBitmapRecorder(context)
        active = fb; mirror(fb)
        return fb.start(outputPath, frameFlow, fps, sdResolution, osdSettings, typeface,
            osdLine1Provider, osdLine2Provider, findingProvider, meterProvider)
    }

    override fun stop(onDone: (String?) -> Unit) = active.stop(onDone)
    override fun pause() = active.pause()
    override fun resume() = active.resume()
    override fun cancel() = active.cancel()

    companion object { private const val TAG = "FallbackRecorder" }
}
```

### C2 — `network/RecorderFactory.kt`: immer FallbackRecorder
```kotlin
object RecorderFactory {
    fun create(context: Context, arbiter: CameraEncoderArbiter): Recorder =
        FallbackRecorder(context, arbiter)
}
```
Doku-Kommentar entsprechend anpassen (kein FeatureFlags-Branch mehr).

### C3 — Schalter + Flag + Persistenz entfernen
- `network/FeatureFlags.kt`: `useHardwareRecorder` entfernen. Wird `FeatureFlags` dadurch leer, die Datei löschen und alle Importe/Referenzen mit auflösen.
- `ui/screens/settings/SettingsViewModel.kt`: aus `SettingsUiState` das Feld `useHardwareRecorder`; die Methode `updateUseHardwareRecorder`; `KEY_USE_HARDWARE_RECORDER`; die init-Lesung; und die Zeile `FeatureFlags.useHardwareRecorder = …` entfernen. `saveAll` entsprechend bereinigen (falls es das Feld schreibt).
- `ui/screens/settings/SettingsScreen.kt`: die `DqSettingRow` mit `S("settings_hw_recorder_title")`/`S("settings_hw_recorder_desc")` samt zugehörigem `DqRowDivider` entfernen.
- `OneApp.kt` `onCreate`: den eager `FeatureFlags.useHardwareRecorder = runBlocking { … KEY_USE_HARDWARE_RECORDER … }`-Block entfernen.
- `ui/screens/inspection/InspectionScreen.kt` (~Z.284-286): der `RecorderFactory.create(context, encoderArbiter)`-Aufruf bleibt unverändert (liefert jetzt den FallbackRecorder). Nur den Kommentar über FeatureFlags anpassen.
- L10n-Keys `settings_hw_recorder_title`/`settings_hw_recorder_desc` dürfen bleiben (ungenutzt schadet nicht) oder entfernt werden.

NICHT löschen: `LocalBitmapRecorder` und `HardwareBitmapRecorder` (beide weiter gebraucht). Recovery/Journal-Logik unverändert.

---

## Verifikation
- Build grün, Unit-Tests grün (Teil A neue Tests inklusive).
- Am Gerät: (A) Projekt anlegen, mit C18 aufnehmen, Kopf auf C10 wechseln → PDF-Kameratyp „C18, C10". (B) Projektliste zeigt Auftraggeber vorne. (C) kein HW-Recorder-Schalter mehr in den Einstellungen; normale Aufnahme läuft (HW); Recovery/Pause unverändert.

## Nicht anfassen
Meterwert-/Recovery-Logik, OSD, Hardbutton, Speicheranzeige, andere Settings-Zeilen.
