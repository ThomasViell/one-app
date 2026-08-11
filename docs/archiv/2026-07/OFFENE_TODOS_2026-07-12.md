> ÜBERHOLT am 2026-08-05 durch OFFENE_PUNKTE.md. Nur noch als Nachweis aufbewahrt.

# Offene To-dos — Stand 12.07.2026 (Mittag)

Branch `feature/dual-mode`, origin-HEAD = `a94eaae` (0.5.6/506). Kein Merge, kein Tag.
Portal Channel beta: **0.5.7-beta / 507 LIVE** (12.07., per releases.beta.json verifiziert) — enthält den M2-Fix.
**⚠ M2-Fix ist UNCOMMITTET** (nur Arbeitskopie + Portal-APK): `InspectionScreen.kt`, `CapturePersistenceTest.kt`, `RESULT_FIX_M2.md`. 382/382 Tests grün. Vor Feierabend committen + pushen.
**⚠ Thomas-ONE reagiert nicht** (defekt oder Akku leer) — lädt seit Mittag. Alle Gerätetests blockiert, bis das Gerät wieder da ist.

## Heute (Ziel: neue Version + Louis-Antwort)

- [x] M2-Fix umgesetzt (CC, Sonnet): Beobachter-Effekt trägt `kameratyp` nach, sobald C10/C18 erkannt; kein Override belegter Felder. 2 neue Backfill-Tests. Doku: `RESULT_FIX_M2.md`.
- [x] 0.5.7-beta/507 ins Portal (publish-one-release.ps1).
- [ ] ONE lädt → Update auf 0.5.7 über Portal ziehen (testet Update-Pfad mit).
- [ ] **M2-Nachtest am Gerät** — VORHER Tages-Bucket „Schnellaufnahme_110726" löschen (Idempotenz!). 3 Szenarien laut `FIX_M2_KAMERATYP_PROMPT.md`: (1) Kopf erkannt → Schnellaufnahme → Formular+PDF zeigen C18; (2) Schnellaufnahme VOR Kopferkennung → Wert trägt sich nach; (3) manueller C10-Override wird nicht überschrieben.
- [ ] **M4 Font-Messung**: PDF per `adb pull /sdcard/Download/…` ziehen → pdffonts: Inter eingebettet? (bisher nur App-Renderer geprüft).
- [ ] Lange Aufnahme ≥ 5 min mit Pause: Dauer == Echtzeit minus Pause; Recovery-Badge + PDF-Hinweis; Rückfallschalter (HW-Recorder aus → alter Weg).
- [ ] Kill-Test auf 0.5.7: Meter-Spur endet innerhalb 1 s der Videodauer; Foto hinter Spurende → Station leer.
- [ ] M2-Fix committen + pushen (feature/dual-mode).
- [ ] **Louis-Antwortmail**: Status seiner Befunde (B2/B1/M3/M4 = gefixt+abgenommen, M2 = gefixt in 0.5.7), Bitte um Update auf 0.5.7, Meterwert-Rückfrage (Foto/Schaden aus Video → exakt eingebrannter Wert? Leer=ok, falsche Zahl=kritisch), Testauftrag Station mit laufendem Meterzähler (sein Fahrwagen/Haspel — auf Thomas-ONE nicht testbar).

## Merge-Gate `feature/dual-mode` → master

- [ ] M2-Nachtest grün am Gerät (s. o.).
- [ ] M4 pdffonts-Messung (Datei, nicht Renderer).
- [ ] **Station mit laufendem Meterzähler** — einziger nie verifizierter Kernpunkt; braucht Fahrwagen/Haspel → Louis.
- [ ] Louis-Meterwert-Rückfrage beantwortet.
- [ ] Lange Aufnahme / Kill-Test / Recovery-Badge / Rückfallschalter am Gerät (s. o.).

## Entscheidungen (CEO)

- [ ] **Signing-Key.** Flotte in zwei Signatur-Welten (Release `18f9dadb…` vs. Debug `0a03f9ca…`); Debug-Key hängt am Windows-Benutzerprofil. Entscheidungsvorlage vor nächster Kundenauslieferung.
- [ ] **RTSP-Freeze während Aufnahme** (Ein-Encoder-Gate): erlaubt RK3588 zwei Encoder-Instanzen? Für Louis irrelevant, im Tablet-Betrieb Funktionsverlust.
- [ ] **Thomas-ONE defekt?** Falls das Gerät nach Ladung nicht bootet: Hardware-Fall; Gerätetests dann komplett auf Louis verlagern oder Ersatz-ONE.

## Welle 6 (nach Louis' Rückmeldung)

- [ ] USB-Export: kryptische Dateinamen, Fotos/Videos nicht öffenbar (Louis testet im Büro).
- [ ] Speicher-/Kapazitätsanzeige (intern + USB-Stick).
- [ ] Golden-Image neu (Werksreset + `dpm set-device-owner`) — erst mit finalem Signing-Key. Auch Voraussetzung für echte B1-Auto-Zeit (Device-Owner).

## Backlog / niedrige Priorität

- [ ] Zero-Copy-Aufnahmeweg (volle 30 fps; mit 27,55 fps nicht dringend).
- [ ] Dedizierter Publish-API-Key mit Rotation.
- [ ] PDF-Handbuch neu rendern (barlow→inter).
- [ ] CHANGELOG.md ist veraltet (endet bei 0.4.0 Unreleased) — 0.5.x-Historie nachziehen oder Datei einstellen.
- [ ] DE→EN-Sprachwechsel live ok, EN→DE verlangt App-Neustart (minor, Abnahme 11.07.).
