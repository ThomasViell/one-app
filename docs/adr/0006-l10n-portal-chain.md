# ADR-0006 — Portalgestuetzte Uebersetzungskette fuer DrainQ.ONE

**Status:** Umgesetzt — Welle `l10n-anschluss`, CEO-Entscheide 16./17.09.2026 (`ENTSCHEIDE_l10n_2026-09-16.md`, `PLAN_NACHTRAG` der Welle).
**Datum:** 2026-09-19
**Entscheider:** Thomas Viell (CEO)
**Betrifft:** `drainq.one` (Repo, Zweig `welle/l10n-anschluss`, nicht gemergt), `drainq.web` (Portal, nur gelesen)
**Grundlage:** Synthese `_ketten/l10n-synthese/SYNTHESE_l10n-workflow_2026-08-24.md`, gleicher SOLL-Ablauf wie DrainQ.SA

---

## 1. Ausgangslage

Die ONE trug bis zu dieser Welle 11.091 Zeilen statische Kotlin-Maps fuer 35 Sprachen in
`LocalizationManager.kt`, hinter einem `BETA_LANGUAGE_GATE` auf zwei Sprachen (de/en) begrenzt.
Ein Haendler, der seine Sprache pflegen wollte, haette Kotlin anfassen und einen neuen Bau
abwarten muessen — fuer den vom CEO vorgegebenen Ablauf (Entwickler schreibt Deutsch, DeepL
uebersetzt nach Englisch, jede weitere Sprache uebersetzt und prueft ein Haendler-Partner selbst
im Portal) war die ONE damit technisch nicht gehbar, obwohl `drainq.web` die Sprachverwaltung
seit dem 24.08.2026 fuer die ONE bereits bereitstellt (`L10nApiController.cs`, `app: "one"`).

## 2. Entscheidung: die Kette

```
getString(key, lang):
  pack[lang]        -- nachgeladenes Portal-Paket (fuer de/en: Asset im Bau, sonst LocalePackStore)
  -> bundleEn        -- Asset-EN, unabhaengig von der gewaehlten Sprache
  -> Map[lang]        -- bestehende 35-Sprachen-Map, Uebergangsglied (E-4)
  -> Map[en]          -- Map-EN
  -> Schluesselname
```

**Deutsch ist ab dieser Welle kein Rueckfallglied mehr fuer `lang != de`.** Der fruehere stille
Rueckfall auf Deutsch (L-214) war der Fehler, den die DrainQ.SA im selben Zeitraum fuer ihren
eigenen Katalog abstellt — der Berater hatte dem CEO zwischenzeitlich vorgeschlagen, den
Rueckfall bei „Deutsch" zu belassen, ohne den SA-Workflow zu kennen; das wurde am 16.09.2026
berichtigt (`PLAN_NACHTRAG`, Abschnitt 1, Punkt 4 des Auftrags).

### 2a. Scope-Entscheidung: `one,shared`, nicht `one`

Gemessen am 17. und erneut am 18.09.2026 gegen `https://license.drainq.com`:
`scope=one` liefert 458 (de) / 457 (en) Schluessel, `scope=one,shared` 469 / 468. Die 11
Differenzschluessel (`Camera.Cable.*`, `Camera.Control.*`, `Camera.Telemetry.*`,
`Connection.Wifi.*`, `Project.Label`) benutzt die ONE heute nicht — aber der Mai-2026-Commit
„SHARED-Konzept entfaellt" aus einer fruehen Arbeitskopie ist damit widerlegt: der Scope
existiert und traegt Schluessel. Entscheidung: `one,shared`, die Obermenge, damit ein
kuenftiger SHARED-Schluessel nicht still fehlt (Fehlerklasse L-216: zwei Bezuege, ein gruenes
Ergebnis).

### 2b. Nur ETag, kein `If-Modified-Since`

Gemessen: `If-None-Match` mit dem gelieferten ETag liefert `304`; `If-Modified-Since` mit dem
gelieferten `Last-Modified` liefert `200` (erwartet 304). Ursache vermutlich ein
Bruchteilsekunden-Vergleich im Portal-Code (`TranslationController.GetScopedTranslationAsync`),
am Code gelesen, nicht am Server gemessen — Zeile fuer `drainq.web` in die Queue (siehe unten).
Der Client (`L10nPortalClient`) sendet deshalb ausschliesslich `If-None-Match`.

