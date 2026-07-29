# Disziplin: Analyse

## 1. Projektinformationen

| Feld              | Wert                                                         |
| ----------------- | ------------------------------------------------------------ |
| Name              | DrainQ.ONE                                                   |
| System            | one                                                          |
| Version           | 0.5.x-beta (Analysestand: 0.5.14/514, 2026-07-13)           |
| Autor             | Thomas Viell (CEO) — Erstentwurf AI, rückwärts rekonstruiert |
| Datum             | 2026-07-14 (v2 nach Sonnet-Audit)                            |
| Dokumentvorlage   | [[01-analysis_template]]                                     |
| Namenskonvention  | [[naming-convention_one]]                                    |
| Projektkatalog    | `C:\Projekte\drainq.one\`                                    |
| Analysekatalog    | `docs\engineering\`                                          |
| Freigegeben am    | — (offen: CEO-Verifikation Prioritäten + DIN-Entscheidung)  |

**Verknüpfte Dokumente:**

| Feld            | Wert                                              |
| --------------- | ------------------------------------------------- |
| Initialisierung | (nachträglich; 00-inception nicht geführt)        |
| Entwurf         | [[02-project_one]] (folgt)                         |

---

> [!info] Zusammenarbeit mit AI und Methodik
> **AI-first-Anwendung:** Gebaut vom Opus-Berater, adversarial auditiert von Sonnet
> (unabhängiges Modell). Diese v2 schließt die Audit-Befunde vom 14.07. (Traceability
> vollständig, KRITIS-Fehlannahme korrigiert, Sicherheits-REN gegen Code realistisch gefasst).
> **Rückwärts-Rekonstruktion:** beschreibt den **Ist-Stand** der 0.5.x-App.
> **Verbleibende CEO-Entscheidungen (Geschäftsebene, nicht aus Code ableitbar):**
> (a) Prioritätenspalte A–D, (b) DIN-Frage (siehe §9). Beide mit `[AI-draft]` markiert.

## 2. Prozess

Ein Inspekteur prüft Abwasserkanäle, Grundstücksleitungen und Rohre optisch mit einem
**Schiebekamerasystem** („Pushrod"): Eine Kamera am Ende eines aufgerollten Kabels wird von
einem Schacht oder Zugangspunkt aus in die Haltung geschoben. Der Inspekteur beobachtet das
Live-Videobild, schiebt bis zum nächsten Schaden oder Zulauf und dokumentiert jede Feststellung
mit ihrer **Station** (Meterwert ab Startpunkt, vom Kabel-Meterzähler geliefert), einer
Kurzbeschreibung/Kategorie und Fotos. Zur Lokalisierung eines Kamerapunkts von der Oberfläche
dient eine **Ortungssonde** (Frequenzsender im Kamerakopf). Nach der Befahrung entsteht ein
**Haltungsbericht** (PDF) mit Kopf-/Stammdaten, Schadensliste, Fotos und Video, der an den
Auftraggeber übergeben wird.

Vor Einführung von DrainQ.ONE lief dieser Prozess auf der Gerätehardware über die
**Hersteller-App des Hardwarelieferanten** (`com.bominwell.minipush`). Diese deckte Bedienung,
Berichtswesen, Kiosk-Betrieb, Software-Update und Datenexport nur unzureichend ab und trug nicht
das DrainQ-Erscheinungsbild. DrainQ.ONE ersetzt diese App **auf derselben Hardware**.

Der Prozess ist **feld- und offline-geprägt**: Er läuft am Schachtdeckel, oft ohne
Internetverbindung, unter Zeitdruck, bei Nässe und mit Handschuhen. Die Hardware ist ein
verbautes Android-Gerät (Rockchip RK3588), das als abgeschlossenes Inspektionsgerät („Kiosk")
betrieben wird, nicht als offenes Tablet.

---

## 3. Akteure und Rollen

| ID     | Akteur | Typ | Rolle und Handlungen im Prozess |
| ------ | ------ | --- | --------------------------------- |
| ACT-01 | Inspekteur | Person | Primärnutzer. Legt Projekt an, verbindet Gerät, führt Befahrung durch, erfasst Schäden/Fotos/Notizen, steuert Licht/Sonde, erzeugt und exportiert den Bericht. |
| ACT-02 | ONE-Schiebekamerasystem | System | Die Gerätehardware (RK3588, Android). Liefert Videobild (V4L2 `/dev/video0`, RTSP) und Telemetrie über serielle Leitung (`/dev/ttyS5`). Trägt die App selbst. |
| ACT-03 | Kamerakopf (C10 / C18) | Objekt | Auswechselbarer Frontkamerakopf. Bestimmt Bildeigenschaften; Typ ist nicht immer über die Leitung erkennbar. |
| ACT-04 | Meterzähler (Kabelhaspel) | Objekt | Misst die eingeschobene Kabellänge; liefert den Stationswert (Meter). |
| ACT-05 | Ortungssonde | Objekt | Sender im Kamerakopf; per Frequenz von der Oberfläche ortbar. Vom Inspekteur ein-/umschaltbar. |
| ACT-06 | USB-Speicher | Objekt | Zieldatenträger für den Projekt-/Datei-Export im Feld. |
| ACT-07 | DrainQ-Update-Portal | System | `license.drainq.com` — liefert Software-Updates (Kanal beta) und Sprachpakete. |
| ACT-08 | DrainQ-Cloud | System | Konto/Login (derzeit Stub); künftige Synchronisation. |
| ACT-09 | Kartendienste | System (extern) | OpenStreetMap / Nominatim (Geocoding, statische Karten), Wetterdienst; Offline-Kartenkacheln. |
| ACT-10 | Standort/GPS | System / Ereignis | Liefert die Geoposition des Inspektionsorts. |
| ACT-11 | Zweitgerät / Fernanzeige | System | Über `OneRemoteServer`/`OneRemoteProtocol` gekoppeltes zweites Gerät (Dual-Mode-Betrieb). |
| ACT-12 | Auftraggeber | Person (extern) | Empfänger des Haltungsberichts. Steht außerhalb des Geräteprozesses (auch Stakeholder). |

---

## 4. Problem und Ziel

**Problem:** Die auf der ONE-Hardware vorinstallierte Hersteller-App erfüllt den
Inspektionsprozess nur teilweise: umständliche Feldbedienung, kein DrainQ-konformer
Haltungsbericht, kein kontrollierter Kiosk-Betrieb, kein gesicherter Software-Update-Weg, kein
strukturierter Datenexport und kein DrainQ-Branding. Damit ist die Hardware nicht als
DrainQ-Produkt vermarktbar und im Feld nicht optimal bedienbar.

**Ziel:** Eine durchgängige, feldtaugliche Inspektions-App **auf der ONE-Hardware**, die
[Schätzung] die Zeit von Befahrung bis fertigem Haltungsbericht senkt und ohne PC auskommt.
Messbare Zieleigenschaften: kiosk-fester Direktstart in die App, ruckelfreie Videoaufnahme in
HD mit eingebranntem OSD (Station/Datum), Station im Video **pixelgleich** zum erfassten
Meterwert, absturzsichere Aufnahme (Geräte-Kill → abspielbare Datei bleibt), Schadensdoku und
PDF-Bericht direkt am Gerät, Export per USB und Update über das DrainQ-Portal.

---

## 5. Annahmen und Einschränkungen

### 5.1 Annahmen

| ID     | Annahme | Konsequenz, falls unzutreffend |
| ------ | ------- | ------------------------------ |
| ASM-01 | Zielgerät ist als **Device-Owner** provisioniert (Golden Image), sonst greift der privilegierte WLAN-/Kiosk-Pfad nicht. | Kiosk und In-App-WLAN nur eingeschränkt; Betrieb als offenes Tablet. |
| ASM-02 | Der RK3588-**Hardware-H.264-Encoder** (`c2.rk.avc.encoder`) ist verfügbar und trägt ~25 fps HD. | Rückfall auf langsameren Software-Weg; Zeitraffer-/fps-Ziel gefährdet. |
| ASM-03 | Der Meterwert kommt zuverlässig über die Geräteleitung (seriell/WiFi). | Station im Bericht unzuverlässig; Kernnutzen entfällt. |
| ASM-04 | [AI-draft] Der Kamerakopftyp (C10/C18) ist am Feld nicht immer automatisch erkennbar; manuelle/angehängte Erfassung nötig. | Falsche Kopfangabe im Bericht. |
| ASM-05 | Betrieb überwiegend **offline**; Internet (Portal/Cloud/Karten) ist optional, nicht Voraussetzung der Kernfunktion. | Bei Online-Zwang wäre die App felduntauglich. |

### 5.2 Einschränkungen

| ID     | Einschränkung | Quelle |
| ------ | ------------- | ------ |
| CON-01 | Zielplattform ist fix: RK3588 / Android 8+ (verbautes Gerät). | Hardware |
| CON-02 | Betrieb im **Kiosk** (LockTask, Device-Owner, HOME-Activity). | Produktentscheidung |
| CON-03 | **Datenschutz (DSGVO)** für lokale Personen-/Auftraggeberdaten. **KRITIS/NIS2/ISO 27001 sind für die ONE NICHT einschlägig** — die ONE ist mobiles Feld-Erfassungsgerät, kein Teil der kritischen Infrastruktur-Steuerung; der Compliance-Rahmen der DrainQ-Suite betrifft Backend/Portal, nicht das Feldgerät. | CEO 14.07. |
| CON-04 | **Kein DIN-EN-13508-2-/XML-Export** in der Beta — Schadenserfassung bewusst auf Presets + Position + Freitext reduziert (CEO-Entscheid W1-E). | CEO |
| CON-05 | Sideload-Gerät: `MANAGE_EXTERNAL_STORAGE` für USB-Export; kein Play-Store-Vertrieb. | Plattform |
| CON-06 | Video-/Berichtssprache und OSD ohne DIN-Kodierung; Feststellungen frei kategorisiert. | folgt aus CON-04 |

---

## 6. Anforderungen

**Prioritätenskala:** A notwendig · B wichtig · C nützlich · D optional.
**[AI-draft] Die gesamte Prioritätenspalte ist ein Vorschlag und vom CEO zu verifizieren.**

### 6.1 Funktionale Anforderungen

| ID     | Anforderung | Beschreibung | Akteur | Priorität |
| ------ | ----------- | ------------ | ------ | --------- |
| REF-01 | Projekt anlegen/bearbeiten | Projekt mit Auftraggeber, Standort, Projektnummer, Kameratyp, Inspektionsdatum erfassen und ändern. | ACT-01 | A |
| REF-02 | Projektliste | Projekte listen (Anzeige „Auftraggeber — Standort — Projektnr."), paginiert, öffnen. | ACT-01 | A |
| REF-03 | Gerät verbinden | Automatische Verbindung zur ONE (Discovery, bekannte Geräte, Auto-Connect, Pairing). | ACT-01, ACT-02 | A |
| REF-04 | Live-Videostream | Kamerabild live anzeigen (RTSP/V4L2), geringe Latenz. | ACT-02, ACT-03 | A |
| REF-05 | OSD-Einblendung | Station (Meter) und Datum im Bild einblenden — live und ins Video/Foto eingebrannt. | ACT-01 | A |
| REF-06 | Videoaufnahme | Aufzeichnung in HD über HW-Encoder, mit Pause/Fortsetzen, absturzsicher, unsichtbarer Auto-Rückfall. | ACT-01, ACT-02 | A |
| REF-07 | Foto-Aufnahme | Standbild mit eingebranntem OSD erfassen und dem Projekt zuordnen. | ACT-01 | A |
| REF-08 | Schaden erfassen | Feststellung mit Kategorie/Preset, Uhrzeitposition, Station und Freitext dokumentieren (ohne DIN/XML). | ACT-01 | A |
| REF-09 | Meterzähler anzeigen/nullen | Aktuelle Station anzeigen, am Startpunkt nullen, Wert stabilisiert (Filter). | ACT-04 | A |
| REF-10 | PDF-Haltungsbericht | Bericht mit Stammdaten, Schadensliste, Fotos, Leitungsverlauf-Grafik, Logo erzeugen und vorschauen. | ACT-01, ACT-12 | A |
| REF-11 | Kiosk-/Autostart-Betrieb | Direktstart in die App, LockTask, kein Verlassen ohne Berechtigung. | ACT-02 | A |
| REF-12 | Software-Update aus Portal | Update-Anzeige, Download mit Fortschritt, Installation über signiertes Paket. | ACT-07 | A |
| REF-13 | Notiz erfassen | Freitext-Notiz zum Projekt anlegen. | ACT-01 | B |
| REF-14 | Sonde steuern | Ortungssonde per Hardbutton ein-/umschalten (Frequenzzyklus), Auto-Ausblendung. | ACT-05 | B |
| REF-15 | Licht steuern | Kamerakopf-Licht per Hardbutton stufig heller/dunkler. | ACT-02 | B |
| REF-16 | Meter-Spur zum Video | Station zeitgenau zum Video aufzeichnen und bei Wiedergabe anzeigen (Distanzspur). | ACT-01 | B |
| REF-17 | Videowiedergabe | Aufgenommenes Video mit Meter-Overlay abspielen. | ACT-01 | B |
| REF-18 | USB-Export | Komplettprojekt oder Einzeldateien auf USB-Stick exportieren (Struktur `/DrainQ/<Projektnr>/`), Fortschritt, Stick-Erkennung. | ACT-01, ACT-06 | B |
| REF-19 | Projekt-Export (ZIP) | Projekt als ZIP-Paket bündeln. | ACT-01 | B |
| REF-20 | Speicheranzeige | Füllstand intern + USB als Balken, farbcodiert, mit Auto-Refresh bei USB-Wechsel. | ACT-01, ACT-06 | B |
| REF-21 | Bild-Annotation | Foto markieren/beschriften. | ACT-01 | C |
| REF-22 | Netzwerk & Verbindung | Online-Status, In-App-WLAN, Hotspot/SoftAP, Tethering-Sprung. | ACT-01 | C |
| REF-23 | Offline-Karten | Kartenkacheln herunterladen, anzeigen, Ort per Karte wählen (Map-Picker). | ACT-09 | C |
| REF-24 | Standort/Geocoding | Inspektionsort per GPS/Adresssuche setzen, Wetter-Preset. | ACT-10, ACT-09 | C |
| REF-25 | Helligkeit | Display-Helligkeit in der App regeln. | ACT-01 | C |
| REF-26 | Mehrsprachigkeit | UI in mehreren Sprachen (fest eingebaut), Nachladen über Portal. | ACT-01, ACT-07 | C |
| REF-27 | Cloud-Login | Anmeldung am DrainQ-Konto (derzeit Stub). | ACT-08 | C |
| REF-28 | Fernanzeige / Dual-Mode | Zweites Gerät koppeln, Bild/Steuerung teilen (OneRemote). | ACT-11 | C |
| REF-29 | Theme (Dark/Light) | DrainQ-Design, Hell/Dunkel, Amber-Akzent. | ACT-01 | D |
| REF-30 | Kameratyp-Vorbelegung | Kopftyp (C10/C18) automatisch nachtragen/anhängen, sobald erkannt. | ACT-03 | D |

### 6.2 Nichtfunktionale Anforderungen

| ID     | Anforderung | Beschreibung | Akteur | Priorität |
| ------ | ----------- | ------------ | ------ | --------- |
| REN-01 | Videoleistung | HD-Aufnahme im Mittel ≥ 24 fps auf RK3588; Live-Latenz gering. | — | A |
| REN-02 | Zeittreue | Videodauer == pausenbereinigte Echtzeit (VFR-PTS); kein Zeitraffer. | — | A |
| REN-03 | Datentreue Station | Im Video eingebrannter Meterwert == an gleicher Stelle erfasste Station (kein Rundungs-/Interpolationsdrift). | ACT-04 | A |
| REN-04 | Absturzsicherheit Aufnahme | Prozess-Kill während der Aufnahme → nach Neustart abspielbare Datei; kein Totalverlust. | — | A |
| REN-05 | Kiosk-Robustheit | App bleibt im LockTask; kein unbeabsichtigtes Verlassen; Autostart nach Boot. | ACT-02 | A |
| REN-06 | Feldbedienbarkeit | Touch-Ziele ≥ 48 dp, Handschuh-tauglich, Landscape, Hardbuttons für Kernaktionen. | ACT-01 | A |
| REN-07 | Offline-Fähigkeit | Kernfunktionen (Video, Erfassung, Bericht, Export) ohne Internet nutzbar. | — | A |
| REN-08 | Zielplattform | Läuft auf RK3588 / Android 8+ (SDK 26+, Target 34). | ACT-02 | A |
| REN-09 | Schutz gespeicherter Zugangsdaten | Sensible lokale Zugangsdaten (WLAN-Credentials) verschlüsselt ablegen — belegt: `AndroidEncryptedStorage` (EncryptedSharedPreferences/Keystore), genutzt von `KnownOneStore`. Konto-Token erst relevant, wenn Cloud-Login über den Stub hinausgeht. | — | B |
| REN-10 | Update-Integrität | APK gegen SHA-256 aus dem Manifest geprüft; Bezugsquelle fix `https://license.drainq.com`. Offen (Sicherheits-Hygiene, kein Compliance-Zwang): explizite Signaturprüfung + Domain-Allowlist für die `proxyUrl`-Überschreibung. | ACT-07 | B |
| REN-11 | Datenschutz (DSGVO) | Personen-/Auftraggeber-/Standortdaten bleiben lokal; keine Cloud-Übertragung ohne Einwilligung; Zweckbindung. | ACT-10 | B |
| REN-12 | Sicherheits-Hygiene (optional) | Kein Compliance-Zwang (KRITIS nicht einschlägig, CON-03). Optional/niedrig: generelles Ereignis-Logging nur, falls später als Kundenanforderung gefordert. | — | D |
| REN-13 | Transport-Hygiene | Service-Verkehr läuft über `https` (Code belegt: Weather/Nominatim/OSM/Update). Altlast: `usesCleartextTraffic="true"` im Manifest ohne `network_security_config` — entfernbar, niedriges Restrisiko. | ACT-07, ACT-08 | C |
| REN-14 | Stabilität Meterwert | Ausreißer-/Plausibilitätsfilter (Median), begrenzte Schrittweite. | ACT-04 | B |
| REN-15 | Datenerhalt bei Update | DB-Migrationen verlustfrei (bestehende Projekte/Schäden/Notizen bleiben erhalten). | — | A |

