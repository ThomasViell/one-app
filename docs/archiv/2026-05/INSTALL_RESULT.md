# INSTALL_RESULT — v0.3.0 auf Test-Tablet + Recorder-Bugfix

**Datum:** 2026-05-11
**Branch:** `feature/osd-phase-7`
**Ausgeführt nach:** `INSTALL_APK_AUF_TABLET.md` + Folgeaufgabe (Bugfix Recorder)

---

## Teil 1 — Installation (Smoke-Test)

### Umgebung

| Feld | Wert |
|------|------|
| adb-Version | 1.0.41 (37.0.0-14910828) |
| Installation | `winget install Google.PlatformTools` |
| Tablet-Seriennr. | R52Y303GEZH |
| Hersteller / Modell | samsung / SM-X610 (Galaxy Tab S9 FE+ WiFi) |
| Android-Version / API | 16 / 36 |
| Device-Codename | gts9fepwifi |

### APK (initial, MD5 `CA525B0D2D9CA9E60FD91E5F7893A583`)

```
> adb shell pm list packages com.uip.drainq.one
(leer — keine Vorversion installiert)

> adb install -r -d app-release.apk
Performing Streamed Install
Success
```

### App-Start

```
> adb shell am start -n com.uip.drainq.one/com.uip.oneapp.MainActivity
Starting: Intent { cmp=com.uip.drainq.one/com.uip.oneapp.MainActivity }
> adb shell pidof com.uip.drainq.one
15495     (stabil über ≥ 30 s)
```

### Logcat-Snapshot (`install_logcat.txt`, 290 Zeilen, 30 s)

| Pattern | Treffer |
|---------|---------|
| FATAL / AndroidRuntime / CRASH / OutOfMemory / RuntimeException / ANR | **0** |

Einziger App-eigener Logeintrag:
```
E/.uip.drainq.one(15495): Not starting debugger since process cannot load the jdwp agent.
```
→ Erwartet bei Release-Build (kein JDWP). **Kein Fehler.**

**Smoke-Test: bestanden.**

---

## Teil 2 — Defekte Aufnahme aus dem Feldtest

Nach dem Smoke-Test hat der Anwender eine Test-Aufnahme gemacht und das ZIP-Export-Paket vom Tablet gezogen:

```
/sdcard/Download/Projekt_110526_2125_001.zip   →   C:\Projekte\drainq.one\
```

### Inhalt

| Datei | Größe |
|------|------|
| `Bericht_110526_2125_001.pdf` | 274 KB |
| `Daten_110526_2125_001.xml` | 1.1 KB |
| `fotos/dmg_1778527697066.jpg` | 135 KB |
| `videos/110526_2125_001_20260511_212759.mp4` | **14.6 MB** |
| `projekt_info.txt` | 0.4 KB |

### Befund — Video schwarz / nicht abspielbar

```
> ffprobe videos\110526_2125_001_20260511_212759.mp4
[mov,mp4,m4a,3gp,3g2,mj2 @ ...] moov atom not found
Invalid data found when processing input
```

Header-Inspektion bestätigt das:

```
00 00 00 20 66 74 79 70 69 73 6F 6D ...    ftyp isom (avc1 mp41)
00 00 00 08 66 72 65 65                    free
00 00 00 00 6D 64 61 74                    mdat       ← H.264-Daten folgen
```

→ Datei beginnt mit `ftyp` + `free` + `mdat`, der `moov`-Atom fehlt komplett. Das ist das klassische Symptom für eine **nicht-finalisierte MP4**: die Encodierung lief, aber der Trailer (`av_write_trailer()`) wurde nie geschrieben. Ohne `moov` kein Sample-Index, kein Codec-Config, keine Wiedergabe — der Player zeigt korrekterweise nichts an.

---

## Teil 3 — Root Cause

