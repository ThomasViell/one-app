# DrainQ.ONE — Update-Prozess (Konzept)

**Stand:** 2026-05-12
**Autor:** Claude (auf Basis Drainq Suite / Hetzner-Proxy)
**Status:** Beschlossen — Variante A aktiv seit 2026-05-12 (siehe ADR 0001)

---

## TL;DR

DrainQ.ONE hat einen In-App-Updater nach demselben Trust-Modell wie die Drainq Suite (Tag → GitHub Actions → GitHub Release → Client). Da Velopack Windows-only ist, wird der Client-Teil eigenständig in Kotlin gebaut (Manifest-JSON + APK-Download + `PackageInstaller`-API). Tablets ziehen direkt von **GitHub Release Assets** — kein Mirror-Server erforderlich (Repo ist public).

> **Gewählte Variante: A — direkter GitHub-Download**  
> Variante B (Hetzner-Mirror) wurde am 2026-05-12 verworfen. Begründung: Hetzner-Container-Stack hat keinen Bare-Metal-Nginx, der Suite-Mirror existiert dort nicht, Aufwand unverhältnismäßig. Siehe ADR 0001.

---

## Vergleich Suite ↔ ONE

| Schicht | Drainq Suite (Windows) | DrainQ.ONE (Android) |
|---|---|---|
| Build-Trigger | Tag `v*.*.*` push | Tag `v*.*.*` push |
| CI | GitHub Actions, `windows-latest` | GitHub Actions, `ubuntu-latest` |
| Build-Tool | `dotnet publish` + `vpk pack` | `./gradlew assembleRelease` |
| Signatur | Authenticode (offen) | Android Keystore (RSA 2048, eigener Key) |
| Release-Artefakt | `Setup.exe` + nupkg + `RELEASES-stable` + `releases.stable.json` | `drainq-one-<ver>.apk` + `releases.stable.json` (+ `.sha256`) |
| Hosting | GitHub Release (privat) | GitHub Release (**public**) |
| Mirror | `updates.drainq.de` (Nginx, mirror-releases.sh, 5-min-Timer) | **keiner** — direkt von GitHub |
| Client-Lib | Velopack `SimpleWebSource` + `UpdateManager` | Eigener `UpdateService.kt` + OkHttp + `PackageInstaller` |
| Config-Lookup | ProgramData/LocalAppData/AssemblyMetadata | `BuildConfig` + SharedPreferences-Override |
| Token-Modell | Proxy = kein Token, GitHub = PAT | **kein Token** — Repo public |

---

## Architektur (Variante A — aktiv)

```
GitHub Tag v0.4.0
        │
        ▼
GitHub Actions (.github/workflows/release-apk.yml)
   - assembleRelease (signiert mit DRAINQ_ONE_KEYSTORE secret)
   - generate releases.stable.json (versionCode, sha256, notes)
   - softprops/action-gh-release → ThomasViell/one-app (public)
        │
        ▼
GitHub Release (public)
   releases.stable.json
   drainq-one-<ver>.apk
   drainq-one-<ver>.apk.sha256
        │
        ▼
Tablet (DrainQ.ONE) — direkt von GitHub
   - UpdateService.checkForUpdate() — beim App-Start + manuell
   - Manifest: https://github.com/ThomasViell/one-app/releases/latest/download/releases.stable.json
   - APK:      https://github.com/ThomasViell/one-app/releases/download/v<ver>/drainq-one-<ver>.apk
   - vergleicht BuildConfig.VERSION_CODE mit Manifest
   - bei neuer Version: APK in cacheDir, SHA256 prüfen
   - PackageInstaller-Session → User-Bestätigung → Restart
```

> GitHub redirectet `/latest/download/` auf die konkrete Asset-URL der neuesten Release.  
> OkHttp folgt dem Redirect automatisch (`followRedirects = true`, `followSslRedirects = true`).

---

## ~~Architektur (Variante B — verworfen am 2026-05-12)~~

> Variante B war die ursprüngliche Entscheidung (Hetzner-Proxy `updates.drainq.de`).  
> Sie wurde am 2026-05-12 verworfen, weil der Hetzner-Container-Stack keinen Bare-Metal-Nginx hat und der Setup-Aufwand unverhältnismäßig war. Alle Ops-Dateien (`ops/hetzner-update-proxy/`) wurden gelöscht. Siehe ADR 0001.

---