---

## 7. Anwendungsfälle

| ID    | Name | Akteur | Verknüpfte Anforderungen |
| ----- | ---- | ------ | ------------------------ |
| UC-01 | Inspektion durchführen (Befahrung) | ACT-01, ACT-02, ACT-04 | REF-03,04,05,06,09 · REN-01,02,03,04 |
| UC-02 | Schaden dokumentieren | ACT-01 | REF-08,07,05,13,21 |
| UC-03 | Haltungsbericht erzeugen | ACT-01, ACT-12 | REF-10,08,07,16 |
| UC-04 | Projekt anlegen und verwalten | ACT-01 | REF-01,02,24,30 |
| UC-05 | Gerät verbinden | ACT-01, ACT-02 | REF-03 |
| UC-06 | Video aufnehmen mit Pause | ACT-01, ACT-02 | REF-06 · REN-02,04 |
| UC-07 | Video wiedergeben mit Distanzspur | ACT-01 | REF-16,17 |
| UC-08 | Projekt per USB exportieren | ACT-01, ACT-06 | REF-18,19,20 |
| UC-09 | Software aktualisieren | ACT-01, ACT-07 | REF-12 · REN-10,15 |
| UC-10 | Sonde orten | ACT-01, ACT-05 | REF-14 |
| UC-11 | Licht anpassen | ACT-01 | REF-15 |
| UC-12 | Netzwerk/WLAN einrichten | ACT-01 | REF-22 · REN-13 |
| UC-13 | Offline-Karte vorbereiten | ACT-01, ACT-09 | REF-23,24 |
| UC-14 | Gerät als Kiosk starten | ACT-02 | REF-11 · REN-05 |
| UC-15 | Am DrainQ-Konto anmelden | ACT-01, ACT-08 | REF-27 · REN-09 |
| UC-16 | Fernanzeige koppeln (Dual-Mode) | ACT-01, ACT-11 | REF-28 |
| UC-17 | App konfigurieren | ACT-01 | REF-25,26,29 |

