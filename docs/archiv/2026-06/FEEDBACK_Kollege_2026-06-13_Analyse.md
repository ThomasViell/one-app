# Kollegen-Feedback DrainQ.ONE — Analyse & Maßnahmenkatalog

**Stand:** 2026-06-13 · **Tool:** drainq.one (ONE-Schiebekamera, läuft direkt auf RK3588/Android) · **Code-Stand:** Branch `feature/beta-wave-1`
**Auftrag:** Bescheid + Maßnahmenvorschlag je Finding. **Keine Code-Änderungen vorgenommen** — reine Ausarbeitung.

> **Versionshinweis vorab:** Die Analyse bezieht sich auf den aktuellen Branch-Stand. Bitte einmal bestätigen, welche App-Version der Kollege getestet hat — einige Punkte (v. a. der graue Balken) sind bereits in Arbeit und können in einem neueren Build anders aussehen.

---

## Übersicht (sortiert nach empfohlener Priorität)

| Nr | Finding (Kollege) | Bescheid | Maßnahme (kurz) | Aufwand | Prio |
|---|---|---|---|---|---|
| 1 | Grauer Balken verdeckt die Bedienikonen | **Berechtigt — bekannt** | System-Taskbar endgültig unterdrücken (Original-Technik) | S–M | **Hoch** |
| 6 | Meterzähler wackelt sporadisch | **Berechtigt** | Plausibilitätsfilter + Glättung + RX-Frame-Prüfung | M | **Hoch** |
| 2 | Bedienikonen verschwinden automatisch nach ~3 s | **Berechtigt** | Auto-Ausblenden als abschaltbare Option, Default AUS | S | **Hoch (Quick Win)** |
| 5 | Buttons/Reiter im Sub-Menü zu klein | **Berechtigt** | Reiter größer + farbige Icons + größerer Text | S | **Mittel (Quick Win)** |
| 3 | 4 Menüs → nur Home + Einstellungen | **Produktentscheidung** | Tabs reduzieren — mit Einschränkung (s. u.) | S | **Mittel (Entscheidung)** |
| 4 | Notizen-Bereich überflüssig | **Produktentscheidung** | Notizen entfernen ODER in Schaden integrieren | S–M | **Niedrig (Entscheidung)** |

---

## Finding 1 — Grauer Balken verdeckt die Bedienikonen

**Bescheid: Berechtigt und bereits als Kernproblem bekannt.**

Auf der ONE (großes Display) läuft die Inspektion im Vollbild; die App blendet ihre eigene Navigationsleiste dort bewusst aus (`NavGraph.kt:109`). Der graue Balken unten ist **nicht** Teil der App, sondern die **Android-System-Taskbar der Gesten-Navigation** (launcher3). Sie legt sich genau über die untere Softbutton-Leiste (`InspectionScreen.kt:667 ff.`) und verdeckt damit die Funktionsicons — exakt das beschriebene Symptom.

Das ist intern dokumentiert: `RESULT_NAVBAR_FIX.md` (Diagnose + erster Fix über `navigation_mode=0`) und `FIX_NAVBAR_V2_PROMPT.md` (Auftrag, die in der Original-App bewährte Technik zu übernehmen). Der erste Fix war nur eine Teillösung; bestimmte Dialoge/die Tastatur holen den Balken zurück.

**Maßnahmen:**
1. Den V2-Ansatz fertigstellen: Original-Technik übernehmen (Legacy-Immersive-Flags `5894` + Re-Hide-Listener auf jedem Fenster, Dialoge zusätzlich kurz `FLAG_NOT_FOCUSABLE`) — Vorlage liegt bereits in `FIX_NAVBAR_V2_PROMPT.md`.
2. Sicherstellen, dass auf dem Testgerät `WRITE_SECURE_SETTINGS` erteilt ist (sonst greift `navigation_mode=0` nicht — die App loggt das). Gehört ins Golden-Image der Provisionierung.
3. Am Gerät jeden Dialog + Tastatur durchspielen (Schaden, Notiz, Foto, Export, Dropdowns) — Erfolgskriterium ist der Screenshot, nicht die Theorie.

**Aufwand:** S–M (Konzept steht, On-Device-Iteration nötig). **Risiko:** gering. **KRITIS:** positiv — die Maßnahme härtet zugleich den Kiosk-Charakter (kein Ausbruch zur Android-Oberfläche).

---