### 2c. Die Map bleibt — als Uebergang, nicht als Rueckfall auf Deutsch

E-4 (Auftrag Abschnitt 4, Stand 19.09.2026): keine Zeile der 35 Sprach-Maps wird entfernt.
Gemessen am 17.09.2026 (Kettenordner `_ketten/l10n-anschluss/PLAN.md`, Abschnitt 1.1, Skript
`cmp.py`: 576 eindeutige de-Map-Schluessel gegen 469 Portal-Schluessel `scope=one,shared`,
Portalstand `Last-Modified` 13.07.2026) fehlten im Portal 123 Map-Schluessel; fuer sie war die
Map damals die einzige Quelle — deshalb blieb sie im dritten/vierten Kettenglied, nicht als
Rueckfall, sondern weil das Portal der Map nachlief. Beide Saetze sind seit 24.09.2026
ueberholt, siehe Nachtrag unten.

**Nachtrag 24.09.2026 (W-33e, CEO-Freigabe):** E-4 schuetzt Werte **benutzter** Schluessel.
Schluessel ohne Verbraucher in `app/src/main/java` (Kriterium: `"<schluessel>"` kommt in
keiner `.kt`-Datei ausser `LocalizationManager.kt` vor; Waechter `L10nToteSchluesselTest`)
werden entfernt — 83 aus allen 35 Bloecken: 81 nach dem Kriterium plus 2 von Hand entschieden
(`inspection`, `reports` — ihre je 8 Literalstellen sind Routen-, Icon- und Ordnernamen, kein
Uebersetzungsaufruf; der Waechter sieht diese zwei nicht, Blindstelle in seinem KDoc). −2.061
Zeilen: 2.060 Eintraege und ein verwaister Kommentar; kein Wert geaendert; Belegliste
`_ketten/w33e-neu/TOTE_SCHLUESSEL.md`, Kettenordner. Portal-Abgleich 24.09.2026 (GET
`de.json?scope=one,shared`, 602 Schluessel): von den 588 de-Map-Schluesseln fehlen im Portal
2 — `logo_default_label` (Verbraucher `SettingsScreen.kt:493`) und `ok` (u. a.
`DateTimeScreen.kt:254`); beide bleiben. Die 83 entfernten Schluessel fuehrt das Portal
weiter; das Import-Werkzeug sortiert sie als NUR-PORTAL ein und loescht nicht
(`tools/l10n-import-to-portal.ps1:186`) — Loeschen dort ist eine Portal-Welle.

## 3. Sichtbarkeit: das Gate faellt, die Liste kommt vom Portal (Z-5)

`BETA_LANGUAGE_GATE` ist entfernt. `availableLanguages` ist eine `StateFlow<List<AppLanguage>>`,
deren Inhalt **ausschliesslich** von `GET /api/locales?app=one` kommt (bei Nichterreichbarkeit
die zuletzt gespeicherte Liste, sonst das Paket de/en). Die 33 uebrigen Map-Sprachen werden
**nicht** angeboten, bis das Portal sie fuehrt, auch wenn ihr Kotlin-Block im Repo steht
(Sicherung `availableLanguages_neverExposesMapOnlyLanguage`). Heute (19.09.2026) fuehrt das
Portal fuer die ONE genau `de` und `en` — die sichtbare Aenderung fuer den Bediener ist damit
**null**, der Grundsatz gilt trotzdem ab sofort (CEO-Entscheid PLAN_NACHTRAG R-1).

## 4. Herkunftswaechter (Z-7)

`assets/l10n/HERKUNFT.md` + `L10nHerkunftTest.kt`: jeder fremdsprachige Map-Block (33) und jede
`assets/i18n/<code>.json`-Datei (33) traegt einen SHA-256 gegen den Altbestand
(„nicht belegt, eingefroren 17.09.2026" — Verfasser aus der Git-Historie nicht mehr
feststellbar). Eine Aenderung an einem fremdsprachigen Wert ohne begleitende Aktualisierung
dieser Datei macht den Test rot. Damit ist die CEO-Zusicherung „nur Portal/DeepL/Partner
uebersetzt, nicht die KI" (Regel 12) erstmals **maschinell** durchgesetzt statt nur behauptet —
genau die Luecke, die in der DrainQ.SA 156 unbelegte polnische Werte (davon 29
modellgeneriert) durchgelassen hat.

