# Welle 1 — Inspektion + OSD (Claude Code Prompt)

Modell **Opus, Effort High**. Branch `feature/sa-design-rollout` (weiter, nicht neu). Vorgabe: `docs/design/drainq-one_SA-Design_Umsetzung_2026-06-04.md` (Abschnitte 1–7), Mockup `docs/design/drainq-one_02_inspektion_SA-design.svg`.

```
Setze Welle 1 (Inspektion + OSD) auf feature/sa-design-rollout um. Nutze die Dq-Komponenten + Tokens aus Welle 0. Hardware-/recording-nah → sorgfältig, seriell.

1) ui/screens/inspection/InspectionScreen.kt → Cinema/Vollbild wie Mockup 02:
   - Vollbild-Video, Back oben links, Live-Status-Chips oben rechts (Licht %, Sonde kHz, Meter, REC) als DqStatusChip auf OsdBg.
   - Untere Softbutton-Leiste 112 dp mit großen Touch-Buttons: Foto, Schaden, Aufnahme (Amber, mittig), Sonde, Licht −/+, Meter 0.
   - WICHTIG: bestehende Logik unangetastet lassen — gemeinsame Aktionsliste, Hardtasten F1-F8 (hardware/HardwareKeyBus.kt) spiegeln weiter die Leiste. Nur Optik/Maße auf Dq-Komponenten + Tokens umstellen. Serielle Schicht und app/src/main/cpp/v4l2bridge.c NICHT anfassen.

2) Live-OSD: ui/components/OsdOverlay.kt, ui/components/InspectionOsd.kt, ui/components/FfmpegVideoPlayer.kt (Canvas-Overlay) → Token-Design (OsdBg, TextPrimary, Station/Meter in Amber), Inter.

3) OSD-EINBRENNUNG (aufgenommenes Video) NEU gestalten — export/OsdRenderer.kt + export/VideoOverlayProcessor.kt, genutzt von network/FfmpegRtspRecorder.kt und network/LocalBitmapRecorder.kt:
   - translucenter Balken OsdBg, weißer Text, Station/Meter in Amber, INTER-Schrift (Typeface aus res/font/inter_*.ttf für Canvas bzw. fontfile-Pfad für FFmpeg drawtext bereitstellen).
   - Feldsemantik (Projekt, Station, Datum) und Positionen so lassen, dass PDF/Bericht-Overlay (export/ProjectExportService.kt) weiter korrekt ist. Burn-in-Feldreihenfolge nicht ändern.

4) Dialoge: ui/screens/inspection/DamageDialog.kt, NoteDialog.kt, ImageAnnotationDialog.kt → DqCard-Dialoge, Inputs 56 dp, DqButton, Code-Familienfarben für Schadenscode-Chips, KeyboardHideButton-Regel. Dark + Light.

Regeln: nur Tokens (keine Hardcode-Farben), neue Strings über LocalizationManager (de/en), Touch ≥ 48 dp, alles in Dark UND Light korrekt.

KRITIS: Burn-in-Änderung = Rückwärtskompat-WARNUNG. Vor dem Commit am Gerät 233b4bd2865177ed PFLICHT-Test:
- Inspektion in Dunkel + Hell gegen Mockup 02.
- Aufnahme starten/stoppen → aufgenommenen Clip prüfen: Burn-in-OSD lesbar, korrekte Felder, Inter sichtbar.
- PDF-Bericht eines Projekts erzeugen → eingebrannte/Overlay-Felder weiter korrekt.
- Hardtasten F1-F8 + Licht −/+ + Sonde + Meter 0 funktionieren am Gerät (serielle Funktion unverändert).

Bauen: gradlew assembleDebug (BUILD SUCCESSFUL Pflicht). Gezielt committen (kein git add -A).
Melde: geänderte Dateien, Build-Ergebnis, Ergebnis der Geräte-/Recording-Tests.
```
