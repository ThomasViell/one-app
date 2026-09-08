# DrainQ.ONE — Update-Operations-Guide

**Version:** 0.9.2
**Stand:** 2026-09-05
**Zielgruppe:** Ops-Team, Release-Manager, Support-Techniker
**Referenzen:** `ADR 0001` (Nachtrag 05.09.2026), `UPDATE_PROCESS_CONCEPT.md`, `RELEASE_PUBLISHING.md`

> **Wegwechsel 05.09.2026 (CEO-Entscheid):** Updates laufen über das DrainQ-Portal
> `license.drainq.com` — der frühere GitHub-Weg (Tag-Push → Actions → Release-Assets) ist
> veraltet und wird hier nicht mehr beschrieben. Der Portalweg steht seit dem CEO-Beschluss
> 07.06.2026 im Code (`UPDATE_PROXY_URL`, `app/build.gradle.kts:57-62`).

---

## Übersicht

Dieser Guide beschreibt:
1. **Voraussetzungen** — was vor dem ersten Portal-Release stehen muss
2. **Ablauf** — Bau → Skript → Portal (Freigeben/Veröffentlichen durch einen Menschen)
3. **Prüfung** — Manifest und Gerät verifizieren
4. **Rückweg** — was geht, was nicht geht
5. **Notfall-Sideload** — wenn die In-App-Update-Funktion ausfällt

---

## 1. Voraussetzungen

| Voraussetzung | Woher |
|---|---|
| PowerShell 7+ (`pwsh`) | Skript braucht `Invoke-RestMethod -Form` (Multipart) |
| API-Schlüssel `DRAINQ_PUBLISH_APIKEY` als Benutzer-Umgebungsvariable | einmalig: `[Environment]::SetEnvironmentVariable("DRAINQ_PUBLISH_APIKEY","<KEY>","User")`, danach neue pwsh öffnen |
| Plattformschlüssel-Datei `bominwellalias.keystore` lokal | Pfad in `ONE_PLATFORM_KEYSTORE`; Passwort wird **von Hand** in `ONE_PLATFORM_PASS` gesetzt, nirgends gespeichert |
| Admin-Konto im Portal | für Freigeben/Veröffentlichen unter `https://license.drainq.com/admin/releases` |
| Versionspaar (z. B. 0.9.2 / 902) höher als jeder je veröffentlichte `versionCode` | Portal-Schema: MAJOR*10000 + MINOR*100 + PATCH (`app/build.gradle.kts:38-40`) |

**Plattformschlüssel-Verlust = keine Updates mehr für bereits ausgelieferte Geräte.**
Backup und Passwort-Ablage wie bisher (Vault), siehe auch `docs/WERKSEINRICHTUNG.md`.

---

## 2. Ablauf (pro Release)

### 2.1 Bauen + Anlegen + Hochladen (Skript)

```powershell
cd C:\Projekte\drainq.one
$env:ONE_PLATFORM_KEYSTORE = 'C:\...\bominwellalias.keystore'
$env:ONE_PLATFORM_PASS     = '<von Hand>'
.\tools\publish-one-release.ps1 -VersionName 0.9.2 -VersionCode 902 -Notes "..."
```

Das Skript:
- bricht **fail-closed** ab, wenn `ONE_PLATFORM_KEYSTORE`/`ONE_PLATFORM_PASS` fehlen — ein
  Release-Bau ohne Plattformschlüssel wird mit dem Debug-Schlüssel signiert und ist auf dem
  Gerät (`sharedUserId="android.uid.system"`, ADR-0005) nicht installierbar;
- baut `assembleRelease --no-daemon` (plattformsigniert, `versionCode`/`versionName` aus den
  Parametern via `APP_VERSION_CODE`/`APP_VERSION_NAME`);
- läuft durch das Docs-Gate (HelpCoverageTest, Golden-Diff, Render, PDF) — `-SkipDocs` nur im
  Notfall und zu dokumentieren;
- legt den Release im Portal als **Entwurf** an (`POST /api/software/releases`) und lädt die
  APK hoch (`POST /api/software/releases/{id}/artifacts`); **sha256 und Größe rechnet der
  Server**, das Skript meldet beide zurück;
- endet mit `exit 0` und dem Hinweis „FERTIG … liegt im Portal als Entwurf".

### 2.2 Freigeben + Veröffentlichen (Mensch im Portal)

