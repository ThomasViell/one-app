# Welle 4 — Settings-Feinschliff, Update, Connection, OfflineMaps, Splash (Claude Code Prompt)

Modell **Sonnet, Effort mittel**. Branch `feature/sa-design-rollout` (weiter) oder Worktree (disjunkt von Welle 3). Vorgabe: `docs/design/drainq-one_SA-Design_Umsetzung_2026-06-04.md`, Mockup `docs/design/drainq-one_04_einstellungen_SA-design.svg`.

```
Setze Welle 4 (Rest) auf feature/sa-design-rollout um. Reine UI, Dq-Komponenten + Tokens aus Welle 0, Logik unverändert.

1) ui/screens/settings/UpdateSection.kt + ui/components/UpdateDialog.kt + ui/components/UpdateProgressDialog.kt: „Nach Updates suchen" als DqButton (Amber), Fortschritt Amber, Release-Notes in DqCard, Kanal-/Versions-Anzeige als DqStatusChip. Update-Logik (Check/Download/Install) NICHT ändern.
2) ui/screens/settings/SettingsScreen.kt: Feinschliff gegen Mockup 04 (Karten-/Toggle-/Dropdown-Konsistenz, Abstände) — falls schon vollständig aus Welle 0, nur prüfen/angleichen.
3) ui/screens/connection/ConnectionScreen.kt: Status-Dots/-Chips als DqStatusChip (Success/Warning/Error), „Neu verbinden" als DqButton. Verbindungslogik unverändert.
4) ui/screens/offlinemaps/OfflineMapsScreen.kt: Listen/Karten als DqCard, Download-Fortschritt Amber, Aktionen als DqButton.
5) ui/screens/splash/SplashScreen.kt: BG bgWindow-Token, Logo-Slot zentral, Amber-Akzent; in Dark UND Light korrekt.

Regeln: nur Tokens (keine Hardcode-Farben), neue Strings über LocalizationManager (de/en), Touch ≥ 48 dp, Dark UND Light korrekt, KeyboardHideButton wo Tastatur.

Außerdem: die docs/design/welle-*-prompt.md in einen kleinen Docs-Commit mitnehmen (docs/design vollständig tracken).

Bauen: gradlew assembleDebug (BUILD SUCCESSFUL Pflicht). Gezielt committen (kein git add -A).
Melde: geänderte Dateien, Build-Ergebnis. Danach: ganze App in Dunkel+Hell visuell auf Token-Konsistenz prüfen (Restbestände Teal/Barlow?).
```
