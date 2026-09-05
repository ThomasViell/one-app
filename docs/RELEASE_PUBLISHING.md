# Release veröffentlichen — DrainQ-Portal (Ops)

Stand 05.09.2026. Der frühere GitHub-Weg (GitHub-Release-Assets als Download-Quelle) gilt
seit dem 05.09.2026 nicht mehr (CEO-Entscheid; der Portalweg steht seit dem CEO-Beschluss
07.06.2026 im Code, `app/build.gradle.kts:57-62`: `UPDATE_PROXY_URL =
https://license.drainq.com/api/software/one/`, `UPDATE_CHANNEL = beta`).

Der Weg hat neun Schritte; jede Zeile nennt ihre Quelle.

| # | Schritt | Wer / Womit | Quelle |
|---|---|---|---|
| 1 | Bauen | CEO-Konsole: `assembleRelease --no-daemon`, Plattformschlüssel aus `ONE_PLATFORM_KEYSTORE`/`ONE_PLATFORM_PASS`, Passwort von Hand (siehe unten) | `tools/publish-one-release.ps1` |
| 2 | Release anlegen | Skript → `POST https://license.drainq.com/api/software/releases` mit `{product:"one", channel, version, versionCode, releaseNotes}`, Kopfzeile `X-DrainQ-ApiKey` aus `DRAINQ_PUBLISH_APIKEY` (Benutzer-Umgebungsvariable) | `tools/publish-one-release.ps1` („Release anlegen"); Portal: `SoftwareDistributionController.cs` |
| 3 | APK hochladen | Skript → `POST …/releases/{id}/artifacts` (Multipart `platform=android-apk`, `file`); **sha256 und Größe rechnet der Server** | `tools/publish-one-release.ps1` („APK hochladen"); Portal: `SoftwareDistributionController.cs` |
| 4 | Freigeben | Mensch im Portal `https://license.drainq.com/admin/releases`, Knopf „Freigeben" → `ApprovedByUserId`/`ApprovedAt`, Audit-Eintrag `ReleaseApproved`. Kanal `beta`: Ersteller darf selbst freigeben. Kanal `stable`: Zweit-Admin (laut Welle `portal-freigabe-4augen`) | Portal: `AdminReleases.razor` |
| 5 | Veröffentlichen | Mensch im Portal, Knopf „Veröffentlichen" → `IsPublished=true`, `PublishedAt`, Audit `ReleasePublished`. Vorbedingungen (fail-closed): ≥ 1 Artefakt, alle mit sha256, freigegeben. Die Oberfläche meldet, ob die Fassung `latest` wird | Portal: `AdminReleases.razor` |
| 6 | Wer setzt `latest`? | **Niemand.** `latest` = höchster `versionCode` aller veröffentlichten Releases des Kanals, live berechnet | Portal: `SoftwareDistributionController.cs` (`LatestPublishedAsync`) |
| 7 | Wo liegt die APK? | Server-Storage, `StoredPath` relativ zur Storage-Basis, nicht öffentlich; Auslieferung nur über `GET /api/software/download/{artifactId}` und nur wenn `IsPublished` | Portal: `Releases.cs`, `SoftwareDistributionController.cs` |
| 8 | Manifest | `GET https://license.drainq.com/api/software/one/releases.beta.json` — Format siehe unten | Portal: `SoftwareDistributionController.cs` |
| 9 | Zurücknehmen | **Im Portal nicht möglich** — siehe Abschnitt „Eine Veröffentlichung lässt sich nicht zurückziehen" | Portal: `AdminReleases.razor` („Zurückziehen derzeit nicht möglich") |

## Aufruf (Skript)

```powershell
cd C:\Projekte\drainq.one
$env:ONE_PLATFORM_KEYSTORE = 'C:\...\bominwellalias.keystore'
$env:ONE_PLATFORM_PASS     = '<von Hand, nirgends gespeichert>'
.\tools\publish-one-release.ps1 -VersionName 0.9.2 -VersionCode 902 -Notes "..."
```

Das Skript baut den **Release-Bautyp, plattformsigniert** (`assembleRelease --no-daemon`),
bricht fail-closed ab, wenn `ONE_PLATFORM_KEYSTORE`/`ONE_PLATFORM_PASS` fehlen (ein Bau ohne
Plattformschlüssel wird mit dem Debug-Schlüssel signiert und ist auf dem Gerät wegen
`sharedUserId="android.uid.system"` nicht installierbar, ADR-0005), legt den Release im Portal
als **Entwurf** an und lädt die APK hoch. Danach: Schritte 4+5 im Portal durch einen Menschen.

## Manifest-Format, wie es das Gerät liest

```json
{"channel":"beta","latest":{"version":"0.9.2","versionCode":902,"minSdk":26,
 "url":"https://license.drainq.com/api/software/download/<artifactId>",
 "sha256":"<lowercase-hex, vom Server gerechnet>","size":<bytes>,
 "releasedAt":"2026-09-05","notes":"…","mandatory":false},"history":[]}
```

`minSdk` ist fest 26, `mandatory` fest `false`, `history` immer leer
(`SoftwareDistributionController.cs`). Die App aktualisiert nur, wenn
`latest.versionCode > BuildConfig.VERSION_CODE` und `minSdk <= SDK_INT`
(`HttpUpdateService.kt`), prüft den sha256 nach dem Download und installiert über eine
`PackageInstaller`-Session mit System-Bestätigungsdialog.

## ⚠ Eine Veröffentlichung lässt sich nicht zurückziehen

Das Portal kennt weder einen Zurückziehen-Knopf noch einen Unpublish-Endpunkt
(`AdminReleases.razor`: „Zurückziehen derzeit nicht möglich"; Controller ohne Delete/Put).
**Der einzige Rückweg, der ein Feldgerät erreicht, ist eine höhere Nummer mit dem alten
Stand** (z. B. `versionCode 903` = Rückbau auf den letzten guten Commit), und selbst die
erreicht nur Geräte, die danach „Nach Updates suchen". Der ausführliche Rückweg steht in
`docs/UPDATE_OPS_GUIDE.md`, Abschnitt „Rückweg / Was tun, wenn eine Fassung schlecht ist".

**Konsequenz vor jedem Klick auf „Veröffentlichen":** Der Bau muss vorher abgenommen sein
(Update-Lauf auf einem Testgerät), und der Commit-Hash des letzten guten Stands muss bekannt
sein — er ist die einzige Rückfallposition.

## Voraussetzungen am Gerät

- Plattformsignatur (die App läuft mit `sharedUserId="android.uid.system"`, ADR-0005).
- Die Installation ist **non-silent**: der Nutzer bestätigt den System-Dialog
  (`STATUS_PENDING_USER_ACTION`, `UpdateInstallReceiver.kt`).

## Beta-Channel

Der Kanal steht fest auf `beta` (`UPDATE_CHANNEL`, `app/build.gradle.kts:62`). Das Manifest
des Kanals heißt `releases.beta.json`.