## 5. Rueckfallzaehler (Z-6)

`FallbackCounter` zaehlt jeden Uebergang auf Englisch bzw. auf den Schluesselnamen und haelt
die letzten zehn fehlenden Schluessel. Die Zahl wird in der Karte „Sprachpakete" auf den
Einstellungen gespiegelt und uebersteht — Muster identisch zu Welle 28
(`SettingsViewModel.persistDiagnostic`) — einen Neustart.

## 6. Standardbezeichnungen folgen der Sprache (Z-8)

`DamagePresetRepository` legte vor dieser Welle beim ersten Start sieben aufgeloeste **Texte**
in den DataStore und las danach nur noch Texte — ein Sprachwechsel aenderte sie nicht. Ab
dieser Welle traegt jeder Standardeintrag seinen Schluessel (folgt der Sprache), jeder eigene
oder editierte Eintrag seinen Text (bleibt stehen). Migration eines Altbestands erkennt
Standardwerte gegen alle bekannten Sprachwerte (Map + Pakete).

## 7. Nicht Teil dieser Welle

- **Portal-Nachzug**: Werkzeug fertig (Welle `portal-nachzug`, Zweig `welle/portal-nachzug`,
  gemessen 21.09.2026 gegen das lebende Portal: 588 eindeutige Map-Schluessel, 135 NEU,
  8 abweichende Werte — je Fall entschieden, 4 mit Repo-Wert freigegeben, 3 Portal gewinnt,
  1 toter Schluessel —, 474 `help.*` bleiben aussen). Das Skript sendet nur Deutsch (kein
  `en`-Block, kein `sourceEn`), gleicht vor dem Paketbau mit dem Portal ab und sendet
  ausschliesslich NEU + freigegebene ABWEICHEND. Runde 3 (NACHBESSERUNG, CEO-Entscheide
  21.09.2026 spaet): die Fremd-Bereiche werden in de **und en** plus der SA-Sicht
  `sa/{lang}.json` geholt; FREMD-Schluessel (auch `ok` aus HMX und die 55 nur in en
  liegenden HMX-Referenzen) fallen aus NEU heraus; Positivliste, Mindestumfang je Bereich
  und Sprache und Pflicht-Schluessel `ok` machen jeden verdaechtig leeren oder zu kleinen
  Fremd-Bestand zum Abbruch (Exit 4); die Untergrenze der Haupt-Ansicht liegt bei 460
  (Runde 4/D-7: die Vorrunden-Grenze 450 liess einen um 19 gekuerzten Abruf noch durch,
  der um 68 gekuerzte Abruf des Pruefers scheitert weiterhin). **Englisch fuer die neuen
  Schluessel (Neustart, DeepL) ist nicht mehr Teil dieser Welle** (CEO-Schnitt
  22.09.2026, NACHBESSERUNG Runde 4, K-2): die Ursache (DeepL-Filter ohne Beachtung der
  Schreibweise, sprachweites Veroeffentlichen, kein Neustart-Befehl im Repo) liegt im
  Portal (`drainq.web`), nicht im ONE-Repo — Befunde D-1/D-10 namentlich in
  `_ketten/portal-nachzug/belege/r4_portal_welle_befunde.md`, eigene Welle `drainq.web`.
  Die vier Freigaben von Hand bleiben unabhaengig davon moeglich, Zielwerte traegt der CEO
  in `_ketten/portal-nachzug/R2_EN_ZIELWERTE.md` ein. Der Upload selbst ist CEO-Akt und
  laeuft nach dieser Runde und ihrer Pruefung; Messungen in
  `_ketten/portal-nachzug/BERICHT.md`.
- **Werkseinrichtung/Vorladen der Landessprache** (E-7): haengt an W-30a, eigene Welle.
- **Entfernen der 35-Sprachen-Map**: eigene Welle mit eigener Pruefung, sobald belegt ist, dass
  das Nachladen am Geraet traegt.

## 8. Nicht hergestellt (kein Geraet in dieser Welle)

Zwischenspeicher-Ueberleben von Neustart/Flugmodus (H-4), Ladezeit der Assets im `init` am
RK3588 (H-5), Portal-Zwischenspeicher vor dem Server (H-6), jede sichtbare Oberflaeche. Auflage
vor dem naechsten Merge: Geraeteabnahme entlang der Szenarien in
`docs/auftraege/SZENARIEN_l10n-anschluss.md`.