## Finding 2 — Bedienikonen verschwinden automatisch (~3 s)

**Bescheid: Berechtigt. Funktion ist gewollt, aber das automatische Einschalten ist das Problem.**

Es gibt ein Auto-Ausblenden im Cinema-Modus: das rechte Panel nach 5 s (`Dimensions.ControlsAutoHideMs = 5000`, `InspectionScreen.kt:328`), das untere Bedien-Band nach 4 s (`InspectionScreen.kt:339`). Beide starten ausgeblendet und werden per Video-Tipp gemeinsam ein-/ausgeblendet (`InspectionScreen.kt:583`). Die Wahrnehmung „nach 3 Sekunden" passt also der Sache nach — der genaue Wert ist 4–5 s.

Die Bewertung des Kollegen ist deckungsgleich mit dem Wunsch: Auto-Ausblenden ja, aber **nicht** als Standard-Automatik.

**Maßnahmen:**
1. Neue Einstellung „Bedienleiste automatisch ausblenden" — **Default AUS**. Aus = Leiste bleibt dauerhaft sichtbar (Auto-Hide-Timer wird nicht gestartet). An = heutiges Verhalten.
2. Alternativ/ergänzend ein kleines Pin-Symbol direkt in der Leiste zum Fixieren/Lösen.
3. Auto-Hide-Dauer (4–5 s) optional konfigurierbar; mindestens vereinheitlichen.

**Aufwand:** S (eine Einstellung + Timer-Gate). **Risiko:** sehr gering. Echter Quick Win.

---

## Finding 6 — Meterzähler wackelt sporadisch

**Bescheid: Berechtigt. Technische Ursache identifiziert.**

Der Meterwert wird ungefiltert durchgereicht: Group-22-Frame → 32-bit-Wert (`OneInternalHardwareService.kt:333`) → `LinearMeterCalculator` ist aktuell ein **Identitäts-Stub ohne jede Glättung/Linearisierung** (`LinearMeterCalculator.kt:11`) → 1:1 in die Anzeige (`InspectionScreen.kt:376`). Zusätzlich prüft der RX-Parser nur Magic + Länge, **keine Prüfsumme** der einzelnen Subframes (`OneFrameCodec.kt:109`). Folge: Ein einzelner verrauschter/verstümmelter Frame schlägt sofort als Sprung in der Anzeige durch — und ist nach dem nächsten korrekten Frame wieder weg. Das erklärt exakt „wackelt ab und zu" und „nach ½ m raus/rein ist es weg".

**Maßnahmen (gestaffelt):**
1. **Plausibilitätsfilter:** unrealistische Sprünge zwischen zwei Frames verwerfen (max. Δ pro Zeiteinheit / physikalische Obergrenze).
2. **Leichte Glättung der Anzeige:** Median über die letzten N Werte oder kleiner Tiefpass — beseitigt Einzelausreißer, ohne spürbare Verzögerung.
3. **RX-Frame validieren:** Prüfsumme/XOR der Subframes auswerten (falls vorhanden) und fehlerhafte Frames verwerfen, statt sie anzuzeigen.
4. **Mittelfristig** die echte Linearisierungstabelle der ONE portieren (`LinearMeterCalculator` ist dafür schon vorbereitet — Hinweis auf `LinearDataPoints.java` im Code) → korrekte Absolutwerte über die Kabel-Dehnung.

**Aufwand:** M. **Risiko:** mittel — am realen Kabel kalibrieren/gegentesten. **KRITIS:** Datenintegrität — der Meterwert geht in Bericht/OSD ein; saubere Werte sind dokumentationsrelevant.

---

## Finding 5 — Buttons/Reiter im Sub-Menü zu klein

**Bescheid: Berechtigt.**

Gemeint ist die Reiter-Leiste in der Projektansicht mit den vier Reitern Fotos / Schäden / Videos / Notizen (`ProjectDetailScreen.kt:474–482`). Die Reiter sind aktuell **reiner Text + optionales Zähler-Badge, ohne Icon** (`ProjectDetailScreen.kt:804`) und nutzen die Standard-Reiterhöhe — auf dem großen Feld-Display (ggf. mit Handschuh) zu klein und ohne visuellen Anker. Der Platz ist vorhanden.

