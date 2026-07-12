# Auftrag: Welle 5 — Aufnahmeweg umbauen (25 fps, Echtzeit, ruckelfrei)

**Branch:** `feature/dual-mode` (weiterarbeiten, KEIN Merge, kein Tag)
**Build:** OHNE Keystore — `cd C:\Projekte\drainq.one; .\gradlew.bat assembleDebug test`. NIE `assembleRelease`/`KEYSTORE_`-Env.
**Commit-Präfix:** `feat(w5-video): …` — nach jedem Schritt Build + Tests grün, dann committen.
**Git-Regel:** Zustand NIE über Sandbox-bash beurteilen (CRLF-Phantom-Diffs) — Host-Read/Grep ist Schiedsrichter.
**Modell/Effort:** Opus/hoch (Encoder-/Zeitbasis-Kern). Pre-Merge-Review durch ein **nicht-bauendes** Modell/hoch.
**Zuerst:** Skill `drainq-kritis-compliance` konsultieren.

CEO-Entscheid 09.07.: Umbau **vor** Louis' Abnahme, damit er alles in einem Durchgang testet.

## Ziel (Abnahme durch den CEO, nicht durch Zahlen im Log)

1. **Ruckelfrei:** 25 Bilder/s in HD, stabil über eine ≥ 5-Minuten-Aufnahme.
2. **Echtzeit:** Videodauer == echte Aufnahmedauer (ohne Pausen). Kein Zeitraffer, keine Zeitlupe.
3. **Werte im Bild:** OSD (Meter, Datum, Projekt, Befund-Flash) weiterhin eingebrannt und korrekt.
4. **Station stimmt:** Foto/Schaden aus dem fertigen Video trägt exakt den im Bild eingebrannten Meterwert (Welle 4b-Kriterium bleibt).
5. **Absturzsicher:** Wird die App während der Aufnahme getötet, bleibt eine abspielbare Datei zurück (Eigenschaft aus Welle 2 darf NICHT verloren gehen).

## Schritt 0 — Messen, bevor etwas geändert wird (Pflicht, Ergebnis in den Bericht)

Auf der ONE mit dem heutigen Stand: exakt **60 s** HD aufnehmen (OSD an, Fahrwagen bewegt), stoppen, Videolänge ablesen.
`reale_fps ≈ 12 × (Videolänge / 60 s)`. Dieser Wert ist die **Baseline**. Ohne ihn ist „besser" nicht belegbar.

## Ursache (bekannt, code-belegt)

`LocalBitmapRecorder.kt`: Jedes Bild wird auf der CPU kopiert (`Bitmap.copy`), das OSD hineingerendert, als **JPEG komprimiert** (Q85), durch eine **FIFO** geschickt — und ffmpeg (`-f image2pipe -framerate 12`, Z. 123) **dekodiert das JPEG wieder** und kodiert es als H.264 neu. Fünf Schritte pro Bild in Software.

Zusätzlich vergibt ffmpeg die Zeitstempel **nach Frame-Index** (`Medienzeit = index / 12`), während die Schleife langsamer als 12 fps liefert (sie wartet `1000/f` ms **und** arbeitet danach, Z. 137/184). Daraus folgt: Video ist kürzer als die Wirklichkeit → **Zeitraffer**. Die Bildrate `12` steht hartkodiert an drei Stellen (Z. 88, 1501, 1549 InspectionScreen) ohne Begründung.

## Umbau

**Hardware-Encoder statt JPEG-Umweg.** Der RK3588 hat einen H.264-Encoder; nutze `MediaCodec` mit **Input-Surface** und rendere die Kamerabilder + OSD per GPU darauf (Bild als Textur, OSD als zweite Ebene). Kein JPEG, keine FIFO, keine Neukodierung. Damit sind 25–30 fps in HD erreichbar.

**Echte Zeitstempel.** `presentationTimeUs` je Bild aus einer monotonen Uhr (`System.nanoTime()`), **abzüglich der Pausenzeit** — das erhält das heutige Verhalten „Pause fehlt im Video, Datei läuft nahtlos weiter". Damit ist die Medienzeit **per Konstruktion** gleich der (pausenbereinigten) Echtzeit: Echtzeit-Wiedergabe unabhängig davon, wie viele Bilder das Gerät real schafft. Variable Bildrate ist erlaubt und erwünscht.

