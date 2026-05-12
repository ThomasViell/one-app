# DrainQ.ONE — Update-Prozess (Konzept zur Diskussion)

**Stand:** 2026-05-12
**Autor:** Claude (auf Basis Drainq Suite / Hetzner-Proxy)
**Status:** Entwurf — wartet auf Freigabe durch Thomas

---

## TL;DR

DrainQ.ONE bekommt einen In-App-Updater nach demselben Trust-Modell wie die Drainq Suite (Tag → GitHub Actions → GitHub Release → Hetzner-Proxy → Client). Da Velopack Windows-only ist, wird der Client-Teil eigenständig in Kotlin gebaut (Manifest-JSON + APK-Download + `PackageInstaller`-API). Der bereits laufende Hetzner-Proxy `updates.drainq.de` wird um ein zweites Repo erweitert.

---

## Vergleich Suite ↔ ONE

| Schicht | Drainq Suite (Windows) | DrainQ.ONE (Android) |
|---|---|---|
| Build-Trigger | Tag `v*.*.*` push | Tag `v*.*.*` push |
| CI | GitHub Actions, `windows-latest` | GitHub Actions, `ubuntu-latest` |
| Build-Tool | `dotnet publish` + `vpk pack` | `./gradlew assembleRelease` |
| Signatur | Authenticode (offen) | Android Keystore (RSA 2048, eigener Key) |
| Release-Artefakt | `Setup.exe` + nupkg + `RELEASES-stable` + `releases.stable.json` | `drainq-one-<ver>.apk` + `releases.stable.json` (+ `.sha256`) |
| Hosting | GitHub Release (privat) | GitHub Release (privat) |
| Mirror | `updates.drainq.de` (Nginx, mirror-releases.sh, 5-min-Timer) | **gleiche** Maschine, **gleicher** Timer, neuer Subpfad `/one/` |
| Client-Lib | Velopack `SimpleWebSource` + `UpdateManager` | Eigener `UpdateService.kt` + OkHttp + `PackageInstaller` |
| Config-Lookup | ProgramData/LocalAppData/AssemblyMetadata | `BuildConfig` + SharedPreferences-Override |
| Token-Modell | Proxy = kein Token, GitHub = PAT | identisch (Proxy primär, GitHub als Fallback im Debug) |

---

## Architektur

```
GitHub Tag v0.4.0
        │
        ▼
GitHub Actions (.github/workflows/release-apk.yml)
   - assembleRelease (signiert mit DRAINQ_ONE_KEYSTORE secret)
   - generate releases.stable.json (versionCode, sha256, notes)
   - softprops/action-gh-release → ThomasViell/one-app
        │
        ▼
GitHub Release (private)
        │  drainq-mirror.timer (alle 5 min, EXISTIERT BEREITS)
        │  mirror-releases.sh — erweitert um Repo "one-app" → /var/www/drainq-updates/one/
        ▼
https://updates.drainq.de/one/releases.stable.json
https://updates.drainq.de/one/drainq-one-<ver>.apk
        │
        ▼
Tablet (DrainQ.ONE)
   - UpdateService.checkForUpdate() — beim App-Start + manuell
   - vergleicht BuildConfig.VERSION_CODE mit Manifest
   - bei neuer Version: APK in cacheDir, SHA256 prüfen
   - PackageInstaller-Session → User-Bestätigung → Restart
```

---

## Manifest-Format (`releases.stable.json`)

```json
{
  "channel": "stable",
  "latest": {
    "version": "0.4.0",
    "versionCode": 12,
    "minSdk": 26,
    "url": "https://updates.drainq.de/one/drainq-one-0.4.0.apk",
    "sha256": "abc123...",
    "size": 145728912,
    "releasedAt": "2026-05-14T08:00:00Z",
    "notes": "Phase 8 (libVLC-Revival), Map-Picker Multi-Zoom, …",
    "mandatory": false
  },
  "history": [
    { "version": "0.3.0", "versionCode": 11, "releasedAt": "2026-05-10T..." }
  ]
}
```

`mandatory: true` → App zeigt blockierenden Dialog, kein "später".

---

## Client-Code (Kotlin, neu)

```
app/src/main/java/com/uip/oneapp/
├── update/
│   ├── UpdateService.kt              Interface
│   ├── HttpUpdateService.kt          OkHttp-Impl gegen Hetzner-Proxy
│   ├── UpdateModels.kt               Manifest, ReleaseInfo
│   ├── UpdateInstaller.kt            PackageInstaller-Session + FileProvider
│   ├── UpdateConfig.kt               BuildConfig + SharedPrefs-Override
│   └── UpdateWorker.kt               WorkManager periodic check (default: 1×/24h)
└── ui/screens/settings/
    └── UpdateSection.kt              Settings-Karte mit Check-Button + Status
```

DI über `Koin` analog zu existierenden Services. Lokalisation über `LocalizationManager`.

---

## Sicherheit / KRITIS

