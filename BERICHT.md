# BERICHT — Welle usb-namen (08.09.2026)

**Zweig:** `welle/usb-namen` ab `master` `ab264bc` · **6 Commits:** `d0b08b1`,
`1197795`, `29cbfad`, `7efbc32`, `86c965b`, `8ff44f6` · **Kein Merge, kein
Push, kein Tag.** Belege: `belege/` (9 Dateien, Liste in Abschnitt 8).

## 1. Zusammenfassung

Der USB-Export benennt Fotos und Notizen beim Sammeln sprechend
(`UsbExportNames.kt`), Videos, Berichte, `map.jpg` und die Ausschlussliste
bleiben unveraendert (Z-2), die Datenbank wird nicht angefasst. Die
Zuordnung Datei → Befund kommt aus den bestehenden `damages`/`notes`-Zeilen
— damit bekommen auch Projekte von vor dieser Welle sprechende Namen (E-1).

Mengen: **Tests 490/0** (Basislinie 465 + 25 neue), **Erzeuger 5/5**
abgedeckt, **Verbraucher 3/3** verdrahtet, **G-1..G-5 0/5** hergestellt
(Geraetefrei), `verify.ps1` de+en **Exit 0**, Goldens unveraendert.

## 2. Erhebung ueber alle Erzeuger (Regel 18, Belegpflicht 1)

Suchmuster (roh): `git grep -nE 'File\([^)]*"[^"]*\$\{?[a-zA-Z]' master --
'app/src/main/*.kt'`. Phase 0 gegen `ab264bc` neu gemessen: **18 Treffer,
alle Zeilennummern aus PLAN 1.1 bestaetigt, keine Abweichung.** Urteil je
Treffer (Kurzform; Volltext PLAN 1.1):

| # | Stelle (master) | Name heute | Auf dem Stick? | Urteil |
|---|---|---|---|---|
| 1 | `InspectionScreen.kt:551` | `foto_<ms>.jpg` | ja | kryptisch — Gegenstand |
| 2 | `InspectionScreen.kt:582` | `dmg_<ms>.jpg` | ja | kryptisch — Gegenstand |
| 3 | `ImageAnnotationDialog.kt:345` | `<original>_annotated.jpg` | ja | kryptisch — Gegenstand |
| 4 | `VideoPlaybackDialog.kt:132` | `video_frame_<ms>.jpg` | ja | kryptisch — Gegenstand (im Auftrag nicht genannt) |
| 5 | `NoteDialog.kt:105` | `note_<ms>.m4a` | ja | kryptisch — Gegenstand |
| 6–9 | `InspectionScreen.kt:1681/1702/1733/1751` | `<Projektnr>_<Zeit>.mp4` | ja | sprechend — Z-2, bleibt |
| 10 | `ProjectExportService.kt:59` | `Bericht_<Projektnr>.pdf` | ja | sprechend — nicht betroffen |
| 11 | `ProjectExportService.kt:394` | `Projekt_<Projektnr>.zip` | ja | sprechend — nicht betroffen |
| 12 | `ProjectExportService.kt:444` | `projekt_info.txt` | nein (nur in ZIP) | nicht betroffen |
| 13 | `ProjectFormViewModel.kt:177/223` | `map_project_<ms>.jpg` | ja, als `map.jpg` | bereits umbenannt — nicht betroffen |
| 14 | `LocalBitmapRecorder.kt:108` | `rec_<ms>.mjpeg` | nein | nicht betroffen |
| 15 | `MapPickerDialog.kt:161` | `map_picker_<ms>.jpg` | nein | nicht betroffen |
| 16 | `OfflineMapDownloadWorker.kt:53` | `<dest>.part` | nein | nicht betroffen |
| 17 | `UsbExportService.kt:90` | uebernimmt `it.name` | — | die Stelle, an der der Export benennt |
| 18 | `ReportsScreen.kt:63` | FileProvider-URI | nein | nicht betroffen |

Der Auftrag nennt vier kryptische Erzeuger; gemessen sind es **fuenf** (#4
Standbild aus dem Video). Alle fuenf schreiben nach `damages/` oder `notes/`
— genau die Ordner, die der Export umbenennt. Nicht-Reichweite der Erhebung:
PLAN 1.1.

## 3. Berater-Auflage (08.09.2026): createdAt bei Altzeilen — gemessen

**Ergebnis: `createdAt` ist bei Zeilen aus der Zeit vor Migration 8→9 immer
gefuellt.** Gemessen an vier Stellen:

1. Schema 8 (`app/schemas/.../8.json`): `damages.createdAt` und
   `notes.createdAt` sind `INTEGER NOT NULL` **ohne** DEFAULT (damages
   Zeilen 602–606, notes 689–693) — ein stilles 0 faellt dort nicht ab.
2. Migration 8→9 (`AppDatabase.kt:209–245`): `damages_new` deklariert
   `createdAt INTEGER NOT NULL` **ohne** DEFAULT (`:222`); das `INSERT …
   SELECT` kopiert `createdAt, updatedAt` **woertlich** aus der alten Tabelle
   (`:235`). Die Migration traegt also keinen Vorgabewert und keine
   Migrationszeit ein. **Berichtigt (Runde 2):** „Gleiches Muster bei `notes`"
   traf nicht zu — **keine** Migration fasst `notes` an; alle sechs
   Migrationsobjekte liegen in `AppDatabase.kt`, und `notes` kommt in dieser
   Datei nicht ein einziges Mal vor (Grep, 0 Treffer). `notes` behaelt sein
   Schema-8-DDL unveraendert.