**Meter-Spur v3.** Weil die Zeitbasis jetzt eine echte Uhr ist (nicht mehr der Frame-Index), wird die Spur auf **dieselbe Zeitachse** gestempelt, die auch `presentationTimeUs` speist: `{"v":3}` Kopf, dann `{"tUs":<pts>,"m":<meter>}`. Nachschlagen bei Wiedergabe über `exoPlayer.currentPosition` (ms → µs), lineare Interpolation.
**v1 und v2 werden nicht gelesen** → `MeterTrack.EMPTY` → Stufe-1-Fallback (leeres Pflichtfeld, nie 0.00). Die v2-Logik bleibt im Code, solange der alte Recorder existiert.

**Absturzsicherheit.** `MediaMuxer` schreibt den Index erst beim Stopp — ein Kill während der Aufnahme würde die Datei verlieren. Das ist ein **Rückschritt gegenüber Welle 2 und nicht akzeptabel**. Wähle und begründe einen Weg, der die Eigenschaft erhält (Kandidaten: Encoder-Ausgabe als Elementarstrom mitschreiben und beim Stopp verlustfrei muxen; oder segmentiert schreiben und beim Stopp zusammenfügen). Die Wahl gehört in eine kurze ADR **vor** dem Coden.

**Feature-Flag (zwingend).** Neuer Weg hinter `FeatureFlags.useHardwareRecorder` (Default: **an** für den Testbuild). Der bestehende `LocalBitmapRecorder` bleibt vollständig erhalten und in einem Schalter erreichbar. Bricht am Objekt etwas, schaltet der Anwender zurück und arbeitet weiter. Nichts löschen.

**Bildrate an EINER Stelle.** Die hartkodierte `12` an den drei Fundstellen durch eine einzige Konstante ersetzen (Ziel 25). Der tatsächlich erreichte Wert wird gemessen und protokolliert, nicht behauptet.

## Reihenfolge

1. Schritt 0 messen, Baseline notieren.
2. ADR: Encoder-Weg + Absturzsicherheit (kurz, entscheidungsfähig).
3. Encoder-Pfad hinter Flag, OSD als Ebene, echte Zeitstempel.
4. Meter-Spur v3 + Reader-Weiche (v1/v2 → EMPTY).
5. Geräte-Abnahme (unten). Erst danach Freigabe an Louis.

## Geräte-Abnahme (bevor Louis den Build bekommt)

- [ ] **60-s-Test:** Videolänge == 60 s (± 1 s). Kein Zeitraffer.
- [ ] **≥ 5-Minuten-HD-Aufnahme mit einer Pause**, Fahrwagen bewegt: Länge == echte Aufnahmedauer minus Pause.
- [ ] Bildrate: gemessen ≥ 24 fps im Mittel; sichtbar ruckelfrei.
- [ ] OSD im Bild: Meter, Datum, Projekt korrekt; Befund-Flash erscheint.
- [ ] Foto/Schaden **aus dem fertigen Video** an drei Stellen (Beginn, nach der Pause, kurz vor Ende): Station == eingebrannter Meterwert.
- [ ] Gleiches in **SD**.
- [ ] **App während der Aufnahme killen** → zurückbleibende Datei ist abspielbar.
- [ ] **Flag aus** → alter Weg funktioniert unverändert (Rückfallebene bewiesen).
- [ ] Alte Aufnahmen (v1/v2-Spur) → leeres Pflichtfeld, keine stille 0.00.

## Leitplanken

- Bedienkonzept unverändert. Kein neuer Netz-/Logging-Datenfluss, keine neue Permission.
- Welle 2 (Remux/Absturzsicherheit), Welle 3 (Kamera-Vorbelegung, HD-Eingabe) und Welle 4/4b (Station) dürfen **nicht** regressieren — deren Tests bleiben grün.
- **KRITIS:** rein lokale Verarbeitung. Der Umbau betrifft die Integrität der Aufnahme (Beweismittel für den Bericht) — Absturzsicherheit und Zeittreue als Datenqualitäts-Punkte im RESULT dokumentieren.
- Adversariale Selbst-Review vor dem letzten Commit: Encoder-Stall, Surface-Lifecycle, Pause/Resume-PTS, Kill während Aufnahme, Spur ohne Video, Video ohne Spur, Flag-Umschaltung zur Laufzeit.

## Bericht

`RESULT_W5_VIDEOWEG.md` (Repo-Root): Baseline aus Schritt 0, erreichte fps, gemessene Videodauer vs. Echtzeit, ADR-Verweis, Testausgabe, Geräte-Checkliste, Rückfallebene bestätigt.
