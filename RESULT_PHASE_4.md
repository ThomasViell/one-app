# RESULT PHASE 4 — GitHub Actions Release-Workflow (Update-Prozess)

**Datum:** 2026-05-12
**Branch:** `feature/update-phase-4`
**Status:** abgeschlossen

---

## Branch + Commits

```
Branch: feature/update-phase-4 (aus master)
Commit: [siehe git log nach diesem Dokument]
```

---

## Liste aller neuen und geänderten Dateien

| Datei | Status |
|---|---|
| `.github/workflows/release-apk.yml` | neu |
| `scripts/generate-release-manifest.py` | neu |
| `CHANGELOG.md` | neu |
| `app/build.gradle.kts` | geändert |
| `RESULT_PHASE_4.md` | geändert (OSD-Inhalt ersetzt durch Update-Prozess) |

---

## Diff-Summary pro Datei

**`.github/workflows/release-apk.yml`** — Vollständiger GitHub Actions Release-Workflow. Trigger: `push: tags: v*.*.*`. Steps: Checkout (fetch-depth 0), JDK 17 temurin, Gradle-Cache (`actions/cache@v4`), Secret-Masking (`::add-mask::`), Keystore-Decode aus `DRAINQ_ONE_KEYSTORE_BASE64` → `$RUNNER_TEMP/release.keystore`, Version aus Tag extrahieren (MARKER_VERSIONCODE: FROM_TAG → `MAJOR*10000+MINOR*100+PATCH`), `assembleRelease` mit ENV-Signing-Vars, APK umbenennen zu `drainq-one-<version>.apk`, SHA256 berechnen + `.sha256`-Datei schreiben, `generate-release-manifest.py` aufrufen, `softprops/action-gh-release@v2` mit allen drei Assets.

**`scripts/generate-release-manifest.py`** — Python 3 CLI-Skript für Manifest-Erzeugung. Liest `CHANGELOG.md`, extrahiert Notizen-Block für die aktuelle Version via Regex, berechnet APK-Dateigröße, schreibt `releases.stable.json` gemäß Schema aus `UPDATE_PROCESS_CONCEPT.md`. Rollt bestehende `latest`-Einträge automatisch in `history`. CLI-Argumente: `--version`, `--version-code`, `--sha256`, `--apk-path`, optional `--output`, `--channel`, `--mandatory`.

**`CHANGELOG.md`** — Neu angelegt (Datei existierte nicht). Enthält Einträge für v0.3.0 (Update-Modul + OSD-Cleanup), v0.2.0 (Maps/Hardware-OSD), v0.1.0 (Erstrelease). Wird vom Python-Skript für Release-Notes ausgelesen.

**`app/build.gradle.kts`** — Vier Änderungsbereiche:
1. `versionCode`/`versionName` lesen jetzt aus ENV `APP_VERSION_CODE`/`APP_VERSION_NAME` (Fallback: 3 / "0.3.0").
2. Neuer `signingConfigs.release`-Block: liest KEYSTORE_PATH, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD aus ENV — kein Keystore-File im Repo.
3. `buildTypes.release.signingConfig`: `if (KEYSTORE_PATH ENV gesetzt) → signingConfigs.release else → signingConfigs.debug` (Dev-Builds unberührt).
4. `buildConfigField`-Zeilen für UPDATE_MODE/UPDATE_PROXY_URL/UPDATE_CHANNEL + OkHttp-Dependency aus Phase 2 ergänzt (waren auf master-Branch noch nicht gemergt).

---

## Compile- und Test-Ergebnisse

### ./gradlew assembleDebug

```
w: LocalizationManager.kt:10552: Variable 'lang' is never used   [pre-existing]
w: SettingsScreen.kt:394/656/793/918/971/1154: Divider deprecated → HorizontalDivider  [pre-existing]

> Task :app:assembleDebug

BUILD SUCCESSFUL in 37s
40 actionable tasks: 17 executed, 23 up-to-date
```

### ./gradlew testDebugUnitTest

```
BUILD SUCCESSFUL in 11s
31 actionable tasks: 8 executed, 23 up-to-date
```

### Python-Skript Syntaxcheck

```
$ python scripts/generate-release-manifest.py
usage: generate-release-manifest.py [-h] --version VERSION --version-code ...
generate-release-manifest.py: error: the following arguments are required: --version, --version-code, --sha256, --apk-path
```
Skript lädt korrekt, argparse zeigt erwarteten Fehler bei fehlenden Pflichtargumenten.

### YAML-Validation

`pyyaml` lokal nicht installiert. Workflow-Struktur manuell verifiziert (Standard-GitHub-Actions-Format, alle Action-Versionen aktuell: `actions/checkout@v4`, `actions/setup-java@v4`, `actions/cache@v4`, `softprops/action-gh-release@v2`).

---

## Workflow-Anleitung: Erst-Tag-Push

### Voraussetzungen (manuell, vor erstem Release-Tag)