### UC-01 — Inspektion durchführen (Befahrung)

**Akteur:** ACT-01 Inspekteur (mit ACT-02 Gerät, ACT-04 Meterzähler)
**Verknüpfte Anforderungen:** REF-03,04,05,06,09; REN-01,02,03,04
**Vorbedingungen:** Projekt ist angelegt; Gerät verbunden; Kamera im Zugangspunkt.

**Hauptszenario:**
1. Inspekteur öffnet das Projekt und startet die Inspektionsansicht.
2. Live-Bild erscheint; Meterzähler wird am Startpunkt genullt.
3. Inspekteur startet die Aufnahme; OSD (Station/Datum) wird eingeblendet und eingebrannt.
4. Inspekteur schiebt die Kamera; die Station läuft mit.
5. An einem Schaden pausiert er, dokumentiert (UC-02), setzt fort.
6. Am Ziel stoppt er die Aufnahme; die Datei wird abspielbar gesichert.

**Alternativszenarien:**
- Verbindung bricht ab → App zeigt Status, versucht Auto-Reconnect (REF-03).
- HW-Encoder nicht verfügbar → unsichtbarer Rückfall auf Software-Aufnahme (REF-06).

**Nachbedingungen:** Video mit korrektem OSD und pausenbereinigter Dauer liegt im Projekt.
**Ausnahmen:** Geräte-Kill während Aufnahme → nach Neustart wird die Datei automatisch finalisiert (REN-04).

