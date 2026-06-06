# Self-Update — Release veröffentlichen (B2, Ops)

Damit „Nach Updates suchen" in DrainQ.ONE ein Update findet **und** installiert, müssen zwei
Dinge stimmen: ein passend strukturiertes **GitHub-Release** (dieser Doc) und der **Installer-
Status-Receiver** (B3, bereits im Code). Ohne Release liefert die Manifest-URL HTTP 404 und der
Check meldet jetzt ehrlich „Update-Dienst nicht erreichbar oder nicht konfiguriert" (M15).

## Wie die App das Update findet

`build.gradle.kts` setzt `UPDATE_PROXY_URL = https://github.com/ThomasViell/one-app/releases/latest/download/`.
Die App lädt daraus `releases.<channel>.json` (Default-Channel `stable`):

```
https://github.com/ThomasViell/one-app/releases/latest/download/releases.stable.json
```

`releases/latest/download/<asset>` liefert das gleichnamige Asset des als **latest** markierten
Releases. Es braucht also ein Release mit **genau diesem Asset-Namen** + der darin referenzierten APK.

## Schritt für Schritt (pro Release)

**1. Release-APK bauen** (signiert, Release-Variante):
```powershell
$env:JAVA_HOME="C:\Android\jdk17"; C:\Projekte\drainq.one\gradlew.bat assembleRelease
```
APK: `app/build/outputs/apk/release/app-release.apk`.

**2. versionCode hochzählen.** In `app/build.gradle.kts` `versionCode` strikt **größer** als der
installierte Stand setzen (aktuell `3`). Die App vergleicht `manifest.latest.versionCode > BuildConfig.VERSION_CODE`.

**3. sha256 + size der APK ermitteln** (beides kommt ins Manifest):
```powershell
$apk = "app/build/outputs/apk/release/app-release.apk"
(Get-FileHash $apk -Algorithm SHA256).Hash.ToLower()
(Get-Item $apk).Length   # size in Bytes
```

**4. `releases.stable.json` erstellen** (Struktur = `ReleaseManifest`/`ReleaseInfo` aus `update/UpdateModels.kt`):
```json
{
  "channel": "stable",
  "latest": {
    "version": "0.4.0",
    "versionCode": 4,
    "minSdk": 26,
    "url": "https://github.com/ThomasViell/one-app/releases/download/v0.4.0/app-release.apk",
    "sha256": "<lowercase-hex-aus-Schritt-3>",
    "size": <bytes-aus-Schritt-3>,
    "releasedAt": "2026-06-06",
    "notes": "BETA-Welle 1: Kiosk, Export, Self-Update, Sonde-Frequenz, Migrationen.",
    "mandatory": false
  },
  "history": [
    { "version": "0.3.0", "versionCode": 3, "releasedAt": "2026-06-04" }
  ]
}
```
Wichtig: `url` zeigt auf die APK **dieses** Releases (Tag-Pfad `releases/download/<tag>/...`),
`sha256`/`size` müssen exakt zur hochgeladenen APK passen (sonst lehnt die App mit
Integritätsfehler ab — gewollt).

**5. GitHub-Release anlegen** (Repo `ThomasViell/one-app`):
```bash
gh release create v0.4.0 \
  app/build/outputs/apk/release/app-release.apk \
  releases.stable.json \
  --title "DrainQ.ONE 0.4.0" --notes "BETA-Welle 1" --latest
```
- `--latest` ist zwingend (sonst greift `releases/latest/download/` nicht).
- Beide Assets (`app-release.apk` + `releases.stable.json`) müssen am Release hängen.
- Kein Pre-Release (Pre-Releases werden von `latest` nicht berücksichtigt).

**6. Verifizieren:**
```powershell
curl.exe -sL https://github.com/ThomasViell/one-app/releases/latest/download/releases.stable.json
# muss das JSON liefern (HTTP 200), nicht 404.
```
Dann in der App: Einstellungen → „Nach Updates suchen" → Update verfügbar → Installieren →
**System-Installdialog erscheint** (Receiver B3) → bestätigen → App aktualisiert.

## Beta-Channel (optional)

Das 7-Tap-Easter-Egg in den Einstellungen schaltet den Channel `beta`. Dann lädt die App
`releases.beta.json`. Für Beta-Tester ein zweites Asset `releases.beta.json` (höhere/Vorab-
versionCode) ans selbe oder ein dediziertes Release hängen.

## Voraussetzungen am Gerät

- `REQUEST_INSTALL_PACKAGES` ist im Manifest (vorhanden). Die Installation ist **non-silent**:
  der Nutzer bestätigt den System-Dialog. Auf der als Device-Owner provisionierten ONE kann
  später optional eine Silent-Install-Policy ergänzt werden (separater Ausbau, nicht Teil dieser Welle).
