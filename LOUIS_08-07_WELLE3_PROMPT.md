# Auftrag: Louis-Feldfeedback 08.07. — Welle 3 (Inspektor-Blocker vor Merge)

**Branch:** `feature/dual-mode` (weiterarbeiten, KEIN Merge, kein Tag)
**Build:** OHNE Keystore — `cd C:\Projekte\drainq.one; .\gradlew.bat assembleDebug test`. NIE `assembleRelease`/`KEYSTORE_`-Env.
**Commit-Präfix:** `fix(louis-w3): …` — nach jedem Schritt Build + Tests grün, dann committen.
**Git-Regel:** Zustand NIE über Sandbox-bash beurteilen (CRLF-Phantom-Diffs) — Host-Read/Grep ist Schiedsrichter. Kein `python`/`bash`-Write auf große Host-Dateien.

Diese Welle ist der **Blocker vor dem Merge** `feature/dual-mode` → master. Drei Aufgaben: A + B sind klar lösbar, C ist geräteiterativ.

---

## Aufgabe A — Kamera beim neuen Projekt automatisch übernehmen  [klar, zuerst]

**Symptom (Louis):** „when you start a new project the system should add the connected camera. Now I have manually selected the C10."

**Ist-Stand (verifiziert):**
- Die Erkennung existiert schon: `CameraHead.from(cable.cameraId)` → `C10 / C18 / UNKNOWN` (InspectionScreen.kt ~Z. 900).
- Das Projektformular nutzt aber ein **manuelles Dropdown** ohne Vorbelegung: `ProjectFormScreen.kt` Z. 712–737 (`viewModel.kameratyp`), Werte `S("camera_c10")`, `S("camera_c18")` (Z. 168).
- `HardwareService` ist ein Koin-`single` (AppModule Z. 83) und liefert `hardwareState.cableController` (wie in InspectionScreen Z. 140–141).

**Fix:**
- Beim **Neuanlegen** (`!viewModel.isEditing`) und wenn `viewModel.kameratyp` leer ist: erkannten Kopf vorbelegen — `HardwareService` per `koinInject()` lesen, `CameraHead.from(hardwareState.value.cableController.cameraId)` → `C10 → S("camera_c10")`, `C18 → S("camera_c18")`, `UNKNOWN → nichts vorbelegen` (nie raten).
- Nur **Default-Vorbelegung**, kein Zwang: der Nutzer muss das Dropdown weiter überschreiben können (Report-Override bleibt manuell). Im Editier-Modus **nicht** überschreiben.
- Wird der Kopf erst nach Öffnen des Formulars erkannt, darf die Vorbelegung nachziehen — aber **nur** solange das Feld noch leer/unangetippt ist.

---

## Aufgabe B — Schaden & Foto bekommen den echten Meterwert, nicht 0.00 m  [Daten-/Report-Fehler]

**Symptom (Louis):** „If you use damage, it needs to take the actual metercounter value (displayed in the video) and not 0.00 m. This is also for Photo. For Note you can add the metercount value manually."

**Ist-Stand (verifiziert):**
- `meterValue` (InspectionScreen.kt Z. 144, Start 0f) wird aus `cable.meterReading` gespeist (Z. 378–379).
- Video-OSD nutzt `buildOsdLine2(meterValue, …)` (Z. 276, Aufnahme Z. 307).
- Schaden-Dialog `currentMeter = meterValue` (Z. 1577), Notiz-Dialog ebenso (Z. 1645), Schnell-Foto speichert `position = meterValue` (Z. 438).
- Im Dialog ist das Meterfeld editierbar mit Default `existingDamage?.position ?: currentMeter` (DamageDialog.kt Z. 76).

Damit müssten Overlay und Dialog **denselben** Wert zeigen. Louis sieht im Video aber einen laufenden Zähler und im Dialog 0.00 m — d. h. zum Erfassungszeitpunkt ist `meterValue` 0, obwohl das Overlay einen Wert zeigt.

**Zuerst Repro klären (eine offene Frage an Louis, dann fixen):** Setzt er Schaden/Foto **live während der Aufnahme** oder **beim Abspielen** des Films?
- **Wiedergabe/Backplay** (starke Vermutung): Der Live-`cable.meterReading` ist beim Abspielen 0/veraltet; das Overlay im Film ist der **eingebrannte** Aufnahme-Meter. → Erfassung im Wiedergabemodus muss den **im Bild gezeigten** Meter (Aufnahme-/Frame-Station) nehmen, nicht den Live-Wert.
- **Live:** Prüfen, warum `meterValue` beim Öffnen des Dialogs 0 ist, obwohl das Overlay > 0 zeigt (Timing/State-Quelle) und angleichen.

