# Claude-Code-Auftrag — Louis-Feedback 0.5.5 (B2 + M2/M3/M4)

Repo: `C:\Projekte\drainq.one`. Branch: `feature/dual-mode` (bereits ausgecheckt, `ec52c0b`).
Modell/Effort: Opus, hoher Effort (B2 gerätesensibel, M4 Font-Rendering).
Bauen: `.\gradlew.bat assembleDebug` (OHNE Keystore — Beta-Regel). Version über Env `APP_VERSION_NAME`/`APP_VERSION_CODE` setzen (sonst baut Gradle 0.4.1/401). Ziel-Version diese Runde: **0.5.6 / 506**.
Regel: `OneInternalHardwareService` NICHT anfassen. TWO existiert nicht mehr. Jede Änderung mit Unit-Test wo sinnvoll; adversariale Selbstreview am Ende.

Quelle der Befunde: Louis Wigman, Mail 10.07.2026. Ursachen unten sind am Code (10.07.) verifiziert — nicht neu raten, sondern umsetzen und am Gerät beweisen.

---

## AP1 — B2: Feldeingabe hängt (Durchmesser/Länge/Inspektor), C18→C10-Workaround
**Datei:** `app/src/main/java/com/uip/oneapp/ui/screens/projects/ProjectFormScreen.kt`

**Ursache (verifiziert):** Der W3-Fix (`cfc5f45`) gated `dismissKeyboardOnScroll.onPreScroll` auf `NestedScrollSource.UserInput` in der Annahme, `bringIntoView`/IME-Resize melde `SideEffect`. Auf ONE/RK3588 + Compose 1.7 trifft das nicht zuverlässig zu: beim `moveFocus(FocusDirection.Down)`-Sprung (ImeAction.Next) reißt das Auto-Scroll den gerade gesetzten Fokus über `focusManager.clearFocus()` ab → Feld „geht auf und sofort wieder zu". Der Kopfwechsel C18→C10 erzwingt Recompose und überdeckt es.

**Umsetzung (eindeutig):** Die `nestedScroll(dismissKeyboardOnScroll)`-Verdrahtung und das `dismissKeyboardOnScroll`-Objekt entfernen. Tastatur-Schließen NUR noch über:
- den bereits vorhandenen `KeyboardHideButton` in der TopBar (bleibt), und
- `detectTapGestures(onTap = { focusManager.clearFocus() })` auf Freifläche (bleibt).
Kein automatisches `clearFocus()`/`keyboardController?.hide()` mehr während Scroll/Fokuswechsel. `verticalScroll` bleibt.

**Verifikation (Pflicht am Gerät, Unit-Test beweist das NICHT):** 3 NEUE HD-Projekte anlegen, je Durchmesser → Länge → Startpunkt → Endpunkt direkt über „Weiter" durchtippen, OHNE SD-Umweg, OHNE Kopfwechsel. Kein Feld darf den Fokus verlieren.

---

## AP2 — M3: „Schnellaufnahme" wird in EN nicht übersetzt (Dateiname + Report)
Hartkodierter deutscher String im Quick-Capture-Pfad. Grep im Repo nach `Schnellaufnahme` (Kotlin) — der Tages-Bucket heißt Muster `Schnellaufnahme_ddMMyy`.

**Umsetzung:** Label über `LocalizationManager`/`S("...")` führen (neuer Key, z.B. `quick_capture_bucket`), EN = „Quick capture". Dateinamens-Präfix sprachneutral halten oder EN-Fallback — ABER Bestandslogik nicht brechen: prüfen, ob vorhandene Ordner/Anzeige weiter gefunden werden (Migration/Abwärtskompatibilität des Bucket-Namens bedenken; wenn der Ordnername als Schlüssel dient, Anzeige-Label von Speicher-Pfad trennen).

## AP3 — M2: Kameratyp fehlt im PDF bei Quick-Capture-Projekten
`ProjectExportService.kt` rendert `addPipeRow(t("field_camera_type"), project.kameratyp)` bereits — die Zeile ist leer, weil Quick-Capture-Projekte `kameratyp` nie füllen (die Auto-Vorbelegung sitzt nur in `ProjectFormScreen`, das Quick Capture umgeht).

