# DrainQ.ONE — Android Tablet-App

**Kanalispektion Tablet-Monitor für NSP3CT / BWELL Inspektionssystem**

Kotlin/Jetpack Compose Android-App zur Live-Kamera-Anzeige und DIN EN 13508-2 Inspektionsdokumentation auf Samsung-Tablets. Ausgeführt als **Slave-Monitor** mit Read-Only Hardware-Status.

---

## Links

- **GitHub Repo:** https://github.com/ThomasViell/one-app (privat)
- **Dokumentation:** siehe `HANDOVER.md` (Projekt-Kontext)
- **Architecture:** `docs/adr/` (ADRs)
- **Phasenplan Update-Prozess:** `docs/UPDATE_PROCESS_PHASENPLAN.md`

---

## Quick Start

### Build & Install

```bash
cd C:\Projekte\drainq.one
$env:JAVA_HOME = "C:\Android\jdk17"
$env:ANDROID_SERIAL = "R52Y303GEZH"
.\gradlew installDebug
```

### Anforderungen

- JDK 17 (nicht JADX-JRE)
- Android SDK API 34 (Target), 26+ (Min)
- Gradle 8.7
- Kotlin 1.9

---

## Struktur

```
app/src/main/java/com/uip/oneapp/
├── network/          — Hardware-Anbindung (BWELL, RTSP, MQTT)
├── ui/               — Jetpack Compose Screens
├── data/             — Room Database (Projects, Damages, Notes)
├── export/           — PDF/XML Export
├── update/           — In-App-Update-Modul (Phase 2+)
└── di/               — Koin Dependency Injection

app/src/main/assets/i18n/  — 35 Sprach-JSON-Dateien
docs/                      — Konzept, ADR, Guides, KRITIS
ops/hetzner-update-proxy/  — Update-Server Deployment
```

---

## Update-Prozess

DrainQ.ONE hat ein vollautomatisches Update-System basierend auf:
- **GitHub Releases** (privat)
- **Hetzner-Proxy** unter `https://updates.drainq.de/one/`
- **In-App-Updater** (OkHttp + PackageInstaller)

### Für Endkunden

Tablets prüfen täglich (nur im Internet-WLAN):
1. Öffnen Sie **Einstellungen**
2. Scrollen zu **Update-Einstellungen**
3. Tippen **„Nach Updates suchen"**

Bei verfügbarem Update: Download + Installation mit User-Bestätigung.

**Vollständiger Guide:** `docs/UPDATE_USER_GUIDE.md`

### Für Ops / Release-Manager

Tag pushen triggert automatisch:
```bash
git tag v0.4.0
git push --tags
```

Workflow läuft, APK wird gebaut + signiert, GitHub Release + Mirror update.

**Vollständiger Ops-Guide:** `docs/UPDATE_OPS_GUIDE.md`

---

## Versioning & Releases

- **Schema:** `MAJOR.MINOR.PATCH` (Semantic Versioning)
- **versionCode:** aus Tag berechnet (`MAJOR*10000 + MINOR*100 + PATCH`)
- **Kanäle:** `stable` (default), `beta` (versteckt hinter 7-Tap)
- **Changelog:** `CHANGELOG.md`

---

## Hardware-Setup (Pilot)

| Gerät | Rolle | Serial |
|---|---|---|
| Samsung SM-X610 | Tablet-App | R52Y303GEZH |
| BWELL ONE | Controller | 233b4bd2865177ed |

Verbindung: ONE-Hotspot SSID `ONE_01`, Tablet IP `192.168.35.195`.

**Details:** siehe `HANDOVER.md`

---

## Sprachen

35 Sprachen unterstützt mit Fallback-Mechanismus:

| Sprachen | Status |
|---|---|
| Deutsch, English | Native Übersetzung |
| Alle weiteren 33 | TODO-Markierung + Fallback auf DE |

Übersetzungs-Dateien: `app/src/main/assets/i18n/*.json`

---

## Testing

```bash
./gradlew testDebugUnitTest       # Unit-Tests (Update-Modul, etc.)
./gradlew connectedDebugAndroidTest  # Integration-Tests auf SM-X610
```

---

## Lizenz

Proprietär (UIP Team)

---

## Support

**Kontakt:** t.viell@uip.team  
**Docs:** `HANDOVER.md`, `CLAUDE.md` (für Claude Code)
