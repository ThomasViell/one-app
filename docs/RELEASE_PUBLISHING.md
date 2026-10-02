# Release veröffentlichen — DrainQ-Portal (Ops)

Stand 01.10.2026. Der frühere GitHub-Weg (GitHub-Release-Assets als Download-Quelle) gilt
seit dem 05.09.2026 nicht mehr (CEO-Entscheid; der Portalweg steht seit dem CEO-Beschluss
07.06.2026 im Code, `app/build.gradle.kts`, `productFlavors`: `UPDATE_PROXY_URL` je Paket,
`UPDATE_CHANNEL = beta`).

## Zwei Pakete aus einem Bau (CEO 01.10.2026, ADR-0005 Abschnitt 10)

| | Paket ONE | Paket Tablet |
|---|---|---|
| Gerät | ONE-Anlage (Master) | handelsübliches Android-Tablet (Slave) |
| `sharedUserId` | `android.uid.system` | keins |
| Schlüssel | Plattformschlüssel, `ONE_PLATFORM_KEYSTORE` / `ONE_PLATFORM_PASS` | Tablet-Schlüssel, `ONE_TABLET_KEYSTORE` / `ONE_TABLET_PASS` / `ONE_TABLET_ALIAS` |
| Gradle-Task | `:app:assembleOneRelease` | `:app:assembleTabletRelease` |
| APK | `app\build\outputs\apk\one\release\DrainQ-ONE_<V>_<C>_platform.apk` | `app\build\outputs\apk\tablet\release\DrainQ-ONE_<V>_<C>_tablet.apk` |
| Portal-Produkt | `one` | `one-tablet` |
| Manifest-URL | `https://license.drainq.com/api/software/one/releases.beta.json` | `https://license.drainq.com/api/software/one-tablet/releases.beta.json` |
| Skript | `publish-one-release.ps1` (ohne `-Variante` oder `-Variante one`) | `publish-one-release.ps1 -Variante tablet` |
| Werkseinrichtung / Partner-ZIP | ja | nein |

`assembleRelease` baut beide Pakete und braucht dafür **beide** Schlüssel; ohne alle drei
`ONE_TABLET_*` bricht der Tablet-Teil mit Klartext ab („Release-Bau des Tablet-Pakets
abgebrochen: fehlende Umgebungsvariable(n) …“). `assembleDebug` baut beide Pakete mit dem
Debug-Schlüssel (`…_platform-debug.apk`, `…_tablet-debug.apk`) — kein Auslieferungsweg.
Gleiche `applicationId`, gleiche Version, gleicher Code; Übersetzungen kommen für beide aus
Produkt `one`.

Der Weg hat neun Schritte; jede Zeile nennt ihre Quelle. `<produkt>` ist `one` bzw. `one-tablet`.

