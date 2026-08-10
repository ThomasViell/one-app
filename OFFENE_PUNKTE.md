# Offene Punkte — DrainQ.ONE

**Stand:** 2026-08-05

Diese Liste löst `OFFENE_TODOS_2026-07-01.md`, `OFFENE_TODOS_2026-07-09.md` und `OFFENE_TODOS_2026-07-12.md` ab. Die drei Dateien liegen unverändert im Archiv (`docs/archiv/2026-07/`) und werden nicht mehr gepflegt.

**Worauf sie beruht:** die drei genannten Listen, geprüft gegen `PROJECT_STATUS.md`, die Berichte vom 29./30.07. unter `docs/archiv/2026-07/`, die ADRs unter `docs/adr/` sowie Code und `git log` auf `master`. Punkte, die aus Gesprächen stammen und in keiner Datei standen, sind mit Quelle „Gespräch" gekennzeichnet. Einige Punkte kommen aus `PROJECT_STATUS.md` statt aus den drei Listen — auch das steht in der Quellenspalte.

**Wichtig:** `PROJECT_STATUS.md` hat Stand 29.07. und führt Merge und Tag noch als ausstehend. Beides ist am 30.07. passiert (Tag `v0.9.0` auf `a1afaf7`, `feature/dual-mode` ist in `master` enthalten — nachgeprüft). Die Statusdatei ist an dieser Stelle überholt.

**„Status unklar"** heißt: kein Beleg für Erledigung gefunden, aber auch kein Beleg dagegen. Im Zweifel als offen geführt.

---

## Blockiert Auslieferung

| Punkt | Seit | Quelle | Was fehlt konkret | Wer handelt | Blockiert |
|---|---|---|---|---|---|
| USB-Export unbrauchbar | 09.07. | Liste 09.07. | Beim Export auf den USB-Stick bekommen die Dateien kryptische Namen, und Fotos und Videos lassen sich danach nicht öffnen. Der Kunde kann seine Aufnahmen also nicht weitergeben. | Entwicklung | Auslieferung |
| Signaturschlüssel — formale Freigabe | 09.07. | Liste 09.07. | Die Entscheidungsvorlage ist inzwischen geschrieben (ADR-0005), sie trägt aber noch den Status „wartet auf CEO-Entscheidung", während der Plattformschlüssel in 0.9.0 bereits benutzt wird. Es fehlt die formale Freigabe, damit Praxis und Beschluss übereinstimmen. | CEO-Entscheidung | Auslieferung |
| USB-Stick für die Fertigung | neu 05.08. | Gespräch | Der Ordner `tools/werkseinrichtung` muss ohne den Unterordner `logs` auf einen Stick für Sven Hartmann. Ohne diesen Stick kann die Fertigung nicht anfangen. (`logs` enthält aktuell ein echtes Geräteprotokoll und darf nicht mitgehen.) | CEO-Entscheidung | Auslieferung |
| Treiberfrage Einrichtungsrechner | neu 05.08. | Gespräch | Ungeklärt ist, ob ein Rechner, der noch nie eine ONE gesehen hat, erst einen USB-Treiber braucht. In der Arbeitsanweisung und in den Skripten steht dazu nichts. Klärt sich erst am fremden Rechner. | Fertigung | Auslieferung |
| E-Mail an Sven Hartmann | neu 05.08. | Gespräch | Die Nachricht an `s.hartmann@nsp3ct.pro` ist noch nicht raus; der Text liegt vor, ein Outlook-Entwurf ließ sich nicht anlegen. Solange sie fehlt, weiß die Fertigung nicht, dass und wie sie anfangen soll. | CEO-Entscheidung | Auslieferung |
| Kamerarechte müssen in die Firmware | 01.07. | Liste 01.07. + `PROJECT_STATUS.md` | Die Regel, die das Kamerabild freischaltet, liegt nur in einer Zusatzschicht auf dem Gerät. Sie übersteht einen Neustart, aber kein Neuaufspielen der Firmware. Der Board-Lieferant muss sie fest in sein Werksabbild aufnehmen. | CEO-Entscheidung (Lieferant) | Auslieferung |
| Werksabbild neu erstellen | 09.07. | Liste 09.07. | Ein sauberes Ausgangsabbild für neue Geräte ist noch nicht erstellt. Sinnvoll erst, wenn der Punkt darüber geklärt und der Signaturschlüssel final ist — sonst zweimal Arbeit. | Fertigung | Auslieferung |

