# Szenarien — Welle `bedienbefunde-0915`

Regel 17. Basis: `AUFTRAG.md` (inkl. NACHTRAG 1), `PLAN.md` Abschnitt 3/5. Jede JUnit-Referenz
unten ist gegen die tatsaechlich vorhandenen Testnamen im Wellenzweig geprueft (Regel: „jede
JUnit-Referenz im Szenarienkatalog wird gegen die vorhandenen Testnamen geprueft") — die Namen
weichen an einigen Stellen von `PLAN.md` ab, weil `WIRKUNG.json` (nachgereicht, R-5) eigene
Bezeichner-Muster vorschreibt (`selectionCount`, `exportEnabled`, `datetime_auto_unreadable_state`,
Testname-Muster mit `selectionStartsEmpty`/`autoUnknown`-Familie); die Belegpflicht wiegt hoeher
als die urspruengliche Namensliste im Plan.

| Nr | Szenario | Vorbedingung | Handlung | Messung | Erwartung | JUnit-Referenz |
|---|---|---|---|---|---|---|
| S-1 | Einzelauswahl beginnt leer | Projekt mit ≥ 3 Dateien, Stick gesteckt | USB-Export → „Einzelne Dateien" | Foto | kein Haken, Zaehlung „0 von n", Exportknopf gesperrt | `initialExportSelection_selectionStartsEmpty`, `exportStartEnabled_singleModeNothingSelected_false` |
| S-2 | Alle / Keine | wie S-1 | „Alle auswaehlen", dann „Keine auswaehlen" | Foto | n von n → Knopf aktiv; 0 von n → gesperrt | `selectionCount_allSelected_equalsFileCount`, `exportStartEnabled_singleModeOneSelected_true` |
| S-3 | Vollprojekt unveraendert | wie S-1 | „Komplettes Projekt" → Exportieren | `ls <Stick>/DrainQ/<Nr>/` | alle Dateien, wie Welle 26 | `exportStartEnabled_fullProjectWithFiles_true`, `exportStartEnabled_fullProjectNoFiles_false` |
| S-4 | Ueberschrieben, Automatik unlesbar | nur Fake / Geraet mit werfendem `Settings.Global.getInt` | Uebernehmen | Foto | dritter Text (`datetime_auto_unreadable_state`), nicht „war aus" | `overwrittenCause_bothNull_unreadableAutoIsNotReportedAsOff`, `overwrittenCause_oneNullOneFalse_unreadable` |
| S-5 | Ueberschrieben, Automatik aus | `auto_time=0`, Uhr wird dennoch zurueckgestellt | Uebernehmen | Foto | unveraendert `datetime_overwritten_unknown` | `overwrittenCause_bothFalse_off` |
| S-6 | Zonen zweistufig | Zone `Europe/Berlin` | Zeitseite oeffnen | Foto | Stufe 2 der Gruppe `Europe` offen, Berlin mit Haekchen; „Alle Gruppen" fuehrt zur Gruppenliste, „Weitere" steht am Ende | `zoneGroups_sumOfGroupsEqualsInput`, `groupOf_europeBerlin_isEurope`, `zoneGroups_emptyGroupLast` |
| S-7 | Suche schlaegt Gruppen | — | „berl" tippen | Foto | flache Trefferliste | `filterZones_queryBerl_findsBerlin` (bestehend) |
| S-8 | Nichts erfunden, nichts weggelassen | — | Gruppen durchzaehlen | Test | Summe aller Gruppen = Geraeteliste | `zoneGroups_realJdkList_coversAllIds` |
| S-9 | Kreuz im Abspielmodus | Video im Projekt | 10 Tipps auf das Kreuz | `getevent` + `logcat -s DqKreuz` | 10/10 `onClick` — **nicht hergestellt ohne Geraet** | — |
| S-10 | Meterwert ueber dem Band | Inspektion, Band sichtbar | — | `logcat -s DqBand`, Screenshot | Bandhoehe 122 dp, Meterwert vollstaendig ueber den Kacheln — **nicht hergestellt ohne Geraet** | — |
| S-11 | Meterwert im Video | „Mit Einblendung", `osd_show_meter` AN | 10-s-Aufnahme | Sichtpruefung Video | Meterzeile unten links im Bild; bei „Ohne Einblendung" nicht — **nicht hergestellt ohne Geraet, per Erhebung am Code (BERICHT.md) als Einstellung erklaert** | — |

## Nicht-Reichweite dieser Welle

- `UsbExportNames.kt` gesperrt (`L-213c`), nicht gelesen, nicht angefasst.
- `SystemTimeSetter.kt` unveraendert — die Zeitwelle ist abgenommen.
- `InspectionScreen.kt` NICHT im Scope — Z-4 ist eine Erhebung, kein Bau (Fundstellen
  `InspectionScreen.kt:875-876`, `:1770-1811`, `:1938-1952` — siehe `BERICHT.md`).
- `VideoPlaybackDialog.kt` gelesen, NICHT geaendert — Z-3-Ursache ist ohne Geraet nicht
  belegbar (siehe `BERICHT.md`, drei Kandidaten K1/K2/K3, keiner entschieden).
- Android-ICU-Zonenliste (Geraet) nicht gemessen — Gruppierung ist listenneutral getestet
  (`zoneGroups_realJdkList_coversAllIds` laeuft gegen die JVM-Liste des Testlaufs, nicht gegen
  das Geraet).
- `src/l10n/resources/pl-PL.json` nicht angefasst (`L-31`).
- Sechs neue Schluessel nur `de`/`en` (`usb_select_all`, `usb_select_none`,
  `usb_selected_count`, `datetime_auto_unreadable_state`, `datetime_zone_group_other`,
  `datetime_zone_groups`) — 33 weitere Sprachen fallen ueber `getString` still auf Deutsch
  zurueck (L-214, benannte Luecke: 6 × 33 = 198 fehlende Texte).

## Am Geraet — nicht hergestellt

S-9, S-10, S-11 sowie die Bildschirm-Auspraegung von S-1, S-2, S-4, S-6 (Foto-Messungen) sind in
dieser Welle **nicht hergestellt** (L-213, kein Geraet am 15.09.2026 verfuegbar) — nur die
reinen Funktionen sind gegen JUnit gemessen. `WIRKUNG.json` fuehrt Z-3/Z-4 bewusst NICHT als
Pruefung, weil eine Geraetemessung nicht durch einen Wirkungsnachweis erzwingbar ist.
