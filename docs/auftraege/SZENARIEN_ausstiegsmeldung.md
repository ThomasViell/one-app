# SZENARIEN — Kette ausstiegsmeldung (08.09.2026)

Erfolgskriterien aus `AUFTRAG.md` (Z-1 Bedingungen 1–4, Z-2) und `PLAN.md` (E-1–E-8), je Szenario:
Vorbedingung, Handlung, Messbefehl, Erwartung. Geraet: ONE (Kiosk, LockTask), Debug-Bau der
Welle installiert; BETA-Dialog nach jedem Neustart wegtippen (Gotcha `kiosk-pflicht`).
Der Toast ist fuer `uiautomator dump` unsichtbar — Beleg ist **Screenshot + logcat**,
nicht der Textdump (gemessen `kiosk-pflicht`, `belege/r6_w2_f8_einstellungen.txt`).
Log-Spur je Ausstieg, Tag `DqAusstieg` (E-7):
(1) `Ausstieg: wasLocal=… wasRtsp=…` · (2) `Finalisierung fertig schluessel=… pfad=… exists=… length=… restDa=…` · (3) `Toast gepostet: …`.

## Szenario 1 (Z-1, Fall A) — Aufnahme laeuft, Zurueck-Pfeil: Meldung nach Finalisierung

- Vorbedingung: Inspektion offen, Schnellaufnahme laeuft ≥ 30 s (MP4-Datei waechst, Journal da).
- Handlung: Zurueck-Pfeil (oben links) tippen.
- Messbefehl: Screenshot-Schleife alle 500 ms fuer 10 s; `adb logcat -v time | grep -E "DqAusstieg|HardwareBitmapRecorder|RecorderJournalMuxer"` parallel; danach `ls -l --time-style=full-iso` des Videoordners.
- Erwartung: Logzeilen (1)(2)(3) in dieser Reihenfolge; Toast „Aufnahme beendet — das Video ist gespeichert (Galerie des Projekts)." auf mindestens einem Screenshot des **Zielbildschirms** (Projektdetail/Home); MP4 vorhanden mit Endgroesse, `.h264j` weg; Zeitstempel von (3) **nach** dem Zeitpunkt, zu dem die MP4 ihre Endgroesse hat und die Journaldatei fehlt (`length` aus (2) = `ls`-Groesse); MP4 abspielbar (Galerie oder `ffprobe`: Dauer, `moov` vorhanden).

## Szenario 2 (Z-1, Fall B) — Keine Aufnahme, Zurueck-Pfeil: keine Meldung

- Vorbedingung: Inspektion offen, keine Aufnahme.
- Handlung: Zurueck-Pfeil tippen.
- Messbefehl: wie Szenario 1.
- Erwartung: Logzeile (1) mit `wasLocal=false wasRtsp=false`; **keine** Zeile (2)/(3); kein Toast auf keinem Screenshot.

## Szenario 3 (Z-1, Fall C) — Stopp-Taste, dann sofort Zurueck: keine Meldung

- Vorbedingung: Aufnahme laeuft.
- Handlung: Stopp-Taste tippen, sofort Zurueck-Pfeil.
- Messbefehl: wie Szenario 1.
- Erwartung: Toast bleibt aus (Bedingung 2); MP4 der Stopp-Taste trotzdem fertig (Weg unveraendert). Keine Zeile (2)/(3).

## Szenario 4 (Z-1, Fall D) — Galerie-Kachel mit Aufnahme: gleiche Meldung

- Vorbedingung: Aufnahme laeuft.
- Handlung: Kachel/Taste „Galerie" (`HwButton.GALLERY`).
- Messbefehl: logcat `DqAusstieg`; Screenshot optional (gleicher `onDispose`-Pfad wie Szenario 1).
- Erwartung: Zeilen (1)(2)(3), Toast; Kurzbeleg genuegt — die Wege 5/6 (Projektkarte → Projekt bearbeiten, Doppeltipp → Projekte) laufen durch dasselbe `onDispose`, je ein Logbeleg; Zaehlung 4/4 ungesperrte Wege.

## Szenario 5 (Z-1, Fall E) — Gesperrte Wege unveraendert

