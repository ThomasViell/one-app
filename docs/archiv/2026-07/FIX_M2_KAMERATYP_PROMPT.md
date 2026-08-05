# Reparaturauftrag — M2: Kameratyp bleibt bei Schnellaufnahme leer

Quelle: Geräteabnahme 11.07.2026 (0.5.6 Beta, Thomas-ONE). B2/M3/M4/B1 OK, **M2 fehlgeschlagen**.
Branch: `feature/dual-mode`. Kein Merge, kein Tag.
Empfohlenes Modell: **Sonnet / mittel** (kleiner, lokaler Fix; Planung/Diagnose bereits erledigt).

---

## Befund (am Gerät gemessen)

Frische Schnellaufnahme mit verbundener Kamera: OSD-Chip zeigt korrekt **C18**, aber `kameratyp`
bleibt **leer** — im Bearbeiten-Formular UND im PDF. Dropdown enthält C10/C18, manuelle Auswahl +
PDF-Render funktionieren. Der Fehler sitzt allein darin, dass die Auto-Vorbelegung nicht greift.

## Ursache [Sicher — Code verifiziert]

**Timing-Race + Idempotenz-Einfrieren.**

`ui/screens/inspection/InspectionScreen.kt`, LaunchedEffect ~Z.128–134:
```kotlin
effectiveProjectId = projectId ?: run {
    val head = CameraHead.from(hardwareService.hardwareState.value.cableController.cameraId)
    val camLabel = cameraTypePrefill(head, "", cameraC10Label, cameraC18Label) ?: ""
    projectRepository.getOrCreateQuickProjectId(quickBucketLabel, camLabel)
}
```
Der Kamerakopf wird hier **einmalig, synchron** bei der ersten Komposition gelesen. Die
Kopf-Erkennung stammt aber aus der **debounced seriellen GROUP_CAMERA-Telemetrie** (siehe OSD-Chip
`InspectionScreen.kt` ~Z.917, der `cable.cameraId` LIVE liest). Zum Zeitpunkt des Bucket-Anlegens
ist `cameraId` daher fast immer noch **UNKNOWN** → `cameraTypePrefill` gibt `null` → `camLabel = ""`.

`data/repository/ProjectRepository.kt` `getOrCreateQuickProjectId` (~Z.45–60) ist idempotent:
```kotlin
dao.getByProjectNumber(number)?.let { return it.id }   // bestehender Tages-Bucket → unverändert
```
Der leer angelegte Tages-Bucket wird danach **nie korrigiert**, auch nicht, wenn C18 Sekunden
später eintrifft. Der OSD-Chip zeigt C18 (live), das Feld bleibt leer (eingefroren beim Anlegen).

Beleg: `cameraTypePrefill(head, "", …)` liefert nur dann `""`, wenn `head == UNKNOWN`. Da der Chip
danach C18 zeigt, war der Kopf beim Anlegen nachweislich noch nicht da → Race bestätigt.

---

## Fix [eindeutig]

Kopf **nicht** einmalig beim Anlegen lesen, sondern beobachten und `kameratyp` **nachtragen**,
sobald er sich auf C10/C18 auflöst und das Feld noch leer ist. Nie einen gesetzten Wert
überschreiben (`cameraTypePrefill` garantiert das bereits über `currentValue.isNotBlank()`).

**Datei:** `ui/screens/inspection/InspectionScreen.kt` — nach dem Setzen von `effectiveProjectId`
folgenden Effekt ergänzen (bestehendes `cameraTypePrefill` + `cameraC10Label/cameraC18Label`
wiederverwenden):
```kotlin
// M2-Nachbesserung: Kopf kommt per debounced Telemetrie erst NACH der ersten Komposition.
// Beobachten und kameratyp nachtragen, sobald C10/C18 vorliegt und das Feld leer ist.
// cameraTypePrefill liefert null, wenn currentValue nicht leer ist → kein Override.
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
- Benötigt ein suspend `getProject(id): ProjectEntity?` im Repository (falls nicht vorhanden, dünn
  ergänzen; `updateProject` existiert bereits).
- Gilt auch, wenn die Kamera erst NACH dem Öffnen erkannt wird — dann trägt der Effekt nach.
- Kein Gate auf Status nötig: `cameraTypePrefill` überschreibt nie ein befülltes Feld; ein leeres
  Feld mit erkanntem Kopf zu füllen ist gewünscht (reiner Default). Optional auf
  `status == "QUICK"` einschränken, wenn nur der Schnellaufnahme-Pfad betroffen sein soll.

**Optional/sekundär:** `getOrCreateQuickProjectId` beim Return-Pfad backfillen, wenn der bestehende
Bucket `kameratyp.isBlank()` hat und ein nicht-leeres Label übergeben wird. Nicht zwingend, da der
Effekt oben das abdeckt.

---

## Vor dem Nachtest

Der bereits leer angelegte Tages-Bucket **"Schnellaufnahme_110726"** auf der Test-ONE löschen
(sonst greift auf demselben Gerät weiter die Idempotenz auf den Alt-Eintrag — der Backfill heilt ihn
zwar beim nächsten Öffnen, aber für einen sauberen "frisch angelegt"-Test vorher löschen).

## Verifikation (Pflicht am Gerät — Unit-Test beweist das Timing nicht)

1. Kamera verbunden + erkannt → neue Schnellaufnahme → Bearbeiten-Formular zeigt **C18**, PDF-Zeile
   „Kameratyp" zeigt **C18**.
2. Schnellaufnahme öffnen, BEVOR der Kopf erkannt ist, Kamera dann verbinden → `kameratyp` trägt
   sich auf C18 nach.
3. Manuellen Override im Formular (z. B. C10) setzen → wird durch den Effekt **nicht** überschrieben.

## Unit-Test (ergänzend)

- Repository: Backfill setzt `kameratyp` nur, wenn vorher leer; überschreibt gesetzten Wert nicht.
- `cameraTypePrefill` ist bereits getestet (reine Funktion) — unverändert lassen.

---

## Nicht anfassen
`cameraTypePrefill.kt` (korrekt), `ProjectExportService.kt` Z.176 (PDF-Ausgabe korrekt),
M3-L10n (`quick_capture_bucket`, verifiziert OK), M4 (Inter-Font, verifiziert OK).