---

## Blockiert Entwicklung

| Punkt | Seit | Quelle | Was fehlt konkret | Wer handelt | Blockiert |
|---|---|---|---|---|---|
| Testdatei lässt sich nicht übersetzen | 14.07. | `PROJECT_STATUS.md` | `XmlExportTest.kt` benutzt einen Dienst, den es seit dem XML-Ausbau nicht mehr gibt. Die Datei ist damit nicht übersetzbar und blockiert die Gerätetests. Löschen oder ins Archiv. | Entwicklung | Entwicklung |
| Sprachen lassen sich nicht ins Portal nachladen | 13.07. | `PROJECT_STATUS.md` / `publish-one-l10n.README.md` | Der Aufräum-Zugang am Live-Portal antwortet seit dem 13.07. mit „nicht gefunden". Solange das so ist, lassen sich Französisch und Norwegisch nicht befüllen. | Entwicklung | Entwicklung |
| Widerspruch in der Screenshot-Doku | 14.07. | `PROJECT_STATUS.md` | Die Architekturnotiz ADR-0004 nennt ein anderes Werkzeug (Roborazzi), als tatsächlich gebaut wird (Paparazzi). Vor dem nächsten Screenshot-Lauf muss klar sein, welcher Text stimmt — der Code sagt Paparazzi. | Entwicklung | Entwicklung |
| Live-Bild friert während der Aufnahme ein | 09.07. | Liste 09.07. | Solange die ONE aufnimmt, steht das Live-Bild auf dem Tablet still, weil nur ein Encoder gleichzeitig laufen darf. Ungeklärt ist, ob die Hardware zwei zulässt. Für den Schiebekamera-Betrieb egal, im Tablet-Betrieb ein Funktionsverlust. | Entwicklung | Entwicklung |

---

## Blockiert nichts

