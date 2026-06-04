# Welle 3 — Detail/Galerie + Media-Dialoge + Reports (Claude Code Prompt)

Modell **Sonnet, Effort mittel**. Branch `feature/sa-design-rollout` (weiter) oder eigener Worktree (disjunkt von Welle 4 → parallel möglich). Vorgabe: `docs/design/drainq-one_SA-Design_Umsetzung_2026-06-04.md`, Mockup `docs/design/drainq-one_03_projekte-galerie_SA-design.svg` (Detail-Hälfte).

```
Setze Welle 3 (Detail/Galerie + Media-Dialoge + Reports) auf feature/sa-design-rollout um. Reine UI, Dq-Komponenten + Tokens aus Welle 0, Logik/Daten unverändert.

1) ui/screens/projectdetail/ProjectDetailScreen.kt → Mockup 03 Detail: Titel + Status-DqStatusChip, Tabs Fotos/Schäden/Videos/Notizen mit Amber-Underline (aktiv), Thumbnail-Grid (DqCard-Kacheln, Code-Familienfarben-Chips für Schadenscodes), untere Aktionen „Inspektion fortsetzen" (DqButton Secondary, Amber-Border) + „PDF-Bericht" (DqButton). Benennung „Galerie" konsistent.
2) ui/screens/projectdetail/FullscreenImageDialog.kt: schwarzes BG, Amber-Controls, große Touch-Buttons (DqIcon ≥ 48 dp) für Schließen/Navi.
3) ui/screens/projectdetail/VideoPlaybackDialog.kt: Player-Controls als DqIcon (36), Amber-Play, Fortschritt Amber.
4) ui/screens/projectdetail/PdfPreviewDialog.kt: DqHeader + DqButton „Teilen/Exportieren"; Vorschaufläche tokenbasiert.
5) ui/screens/reports/ReportsScreen.kt: DqCards, zentrale Large-CTA „Bericht generieren" (72 dp, Amber).

Regeln: nur Tokens (keine Hardcode-Farben), neue Strings über LocalizationManager (de/en), Touch ≥ 48 dp, Dark UND Light korrekt, KeyboardHideButton wo Tastatur. Daten-/Export-Logik (ProjectDetailViewModel, ProjectExportService) NICHT ändern — nur Optik.

Bauen: gradlew assembleDebug (BUILD SUCCESSFUL Pflicht). Gezielt committen (kein git add -A).
Optional Geräte-Sichtcheck Dunkel+Hell gegen Mockup 03 (kein Recording, nicht commit-blockierend).
Melde: geänderte Dateien, Build-Ergebnis.
```