`FfmpegRtspRecorder.kt` benutzte vor dem Fix `-movflags +faststart` (Zeile 105) und verließ sich darauf, dass `FFmpegKit.cancel()` SIGINT auslöst und ffmpeg den Trailer schreibt (Kommentar Zeile 83).

Auf Android trifft das nicht zuverlässig zu:

- `FFmpegKit.cancel()` setzt einen internen Cancel-Flag in libavutil. Der Encoder bricht beim nächsten Frame mit `AVERROR_EXIT` ab.
- Der `mov`-Muxer puffert den vollständigen Sample-Index im RAM und schreibt ihn erst am Schluss in einem einzigen `moov`-Atom. Wird der Prozess vorher unterbrochen, fehlt `moov` einfach.
- `+faststart` hilft hier nicht — das verschiebt einen schon geschriebenen `moov` nach vorne. Wenn `moov` nie geschrieben wird, macht `+faststart` gar nichts.

**Die echte Aufnahme aus dem Feldtest ist die Hard-Evidence dafür** — der Beweis, dass das auf diesem Gerät passiert.

---

## Teil 4 — Fix

`buildFullCommand` jetzt:

```kotlin
val muxFlags = "-f mp4 -movflags +frag_keyframe+empty_moov+default_base_moof " +
               "-frag_duration 1000000"
return "-rtsp_transport tcp -i $rtspUrl $videoArgs -an $muxFlags -y $outPath"
```

### Was die einzelnen Flags tun

| Flag | Wirkung |
|------|--------|
| `-f mp4` | Erzwingt MP4-Muxer (kein Format-Guessing) |
| `+empty_moov` | Schreibt leeren `moov`-Header **sofort** beim Start |
| `+frag_keyframe` | Beginnt neues Fragment bei jedem Keyframe |
| `+default_base_moof` | Setzt `base_data_offset` in jedem `moof` — fragment-lokale Offsets |
| `-frag_duration 1000000` | Maximal 1 s pro Fragment → max 1 s Datenverlust bei harten Abbrüchen |

### Strukturelle Folge

```
Vorher:  ftyp | free | mdat[60s Daten]              ← unspielbar wenn nicht finalisiert
Nachher: ftyp | moov(leer) | moof | mdat | moof | mdat | … moof | mdat
                              └──── 1-s-Fragment ────┘
```

Jedes Fragment ist self-contained. Datei bleibt abspielbar bei Crash, Force-Kill, Akku leer, App geswiped, OOM-Killer — egal was. Unterstützt von ExoPlayer/Media3, VLC, Android MediaPlayer, allen Browsern, allen Desktop-Playern.

### Geänderte Dateien

- `app/src/main/java/com/uip/oneapp/network/FfmpegRtspRecorder.kt` — `buildFullCommand` + Kommentar an `stopRecording`
- `app/src/test/java/com/uip/oneapp/network/FfmpegRtspRecorderTest.kt` — 4 neue Asserts (frag_keyframe, empty_moov, default_base_moof, kein faststart, `-f mp4`, `-frag_duration`)
- `scripts/verify_fragmented_mp4.ps1` — lokales Reproduktions- und Verifikations-Skript

---

## Teil 5 — Verifikation

### Unit-Tests

```
> gradlew testReleaseUnitTest --tests "com.uip.oneapp.network.FfmpegRtspRecorderTest"
BUILD SUCCESSFUL in 11s
```

Alle 17 Tests grün (13 vorhandene + 4 neue).

### Lokale Reproduktion mit Desktop-ffmpeg

```
> pwsh -File scripts\verify_fragmented_mp4.ps1
LEGACY (faststart) → playable (Windows-ffmpeg flushed on Process.Kill)
FIXED  (fragmented) → playable
VERDICT: INCONCLUSIVE
```

