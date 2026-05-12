# Changelog

Alle signifikanten Änderungen an DrainQ.ONE werden hier dokumentiert.
Format orientiert sich an [Keep a Changelog](https://keepachangelog.com/de/1.0.0/).
Versionierung: SemVer. versionCode = MAJOR×10000 + MINOR×100 + PATCH.

---

## [0.4.0] — Unreleased

### Geändert
- refactor(update): Wechsel von Variante B (Hetzner-Mirror) auf Variante A (direkter GitHub-Download); Repo public
- `UPDATE_PROXY_URL` zeigt jetzt auf `https://github.com/ThomasViell/one-app/releases/latest/download/`
- OkHttp: `followRedirects = true`, `followSslRedirects = true` explizit gesetzt; User-Agent `DrainQ.ONE/<version>` für GitHub-Downloads
- `generate-release-manifest.py`: APK-URL generiert GitHub-Asset-URL (`/releases/download/v<ver>/`)
- ADR 0001: `MARKER_HOSTING` von `SUBPATH` auf `GITHUB_PUBLIC` geändert

### Entfernt
- `ops/hetzner-update-proxy/` komplett gelöscht (mirror-releases-one.sh, drainq-one-mirror.service, drainq-one-mirror.timer, nginx-snippet-one.conf, DEPLOYMENT.md)
- GitHub Secret `DRAINQ_RELEASE_PAT` nicht mehr benötigt (kein Mirror, Repo public)

---

## [0.3.0] — 2026-05-12

### Hinzugefügt
- Update-Modul: In-App-Updater mit OkHttp, SHA256-Prüfung, PackageInstaller-Session
- Update-Settings-Karte: Version, Channel, letzter Check-Zeitpunkt, Update-Button
- WorkManager Periodic-Check (24 h, WLAN-only) mit lokaler Notification bei verfügbarem Update
- UpdateDialog mit scrollbaren Release-Notes und optionalem Mandatory-Banner
- UpdateProgressDialog mit determiniertem/indeterminierten Fortschrittsbalken
- 7-Tap-Easter-Egg auf Versionsnummer für Beta-Channel-Switch
- Audit-Log für Update-Events (Room DB, 6 Event-Typen, 90-Tage-Retention)
- KRITIS-Dokumentation in `docs/kritis/update-process.md`
- Lokalisierung: 35 Sprachdateien unter `app/src/main/assets/i18n/` (DE + EN nativ, 33 weitere mit DE-Fallback)
- Dokumentation: `docs/UPDATE_USER_GUIDE.md`, `docs/UPDATE_OPS_GUIDE.md`
- Integrationstests: 8 JVM-Tests mit MockWebServer (Phase 6)

### Geändert
- ExoPlayer/Media3 ist jetzt einziger Video-Player (libVLC entfernt)
- OSD-Overlay immer via Canvas (kein Feature-Flag mehr)
- FFmpegRtspRecorder immer aktiv (kein Feature-Flag mehr)
- APK-Größe: 230 MB → 144 MB (−86 MB durch Entfernung von libvlc-all)

### Entfernt
- VlcVideoPlayer.kt
- libvlc-all:3.6.5 Dependency
- Feature-Flags useFfmpegOsdPlayer, useFfmpegRecording

### Sicherheit
- Transport-Security: HTTPS, TLS 1.2+
- Integrität: SHA256-Prüfung vor Installation + Android-Signaturverifikation
- Permission-Surface: REQUEST_INSTALL_PACKAGES mit User-Bestätigungsdialog
- Audit-Log: vollständige Update-Event-Protokollierung (DSGVO-konform, 90 Tage)

---

## [0.2.0] — 2026-04-15

### Hinzugefügt
- Adresssuche mit Forward-Geocoding (Nominatim)
- Interaktiver Map-Picker mit dynamischem OSM-Tile-Loading
- Offline-Maps via MapsForge
- Localization in 35 Sprachen (LocalizationManager)
- Hardware-OSD Live-Toggle (BWELL-Protokoll via DeviceService:12345)
- Aspect-Ratio-Korrektur (Letterbox via Modifier.aspectRatio)

---

## [0.1.0] — 2026-03-01

### Erstveröffentlichung
- Live-RTSP-Stream (ExoPlayer)
- Schadensdokumentation nach DIN EN 13508-2
- PDF-Reporterzeugung (iText7)
- Room-Datenbank für Projekte, Inspektionen, Schäden
- Dark-Theme (DrainQ Design System)
- Adaptive Navigation (Rail / BottomBar)
