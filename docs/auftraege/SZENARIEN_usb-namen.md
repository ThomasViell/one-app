# SZENARIEN — Kette usb-namen (08.09.2026)

Erfolgskriterien aus `AUFTRAG.md`: Z-1 sprechende Namen auf dem Stick, Z-2
Unveraendertes (Videos, Ausschlussliste, Datenbank). Je Szenario:
Vorbedingung, Handlung, Messbefehl, Erwartung. **Geraet: keines in dieser
Runde** — die Faelle G-1 bis G-5 gehen in den CEO-Klickdurchgang (zusammen
mit der Welle `ausstiegsmeldung`) und sind hier als **nicht hergestellt**
benannt, nicht hergeleitet.

## Szenario 1 (G-1) — Altprojekt bekommt sprechende Namen

- Vorbedingung: Projekt, das VOR dem Bau dieser Welle aufgenommen wurde
  (z. B. Juli 2026), Fotos und Notizen im Bestand; USB-Stick eingesteckt.
- Handlung: Projekt oeffnen → USB-Export → komplettes Projekt exportieren.
- Messbefehl: `ls <Stick>/DrainQ/<Projektnr>/fotos/` und `/audio/`, Abgleich
  gegen `damages`/`notes` der Datenbank (`sqlite3` ueber `adb`).
- Erwartung: jede Datei mit Zeile traegt
  `<Projektnr>_<yyyyMMdd_HHmmss>_<Position>m_<Schadensart>[_markiert].<endung>`;
  Videos unveraendert. Einziger Beleg, dass der Exportweg (E-1) an echten
  Datenbankzeilen traegt.

## Szenario 2 (G-2) — Waisen im Bestand

- Vorbedingung: ein Altprojekt (wie G-1).
- Handlung: keine — Bestandsaufnahme.
- Messbefehl: `ls damages/project_<id>` und `notes/project_<id>` gegen die
  Datenbank; Zahl der Dateien ohne Zeile notieren.
- Erwartung: Zahl im Bericht; Waisen exportieren mit
  `<Projektnr>_<Zeit aus altem Namen>_Foto[_markiert]` bzw. `_Notiz`, ohne
  lesbare Zeit mit altem Namen (E-3).

## Szenario 3 (G-3) — Dateisystem der Kunden-Sticks

- Vorbedingung: die im Feld verwendeten Sticks.
- Handlung: Bestandsaufnahme am Geraet oder am PC.
- Messbefehl: Stick am PC, `Get-Volume` / Laufwerkseigenschaften.
- Erwartung: Dateisystem (FAT32/exFAT/NTFS) benannt — bestimmt, welche
  FAT-Messung die massgebliche ist (m1 war am PC nicht herstellbar, die
  Verbotsliste steht als Spezifikations-Hypothese im Bericht).

## Szenario 4 (G-4) — Sonderzeichen in der Schadensart

- Vorbedingung: Geraet mit dieser Welle.
- Handlung: Befund mit Schadensart `A/B` anlegen, exportieren.
- Messbefehl: Export endet ohne Fehler; `ls` des Stickordners.
- Erwartung: kein `IOException`, Name enthaelt `A_B` (fatSafe) — Beleg, dass
  die FAT-Sicherung am Geraete-Dateisystem reicht.
- Ohne Geraet (diese Runde): JUnit
  `fatSafe_ersetztVerboteneZeichenUndSteuerzeichen` und
  `eindeutigkeit_nachFatSafe_gleicheNamensbasis` decken die Logik, nicht das
  Dateisystem.

## Szenario 5 (G-5) — Einzelauswahl zeigt Zielnamen

- Vorbedingung: Geraet mit dieser Welle, Projekt mit Dateien.
- Handlung: USB-Export → Modus „Einzelne Dateien".
- Messbefehl: Screenshot der Liste.
- Erwartung: Liste zeigt die Zielnamen (sprechende Namen, Videos
  unveraendert), nicht die kryptischen Quellnamen.
- Ohne Geraet (diese Runde): Golden `dlg_usb_export` unveraendert (Liste im
  Golden nicht sichtbar), `verify.ps1` Exit 0.

## Szenario 6 — Zwei Fotos in derselben Sekunde (Eindeutigkeit, Beleg 3)

- Vorbedingung: zwei Dateien, deren sprechende Namen identisch waeren
  (gleiche Sekunde, Position, Schadensart).
- Handlung: Export (oder der Test).
- Messbefehl: JUnit `service_zweiDateienGleicheSekunde_bekommtZaehler`, dazu
  `eindeutigkeit_caseInsensitiv` und `eindeutigkeit_nachFatSafe_gleicheNamensbasis`.
- Erwartung: zweiter Name traegt `_2` vor der Endung; der Vergleich ist
  case-insensitiv (FAT ist es auch).

## Szenario 7 — Leere Projektnummer

- Vorbedingung: Projekt ohne Projektnummer.
- Handlung: Export (oder der Test).
- Messbefehl: JUnit `leereProjektnummer_faelltAufProjektId`; dieselbe
  Rueckfallregel wie der Zielordner
  (`project.projectNumber.ifEmpty { "Projekt_${project.id}" }`).
- Erwartung: `Projekt_<id>_…`.

## Szenario 8 — Videos und Ausschlussliste unveraendert (Z-2)

- Vorbedingung: Projekt mit Videos, `.frag.mp4`/`.meter.jsonl`/Journal/
  Recovery-Resten.
- Handlung: Export (oder der Test).
- Messbefehl: JUnit `service_benenntNurFotosUndNotizen_videosBleiben` und
  `service_ausschlusslisteBleibtWirksam`; Beleg
  `belege/b2_ausschluss_unveraendert.txt`.
- Erwartung: Video-Namen `<Projektnr>_<yyyyMMdd_HHmmss>.mp4` unveraendert;
  die vier Ausschlussendungen erscheinen nicht auf dem Stick.

## Szenario 9 — Testsuite und Golden-Tor

- Vorbedingung: Bau abgeschlossen.
- Handlung: `./gradlew.bat :app:testDebugUnitTest`;
  `.\tools\manual\verify.ps1 -Langs "de,en"`.
- Messbefehl: JUnit-XML auszaehlen (nicht nur Exit-Code); Exit-Code von
  verify.ps1.
- Erwartung: Basislinie 465 + 25 neue Tests = 490, 0 Failures; verify.ps1
  Exit 0, keine Golden-Aenderung.
