# Release-Runbook — DrainQ.ONE Beta mit Louis-Fixes (Portal/Hetzner)

**Ziel:** Die Version mit den Louis-Feedback-Fixes (`feature/louis-feedback`) als **Beta** über das DrainQ-Portal verteilen, damit die ONE sie per Self-Update zieht.
**Stand:** vorbereitet 2026-06-13 · **Quelle der Fixes:** `RESULT_LOUIS_FEEDBACK.md`

---

## Wichtig zuerst — Reihenfolge

1. **Merge-Gate offen:** Die On-Device-Abnahme (W1 Dialoge inkl. Soft-Tastatur, W3 Kabel mehrfach) steht noch aus, weil das Gerät im CC-Lauf nicht erreichbar war. **Empfehlung: erst Abnahme, dann Beta veröffentlichen** — sonst geht ungetesteter Stand an die Tester.
2. Branch `feature/louis-feedback` ist gepusht; für den Release entweder direkt von dort bauen oder vorher nach master mergen + taggen.

---

## Wie die App das Update findet (Ist-Stand, verifiziert)

`build.gradle.kts` → Updates laufen über das **Portal**, nicht mehr über GitHub:
- `UPDATE_PROXY_URL = https://license.drainq.com/api/software/one/`
- `UPDATE_CHANNEL = beta`
- Die App lädt: `https://license.drainq.com/api/software/one/releases.beta.json` (`UpdateConfig.manifestUrl = proxyUrl + "releases.$channel.json"`).
- Update-Vergleich: `manifest.latest.versionCode > BuildConfig.VERSION_CODE`.
- versionCode-Schema (CEO 07.06.): `MAJOR*10000 + MINOR*100 + PATCH`. Aktuell installiert: **401 / „0.4.1"**.

`releases.beta.json`-Struktur (aus `update/UpdateModels.kt`):
```json
{
  "channel": "beta",
  "latest": {
    "version": "0.4.2",
    "versionCode": 402,
    "minSdk": 26,
    "url": "<vom Portal ausgelieferte APK-URL>",
    "sha256": "<lowercase-hex der APK>",
    "size": <APK-Größe in Bytes>,
    "releasedAt": "2026-06-13",
    "notes": "Louis-Feedback: Systemleiste, Bedienleiste sichtbar, Meterzähler stabil, größere Reiter, schlankere Navigation.",
    "mandatory": false
  },
  "history": [
    { "version": "0.4.1", "versionCode": 401, "releasedAt": "2026-06-07" }
  ]
}
```

---

## Schritte (lokal auf dem Build-Rechner)

**1. Version setzen** — kein Datei-Edit nötig, `build.gradle.kts` liest Umgebungsvariablen:
```powershell
$env:APP_VERSION_CODE="402"; $env:APP_VERSION_NAME="0.4.2"
```
(Vorschlag: Patch 0.4.1 → 0.4.2 / 402. Falls anders gewünscht, hier ändern.)

**2. Signierte Release-APK bauen** (Keystore liegt im Repo-Root: `oneapp-release.keystore`):
```powershell
$env:JAVA_HOME="C:\Android\jdk17"
$env:KEYSTORE_PATH="C:\Projekte\drainq.one\oneapp-release.keystore"
$env:KEYSTORE_PASSWORD="<aus Passwortspeicher>"; $env:KEY_ALIAS="<alias>"; $env:KEY_PASSWORD="<aus Passwortspeicher>"
cd C:\Projekte\drainq.one; .\gradlew.bat assembleRelease
```
APK: `app/build/outputs/apk/release/app-release.apk`. **Passwörter nicht ins Repo/Doc schreiben — nur als Env.**

**3. sha256 + Größe ermitteln** (beides muss exakt ins Manifest):
```powershell
$apk="app/build/outputs/apk/release/app-release.apk"
(Get-FileHash $apk -Algorithm SHA256).Hash.ToLower()
(Get-Item $apk).Length
```

**4. Im DrainQ-Portal-Admin (drainq.web, läuft auf Hetzner) Release anlegen:**
- Produkt **one**, Channel **beta**, Version **0.4.2**, versionCode **402**, Notes wie oben.
- APK hochladen; sha256 + size eintragen; das Portal liefert daraus `…/api/software/one/releases.beta.json` aus.
- (Den genauen Admin-Klickpfad kennt das drainq.web-Projekt — dieser Schritt passiert dort.)

**5. Verifizieren:**
```powershell
curl.exe -sL https://license.drainq.com/api/software/one/releases.beta.json
# muss HTTP 200 + das JSON liefern (nicht 404), versionCode 402.
```

**6. Self-Update am Gerät testen:** Einstellungen → „Nach Updates suchen" → Update verfügbar → Installieren → System-Installdialog bestätigen → App auf 0.4.2.

---

## Was vorbereitet ist / was offen bleibt

- **Vorbereitet (hier):** Ablauf, Versions-Vorschlag (0.4.2/402), Build-/Signing-Kommandos, Manifest-Struktur, Verifikation.
- **Muss lokal/Portal laufen (kann ich von hier nicht):** APK bauen (Android-SDK), Portal-Admin-Upload (Login), Self-Update-Test am Gerät.
- **Vorgelagert:** On-Device-Abnahme der Louis-Fixes (Merge-Gate).

🔒 **KRITIS-Check:** RELEASE/RELEVANT — APK signiert ausliefern; Integrität über `sha256`/`size` im Manifest (App lehnt bei Abweichung ab — gewollt). Keystore-Passwörter ausschließlich als Umgebungsvariablen, niemals in Repo/Doc/Portal-Klartext. Auslieferung über TLS (`https://license.drainq.com`). Keine neuen Endpunkte/Dependencies.
