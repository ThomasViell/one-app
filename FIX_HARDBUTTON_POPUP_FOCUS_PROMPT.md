/clean
/goal: Licht/Sonde-Hardtaste toggelt nicht bei offenem Popup — Popups nicht-fokussierbar machen, damit F1/F2 weiter ankommen
/model: sonnet
/effort: mittel

# Reparaturauftrag — Hardbutton-Popup stiehlt den Tastenfokus

Quelle: Gerätetest 0.5.9 (12.07.). Licht- und Sonde-Popup öffnen beim 1. Druck, aber weitere Drücke zykeln den Wert NICHT (Auto-Hide 3 s funktioniert). Nur diese eine Datei anfassen.
Branch: `feature/dual-mode`. Kein Merge, kein Tag.

## Ursache [Sicher — Code verifiziert]
Hardtasten kommen über `MainActivity.onKeyDown` → `HardwareKeyBus.emit`. `onKeyDown` feuert nur, wenn das **Activity-Fenster** den Tastenfokus hat. Die beiden Popups in `InspectionScreen.kt` sind `PopupProperties(focusable = true)` → offenes Popup = eigenes Fenster mit Tastenfokus → die nächsten F1/F2 gehen dorthin statt an `onKeyDown` → `runHwButton` läuft nicht erneut → kein Toggle. Der 3-s-Auto-Hide ist eine Coroutine (unabhängig) und läuft weiter. (Deckt sich mit Louis' „Licht nur nach Ausblenden + zweitem Druck".)

## Fix [eindeutig]
`ui/screens/inspection/InspectionScreen.kt`:
- Z.806 (Licht-Popup): `properties = PopupProperties(focusable = true)` → `properties = PopupProperties(focusable = false)`
- Z.852 (Sonde-Popup): `properties = PopupProperties(focusable = true)` → `properties = PopupProperties(focusable = false)`

Nicht-fokussierbare Popups empfangen weiterhin Touch (Slider-Drag, Frequenz-Antippen) — nur den Tastenfokus geben sie nicht mehr, sodass F1/F2 im Activity-Fenster bleiben.

## Verifikation (Pflicht am Gerät)
- **Licht:** F1 öffnet Slider; jeder weitere F1-Druck +10 % (100→0); 3 s blenden aus. Slider zusätzlich per Touch ziehbar.
- **Sonde:** F2 öffnet Wahl; jeder weitere F2-Druck zyklt (Off→33→640→512→Off) und sendet; 3 s blenden aus. Frequenz zusätzlich per Antippen wählbar.
- Auto-Hide (3 s) unverändert.

## Falls Touch im Popup nach der Umstellung NICHT ankommt (gerätespezifisch)
Rückfallebene: das jeweilige Popup als In-Window-Overlay im selben Activity-Fenster umsetzen (wie der Aufnahme-Dialog in `InspectionScreen`, siehe MainActivity-Kommentar „In-Window-Overlay") statt als separates `Popup`-Fenster — löst Fokus- UND Kiosk-Taskbar-Problem. Nur ziehen, wenn `focusable = false` den Touch bricht.

## Nicht anfassen
Die Handler-Logik (open→cycle), `nextLightStep`/`nextSondeCode`, die Auto-Hide-Effekte, andere Hardtasten.
