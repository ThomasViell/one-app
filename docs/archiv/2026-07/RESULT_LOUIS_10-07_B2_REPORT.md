# RESULT — Louis-Feedback 0.5.6 (B2 + M2/M3/M4 + B1-Interim)

Branch: `feature/dual-mode` · Ziel-Version: **0.5.6 / 506** (via `APP_VERSION_NAME`/`APP_VERSION_CODE`).
Build: `.\gradlew.bat assembleDebug` **grün** (APK `app-debug.apk`, versionCode 506 / versionName 0.5.6, debug-signiert, kein Keystore).
Unit-Tests: `testDebugUnitTest` **grün** (379 gesamt, 0 Fehler; darunter 10 neue Guard-Tests + 1 neuer Bucket-Test).
`OneInternalHardwareService` unangetastet. Kein Merge, kein Tag.

---

## AP1 — B2: Feldeingabe hängt (Durchmesser/Länge/Inspektor) — GELÖST (Code), Geräteabnahme offen

**Datei:** `app/src/main/java/com/uip/oneapp/ui/screens/projects/ProjectFormScreen.kt`

- Das `dismissKeyboardOnScroll`-`NestedScrollConnection`-Objekt **komplett entfernt** (war ~Z. 90–109) und die
  `.nestedScroll(dismissKeyboardOnScroll)`-Verdrahtung aus der Column-Modifier-Kette entfernt
  (jetzt nur noch `.pointerInput{…}.verticalScroll(…)`, `ProjectFormScreen.kt:245–250`).
- Nicht mehr benötigte Imports (`Offset`, `NestedScrollConnection`, `NestedScrollSource`, `nestedScroll`) entfernt.
- Tastatur-Schließen läuft jetzt **ausschließlich** über (a) `KeyboardHideButton` in der TopBar (unverändert)
  und (b) `detectTapGestures(onTap = { focusManager.clearFocus() })` auf Freifläche (unverändert). `verticalScroll` bleibt.
- Kein automatisches `clearFocus()`/`keyboardController?.hide()` mehr während Scroll/Fokuswechsel.

**Warum das der Fix ist:** Der W3-Versuch (`cfc5f45`) trennte „echte Wischgeste" von „programmatischem
Scroll" über `NestedScrollSource.UserInput`. Auf ONE/RK3588 + Compose 1.7 meldet das
`moveFocus(FocusDirection.Down)`-Auto-Scroll (ImeAction.Next) ebenfalls `UserInput` → `clearFocus()` riss den
gerade gesetzten Fokus ab. Ohne diese Verdrahtung gibt es keinen Pfad mehr, der den Fokus beim Durchtippen abreißt.

**Unit-Test:** bewusst keiner — reines Compose-/IME-Fokusverhalten auf echter Hardware ist mit JVM-Unit-Tests nicht
sinnvoll abbildbar (die Ursache war genau eine gerätespezifische Fehlklassifizierung der Scroll-Quelle).

**NUR am Gerät prüfbar (Pflicht):** 3 NEUE HD-Projekte anlegen, je Durchmesser → Länge → Startpunkt → Endpunkt
per „Weiter" durchtippen, OHNE SD-Umweg, OHNE Kopfwechsel. Kein Feld darf den Fokus verlieren.

---

## AP2 — M3: „Schnellaufnahme" in EN nicht übersetzt (Dateiname + Report) — GELÖST

**Dateien:** `LocalizationManager.kt`, `ProjectRepository.kt`, `InspectionScreen.kt`

- Neuer L10n-Key `quick_capture_bucket` (DE „Schnellaufnahme", EN „Quick capture") in `deTranslations()` und
  `enTranslations()` (`LocalizationManager.kt`).
- `ProjectRepository.getOrCreateQuickProjectId(label, kameratyp)` nimmt jetzt das **lokalisierte Label** als
  Bucket-Präfix (`"<label>_ddMMyy"`) statt hartkodiert „Schnellaufnahme_" (`ProjectRepository.kt:45–72`).
  Default-Parameter `QUICK_BUCKET_DEFAULT_LABEL = "Schnellaufnahme"` (companion) für Aufrufer ohne UI-Kontext.