3. `DEFAULT 0` existiert in der Datenbank an **sechs** Stellen (Runde 2
   gemessen; berichtigt statt „nur an zwei Stellen"), alle in
   `MIGRATION_5_6` (`AppDatabase.kt:57–131`): `pipes.createdAt` (`:79`),
   `inspections.startMeter` (`:95`), `inspections.endMeter` (`:96`),
   `inspections.createdAt` (`:104`) — die vier auf den **toten** Tabellen
   `pipes`/`inspections`, die die Migration 8→9 loescht (`:242–243`) — und
   zwei auf der **lebenden** Tabelle `damages`: `continuous` (`:126`) und
   `updatedAt` (`:130`). Keine der sechs Stellen ist `damages.createdAt`:
   die Spalte, auf der der Name ruht, hat nirgends `DEFAULT 0` — der
   zentrale Anspruch haelt.
4. Die Entities trugen den Kotlin-Vorgabewert `createdAt: Long =
   System.currentTimeMillis()` schon vor v9 (`afc9ff3`: `DamageEntity.kt:61`,
   `NoteEntity.kt:24`) und tragen ihn heute (`DamageEntity.kt:44`,
   `NoteEntity.kt:24`); alle Insert-Stellen fuellen den Wert ueber diesen
   Default oder explizit.

Damit gilt Fall 3 der Auflage: der Plan traegt wie geschrieben, eine
1970-Datierung kann ueber Altzeilen nicht entstehen. **Abweichung zur
Vermutung des Beraters:** gemessen ist ein `0`-Vorgabewert in `damages` nur
auf `continuous`/`updatedAt` moeglich (Runde-2-Berichtigung: sechs Stellen
statt zwei, siehe Punkt 3) — in `damages.createdAt`/`notes.createdAt` nie,
der einzig denkbare Weg waere ein expliziter Insert mit 0. Weil genau
das der Weg waere, den eine kuenftige
Aenderung still gehen koennte, wurde der Fall-2-Rueckfall **trotzdem**
gebaut: `createdAt > 0`, sonst Zeit aus dem alten Dateinamen
(`parseLegacyTimestamp`), sonst alter Name (`UsbExportNames.kt:139–176`) —
mit zwei eigenen Tests (`createdAtNull_ziehtZeitAusAltemDateinamen`,
`createdAtNull_ohneLesbareZeit_bleibtUnveraendert`).

## 4. Entscheidungen E-1..E-7 und ihre Umsetzung

- **E-1 (Export statt Erzeugung):** `git diff master..HEAD` enthaelt keine
  der Erzeuger-Dateien (`InspectionScreen.kt`, `NoteDialog.kt`,
  `ImageAnnotationDialog.kt`, `VideoPlaybackDialog.kt`) und keine
  Datenbank-Datei — die Erzeuger sind byte-gleich, die Datenbank unangetastet.
- **E-2 (Namensform):** `speakingName` (`UsbExportNames.kt:66`) baut
  `<Projektnr>_<yyyyMMdd_HHmmss>[_<Pos>m]_<Schadensart>[_markiert].<endung>`
  — Praefix woertlich die Videoform, dazu die drei Angaben, die das PDF je
  Befund unveraendert druckt. Keine laufende Nummer (Nebenbefund 9.1).
- **E-3 (Waisen):** `parseLegacyTimestamp` (`:98`) liest die 13-stellige
  Millisekundenzahl aus Vorwellen-Namen (`^[a-z_]+_(\d{13})(?:_annotated)?\.\w+$`);
  ohne lesbare Zeit bleibt der alte Name (`:175`).
- **E-4 (fatSafe, `:41`):** verbotene Zeichen und Steuerzeichen → `_`,
  Leerraumlaeufe → `-`, Endpunkt/Leerzeichen am Ende weg, Segmentdeckel 40,
  Gesamtdeckel 100 inklusive Endung (Kuerzung trifft nur das Etikett).
  Verbotsliste = Spezifikations-Hypothese, siehe Abschnitt 5.
- **E-5 (Eindeutigkeit, `assignUnique` `:106`):** zweiter/weiterer Treffer
  bekommt `_2`, `_3` … vor der Endung; Vergleich kleinschreibungsfaltend
  (FAT ist es auch). Vier Tests, dazu der Service-Test mit zwei Zeilen in
  derselben Sekunde. Der Riegel laeuft ueber die ganze Liste inklusive
  `map.jpg` — und schuetzt damit auch den Compose-Schluessel `zipPath`
  (Doppelname = Dialog-Absturz, PLAN 1.2).
- **E-6 (Zeilen hereinreichen, ohne Vorgabewerte):**
  `collectProjectFiles(project, damages, notes)` (`UsbExportService.kt:79–85`)
  delegiert an `collectProjectFilesForFolders`; der Dialog zeigt den
  Zielnamen (`UsbExportDialog.kt:175`). Verbraucher 3/3 verdrahtet:
  `UsbExportDialog.kt:42`, `ProjectDetailScreen.kt:629`,
  `ManualScreenshotTest.kt:348` (leere Listen).
- **E-7 (Namenslogik in neuer Datei, rot vor gruen):** 25 Tests in
  `UsbExportNamesTest.kt` (gefordert: mindestens 12). Rot-Lauf vor dem Bau:
  `belege/b1_test_rot.txt` — 25 FAILED, davon **22 durch `NotImplementedError`**
  (kein Kompilierfehler, die Faelle sind einzeln rot) und **3 durch
  `IOException` in der Testvorrichtung** (`UsbExportNamesTest.kt:33`):
  `service_zweiDateienGleicheSekunde_bekommtZaehler`,
  `service_benenntNurFotosUndNotizen_videosBleiben`,
  `service_ausschlusslisteBleibtWirksam` — sie fielen, bevor der Stub
  erreicht wurde. Herkunft (Runde 2): die 22/3 stehen so im Rot-Beleg selbst;
  getragen wird das Endergebnis vom Gruen-Lauf: `belege/b1_test_gruen.txt` —
  25/0.

**Abweichung vom Plan (benannt):** PLAN E-7 schrieb wörtlich
„collectProjectFiles baut nur noch die Karten, listet die Ordner" — die
Ordner- und Kartenlogik liegt jetzt in `collectProjectFilesForFolders`
(`UsbExportNames.kt:178`), der Service reicht nur noch
`getExternalFilesDir` nach (`UsbExportService.kt:83`). Begruendung: genau
das fordert der Plan zwei Zeilen spaeter selbst („getExternalFilesDir bleibt
die einzige Android-Abhaengigkeit und bleibt im Service") und sein Test 9
erlaubt ausdruecklich „eine interne Ueberladung, die die Ordner
hereinnimmt; der Bauer waehlt das Kleinere". Gewinn: alle Service-Tests
laufen als reines JUnit ohne Robolectric. Verhalten und Signaturen sind
plan-identisch.

## 5. FAT-Grenzen (Phase 1, Schritt 5): nicht hergestellt

`belege/m1_fat_grenzen.txt`: PC ohne Admin-Rechte, nur NTFS-Volumes, kein
Wechselmedium, D: ist ein Netzwerkpfad (Fehler 53), WSL2 ohne Loop-Devices,
Docker-Daemon aus, `adb` leer. **Schritt als nicht hergestellt benannt.**
Die Verbotsliste (`\ / : * ? " < > |`, Steuerzeichen < 0x20, Endpunkt/
Leerzeichen am Ende) folgt der Spezifikation und bleibt **Hypothese**, bis
G-3/G-4 am Geraet messen. Die Deckel 40/100 sind durch Test 6 fixiert
(`fatSafe_kuerztSegmentAuf40Zeichen`,
`gesamtname_kuerztAuf100_praefixUndEndungBleiben`).

## 6. Vorher/Nachher (synthetisches Projekt id=42, REF0904-H1)

Vorher gemessen vor dem Bau (`belege/m2_ist_namen.txt`), Nachher aus einem
**echten Lauf** des Szenario-Tests
`service_szenarioAbschnitt6_nachherSpalteAusEchtemLauf`
(`belege/b4_nachher_lauf.txt`, Runde 2 — berichtigt: die Runde-1-Spalte war
von Hand hergeleitet und zeigte fuer beide Waisen denselben Zielnamen; der
eigene Code vergibt ueber `assignUnique` `_2`, E-5). Zeitstempel
`1751000000000` = **27.06.2025 04:53:20 UTC (06:53:20 MESZ)** — der Plan
schrieb zum selben Stempel „27.06.2026", das ist ein Jahres-Tippfehler,
korrigiert in Commit `8ff44f6`; im Test berechnet der Formatierer die Zeit
selbst, keine feste Zeichenkette.

| Vorher (zipPath heute) | Nachher (zipPath nach der Welle) |
|---|---|
| `fotos/dmg_1751000000000.jpg` | `fotos/REF0904-H1_20250627_065320_4,10m_Riss.jpg` |
| `fotos/dmg_1751000000000_annotated.jpg` | `fotos/REF0904-H1_20250627_065320_4,10m_Riss_markiert.jpg` |
| `fotos/foto_1751000000000.jpg` | `fotos/REF0904-H1_20250627_065320_Foto.jpg` (Waise) |
| `fotos/video_frame_1751000000000.jpg` | `fotos/REF0904-H1_20250627_065320_Foto_2.jpg` (Waise, E-5) |
| `videos/REF0904-H1_20260627_101500.mp4` | unveraendert (Z-2) |
| `audio/note_1751000000000.m4a` | `audio/REF0904-H1_20250627_065320_1,50m_Notiz.m4a` |
| `berichte/Bericht_REF0904-H1.pdf` | unveraendert |
| `map.jpg` | unveraendert |

Der Vorher-Beleg zeigt ausserdem: `.frag.mp4`/`.meter.jsonl`-Reste fehlen
schon heute auf dem Stick (Ausschlussliste), der Nachher-Beleg
`b2_ausschluss_unveraendert.txt` beweist die Zeichenidentitaet der
Ausschlusszeilen nach dem Umzug (whitespace-normalisierter diff leer, roher
Service-Diff daneben).

## 7. Mengen (Regel 36)

- **Tests:** 490, davon 465 Basislinie (`belege/p0_tests_basislinie.txt`)
  und 25 neue (`b1_test_gruen.txt`); Vollstaendiger Lauf **0 Failures,
  0 Errors, 59 Suiten**, BUILD SUCCESSFUL (`belege/b5_tests.txt`).
- **Erzeuger:** 5/5 abgedeckt — `foto_`, `dmg_`, `_annotated`,
  `video_frame_`, `note_` laufen alle durch `exportNameFor`, je Form mit
  eigenem Test (mit Zeile, Waise, annotiert, Notiz).
- **Verbraucher:** 3/3 verdrahtet (Abschnitt 4, E-6); der Compiler zaehlt
  sie, weil die Signatur keine Vorgabewerte hat.
- **Geraete-Faelle:** G-1..G-5 = **0/5 hergestellt** (Abschnitt 11).
- **verify.ps1:** de+en **Exit 0**, alle Szenen identisch mit den
  committeten Goldens (`belege/b6_verify.txt`) — das Golden
  `dlg_usb_export` bleibt wie vorhergesagt unveraendert (Liste im Golden
  nicht sichtbar). **Runde 2:** der Beleg ist neu aufgezeichnet, roh mit der
  Vollausgabe des Laufs — die Runde-1-Fassung enthielt nur die 22-Byte-Zeile
  `--- EXITCODE: 0 ---` und trug die Aussage nicht.

## 8. Belegpflicht L-96 — die sechs Belege

1. **Erhebung ueber alle Erzeuger:** Abschnitt 2 (18 Treffer, Urteil je
   Treffer; rohes Muster und Volltext PLAN 1.1).
2. **Vor dieser Welle aufgenommenes Foto bekommt sprechenden Namen:**
   Test `fotoMitZeile_vorDerWelle_bekommtSprechendenNamen` — Datei
   `dmg_1751000000000.jpg` (27.06.2025) plus Zeile in der Form, die die App
   seit Migration 8→9 schreibt; getragen von der createdAt-Messung
   (Abschnitt 3). Beleg fuer die Altzeilen, nicht nur fuer neue.
3. **Eindeutigkeit:** `eindeutigkeit_zweiGleicheNamen_bekommtZaehler`,
   `eindeutigkeit_caseInsensitiv`, `eindeutigkeit_nachFatSafe_gleicheNamensbasis`,
   `determinismus_andereReihenfolge_gleicheNamensmenge`,
   `service_zweiDateienGleicheSekunde_bekommtZaehler` — fuenf Tests, nicht
   eine Zusage.
4. **Vollstaendiger Testlauf:** `belege/b5_tests.txt` — 490, 0 Failures,
   roh mit Messwert-Zeile aus den XML.
5. **verify.ps1 de+en:** `belege/b6_verify.txt` — Exit 0, roh. Runde 2 neu
   aufgezeichnet mit Vollausgabe (die Runde-1-Fassung war 22 Byte mit nur
   `--- EXITCODE: 0 ---`).
6. **Porcelain + Diff-Stat:** `git status --porcelain` = nur
   `?? OFFENE_PUNKTE.md.vor-ausstiegsmeldung-20260908` (Beraterdatei, liegt
   unangetastet, wird nicht committet — ausdruecklich benannt, PLAN Schritt
   18). `git diff master..HEAD --stat` = genau die 7 geplanten Dateien:
   `UsbExportService.kt`, `UsbExportNames.kt` (neu), `UsbExportNamesTest.kt`
   (neu), `UsbExportDialog.kt`, `ProjectDetailScreen.kt`,
   `ManualScreenshotTest.kt`, `docs/auftraege/SZENARIEN_usb-namen.md` (neu).
   Unerlaubte Dateien (Erzeuger, `DamageEntity.kt`, `AppDatabase*`,
   `ProjectExportService.kt`, `pl-PL.json`): nicht im Diff.

## 9. Nebenbefunde (nicht Gegenstand, benannt)

1. **PDF-Nummer ist nicht stabil** (`damages_newest_first` kehrt die Liste
   um): der Name traegt deshalb Zeit, Position und Schadensart statt einer
   laufenden Nummer (E-2). Wer die Nummer will, muss zuerst die PDF-Nummer
   stabil machen — eigene Welle.
2. **Waisen** (`dmg_`/`video_frame_` vor dem Dialog, Abbruch loescht
   nicht): exportieren mit Projekt + Zeit aus dem alten Namen; ob es solche
   Dateien in realen Projekten gibt, misst G-2.
3. **`export()` nimmt `projectNumber` ungesichert als Ordnernamen**
   (`UsbExportService.kt:103` heute — nach dem N-1-KDoc-Einschub; im
   Runde-1-Stand `:97`; Plan nannte `:116`): ein `/` in der Projektnummer
   erzeugte einen Unterordner. Geschwister, nicht Gegenstand. **Herkunft der
   zwischenzeitlich falschen Zeile „`:100`":** das ist die Zeile von
   `if (!targetRoot.isDirectory)` — drei Zeilen unter der
   `folderName`-Zuweisung (`:97` im Runde-1-Stand); die Nennung griff die
   Pruefzeile statt der Zuweisung.
4. **Zeitstempel-Korrektur:** Plan „27.06.2026" → gemessen
   27.06.2025 04:53:20 UTC (Abschnitt 6).

## 10. Abweichungen vom Plan

1. Ort der Ordnerlogik (`collectProjectFilesForFolders` in
   `UsbExportNames.kt` statt im Service) — Abschnitt 4, E-7.
2. 25 Tests statt „mindestens 12".
3. Sechs Commits statt vier: zusaetzlich `86c965b` (Screenshot-Test
   stellt `getExternalFilesDir` — Paparazzi liefert null, der erste
   Vollstlauf `b5` war deshalb einmal rot mit 1 Failure `dlg_usb_export`;
   nach dem Fix 490/0) und `8ff44f6` (Kommentar-Korrektur Zeitstempel).
   Beide fassen nur Testcode an. **Herkunft der zwischenzeitlich falschen
   Zahl „sieben":** sie entstand aus `git log --oneline -7` — das Fenster
   zeigt sieben Zeilen, weil es den Basis-Commit `ab264bc` (master, kein
   Wellen-Commit) mitzaehlt; auf dem Zweig liegen sechs.
4. createdAt-Rueckfall zusaetzlich gebaut, obwohl die Messung Fall 3 ergab
   (Abschnitt 3) — Sicherheitsweg fuer den gemessenen unmoeglichen Fall.

## 11. Nicht hergestellt (benannt, nicht hergeleitet)

- **G-1 Altprojekt** auf den Stick exportieren (einziger Beleg an echten
  Datenbankzeilen), **G-2 Waisen-Zaehlung** im Bestand, **G-3
  Stick-Dateisystem** der Kunden-Sticks, **G-4 Schadensart `A/B`** ohne
  `IOException`, **G-5 Einzelauswahl** zeigt Zielnamen — Katalog mit
  Vorbedingung/Handlung/Messbefehl/Erwartung in
  `docs/auftraege/SZENARIEN_usb-namen.md`.
- **N-1/N-2 im Klickdurchgang (N-3, Runde 2):** N-2 ist in G-1 sichtbar —
  die Dialog-Flows starten mit `emptyList()` (`stateIn(…,
  WhileSubscribed(5000), emptyList())`, `ProjectDetailViewModel.kt:43–49`)
  und liefern die Datenbankzeilen erst kurz nach dem Oeffnen; der CEO sieht,
  dass die Namensliste beim Eintreffen der Zeilen von Waisen-Namen (`_Foto`,
  ohne Position und Schadensart) auf die echten Namen (`_4,10m_Riss`)
  umspringt — vor der Korrektur blieb sie auf dem Waisen-Stand stehen und
  exportierte so (Messbefehl: Anteil `_<Pos>m_<Schadensart>`- gegen
  `_Foto`-Namen zaehlen). N-1 ist in G-1 sichtbar, wenn der Stick gesteckt,
  der externe Speicher aber nicht eingehaengt ist — Vorbedingung: Stick
  gesteckt, externer Speicher **nicht** eingehaengt; Handlung: Dialog
  oeffnen, „Export starten" druecken; Erwartung: kein Absturz, sondern die
  Zeile **„Keine Dateien zum Exportieren vorhanden."** (`UsbExportDialog.kt:219`
  → `LocalizationManager.kt:327`). Dieser Zustand ist am Geraet nicht
  willkuerlich herstellbar und deshalb bis auf Weiteres **nicht** Teil von
  G-1. **Berichtigt (Runde 3):** die Runde-2-Fassung nannte den fehlenden
  Stick und „Kein Stick erkannt" — beides falsch, Herkunft im Anhang
  Runde 3.
- **FAT-Messung** am echten Datentraeger (Abschnitt 5).
- Alle gehen in den CEO-Klickdurchgang, zusammen mit der Welle
  `ausstiegsmeldung`.

## 12. Grenzen eingehalten

- Nur Zweig `welle/usb-namen`; kein Merge, kein Push, kein Tag.
- `git_pfadpflicht`: alle `git add` mit namentlichen Pfaden, kein `add -A`,
  kein `--force`, kein `--no-verify`, kein `reset`/`checkout -- .`/`clean`.
- `src/l10n/resources/pl-PL.json` unberuehrt (L-31).
- `OFFENE_PUNKTE.md` und `PROJECT_STATUS.md` nicht angefasst; die
  Beraterdatei `OFFENE_PUNKTE.md.vor-ausstiegsmeldung-20260908` bleibt
  unverfolgt liegen.
- Keine Wettbewerbernamen.
- Belege im Kettenordner: `p0_tests_basislinie.txt`, `p0_tests_roh.log`,
  `m1_fat_grenzen.txt`, `m2_ist_namen.txt`, `b1_test_rot.txt`,
  `b1_test_gruen.txt`, `b2_ausschluss_unveraendert.txt`, `b5_tests.txt`,
  `b6_verify.txt`.

---

# ANHANG — Runde 2 (Nachbesserung, 08.09.2026)

**Zweig:** `welle/usb-namen` · **Kopf:** `a883f42` · **3 Commits:** `a744712`
(N-1), `c131355` (N-1-Tests + B-4), `a883f42` (N-2) · **Kein Merge, kein
Push, kein Tag.** Anlass: `PRUEFBERICHT_A.md.runde1` — ROT wegen fuenf
Belegstellen (B-1 bis B-5), zwei Codebefunden (N-1, N-2) und zwei
Selbstkorrekturen ohne Herkunft (N-5). Alle Punkte der `NACHBESSERUNG.md`
sind umgesetzt; die Berichtigungen stehen **in place im Runde-1-Text,
nichts geloescht** (Abschnitte 3, 4, 6, 7, 8, 9, 10, 11).

## R2-1. Zusammenfassung (Regel 36 — am Ergebnis gezaehlt)

- **N-1** erledigt: der `!!`-Absturzweg ist entschaerft (leere Liste),
  **3 neue Tests**, Rot-/Gruen-Beleg.
- **N-2** erledigt: der remember-Schluessel schliesst damages/notes ein,
  **2 neue Tests** (headless Recomposer), Rot-/Gruen-Beleg.
- **N-3** erledigt: G-1-Vermerk in §11 in place.
- **N-4** erledigt: B-1 bis B-5 berichtigt, jede mit Herkunft; §6-Spalte aus
  einem **echten Lauf**, `b6_verify.txt` **neu, roh, mit Inhalt**.
- **N-5** erledigt: beide Herkuenfte in §9.3/§10.3 nachgetragen.
- **Tests 496/0** (Runde 1: 490; neu 6 = 2 reine N-1 + 1 N-1-Robolectric +
  2 N-2 + 1 B-4), **61 Suiten**, voller Lauf roh: `belege/b5_tests_runde2.txt`.
- **verify.ps1** de+en **Exit 0**: `belege/b6_verify.txt` (7.294 Byte statt
  der 22-Byte-Runde-1-Fassung).
- **Erzeuger byte-gleich, Datenbank unangetastet:** `git diff 8ff44f6..HEAD
  --stat` nennt genau die 6 Runde-2-Dateien (`belege/git_status_runde2.txt`).

## R2-2. N-1 — der Fall ist behandelt, die Erreichbarkeit benannt

**Herkunft: NEU.** Vor der Welle gab es an dieser Stelle keinen `!!`; der
Null-Fall (externer Speicher nicht eingehaengt, Android-Doku) lief still in
eine leere Liste, weil `File(null, …)` nicht existierte.

**Messversuch Erreichbarkeit (verlangt: erst messen):** kein Geraet
verbunden (`adb devices` leer), also nicht am Geraet herstellbar — die
Dokumentation nennt null ausdruecklich, und der Paparazzi-Lauf von `86c965b`
ist real daran gescheitert (BridgeContext lieferte null). Erreichbarkeit am
Geraet: **NICHT gemessen**, so benannt (`belege/n1_messung_geraet.txt`).
Der `!!` fiel trotzdem: eine unbelegte Annahme ist kein Grund fuer einen
Absturz, der den Bediener im Feld trifft.

**Umsetzung:** `UsbExportService.collectProjectFiles` reicht den null-Wert
durch; `collectProjectFilesForFolders` loest alle vier Ordner **vor** dem
Sammeln auf und liefert bei null die leere Liste — kein Teilbestand
(`UsbExportNames.kt:184–196`).

**Tests (3 neu):** `service_externerSpeicherNichtEingehaengt_leereListeStattAbsturz`,
`service_einOrdnerOhneSpeicher_leereListeStattTeilbestand` (reines JUnit,
`UsbExportNamesTest.kt`) und
`collectProjectFiles_externerSpeicherNichtEingehaengt_liefertLeereListe`
(Robolectric, Kontext ohne externen Speicher, `UsbExportServiceTest.kt`).

**Rot/Gruen:** `belege/n1_test_rot.txt` (gefallener Test mit Name und
Zeile, kein Uebersetzungsfehler), `belege/n1_test_gruen.txt`.

## R2-3. N-2 — der Schluessel schliesst die neuen Eingaben ein

**Messung des Pruefers bestaetigt:** `remember(project.id)` baute die
Dateiliste bei neuen damages/notes nicht neu — der Dialog zeigte und
exportierte veraltete Namen. Bedeutung fuer den Bediener: die Flows starten
mit `emptyList()` (`stateIn(…, WhileSubscribed(5000), emptyList())`,
`ProjectDetailViewModel.kt:43–49`) und liefern die Zeilen kurz nach dem
Oeffnen — jede Datei fiel in den Waisen-Zweig und verlor Position und
Schadensart.

**Fix:** `remember(project.id, damages, notes)`; auch die Vorauswahl haengt
an der frischen Liste (`UsbExportDialog.kt:31–67`).

**Tests (2 neu, alles gemessen):** `UsbExportDialogRefreshTest.kt` treibt
die Komposition headless (Recomposer ohne View, NoopApplier) und zaehlt die
Sammeleffekte. Die Maschinerie ist im Test-KDoc dokumentiert: FrameClock im
Runner-Kontext (`Recomposer.kt:1017`), ein Write allein weckt den
Recomposer nicht — `Snapshot.sendApplyNotifications()` schon, LAZY-Start
nach `setContent`, begrenzte Pump-Schleifen statt `withTimeout` (feuerte
unter Robolectric nicht).

**Rot/Gruen:** `belege/n2_test_rot.txt` (2 FAILED, je mit N-2-Meldung:
"expected:<2> but was:<1>"), `belege/n2_test_gruen.txt`.

## R2-4. N-3 — G-1-Vermerk

In §11 in place nachgetragen (letzter Punkt): N-2 ist in G-1 sichtbar — der
CEO sieht die Liste beim Eintreffen der Zeilen von Waisen-Namen auf die
echten Namen umspringen (vorher blieb sie stehen), Messbefehl: Anteil
`_<Pos>m_<Schadensart>`- gegen `_Foto`-Namen zaehlen. N-1 ist bei gestecktem
Stick und nicht eingehaengtem externen Speicher sichtbar: statt des
Absturzes zeigt der Dialog nach „Export starten" die Zeile **„Keine Dateien
zum Exportieren vorhanden."** (`UsbExportDialog.kt:219` →
`LocalizationManager.kt:327`). **Berichtigt (Runde 3):** die Runde-2-Fassung
nannte den fehlenden Stick und „Kein Stick erkannt" — falsch, Herkunft im
Anhang Runde 3.

