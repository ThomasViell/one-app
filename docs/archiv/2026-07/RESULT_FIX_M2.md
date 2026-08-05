# RESULT — FIX M2: Kameratyp-Vorbelegung bei Schnellaufnahme

**Branch:** feature/dual-mode  
**Datum:** 2026-07-12  
**Befund-Quelle:** Geräteabnahme 11.07.2026 (0.5.6 Beta)

---

## Umgesetzte Änderungen

### 1. `ui/screens/inspection/InspectionScreen.kt`

**Imports ergänzt** (nach `kotlinx.coroutines.launch`):
```kotlin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
```

**Neuer `LaunchedEffect(effectiveProjectId)`** nach dem bestehenden Bucket-Anlege-Effekt eingefügt:
```kotlin
LaunchedEffect(effectiveProjectId) {
    val pid = effectiveProjectId ?: return@LaunchedEffect
    hardwareService.hardwareState
        .map { CameraHead.from(it.cableController.cameraId) }
        .distinctUntilChanged()
        .collect { head ->
            val proj = projectRepository.getProject(pid) ?: return@collect
            cameraTypePrefill(head, proj.kameratyp, cameraC10Label, cameraC18Label)
                ?.let { projectRepository.updateProject(proj.copy(kameratyp = it)) }
        }
}
```

**Mechanismus:** Der Effekt beobachtet `hardwareState` als Flow. Sobald sich der erkannte Kopf
ändert (z. B. von UNKNOWN → C18), wird das Projekt geladen. `cameraTypePrefill` gibt `null`
zurück, wenn `kameratyp` bereits belegt ist — verhindert so jeden Override. Nur bei leerem
Feld wird `updateProject` aufgerufen. Der Effekt startet neu, wenn `effectiveProjectId` sich
ändert (d. h. nach dem Anlegen des Quick-Buckets).

### 2. `data/repository/ProjectRepository.kt`

Keine Änderung nötig — `getProject(id)` und `updateProject()` existierten bereits.

### 3. `data/repository/CapturePersistenceTest.kt`

Zwei neue Tests ergänzt:

| Test | Szenario | Ergebnis |
|------|----------|---------|
| `backfill_setzeKameratypWennFeldLeer` | Bucket mit leerem `kameratyp`, Backfill mit C18 | `kameratyp = "C18"` ✓ |
| `backfill_ueberschreibtNichtWennFeldBelegt` | Bucket mit `kameratyp = "C10"`, Backfill-Versuch C18 | `kameratyp = "C10"` bleibt ✓ |

---

## Test-Ergebnis

```
Tests gesamt: 382
Fehlgeschlagen: 0
Fehler: 0
```

Alle bestehenden Tests unverändert grün. Beide neuen Backfill-Tests bestanden.

---

## Build-Ergebnis

```
BUILD SUCCESSFUL
APK: app/build/outputs/apk/debug/app-debug.apk
Größe: 155.7 MB
Erstellt: 2026-07-12 12:12
```

---

## Nicht angefasst (laut Auftrag)

- `cameraTypePrefill.kt` — unverändert
- `ProjectExportService.kt` — unverändert
- M3-L10n (`quick_capture_bucket`) — unverändert
- M4 (Inter-Font) — unverändert

---

## Geräte-Abnahme (noch offen)

Pflicht vor Freigabe — Unit-Tests beweisen das Timing-Verhalten nicht:

1. Kamera verbunden + erkannt → neue Schnellaufnahme → Bearbeiten-Formular zeigt **C18**, PDF-Zeile zeigt **C18**
2. Schnellaufnahme öffnen BEVOR Kopf erkannt → Kamera verbinden → `kameratyp` trägt sich auf **C18** nach
3. Manuellen Override (z. B. C10) setzen → wird durch den Effekt **nicht** überschrieben
4. Alten Tages-Bucket „Schnellaufnahme_110726" auf Test-ONE löschen für sauberen Frisch-Test
