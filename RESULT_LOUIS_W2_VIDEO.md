# Ergebnis: Louis-Feldfeedback 06.07. — Welle 2 (Video)

**Branch:** `feature/dual-mode` (kein Merge, kein Tag)
**Behebt:** Louis **#9a** (nach Pause bricht die Wiedergabe nach ~2 s ab) und **#5b** (kein laufender Timer, nur Endzeit).
**Status:** Code + Unit-Tests fertig, lokal per ffmpeg/ffprobe belegt. **Geräte-Abnahme (beide Modi) steht aus** — Checkliste unten.

---

## Ursache (im Code + lokal verifiziert)

Beide Recorder schrieben die Aufnahme live als **fragmentiertes MP4**
(`-movflags +frag_keyframe+empty_moov+default_base_moof`) — gewählt für **Absturzsicherheit**
(bleibt bei Crash/Kill/Akku-Aus spielbar). `empty_moov` bedeutet aber: der Datei-**Header (`moov`/`mvhd`) trägt keine Gesamtdauer** (`duration == 0`). Die eigentliche Zeitachse steckt in den `moof`-Fragmenten.

Folgen für Player, die die Länge aus dem Header lesen (Androids MediaPlayer/ExoPlayer):
- Länge unbekannt → nur **Endzeit**, kein mitlaufender Timer/keine seekbare Leiste (**#5b**).
- Nach einer **Pause** (Zeitstempel-Sprung über die Pausenlücke) **Abbruch nach ~2 s** (**#9a**).

Die Bytes waren immer vollständig da — es ist ein reines **Container-/Dauer-Problem**, keine abgeschnittene Datei.

---

## Lösung: Remux beim sauberen Stopp

Live weiterhin fragmentiert aufnehmen (absturzsicher). Beim **gewollten Stopp** die Datei **einmal
verlustfrei** in ein normales MP4 mit korrektem `moov` umbauen — kein Re-Encode:

```
-i <frag> -c copy -movflags +faststart -y <final>
```

`-c copy` = Stream-Copy → Sekundenbruchteile, keine Qualitäts-/Auflösungs-/OSD-Änderung
(OSD ist im Capture-Pass bereits eingebrannt).

### Umgesetzt

| Schritt | Datei | Inhalt |
|--------|-------|--------|
| 1 | `network/RecorderRemux.kt` *(neu)* | `remuxToFaststart` (eigene FFmpegKit-Session, nie globaler `cancel`), `finalizeFragRecording` (Remux + Fallback, injizierbarer Delegate → ohne echtes FFmpeg testbar), `cleanupOrphanFrags`, `FRAG_SUFFIX`. |
| 2 | `network/LocalBitmapRecorder.kt` | Lokal/V4L2-Pfad (Louis' Aufnahmen): Capture → `<out>.frag.mp4`; `stop()` remuxt nach `<out>`; fragilen Rückgabepfad (`command.substringAfterLast`) durch gemerktes `finalPath` ersetzt; verwaiste Frags beim Start aufgeräumt. |
| 3 | `network/FfmpegRtspRecorder.kt` | RTSP-Pfad (ONE.PRO/Remote): Capture → `<out>.frag.mp4`; Completion-Callback remuxt bei `rc ∈ {0,255}` + nicht-leerer Frag nach `<out>`. |
| — | `ProjectDetailViewModel`, `UsbExportService`, `ProjectExportService` | `*.frag.mp4` (absturzsichere Zwischenstände) aus Videoliste + beiden Export-Pfaden gefiltert. |

**Fallback (Aufnahme nie verlieren):** Schlägt der Remux fehl, wird die Frag-Datei verlustfrei
**zur** finalen Datei (umbenannt/kopiert) und dieser Pfad zurückgegeben. Bei echtem Crash (kein
Callback) bleibt die Frag-Datei spielbar und wird beim nächsten Start aufgeräumt.

---

## Lokaler Beweis (ffmpeg 8.1.1)

`scripts/verify_remux_faststart.ps1` nimmt mit den **exakten Recorder-Flags** eine 8-s-Testquelle
fragmentiert auf, remuxt mit dem **exakten Produktions-Kommando** und liest die
`mvhd.duration` direkt aus dem `moov`-Atom (genau der Wert, den schwache Player lesen):

```
LIVE (fragmentiert, empty_moov):
  Top-Level-Boxen : ftyp moov moof mdat moof mdat … mfra
  mvhd.duration   : 0,000 s   ← Header-Dauer = 0  → reproduziert #5b/#9a
  ffprobe scan    : 8.000000 s ← ffprobe scannt Fragmente, versteckt den Header-Bug

GESTOPPT (remuxt, +faststart):
  Top-Level-Boxen : ftyp moov free mdat   ← moov VOR mdat (faststart, seekbar)
  mvhd.duration   : 8,000 s   ← korrekte Gesamtdauer im Header
  ffprobe scan    : 8.000000 s
VERDICT: PASS
```

> Wichtig: **ffprobe auf dem Desktop meldet für beide Dateien 8 s**, weil es die Fragmente
> voll scannt. Genau deshalb blieb der Bug in Desktop-Tools unsichtbar — er trifft nur
> Player, die die Dauer aus dem Header lesen. Der Header-Wert (`mvhd.duration`) zeigt den
> Unterschied eindeutig: **0 s → 8 s**.

---

## Tests

- **Neu** `RecorderRemuxTest` (10 Tests, Remux als Fake-Delegate — ohne echtes FFmpeg):
  Erfolg → finaler Pfad + Frag gelöscht · Fehlschlag/Exception/„lügender" Delegate → Fallback-Pfad,
  Aufnahme erhalten · fehlende/leere Frag → `null` · Orphan-Cleanup löscht nur `*.frag.mp4`.
- Bestehende `LocalBitmapRecorderStateTest` / `FfmpegRtspRecorderTest` / `RecorderFormatAndOsdDecisionTest` unverändert grün.
- `.\gradlew assembleDebug test` grün (Debug + Release Unit-Tests).

---

## Geräte-Checkliste (bitte am ONE abnehmen — BEIDE Modi: lokal V4L2 **und** RTSP)

Pro Modus (Lokal-Aufnahme ohne RTSP / RTSP-Aufnahme mit und ohne Overlay):

1. **Aufnahme ohne Pause** → in der Wiedergabe **läuft der Timer mit**, korrekte Gesamtlänge, Leiste seekbar.
2. **Aufnahme mit Pause/Resume** → **volles Video**, korrekte Dauer, **kein Abbruch nach ~2 s** (Kern von #9a).
3. Fertige Datei mit `ffprobe` prüfen: `moov` vorhanden, **`duration > 0`** (bzw. `mvhd.duration > 0`),
   `moov` vor `mdat`. *(z. B. `adb pull` + `ffprobe -show_format`, oder Box-Reihenfolge wie im Skript.)*
4. **App-Kill während der Aufnahme** → die verbleibende `*.frag.mp4` ist **noch spielbar**
   (Absturzsicherheit); sie taucht **nicht** in der Videoliste auf und wird beim nächsten Aufnahme-Start entfernt.

**Erwartete Nebenwirkung:** Verlässt man die Inspektions-Ansicht **ohne** Stopp mitten in einer
**Lokal**-Aufnahme, wird abgebrochen (`cancel`) → es bleibt nur die absturzsichere `*.frag.mp4`
(nicht gelistet). Der normale Weg (Stopp-Button) remuxt und listet die fertige Datei.

---

## Selbst-Review (vor dem letzten Commit)

- **Race `stop` vs. `cancel`:** `stop` liest `frag/final` in lokale Variablen **vor** `cleanup()`;
  `cancel` setzt nur `IDLE`+`cleanup` und lässt die Frag-Datei liegen. Kein Datenverlust, kein Crash.
- **Frag-Leak:** Erfolg löscht Frag; Fehlschlag benennt Frag → final um; Crash/Cancel-Reste räumt
  `cleanupOrphanFrags` beim nächsten Start; Liste/Exporte filtern `*.frag.mp4`.
- **Doppelte Session:** Start-Guards verhindern eine zweite Capture-Session; der Remux nutzt eine
  **eigene** Session; `FFmpegKit.cancel()` wird **nie ohne Id** aufgerufen (Export-Encodes unberührt).
- **Rückgabepfad in allen Zweigen:** immer der gemerkte `finalPath` (Erfolg wie Fallback);
  `null` nur, wenn nichts (Sinnvolles) aufgenommen wurde.
- **KRITIS:** rein lokale Datei-Verarbeitung im app-privaten `recordings`-Verzeichnis; keine neue
  Permission, kein neuer Netz-/Logging-Datenfluss.

---

## Nicht Teil dieser Welle

- Louis **#8** (Durchmesser/Länge) — erst Geräte-Repro.
- Louis **#9b** (USB) — Handbuch/Bedienung, `UsbExportService` existiert.
- W1-Geräteabnahme (5 Quick Wins) — läuft parallel beim nächsten ONE-Test.