GitHub Secrets im Repo `ThomasViell/one-app` → Settings → Secrets and variables → Actions:

| Secret-Name | Wert |
|---|---|
| `DRAINQ_ONE_KEYSTORE_BASE64` | `base64 -w 0 oneapp-release.keystore` |
| `DRAINQ_ONE_KEYSTORE_PASSWORD` | Store-Passwort |
| `DRAINQ_ONE_KEY_ALIAS` | Key-Alias (z. B. `drainq-one`) |
| `DRAINQ_ONE_KEY_PASSWORD` | Key-Passwort |

Keystore erzeugen (falls noch nicht vorhanden):
```bash
keytool -genkey -v -keystore oneapp-release.keystore \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias drainq-one
base64 -w 0 oneapp-release.keystore
# → in DRAINQ_ONE_KEYSTORE_BASE64 Secret eintragen
```

### Tag setzen und pushen

```bash
git tag v0.4.0
git push origin v0.4.0
```

GitHub Actions läuft automatisch → Release unter `ThomasViell/one-app/releases` mit:
- `drainq-one-0.4.0.apk`
- `drainq-one-0.4.0.apk.sha256`
- `releases.stable.json`

versionCode-Berechnung: v0.4.0 → 0×10000 + 4×100 + 0 = **400**

### Lokaler Release-Build testen (ohne CI)

```powershell
$env:KEYSTORE_PATH     = "C:\Pfad\zu\oneapp-release.keystore"
$env:KEYSTORE_PASSWORD = "..."
$env:KEY_ALIAS         = "drainq-one"
$env:KEY_PASSWORD      = "..."
$env:APP_VERSION_NAME  = "0.4.0"
$env:APP_VERSION_CODE  = "400"
.\gradlew assembleRelease
```

---

## Fortgepflanzte Marker

```
MARKER_HOSTING:      SUBPATH    → unverändert
MARKER_MANDATORY:    NO         → unverändert
MARKER_CHANNEL_UI:   HIDDEN     → unverändert
MARKER_VERSIONCODE:  FROM_TAG   → IMPLEMENTIERT: Workflow extrahiert v-Tag → MAJOR*10000+MINOR*100+PATCH
MARKER_AUTOCHECK:    DAILY_WIFI → unverändert
MARKER_CERT_PINNING: OFF        → unverändert
```

---

## Pragmatische Entscheidungen

1. **`setup-android@v3` weggelassen**: Ubuntu-latest auf GitHub Actions hat Android SDK bereits vorinstalliert. Zusätzlicher Setup-Step würde ~3 min kosten ohne Mehrwert.

2. **`isMinifyEnabled = false`** bleibt unverändert. ProGuard-Aktivierung betrifft iText7, Compose-Reflection, Koin — eigene Entscheidung außerhalb Phase 4.

3. **`generate_release_notes: false`** in `action-gh-release`: Verhindert automatische GitHub-Release-Notes mit Commit-Hashes. Stattdessen aufbereitete Notes aus CHANGELOG.md über das Python-Skript.

4. **RESULT_PHASE_4.md überschrieben**: Datei enthielt OSD-Phase-4-Inhalt. OSD-Phasen sind vollständig in master gemergt (HANDOVER.md Stand 2026-05-12), der OSD-Inhalt ist in git-History erhalten. Update-Phasenplan verlangt explizit `RESULT_PHASE_4.md` als Output.

5. **`drainq-kritis-compliance`-Skill**: Skill-Datei nicht im Skill-Verzeichnis gefunden (`C:\Users\t.viell\.claude\skills\`). Phase 4 berührt keine App-seitigen Netzwerk- oder Permission-Pfade — nur CI/CD-Build. Sicherheitsrelevant umgesetzte Maßnahmen: kein Keystore/Password im Code, Secrets ausschließlich via GitHub ENV, Masking via `::add-mask::`, Keystore-Datei nur in `$RUNNER_TEMP` (ephemeral, nicht in Artifacts). Vollständiger KRITIS-Check folgt Phase 6.

---

## Bekannte Issues und TODOs für Folge-Phasen

- **Phase 5:** Hetzner-Mirror-Skript, Systemd-Service/-Timer, Nginx-Snippet, DEPLOYMENT.md.
- **Phase 6:** `InstallStatusReceiver` für `ACTION_INSTALL_STATUS` nicht registriert. Audit-Log-Entity, KRITIS-Doku, Integrationstests. APK-Cleanup in `cacheDir/updates/`.
- **Phase 7:** `update_*`-Localization-Keys fehlen in 34 Sprachen (nur DE-Fallback aktiv).
- **Keystore-Rotation:** Kein automatischer Ablauf-Check; Backup-Anforderung aus ADR manuell via 1Password/Vault umzusetzen.
- **versionCode v0.x.x**: Bei v1.0.0 springt versionCode auf 10000 (Lücke von 400 auf 10000). Das ist per ADR so gewollt (deterministisch aus Tag).