## R2-5. N-4 — die fuenf Belegstellen, berichtigt mit Herkunft

| # | Stand Runde 1 | Berichtigt | Herkunft (Beleg) |
|---|---|---|---|
| B-1 | „25 FAILED durch NotImplementedError" | **22** NotImplementedError, **3** IOException in der Testvorrichtung (`UsbExportNamesTest.kt:33`) | im Rot-Beleg nachgezaehlt: `belege/b1b2b3_messung_runde2.txt` (22/3, Testnamen) — berichtigt in §4 E-7 |
| B-2 | „DEFAULT 0 nur an zwei Stellen, beide tote Tabellen" | **sechs**, davon **zwei auf der lebenden Tabelle `damages`** | alle sechs Stellen gemessen, alle in `MIGRATION_5_6` (`AppDatabase.kt:57–131`): `pipes.createdAt` `:79`, `inspections.startMeter` `:95`, `inspections.endMeter` `:96`, `inspections.createdAt` `:104` (tot), `damages.continuous` `:126`, `damages.updatedAt` `:130` (lebend) — berichtigt in §3 Punkt 3; keine der sechs ist `damages.createdAt`, der Anspruch haelt |
| B-3 | „Gleiches Muster bei notes" | **keine** Migration fasst `notes` an | alle sechs Migrationsobjekte liegen in `AppDatabase.kt`; `notes` kommt dort nicht ein einziges Mal vor (Grep, 0 Treffer) — berichtigt in §3 Punkt 2 |
| B-4 | Nachher-Spalte zeigte zwei identische Zielnamen | der Code liefert `…_Foto.jpg` und `…_Foto_2.jpg` (E-5) | §6-Spalte aus einem **echten Lauf** erzeugt: Szenario-Test `service_szenarioAbschnitt6_nachherSpalteAusEchtemLauf` sammelt das §6-Projekt und schreibt die Namen nach `build/tmp/b4_scenario_namen.txt`; Rohausgabe `belege/b4_nachher_lauf.txt` — berichtigt in §6 (Kopf + Zeile 4) |
| B-5 | `b6_verify.txt` 22 Byte, nur `--- EXITCODE: 0 ---` | **neu, roh, mit Inhalt** | `tools\manual\verify.ps1 -Langs "de,en"` erneut gefahren, Vollausgabe roh ueberschrieben (7.294 Byte, PASS de+en, Exit 0) — Hinweise in §7/§8 |