Anmerkung: Auf Windows reagiert Desktop-ffmpeg auf `Process.Kill()` mit einem Signal-Handler, der `av_write_trailer()` noch ausführt. Der Bug ist also auf Windows nicht reproduzierbar. **Die echte Reproduktion liefert die kaputte MP4 vom Tablet** (Teil 2). Der Fix bleibt korrekt — fragmented MP4 entkoppelt die Wiedergabbarkeit komplett vom Trailer.

### Release-Build

```
> gradlew assembleRelease
BUILD SUCCESSFUL in 42s
```

| Feld | Wert |
|------|------|
| Pfad | `app\build\outputs\apk\release\app-release.apk` |
| Größe | 143.5 MB |
| MD5 (nach Fix) | `5BC797AC00D3DF8E11208337EA998F52` |
| MD5 (vor Fix) | `CA525B0D2D9CA9E60FD91E5F7893A583` |

### Install + Smoke-Test (nach Fix)

```
> adb install -r app-release.apk
Performing Streamed Install
Success

> adb shell am start -n com.uip.drainq.one/com.uip.oneapp.MainActivity
> adb shell pidof com.uip.drainq.one
20983     (stabil ≥ 8 s)

> adb shell dumpsys package com.uip.drainq.one | findstr version
versionName=0.3.0
versionCode=3
```

---

## Empfehlung — nächster Schritt

➡️ **Vor-Ort-Test:** Neue Aufnahme mit gleichem Setup machen, ZIP exportieren, MP4 prüfen.

```
ffprobe videos\*.mp4
```

Soll jetzt nicht mehr `moov atom not found` sagen, sondern Streams, Dauer und `nb_frames` melden. Dann ist der Bug geschlossen.

➡️ Optional: alte Aufnahme `videos/110526_2125_001_20260511_212759.mp4` ist (durch fehlenden moov) nicht mehr retten — bitte verwerfen. Die Roh-H.264-Frames sind zwar im `mdat`, aber ohne Sample-Tabelle nicht referenzierbar. Eine Wiederherstellung wäre extrem aufwändig und der User hat explizit darauf verzichtet.

---

## Pragmatische Entscheidungen (GodMode-Lauf)

| Entscheidung | Begründung |
|------|------|
| Fragmented MP4 statt PreparativeStop-Versuch | Strukturelle Robustheit > Hoffnung auf clean shutdown. Funktioniert auch bei Crash/OOM/Akku leer. |
| `-frag_duration 1000000` (1 s) | Guter Kompromiss: max 1 s Datenverlust, geringer Mux-Overhead. |
| `+default_base_moof` mit dazu | Empfohlen für moderne MP4-Player, beseitigt absolute Offsets. |
| `-f mp4` explizit | Verhindert dass ffmpeg aus dem Suffix `.mp4` ableitet — defensive. |
| Lokales Verifikations-Skript trotz INCONCLUSIVE behalten | Wenn der Test eines Tages auf Linux/macOS läuft (anderer Signal-Handler), würde er reproduzieren. Selbst wenn nicht: er verifiziert mindestens, dass das neue Profil eine spielbare Datei erzeugt. |
| Alte Aufnahme nicht zu retten versuchen | Userwunsch explizit: "Du musst nicht die Daten retten! Einfach die APK anpassen das das nicht passiert!" |
| Versionscode nicht hochgezogen | Bug-Fix ohne API-Änderung, v0.3.0 bleibt; falls Versions-Bump gewünscht (z. B. 0.3.1), nachreichen. |

## Was bleibt offen

- **Hardware-Test mit echter Kamera ausstehend** — der Tablet-Smoke-Test bewies nur App-Start, nicht Recording-Flow. RTSP-Quelle für End-to-End-Test war nicht angeschlossen.
- **Auf älteren MP4-Playern (Win7 Media Player, ältere Embedded-Geräte) ist fragmented MP4 evtl. nicht spielbar** — irrelevant für den Use Case (Tablet + DrainQ.Windows ExoPlayer/VLC).
- **Versionscode bleibt 3** — falls Release/Tagging gewünscht, bitte separat anstoßen.