| # | Schritt | Wer / Womit | Quelle |
|---|---|---|---|
| 1 | Bauen | CEO-Konsole: Skript baut `:app:assembleOneRelease` bzw. `:app:assembleTabletRelease` (`--no-daemon`), Schlüssel aus `ONE_PLATFORM_*` bzw. `ONE_TABLET_*`, Passwort von Hand (siehe unten) | `tools/publish-one-release.ps1` |
| 2 | Release anlegen | Skript → `POST https://license.drainq.com/api/software/releases` mit `{product:"<produkt>", channel, version, versionCode, releaseNotes}`, Kopfzeile `X-DrainQ-ApiKey` aus `DRAINQ_PUBLISH_APIKEY` (Benutzer-Umgebungsvariable). Das Portal nimmt jedes Produkt als freien Text an; die Auswahlliste der Admin-Seite kennt `one-tablet` nicht, angelegt wird es nur über das Skript | `tools/publish-one-release.ps1` („Release anlegen"); Portal: `SoftwareDistributionController.cs` |
| 3 | APK hochladen | Skript → `POST …/releases/{id}/artifacts` (Multipart `platform=android-apk`, `file`); **sha256 und Größe rechnet der Server** | `tools/publish-one-release.ps1` („APK hochladen"); Portal: `SoftwareDistributionController.cs` |
| 4 | Freigeben | Mensch im Portal `https://license.drainq.com/admin/releases`, Knopf „Freigeben" → `ApprovedByUserId`/`ApprovedAt`, Audit-Eintrag `ReleaseApproved`. Freigeben und Veröffentlichen sind zwei Klicks desselben Admins. Eine Trennung nach Kanal (Zweit-Admin für `stable`) ist **nicht gebaut** — Stand 06.09.2026, gemessen in `AdminReleases.razor`, Methode `Freigeben` — Portal-Commit `3c65926`. Die Welle `portal-freigabe-4augen` ist offen. | Portal: `AdminReleases.razor` |
| 5 | Veröffentlichen | Mensch im Portal, Knopf „Veröffentlichen" → `IsPublished=true`, `PublishedAt`, Audit `ReleasePublished`. Vorbedingungen (fail-closed): ≥ 1 Artefakt, alle mit sha256, freigegeben. Die Oberfläche meldet, ob die Fassung `latest` wird | Portal: `AdminReleases.razor` |
| 6 | Wer setzt `latest`? | **Niemand.** `latest` = höchster `versionCode` aller veröffentlichten Releases des Kanals, live berechnet | Portal: `SoftwareDistributionController.cs` (`LatestPublishedAsync`) |
| 7 | Wo liegt die APK? | Server-Storage, `StoredPath` relativ zur Storage-Basis, nicht öffentlich; Auslieferung nur über `GET /api/software/download/{artifactId}` und nur wenn `IsPublished` | Portal: `Releases.cs`, `SoftwareDistributionController.cs` |
| 8 | Manifest | `GET https://license.drainq.com/api/software/<produkt>/releases.beta.json` — Format siehe unten. Solange unter `one-tablet` nichts veröffentlicht ist, antwortet das Portal mit 404 („kein veröffentlichtes Release“) | Portal: `SoftwareDistributionController.cs` |
| 9 | Zurücknehmen | **Im Portal nicht möglich** — siehe Abschnitt „Eine Veröffentlichung lässt sich nicht zurückziehen" | Portal: `AdminReleases.razor` („Zurückziehen derzeit nicht möglich") |

## Voraussetzungen zum Ausführen

- **PowerShell 7** (`pwsh`) — das Skript braucht `Invoke-RestMethod -Form` für den
  Multipart-Upload. Wird es unter Windows PowerShell 5.1 gestartet, startet es sich selbst
  unter `pwsh` neu; das setzt voraus, dass `pwsh` installiert ist (`winget install --id
  Microsoft.PowerShell`). Solange N-3a (Nicht-ASCII-Zeichen brechen 5.1) nicht in jedem Skript
  im Repo behoben ist, gilt das auch als Empfehlung für alle anderen `.ps1`-Aufrufe hier.
- Das **Docs-Gate** läuft vor dem Upload (siehe unten) und braucht `APP_VERSION_CODE`/
  `APP_VERSION_NAME` als Umgebungsvariablen — das Skript setzt beide selbst, auch bei
  `-SkipBuild`.

## Aufruf (Skript)

ONE-Paket (wie bisher, ohne `-Variante`):

```powershell
cd C:\Projekte\drainq.one
$env:ONE_PLATFORM_KEYSTORE = 'C:\...\bominwellalias.keystore'
$env:ONE_PLATFORM_PASS     = '<von Hand, nirgends gespeichert>'
.\tools\publish-one-release.ps1 -VersionName 0.9.2 -VersionCode 902 -Notes "..."
```

Tablet-Paket:

```powershell
cd C:\Projekte\drainq.one
$env:ONE_TABLET_KEYSTORE = 'C:\...\<Tablet-Keystore>'
$env:ONE_TABLET_PASS     = '<von Hand, nirgends gespeichert>'
$env:ONE_TABLET_ALIAS    = '<Alias>'
.\tools\publish-one-release.ps1 -Variante tablet -VersionName 0.9.7 -VersionCode 907 -Notes "..."
```

Probe vorab, ohne Bau und ohne Portal-Kontakt (gibt den Plan aus, nur Namen, nie Werte):

```powershell
.\tools\publish-one-release.ps1 -Variante tablet -VersionName 0.9.7 -VersionCode 907 -Trockenlauf
```

Das Skript baut den **Release-Bautyp der gewählten Variante** (`:app:assembleOneRelease` bzw.
`:app:assembleTabletRelease`, `--no-daemon`) und bricht fail-closed ab, wenn die
Schlüsselvariablen fehlen: ONE ohne `ONE_PLATFORM_KEYSTORE`/`ONE_PLATFORM_PASS` (ein Bau ohne
Plattformschlüssel wird mit dem Debug-Schlüssel signiert und ist auf der ONE wegen
`sharedUserId="android.uid.system"` nicht installierbar, ADR-0005), Tablet ohne **eine** der
drei `ONE_TABLET_*` (die Meldung nennt die fehlenden Namen). Es legt den Release im Portal als
**Entwurf** an und lädt die APK hoch. Danach: Schritte 4 **und** 5 im Portal durch einen
Menschen — erst nach „Veröffentlichen“ findet ein Gerät das Update.

`-SkipBuild` nimmt die vorhandene APK der Variante und prüft ihr Zertifikat: ONE gegen den
festen Plattform-Fingerabdruck, Tablet gegen den SHA-256 des Zertifikats unter
`ONE_TABLET_ALIAS` im Tablet-Keystore (`keytool -list -v … -storepass:env ONE_TABLET_PASS`,
`keytool` aus `JAVA_HOME\bin`, sonst `PATH`). Der Tablet-Schlüssel muss **RSA** sein und
v1-signieren — `Get-ApkSignatureFingerprint` liest nur `META-INF/*.RSA|*.DSA`; ein EC-Schlüssel
lässt die Probe fail-closed scheitern. Die Kopie im Repo-Wurzelordner heißt
`DrainQ-ONE_<V>-<Kanal>_<C>.apk` (ONE) bzw. `DrainQ-ONE_<V>-<Kanal>_<C>_tablet.apk` (Tablet).
Ein Auslieferungs-ZIP (`tools\werkseinrichtung\dist`) entsteht nur beim ONE-Paket.

Der `versionName` besteht nur aus Ziffern und Punkten (z. B. `0.9.7`), sonst nimmt die
Werkseinrichtung die ONE-Datei nicht an (der Bau warnt).

## Docs-Gate (W-H5)

Vor dem Upload laufen automatisch vier Prüfungen (`-SkipDocs` überspringt sie — nur für
Notfälle, nicht für Routine-Releases): `HelpCoverageTest` (Kotlin-Unit-Test über
`:app:testOneDebugUnitTest` — die Oberfläche ist für beide Pakete gleich, ein Golden-Satz;
`verifyPaparazziDebug`/`recordPaparazziDebug` sind Weichen auf die ONE-Variante), Golden-Diff
(`tools\manual\verify.ps1`), Render aller Portal-Sprachen (`tools\manual\render.ps1`) und
PDF-Erzeugung je Sprache (`tools\manual\generate.js`). Bricht eine der vier Prüfungen ab,
bricht der Release ab, bevor irgendetwas im Portal angelegt wird. Ziel: unter 600 Sekunden
gesamt.

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

- ONE: Plattformsignatur (das ONE-Paket läuft mit `sharedUserId="android.uid.system"`, ADR-0005).
- Tablet: das Tablet-Paket, signiert mit dem Tablet-Schlüssel. Ein Update findet nur, wer
  bereits ein Paket mit **demselben** Schlüssel trägt; ein Wechsel zwischen ONE- und
  Tablet-Paket oder von einer Altsignatur (z. B. `0.4.3-lohs-test`) geht nur über
  Deinstallation (siehe `docs/UPDATE_OPS_GUIDE.md`, Diagnose).
- Die Installation ist **non-silent**: der Nutzer bestätigt den System-Dialog
  (`STATUS_PENDING_USER_ACTION`, `UpdateInstallReceiver.kt`).

## Beta-Channel

Der Kanal steht für beide Pakete fest auf `beta` (`UPDATE_CHANNEL`, `app/build.gradle.kts`,
`defaultConfig`). Das Manifest des Kanals heißt `releases.beta.json`.
