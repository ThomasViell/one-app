# RESULT — Louis-Feldfeedback 08.07., Welle 3 (Inspektor-Blocker vor Merge)

**Branch:** `feature/dual-mode` (kein Merge, kein Tag)
**Build:** `.\gradlew.bat assembleDebug test` — grün (ohne Keystore).
**Commits dieser Welle:**
- `52e9e2a` — `fix(louis-w3): A — Kamerakopf beim neuen Projekt automatisch vorbelegen`
- `fd12eca` — `fix(louis-w3): C — HD-Formularfelder direkt beschreibbar (Fokus/IME-Race)`
- `cfc5f45` — `fix(louis-w3): A-Review — Kopf aus Flow ableiten statt ganzen hardwareState collecten`

Aufgabe **B** (Meterwert bei Schaden/Foto) ist **bewusst offen** — sie wartet auf Louis'
Repro-Rückmeldung (live während Aufnahme vs. bei Wiedergabe). Siehe Abschnitt B.

---

## Aufgabe A — Kamera beim neuen Projekt automatisch übernehmen ✅ (Code fertig, Geräte-Abnahme offen)

**Louis:** „when you start a new project the system should add the connected camera. Now I have manually selected the C10."

**Umsetzung:**
- Neue **reine, unit-testbare** Ableitung `cameraTypePrefill(detectedHead, currentValue, c10Label, c18Label)`
  in `app/.../ui/screens/projects/CameraTypePrefill.kt`:
  - `currentValue` bereits belegt/angetippt → `null` (nie überschreiben).
  - `CameraHead.UNKNOWN` → `null` (nie raten).
  - `C10`/`C18` → das bereits **lokalisierte** Label (`S("camera_c10")` / `S("camera_c18")`).
- Verdrahtung in `ProjectFormScreen.kt`: `HardwareService` per `koinInject()`, `hardwareState`
  collected, `detectedHead = CameraHead.from(hwState.cableController.cameraId)`.
  `LaunchedEffect(detectedHead, editProjectId)` belegt `kameratyp` **nur beim neuen Projekt**
  (`editProjectId == null` — race-frei; `isEditing` kippt erst nach dem asynchronen `loadProject`)
  und **nur solange das Feld leer** ist. Zieht nach, wenn der Kopf erst nach dem Öffnen erkannt wird.
- Manueller Override im Dropdown bleibt jederzeit möglich; im Editier-Modus wird nie vorbelegt.

**Tests:** `CameraTypePrefillTest` (7 Fälle): C10/C18-Mapping, UNKNOWN raten-Verbot,
belegtes Feld nie überschreiben, Whitespace = leer, Label-Durchreichung.

---

## Aufgabe C — #8: Durchmesser/Länge im HD-Modus nicht eingebbar ✅ (Code-Fix, Geräte-Iteration offen)

**Louis:** „the box tries to open, but as soon as you want to add a value it closes." — erst
**HD→SD** (oder Kamerakopf-Dropdown wechseln) macht die Felder wieder beschreibbar.

**Belegte Ursache (aus dem Code, ohne Gerät):** Die Felder sind **nicht** per `enabled` an die
Videoqualität gekoppelt — HD/SD ist ein Workaround, kein Gate. Der `dismissKeyboardOnScroll`-
`NestedScrollConnection` rief in `onPreScroll` bei **jedem** Scroll (`available.y != 0`)
`focusManager.clearFocus()` + `keyboardController.hide()` auf. Fokussiert man ein **weiter unten**
liegendes Feld, löst Compose beim per `windowSoftInputMode="adjustResize"` verkleinerten Fenster
ein **programmatisches `bringIntoView`-Auto-Scroll** aus (in Compose 1.7 als
`NestedScrollSource.SideEffect` gemeldet). Das alte `onPreScroll` behandelte dieses Auto-Scroll wie
ein Nutzer-Wischen → riss den gerade gesetzten Fokus sofort wieder ab → Tastatur ging zu. Der
HD→SD-Umschalter war nur ein erzwungenes Recompose, das den festhängenden Zustand zufällig löste.
Deshalb traf es die **unteren** Felder (Durchmesser/Länge/Start/Ende), nicht die obersten.