### UC-02 — Schaden dokumentieren

**Akteur:** ACT-01
**Verknüpfte Anforderungen:** REF-08, REF-07, REF-05, REF-13, REF-21
**Vorbedingungen:** Laufende oder geöffnete Inspektion; Station verfügbar.

**Hauptszenario:**
1. Inspekteur öffnet den Schadensdialog.
2. Er wählt eine Kategorie/Preset, setzt die Uhrzeitposition, übernimmt die Station.
3. Er ergänzt Freitext und nimmt ein Foto mit eingebranntem OSD auf; optional markiert er das Foto (REF-21) oder ergänzt eine Notiz (REF-13).
4. Er speichert; der Schaden erscheint in der Schadensliste des Projekts.

**Alternativszenarien:** Kein Preset passend → nur Freitext.
**Nachbedingungen:** Schaden mit Station, Position, Text und Foto ist persistiert.
**Ausnahmen:** Keine DIN-Kodierung/-Validierung (CON-04).

### UC-03 — Haltungsbericht erzeugen

**Akteur:** ACT-01 (Ergebnis für ACT-12)
**Verknüpfte Anforderungen:** REF-10, REF-08, REF-07, REF-16
**Vorbedingungen:** Projekt mit Stammdaten und mindestens einer Feststellung.

