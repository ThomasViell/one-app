# FIX-RUN W-H4b: Sprachschleife der Synth-Screenshots reparieren + harte Gates

**Modus:** vollautonom, keine Rückfragen. Branch `feature/help-system` weiterführen, KEIN Merge.
**Anlass (Cowork-Gegenprüfung 17.07. — Fakten, nicht verhandelbar):**
1. ALLE 19 EN-Renderings in `docs/manual/screenshots_synth/en/` sind **byte-identisch** mit den
   DE-Renderings (md5 verifiziert, Stichprobe zusätzlich pixel-identisch). Die Sprachumschaltung
   im Render-Pfad war wirkungslos. Der W-H4-Report meldete trotzdem „EN ✓" — das Paritäts-Gate
   hat EN nie real geprüft.
2. `scr07b_inspection_recording` ist hash-identisch mit `scr07_inspection_live` — der
   Aufnahme-Zustand (REC/PAUSE-Chip, laufender Timer) wird im Fake-State nicht dargestellt.
3. `scr02_home_storage_usb` ist identisch mit `scr02_home` (als „Alias" deklariert — jetzt echt
   machen oder streichen, nicht doppelt führen).

## Harte Regeln
Wie W-H4 (kein git add -A, kein Merge, keine Wettbewerbernamen, E6 bindend). Jede Behauptung im
Report nur mit ausgeführtem Beweis-Kommando (Hash/Diff), NIE aus der Erinnerung.

## Phase 1 — Root-Cause Sprachschleife
Nachvollziehen, warum `render.ps1 -Langs de,en` zweimal DE erzeugt: Kommt
`-Pscreenshot.lang=en` in der Test-JVM an (`System.getProperty` im Test loggen)? Wird die Sprache
im Composable-Baum wirksam (LocalizationManager-Aufruf im Render-Kontext — CompositionLocal/
Injection-Kette prüfen)? Oder rendert Gradle wegen UP-TO-DATE/Caching den zweiten Lauf gar nicht
neu (`--rerun-tasks` nötig)? Root-Cause SCHRIFTLICH ins RUN_REPORT, dann fixen.
Verdacht [zu verifizieren]: Test-Task UP-TO-DATE beim zweiten Sprachlauf ODER injectLanguage
greift nicht in den hartkodierten Kotlin-Maps-Pfad für Bundle-Sprachen (en ist Bundle-Sprache,
E5-Fallback-Kette liefert trotzdem de?).

## Phase 2 — Fix + NEUES HARTES GATE „Sprachdifferenz"
In `render.ps1` nach jedem Mehrsprachen-Lauf automatisch: für JEDE Szene md5(de) != md5(en)
— sonst bricht das Skript ROT ab (Ausnahme: Szenen ohne jeden sichtbaren Text, aktuell KEINE).
Dieses Gate bleibt dauerhaft im Skript (wird in W-H5 Teil des Build-Gates).

## Phase 3 — Recording-Zustand + USB-Variante
- `scr07b`: FakeHardwareService/Fake-Recorder-State liefert „Aufnahme läuft" (REC-Chip, Timer
  sichtbar, z. B. 00:42) — exakt der Zustand, den das Geräte-Referenzbild zeigt.
- `scr02_home_storage_usb`: Fake-USB-Volume in den Seed (Balken „USB" gefüllt sichtbar) ODER
  Szene aus scenes.json + help_*.json streichen mit Begründung. Entscheiden, umsetzen.

## Phase 4 — Echtes Paritäts-Gate EN (gegen Geräte-Referenz)
Opus-Sichtprüfung je Szene: synth-EN gegen `docs/manual/screenshots/en/<szene>.png` (echte
Geräte-EN aus W-H2/W-H3): englische Beschriftungen? gleiche Elemente? PASS/FAIL je Szene,
max. 2 Iterationen. Zusätzlich Stichproben-Gegenkontrolle DE analog.

## Phase 5 — PDFs neu + Abschluss
`generate.js` DE+EN neu bauen (EN-PDF jetzt mit ECHTEN EN-Bildern), Gate wie gehabt.
`RUN_REPORT_HELP_W4B_<datum>.md`: Root-Cause, Beweis-Hashes VOR/NACH (Auszug), Gate-Ausgabe,
Paritäts-Verdicts, Offen-Liste (P1 dlg_pdf_preview-Paginierung darf offen bleiben).
Commits gezielt, push. STOPP.