## Manifest-Format (`releases.stable.json`)

```json
{
  "channel": "stable",
  "latest": {
    "version": "0.4.0",
    "versionCode": 400,
    "minSdk": 26,
    "url": "https://github.com/ThomasViell/one-app/releases/download/v0.4.0/drainq-one-0.4.0.apk",
    "sha256": "abc123...",
    "size": 145728912,
    "releasedAt": "2026-05-14T08:00:00Z",
    "notes": "Wechsel auf direkten GitHub-Download, User-Agent, …",
    "mandatory": false
  },
  "history": [
    { "version": "0.3.0", "versionCode": 300, "releasedAt": "2026-05-12T..." }
  ]
}
```

`mandatory: true` → App zeigt blockierenden Dialog, kein "später".

---

## Client-Code (Kotlin)

```
app/src/main/java/com/uip/oneapp/
├── update/
│   ├── UpdateService.kt              Interface
│   ├── HttpUpdateService.kt          OkHttp-Impl gegen GitHub-Assets
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
| **Transport** | HTTPS-only, GitHub TLS 1.2/1.3, Cert-Pinning optional (Phase 2 — entscheiden) |
| **Integrität** | SHA256-Hash im Manifest, Verifikation vor Install — Pflicht |
| **Authentizität** | Android verlangt identischen Signing-Key wie installierte App → effektive Authentizität durch Keystore |
| **Keystore** | Eigener Release-Keystore (`oneapp-release.keystore`), in GitHub Secrets als base64, NICHT im Repo |
| **Berechtigungen** | `REQUEST_INSTALL_PACKAGES` (User-Bestätigung), keine MANAGE-Permission |
| **Audit-Log** | Updates lokal in DB-Tabelle `update_events` (alt → neu Version, Zeitpunkt, Quelle) |
| **Rollback** | Android speichert vorherige Version nicht automatisch; alte GitHub Releases bleiben erhalten, Sideload via ADB als Fallback |
| **DSGVO** | Download-IP wird in GitHub-Access-Logs (Microsoft/GitHub Infrastructure) protokolliert — in AVV ergänzen |

---

## Modi

| Modus | Quelle | Token | Einsatz |
|---|---|---|---|
| `proxy` (Default) | `https://github.com/ThomasViell/one-app/releases/latest/download/` | nein | Produktion, Pilot-Tablets |
| `local` (Test) | `http://<dev-PC>:8080/` | nein | E2E-Tests, lokales Manifest |

> Der Modus heißt weiterhin `proxy` im Code — die Bedeutung wurde umdefiniert: statt Hetzner-Proxy meint er jetzt „direkter GitHub-Download ohne PAT".  
> Modus-Lookup-Reihenfolge: SharedPreferences `update_mode` → `BuildConfig.UPDATE_MODE` (Default `proxy`).

---

## Versions-Schema

- `versionName`: SemVer `0.4.0`
- `versionCode`: monoton steigender Integer, **gebunden an Git-Tag** (CI extrahiert aus Tag und `git rev-list --count`)
- Release-Channels über Manifest-Pfad: `releases.stable.json` vs. `releases.beta.json` (beide als GitHub Release Asset)

---

## Erfolgs-Kriterien für die Umsetzung

- [x] In-App-Updater implementiert (Phasen 2–7)
- [x] Wechsel auf Variante A (direkter GitHub-Download) — 2026-05-12
- [ ] Push von Tag `v0.4.0` triggert vollautomatisch signiertes APK auf GitHub Release
- [ ] DrainQ.ONE auf SM-X610 zeigt Update-Banner, lädt APK, installiert mit User-Bestätigung, App startet in neuer Version
- [ ] SHA256-Manipulation im Manifest → Client bricht Install ab
- [ ] Manuell ausgelöster Check funktioniert offline-resilient (keine Crashes bei Netzfehler)
- [ ] Keine PATs auf Tablets, keine PATs im Repo, keine PATs in Logs

---

## Referenzen aus Drainq Suite

- `installer/scripts/build.ps1` — Master-Build-Skript
- `installer/velopack/releasify.ps1` — Velopack-Pack-Wrapper
- `.github/workflows/release.yml` — Tag-getriggerter Build
- `src/DrainQ.WPF/Services/VelopackUpdateService.cs` — Client-Logik (Modi-Lookup übernommen)
- `build/release-checklist.md` — Pre-/Post-Release Checks
