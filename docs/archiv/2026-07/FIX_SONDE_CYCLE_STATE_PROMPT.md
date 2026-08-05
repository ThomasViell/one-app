/clean
/goal: Sonde-Zyklus toggelt nicht (immer 33kHz) — lokale MutableState statt eingefrorenem crawler
/model: sonnet
/effort: mittel

# Reparaturauftrag — Sonde-Frequenz zykelt nicht (bleibt auf 33 kHz)

Quelle: Gerätetest 0.5.10 (12.07.). Licht toggelt jetzt sauber. Sonde: 1. Druck öffnet, 2. Druck springt IMMER auf 33 kHz, weitere Drücke ohne Wirkung. Auto-Hide 3 s ok. Nur InspectionScreen.kt + InspectionControls.kt anfassen.
Branch: `feature/dual-mode`. Kein Merge, kein Tag.

## Ursache [Sicher — Code verifiziert]
- `InspectionScreen.kt` Z.171-173: `val hwState by hardwareService.hardwareState.collectAsState()` → `val crawler = hwState.crawlerController` — `crawler` ist ein **roher val**, bei jeder Recomposition neu.
- Z.512 `val runHwButton = { b -> … }`, Z.531 SONDE-Zweig ruft `nextSondeCode(crawler.sondeFrequency)`.
- Z.551 `LaunchedEffect(Unit) { HardwareKeyBus.events.collect { runHwButton(it) } }` fängt `runHwButton` **einmalig** (erstes Compose) ein → das Lambda schließt über den **ersten** `crawler` (stale, sondeFrequency = Off). Jeder Druck liest „Off" → `nextSondeCode` gibt 33 kHz. Kein Fortschreiten.
- Licht funktioniert, weil `lightLevel` eine **MutableState** ist (im gefangenen Lambda live gelesen/geschrieben). Genau das braucht die Sonde auch.

## Fix

### 1) InspectionControls.kt — Helfer auf Int-Code umstellen + Init-Helfer
`nextSondeCode(currentRxLabel: String?)` ersetzen durch zwei Funktionen:
```kotlin
/** Sonde-Zyklus [0,1,2,3] = Off→33kHz→640Hz→512Hz→Off. Nächster Code nach dem aktuellen. */
fun nextSondeCode(current: Int): Int {
    val cycle = listOf(SondeFrequency.OFF) + SondeFrequency.selectableCodes   // [0,1,2,3]
    val idx = cycle.indexOf(current).let { if (it < 0) 0 else it }
    return cycle[(idx + 1) % cycle.size]
}

/** RX-Label → Code (für die Initialbelegung des lokalen TX-States). Fallback Off. */
fun sondeCodeFromLabel(rxLabel: String?): Int {
    val cycle = listOf(SondeFrequency.OFF) + SondeFrequency.selectableCodes
    fun norm(s: String) = s.filterNot(Char::isWhitespace).lowercase()
    val activeLabel = rxLabel?.takeIf { it.isNotBlank() } ?: SondeFrequency.name(SondeFrequency.OFF)
    return cycle.firstOrNull { norm(SondeFrequency.name(it)) == norm(activeLabel) } ?: SondeFrequency.OFF
}
```
Unit-Tests: `nextSondeCode` (0→1,1→2,2→3,3→0, unbekannt/-1→1); `sondeCodeFromLabel` ("33 kHz"→1, "33kHz"→1, "512 Hz"→3, null→0, "Off"→0). `isSondeFrequencyActive` bleibt unverändert (wird evtl. nicht mehr gebraucht → nur entfernen, wenn ungenutzt).

### 2) InspectionScreen.kt — lokale Sonde-MutableState (wie lightLevel)
- Neben `showSondePopup`/`sondeTick` (Z.~254-256) NEU:
```kotlin
var sondeTxCode by remember { mutableStateOf(sondeCodeFromLabel(crawler.sondeFrequency)) }
```
- SONDE-Handler (Z.524-531) Zyklus-Zweig auf die lokale State umstellen:
```kotlin
HwButton.SONDE -> {
    showBottomBar = true
    lastBottomBarMs = System.currentTimeMillis()
    sondeTick++
    if (!showSondePopup) {
        showSondePopup = true
    } else {
        sondeTxCode = nextSondeCode(sondeTxCode)        // lokal weiterschalten (live in MutableState)
        hardwareService.sendFrequency(sondeTxCode)
    }
}
```
- Sonde-Popup (Z.~858-866): Antippen setzt die lokale State, Highlight aus der lokalen State (sofortiges Feedback, unabhängig vom RX-Echo):
  - Highlight-Zeile `val active = isSondeFrequencyActive(f, crawler.sondeFrequency)` → `val active = (f == sondeTxCode)`
  - Im `TextButton onClick` vor `hardwareService.sendFrequency(f)` ergänzen: `sondeTxCode = f`
- OSD-Zeile `sondeMode = crawler.sondeFrequency ?: "—"` (Z.683) NICHT ändern (zeigt bewusst den RX-Wert).

## Verifikation (Pflicht am Gerät)
- Sonde: 1. F2-Druck öffnet; jeder weitere F2-Druck schaltet **eine Stufe weiter**: Off→33 kHz→640 Hz→512 Hz→Off; das Popup markiert die jeweils gewählte Frequenz; die Frequenz wird gesendet. Antippen wählt weiterhin direkt. 3 s Auto-Hide unverändert.
- Licht unverändert grün.

## Nicht anfassen
Licht-Handler/`nextLightStep`, `focusable=false`, Auto-Hide-Effekte, andere Hardtasten, OSD-RX-Anzeige.
