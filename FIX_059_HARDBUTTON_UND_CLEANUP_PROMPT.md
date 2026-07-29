/clean
/goal: 0.5.9 — Hardbutton Licht/Sonde-Bedienung + zwei ersatzlose UI-Entfernungen (Sprach-Neustart-Dialog, Inspektionsmethode-Sektion)
/model: sonnet
/effort: mittel

# Sammel-Auftrag 0.5.9 — drei unabhängige Änderungen in einem Lauf

Quelle: CEO-Entscheide 12.07.2026. Branch `feature/dual-mode`. Kein Merge, kein Tag.
Drei Teile, unterschiedliche Dateien, kein Überschneiden. Nur die je genannten Dateien anfassen, sonst nichts. Am Ende alles zusammen committen (kein Merge/Tag).

Betroffene Dateien gesamt:
- Teil A: `ui/screens/inspection/InspectionScreen.kt`, `ui/screens/inspection/InspectionControls.kt`
- Teil B: `ui/screens/settings/SettingsScreen.kt`, `ui/localization/LocalizationManager.kt`
- Teil C: `ui/screens/projects/ProjectFormScreen.kt`

---

## TEIL A — Hardbutton Licht/Sonde: öffnen → zyklen → 3 s Auto-Hide

### Soll-Verhalten [eindeutig]
**Licht-Hardtaste (F1):**
- Popup ZU → 1. Druck öffnet den Licht-Slider mit dem AKTUELLEN Stand. KEINE Wertänderung, kein Send.
- Popup OFFEN → jeder weitere Druck +10 % (nach 100 → 0), Wert senden.
- 3 s ohne Druck → Popup ausblenden.

**Sonde-Hardtaste (F2):**
- Popup ZU → 1. Druck öffnet die Frequenzwahl (aktuelle Frequenz bleibt hervorgehoben). Kein Send.
- Popup OFFEN → jeder weitere Druck zyklt zur nächsten Frequenz UND sendet sie. Zyklus **inkl. Off**: Off → 33 kHz → 640 Hz → 512 Hz → Off. (Falls Off NICHT gewünscht: nur `selectableCodes` [1,2,3]. Default = inkl. Off.)
- 3 s ohne Druck → Popup ausblenden.
- Vorhandene Antipp-Liste im Popup bleibt (Direktauswahl weiter möglich).

### Ist-Stand (Anker) — `InspectionScreen.kt`
- Z.184 `var lightLevel by remember { … crawler.frontLightPower … }`
- Z.253/254 `showLightPopup` / `showSondePopup`
- Z.256-258 Licht-Auto-Hide: `LaunchedEffect(showLightPopup, lightLevel) { if (showLightPopup) { delay(4000); showLightPopup = false } }`
- Z.512-517 LIGHT-Handler: `lightLevel = nextLightLevel(lightLevel); hardwareService.sendLightPower(lightLevel); showLightPopup = true` — zykelt FALSCH schon beim 1. Druck.
- Z.518-525 SONDE-Handler: nur `showBottomBar = true; lastBottomBarMs = …; showSondePopup = true`.
- Z.794-833 Licht-Popup (Slider), Z.836-871 Sonde-Popup (Frequenzliste, `sendFrequency` beim Antippen).
`InspectionControls.kt`: `LightCycle=[0,30,60,100]`, `nextLightLevel`, `isSondeFrequencyActive`.
`network/internal/SondeFrequency.kt`: `OFF=0`, `selectableCodes=[1,2,3]`, `name(code)`.

### Fix A1 — InspectionControls.kt (reine Helfer + Unit-Tests)
```kotlin
/** Nächste Licht-Stufe: +10 %, nach 100 % wieder 0. Snappt krumme Slider-Werte auf die nächste 10er-Stufe. */
fun nextLightStep(current: Int): Int =
    if (current >= 100) 0 else ((current / 10) * 10 + 10).coerceAtMost(100)

/** Nächste Sonde-Frequenz im Zyklus Off→33kHz→640Hz→512Hz→Off. currentRxLabel = crawler.sondeFrequency. */
fun nextSondeCode(currentRxLabel: String?): Int {
    val cycle = listOf(SondeFrequency.OFF) + SondeFrequency.selectableCodes  // [0,1,2,3]
    fun norm(s: String) = s.filterNot(Char::isWhitespace).lowercase()
    val activeLabel = currentRxLabel?.takeIf { it.isNotBlank() } ?: SondeFrequency.name(SondeFrequency.OFF)
    val current = cycle.firstOrNull { norm(SondeFrequency.name(it)) == norm(activeLabel) } ?: SondeFrequency.OFF
    return cycle[(cycle.indexOf(current) + 1) % cycle.size]
}
```
`LightCycle`/`nextLightLevel` entfernen, sobald ungenutzt (samt Tests). Unit-Tests: `nextLightStep` (0→10, 30→40, 95→100, 100→0), `nextSondeCode` (Off→33 kHz, 33 kHz→640 Hz, 640 Hz→512 Hz, 512 Hz→Off; null/leer → 33 kHz).