**Fix-Ziel (für BEIDE Fälle gültig):** Die gespeicherte Station (`position`) eines Schadens **und** eines Fotos ist **exakt der Meterwert, der im selben Moment im Video-Overlay steht**. Kein 0.00-Default, wenn das Overlay einen Wert zeigt. Notiz-Verhalten (manuell editierbar) bleibt.

---

## Aufgabe C — #8: Durchmesser/Länge im HD-Modus nicht eingebbar  [geräteiterativ]

**Symptom (Louis, reproduziert):** Im HD-Modus lassen sich Durchmesser/Länge/Start/Ende nicht befüllen — „the box tries to open, but as soon as you want to add a value it closes". Erst **HD→SD umschalten** (oder Kamerakopf-Dropdown wechseln) macht die Felder wieder beschreibbar; danach zurück auf HD zum Inspizieren.

**Ist-Stand (verifiziert):** Die Felder sind **nicht** per `enabled` an die Videoqualität gekoppelt (ProjectFormScreen.kt: Felder Z. 618–664; VideoQuality-Umschalter Z. 807–818 setzt nur ein State-Flag). Es gibt **kein** Live-Video im Formular. → HD/SD ist ein **Workaround**, kein Gate: das Umschalten erzwingt eine Neuzeichnung, die einen **festhängenden Compose-Fokus/IME-Zustand** löst. Verdächtig: Zusammenspiel der `ExposedDropdownMenuBox`-Dropdowns (Leitungstyp/Material/Kameratyp) mit `ImeAction.Next`/`moveFocus` und `windowSoftInputMode="adjustResize"` im scrollenden Formular.

**Vorgehen (NICHT blind fixen):**
1. **Am Gerät reproduzieren** (ONE, DIRECT). Genau protokollieren: Nach welcher Interaktion hängt die Tastatur? (direkt nach einem Dropdown? nach `ImeAction.Next`? nur bei laufendem HD-Stream im Hintergrund?)
2. Erst mit belegter Ursache fixen — Kandidaten: sauberes Fokus-/IME-Handling zwischen Dropdown-Dismiss und Textfeld-Fokus; ggf. `FocusRequester`/`bringIntoView`; Dropdown-Expand-State robust zurücksetzen; prüfen ob ein Hintergrund-Decoder/Recompose den Fokus stiehlt.
3. Akzeptanzkriterium: **Alle Felder (Durchmesser, Länge, Start, Ende) sind im HD-Modus direkt beschreibbar** — ohne HD→SD-Umweg. Stabil über mehrere Neuanlagen.

---

## Leitplanken

- Bedienkonzept/Norm-Logik unverändert; nur die beschriebenen Defekte.
- A + B testbar halten: Kopf-Mapping und die Station-Quelle als reine, unit-testbare Ableitung kapseln; bestehende Tests grün.
- Kein neuer Netz-/Export-Endpunkt, keine neue Permission, kein neues Logging von Roh-Telemetrie.
- **KRITIS:** rein lokal (DB-Feld `position`, Formular-Vorbelegung). Aufgabe B verbessert die **Report-Richtigkeit** (korrekte Station im Bericht) — als Datenqualitäts-Fix im RESULT vermerken. Keine SBOM-/Dependency-Änderung.
- **Modell/Effort:** A + B → Sonnet/mittel. C → Opus/hoch (Fokus/IME-Kern). Pre-Merge-Review durch ein **nicht-bauendes** Modell/hoch.

## Abschlussbericht `RESULT_LOUIS_W3.md` (Repo-Root) mit Geräte-Checkliste

- [ ] **A Kamera:** Kamera anschließen → neues Projekt → Kameratyp ist korrekt vorbelegt (C10/C18), manuell überschreibbar; im Editier-Modus nicht überschrieben.
- [ ] **B Meter Schaden:** live + bei Wiedergabe je einen Schaden setzen → gespeicherte Station == im Video gezeigter Meter (nicht 0.00), steht so im Report.
- [ ] **B Meter Foto:** dito für Foto.
- [ ] **C HD-Eingabe:** neues Projekt im **HD**-Modus → Durchmesser, Länge, Start, Ende direkt eingebbar, ohne HD→SD-Umweg; über 3 Anlagen stabil.
- [ ] Build + Tests grün; adversariale Selbst-Review (Fokus-Race, Null-/Leerfälle Kopf-Mapping, Station bei fehlender Telemetrie).

## Nicht Teil dieser Welle

- USB-Export (kryptische Dateinamen / Foto·Video nicht öffenbar) → Welle 4, Louis testet im Büro.
- Speicher-/USB-Kapazitätsanzeige (Feature-Wunsch) → Welle 4.
- Datum: „automatisch" funktioniert; manueller Uhr-Fall = Doku/Onboarding, kein Code hier.
