# DrainQ.ONE — Update-Prozess (Konzept)

**Stand:** 2026-09-05
**Status:** Beschlossen — Portalweg aktiv seit 05.09.2026 (CEO-Entscheid; siehe ADR 0001,
Nachtrag 05.09.2026). Der GitHub-Weg (Variante A, aktiv seit 12.05.2026) ist abgelöst.

---

## TL;DR

DrainQ.ONE hat einen In-App-Updater gegen das **DrainQ-Portal** `license.drainq.com`
(Produkt `one`, Software-Distribution). Bau und Upload laufen lokal über
`tools/publish-one-release.ps1`; Freigabe und Veröffentlichung sind menschliche Akte im
Portal. Tablets ziehen Manifest und APK vom Portal — kein GitHub, kein Mirror-Server.
Die App ist plattformsigniert (`sharedUserId="android.uid.system"`, ADR-0005) — das ist die
Authentizitätsanker der Auslieferung.

---

## Architektur (Portalweg — aktiv)

```
Bauer/CEO (lokale Konsole)
   tools/publish-one-release.ps1
   - assembleRelease --no-daemon, plattformsigniert
     (ONE_PLATFORM_KEYSTORE / ONE_PLATFORM_PASS — fail-closed)
   - Docs-Gate (HelpCoverageTest, Golden-Diff, Render, PDF)
        │
        ▼  POST /api/software/releases        (X-DrainQ-ApiKey)
DrainQ-Portal license.drainq.com
   - Release-Datensatz (Entwurf) in der Portal-DB
   - POST /api/software/releases/{id}/artifacts → APK in Server-Storage
     (sha256 + size rechnet der Server; StoredPath nicht öffentlich)
        │
        ▼  Mensch im Portal /admin/releases
   Freigeben (ApprovedByUserId/ApprovedAt, Audit ReleaseApproved)
   Veröffentlichen (IsPublished=true, Audit ReleasePublished;
   fail-closed: ≥1 Artefakt, alle mit sha256, freigegeben)
        │
        ▼  live berechnet: latest = höchster veröffentlichter versionCode
   Manifest: GET /api/software/one/releases.beta.json
   APK:      GET /api/software/download/{artifactId}  (nur wenn IsPublished)
        │
        ▼
Tablet (DrainQ.ONE)
   - HttpUpdateService.checkForUpdate() — manuell + WorkManager periodic
   - vergleicht BuildConfig.VERSION_CODE mit latest.versionCode
   - APK-Download in cacheDir, SHA256-Prüfung gegen Manifest
   - PackageInstaller-Session → System-Bestätigungsdialog → Installation
```

`latest` wird von niemandem gesetzt: es ist der höchste veröffentlichte `versionCode` des
Kanals, bei jeder Manifest-Anfrage live berechnet
(`SoftwareDistributionController.cs`, `LatestPublishedAsync`).

---

## Vergleich Suite ↔ ONE (Ist-Zustand)

| Schicht | Drainq Suite (Windows) | DrainQ.ONE (Android) |
|---|---|---|
| Build | lokal / CI | lokal, CEO-Konsole (`assembleRelease --no-daemon`) |
| Signatur | Authenticode | **Plattformschlüssel** `bominwellalias` (ADR-0005), v1+v2 |
| Verteilung | Velopack/Update-Server | DrainQ-Portal `license.drainq.com` (eigene Software-Distribution) |
| Freigabe | — | Mensch im Portal (beta: Ersteller; stable: kein Zweit-Admin — **nicht gebaut**, Stand 06.09.2026, gemessen in `AdminReleases.razor:179-186`; Welle `portal-freigabe-4augen` offen) |
| Client | Velopack | `HttpUpdateService.kt` + OkHttp + `PackageInstaller` |
| Manifest | Velopack-Format | `releases.<channel>.json` (Portal-Format, `UpdateModels.kt`-kompatibel) |

---

## Manifest-Format (`releases.beta.json`)

```json
{
  "channel": "beta",
  "latest": {
    "version": "0.9.2",
    "versionCode": 902,
    "minSdk": 26,
    "url": "https://license.drainq.com/api/software/download/<artifactId>",
    "sha256": "abc123…",
    "size": 145728912,
    "releasedAt": "2026-09-05",
    "notes": "…",
    "mandatory": false
  },
  "history": []
}
```

Felder, die der Server fest setzt (`SoftwareDistributionController.cs`): `minSdk` = 26,
`mandatory` = false, `history` = leer. `url` zeigt auf den Download-Endpunkt des Portals,
nicht auf einen Storage-Pfad.

---

## Client-Code (Kotlin)

