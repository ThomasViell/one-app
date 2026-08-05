# Auftrag: Louis-Feldfeedback 06.07. — Welle 2 (Video nach Pause / nur Endzeit)

**Branch:** `feature/dual-mode` (weiterarbeiten, KEIN Merge, kein Tag)
**Behebt:** Louis #9a (nach Pause bricht die Wiedergabe nach ~2 s ab) **und** #5b (kein laufender Timer, nur Endzeit).
**Commit-Präfix:** `fix(louis-w2): …` — nach jedem Schritt `.\gradlew assembleDebug test` grün, dann committen.

## Ursache (im Code verifiziert 07.07.)

Beide Recorder schreiben ein **fragmentiertes MP4** (`-movflags +frag_keyframe+empty_moov+default_base_moof`):
- `network/LocalBitmapRecorder.kt` (Z. 100) — Lokal/V4L2-Pfad der ONE (Louis' Aufnahmen).
- `network/FfmpegRtspRecorder.kt` (Z. ~171) — RTSP-Pfad (ONE.PRO/Remote).

fMP4 mit `empty_moov` hat **keine Gesamtdauer im Dateikopf**. Folge: Player kennt die Länge nicht → zeigt nur eine Endzeit (#5b); nach einer Pause (Zeitstempel-Sprung über die Pausenlücke) bricht die Wiedergabe früh ab (#9a). Die Bytes sind vollständig da — es ist ein Container-/Dauer-Problem, keine abgeschnittene Datei. Das fragmentierte Format wurde für **Absturzsicherheit** gewählt (bleibt bei Crash/Kill spielbar) — das bleibt erhalten.

## Lösung: Remux beim sauberen Stopp

Live weiter fragmentiert aufnehmen (absturzsicher), aber beim **gewollten Stopp** die Datei einmal verlustfrei in ein normales MP4 mit korrektem `moov` umbauen. Kein Re-Encode → Sekundenbruchteile, keine Qualitätsänderung.

### Schritt 1 — Gemeinsamer Helfer `remuxToFaststart`

Neu (z. B. `network/RecorderRemux.kt`), von beiden Recordern nutzbar:
```
suspend fun remuxToFaststart(srcFrag: File, dstFinal: File): Boolean
```
- FFmpegKit-Aufruf mit **eigener Session-Id**: `-i <srcFrag> -c copy -movflags +faststart -y <dstFinal>`.
- `-c copy` = verlustfrei/schnell, kein Neu-Encoding.
- Rückgabe `true` nur bei rc==0 **und** `dstFinal` existiert und `> 0` Bytes.
- `FFmpegKit.cancel` nie global — nur die eigene Session.

### Schritt 2 — `LocalBitmapRecorder`: Temp-Frag + Remux beim Stopp

- `start(outputPath, …)`: Capture-FFmpeg NICHT direkt nach `outputPath`, sondern nach einer **Temp-Frag-Datei** (z. B. `<outputPath>.frag.mp4` oder in `cacheDir`). `outputPath` in einem Feld `finalPath` merken.
- `stop(onDone)`: nach `writeJob.join()` + FFmpeg-Finalisierung des Capture → `remuxToFaststart(fragFile, finalFile)`.
  - Erfolg → `onDone(finalFile.absolutePath)`, Frag-Datei löschen.
  - **Fehlschlag → Fallback:** Frag nach `finalFile` umbenennen/kopieren und **diesen** Pfad zurückgeben (Aufnahme nie verlieren), `Log.w`.
- Den fragilen Rückgabepfad aus `s?.command?.substringAfterLast(" ")` durch das gemerkte `finalPath` ersetzen.
- `cancel()`/Crash: Frag-Datei bleibt spielbar. Beim nächsten `start` verwaiste `*.frag.mp4` aus dem Zielordner/Cache aufräumen.

### Schritt 3 — `FfmpegRtspRecorder`: Remux im Completion-Callback

- Analog: Capture nach **Temp-Frag**, `outputFile` merken.
- `stopRecording()` cancelt die Session (rc=255 = gewollter Stopp laut Bestandskommentar). Im **Completion-Callback**, wenn rc ∈ {0, 255} und die Frag-Datei existiert und `> 0`: `remuxToFaststart(frag, outputFile)`.
  - Erfolg → State/Callback mit finalem Pfad, Frag löschen.
  - Fehlschlag → Frag als Fallback behalten (umbenennen auf `outputFile`).
- Echter Crash (kein Callback) → Frag bleibt spielbar (Absturzsicherheit unverändert).

## Leitplanken

- **Pause-Logik unverändert** — Ziel bleibt eine durchgehende Datei; nur der Container wird beim Stopp sauber geschlossen.
- **Absturzsicherheit erhalten:** live immer fragmentiert; Remux ausschließlich beim gewollten Stopp; bei Remux-Fehler ist die Frag-Datei das Ergebnis.
- Kein Re-Encode (`-c copy`), keine Auflösungs-/OSD-Änderung (OSD-Einbrennung bleibt im Capture-Pass).
- Eigene Session-Ids; `FFmpegKit.cancel()` nie ohne Id (Export-Encodes nicht treffen).
- Direkt-Modus/Hardware sonst unberührt; nur die Aufnahme-Pipeline.
- **Tests:** Remux-Zustandslogik testbar machen, ohne echtes FFmpeg — den Remux-Aufruf als **injizierbaren Delegate** (Default = echter `remuxToFaststart`) kapseln, dann in Tests Erfolg/Fehlschlag faken: Erfolg → finaler Pfad + Frag gelöscht; Fehlschlag → Fallback-Pfad zurückgegeben, Aufnahme erhalten. Bestehende Recorder-Tests grün halten.
- **KRITIS:** rein lokale Datei-Verarbeitung, Temp-Frag in app-privatem cache/files-Verzeichnis; kein neuer Netz-/Logging-Datenfluss, keine neue Permission.
- **Abschlussbericht** `RESULT_LOUIS_W2_VIDEO.md` (Repo-Root) mit **Geräte-Checkliste** (beide Modi — lokal V4L2 + RTSP):
  1. Aufnahme ohne Pause → laufender Timer, korrekte Länge, seekbar.
  2. Aufnahme mit Pause/Resume → volles Video, korrekte Dauer, kein Abbruch nach ~2 s.
  3. `ffprobe` auf der fertigen Datei zeigt `moov` mit `duration > 0`.
  4. App-Kill während Aufnahme → verbleibende Frag-Datei noch spielbar (Absturzsicherheit).
- Adversariale Selbst-Review vor dem letzten Commit: Race `stop` vs. `cancel`, Frag-Leak, doppelte Session, korrekter Rückgabepfad in allen Zweigen.

## Nicht Teil dieser Welle

- Louis #8 (Durchmesser/Länge) — erst Geräte-Repro.
- Louis #9b (USB) — Handbuch/Bedienung, `UsbExportService` existiert.
- W1-Geräteabnahme (5 Quick Wins) — läuft parallel beim nächsten ONE-Test.