**Umsetzung:** In der Quick-Capture-Projektanlage den erkannten Kopf setzen: `CameraHead.from(hardwareState…cameraId)` → C10/C18-Label in `project.kameratyp` schreiben (gleiche Quelle/Regel wie `cameraTypePrefill` in ProjectFormScreen; UNKNOWN → leer lassen, nichts raten). Grep den Erzeuger des Quick-Capture-`ProjectEntity`.

## AP4 — M4: Pfeil „→" erscheint nicht im PDF-Report
**Datei:** `app/src/main/java/com/uip/oneapp/export/ProjectExportService.kt`

**Ursache (verifiziert):** `→`/`→` steht in `addPipeRow(t("pdf_route_label"), …)`, in `routeLabel` (Rohrprofil) und in `buildProjectInfoText`. Der Report setzt aber keine Unicode-Schrift → iText-Default (Helvetica/WinAnsi) enthält U+2192 nicht und lässt es still weg. (Louis sieht „Start Ende" ohne Trenner.)

**Umsetzung (eindeutig):** Eine Unicode-TTF einbetten und für die Report-Absätze als Font setzen (Projekt hat Inter-Schnitte unter `app/src/main/res/font/inter_*`; alternativ eine schlanke TTF unter `assets/` bündeln). `PdfFontFactory.createFont(ttf, PdfEncodings.IDENTITY_H, EmbeddingStrategy.PREFER_EMBEDDED)`, Font an `document.setFont(...)` bzw. an die betroffenen `Paragraph`/`Cell` hängen. Prüfen, dass Umlaute + `→` erscheinen. KEIN ASCII-„->"-Workaround (Louis will den Pfeil).

**Verifikation:** Report eines Projekts mit Start/Ende rendern, PDF öffnen: Route zeigt „Start → Ende", Umlaute korrekt, Rohrprofil-Kopf ebenso.

---

## AP5 — B1-Interim: kein stilles Falschdatum (Geräteuhr auf 2021)
**Datei:** `app/src/main/java/com/uip/oneapp/ui/screens/projects/ProjectFormViewModel.kt` (+ ProjectFormScreen für Hinweis).

**Ursache (verifiziert):** `inspektionsdatum = LocalDate.now()` wird EINMALIG bei Konstruktion gesetzt. Steht die Android-Systemuhr auf 2021 (RTC-Reset/kein NTP), friert 2021 ein; der Menü-„Auto"-Schalter setzt die Systemzeit nicht (echte Auto-Zeit braucht Device-Owner → separater Golden-Image-Auftrag, NICHT hier).

**Umsetzung (Interim, ohne Device-Owner, wirkt auf allen Geräten):**
- Plausibilitätsprüfung: ist `LocalDate.now().year < 2015`, das Datumsfeld NICHT still mit dem Falschdatum befüllen. Stattdessen sichtbarer Warnhinweis am Formular („Geräteuhr falsch — bitte Datum/Zeit prüfen") + Verknüpfung auf den vorhandenen Datum-Settings-Knopf (`ACTION_DATE_SETTINGS`).
- `saveProject()` blockiert bei implausiblem Datum (Jahr < 2015) mit derselben Meldung, statt einen 2021-Report zu erzeugen.
- Plausibel = normal wie bisher (kein Verhaltenswechsel).
- Unit-Test: Jahr < 2015 → Guard greift (kein Speichern); Jahr ≥ 2015 → unverändert.

**Bewusst NICHT hier:** echtes `setAutoTimeEnabled(true)` als Device-Owner — kommt mit dem Golden-Image-Rollout.

---

## Abschluss
- Build grün (`assembleDebug`), Unit-Tests grün.
- Ergebnisbericht `RESULT_LOUIS_10-07_B2_REPORT.md` (Repo-Root): je AP was geändert (Datei:Zeile), was der Unit-Test deckt, was NUR am Gerät prüfbar ist.
- Kein Merge, kein Tag. Auf `feature/dual-mode` committen + pushen.
- Portal-Upload macht Thomas separat via `tools\publish-one-release.ps1 -VersionName 0.5.6 -VersionCode 506` (NICHT `-SkipBuild` mit neuer Version kombinieren).

## Explizit NICHT in dieser Runde
- Echte Datum-Automatik (`setAutoTimeEnabled` als Device-Owner) — kommt mit dem Golden-Image-Rollout. NUR der Guard (AP5) ist hier drin.
- M1 (Video 2. Wiedergabe schneller) — Ursache noch nicht am Code verifiziert.
- Hardbutton-Cluster (Welle 3) und USB-Export (Welle 4) — später.