```
app/src/main/java/com/uip/oneapp/
├── update/
│   ├── UpdateService.kt              Interface
│   ├── HttpUpdateService.kt          OkHttp gegen Portal-Manifest (UPDATE_PROXY_URL)
│   ├── UpdateModels.kt               Manifest, ReleaseInfo
│   ├── UpdateInstaller.kt            PackageInstaller-Session
│   ├── UpdateInstallReceiver.kt      Bestätigungsdialog (STATUS_PENDING_USER_ACTION)
│   ├── UpdateConfig.kt               BuildConfig + SharedPrefs-Override
│   └── UpdateWorker.kt               WorkManager periodic check
└── ui/screens/settings/
    └── UpdateSection.kt              Settings-Karte mit Check-Button + Status
```

Konfiguration (`app/build.gradle.kts:57-62`): `UPDATE_MODE="proxy"`,
`UPDATE_PROXY_URL="https://license.drainq.com/api/software/one/"`,
`UPDATE_CHANNEL="beta"`. Diese `buildConfigField`-Werte stehen in `defaultConfig` und gelten
für Debug- und Release-Bau identisch.

---

## Trust-Modell / Sicherheit

| Bereich | Maßnahme |
|---|---|
| **Transport** | HTTPS-only gegen `license.drainq.com` (eigener Server, eigener nginx) |
| **Integrität** | sha256 im Manifest, vom **Server** beim Upload gerechnet; Client verifiziert vor Installation (`HttpUpdateService.kt:104-116`) |
| **Authentizität** | **Plattformsignatur**: Android verlangt identischen Signing-Key; ein Fremder kann keine installierbare Fälschung bauen, solange der Plattformschlüssel beim Hersteller bleibt. Schützt zusätzlich gegen eine kompromittierte Portal-Fassung auf Geräteseite |
| **Schlüssel** | `bominwellalias.keystore` lokal beim CEO, Passwort von Hand — nirgends gespeichert, nicht im Repo, nicht im Skript |
| **API-Zugang** | `X-DrainQ-ApiKey` (Benutzer-Umgebungsvariable `DRAINQ_PUBLISH_APIKEY`). Erlaubt Anlegen, Upload **und Veröffentlichen** (`POST releases/{id}/publish`, `[ApiKeyOrAdminAuth]` — ein gültiger Schlüssel genügt, ohne Anmeldung). **Nicht** erlaubt: Freigeben — dafür existiert kein Endpunkt. |
| **4-Augen** | Nicht gebaut. Freigeben ist der einzige rein menschliche Akt (kein API-Endpunkt) und schreibt einen Audit-Eintrag `ReleaseApproved` (`AdminReleases.razor:183`). Veröffentlichen geht auf zwei Wegen: über die Portal-Oberfläche mit Audit-Eintrag `ReleasePublished` (`AdminReleases.razor:214`), oder per API-Schlüssel über `POST releases/{id}/publish` — **dieser Weg schreibt keinen Audit-Eintrag** (`SoftwareDistributionController.cs:230-254`, nur `LogInformation`). Stand 06.09.2026. |
| **Berechtigungen** | `REQUEST_INSTALL_PACKAGES` (System-Bestätigungsdialog), keine MANAGE-Permission |
| **Rollback** | Keine Zurücknahme im Portal; einziger Rückweg: höhere Nummer mit altem Stand (siehe `UPDATE_OPS_GUIDE.md`, Abschnitt Rückweg) |
| **DSGVO** | Zugriffs-Logs des Portal-nginx und deren Retention: **offen, zu klären** — nicht im Portal-Repo belegt |

---

## Versions-Schema

- `versionName`: SemVer `0.9.2`
- `versionCode`: Portal-Schema MAJOR*10000 + MINOR*100 + PATCH (902), monoton steigend;
  Rueckfallwerte in `app/build.gradle.kts:41-42`, beim Bau explizit via
  `APP_VERSION_CODE`/`APP_VERSION_NAME`; bei Plattformsignatur ohne Variablen bricht der Bau
  hart ab (Guard, `app/build.gradle.kts:18-26`)
- Kanäle über Manifest-Pfad: `releases.beta.json` (aktiv) / `releases.stable.json`

---

## Erfolgs-Kriterien

- [x] In-App-Updater implementiert
- [x] Umstellung auf Portalweg — 05.09.2026 (diese Welle `portalweg`)
- [ ] Veröffentlichung 0.9.2/902 über den Portalweg, Update-Lauf auf dem Testgerät belegt
- [ ] SHA256-Manipulation im Manifest → Client bricht Install ab
- [ ] Manuell ausgelöster Check offline-resilient (keine Crashes bei Netzfehler)
- [ ] Keine Schlüssel/Tokens auf Tablets, im Repo oder in Logs