| Bereich | Maßnahme |
|---|---|
| **Transport** | HTTPS-only, Let's-Encrypt-Cert auf `updates.drainq.de` (bereits live), Cert-Pinning optional (Phase 2 — entscheiden) |
| **Integrität** | SHA256-Hash im Manifest, Verifikation vor Install — Pflicht |
| **Authentizität** | Android verlangt identischen Signing-Key wie installierte App → effektive Authentizität durch Keystore |
| **Keystore** | Eigener Release-Keystore (`drainq-one-release.keystore`), in GitHub Secrets als base64, NICHT im Repo |
| **Berechtigungen** | `REQUEST_INSTALL_PACKAGES` (User-Bestätigung), keine MANAGE-Permission |
| **Audit-Log** | Updates lokal in DB-Tabelle `update_events` (alt → neu Version, Zeitpunkt, Quelle) |
| **Rollback** | Android speichert vorherige Version nicht automatisch; alternative Branches `stable` / `beta` via Manifest-URL umschaltbar |
| **DSGVO** | Polling-IP wird im Nginx-Log protokolliert — in AVV aufnehmen (steht bereits für die Suite) |

---

## Modi

| Modus | Quelle | Token | Einsatz |
|---|---|---|---|
| `proxy` (Default) | `https://updates.drainq.de/one/` | nein | Produktion, Pilot-Tablets |
| `github` (Fallback) | GitHub API direkt | PAT in BuildConfig (Debug-Build) | Entwickler-Builds |
| `local` (Test) | `http://<dev-PC>:8080/` | nein | E2E-Tests, lokales Manifest |

Modus-Lookup-Reihenfolge (analog Suite):
1. SharedPreferences `update_mode` (User-Override via Settings)
2. `BuildConfig.UPDATE_MODE` (Build-Time, Default `proxy`)

---

## Versions-Schema

- `versionName`: SemVer `0.4.0`
- `versionCode`: monoton steigender Integer, **gebunden an Git-Tag** (CI extrahiert aus Tag und `git rev-list --count`)
- Release-Channels über Manifest-Pfad: `/one/releases.stable.json` vs. `/one/releases.beta.json`

---

## Hetzner-Proxy: Erweiterung

Bestehendes Setup (`ops/hetzner-update-proxy/`) wird **nicht ersetzt**, sondern erweitert:

- `mirror-releases.sh` bekommt Parameter `--repo` und `--subdir`
- Neuer Systemd-Service `drainq-one-mirror.service` mit eigenem ENV (`DRAINQ_REPO=ThomasViell/one-app`, `DRAINQ_DEST=/var/www/drainq-updates/one`)
- Eigener Timer `drainq-one-mirror.timer` (5 min, versetzt um 2 min zu Suite-Timer um GitHub-API-Rate-Limit zu schonen)
- Nginx: gleicher Server-Block, neuer `location /one/` (kein eigener Vhost nötig)
- PAT: derselbe Read-Only-PAT, falls Scope ausreicht, sonst zweiter PAT

---

## Offene Fragen für Diskussion

1. **Manifest auf Tablet erweitern?** — Notes als HTML/Markdown im Update-Dialog rendern (Compose-Markdown) oder Plain-Text?
2. **Mandatory-Updates** — bei kritischen Security-Fixes blockieren bis installiert, oder immer optional lassen?
3. **Auto-Download im Hintergrund** — beim WLAN-Connect im Hintergrund vorladen, oder erst nach User-OK ziehen? (Tablets sind eh meist mit ONE-Hotspot verbunden, das hat kein Internet — Download muss auf User-WLAN warten)
4. **Channel-Switch in Settings** — soll Tester-Modus (`beta`) in der UI sichtbar sein oder Hidden-Trigger (z. B. 7× auf Version-Number tippen wie bei Android-Settings)?
5. **Code-Signing-Strategie** — Keystore wer hält (Thomas privat / UIP-Tresor / Vault)? Aktuell nur Debug-Keystore vorhanden.
6. **versionCode-Quelle** — aus Git-Tag deterministisch generieren oder fest in `build.gradle.kts` pflegen?
7. **Subdomain vs. Subpfad** — `one-updates.drainq.de` (cleaner, eigenes Cert) oder `/one/`-Subpfad (weniger Ops-Overhead)?
8. **Reichweite des Autorun-Phasenplans** — soll Phase 1 (ADR) im Autorun mitlaufen oder weiterhin manuell bleiben (wie beim OSD-Phasenplan)?

---

## Erfolgs-Kriterien für die Umsetzung

- [ ] Push von Tag `v0.4.0` triggert vollautomatisch signiertes APK auf GitHub Release
- [ ] Innerhalb 5 min ist `https://updates.drainq.de/one/releases.stable.json` aktualisiert
- [ ] DrainQ.ONE auf SM-X610 zeigt Update-Banner, lädt APK, installiert mit User-Bestätigung, App startet in neuer Version
- [ ] SHA256-Manipulation im Manifest → Client bricht Install ab
- [ ] Manuell ausgelöster Check funktioniert offline-resilient (keine Crashes bei Netzfehler)
- [ ] Keine PATs auf Tablets, keine PATs im Repo, keine PATs in Logs
- [ ] CHANGELOG.md, KRITIS-Check, ADR vorhanden

---

## Referenzen aus Drainq Suite

- `installer/scripts/build.ps1` — Master-Build-Skript
- `installer/velopack/releasify.ps1` — Velopack-Pack-Wrapper
- `.github/workflows/release.yml` — Tag-getriggerter Build
- `src/DrainQ.WPF/Services/VelopackUpdateService.cs` — Client-Logik (Modi-Lookup übernommen)
- `ops/hetzner-update-proxy/` — Nginx + Mirror-Skript + Timer
- `build/release-checklist.md` — Pre-/Post-Release Checks