**Hauptszenario:**
1. Inspekteur öffnet die Projektdetails und wählt „Bericht/PDF".
2. Die App erzeugt den PDF-Haltungsbericht (Stammdaten, Schadensliste, Fotos, Leitungsverlauf-Grafik, Logo).
3. Vorschau; anschließend Export (USB) oder Weitergabe.

**Nachbedingungen:** PDF liegt vor und ist exportierbar.
**Ausnahmen:** Fehlende Schrift/Font → Bericht muss dennoch mit eingebettetem, subset-tem Font rendern (belegt: Inter emb+subset).

<!-- UC-04..UC-17: Kurzszenarien im Index oben; jeder hat mindestens ein AC in §8.
     Detaillierung bei Bedarf im Zuge des 02-project. -->

---

## 8. Akzeptanzkriterien

| ID    | Kriterium | Verknüpfte Anforderungen / UC |
| ----- | --------- | ----------------------------- |
| AC-01 | Nach App-Start (Boot) erscheint ohne Dialog direkt die App; Verlassen ist gesperrt (Inspektion). | REF-11, REN-05, UC-14 |
| AC-02 | 60-s-Aufnahme ergibt ein Video von 60 s ± 1 s; Wiedergabe ohne Zeitraffer. | REF-06, REN-02, UC-06 |
| AC-03 | Der aus dem Video an drei Stellen abgelesene Meterwert stimmt mit der dort erfassten Station exakt überein. | REF-05, REN-03, UC-01 |
| AC-04 | App während der Aufnahme killen → nach Neustart liegt eine abspielbare Datei vor. | REF-06, REN-04, UC-06 |
| AC-05 | Ein Schaden mit Kategorie, Position, Station, Freitext und Foto wird gespeichert und im Bericht gelistet. | REF-08, REF-07, UC-02 |
| AC-06 | Der PDF-Bericht enthält Stammdaten, Schadensliste mit Fotos und Leitungsverlauf; Schrift eingebettet. | REF-10, UC-03 |
| AC-07 | Meterzähler lässt sich nullen; Ausreißer werden gefiltert (kein Sprung > Grenzwert). | REF-09, REN-14, UC-01 |
| AC-08 | Komplettprojekt exportiert nach `/DrainQ/<Projektnr>/` auf den erkannten USB-Stick, mit Fortschritt. | REF-18, UC-08 |
| AC-09 | Update wird angezeigt, geladen (Fortschritt) und installiert; bestehende Projekte bleiben erhalten. | REF-12, REN-10, REN-15, UC-09 |
| AC-10 | Verbindung zur ONE wird automatisch hergestellt; Abbruch → Reconnect-Versuch. | REF-03, UC-05 |
| AC-11 | Live-HD im Mittel ≥ 24 fps auf dem Zielgerät (gemessen, nicht behauptet). | REN-01, UC-01 |
| AC-12 | Sonde per Hardbutton durch den Frequenzzyklus schaltbar, Auto-Ausblendung nach ~3 s. | REF-14, UC-10 |
| AC-13 | Licht per Hardbutton stufig regelbar. | REF-15, UC-11 |
| AC-14 | WLAN-Zugangsdaten liegen verschlüsselt vor (EncryptedSharedPreferences/Keystore), kein Klartext-Log. | REN-09, UC-15 |
| AC-15 | Service-Verkehr (Update/Karten/Wetter) läuft über https; Cleartext-Altlast im Manifest ist dokumentiert. | REN-13, UC-12 |
| AC-16 | Ohne Internet sind Befahrung, Erfassung, Bericht und USB-Export voll nutzbar. | REN-07 |
| AC-17 | Kartenkacheln lassen sich offline vorhalten und anzeigen; Ort per Karte wählbar. | REF-23, UC-13 |
| AC-18 | Speicheranzeige zeigt intern + USB korrekt und aktualisiert bei Stick-Wechsel. | REF-20 |
| AC-19 | Projekt mit Auftraggeber/Standort/Projektnr./Kameratyp/Datum anlegen und in der Liste („Auftraggeber — Standort — Projektnr.") wiederfinden und öffnen. | REF-01, REF-02, UC-04 |
| AC-20 | Nach hergestellter Verbindung erscheint das Live-Bild ohne manuellen Startschritt. | REF-04, UC-01 |
| AC-21 | Eine Notiz zum Projekt lässt sich anlegen und im Projektdetail wiederfinden. | REF-13, UC-02 |
| AC-22 | Die Meter-Spur wird zeitgenau zum Video geschrieben; bei Wiedergabe passt die angezeigte Distanz zur Videoposition (Toleranz ±2 Frame-Dauern). | REF-16, REF-17, UC-07 |
| AC-23 | Projekt lässt sich als ZIP-Paket bündeln. | REF-19, UC-08 |
| AC-24 | Foto lässt sich markieren/beschriften; die Annotation bleibt am Foto erhalten. | REF-21, UC-02 |
| AC-25 | Netzwerk-Screen zeigt Online-Status; In-App-WLAN wählbar; Hotspot/Tethering erreichbar. | REF-22, UC-12 |
| AC-26 | Inspektionsort per GPS, Adresssuche oder Karte setzbar; Wetter-Preset wird gefüllt. | REF-24, UC-04, UC-13 |
| AC-27 | Display-Helligkeit in der App regelbar. | REF-25, UC-17 |
| AC-28 | UI-Sprache umschaltbar; über das Portal nachgeladene Sprachen ergänzen fehlende Texte. | REF-26, UC-17 |
| AC-29 | Am DrainQ-Konto anmeldbar, sobald über den Stub hinaus umgesetzt; bis dahin als Stub gekennzeichnet. | REF-27, UC-15 |
| AC-30 | Zweitgerät koppelbar; Bild/Steuerung werden geteilt. | REF-28, UC-16 |
| AC-31 | Theme Hell/Dunkel umschaltbar, Amber-Akzent konsistent. | REF-29, UC-17 |
| AC-32 | Kopftyp C10/C18 wird automatisch nachgetragen/angehängt, sobald erkannt; manueller Override bleibt geschützt. | REF-30, UC-04 |
| AC-33 | Touch-Ziele ≥ 48 dp, Landscape-Betrieb, Hardbuttons für Licht/Sonde/Aufnahme belegt. | REN-06 |
| AC-34 | App startet und läuft auf RK3588 / Android 8+ (SDK 26+, Target 34). | REN-08 |
| AC-35 | Personen-/Standortdaten verlassen das Gerät nur nach Einwilligung; kein automatischer Cloud-Upload. | REN-11 |
| AC-36 | Optionales Ereignis-Logging bleibt deaktiviert, solange keine Kundenanforderung besteht (bewusste Nicht-Umsetzung dokumentiert). | REN-12 |

---

## 9. Zusammenfassung

**Systembeschreibung:** DrainQ.ONE ist eine **Android-Inspektions-App**, die direkt auf der
ONE-Schiebekamerahardware (RK3588) im Kiosk läuft. Sie zeigt das Live-Kamerabild, zeichnet HD-Video
mit eingebranntem Stations-OSD auf, dokumentiert Schäden (Preset + Position + Freitext, ohne DIN),
erzeugt PDF-Haltungsberichte und exportiert per USB. Einsatzumgebung: **Embedded/Mobile, offline-first**.
Zielakteur: der Inspekteur im Feld.

**Schlüsselakteure:** ACT-01 Inspekteur (Bediener); ACT-02 ONE-Hardware (Video/Telemetrie);
ACT-04 Meterzähler (Station); ACT-07 Update-Portal.

**Im Umfang:** Projekt-/Schadensverwaltung, Verbindung, Live-Video, OSD, HW-Aufnahme mit Pause und
Crash-Sicherheit, Foto, Meter/Sonde/Licht, PDF-Bericht, USB-/ZIP-Export, Kiosk/Autostart, Portal-Update,
Netzwerk/WLAN, Offline-Karten, Mehrsprachigkeit, Cloud-Login (Stub), Dual-Mode-Fernanzeige.

**Außerhalb des Umfangs:** DIN-EN-13508-2-Kodierung und XML-Export (CON-04); Play-Store-Vertrieb (CON-05);
vollwertige Cloud-Synchronisation (nur Stub); Kamerakopf-Firmware-Update [Schätzung]; KRITIS/NIS2-Härtung (CON-03, nicht einschlägig).

**Harte Einschränkungen (CON):** CON-01 RK3588/Android 8+ · CON-02 Kiosk · CON-03 DSGVO (KRITIS nicht einschlägig) · CON-04 kein DIN/XML · CON-05 Sideload/USB.

**Architekturrelevante Annahmen (ASM):** ASM-01 Device-Owner · ASM-02 HW-Encoder · ASM-03 Meter-Zulieferung · ASM-05 Offline-first.

**Funktionale Anforderungen MVP (Priorität A) — Reihenfolge:** REF-01/02 (Projekt) → REF-03 (Verbindung) →
REF-04/05 (Video/OSD) → REF-09 (Meter) → REF-06/07 (Aufnahme/Foto) → REF-08 (Schaden) → REF-10 (Bericht) →
REF-11 (Kiosk) → REF-12 (Update). **[AI-draft] — Prioritäten CEO-zu-verifizieren.**

**Nichtfunktionale Anforderungen MVP (Priorität A):** REN-01,02,03,04 (Video/Zeit/Station/Crash),
REN-05 (Kiosk), REN-06 (Feldbedienung), REN-07 (Offline), REN-08 (Plattform), REN-15 (Datenerhalt).

**Anwendungsfälle MVP (Priorität A):** UC-01, UC-02, UC-03, UC-06, UC-09, UC-14.

**Offene Punkte (vor Freigabe zu schließen):**
1. **[AI-draft] Prioritäten A–D** durch CEO bestätigen/korrigieren.
2. **DIN-Frage entscheiden + CLAUDE.md korrigieren:** CLAUDE.md nennt „DIN EN 13508-2 konform"; PROJECT_STATUS belegt DIN/XML als entfernt (CON-04). Welche Aussage ist die gewollte Produktwahrheit?
3. **[AI-draft] Dual-Mode (REF-28)** und **Cloud-Login (REF-27)** — Reifegrad bestätigen (Produktfeature vs. Experiment/Stub).

*Erledigt in v2 (Sonnet-Audit 14.07.):* KRITIS-Fehlannahme korrigiert (CON-03/REN-12/13); Traceability vollständig (jeder REF/REN/UC hat AC); Sicherheits-REN gegen Code realistisch gefasst.

---

## Domänenglossar

**Schiebekamera (Pushrod)** — Inspektionskamera am Ende eines schiebbaren Kabels, ohne eigenen Fahrantrieb.
**Haltung** — Rohrabschnitt zwischen zwei Schächten; Bezugsobjekt der Befahrung.
**Station** — Längsposition in der Haltung (Meter ab Startpunkt), geliefert vom Meterzähler.
**OSD (On-Screen-Display)** — ins Bild eingeblendete/eingebrannte Textzeile (Station, Datum).
**Feststellung / Schaden** — dokumentierter Befund an einer Station (Kategorie, Position, Foto, Text).
**Kamerakopf (C10/C18)** — auswechselbare Frontkamera unterschiedlichen Typs.
**Ortungssonde** — Frequenzsender im Kopf zur Lokalisierung von der Oberfläche.
**Haltungsbericht** — PDF-Ergebnisdokument der Inspektion für den Auftraggeber.
**Kiosk / LockTask** — abgeschlossener Ein-App-Betrieb des Geräts (Device-Owner).
**Dual-Mode** — Kopplung eines zweiten Geräts als Fernanzeige/-bedienung.

---

## Glossar der Analysebegriffe

**Prozess** — realer Vorgang der Kanalinspektion, unabhängig vom geplanten System.
**System** — DrainQ.ONE als Lösung; antwortet auf das Problem und erfüllt die Anforderungen.
**Akteur** — Person, Objekt, System oder Ereignis mit aktiver Beziehung zum Prozess.
**Anforderung** — messbare Eigenschaft/Verhalten des Systems (Priorität A–D).
**Anwendungsfall** — Beziehung zwischen System und Akteur; Grundlage für Anforderung, Entwurf, Test.
**Annahme (ASM)** — für die Analyse als wahr angenommener, nicht verifizierter Fakt.
**Einschränkung (CON)** — harte Randbedingung, die das System einhalten muss.
**Akzeptanzkriterium (AC)** — messbare Bedingung für die korrekte Umsetzung.

---

*Nach Freigabe des Dokuments: `01-audit_one.md` anlegen.*
