/clean
/goal: Speicheranzeige als Grafik in DrainQ — intern + USB als Füllstandsbalken auf dem Home-Screen
/model: sonnet
/effort: mittel

# Feature — Speicheranzeige (intern + USB) als Grafik

Quelle: Louis-Feedback (Welle 4) + CEO 12.07. („USB bitte auch einbauen"). Louis will die Speicherinfo als Grafik in DrainQ, nicht nur in Android.
Branch: `feature/dual-mode`. Kein Merge, kein Tag.

## Soll
Auf dem **Home-Screen** die bisherige nackte „Speicher frei"-Kennzahl durch eine **„Speicher"-Karte** ersetzen, mit zwei Füllstandsbalken:
- **Intern**: Balken (Anteil belegt) + Text „X,X GB frei von Y GB".
- **USB**: nur wenn ein Stick gemountet ist → Balken + „X,X GB frei von Y GB". Kein Stick → gedämpfte Zeile „Kein USB-Stick".
- Balkenfarbe nach Füllstand: grün < 80 % belegt, amber 80–95 %, rot > 95 %.
- Platzierung: Home, direkt unter der Kennzahlen-Zeile.

## Ist-Stand (Anker)
`ui/screens/home/HomeScreen.kt`:
- Z.~62-65: `val freeGb = remember(context) { StatFs(context.filesDir.absolutePath).availableBytes / 1_000_000_000L }`
- KPI-Row: `StatCard(..., S("nav_projects"), ...)`, `StatCard(..., S("stat_today"), ...)`, `StatCard(..., S("storage_free"), "$freeGb GB")` — die dritte (Speicher) entfernen.
- `private fun StatCard(...)`, `DqCard`, `DqIcon`, `S(...)` vorhanden.

USB-Erkennung wiederverwenden: `export/UsbExportService.kt` → `findUsbVolumes(): List<UsbVolume>` (StorageManager, `isRemovable && MEDIA_MOUNTED`, `UsbVolume(name, rootDir: File)`, API 30+ = ONE/Android 12). KEIN MANAGE_EXTERNAL_STORAGE nötig (nur lesen von `StatFs`).

## Umsetzung
### 1) Reine Helfer (unit-testbar) — neue Datei `ui/screens/home/StorageInfo.kt` oder in HomeScreen
```kotlin
data class VolumeUsage(val freeBytes: Long, val totalBytes: Long) {
    val usedFraction: Float get() = if (totalBytes <= 0) 0f else ((totalBytes - freeBytes).toFloat() / totalBytes).coerceIn(0f, 1f)
}
fun formatGb(bytes: Long): String = String.format(java.util.Locale.US, "%.1f GB", bytes / 1_000_000_000.0)
```
- Balkenfarbe als Composable-nahe Ableitung (reine Funktion auf Fraction → Enum/DrainQTheme-Farbe): grün <0.8, amber 0.8–0.95, rot >0.95.
- Unit-Tests: `usedFraction` (0-Total→0, halb voll→0.5, Clamp), `formatGb` (Locale.US „1.5 GB"), Farb-Schwellen (0.5→grün, 0.85→amber, 0.97→rot).

### 2) Datenerhebung (Android, off-main)
- Neuer Zustand in HomeScreen: `var internal by remember { mutableStateOf<VolumeUsage?>(null) }`, `var usb by remember { mutableStateOf<Pair<String, VolumeUsage>?>(null) }` (Name + Usage; null = kein Stick).
- Erhebung in `LaunchedEffect` beim Betreten + bei `Lifecycle.Event.ON_RESUME` (Stick wird evtl. eingesteckt, während Home offen ist), in `withContext(Dispatchers.IO)`:
  - Intern: `StatFs(context.filesDir.absolutePath)` → `VolumeUsage(availableBytes, totalBytes)`.
  - USB: `UsbExportService(context).findUsbVolumes().firstOrNull()` → `StatFs(vol.rootDir.absolutePath)` → `Pair(vol.name, VolumeUsage(...))`; sonst null.
  - Robuster `runCatching` je Messung (nie crashen, im Fehlerfall null / 0).
- Lifecycle: `androidx.lifecycle.compose.LocalLifecycleOwner` + `DisposableEffect` mit `LifecycleEventObserver` auf `ON_RESUME` → Neuerhebung anstoßen.

### 3) UI — „Speicher"-Karte
- Unter der KPI-Row eine `DqCard(fillMaxWidth)`: Titelzeile (`DqIcon("save" oder passendes Icon)` + `S("storage_title")`), dann:
  - Intern-Zeile: `S("storage_internal")`, `LinearProgressIndicator(progress = usage.usedFraction, color = <fuellstandsfarbe>)`, Text `S("storage_free_of")` mit `{free}`/`{total}` ersetzt durch `formatGb(free)` / `formatGb(total)`.
  - USB-Zeile: analog, wenn `usb != null`; sonst `Text(S("storage_usb_none"))` gedämpft (c.textSecondary).
- Bestehenden `StatCard`-Aufruf für `storage_free` aus der KPI-Row entfernen (KPI-Row = Projekte + Heute).

### 4) Localization (`LocalizationManager.kt`)
Neue Keys in die **de-** UND **en-**Map (die übrigen 33 Sprachen fallen über den vorhandenen de-Fallback zurück — für Beta ok, später übersetzen):
- `storage_title` = „Speicher" / „Storage"
- `storage_internal` = „Intern" / „Internal"
- `storage_usb` = „USB" / „USB"
- `storage_free_of` = „{free} frei von {total}" / „{free} free of {total}"
- `storage_usb_none` = „Kein USB-Stick" / „No USB drive"
`storage_free` bleibt bestehen (nicht löschen — evtl. anderweitig genutzt).

## Verifikation
- Unit-Tests grün (usedFraction, formatGb, Farbschwellen).
- Am Gerät: Home zeigt die „Speicher"-Karte; Intern-Balken plausibel; USB-Stick einstecken → beim Zurückkehren/Resume erscheint der USB-Balken mit freiem/gesamtem Platz; ohne Stick „Kein USB-Stick". Balkenfarbe wechselt bei hohem Füllstand.

## Nicht anfassen
`UsbExportService` (nur `findUsbVolumes` aufrufen), Export-Logik, andere Home-Bereiche (Projektliste/Pager/CTAs), OSD.
