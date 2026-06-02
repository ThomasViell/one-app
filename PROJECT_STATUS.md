# drainq.one — Status

**Stand:** 2026-05-29 · **Rolle:** ONE-Schiebekamera — Tablet/Slave-Monitor (Live-Stream + DIN-EN-13508-2-Doku + PDF)
**Stack:** Kotlin / Jetpack Compose (Room, Koin, ExoPlayer/Media3, MQTT, iText7) · **Repo/Pfad:** `C:\Projekte\drainq.one` (GitHub: ThomasViell/one-app)
**Branch:** master · **Letzter Commit:** 2026-05-20, „polish(osd): Distanz −25% + 30% transparent"

## Aktueller Stand
Reife, ausgelieferte App (APKs bis v1.5.4 im Ordner; aktuelle Entwicklungsversion v0.3.0-Rebranding). Eigenes Auto-Update-System über GitHub-Releases (Variante A: Tablets ziehen direkt von Release-Assets). 35 Sprachen mit Fallback. Player-Stack auf ExoPlayer/Media3 konsolidiert, OSD via Canvas-Overlay, Recording via FfmpegRtspRecorder. MHC nutzt denselben „AIO-ONE"-Build.

## Offene Punkte (priorisiert)
1. [ ] **173 uncommittete Änderungen** sichern/committen — Datenverlust-Risiko
2. [ ] Ordner liegt außerhalb von `C:\Projekte\DrainQ` (Geschwister) — bei Reorg unter ein Dach ziehen
3. [ ] Update-Mechanismus von GitHub-Pull auf `drainq-cloud`-Distribution umstellen (Zielbild)

## Blocker / Risiken
- Großer uneingecheckter Working Tree (173 Dateien).

## Reorg-Bezug
**Saat für `drainq-android`**: wird zur Produkt-Variante `app-one`; aus der App werden die geteilten Core-Module (Domain, Katalog-/Lizenz-Client, i18n, Export, Update-Client) extrahiert.

## Letzte Änderungen
- [2026-05-20] OSD-Politur; Settings-Cleanup (Migration A) gemerged