## R2-6. N-5 — Herkunft der zwei Selbstkorrekturen

- **„6 statt 7 Commits":** die falsche 7 entstand aus `git log --oneline
  -7` — das Fenster zeigt sieben Zeilen, weil es den Basis-Commit `ab264bc`
  (master, kein Wellen-Commit) mitzaehlt; auf dem Zweig liegen sechs
  (`belege/n5_herkunft_selbstkorrekturen.txt`) — nachgetragen in §10.3.
- **„`:97` statt `:100`":** `:100` ist die Zeile von
  `if (!targetRoot.isDirectory)` — drei Zeilen unter der
  `folderName`-Zuweisung (`:97` im Runde-1-Stand); die Nennung griff die
  Pruefzeile statt der Zuweisung. Nach dem N-1-KDoc-Einschub liegt die
  Zuweisung heute auf `:103` — nachgetragen in §9.3.

## R2-7. Belegpflicht L-96 — Zuordnung

1. N-1/N-2 Rot + Gruen: `n1_test_rot.txt`, `n1_test_gruen.txt`,
   `n2_test_rot.txt`, `n2_test_gruen.txt`.
2. B-4 Rohausgabe: `b4_nachher_lauf.txt` (gradle-Lauf roh + die vom Test
   geschriebene Namensdatei, unveraendert).
3. B-5: `b6_verify.txt` — neu, roh, mit Inhalt.
4. Vollstaendiger Testlauf: `b5_tests_runde2.txt` — **496, 0 Failures,
   0 Errors, 0 Skipped, 61 Suiten**, Messwert aus den XML, Rohausgabe dabei.
5. verify.ps1 de+en mit Exit-Code, roh: `b6_verify.txt`.
6. Porcelain + Diff-Stat: `git_status_runde2.txt` — Porcelain leer ausser
   der unverfolgten Beraterdatei, `git diff 8ff44f6..HEAD --stat` = genau
   die 6 benannten Dateien.

## R2-8. Grenzen eingehalten

- Nur Zweig `welle/usb-namen`; kein Merge, kein Push, kein Tag.
- `git_pfadpflicht`: alle `git add` mit namentlichen Pfaden, kein `add -A`,
  kein `--force`, kein `--no-verify`, kein Reset/Restore/Clean.
- Die fuenf Erzeuger byte-gleich, die Datenbank unangetastet (Diff-Stat).
- `src/l10n/resources/pl-PL.json` unberuehrt (L-31).
- `OFFENE_PUNKTE.md` und `PROJECT_STATUS.md` unberuehrt; die Beraterdatei
  `OFFENE_PUNKTE.md.vor-ausstiegsmeldung-20260908` bleibt unverfolgt liegen.

## R2-9. Nicht hergestellt (benannt, nicht hergeleitet)

- **G-1 bis G-5: weiter 0/5** — kein Geraet, die Faelle bleiben offen und
  werden nicht behauptet.
- **Erreichbarkeit des Null-Falls am Geraet:** nicht gemessen (kein Geraet),
  benannt in `n1_messung_geraet.txt`.
- **FAT-Messung** (Abschnitt 5): weiter offen.

---

# ANHANG — Runde 3 (Nachbesserung, 09.09.2026)

**Zweig:** `welle/usb-namen` · **Kopf:** `a883f42` + der Berichts-Commit
dieser Runde (1 Commit, nur `BERICHT.md`) · **Kein Merge, kein Push, kein
Tag.** Anlass: `PRUEFBERICHT_A.md.runde2` — **ERGEBNIS: ROT**, wegen
**eines** Punktes (P-6: der N-1-Abnahmevermerk in §11/R2-4 beschreibt eine
Beobachtung, die es nicht gibt). Umfang laut Auftrag: zwei Saetze in
`BERICHT.md`. Kein Code, kein Lauf, kein neuer Beleg.

## R3-1. Was getan wurde (Regel 36 — am Ergebnis gezaehlt)

- **2 Stellen berichtigt, in place, nichts geloescht:** §11 letzter Punkt und
  R2-4 letzter Satz — je mit Berichtigt-Marker und Verweis auf die Herkunft
  (R3-4).
- **0 Codezeilen geaendert:** `git diff a883f42..HEAD` enthaelt keine
  `.kt`-Datei.
- **0 Testlaeufe, 0 `verify.ps1`-Laeufe:** laut Belegpflicht nicht gefordert
  — die Runde aendert keine Zeile Code, ein Lauf haette nichts belegt, was
  Runde 2 nicht schon belegt hat.
- **1 Datei im Diff: `BERICHT.md`.** Die Belegpflicht nennt `BERICHT.md` als
  einzige Datei im Diff und verlangt einen Commit — deshalb wird der Bericht
  (Original im Kettenordner `C:\Projekte\_ketten\usb-namen\BERICHT.md`, dort
  angefuegt, nie ersetzt) als Kopie an den Repo-Stand ueberfuehrt. Die
  Kettenfassung bleibt das Original.
- **G-1..G-5 weiter 0/5** hergestellt (R3-7).

## R3-2. Der berichtigte Vermerk — was jetzt dasteht

§11 (letzter Punkt) und R2-4 (letzter Satz) benennen jetzt, was der CEO
wirklich taete und saehe:

- **Vorbedingung:** Stick gesteckt, externer Speicher **nicht** eingehaengt.
- **Handlung:** Dialog oeffnen, „Export starten" druecken.
- **Erwartung:** kein Absturz, sondern die Zeile **„Keine Dateien zum
  Exportieren vorhanden."** (`UsbExportDialog.kt:219` →
  `LocalizationManager.kt:327`).
- **Ausdruecklich benannt:** dieser Zustand ist am Geraet nicht willkuerlich
  herstellbar und deshalb bis auf Weiteres **nicht** Teil von G-1.

## R3-3. Eigene Messung (Prufvorbehalt) — keine Abweichung

Der Auftrag verlangt, Zeilennummern und Ablauf selbst nachzumessen; weicht
die eigene Messung ab, gilt sie. Gemessen am Kopf `a883f42` — alle Zahlen
des Pruefers:

| Pruefer-Zahl | Eigenmessung | Ergebnis |
|---|---|---|
| `volumes.isEmpty()` `UsbExportDialog.kt:105-106` | `:105` `volumes.isEmpty() -> {`, `:106` `Text(S("usb_no_stick"))` | trifft |
| `findUsbVolumes()` `UsbExportService.kt:59-70` | `:62` `StorageManager`, `:65` Filter `isRemovable && MEDIA_MOUNTED`, kein `getExternalFilesDir` | trifft |
| Vorwellen-Zweig `git show 1197795^:…/UsbExportDialog.kt:80-81` | `:80` `volumes.isEmpty() -> {`, `:81` `Text(S("usb_no_stick"))` — wortgleich | trifft |
| `collectProjectFiles` bei jeder Komposition `UsbExportDialog.kt:59-61`, `AlertDialog` erst `:77` | `:59-61` `rememberExportFiles(…) { service.collectProjectFiles(…) }`, `:77` `AlertDialog(` | trifft |
| alter `!!` | `git show 8ff44f6:…/UsbExportService.kt:90` — `context.getExternalFilesDir(dirName)!!` | trifft |
| Commit `86c965b` — Dialog braucht den Ordner ohne jeden Stick | Commit-Meldung: „der USB-Dialog braucht den Ordner seit der Welle schon beim Zusammensetzen", Test-Stub `override fun getExternalFilesDir` | trifft |
| `UsbExportDialog.kt:219` | `if (files.isEmpty()) { errorMsg = noFilesMsg; return@TextButton }` | trifft |
| `LocalizationManager.kt:327` | `"usb_no_files" to "Keine Dateien zum Exportieren vorhanden."` | trifft |
| Null → leere Liste statt Absturz | `UsbExportNames.kt:190-196` — alle vier Ordner vor dem Sammeln aufgeloest, `projectDirOf(dirName) ?: return emptyList()` | trifft |

**Ablaufmessung (Kern des Prufvorbehalts):** Stick gesteckt →
`findUsbVolumes()` liefert das Volume, `volumes` ist nicht leer → der Dialog
zeigt den Auswahl-Zweig (`:135`), nicht „Kein Stick erkannt".
`getExternalFilesDir` null → `allFiles` leer, **kein Absturz** (N-1). Der
Button ist sichtbar (`:213`: `progress == null && hasAccess &&
volumes.isNotEmpty()`); der Klick laeuft `files = if (fullProject) allFiles`
(`:217`), leer → `errorMsg = noFilesMsg` (`:219`), angezeigt ueber der
Moduswahl (`:136-139`). **Die Zeile, die der Bediener im Null-Fall sieht,
ist wirklich „Keine Dateien zum Exportieren vorhanden." — keine Abweichung
vom Pruefer.**

Zwei gemessene Einzelheiten, benannt: (1) die Zeile erscheint erst **nach**
dem Klick — der Dialog oeffnet im Null-Fall normal — und bleibt stehen, bis
ein erfolgreicher Export sie loescht (`errorMsg = null` erst `:220`);
(2) `usb_no_files` existierte vor der Welle schon (`1197795^`:
`LocalizationManager.kt:327`) — der Belegcharakter der Beobachtung liegt
darin, dass der Vorwellen-Stand im Null-Fall **vor** jeder Meldung
abstuerzte (Kompositions-`!!`), der Nach-Stand den Dialog oeffnet und die
Zeile zeigt; die Meldung selbst unterscheidet die Staende nicht.

## R3-4. Herkunft der falschen Angabe

Die Runde-2-Fassung entstand beim Verfassen des N-3-Vermerks: der Null-Fall
(`getExternalFilesDir` = null) und der Leer-Zweig „Kein Stick erkannt"
(`volumes.isEmpty()`) wurden gleichgesetzt, ohne den Dialogfluss zu messen.
„Kein Stick erkannt" war die einzige dem Null-Fall benachbarte Meldung im
Dialog — angenommen wurde, der CEO saehe sie im Null-Fall. Gemessen haengt
der Zweig an `volumes`, das `getExternalFilesDir` nie anfasst; bei
gestecktem Stick zeigt der Dialog den Auswahl-Zweig, und der Null-Fall wird
erst beim Klick ueber die leere Liste sichtbar — als `usb_no_files`, nicht
`usb_no_stick`. Der Vermerk griff also eine unbelegte Annahme (Gleichsetzung
zweier getrennter Wege) statt einer Messung des Ablaufs.

## R3-5. Belegpflicht L-96

1. `git diff a883f42..HEAD --stat` — **genau eine Datei: `BERICHT.md`**
   (Neuzugang, 588 Zeilen). Keine `.kt`-Datei, kein Test, keine Datenbank.
2. `git status --porcelain` — leer ausser der unverfolgten Beraterdatei
   `OFFENE_PUNKTE.md.vor-ausstiegsmeldung-20260908`.
3. **Kein Testlauf, kein `verify.ps1`** (R3-1).

## R3-6. Grenzen eingehalten

- Nur Zweig `welle/usb-namen`; kein Merge, kein Push, kein Tag.
- `git_pfadpflicht`: `git add` ausschliesslich mit namentlichem Pfad
  (`BERICHT.md`), kein `add -A`, kein `--force`, kein `--no-verify`, kein
  Reset/Restore/Clean.
- Die fuenf Erzeuger byte-gleich, die Datenbank unangetastet,
  `pl-PL.json` unberuehrt — der Diff-Stat nennt nur `BERICHT.md`.
- `OFFENE_PUNKTE.md` und `PROJECT_STATUS.md` unberuehrt; die Beraterdatei
  bleibt unverfolgt liegen.

## R3-7. Nicht hergestellt / nicht Gegenstand (benannt)

- **G-1..G-5: weiter 0/5** — kein Geraet; der Katalog bleibt in
  `docs/auftraege/SZENARIEN_usb-namen.md`.
- **Der Null-Fall am Geraet:** nicht hergestellt. Der Zustand ist am Geraet
  nicht willkuerlich herstellbar (primaerer externer Speicher = interner
  Flash, kein Bedienerweg zum Unmounten) — gemessen ist auch das nicht
  (kein Geraet).
- **Nicht Gegenstand dieser Runde (laut Auftrag):** die acht
  P-7-Beobachtungen und die vier benannten Schwaechen/Grenzen zu P-1.1 und
  P-2.1 — der Berater sichtet sie und fuehrt, was bleibt, in
  `OFFENE_PUNKTE.md`; die veraltete Belegdatei zu N-5b (P-4) — benannt,
  nicht blockierend; die FAT-Verbotsliste bleibt Hypothese (Abschnitt 5).
