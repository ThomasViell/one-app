# Changelog

Alle signifikanten Änderungen an DrainQ.ONE werden hier dokumentiert.
Format orientiert sich an [Keep a Changelog](https://keepachangelog.com/de/1.0.0/).
Versionierung: SemVer. versionCode = MAJOR×10000 + MINOR×100 + PATCH.

---

## [Unreleased] — Welle `w33e-neu` (Zweig `welle/w33e`, nicht gemergt)

> Nicht Teil der Flotte, solange der Zweig nicht gemergt ist. Belegliste je Schlüssel:
> `_ketten/w33e-neu/TOTE_SCHLUESSEL.md`; Messungen: `_ketten/w33e-neu/BERICHT.md`.

### Entfernt
- 83 tote l10n-Schlüssel aus allen 35 Sprachblöcken der `LocalizationManager` (−2.061 Zeilen: 2.060 Einträge und 1 verwaister Kommentar; kein Wert geändert) — 81 per Kriterium (Literal `"<schluessel>"` in keiner Produktdatei außer `LocalizationManager.kt`) plus `inspection`/`reports` von Hand entschieden (je 8 Literalstellen sind Route/Icon/Ordner, kein Übersetzungsaufruf)

### Hinzugefügt
- Wächter `L10nToteSchluesselTest` (fünf Methoden): ein neu hinzugefügter, nirgends benutzter Schlüssel wird rot — auch wenn er nur über die Sammel-Map `translations` hinzukommt (X1) oder sein Name nur in einem Kommentar im Produktcode steht (X2, W-33e-nb, PRUEFBERICHT-Befund B-1); benannte verbleibende Blindstelle: Schlüssel, deren Name zufällig als Routen-/Icon-/Ordner-Literal vorkommt (wie `inspection`, `reports`), bleiben unsichtbar. Die Rot-Beweise der beiden neuen Prüfungen (Mutationen X1/X2, URL-Fall) fährt der Prüfer in eigenen Klonen (CEO-Nachlauf 24.09.2026, `_ketten/w33e-nb/BERICHT.md`); der Nachlauf-Lauf am Kopf 35c4514 ist grün: Kompilierung, Paket `ui.localization` 40/0/0, Volllauf 645 Tests (644 + 1 neue Methode), 0 Fehlschläge (`_ketten/w33e-nb/belege/nb2_01`–`nb2_04`)

### Geändert
- Export-Logzeile nennt „Export ZIP generated (PDF + media)" statt des XML-Exports, der seit CEO 07.06.2026 entfernt ist (`ProjectExportService`)
- `HERKUNFT.md`: 33 Map-Summen nachgezogen, Nachtragsabsatz; `L10nDoppelschluesselTest`-Schwelle 250 → 200 Paare je Block, weil die Welle jeden kleinen Block von 289 auf 232 Paare verkleinert (CEO-Auflage A-5; Grenz-Rotbeweis 199 rot / 200 grün, vom Prüfer gefahren und belegt: `_ketten/w33e-neu/PRUEFBERICHT.md` Abschnitt 4, `pruef_m12_a5_199_rot.txt` / `pruef_m13_a5_200.txt`)
- ADR-0006 §2c: die Zahl „123 fehlende Portal-Schlüssel" ist als datierte Messung vom 17.09.2026 belegt (Quelle `_ketten/l10n-anschluss/PLAN.md`), der Gegenwartssatz „einzige Quelle" durch einen Nachtrag ersetzt (heute fehlen 2, beide benutzt)

---

## [Unreleased] — Welle `l10n-anschluss` (Zweig `welle/l10n-anschluss`, nicht gemergt)

> Nicht Teil der Flotte, solange der Zweig nicht gemergt ist. Steht hier fuer den Merge-
> Zeitpunkt vor; siehe `docs/adr/0006-l10n-portal-chain.md` fuer die Begruendung und
> `_ketten/l10n-anschluss/BERICHT.md` fuer die Messungen.

### Hinzugefügt
- Portalgestützte Übersetzungskette: Sprachpaket (de/en eingecheckt, weitere Sprachen nachladbar), Zwischenspeicher, echter Portalabruf mit ETag (Z-1, Z-2)
- Sprachverwaltung in den Einstellungen — Karte „Sprachpakete" mit Zustand, Laden/Auffrischen/Löschen, Listenquelle, Rückfall-Diagnosezeile (Z-3, Z-6)
- Herkunftswächter für fremdsprachige Werte (`HERKUNFT.md` + `L10nHerkunftTest`) — eine Änderung ohne Herkunftsvermerk macht den Testlauf rot (Z-7)
- Standard-Schadensbezeichnungen folgen jetzt der Sprache; eigene oder editierte Bezeichnungen bleiben unverändert (Z-8)

### Geändert
- Rückfallkette bei fehlendem Text ist jetzt Sprache → Englisch → Schlüsselname statt eines stillen Rückfalls auf Deutsch (Z-4)
- BETA-Sprachgate entfernt — die angebotene Sprachliste kommt ausschließlich vom Portal, nicht mehr aus einer festen Zwei-Sprachen-Liste (Z-5)

### Bekannt offen
- Geräteabnahme steht aus (kein Testgerät in dieser Welle) — Zwischenspeicher-Überleben von Neustart/Flugmodus, Ladezeit am Gerät, sichtbare Oberfläche
- 123 Map-Schlüssel und 474 Hilfe-Schlüssel fehlen weiterhin im Portal (eigene Welle mit Prüfer vorgesehen)

---

## [Unreleased] — Welle `portal-nachzug` (Zweig `welle/portal-nachzug`, nicht gemergt)

> Nicht Teil der Flotte, solange der Zweig nicht gemergt ist. Messungen in
> `_ketten/portal-nachzug/BERICHT.md`.

### Geändert
- Import-Skript `tools/l10n-import-to-portal.ps1` sendet nur noch Deutsch (kein `en`-Block, kein `sourceEn`), gleicht vor dem Paketbau gegen das lebende Portal ab (NEU/GLEICH/ABWEICHEND/NUR-PORTAL, namentlich als Listen) und sendet ausschließlich NEU + per Freigabedatei freigegebene ABWEICHEND; Trockenlauf ohne Schlüssel, Sperren e1–e6 vor jedem Senden
- Neue Bibliothek `tools/l10n/L10nImportLib.ps1` (Dekodierung, Vergleich, Paketbau, Sperren) mit Pester-Tests (10) und Szenarien `docs/auftraege/SZENARIEN_portal-nachzug.md`

### Bekannt offen
- `help.*` (474) weiterhin nicht im Portal (eigene Welle, E-P7); der echte Upload ist CEO-Akt und noch nicht gelaufen

---

## [0.9.5] — 2026-09-16

> **Lücke 0.5.0–0.9.4:** Dieser Changelog wurde zwischen dem 12.05.2026 (0.4.0) und dem
> 16.09.2026 nicht gepflegt. Die Änderungen der Versionen 0.5.0 bis 0.9.4 sind vollständig in
> `_queue/QUEUE.md` (Wellen 1–29) belegt. Sie werden hier bewusst **nicht** nachträglich
> rekonstruiert — eine halb richtige Historie wäre schlechter als eine benannte Lücke. Der
> darunter stehende Block `[0.4.0] — Unreleased` beschreibt einen Verteilweg über GitHub, der
> mit Welle 22 (08.09.2026) entfernt wurde; er bleibt als Altbestand stehen.

### Hinzugefügt
- Eigene Seite für Datum, Uhrzeit und Zeitzone im Kiosk — Einstellen ohne die App zu verlassen (Welle 27)
- Zeitzonenliste nach Kontinenten gruppiert; die Suche überstimmt die Gruppierung (Welle 29, Z-2)
- USB-Export mit Einzelauswahl: beginnt leer, Exportknopf gesperrt bis etwas gewählt ist; der Vollprojekt-Weg bleibt unverändert (Welle 29, Z-1)
- Diagnosezeile auf der Zeitseite, zeigt die Setz-Werte ohne adb und übersteht einen Geräteneustart (Welle 28)

### Geändert
- Rückleseprobe der Systemzeit misst gegen eine monotone Referenz (`SystemClock.elapsedRealtime()`) und meldet `Overwritten`, statt fälschlich Erfolg zu melden (Welle 28, B-B6)
- Zeitautomatik wird vor dem Setzen geklärt statt danach; abgeschaltet wird nur nach Bestätigung (Welle 28)
- Zwei Systemzugriffe vom Anzeigefaden genommen, vier ungeschützte Port-Zugriffe abgesichert (Welle 28, B-B2)
- Meldung bei unlesbarem Automatik-Zustand nennt diesen Fall als eigenen, statt „die Zeitautomatik war aus" zu behaupten; sie blockiert nicht (Welle 29, Z-5)

### Bekannt offen
- Mit Zeitautomatik AN wird eine von Hand gesetzte Uhrzeit weiterhin vom Gerät überschrieben. Neu ist nur, dass die App es meldet (Feldlauf 15.09.2026, Fall 2)
- Einfrieren beim Bestätigen mit Zeitautomatik AUS: der wahrscheinlichste Auslöser ist entfernt, die Ursache ist **nicht gemessen** (W-27a)
- Die sichtbare Oberfläche der Wellen 28 und 29 ist mangels Gerät nicht abgenommen

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

**Berichtigung 07.09.2026:** Variante A (direkter GitHub-Download, oben beschrieben) wurde vor
einer Veröffentlichung durch den Portalweg ersetzt (`license.drainq.com/api/software/one/`,
CEO-Entscheid 05.09.2026, Welle `portalweg`). `.github/workflows/release-apk.yml` und
`scripts/generate-release-manifest.py` sind entfernt (Welle `github-reste`, 07.09.2026). Dieser
Abschnitt beschreibt einen zwischenzeitlichen, nie veröffentlichten Stand.

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

**Berichtigung 07.09.2026:** `docs/kritis/update-process.md` wurde entfernt — KRITIS/NIS2/ISO
27001 sind für die ONE nicht einschlägig (CEO-Entscheid 14.07./07.09.2026, siehe
`docs/engineering/01-analysis_one.md:113`). Audit-Log und Integrationstests oben bleiben davon
unberührt.

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
