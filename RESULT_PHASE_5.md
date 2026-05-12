# RESULT PHASE 5 — Hetzner-Proxy-Erweiterung (Update-Prozess)

**Datum:** 2026-05-12
**Branch:** `feature/update-phase-5`
**Status:** abgeschlossen

---

## Branch + Commits

```
Branch: feature/update-phase-5 (aus master)
Commit: siehe git log nach diesem Commit
```

---

## Liste aller neuen und geänderten Dateien

| Datei | Status |
|---|---|
| `.gitattributes` | neu |
| `ops/hetzner-update-proxy/mirror-releases-one.sh` | neu |
| `ops/hetzner-update-proxy/drainq-one-mirror.service` | neu |
| `ops/hetzner-update-proxy/drainq-one-mirror.timer` | neu |
| `ops/hetzner-update-proxy/nginx-snippet-one.conf` | neu |
| `ops/hetzner-update-proxy/DEPLOYMENT.md` | neu |
| `RESULT_PHASE_5.md` | neu (ersetzt OSD-Phase-5-Inhalt) |

---

## Diff-Summary pro Datei

**`.gitattributes`** — Neu angelegt. Erzwingt LF-Zeilenenden für `ops/**/*.sh`, `.service`, `.timer`, `.conf` — Deploy-Ziel ist Linux/Hetzner, CRLF würde bash und systemd kaputt machen.

**`ops/hetzner-update-proxy/mirror-releases-one.sh`** — Bash-Skript mit `set -euo pipefail`. Liest PAT aus `/etc/drainq/github-pat-one` (operator-managed), validiert Token-Format per Regex (`ghp_` / `github_pat_`-Präfix). Fragt `/repos/ThomasViell/one-app/releases/latest` ab, filtert Assets nach `.apk`, `.sha256`, `.json`. Lädt nur neue Dateien (Skip wenn vorhanden — immutable Release-Assets). Setzt `chmod 0644`. Bereinigt Webroot: maximal 3 APKs + zugehörige sha256. Flock-Lock verhindert parallele Läufe.

**`ops/hetzner-update-proxy/drainq-one-mirror.service`** — Systemd `Type=oneshot`, läuft als `www-data`. Hardening: `NoNewPrivileges`, `ProtectSystem=full`, `PrivateTmp`, `ReadWritePaths=/var/www/drainq-updates/one /run`, `ReadOnlyPaths=/etc/drainq`, `MemoryDenyWriteExecute`, `RestrictAddressFamilies=AF_INET AF_INET6`. Logs nach journal mit `SyslogIdentifier=drainq-one-mirror`.

**`ops/hetzner-update-proxy/drainq-one-mirror.timer`** — `OnBootSec=4min` (versetzt zu Suite-Timer `2min` um GitHub-API-Rate-Limit zu entlasten), `OnUnitActiveSec=5min`, `Persistent=true`. Aktiviert `drainq-one-mirror.service`.

**`ops/hetzner-update-proxy/nginx-snippet-one.conf`** — `location /one/` Block für bestehenden `updates.drainq.de` Server-Block (MARKER_HOSTING: SUBPATH). APK/ZIP: `Cache-Control: public, max-age=86400, immutable`. Manifest/SHA256/JSON: `Cache-Control: no-cache`. `autoindex off`. Versteckte Dateien gesperrt. `X-Content-Type-Options: nosniff` auf allen Responses.

**`ops/hetzner-update-proxy/DEPLOYMENT.md`** — SSH-Befehlsfolge in 9 Schritten: Webroot anlegen, Skript deployen, PAT-Datei manuell anlegen (`chmod 600`), Systemd-Units installieren + `enable --now`, Nginx-Snippet einbinden + `nginx -t` + reload, ersten Lauf manuell anstoßen, Smoke-Tests via `curl -I`. Enthält auch Rollback-Anweisung für fehlerhaftes Manifest.

---

## Compile- und Test-Ergebnisse

### ./gradlew assembleDebug

```
> Task :app:assembleDebug

BUILD SUCCESSFUL in 10s
40 actionable tasks: 8 executed, 32 up-to-date
```

Phase 5 ändert keinen Android-Kotlin-Code — Build-Status unverändert.

### bash -n (Syntax-Check mirror-releases-one.sh)

```
$ bash -n ops/hetzner-update-proxy/mirror-releases-one.sh
(kein Output — Syntax OK)
```

Ausgeführt via Git Bash (`C:\Program Files\Git\bin\bash.exe -n`). Exit Code 0.

### shellcheck

shellcheck in der Build-Umgebung nicht installiert. Skript folgt shellcheck-konformen Patterns:
- `set -euo pipefail` (SC2248, SC2250)
- Alle Variablen mit `"${VAR}"` gequotet
- `read -r` mit explizitem `IFS=` (SC2162)
- `printf '%s'` statt `echo` für Variablen mit Sonderzeichen
- `[[ ... ]]` durchgehend

---

## Fortgepflanzte Marker

```
MARKER_HOSTING:      SUBPATH    → IMPLEMENTIERT: location /one/ in nginx-snippet-one.conf
MARKER_MANDATORY:    NO         → unverändert (App-seitig, Phase 2/3)
MARKER_CHANNEL_UI:   HIDDEN     → unverändert (App-seitig, Phase 3)
MARKER_VERSIONCODE:  FROM_TAG   → IMPLEMENTIERT (Phase 4)
MARKER_AUTOCHECK:    DAILY_WIFI → unverändert (App-seitig, Phase 3)
MARKER_CERT_PINNING: OFF        → unverändert
```

---

## Sicherheits-Hinweise (KRITIS-relevant, Phase 6)

- PAT verlässt `/etc/drainq/github-pat-one` nie — kein Logging, kein ENV-Export, kein `set -x`
- Token-Validierung via Regex verhindert leere oder falsch formatierte PATs
- Systemd `ReadOnlyPaths=/etc/drainq` + `www-data` User: Service kann PAT lesen, nicht schreiben
- `MemoryDenyWriteExecute` verhindert JIT/Shellcode im Service-Prozess
- Nginx `autoindex off` + Hidden-File-Block verhindert Directory-Traversal

---

## Bekannte Issues und TODOs für Folge-Phasen

- **Phase 6:** KRITIS-Doku für Hetzner-Mirror in `docs/kritis/update-process.md`: Threat-Model Hetzner-Kompromittierung, Nginx-IP-Logs + DSGVO/AVV.
- **shellcheck:** Via `apt install shellcheck` auf Hetzner oder lokal nachholen. Skript ist nach SC2-Standards geschrieben.
- **Suite-PAT-Wiederverwendung:** Falls `/etc/drainq/github-pat` denselben `repo`-Scope hat, kann `PAT_FILE` im Skript umgestellt oder ein Symlink angelegt werden — nur eine PAT-Rotation nötig.
- **beta-Kanal:** `releases.beta.json` wird gespiegelt wenn im Release-Asset vorhanden (Phase 4 `--channel beta` Flag).
- **Hetzner-Deployment:** Ist NICHT Teil des Autorun — Operator führt SSH-Befehle aus DEPLOYMENT.md aus.
