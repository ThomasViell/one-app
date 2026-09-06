# SZENARIEN — Kette portalweg (05.09.2026)

Erfolgskriterien aus `PLAN.md` (Ziele A–D) und der Freigabe (Abschnitt 10), je Szenario:
Vorbedingung, Handlung, Messbefehl, Erwartung. Geraet: `e27915a669970b5f`.
Diese Welle belegt **den Verteilweg**, nicht die App: Versionsnummer 902/0.9.2,
Release-Bau plattformsigniert, Portal-Dokumentation, Rueckweg beschrieben.

## Szenario 1 (A) — Versionsnummer 902/0.9.2

**1a Rueckfallwerte greifen**
- Vorbedingung: Zweig `welle/portalweg`, keine `APP_VERSION_*`-Variablen, kein `ONE_PLATFORM_*`.
- Handlung: `gradlew :app:assembleRelease --no-daemon`.
- Messbefehl: `aapt dump badging app/build/outputs/apk/release/app-release.apk | grep versionCode`.
- Erwartung: `versionCode='902' versionName='0.9.2'`; Warnung im Bau nennt 902/0.9.2; kein `application-debuggable`.

**1b Guard bleibt scharf**
- Vorbedingung: `ONE_PLATFORM_KEYSTORE`/`ONE_PLATFORM_PASS` gesetzt, `APP_VERSION_*` NICHT gesetzt.
- Handlung: `gradlew :app:assembleRelease`.
- Messbefehl: Exit-Code + Fehlermeldung.
- Erwartung: harter Abbruch, Klartext nennt die fehlenden Variablen (kein stiller Bau).

**1c Kein Test kennt 401**
- Messbefehl: `grep -rn "401\|0\.4\.1" app/src/test`.
- Erwartung: 0 relevante Treffer.

## Szenario 2 (B) — Release-Bau plattformsigniert

**2a Signatur**
- Vorbedingung: CEO-Bau mit `ONE_PLATFORM_*` (HALT 1).
- Messbefehl: `keytool -printcert -jarfile <apk>` UND `apksigner verify --print-certs`.
- Erwartung: SHA-256 = `2D:37:0C:21:…:35:22` (Sollwert `tools/werkseinrichtung/Werkseinrichtung.ps1:54`); v1+v2 verifiziert.

**2b R8 laeuft nicht**
- Messbefehl: `ls app/build/outputs/mapping/release/` + `grep isMinifyEnabled app/build.gradle.kts`.
- Erwartung: kein `mapping.txt`; `isMinifyEnabled = false` — die ProGuard-Regeln greifen nicht, weil R8 nicht laeuft (Frage B.1 des Auftrags).

**2c Kein Debug-Rest**
- Messbefehl: `aapt dump xmltree <apk> AndroidManifest.xml | grep -i "ScreenshotRig\|debuggable"`.
- Erwartung: 0 Treffer.

**2d Skript fail-closed**
- Vorbedingung: `ONE_PLATFORM_*` NICHT gesetzt.
- Handlung: `pwsh tools/publish-one-release.ps1 -VersionName 0.9.2 -VersionCode 902 -ApiKey dummy`.
- Messbefehl: Exit-Code + Ausgabe.
- Erwartung: Abbruch VOR Bau und Portal-Kontakt, Klartext, EXIT≠0.

## Szenario 3 (C) — Portalweg dokumentiert und gegangen

**3a Dokumentation ohne GitHub-Weg**
- Messbefehl: `grep -n "ThomasViell/one-app\|latest/download" docs/RELEASE_PUBLISHING.md docs/UPDATE_OPS_GUIDE.md docs/UPDATE_PROCESS_CONCEPT.md docs/kritis/update-process.md`.
- Erwartung: 0 Treffer. ADR-0001 Zeilen 1-123 bit-identisch zu `master`, nur datierter Nachtrag angehaengt.

**3b Veroeffentlichung (HALT 2, CEO)**
- Vorbedingung: Bau abgenommen (Szenario 2), Release als Entwurf im Portal.
- Handlung: CEO gibt frei und veroeffentlicht im Portal.
- Messbefehl: `curl.exe -si https://license.drainq.com/api/software/one/releases.beta.json` vorher/nachher.
- Erwartung vorher: `latest.versionCode=901`; nachher: `902`, `sha256`/`size` = Upload-Meldung des Skripts.

**3c Update-Lauf am Geraet**
- Vorbedingung: Geraet auf 901/0.9.1, Manifest zeigt 902.
- Handlung: Einstellungen → „Nach Updates suchen" → Download → Systemdialog → bestaetigen.
- Messbefehl: `dumpsys package com.uip.drainq.one | grep -E "versionCode|versionName"`; geraeteseitiger sha256; `dumpsys activity activities | grep topResumed`; `mLockTaskModeState`.
- Erwartung: 902/0.9.2 installiert, sha256 = gebaute Datei, `MainActivity` vorne, `LOCKED`.

**3d Gegenprobe Klickdurchgang (kein neuer Funktionsnachweis)**
- Handlung: Punkt-6-Tabelle aus der Welle kiosk-pflicht auf dem Release-Stand (`r6_helpers.sh`/`r6_check.py`): Aufnahme, Wachstum 2× ≥ 15 s, mp4 + moov, Neustart, `LOCKED`.
- Erwartung: dieselben Werte wie auf dem Debug-Stand. Faellt sie durch, ist der Release-Bau rot, nicht die Welle.

## Szenario 4 (D) — Rueckweg beschrieben und soweit moeglich belegt

**4a Kein Ruecknahme-Endpunkt**
- Messbefehl: grep im Portal-Repo nach Delete/Put/Unpublish am Software-Controller.
- Erwartung: 0 Treffer; Text im `UPDATE_OPS_GUIDE.md` sagt „nicht moeglich", nicht „derzeit ueber …".

**4b Paketverwaltung lehnt Downgrade**
- Vorbedingung: Geraet auf 902.
- Handlung: `adb install -r` (OHNE `-d`) der 901-Datei.
- Erwartung: `INSTALL_FAILED_VERSION_DOWNGRADE`; Geraet bleibt auf 902. (Misst die Paketverwaltung, nicht die App.)

**4c Room-Stand erhoben**
- Messbefehl: `grep -rn "fallbackToDestructiveMigration\|version =" app/src/main/java/com/uip/oneapp/data/local/`.
- Erwartung: Ergebnis im Bericht; 901 und 902 schreiben dieselbe DB-Version (kein Schema-Eingriff dieser Welle) → Rueckbau gefahrlos.

## Szenario 5 — Testsuite

- Vorbedingung: Bau abgeschlossen.
- Handlung: `gradlew :app:testDebugUnitTest`.
- Messbefehl: JUnit-XML auszaehlen (nicht nur Exit-Code).
- Erwartung: 459 Tests, 0 Failures/Errors/Skipped (462 minus 3 durch R6 entfernte RecordingStateBusTest).