**Fix:** `onPreScroll` schließt die Tastatur nur noch bei **echten Nutzergesten**
(`source == NestedScrollSource.UserInput` = Touch-Drag). Programmatische Scrolls
(`SideEffect`: `bringIntoView`, Fling, IME-Resize) behalten den Fokus. Die „Wischen schließt
Tastatur"-Geste (Feedback #4) bleibt vollständig erhalten.

**Ehrlicher Status:** Ohne Zugriff auf die ONE-Hardware **code-belegt, aber nicht am Gerät
verifiziert**. Der Fix ist gerichtet und risikoarm (er verengt nur, *wann* Fokus geleert wird — er
kann die Wisch-Geste nicht brechen und im schlimmsten Fall ist er ein No-op). Geräte-Abnahme steht
in der Checkliste. Falls am Gerät weiterhin defekt: nächster Kandidat ist ein `FocusRequester`/
`bringIntoViewRequester`-Handling bzw. Prüfen, ob ein `ExposedDropdownMenuBox`-Dismiss den Fokus
stiehlt (siehe Prompt-Vorgehen Schritt 2).

---

## Aufgabe B — Meterwert bei Schaden/Foto (0.00 m statt Video-Wert) ⏸ OFFEN, wartet auf Louis

**Louis:** „If you use damage, it needs to take the actual metercounter value (displayed in the video)
and not 0.00 m. This is also for Photo. For Note you can add the metercount value manually."

**Warum offen:** Der Fix unterscheidet sich je nach Repro. Ist-Stand (verifiziert): Schaden-Dialog,
Notiz-Dialog und Schnell-Foto nutzen alle **denselben** `meterValue`, der auch das Video-Overlay
speist. Wenn der Dialog 0.00 m zeigt, während das Overlay > 0 zeigt, ist die entscheidende Frage:
**Setzt Louis Schaden/Foto live während der Aufnahme oder beim Abspielen des Films?**
- **Wiedergabe/Backplay** (starke Vermutung): Live-Meter ist beim Abspielen 0/veraltet; das Overlay
  im Film ist der eingebrannte Aufnahme-Meter → Erfassung muss den im Bild gezeigten Meter nehmen.
- **Live:** klären, warum `meterValue` beim Dialog-Öffnen 0 ist, obwohl das Overlay > 0 zeigt.

**Nächster Schritt:** Louis' Antwort abwarten, dann als reine, unit-testbare Stations-Ableitung
kapseln (analog A). **Datenqualitäts-Fix** (korrekte Station im Bericht), rein lokal (DB-Feld
`position`) — keine SBOM-/Dependency-/Endpunkt-Änderung.

---

## Geräte-Checkliste (auf der ONE, DIRECT)

- [ ] **A Kamera:** Kamera anschließen → neues Projekt → Kameratyp ist korrekt vorbelegt (C10/C18),
      manuell überschreibbar; im Editier-Modus **nicht** überschrieben.
- [ ] **A nachziehen:** Formular **ohne** Kamera öffnen (Feld leer) → Kamera anstecken → Kameratyp
      zieht auf C10/C18 nach, solange das Feld unangetippt ist.
- [ ] **C HD-Eingabe:** neues Projekt im **HD**-Modus → Durchmesser, Länge, Start, Ende direkt
      eingebbar, **ohne** HD→SD-Umweg; über **3** Anlagen stabil.