- `InspectionScreen.kt:125–135`: löst `S("quick_capture_bucket")` im Composable-Scope auf und übergibt es beim
  Bucket-Anlegen. Damit trägt der Bucket in **Bericht** (Projekt-Nr.-Zeile in PDF + `projekt_info.txt`) und
  **Dateiname** (`Bericht_<label>_ddMMyy.pdf`, `Projekt_<label>_ddMMyy.zip`) die App-Sprache.

**Design-Entscheidung (Anzeige vs. Speicher):** Der Auftrag skizzierte „Speicher-Pfad neutral halten + Anzeige-
Label trennen". Das hätte einen Display-Transform an **5+** Anzeigestellen (`HomeScreen`, `ProjectsScreen`,
`ProjectDetailScreen`, `InspectionScreen`, PDF ×2) erfordert — und, wenn eine Stelle vergessen wird, DE von
„Schnellaufnahme" auf ein neutrales „QuickCapture" **regressieren** lassen. Stattdessen: Label beim **Anlegen**
lokalisieren. Folge:
- **DE bleibt bit-identisch** zu vorher („Schnellaufnahme_ddMMyy") → volle Abwärtskompatibilität, Bestands-Buckets
  werden weiter per `getByProjectNumber` gefunden, `status == "QUICK"` bleibt der robuste Erkennungsschlüssel.
- **EN** erhält „Quick capture_ddMMyy" in Report **und** Dateiname → genau Louis' Befund behoben.
- Trade-off (bewusst, dokumentiert): Dateiname ist damit sprach-**abhängig** statt strikt neutral, und ein
  Sprachwechsel am selben Tag legt einen zweiten Tages-Bucket an (vernachlässigbar; Nutzer wechseln die Sprache
  nicht mitten in der Erfassung). Das war der risikoärmere Weg (keine 5 Anzeigestellen, keine DE-Regression).

**Unit-Test:** `CapturePersistenceTest.quickCaptureBucket_storesLabelPrefixAndCameraType` — Bucket-Nummer trägt
das übergebene Label als Präfix, Idempotenz gilt pro Label. Bestehender Idempotenz-Test (Default-Label) unverändert grün.

**NUR am Gerät prüfbar:** App auf EN stellen, Schnellaufnahme auslösen, exportieren → Dateiname + Report zeigen
„Quick capture". Auf DE unverändert „Schnellaufnahme".

---

## AP3 — M2: Kameratyp fehlt im PDF bei Quick-Capture-Projekten — GELÖST

**Dateien:** `ProjectRepository.kt`, `InspectionScreen.kt`

- `getOrCreateQuickProjectId(label, kameratyp)` setzt `kameratyp` auf dem neuen `ProjectEntity`
  (`ProjectRepository.kt:60–71`). Nur beim **Erst**anlegen — ein bestehender Tages-Bucket bleibt unverändert (Idempotenz).
- `InspectionScreen.kt:131–133`: liest den Kopf frisch beim Anlegen
  (`CameraHead.from(hardwareService.hardwareState.value.cableController.cameraId)`) und bildet ihn über die bereits
  unit-getestete **reine** `cameraTypePrefill(head, "", c10, c18)`-Funktion auf das C10/C18-Label ab (gleiche
  Quelle/Regel wie `ProjectFormScreen`). **UNKNOWN → leer** (`?: ""`, nie raten).
- `ProjectExportService` rendert `addPipeRow(t("field_camera_type"), project.kameratyp)` bereits — die Zeile ist
  jetzt bei Quick-Capture-Projekten gefüllt.

**Bekannte Grenze (dokumentiert, kein Blocker):** Wird die Inspektion geöffnet, **bevor** der Kopf erkannt ist
(Kamera noch nicht verbunden), entsteht der Bucket mit leerem `kameratyp` und wird an diesem Tag nicht nachgefüllt.
Realer Normalfall (Kamera streamt bereits beim Betreten der Inspektion) ist abgedeckt. Kein „Raten", nur ehrliches Leerbleiben.

**Unit-Test:** derselbe `CapturePersistenceTest`-Test prüft `kameratyp == "C10"` nach Anlegen mit Kopf.
`cameraTypePrefill` bleibt durch 7 Bestands-Tests abgedeckt.

**NUR am Gerät prüfbar:** Mit angeschlossener C10 bzw. C18 eine Schnellaufnahme starten, PDF erzeugen →
Rohrdaten-Tabelle zeigt den Kameratyp.

---

## AP4 — M4: Pfeil „→" fehlt im PDF-Report — GELÖST

**Datei:** `app/src/main/java/com/uip/oneapp/export/ProjectExportService.kt`

- Unicode-TTF **Inter** (`assets/fonts/inter_regular.ttf`, bereits im Projekt, 402 KB) wird als **Dokument-Default-
  Schrift** eingebettet (`ProjectExportService.kt:66–82`):
  `PdfFontFactory.createFont(bytes, PdfEncodings.IDENTITY_H, EmbeddingStrategy.PREFER_EMBEDDED)` →
  `document.setFont(unicodeFont)`. Alle Paragraphen/Zellen (inkl. der `setFixedPosition`-Absätze im Rohrprofil)
  erben diese Schrift; das Canvas zeichnet nur Linien/Kreise, keinen Text.
- Ursache war der iText-Default Helvetica/WinAnsi ohne U+2192 → Pfeil wurde still verworfen. Kein ASCII-„->"-Ersatz.
- Robustheit: Font-Laden ist in `try/catch` gekapselt; schlägt es fehl (Asset fehlt), degradiert der Bericht auf den
  Default statt zu kippen (protokolliert).
- Verifikation der Glyph-Abdeckung vorab am cmap der TTF: U+2192 (→) sowie ä/ö/ü/ß vorhanden → mit Identity-H kein
  `.notdef`-Kästchen, sondern echte Glyphen.

**Unit-Test:** keiner — iText-Layout/Font-Rendering ist im JVM-Unit-Test nicht sinnvoll darstellbar (echtes PDF nötig).

**NUR am Gerät/PDF prüfbar (Pflicht):** Projekt mit Start/Ende rendern, PDF öffnen: Route zeigt „Start → Ende",
Rohrprofil-Kopf ebenso, Umlaute korrekt.

---

## AP5 — B1-Interim: kein stilles Falschdatum (Geräteuhr auf 2021) — GELÖST

**Dateien:** `InspectionDateGuard.kt` (neu), `ProjectFormViewModel.kt`, `ProjectFormScreen.kt`, `app/build.gradle.kts`

- Neue reine, unit-testbare `object InspectionDateGuard` (`InspectionDateGuard.kt`):
  `isSystemClockPlausible(today, buildYear)`, `isSaveableDate(dateStr, buildYear)`, `minPlausibleYear(buildYear)`.
- `ProjectFormViewModel` (`ProjectFormViewModel.kt`):
  - `inspektionsdatum` wird bei implausibler Uhr **NICHT** vorbelegt (leer statt Falschdatum); `showClockWarning`-Flag.
  - `saveProject()` **blockiert** ein klar zurückliegendes Datum (`saveDateError`-Flag), statt einen Falsch-Bericht zu erzeugen.
  - Plausibel = unverändertes Verhalten.
- `ProjectFormScreen` (`ProjectFormScreen.kt:253–298`): sichtbarer Warn-Banner (`errorContainer`) mit Knopf, der wie
  in `SettingsScreen` den `Settings.ACTION_DATE_SETTINGS`-Intent auslöst (defensiv gegen `ActivityNotFoundException`);
  zusätzlich Snackbar bei geblocktem Speicherversuch (gleiche Meldung). Neue L10n-Keys `clock_wrong_warning`,
  `open_datetime_settings` (DE + EN).

### ⚠ Wichtige, bewusste Abweichung vom Auftrag (Schwelle)
Der Auftrag nannte als Kriterium **`Jahr < 2015`**. Der real beobachtete RTC-Reset steht aber auf **~2021** — und
**2021 ≥ 2015**. Eine `< 2015`-Schwelle hätte den 2021-Fall **durchgelassen** und damit genau den 2021-Bericht
erzeugt, den diese Aufgabe verhindern soll (Ziel laut Überschrift: „kein stilles Falschdatum (Geräteuhr auf 2021)").
Der Guard ist deshalb ans **App-Build-Jahr** gekoppelt: implausibel = `Jahr < BuildConfig.BUILD_YEAR − 1`
(neues `buildConfigField("int", "BUILD_YEAR", …)`, `app/build.gradle.kts:42`). Begründung:
- Eine Inspektion kann nicht vor dem App-Build liegen → robuster, selbst-wartender Anker (kein zu pflegender Magic-Year).
- 1 Jahr Puffer (`buildYear − 1`) verhindert Fehlalarme bei jahresnahen, legitimen Daten.
- Fängt 2021 (und jedes weiter zurückliegende Reset-Datum) zuverlässig; blockiert echte 2026+-Daten nie.
Für 0.5.6 (Build 2026) ⇒ `minPlausibleYear = 2025`.

**Unit-Test:** `InspectionDateGuardTest` (10 Tests, buildYear=2026 fest injiziert): 2021 & 2024 blockiert,
2025/2026/2027 erlaubt, leeres/unparsbares Feld **nicht** blockiert (kein „stilles Falschdatum"), Grenzjahr `2025` erlaubt.

**Bewusst NICHT hier:** echtes `setAutoTimeEnabled(true)` als Device-Owner → Golden-Image-Rollout.

**NUR am Gerät prüfbar:** Geräteuhr auf 2021 stellen, Projektformular öffnen → Datumsfeld leer + Banner; „Datum/
Uhrzeit einstellen" öffnet die OS-Einstellung; Speichern mit 2021 wird geblockt. Bei korrekter Uhr alles wie bisher.

---

## Geänderte/neue Dateien
```
app/build.gradle.kts                                              (BUILD_YEAR + import)
app/src/main/java/.../ui/localization/LocalizationManager.kt      (3 Keys × DE/EN)
app/src/main/java/.../data/repository/ProjectRepository.kt        (AP2/AP3 Bucket-Signatur)
app/src/main/java/.../ui/screens/inspection/InspectionScreen.kt   (AP2/AP3 Bucket-Anlage)
app/src/main/java/.../ui/screens/projects/ProjectFormScreen.kt    (AP1 Entfernung + AP5 Banner/Snackbar)
app/src/main/java/.../ui/screens/projects/ProjectFormViewModel.kt (AP5 Guard-Verdrahtung)
app/src/main/java/.../ui/screens/projects/InspectionDateGuard.kt  (AP5 — NEU, reine Funktionen)
app/src/main/java/.../export/ProjectExportService.kt              (AP4 Unicode-Font)
app/src/test/java/.../ui/screens/projects/InspectionDateGuardTest.kt   (AP5 — NEU, 10 Tests)
app/src/test/java/.../data/repository/CapturePersistenceTest.kt        (AP2/AP3 — +1 Test)
```

## Nach dieser Runde offen (Geräteabnahme vor Freigabe an Louis)
- AP1 Durchtipp-Test auf ONE-HW, AP3 mit realer C10/C18, AP4 PDF-Pfeil/Umlaute im Viewer, AP5 mit auf 2021 gestellter Uhr.
- Portal-Upload separat durch Thomas: `tools\publish-one-release.ps1 -VersionName 0.5.6 -VersionCode 506` (NICHT mit `-SkipBuild`).