Unter `https://license.drainq.com/admin/releases`:
1. **Freigeben** → `ApprovedByUserId`/`ApprovedAt`, Audit `ReleaseApproved`.
   Freigeben und Veröffentlichen sind zwei Klicks desselben Admins. Eine Trennung nach Kanal
   (Zweit-Admin für `stable`) ist **nicht gebaut** — Stand 06.09.2026, gemessen in
   `AdminReleases.razor`, Methode `Freigeben` — Portal-Commit `3c65926`. Die Welle `portal-freigabe-4augen` ist offen.
2. **Veröffentlichen** → `IsPublished=true`, `PublishedAt`, Audit `ReleasePublished`.
   Vorbedingungen (fail-closed): ≥ 1 Artefakt, alle mit sha256, freigegeben. Die Oberfläche
   meldet, ob die Fassung `latest` wird.

`latest` setzt niemand von Hand: es ist der höchste veröffentlichte `versionCode` des Kanals,
live berechnet (`SoftwareDistributionController.cs`, `LatestPublishedAsync`).

---

## 3. Prüfung

### 3.1 Manifest

```powershell
curl.exe -si https://license.drainq.com/api/software/one/releases.beta.json
```

Erwartet: `latest.versionCode` = der soeben veröffentlichte Wert, `sha256`/`size` = die vom
Skript beim Upload gemeldeten Werte. Manifest-Felder: `minSdk` fest 26, `mandatory` fest
`false`, `history` leer (`SoftwareDistributionController.cs`).

### 3.2 Zertifikat der APK (vor dem Upload, bei Bedarf)

```powershell
. .\tools\werkseinrichtung\Get-ApkSignatureFingerprint.ps1
Get-ApkSignatureFingerprint -ApkPath .\app\build\outputs\apk\release\app-release.apk
# Soll: 2D:37:0C:21:F5:DF:D5:53:D2:A7:96:31:4B:70:92:5F:B3:8A:DE:EF:90:86:4C:92:0B:BB:BB:12:88:7D:35:22
# (Sollwert: tools/werkseinrichtung/Werkseinrichtung.ps1:54)
```

### 3.3 Gerät

Einstellungen → „Nach Updates suchen" → „Update verfügbar: <Version>" → Download →
System-Bestätigungsdialog → Installation. Danach per adb verifizieren:

```bash
adb shell dumpsys package com.uip.drainq.one | grep -E "versionCode|versionName"
```

Die App aktualisiert nur bei `latest.versionCode > BuildConfig.VERSION_CODE` und
`minSdk <= SDK_INT` (`HttpUpdateService.kt:67`); der sha256 wird nach dem Download geprüft
(`HttpUpdateService.kt:104-116`).

---

## 4. Rückweg / Was tun, wenn eine Fassung schlecht ist

**Eine veröffentlichte Fassung lässt sich im Portal nicht zurücknehmen.** Es gibt weder einen
Knopf (`AdminReleases.razor`: „Zurückziehen derzeit nicht möglich") noch einen Endpunkt (der
Software-Controller hat kein Delete/Put). Ein direkter Datenbank-Eingriff
(`IsPublished=false`) ist **nicht vorgesehen** und wäre eine CEO-Entscheidung — und selbst
dann blieben Geräte, die die Fassung schon haben, darauf stehen.

**Der einzige Rückweg, der ein Feldgerät erreicht: eine höhere Nummer mit dem alten Stand.**

Rezept (Rückbau):
1. Letzten guten Commit auschecken (Commit-Hash des letzten abgenommenen Stands — er gehört
   in jeden Release-Bericht, genau dafür).
2. Bauen mit höherer Nummer, z. B. `APP_VERSION_CODE=903`, `APP_VERSION_NAME=0.9.3-rueckbau`.
3. Plattformsignierter Release-Bau, Portalweg wie Abschnitt 2.
4. `latest` zeigt danach auf die Rückbau-Nummer (der Höchste gewinnt — gemessen 04.09.2026:
   ein veröffentlichter Datensatz mit niedrigerem Code änderte das Manifest nicht).

Was mit Geräten passiert:
- Ein Gerät **ohne** die schlechte Fassung zieht direkt den Rückbau.
- Ein Gerät **mit** der schlechten Fassung sieht die Rückbau-Nummer als höher und zieht den
  alten Stand als „Update".
- **Herabstufen über die App ist ausgeschlossen:** der Vergleich `latest.versionCode >
  VERSION_CODE` lässt nur höhere Nummern zu, und die Android-Paketverwaltung lehnt niedrigere
  `versionCode` ab (`INSTALL_FAILED_VERSION_DOWNGRADE`).