### Fix A2 — InspectionScreen.kt
- NEU State neben Z.254: `var sondeTick by remember { mutableStateOf(0) }`.
- LIGHT-Handler (Z.512-517) ersetzen:
```kotlin
HwButton.LIGHT -> {
    if (!showLightPopup) {
        showLightPopup = true                     // 1. Druck: nur öffnen, Wert unverändert
    } else {
        lightLevel = nextLightStep(lightLevel)    // weitere Drücke: +10 %, 100→0
        hardwareService.sendLightPower(lightLevel)
    }
}
```
- SONDE-Handler (Z.518-525) ersetzen (Leisten-Einblenden aus T9 behalten):
```kotlin
HwButton.SONDE -> {
    showBottomBar = true
    lastBottomBarMs = System.currentTimeMillis()
    sondeTick++                                   // Reset-Schlüssel fürs Auto-Hide
    if (!showSondePopup) {
        showSondePopup = true                     // 1. Druck: nur öffnen
    } else {
        hardwareService.sendFrequency(nextSondeCode(crawler.sondeFrequency))
    }
}
```
- Licht-Auto-Hide (Z.256-258): `delay(4000)` → `delay(3000)`.
- NEU Sonde-Auto-Hide daneben:
```kotlin
LaunchedEffect(showSondePopup, sondeTick) {
    if (showSondePopup) { kotlinx.coroutines.delay(3000); showSondePopup = false }
}
```
- Sonde-Popup-UI (Z.836-871) unverändert lassen.

---

## TEIL B — Sprach-Neustart-Dialog ersatzlos entfernen

Grund: Lokalisierung ist ein reaktiver StateFlow (`S()`), kein Android-Ressourcen-Locale → die UI schaltet live um, ein Neustart ist unnötig. Der `killProcess`-Relaunch funktioniert am Gerät zudem nicht zuverlässig. Also: Dialog + Neustart raus, Dropdown schaltet direkt live + persistiert.

### Ist-Stand — `SettingsScreen.kt`
- Sprach-`DqDropdownRow`: `onSelect = { code -> pendingLangCode = code }`.
- Block `pendingLangCode?.let { langCode -> AlertDialog(… „Neustart erforderlich" …) }` mit `confirmButton` (`scope.launch { setLanguageAwait(...) … startActivity … Process.killProcess(myPid()) }`) und `dismissButton`/`onDismissRequest` (`setLanguage`).

### Fix B
- `onSelect` direkt auf `LocalizationManager.setLanguage(context, code)` umstellen (live + persist), KEIN `pendingLangCode` mehr setzen.
- Den kompletten `pendingLangCode?.let { … AlertDialog … }`-Block entfernen (inkl. `startActivity`/`killProcess`).
- Die State-Variable `pendingLangCode` entfernen.
- In `LocalizationManager.kt` `setLanguageAwait` entfernen (einziger Aufrufer war der gelöschte confirmButton). `setLanguage`, `init`, `getString`, `S` unverändert lassen.

### Verifikation B
Am Gerät: Sprache im Dropdown wählen → Oberfläche kippt SOFORT live, ohne Dialog, ohne Neustart; nach manuellem App-Neustart bleibt die gewählte Sprache erhalten.

---

## TEIL C — Inspektionsmethode-Sektion ersatzlos entfernen

Grund: App läuft auf der ONE, Kopf ist immer autoerkannt; „Inspektionssystem" war ohnehin fix „DrainQ ONE". Manuelle Auswahl ist redundant. Die Auto-Vorbelegung von `kameratyp` BLEIBT (füllt das Feld weiter, PDF unverändert).

### Ist-Stand — `ProjectFormScreen.kt`
- Z.69 `var kameratypExpanded by remember { mutableStateOf(false) }`
- Z.170 `val kameratypen = listOf(S("camera_c10"), S("camera_c18"))`
- Z.172-198 `LaunchedEffect(detectedHead, editProjectId)` + `detectedHead`-Beobachtung → `cameraTypePrefill` setzt `viewModel.kameratyp`. **BLEIBT.**
- Card-Block ~Z.750-822: Header `S("inspection_method")` (Videocam-Icon), readonly-Feld `value = S("inspection_system_value")` / label `S("field_inspection_system")`, dann `ExposedDropdownMenuBox` mit `S("field_camera_type")` (Kameratyp C10/C18).

### Fix C
- Den kompletten **Card-Block mit dem Header `S("inspection_method")`** entfernen (Card-Wrapper + Column + Inspektionssystem-Feld + Kameratyp-Dropdown, ~Z.750-822), inkl. evtl. umgebendem Spacer.
- Die dann ungenutzten `kameratypExpanded` (Z.69) und `kameratypen` (Z.170) entfernen.
- **Die Prefill-Logik Z.172-198 NICHT anfassen** — `viewModel.kameratyp` bleibt auto-gesetzt, wird gespeichert und im PDF (`ProjectExportService`) angezeigt.

### Verifikation C
Am Gerät: Neu-Projekt-Formular hat keinen „Inspektionsmethode"-Abschnitt mehr. Mit verbundener Kamera bekommt das Projekt automatisch den erkannten Kameratyp; im PDF steht der Kameratyp weiterhin.

---

## Gesamt
- Build grün, alle Unit-Tests grün (Teil A ergänzt neue).
- Am Ende committen (kein Merge, kein Tag).
- NICHT anfassen: andere Hardtasten, Softbutton-Leiste, OSD, RX-Highlight-Logik, PDF-Report-Renderer, die Sprach-Live-Mechanik (`S`/`getString`/`init`/`translations`), die Kameratyp-Auto-Vorbelegung.