| Punkt | Seit | Quelle | Was fehlt konkret | Wer handelt | Blockiert |
|---|---|---|---|---|---|
| Sekundenwächter schreibt Systemeinstellung | neu 10.08. | Kette `taskbar-balken` (Auftrag) | `applyNavigationMode` in `MainActivity.kt` schreibt im Sekundentakt `navigation_mode` zurück (nur bei Abweichung, aber der Lesevorgang läuft dauerhaft). Aus der Balken-Untersuchung ausgeklammert; zu prüfen, ob der Schreibzugriff überhaupt noch nötig ist, seit die Taskbar per Stash-Impuls behandelt wird. | Entwicklung | nichts |
| Mikrofonrecht auf einem Testgerät | neu 10.08. | Kette `taskbar-balken` (Auftrag) | Auf `e92df62d` ist `RECORD_AUDIO` nicht erteilt, auf `d7f67f1b` schon. Zusatzbefund 10.08.: Nach Entzug per `pm revoke` startet die Sprachnotiz-Aufnahme trotzdem ohne Rückfrage (App läuft als UID 1000). Zu klären, warum die Abfrage umgangen wird. | Entwicklung | nichts |
| Firmware-Drift der Testgeräte | neu 10.08. | Kette `taskbar-balken` (Auftrag) | Die beiden Test-ONEs tragen Firmware vom 19.05. und vom 30.06.2026. Solange sie auseinanderlaufen, ist unklar, welche Unterschiede vom Alter kommen und welche vom Gerät (`mActivityType=home` vs. `standard`). | Entwicklung | nichts |
| Drei Leerstellen in der Arbeitsanweisung | neu 05.08. | Gespräch | In `tools/werkseinrichtung/ARBEITSANWEISUNG_FERTIGUNG.md` sind drei Zeilen leer: wo das Protokoll abgelegt wird (Z. 44), wer bei einem roten Ergebnis entscheidet (Z. 58), und wo die Fertigung ein aktuelles Paket herbekommt (Z. 72). Ohne diese Angaben ist die Anweisung unvollständig. (Die Datei ist außerdem noch nicht in Git.) | CEO-Entscheidung | nichts |
| Auslieferungspaket nie im Echtbetrieb entstanden | neu 05.08. | Gespräch | Das Veröffentlichungsskript baut seit Kurzem ein Zip mit dem Einrichtungsordner (`tools/publish-one-release.ps1:240`). Bei einer echten Veröffentlichung ist es noch nie entstanden, also ungeprüft. Entsteht beim nächsten Release von selbst — nur hinsehen. | Entwicklung | nichts |
| Kein lebendes Release-Verfahren im Repo | neu 05.08. | Gespräch | `docs/RELEASE_PUBLISHING.md` existiert, beschreibt aber den alten GitHub-Weg und erwähnt das Lizenzportal mit keinem Wort. Der tatsächlich genutzte Portal-Weg steht nur im Skript selbst, in `docs/engineering/01–03` und in einem archivierten Einzelfall vom 13.06. Es fehlt eine lebende Anleitung, die den echten Weg beschreibt. | Entwicklung | nichts |
| Text USB-Export fehlt im Handbuch | 08.07. | `HANDBUCH_USB_EXPORT.md` | Der fertige Text zum USB-Export ist noch nicht in die Bedienungsanleitung und ins Hilfesystem eingeflossen. Die Datei bleibt im Repo-Root, bis das erledigt ist. | Entwicklung | nichts |
| Aufnahmeweg ohne Zwischenkopien | 01.07. | Liste 01.07. | Der Weg vom Kamerachip zum fertigen Video macht unnötige Zwischenschritte. Voraussetzung für volle 30 Bilder je Sekunde; mit gemessenen 27,55 nicht dringend. | Entwicklung | nichts |
| Tablet zeigt Akkustand der ONE falsch | 03.07. | Liste 01.07. | Im Fernbetrieb zeigt das Tablet „0 %" statt des echten Werts, weil das Feld nicht übertragen wird. Kosmetisch, aber irreführend. | Entwicklung | nichts |
| Volle Zuteilung Bild lokal ↔ Tablet | 01.07. | Liste 01.07. | Wenn lokale Anzeige und Tablet gleichzeitig das Bild wollen, gibt es noch keine saubere Regelung, wer Vorrang hat. | Entwicklung | nichts |
| Hotspot-Vollkette am Gerät abnehmen | 01.07. | Liste 01.07. | Der Hotspot mit eigenem Namen ist am Gerät nachgewiesen. Die ganze Kette — QR scannen, Tablet verbindet sich, Bild und Steuerung laufen — ist als Abnahme nirgends protokolliert. | Entwicklung | nichts |
| Videoweg Tablet↔ONE über WLAN | 01.07. | Liste 01.07. | Offen laut Code-Notiz: Abnahme des Videowegs über WLAN, mehrere gleichzeitige Empfänger, und die Frage, welcher Anschluss der richtige ist. **Status unklar** — der E2E-Test vom 03.07. deckt Teile ab, der Punkt ist aber nie abgehakt worden. | Entwicklung | nichts |
| Datenrahmen härten | 01.07. | Liste 01.07. | Der Empfang zählt Klammern statt den vorgesehenen Kopf auszuwerten. Umbau erst sinnvoll, wenn ein echter Mitschnitt vom Gerät vorliegt. | Entwicklung | nichts |
| SD/HD-Umschalter | 04.06. | Liste 01.07. | In der Projektanlage steht weiter ein Umschalter zwischen SD und HD (`ProjectFormScreen.kt:823`). Zu entscheiden: ganz raus und immer HD, oder SD echt umsetzen. Offen seit dem 04.06. | CEO-Entscheidung | nichts |
| Hotspot schließt Internet aus | 01.07. | Liste 01.07. | Solange die ONE einen Hotspot aufspannt, hat sie selbst kein Internet — also keine Cloud und keine Updates. Das ist technisch bedingt; zu entscheiden ist, wie die Bedienung damit umgeht. | CEO-Entscheidung | nichts |
| Wer hat Vorrang bei Licht und Sonde | 01.07. | Liste 01.07. | Verbindet sich ein Tablet, überschreibt es beim ersten Kontakt den Licht- und Frequenzstand der ONE (meist auf „Licht aus"). Bewusst so gebaut, aber nie entschieden. | CEO-Entscheidung | nichts |
| Sprachauswahl in der Beta | 07.06. | Liste 01.07. | Aktuell sind nur Deutsch und Englisch auswählbar, die übrigen 33 sind gesperrt. Faktisch ist damit entschieden — der Beschluss dazu fehlt aber. | CEO-Entscheidung | nichts |
| Helligkeitsregler im Tablet-Betrieb | 01.07. | Liste 01.07. | Zu bestätigen, ob der Regler für die Bildschirmhelligkeit im Tablet-Betrieb ausgeblendet werden soll. | CEO-Entscheidung | nichts |
| Umfang des Cloud-Logins | 01.07. | Liste 01.07. | Das Cloud-Login ist als Platzhalter angelegt und ehrlich als „kommt später" gekennzeichnet. Was es können soll, ist offen. | CEO-Entscheidung | nichts |
| App-Start wartet auf Einstellungen | 01.07. | Liste 01.07. | Beim Start blockiert das Laden der Einstellungen kurz die Oberfläche (`AppModule.kt:94`). Sauber zu lösen nur mit einem kleinen Umbau, nicht per Schnellkorrektur. | Entwicklung | nichts |
| Ruckler bei Foto und Schaden | 01.07. | Liste 01.07. | Beim Auslösen wird das Bild noch auf der Oberfläche komprimiert und geschrieben (`InspectionScreen.kt:502`), das ruckelt sichtbar während der Aufnahme. | Entwicklung | nichts |
| Update-Fortschritt stimmt nicht | 01.07. | Liste 01.07. | Der Fortschritt beim Update zeigt immer 0, die Stufe „wird installiert" erscheint nie, und ein Verlassen des Bildschirms bricht den Download ab. | Entwicklung | nichts |
| Wörterliste aufräumen | 01.07. | Liste 01.07. | Einige Textbausteine gehören zu längst entfernten Oberflächen und stehen noch in allen 35 Sprachblöcken; umgekehrt fehlen neuere Bausteine in 33 Sprachen. | Entwicklung | nichts |
| Kartenausschnitt räumt nicht auf | 01.07. | Liste 01.07. | Der Kartendialog gibt ein Bild nicht frei und lässt Zwischendateien im Zwischenspeicher liegen. **Status unklar** — seit dem 01.07. nicht erneut geprüft. | Entwicklung | nichts |
| Veraltete Aufrufe im Code | 01.07. | Liste 01.07. | Eine Handvoll Aufrufe gilt als veraltet (u. a. fünf Stellen `menuAnchor()`); sie funktionieren, sollten aber bei Gelegenheit nachgezogen werden. | Entwicklung | nichts |
| Ungenutzte Werte in der Einblendung | 01.07. | Liste 01.07. | Die Bildeinblendung nimmt zwei Werte entgegen, die sie nicht zeichnet — bewusst stehen gelassen, beim nächsten Umbau entfernen. | Entwicklung | nichts |
| Zeilenenden dauerhaft festlegen | 01.07. | Liste 01.07. | Die Regel `* text=auto eol=lf` fehlt weiterhin in `.gitattributes` (dort stehen nur Sonderfälle für `ops/`). Ohne sie melden Werkzeuge immer wieder Änderungen, die keine sind. Braucht einen eigenen, abgestimmten Durchgang. | Entwicklung | nichts |
| Eigener Schlüssel fürs Veröffentlichen | 09.07. | Liste 09.07. | Zum Veröffentlichen wird derselbe Zugangsschlüssel benutzt wie für anderes. Ein eigener, regelmäßig gewechselter wäre sauberer. | Entwicklung | nichts |
| CHANGELOG ist stehengeblieben | 12.07. | Liste 12.07. | Die Änderungsübersicht endet bei 0.4.0, ausgeliefert ist 0.9.0. Entweder nachziehen oder die Datei bewusst einstellen. | CEO-Entscheidung | nichts |
| Sprachwechsel nur in eine Richtung sofort | 12.07. | Liste 12.07. | Von Deutsch auf Englisch wirkt sofort, umgekehrt verlangt die App einen Neustart. **Status unklar** — kein Beleg, dass das behoben wurde. | Entwicklung | nichts |
| Lange Aufnahme über 5 Minuten | 09.07. | Liste 09.07. | Nachweis fehlt, dass eine lange Aufnahme mit Pause exakt der echten Zeit abzüglich Pause entspricht. **Status unklar** — nie als abgeschlossen protokolliert. | Entwicklung | nichts |
| Abbruchtest der Meterspur wiederholen | 09.07. | Liste 09.07. | Nach dem Korrekturlauf sollte ein erneuter Abbruchtest zeigen, dass die Stationsspur höchstens eine Sekunde vor dem Videoende endet. **Status unklar** — die Wiederholung ist nirgends als erledigt vermerkt. | Entwicklung | nichts |
| Foto hinter dem Spurende | 09.07. | Liste 09.07. | Nachweis fehlt, dass ein Foto hinter dem Ende der Spur die Station leer lässt statt eine falsche Zahl zu zeigen. **Status unklar**. | Entwicklung | nichts |
| Kennzeichnung geretteter Videos abnehmen | 09.07. | Liste 09.07. | Kennzeichen in der Liste und Hinweis im Bericht sind gebaut; die Abnahme am Gerät ist nicht protokolliert. **Status unklar**. | Entwicklung | nichts |
| Restpunkte Hilfe-System | 17.07. | `PROJECT_STATUS.md` | Vier Kleinigkeiten aus der Hilfe-Welle: eine Vorschauseite rendert unsauber, vier Dialogseiten sind am Gerät nicht abgenommen, ein Kartenbild ist in der englischen Strecke deutsch, und die Gegenprobe des Vergleichs-Tors lief nur als Durchsicht, nicht als echter Lauf. | Entwicklung | nichts |
| Nächste Feldtest-Runde | 01.07. | Liste 01.07. | Nach dem 13.07. ist keine Rückmeldung aus dem Feld dokumentiert. **Status unklar** — ob die Runde stattfand, lässt sich aus dem Repo nicht belegen. | CEO-Entscheidung | nichts |

---

## Seit Juli erledigt

| Punkt | Wodurch erledigt |
|---|---|
| Zusammenführung nach `master` + Version | Merge am 30.07., Tag `v0.9.0` auf `a1afaf7` — `RESULT_MERGE_0_9_0_2026-07-30.md`; nachgeprüft: `feature/dual-mode` ist in `master` enthalten |
| Station mit laufendem Meterzähler | Am Gerät grün am 13.07. (offeriert == eingebrannt, 3 Stellen exakt) — `PROJECT_STATUS.md:84` |
| Kamerakopf C10/C18 erkennen | Kopfwechsel C18→C10 am Gerät grün 13.07. — `PROJECT_STATUS.md:84`, `CameraTypePrefillTest.kt` |
| Kameraumbau auf Camera2 | Abgenommen 29.07., 30,0 statt 13,5 Bilder je Sekunde — `RESULT_CAMERA2_UMBAU_2026-07-29.md` |
| Plattform-Signaturschlüssel (Hotspot) | Schlüssel liegt vor und ist in 0.9.0 im Einsatz; Hotspot mit eigenem Namen gelöst — `RESULT_PLATTFORMSIGNATUR_2026-07-29.md`, ADR-0005. *(Kamerarechte damit ausdrücklich NICHT gelöst — siehe offener Punkt oben.)* |
| Einrichtungswerkzeug für die Fertigung | Vollständig, Einrichtungslauf am Gerät nachgeholt — `RESULT_WERKSEINRICHTUNG_2026-07-30.md`, Commit `92ae04a` |
| Selbstaktualisierung des Werkzeugs | Aus dem Lizenzportal inkl. Prüfsumme, Negativprobe unter echter Laufzeit — `RESULT_WERKZEUG_AUTOUPDATE_2026-07-30.md`, Commits `c10eeff`/`c717c38` |
| Speicher- und Kapazitätsanzeige | Am Gerät grün 12.07. (intern + USB, Auto-Auffrischung) — `PROJECT_STATUS.md:103` |
| Kameratyp-Nachtrag (M2) | Am Gerät grün 12.07. abends — `PROJECT_STATUS.md:99`, `RESULT_FIX_M2.md` |
| Schrift im PDF-Bericht (M4) | `pdffonts` gemessen, Inter eingebettet und untergesetzt — `PROJECT_STATUS.md:99` |
| Antwort an den Feldtester | Entwurf angelegt und am 12.07. versendet — `PROJECT_STATUS.md:86`/`:105` |
| Defektverdacht Test-ONE | Gerät kam nach dem Laden zurück, volle Testrunde gelaufen — `PROJECT_STATUS.md:98` |
| Handbuch-Schriftart | Produktiver Generator nutzt Inter (`tools/manual/generate.js:139-141`); der bemängelte Altgenerator liegt nur noch unter `docs/manual/_legacy/` |
| Licht-Regler im Fernbetrieb | Am Gerät bestätigt beim E2E-Lauf am 03.07. — `TESTREPORT_DUAL_MODE_E2E_2026-07-03.md` |
| Feldeingabe in HD (W3) | Am Gerät abgenommen 11.07. — `PROJECT_STATUS.md` |
| Status des Sprach-Skripts geklärt | CEO-Entscheid 05.08.: Weg abgelöst. Skript im Nachbar-Repo seit 22.05. unverändert und nie in Git aufgenommen, der beschriebene 404-Blocker seither ungelöst, `tools/l10n-import-to-portal.ps1` wird hier produktiv benutzt. Datei nach `docs/archiv/2026-07/publish-one-l10n.README.md` verschoben. *(Der Punkt „Portal antwortet mit 404" bleibt unverändert offen — unabhängig davon.)* |
| Datum im Vergleichsbild wandert | An der Wurzel behoben (05.08.): `java.time.Clock` als Koin-Single eingeführt (`AppModule.kt` — Vorgabe `Clock.systemDefaultZone()`, unverändert echtes Datum in der App), `ProjectFormViewModel` und `ProjectFormScreen.kt:484` nutzen `LocalDate.now(clock)` statt `LocalDate.now()`. `ScreenshotTestModule.kt` überschreibt den Single mit einem festen Kalendertag. Belege: `verify.ps1` PASS de+en; Negativprobe — injiziertes Datum testweise auf 2027-07-17 gesetzt (ein Jahr voraus, reale Systemuhr blieb bei 05.08.2026) blieb nach Golden-Neuaufnahme grün, danach auf 2026-07-17 zurückgesetzt und erneut PASS bestätigt; Testsuite 54 Testsuites/450 Tests/0 Failures (`--rerun-tasks`); neuer Test `ProjectFormViewModelDefaultClockTest` belegt, dass der Produktionspfad ohne injizierten Clock weiterhin das echte heutige Datum liefert. Windows-Systemdatum selbst ließ sich ohne Admin-Rechte nicht verstellen (`Set-Date` verweigert) — die Negativprobe lief stattdessen über das injizierte Datum, wie im Auftrag als Alternative vorgesehen. |

**Entfallen statt erledigt:** Der Rückfallschalter für den Aufnahmeweg wurde am 13.07. bewusst ersatzlos entfernt (`FeatureFlags.kt` gelöscht, unsichtbarer Auto-Rückfall bleibt über `FallbackRecorder.kt`) — `PROJECT_STATUS.md:83`. Der Punkt ist damit gegenstandslos, nicht abgearbeitet.

---

## Befund zu `publish-one-l10n.README.md`

Geprüft, ob das Skript oder ein Nachfolger noch angesprochen wird. Ergebnis, ohne Empfehlung:

- Das Skript `publish-one-l10n.ps1` liegt im Nachbar-Repo `C:\Projekte\drainq.one-localization` und ist dort bis heute **nicht in Git aufgenommen** (`git status` meldet es als unverfolgt).
- Der letzte Commit in diesem Nachbar-Repo stammt vom **22.05.2026** — also rund sieben Wochen **vor** dem Testballon vom 13.07., über den die Datei berichtet. Seither ist dort nichts mehr passiert.
- Der im README beschriebene Blocker (Aufräum-Zugang liefert 404) ist in `PROJECT_STATUS.md:59` mit Stand 29.07. weiterhin als offen geführt. Eine Auflösung ist nirgends dokumentiert.
- Der Portal-Anschluss der App (Nachladen von Sprachen) ist in diesem Repo **nicht** vorhanden: weder `l10n.portal.url` noch ein Nachlade-Pfad im `LocalizationManager`.
- Ein **anderes** Skript mit ähnlichem Zweck lebt dagegen in diesem Repo und wird benutzt: `tools/l10n-import-to-portal.ps1` (spielt die Wörterliste ins Portal, in `PROJECT_STATUS.md:58` im Zusammenhang mit den Hilfe-Texten erwähnt).

**Kurz:** Der Weg aus dem README wurde nach dem 13.07. nachweislich nicht weiterverfolgt, ist aber auch nicht förmlich eingestellt worden. Die Entscheidung, ob er wiederaufgenommen oder beendet wird, liegt beim CEO — bis dahin bleibt die Datei unverändert im Repo-Root.
