# SZENARIEN — Welle l10n-anschluss (19.09.2026)

Erfolgskriterien aus `AUFTRAG.md`: Z-1 Portalabruf mit ETag/Scope-Messung; Z-2 Paket +
Zwischenspeicher, offline verfuegbar; Z-3 Sprachverwaltung in den Einstellungen; Z-4 Kette
Sprache → Englisch → Map → Schluesselname; Z-5 BETA-Gate faellt, Liste vom Portal; Z-6
Rueckfallzaehler statt stillem Rueckfall; Z-7 Herkunftswaechter; Z-8 Standardbezeichnungen
folgen der Sprache. Je Szenario: Vorbedingung, Handlung, Messbefehl, Erwartung, JUnit-Referenz.

**Geraet: keines in dieser Welle** (`adb devices` leer). Die Szenarien S-2 bis S-4, S-7 und
S-10 sind **nicht hergestellt** — hier als Pruefplan fuer eine kuenftige Geraetesitzung
festgehalten, nicht als erledigt behauptet. S-1, S-5, S-6, S-8, S-9, S-11, S-12 sind am
Schreibtisch (JUnit, Robolectric, echter Portallauf) belegt; die Erwartung „am Geraet" bleibt
trotzdem offen, bis ein Bediener sie klickt.

## S-1 — Sprachliste vom Portal

- Vorbedingung: Netz vorhanden, Portal erreichbar.
- Handlung: Einstellungen oeffnen.
- Messbefehl: `L10nPortalLiveTest.fetchLocales_containsDeAndEn` (opt-in `-Dl10n.live=true`).
- Erwartung: Liste entspricht `GET /api/locales?app=one` (heute `de`, `en`), Kopfzeile der Karte
  „Sprachpakete" nennt „Liste vom Portal".
- JUnit: `LocalizationManagerListTest.availableLanguages_isPortalListNotMapList`,
  `L10nPortalLiveTest`. **Sichtbare Oberflaeche am Geraet: nicht hergestellt.**

## S-2 — Kein Netz, Liste aus Speicher

- Vorbedingung: S-1 einmal gelaufen (Liste war schon gespeichert), Flugmodus an.
- Handlung: Einstellungen oeffnen.
- Messbefehl: Foto der Karte „Sprachpakete".
- Erwartung: gespeicherte Liste, Hinweiszeile „Portal nicht erreichbar — gespeicherter Stand".
- JUnit: `LocalizationManagerListTest.availableLanguages_portalUnreachable_usesStoredListThenBundle`.
  **Geraet: nicht hergestellt.**

## S-3 — Sprachpaket laden

- Vorbedingung: Portal veroeffentlicht eine dritte Sprache (heute keine — Portal fuehrt fuer
  die ONE nur `de`/`en`, `pl.json` → 404, gemessen).
- Handlung: in der Karte „Sprachpakete" bei der dritten Sprache „Laden" antippen.
- Messbefehl: Foto, Zustandszeile wechselt auf „geladen" mit Groessenangabe.
- Erwartung: Texte der Sprache erscheinen in der UI nach Auswahl.
- JUnit: `L10nPortalClientTest.fetchBundle_*`, `LocalePackStoreTest.save_thenNewInstanceReads_isFieldEqual`.
  **Am Portal heute nicht herstellbar** (nur de/en veroeffentlicht) **und Geraet: nicht hergestellt.**

## S-4 — Paket uebersteht Neustart und Flugmodus

- Vorbedingung: S-3.
- Handlung: App beenden, Flugmodus an, App starten.
- Messbefehl: Foto, Sprache und Texte unveraendert.
- Erwartung: kein Netz heisst nicht „keine Sprache".
- JUnit: `LocalePackStoreTest` (belegt nur: eine zweite Instanz liest, was eine erste
  geschrieben hat — dateibasiert, nicht prozessgebunden). **Geraet: nicht hergestellt.**

## S-5 — Fehlender Schluessel faellt auf Englisch, nicht Deutsch

- Vorbedingung: gewaehlte Sprache ≠ Deutsch, ein Schluessel fehlt in Paket und Map dieser
  Sprache, ist aber in Englisch vorhanden.
- Handlung: Bildschirm mit diesem Schluessel oeffnen.
- Messbefehl: Textwert am Schirm, Diagnosezeile in der Karte „Sprachpakete".
- Erwartung: englischer Text sichtbar, Zaehler „Rueckfaelle seit Start: EN" um eins hoeher.
- JUnit: `LocalizationManagerChainTest.getString_missingInLanguage_fallsBackToEnglishNotGerman`
  (echter Rot-Beweis gegen den Ausgangskopf, siehe BERICHT.md).

## S-6 — Schluesselname als letzte Stufe

- Vorbedingung: Schluessel nirgends vorhanden (weder Paket noch Map, keine Sprache).
- Handlung: entsprechender Bildschirm.
- Messbefehl: Textwert, Diagnosezeile.
- Erwartung: Schluesselname sichtbar statt eines deutschen Ersatzwerts, Zaehler
  „Schluesselname" um eins hoeher.
- JUnit: `LocalizationManagerChainTest.getString_missingEverywhere_returnsKeyName` (echter
  Rot-Beweis: vor dieser Welle lieferte dieselbe Konstellation den **deutschen** Wert).

## S-7 — Diagnosezeile uebersteht Neustart

- Vorbedingung: S-5 einmal ausgeloest.
- Handlung: App neu starten, Einstellungen oeffnen.
- Messbefehl: Foto.
- Erwartung: letzter Zaehlerstand sofort sichtbar, ohne erneuten Rueckfall.
- JUnit: Muster `SettingsViewModel.persistDiagnostic`/`loadPersistedDiagnostic` (Welle 28),
  hier `persistL10nDiagnostic` — kein eigener Persistenztest in dieser Welle, das Muster ist
  identisch uebernommen. **Geraet: nicht hergestellt.**