- Vorbedingung: Aufnahme laeuft.
- Handlung: Kachel „Einstellungen" bzw. Power-Langdruck.
- Messbefehl: Screenshot; Journal-Wachstum zweimal im Abstand ≥ 5 s.
- Erwartung: Bandsperren-Toast (`recording_active_settings_blocked`) bzw. Sperrdialog (`exit_app_blocked_recording`, nur OK-Knopf) wie in Runde 6; **keine** `DqAusstieg`-Zeile; Journal waechst weiter (Aufnahme laeuft).

## Szenario 6 (Z-2, Fall F) — Power-Dialog und Einstellungen ohne Aufnahme

- Vorbedingung: keine Aufnahme. Geraet: ONE.
- Handlung: Power-Langdruck → Dialog; Einstellungen → Karte „App verlassen" → Bestaetigung.
- Messbefehl: Screenshot.
- Erwartung: „Die App wird zur Android-Oberfläche verlassen. Das Livebild läuft nicht weiter. Zurück in die App: …" — Rueckweg-Satz zeichengleich (belegt `kiosk-pflicht`, `belege/r3_m4_09..14`). Tablet-Satz: nur auf einem Tablet im Power-Dialog; in `SettingsScreen` strukturell unerreichbar (M-3) — am Geraet nicht herstellbar, vermerken.

## Szenario 7 (Z-1, Fall G) — RTSP-Pfad (Tablet, Fernmodus)

- Vorbedingung: Tablet mit ONE gekoppelt (QR-Kopplung belegt, Memory `dual_mode_e2e`), Aufnahme am Tablet laeuft.
- Handlung: Zurueck.
- Messbefehl: logcat `DqAusstieg|FfmpegRtspRecorder|RecorderRemux`; Screenshot-Schleife.
- Erwartung: Toast **nach** dem Remux (Zeile (2) mit `exists=true` vor Zeile (3)); `restDa=false` im Erfolgsfall. Ohne Tablet: ausdruecklich „nicht hergestellt" — Unit-Tests `FfmpegRtspRecorderStopTest` sind dann der einzige Beleg (Auflage des Beraters: Ablauf „Stopp-Taste → sofort Zurueck, Callback unterwegs").

## Szenario 8 (Z-1, Fehlerfall) — Finalisierung ohne Ergebnis: NOT_SAVED

- Vorbedingung: am Geraet vermutlich nicht herstellbar (Finalisierung fallt auf Frag-Fallback zurueck, `RecorderRemux.kt`).
- Messbefehl: `gradlew :app:testDebugUnitTest --tests "*ExitRecordingNoticeTest" --tests "*FfmpegRtspRecorderStopTest"`.
- Erwartung: gruen; Logik: `onDone(null)` → Schluessel `recording_exit_not_saved` — nie „gespeichert" vor einer fertigen Datei (Bedingung 4). Geraetefall nicht herstellen → so benennen, nie behaupten.

## Szenario 9 (Z-2, Textstellen) — vier Zeilen vorher/nachher

- Messbefehl: `git diff -U0 master..HEAD -- app/src/main/java/com/uip/oneapp/ui/localization/LocalizationManager.kt` (Rohausgabe im Bericht, `belege/z2_vorher_nachher.txt`).
- Erwartung: `exit_app_confirm_hint` + `exit_app_confirm_hint_tablet` je de/en geaendert (Aufzeichnungs-Aussage raus, Livebild-Aussage bleibt); Rueckweg-Satz zeichengleich; `src/l10n/resources/pl-PL.json` unberuehrt (L-31).

## Szenario 10 (Regel 3) — Gegenprobe der unveraenderten Wege

- Handlung: normaler Stopp ueber die Stopp-Taste (Aufnahme laeuft, Stopp, Warten auf Fertigstellung) — kein `DqAusstieg`-Log, MP4 in der Galerie sichtbar; Aufnahme starten/stoppen ohne Verlassen weiter moeglich; Kiosk bleibt `LOCKED` (`dumpsys activity` `mLockTaskModeState`).
- Erwartung: keine Verhaltens-Aenderung gegenueber Runde 6 ausser der Meldung; `cancel()`-Implementierungen byte-gleich (`git diff master..HEAD` beweist es).
