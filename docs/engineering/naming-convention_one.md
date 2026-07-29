
## Daten

| Bedeutung       | Variable          | Wert                                                  |
| --------------- | ----------------- | ----------------------------------------------------- |
| Projekttitel    | {projekt-titel}   | DrainQ.ONE                                             |
| Projektversion  | {projekt-version} | laufende App-Version (aktuell 0.5.x-beta)             |
| Projektsymbol   | {projekt-symbol}  | **one**                                               |

<!-- Abweichung von der Regelwerks-Formel {projekt-titel}-{projekt-version} bewusst:
     das Symbol „one" ist im Repo, in den Dateinamen (*_one.md) und in der
     Projektmemory bereits etabliert. Es bleibt stabil über App-Versionen hinweg;
     die App-Version wird je Disziplindokument im Kopf (Feld „Version") geführt. -->

---

## Basisnamen

| Bedeutung        | {disziplin-name}   | Wert              |
| ---------------- | ------------------ | ----------------- |
| Initialisierung  | {name-inception}   | 00-inception      |
| Analyse          | {name-analyse}     | 01-analysis       |
| Entwurf          | {name-entwurf}     | 02-project        |
| Implementierung  | {name-implement}   | 03-implementation |
| Test             | {name-test}        | 04-testing        |
| Auslieferung     | {name-deployment}  | 05-deployment     |
| Wartung          | {name-wartung}     | 06-maintenance    |
| Projektstruktur  | {name-struktur}    | naming-convention |

Die englischen Dateipräfixe sind sprachunabhängige IDs, keine Übersetzungsgegenstände.

---

## Kataloge

| Bedeutung           | Variable          | Wert                                        |
| ------------------- | ----------------- | ------------------------------------------- |
| Repository-Wurzel   | {projekt-katalog} | `C:\Projekte\drainq.one\`                   |
| Engineering-Katalog | {eng-katalog}     | `{projekt-katalog}docs\engineering\`        |
| Code-Katalog        | {code-katalog}    | `{projekt-katalog}app\`                     |
| Quellcode-Katalog   | {src-katalog}     | `{code-katalog}src\main\`                   |
| Build-Katalog       | {build-katalog}   | `{code-katalog}build\`                      |
| Distributionskatalog| {dist-katalog}    | `{projekt-katalog}` (signierte APK im Root) |

<!-- Abweichung von der Regelwerks-Ordnerstruktur ({projekt-katalog}{name-disziplin}\):
     Die Disziplindokumente liegen gebündelt unter docs\engineering\ statt in je
     einem eigenen Wurzelordner je Disziplin. Grund: Der Code liegt bereits als
     etabliertes Android-Gradle-Projekt (app\src\main\...) im Repo; die
     Engineering-Dokumentation wird additiv daneben gelegt, nicht der Code umgezogen.
     Der von Gregory geforderte getrennte ai_build\-Katalog ist bei einem
     Ein-Personen-/CC-Build-Setup nicht erforderlich und entfällt (Begründung im
     03-implementation zu dokumentieren). -->

---

## Namen der Projektdateien

| Bedeutung          | Variable                       | Wert                                        |
| ------------------ | ------------------------------ | ------------------------------------------- |
| Disziplindatei     | {name-datei-[disziplin]}       | {name-[disziplin]}_one.md                   |
| Audit-Datei        | {name-audit-[disziplin]}       | {nr}-audit_one.md                           |
| Change-log         | {name-datei-changelog}         | 06-change-log_one.md                        |

Beispiele: `01-analysis_one.md`, `01-audit_one.md`, `02-project_one.md`, `06-change-log_one.md`.
Alle Disziplin- und Audit-Dateien liegen unter `docs\engineering\`.

---

## Traceability-IDs (kanonisch, sprachunabhängig)

`STK` Stakeholder · `RIS` Risiko · `ACT` Akteur · `ASM` Annahme · `CON` Einschränkung ·
`REF` funktionale Anforderung · `REN` nichtfunktionale Anforderung · `UC` Anwendungsfall ·
`AC` Akzeptanzkriterium · `ENT` Entität · `MOD` Modul · `SCR` Ansicht ·
`TU`/`TI`/`TS`/`TA` Unit-/Integrations-/System-/Akzeptanztest · `CHG` Änderungsantrag.

Kette: **ACT → REF/REN → UC → AC → MOD → Test.**

---

## Kopplungsregel Vorlage ↔ Audit

Jede Audit-Checkliste referenziert die Abschnittsnummern ihrer Disziplindatei. Wird die
Struktur eines Disziplindokuments geändert, ist die zugehörige Audit-Datei im selben
Arbeitsschritt anzupassen. Ein Audit gegen veraltete Nummern ist ungültig.