Datenbestand beim Rückbau: 901 und 902 schreiben dieselbe Room-Datenbankversion (in dieser
Welle ändert sich kein Schema) — ein Rückbau 902→901-Code ist gefahrlos. **Regel für künftige
Wellen:** ändert eine Welle das Room-Schema, ist der Rückbau nur noch mit Migration möglich;
das gehört dann in den Wellenplan, nicht in diesen Guide.

**Konsequenz fürs Ausrollen:** Jede Veröffentlichung ist endgültig. Vor dem Klick auf
„Veröffentlichen" muss der Bau auf einem Testgerät abgenommen sein (Update-Lauf + kurzer
Funktionsdurchgang) und der Rückbau-Commit bekannt sein.

---

## 5. Notfall-Sideload via ADB

Wenn die In-App-Update-Funktion ausfällt:

```powershell
# APK lokal bauen (plattformsigniert, siehe Abschnitt 1) oder aus dem Portal laden:
# GET /api/software/download/{artifactId} (artifactId aus dem Manifest-Feld "url")
adb install -r DrainQ-ONE_<version>_<code>_platform.apk
# Ausgabe: "Success"
```

`-r` ersetzt die installierte App. Eine **niedrigere** `versionCode` lehnt die
Paketverwaltung ab (`INSTALL_FAILED_VERSION_DOWNGRADE`) — Sideload ist also kein Rückweg.

---

## Update-Fehlerdiagnose

### Tablet zeigt immer „aktuell"

1. **Manifest prüfen** (siehe 3.1): steht dort wirklich die höhere Nummer?
2. **Kanal prüfen:** die App liest `releases.beta.json` (`UPDATE_CHANNEL=beta`).
3. **Internet-Verbindung des Tablets** sicherstellen (nicht im ONE-Hotspot):
   ```bash
   adb shell ping -c 3 license.drainq.com
   ```
4. **App-Logcat:**
   ```bash
   adb logcat | grep -i "Update\|PackageInstaller"
   ```

### Download-Fehler (sha256-Mismatch)

Der sha256 im Manifest rechnet der **Server** beim Upload. Stimmt er nicht mit der
heruntergeladenen Datei überein, lehnt die App mit Integritätsfehler ab (gewollt) — lokal
nachrechnen: `(Get-FileHash <apk> -Algorithm SHA256).Hash.ToLower()`. Bei Mismatch: Artefakt
im Portal prüfen, Download wiederholen.

---

## Troubleshooting-Checkliste

| Problem | Diagnose | Lösung |
|---|---|---|
| Manifest nicht erreichbar | `curl.exe -si …/releases.beta.json` | Portal down? Internet-Verbindung Tablet? |
| Skript bricht mit „ONE_PLATFORM_*" ab | Env-Variablen gesetzt? | Beide Variablen setzen (Abschnitt 1) — bewusst fail-closed |
| Skript 401 | ApiKey falsch | `DRAINQ_PUBLISH_APIKEY` prüfen (Portal: `ApiKeyOrAdminAuthAttribute.cs`, Schlüssel `DrainQCloud:ApiKey`) |
| Skript 409 | versionCode existiert schon | Nummer erhöhen; ein Datensatz ist nicht löschbar |
| „Veröffentlichen" verweigert | Portal-Meldung lesen | Vorbedingungen: ≥ 1 Artefakt, sha256, freigegeben |
| Tablet sieht kein Update | Manifest + Kanal + Internet (oben) | versionCode im Manifest > installiertem Stand? |
| Installation blockiert | `adb shell pm list packages \| grep drainq` | Signaturwechsel? Geräte nehmen nur plattformsignierte APK |

---

## Best Practices

### 1. Release-Notes finalisieren, bevor das Skript läuft
Sie werden als `releaseNotes` im Portal gespeichert und im Tablet-Update-Dialog angezeigt.

### 2. Jede Veröffentlichung vorher auf einem Testgerät abnehmen
Der Rückweg ist ein Neubau unter Zeitdruck (Abschnitt 4) — die Abnahme davor ist billiger.

### 3. DSGVO: Portal statt GitHub
Tablets fragen Manifest und APK bei `license.drainq.com` ab (eigener Server, eigener nginx).
Zugriffs-Logs und Retention des Portals sind **offen, zu klären** — sie gehen aus dem
Portal-Repo nicht hervor und dürfen hier nicht erfunden werden.

---

**Support-Kontakt:** t.viell@uip.team
**Dokumentversion:** 0.9.2 (2026-09-05, Portalweg)