**Maßnahmen:**
1. Reiterhöhe vergrößern (z. B. ≥ 72 dp gemäß Touch-Vorgabe in `Dimensions.kt`).
2. Je Reiter ein **farbiges Icon** ergänzen (Foto / Schaden / Video / Notiz) + größerer Text.
3. Aktiven Reiter farblich klar absetzen (Amber-Akzent passt zum SA-Design).

**Aufwand:** S (eine Komponente, `TabWithBadge`). **Risiko:** gering. Quick Win, gut mit Finding 4 zu bündeln.

---

## Finding 3 — 4 Menüs → nur Home + Einstellungen

**Bescheid: Nachvollziehbar, aber Produktentscheidung mit Einschränkung — nicht 1:1 empfohlen.**

Die Hauptnavigation hat vier Einträge: Home, Inspektion, Projekte, Einstellungen (`NavGraph.kt:76–81`). Der Einwand „alles ist schon über Home erreichbar" stimmt für **Projekte** (Home hat eine Schnellzugriff-Kachel). Bei **Inspektion** rate ich ab, den Tab zu streichen: das Live-Bild ist die meistgenutzte Funktion — ein direkter Sprung dorthin ist wertvoll, ein Umweg über Home kostet im Feld Zeit.

**Optionen zur Entscheidung:**
- **A (empfohlen):** Auf drei Einträge reduzieren — Home, Inspektion, Einstellungen. „Projekte" entfällt aus der Leiste (bleibt über Home erreichbar).
- **B (Wunsch Kollege):** Nur Home + Einstellungen. Inspektion und Projekte nur über Home.
- **C:** Bei vier Einträgen bleiben, aber Home als klare Startseite schärfen.

**Aufwand:** S (Liste kürzen). **Risiko:** gering technisch; UX-Wirkung beachten (Erreichbarkeit/Klickwege).

---

## Finding 4 — Notizen-Bereich überflüssig

**Bescheid: Teilweise berechtigt — Produktentscheidung mit Funktionsverlust-Risiko.**

Es gibt einen eigenständigen Notiz-Pfad: Reiter „Notizen" (`ProjectDetailScreen.kt:482, 504`), Notiz-Aktion im Inspektions-Panel (`InspectionScreen.kt:998`) sowie eigene Datenhaltung (`NoteEntity`/`NoteDao`/`NoteRepository`/`NoteDialog`). Der Einwand „passt in den Schaden" trifft auf **Text** zu — der Schaden hat ein Freitext-Beschreibungsfeld. **Aber:** Notizen können auch **Sprachnotizen (Audio)** sein (Mikrofon-Icon, `audioPath`) und sind **nicht an einen Schaden gebunden** (freie Notiz an einer Position). Beides ginge bei ersatzlosem Entfernen verloren.

**Optionen zur Entscheidung:**
- **A:** Notizen-Reiter/-Aktion entfernen, Freitext nur noch am Schaden — schlanker, aber Sprachnotiz + schadenslose Notiz fallen weg.
- **B (empfohlen):** Sichtbarkeit reduzieren statt löschen — Notiz aus der Hauptbedienung nehmen, Funktion (inkl. Audio) hinter dem Schaden-/Detailbereich behalten.
- **C:** Schaden um ein Audio-Memo erweitern, dann Notizen entfernen — vereint beide Wünsche, etwas mehr Aufwand.

**Aufwand:** S–M je nach Option. **Risiko:** mittel — vor dem Entfernen klären, ob Sprachnotizen im Feld genutzt werden.

---

## Offene Entscheidungen für dich (CEO/PO)

1. **Finding 3:** Drei Einträge (Home/Inspektion/Einstellungen, empfohlen) oder zwei (nur Home/Einstellungen)?
2. **Finding 4:** Notizen entfernen, verstecken (empfohlen) oder als Audio-Memo in den Schaden integrieren?
3. **Versionsabgleich:** Welche App-Version hat der Kollege getestet? (Klärt, ob Finding 1 im aktuellen Build schon teilweise behoben ist.)

## Empfohlene Reihenfolge der Umsetzung

1. **Finding 1** (Balken weg) + **Finding 2** (Auto-Hide abschaltbar) — gemeinsam, beheben den unmittelbaren Bedien-Eindruck.
2. **Finding 6** (Meterzähler stabil) — Datenqualität der Kernmessung.
3. **Finding 5** (Reiter größer/farbig) — Quick Win, mit Finding 4 bündeln.
4. **Finding 3 + 4** nach deiner Entscheidung.