- [ ] **C Wisch-Geste bleibt:** Feld fokussiert, dann Formular wischen/scrollen → Tastatur schließt
      wie bisher (Feedback #4 nicht gebrochen).
- [ ] **B (nach Louis-Antwort):** live + bei Wiedergabe je einen Schaden setzen → gespeicherte
      Station == im Video gezeigter Meter (nicht 0.00), steht so im Report. Dito für Foto.
- [x] Build + Tests grün.
- [x] Adversariale Selbst-Review (siehe unten).

---

## Adversariale Selbst-Review

Pre-Merge-Review als Multi-Agent-Lauf (4 diverse-Lens-Reviewer über den Commit-Diff + adversariale
Verifikation jedes materiellen Findings, nicht-bauende Modelle). Ergebnis:

- **Task C Fokus-Race — validiert.** Der Verifier hat den „nicht am Gerät verifiziert"-Zweifel als
  Code-Defekt **widerlegt**: Die Modifier-Kette `.nestedScroll(dismissKeyboardOnScroll).verticalScroll()`
  auf derselben Column leitet die `bringIntoView`-Deltas tatsächlich durch `onPreScroll`; das
  `UserInput`-Gate kompiliert (1.7.3) und ist eine strikte, regressionsfreie Verbesserung. Der
  Mechanismus **erklärt** sowohl die HD-Spezifik (Race wird unter HD-Hauptthread-Last verloren) als
  auch den dauerhaften HD→SD-Workaround (SD senkt die Last → Race bleibt eine Session lang gewonnen).
  Einziger Restpunkt = Geräte-Abnahme, bereits in der Checkliste.
- **Task A Recompose-Regression — bestätigt (minor), gefixt.** Konsens über 4 Lenses: Das
  ursprüngliche `collectAsState()` auf den kompletten `hardwareState` ließ den Formular-Rumpf bei
  verbundener Kamera ~30×/s neu komponieren (`lastUpdateMs` ändert sich jeden Frame). **Kein**
  Fokus-/Tipp-Problem (Compose erhält TextField-Fokus über Recompose), aber vermeidbare CPU/Akku-Last
  auf genau dem Screen, den Aufgabe C flüssig machen soll. **Fix:**
  `hardwareState.map { CameraHead.from(it.cableController.cameraId) }.distinctUntilChanged()` vor dem
  Collect → Recompose nur noch bei echtem C10/C18/UNKNOWN-Wechsel. (Commit `cfc5f45`.)
- **Null-/Leerfälle Kopf-Mapping — sauber.** `cameraTypePrefill` durch 7 Unit-Tests abgedeckt
  (UNKNOWN/leeres Feld/Whitespace/belegtes Feld). KDoc-Wortlaut an `isNotBlank()` angeglichen (Nit).
- **Bewusst NICHT gefixt (dokumentiert):** Die Vorbelegung speichert das **lokalisierte Label**
  (`S("camera_c10")`), nicht einen stabilen Code. Ein Sprachwechsel *nach* der Vorbelegung ließe einen
  altsprachlichen Wert stehen (Feld ist dann nicht mehr leer). Das ist ein latenter i18n-Smell des
  bestehenden Dropdown-/Report-Datenmodells (ProjectEntity speichert überall das Label) — eine
  Umstellung auf Codes gehört in eine eigene L10n-Welle, nicht in diesen Blocker-Fix. Kein neuer
  Defekt gegenüber dem Ist-Zustand.
- **Kein** Compile-/Regressionsrisiko gefunden; `NestedScrollSource.UserInput` ist der aktuelle,
  nicht-deprecatede Konstant in Compose 1.7.3.

## Leitplanken eingehalten

- Bedienkonzept/Norm-Logik unverändert; nur die beschriebenen Defekte.
- A als reine, unit-testbare Ableitung gekapselt; bestehende Tests grün.
- Kein neuer Netz-/Export-Endpunkt, keine neue Permission, kein neues Roh-Telemetrie-Logging.
- KRITIS: rein lokal (Formular-Vorbelegung, Fokus-Handling). Keine SBOM-/Dependency-Änderung.
