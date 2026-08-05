/clean
/goal: Hardbutton Licht + Sonde — 1. Druck öffnet, jeder weitere Druck zyklt den Wert, 3 s ohne Druck blendet aus
/model: sonnet
/effort: mittel

# Reparaturauftrag — Hardbutton Licht/Sonde: öffnen → zyklen → 3 s Auto-Hide

Quelle: CEO-Spec 12.07.2026 (ex-Louis-Welle-3 / H3). H1/H2 (Schaden-/Galerie-Taste) sind erledigt — NICHT anfassen. Nur die Licht- und Sonde-Hardtaste.
Branch: `feature/dual-mode`. Kein Merge, kein Tag. Nur die zwei unten genannten Dateien anfassen, sonst nichts.

## Soll-Verhalten [eindeutig]

**Licht-Hardtaste (F1):**
- Popup ZU → 1. Druck öffnet den Licht-Slider mit dem AKTUELLEN Stand. KEINE Wertänderung, kein Send.
- Popup OFFEN → jeder weitere Druck +10 % (nach 100 → 0), Wert senden.
- 3 s ohne Druck → Popup ausblenden.

**Sonde-Hardtaste (F2):**
- Popup ZU → 1. Druck öffnet die Frequenzwahl (aktuelle Frequenz bleibt hervorgehoben). Kein Send.
- Popup OFFEN → jeder weitere Druck zyklt zur nächsten Frequenz UND sendet sie. Zyklus **inkl. Off**: Off → 33 kHz → 640 Hz → 512 Hz → Off.
  (Falls Off NICHT im Zyklus sein soll: `selectableCodes` [1,2,3] statt [0,1,2,3] — Default = inkl. Off.)
- 3 s ohne Druck → Popup ausblenden.
- Die vorhandene Antipp-Liste im Popup bleibt (Direktauswahl weiter möglich).

## Ist-Stand (Anker)

`ui/screens/inspection/InspectionScreen.kt`:
- Z.184 `var lightLevel by remember { … crawler.frontLightPower … }`
- Z.253/254 `showLightPopup` / `showSondePopup`
- Z.256-258 Licht-Auto-Hide: `LaunchedEffect(showLightPopup, lightLevel) { if (showLightPopup) { delay(4000); showLightPopup = false } }`
- Z.512-517 LIGHT-Handler: `lightLevel = nextLightLevel(lightLevel); hardwareService.sendLightPower(lightLevel); showLightPopup = true` — zykelt FALSCH schon beim 1. Druck.
- Z.518-525 SONDE-Handler: nur `showBottomBar = true; lastBottomBarMs = …; showSondePopup = true` — kein Zyklus, kein Auto-Hide.
- Z.794-833 Licht-Popup (Slider), Z.836-871 Sonde-Popup (Frequenzliste, `sendFrequency` beim Antippen).

`ui/screens/inspection/InspectionControls.kt` (reine, unit-getestete Helfer):
- `LightCycle = [0,30,60,100]`, `nextLightLevel(current)` (0→30→60→100→0).
- `isSondeFrequencyActive(optionCode, rxLabel)` (normalisierter Vergleich gegen `SondeFrequency.name`).

`network/internal/SondeFrequency.kt`: `OFF=0`, `selectableCodes=[1,2,3]`, `name(code)` (0=Off,1=33 kHz,2=640 Hz,3=512 Hz).

## Fix

### 1) InspectionControls.kt — reine Helfer + Unit-Tests
- NEU:
```kotlin
/** Nächste Licht-Stufe: +10 %, nach 100 % wieder 0. Snappt krumme Slider-Werte auf die nächste 10er-Stufe. */
fun nextLightStep(current: Int): Int =
    if (current >= 100) 0 else ((current / 10) * 10 + 10).coerceAtMost(100)

/** Nächste Sonde-Frequenz im Zyklus Off→33kHz→640Hz→512Hz→Off. currentRxLabel = RX-Anzeige (crawler.sondeFrequency). */
fun nextSondeCode(currentRxLabel: String?): Int {
    val cycle = listOf(SondeFrequency.OFF) + SondeFrequency.selectableCodes  // [0,1,2,3]
    fun norm(s: String) = s.filterNot(Char::isWhitespace).lowercase()
    val activeLabel = currentRxLabel?.takeIf { it.isNotBlank() } ?: SondeFrequency.name(SondeFrequency.OFF)
    val current = cycle.firstOrNull { norm(SondeFrequency.name(it)) == norm(activeLabel) } ?: SondeFrequency.OFF
    return cycle[(cycle.indexOf(current) + 1) % cycle.size]
}
```
- `LightCycle` / `nextLightLevel` entfernen, sobald nach der Umstellung ungenutzt (samt zugehöriger Tests); falls noch woanders referenziert, belassen.
- Unit-Tests: `nextLightStep` (0→10, 30→40, 95→100, 100→0, 0-Grenze), `nextSondeCode` (Off→33 kHz, 33 kHz→640 Hz, 640 Hz→512 Hz, 512 Hz→Off; leeres/null Label → 33 kHz).

### 2) InspectionScreen.kt — Handler + Auto-Hide
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
(`sondeTick` ändert sich bei jedem Sonde-Druck → der 3-s-Timer setzt bei jedem Weiterdruck neu auf; beim 1. Druck greift zusätzlich der `showSondePopup`-Schlüssel.)
- Sonde-Popup-UI (Z.836-871) unverändert lassen — Antippen bleibt Direktauswahl.

## Verifikation
- Unit: `nextLightStep` + `nextSondeCode` grün.
- Am Gerät (Pflicht):
  - Licht: 1. F1-Druck öffnet den Slider OHNE Wertsprung; jeder weitere +10 %, nach 100 wieder 0; 3 s ohne Druck blendet aus.
  - Sonde: 1. F2-Druck öffnet die Wahl; jeder weitere wechselt die Frequenz (Off→33→640→512→Off) und sendet; 3 s ohne Druck blendet aus.

## Nicht anfassen
Andere Hardtasten (Power/Aufnahme/Stop/Foto/Galerie/Settings — laut Thomas ok), die Softbutton-Leiste, OSD, die RX-Anzeige-/Highlight-Logik (`isSondeFrequencyActive`).