## S-8 — Standardbezeichnung folgt der Sprache

- Vorbedingung: Schadenspresets unveraendert (keine eigenen, keine editierten).
- Handlung: Sprache von Deutsch auf Englisch wechseln, Schadensdialog oeffnen.
- Messbefehl: erster Eintrag der Liste.
- Erwartung: „Riss" wird zu „Crack" (oder dem jeweiligen englischen Standardwert), ohne dass
  der Bediener etwas am Preset getan hat.
- JUnit: `DamagePresetRepositoryTest.defaults_followLanguageChange` (echter Rot-Beweis: vor
  dieser Welle blieb die Bezeichnung in der Startsprache stehen).

## S-9 — Eigene Bezeichnung bleibt stehen

- Vorbedingung: eine eigene Bezeichnung angelegt oder eine Standardbezeichnung editiert.
- Handlung: Sprache wechseln.
- Messbefehl: der betroffene Eintrag.
- Erwartung: Text bleibt unveraendert (er ist jetzt ein Nutzerdatum).
- JUnit: `DamagePresetRepositoryTest.customAndEditedEntries_stayVerbatim` (Sicherung, vorher
  wie nachher gruen).

## S-10 — Altbestand wird erkannt (Migration)

- Vorbedingung: Geraet mit v1-Ablage (Presets als reine Texte im DataStore, aus einer Version
  vor dieser Welle).
- Handlung: Update auf diese Welle einspielen, Schadensdialog oeffnen.
- Messbefehl: Foto vor/nach Sprachwechsel.
- Erwartung: als Standard erkannte Texte folgen jetzt der Sprache, eigene Texte bleiben stehen.
- JUnit: `DamagePresetRepositoryTest.legacyTextList_isMigratedToKeys`. **Geraet: nicht
  hergestellt** — die Migration selbst ist am Schreibtisch belegt (Robolectric, echter
  Altbestand-Datensatz vorbelegt).

## S-11 — Fremdsprachiger Wert ohne Herkunftsnachweis macht den Waechter rot

- Vorbedingung: ein Bearbeiter aendert einen Wert in einem der 33 fremdsprachigen Map-Bloecke,
  ohne `HERKUNFT.md` nachzufuehren.
- Handlung: Testlauf.
- Messbefehl: `.\gradlew.bat :app:testDebugUnitTest --tests "*L10nHerkunftTest*"`.
- Erwartung: `mapForeignBlocks_matchHerkunftSha256` schlaegt fehl, Fehlertext nennt den
  betroffenen Sprachcode.
- JUnit: `L10nHerkunftTest.mapForeignBlocks_matchHerkunftSha256` — Mutationsbeweis mit
  SHA-256 vor/nach in `belege/rb1_mutation*.txt`.

## S-12 — Scope-Vollstaendigkeit (echter Portallauf)

- Vorbedingung: Portal erreichbar.
- Handlung: `L10nPortalLiveTest` mit `-Dl10n.live=true` ausfuehren.
- Messbefehl: `system-out` der Testausgabe.
- Erwartung: `scope=one,shared` ist eine Obermenge von `scope=one` (Schluesselmenge), beide
  Zahlen stehen in der Ausgabe, alle sieben `damage_type_*`-Presets sind im Paket enthalten.
- JUnit: `L10nPortalLiveTest.fetchBundle_de_oneSharedSupersetOfOne_containsAllDamageTypePresets`.

## Nachzug l10n-auflagen (19.09.2026)

Vier Ziele, alle am Schreibtisch belegt (Robolectric; Geraetefaehigkeit siehe je Szenario):

- **Z-1** — beschaedigter Preset-Bestand (DataStore) stoppt die App nicht mehr: Rueckfall auf
  die Standardliste. JUnit: `DamagePresetRepositoryTest.corruptStored_*` (3 Tests, Rot-Beweis
  mit `JsonSyntaxException` gegen den Ausgangskopf). Geraet: nicht hergestellt.
- **Z-2** — der Herkunftswaechter erhebt die 33 fremdsprachigen Bloecke aus der Quelle
  (`LocalizationManager.kt`) und die Dateien aus `assets/i18n` selbst; ein neuer Block ohne
  Herkunftsvermerk macht `L10nHerkunftTest` rot (Mutationsbeweis MUT-1/MUT-2, SHA-256
  vor/nach). JUnit: `noForeignMapBlockWithoutHerkunft`, `noHerkunftEntryWithoutBlock`.
- **Z-3** — gespeicherte Sprache steht vor dem ersten Bild (synchrones Lesen in `init`).
  JUnit: `LocalizationManagerStartLanguageTest`; `Z3_READ_MS_MAX` unter der 50-ms-Grenze
  (H-5). Geraet: Aufblitzen nach Neustart nicht hergestellt.
- **Z-4** — vier Saetze: `L10nBundleLoadTimeTest` (H-5), Ruecknahmebedingung am
  ETag-Umweg, Klassenkopf `BundleGapTest` auf „einer", WIRKUNG-Muster der Vorwelle
  verankert. JUnit: `L10nBundleLoadTimeTest` (H5_BUNDLE_LOAD_MS < 50, Assetgroessen).
  Geraet: H-5 am RK3588 nicht hergestellt.

Welle `l10n-auflagen` (Kopf im WIRKUNG-Beleg der Welle), 630 Tests / 0 Fehler im Volllauf
(`--rerun-tasks -Dl10n.live=true`).
