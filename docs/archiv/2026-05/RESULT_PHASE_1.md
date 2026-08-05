# RESULT PHASE 1 — Update-Prozess ADR + Marker

**Datum:** 2026-05-12
**Status:** abgeschlossen, freigegeben für Autorun Phasen 2-7

---

## Marker (für Phasen 2-7 maschinenlesbar)

```
MARKER_HOSTING:      SUBPATH
MARKER_MANDATORY:    NO
MARKER_CHANNEL_UI:   HIDDEN
MARKER_VERSIONCODE:  FROM_TAG
MARKER_AUTOCHECK:    DAILY_WIFI
MARKER_CERT_PINNING: OFF
```

## Konfigurationswerte (für nachfolgende Phasen)

| Feld | Wert |
|---|---|
| ProxyUrl | `https://updates.drainq.de/one/` |
| Manifest-Pfad (stable) | `https://updates.drainq.de/one/releases.stable.json` |
| Manifest-Pfad (beta) | `https://updates.drainq.de/one/releases.beta.json` |
| APK-URL-Schema | `https://updates.drainq.de/one/drainq-one-<version>.apk` |
| GitHub-Repo | `ThomasViell/one-app` |
| Default-Channel | `stable` |
| Auto-Check-Intervall | 24h, NetworkType.UNMETERED |
| Versionsschema | `vMAJOR.MINOR.PATCH` → versionCode = `MAJOR*10000 + MINOR*100 + PATCH` |

## Manifest-Schema (JSON)

```json
{
  "channel": "stable",
  "latest": {
    "version": "0.4.0",
    "versionCode": 400,
    "minSdk": 26,
    "url": "https://updates.drainq.de/one/drainq-one-0.4.0.apk",
    "sha256": "<64 hex chars>",
    "size": 0,
    "releasedAt": "2026-05-14T08:00:00Z",
    "notes": "release notes plain text",
    "mandatory": false
  },
  "history": [
    { "version": "0.3.0", "versionCode": 300, "releasedAt": "2026-05-10T00:00:00Z" }
  ]
}
```

`mandatory` bleibt im Schema, wird in Phase 3 UI-seitig als Banner (nicht Block) behandelt — siehe MARKER_MANDATORY: NO.

## GitHub Secrets (für Phase 4, manuell vorher zu setzen)

- `DRAINQ_ONE_KEYSTORE_BASE64`
- `DRAINQ_ONE_KEYSTORE_PASSWORD`
- `DRAINQ_ONE_KEY_ALIAS`
- `DRAINQ_ONE_KEY_PASSWORD`
- `DRAINQ_RELEASE_PAT` (Read-Only auf Repo, für Mirror)

**Autorun setzt diese NICHT** — wird in Workflow-File nur referenziert.

## Annahmen für nachfolgende Phasen

1. Master-Branch ist stabil, Phase-8-OSD-Branch ist gemergt oder pausiert.
2. `oneapp-release.keystore` liegt lokal vor (gitignored), wird NICHT vom Autorun angefasst.
3. Manifest wird vom CI-Workflow (Phase 4) generiert, nicht manuell.
4. Mirror-Skript (Phase 5) spiegelt aus privatem Repo `ThomasViell/one-app`, Service-User `www-data`.
5. Lokalisation: DE als Source, andere Sprachen in Phase 7 maschinell oder per Fallback.

## Liste der Dateien aus Phase 1

| Datei | Zweck |
|---|---|
| `docs/UPDATE_PROCESS_CONCEPT.md` | Konzept zur Diskussion |
| `docs/UPDATE_PROCESS_PHASENPLAN.md` | Phasenplan für Autorun |
| `docs/adr/0001-update-process-android.md` | ADR mit Entscheidungen und Begründungen |
| `update_autorun.ps1` | PowerShell-Autorun-Skript |
| `RESULT_PHASE_1.md` | dieses Dokument |

## Nächste Schritte

```powershell
cd C:\Projekte\drainq.one
.\update_autorun.ps1 *>&1 | Tee-Object -FilePath update_autorun.log
```

Autorun führt Phasen 2-7 sequentiell aus, jede Phase eigener Claude-Context, Übergabe via `RESULT_PHASE_N.md`.

## Bekannte Risiken / Caveats

- Erst-Tag-Push erfordert vorher gesetzte GitHub Secrets, sonst scheitert Workflow.
- Erst-Release wird Signatur ändern → bestehende Tablet-Installation muss deinstalliert werden (siehe ADR Punkt 2).
- Hetzner-Deployment (Phase 5 liefert nur Files, kein automatischer Server-Push) bleibt manueller Schritt.
