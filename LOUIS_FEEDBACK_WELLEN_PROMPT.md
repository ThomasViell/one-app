# Auftrag: Louis-Feedback DrainQ.ONE in Wellen umsetzen (On-Device, autonom)

**Rolle:** Autonomer On-Device-Entwickler für DrainQ.ONE. Setze die unten beschriebenen Wellen der Reihe nach um, baue + teste nach jeder Welle, committe gezielt und verifiziere am Gerät. Quelle der Wahrheit ist der Screenshot am Gerät, nicht die Theorie.

**Quelle:** Feldtest-Feedback Louis Wigman (E-Mail „Software ONE Pushrod", 2026-06-13). Vollständige Analyse: `FEEDBACK_Kollege_2026-06-13_Analyse.md`.

---

## Kontext & Randbedingungen (verbindlich)

- **Hardware:** App läuft direkt auf der ONE (RK3588, Android 12). Steuerung seriell `/dev/ttyS5`, Video V4L2 `/dev/video0`. Kiosk ist AN.
- **Gerät:** Serial `233b4bd2865177ed`.
- **Branch:** Neuen Branch `feature/louis-feedback` von `feature/beta-wave-1` abzweigen. **Vorher** sicherstellen, dass `feature/beta-wave-1` gepusht ist (Status meldet ungepushte Commits = Verlustrisiko).
- **Build/Test:** `$env:JAVA_HOME="C:\Android\jdk17"; cd C:\Projekte\drainq.one; .\gradlew assembleDebug test`
- **Install:** `$env:ANDROID_SERIAL="233b4bd2865177ed"; .\gradlew installDebug`
- **Git-Hygiene:** **Gezielt committen, nie `git add -A`.** Ein Commit pro Welle (Vorschläge unten). Nach Abschluss pushen.
- **Datei-Schreibzugriffe:** Nur über Editor-Tools. **Keine bash/python-Rewrites großer Dateien** (CRLF/Korruption — schon einmal LocalizationManager.kt zerstört).
- **Kein `su`.** HW-Serial nativ. `WRITE_SECURE_SETTINGS` ist am Gerät erteilt.
- **Lokalisierung:** Neue UI-Strings nur **de + en** pflegen (BETA-Gate); restliche Sprachen laufen über Fallback de und werden im nächsten L10N-Lauf nachgezogen.
- **Tests:** Müssen nach jeder Welle grün bleiben. Neue Logik = neuer Test.
- **Belege:** Vorher/Nachher-Screenshots je Welle nach `tools/_louis/` ablegen.

---

## Reihenfolge & Wellenübersicht

| Welle | Inhalt | Finding | Risiko |
|---|---|---|---|
| W0 | Vorbereitung + grüne Baseline | — | — |
| W1 | Grauer Balken (System-Leiste) endgültig weg | 1 | gering |
| W2 | Auto-Ausblenden abschaltbar, Default AUS | 2 | sehr gering |
| W3 | Meterzähler stabilisieren | 6 | mittel (Gerät) |
| W4 | Sub-Menü-Reiter größer + farbige Icons | 5 | gering |
| W5 | Hauptnavigation verschlanken | 3 | gering (Entscheidung) |
| W6 | Notizen mit Schaden zusammenlegen | 4 | mittel (Entscheidung) |
| W7 | Doku + Hinweise (USB, Neustart, Sprachen) | 7/9/8 | gering |
| W8 | Abschluss: Build, On-Device-Abnahme, Push, Result-Doc | — | — |

> **Zwei Entscheidungen sind vor W5/W6 zu bestätigen** (siehe dort). Default-Vorgabe ist gesetzt — falls Thomas anders entscheidet, nur den jeweiligen Default ändern.

---

## W0 — Vorbereitung

1. `feature/beta-wave-1` pushen (sichern), dann `feature/louis-feedback` abzweigen.
2. Baseline-Build + Tests grün: `.\gradlew assembleDebug test`.
3. App aufs Gerät: `installDebug`. Ausgangs-Screenshots der Inspektionsansicht + Projektansicht nach `tools/_louis/`.

**Abnahme:** Build grün, App startet, Screenshots liegen vor.

---

## W1 — Grauer Balken verdeckt die Bedienicons (Finding 1)

**Ziel:** Die Android-System-Leiste (launcher3-Gesten-Taskbar) erscheint an KEINER Stelle mehr über der unteren Bedienleiste — auch nach Dialogen und Tastatureingabe.

**Vorgehen:** Den bereits ausgearbeiteten Auftrag **`FIX_NAVBAR_V2_PROMPT.md` ausführen** (Original-App-Technik: Legacy-Immersive-Flags `5894` + Re-Hide-Listener auf jedem Fenster; Dialoge zusätzlich kurz `FLAG_NOT_FOCUSABLE`). `navigation_mode=0`-Logik in `MainActivity` bleibt als zusätzliche Absicherung.

- Wiederverwendbarer Helfer für ALLE Compose-Dialoge/Popups (`DamageDialog`, `NoteDialog`, `ImagePickerDialog`, Export-/USB-/Lösch-Dialoge, Dropdowns).
- Sicherstellen, dass `WRITE_SECURE_SETTINGS` am Gerät erteilt ist (sonst greift `navigation_mode=0` nicht — App loggt das).

**Abnahme (am Gerät, jeder Punkt streifenfrei):** Aufnahme (Ohne/Mit Einblendung, Pause/Stop), Foto, Schaden-Dialog **inkl. Tastatureingabe**, Notiz-Dialog, Logo-Auswahl, Export/USB, Dropdowns, Projektansicht nach Aufnahme. Vorher/Nachher nach `tools/_louis/`.

**Commit:** `fix(kiosk): System-Leiste über Bedienleiste endgültig unterdrückt (Original-Technik)`

🔒 **KRITIS-Check:** RELEVANT/OK — härtet den Kiosk-Charakter (kein Ausbruch zur Android-Oberfläche am Feldgerät); positiv für Zugriffskontrolle. Keine neuen Berechtigungen außer dem bereits dokumentierten `WRITE_SECURE_SETTINGS`.

---

## W2 — Auto-Ausblenden der Bedienicons abschaltbar machen (Finding 2)

**Ziel:** Bedienicons bleiben standardmäßig **dauerhaft sichtbar**. Das automatische Ausblenden bleibt als **optionale** Funktion erhalten, ist aber per Default AUS und nur über die Einstellungen aktivierbar.

**Aufgaben:**
1. Neue Einstellung „Bedienleiste automatisch ausblenden" (DataStore-Key z. B. `controls_auto_hide`, **Default `false`**). In `SettingsViewModel` + Settings-State aufnehmen, Toggle in `SettingsScreen` ergänzen.
2. In `InspectionScreen.kt` die Auto-Hide-Effekte nur greifen lassen, wenn die Einstellung AN ist:
   - Cinema-Auto-Hide `showControls` (Z. 328–336)
   - unteres Band `showBottomBar` (Z. 339–347)
3. Bei Einstellung AUS: untere Bedienleiste beim Öffnen der Inspektion einblenden und **sichtbar lassen** (kein Timer). Das rechte Panel darf weiter tap-on-demand bleiben (verdeckt mehr Bild).
4. Lokalisierung de+en: `settings_autohide_title`, `settings_autohide_desc`.

**Abnahme:** Mit Default-Einstellung bleiben die Icons sichtbar; nach Einschalten der Option blenden sie wie bisher nach Inaktivität aus. Am Gerät bestätigen.

**Commit:** `feat(inspection): Auto-Ausblenden der Bedienleiste optional (Default aus)`

🔒 **KRITIS-Check:** OK — reine UI-/Einstellungsänderung, keine Compliance-Relevanz.

---

## W3 — Meterzähler stabilisieren (Finding 6)

**Ziel:** Der Meterwert läuft ruhig und stabil; einzelne Ausreißer-Frames schlagen nicht mehr auf die Anzeige durch.

**Ursache (Ist-Zustand):** Group-22-Wert wird ungefiltert durchgereicht — `OneInternalHardwareService.kt:333–346` → `LinearMeterCalculator` ist Identitäts-Stub (`LinearMeterCalculator.kt:11`) → 1:1 in die Anzeige (`InspectionScreen.kt:376`). Der RX-Parser prüft nur Magic + Länge, keine Prüfsumme der Subframes (`OneFrameCodec.kt:109`).

**Aufgaben (gestaffelt):**
1. **Plausibilitätsfilter** im Meter-Pfad: unrealistische Sprünge zwischen zwei aufeinanderfolgenden Frames verwerfen (physikalisch sinnvolle Δ-Obergrenze pro Update). Verworfener Frame = letzten gültigen Wert halten.
2. **Glättung der Anzeige:** kleiner Median/gleitender Mittelwert über die letzten N Werte (N klein halten, damit keine spürbare Verzögerung entsteht).
3. **RX-Frame validieren:** prüfen, ob das Protokoll eine Prüfsumme/XOR über die RX-Subframes trägt; falls ja, defekte Frames verwerfen statt anzeigen. Falls nein: Punkt 1 ist die Absicherung.
4. **Tests:** `OneFrameCodecTest` ergänzen (Ausreißer/Defekt-Frame) + neuer Filter-/Glättungstest.
5. **(Optional, Folge):** echte Linearisierungstabelle der ONE in `LinearMeterCalculator` portieren (Vorbereitung im Code dokumentiert: `LinearDataPoints.java`) — für korrekte Absolutwerte; nur wenn am Kabel kalibrierbar.

**Abnahme:** Am Gerät Kabel mehrfach aus-/einziehen; Anzeige bleibt ruhig, kein Springen. Reset (absolut/Strecke) weiterhin korrekt.

**Commit:** `fix(meter): Plausibilitätsfilter + Glättung gegen Ausreißer im Meterzähler`

🔒 **KRITIS-Check:** RELEVANT — Inspektionsdaten-Integrität (der Meterwert geht in Bericht/OSD ein). Filter/Glättung verbessern die Datenqualität; sicherstellen, dass keine echten Bewegungen verschluckt werden (Δ-Schwelle großzügig genug).

---

## W4 — Sub-Menü-Reiter größer + farbige Icons (Finding 5)

**Ziel:** Die Reiter-Leiste in der Projektansicht (Fotos / Schäden / Videos / Notizen) ist größer, mit farbigem Icon je Reiter und größerem Text — gut mit Handschuh bedienbar.

**Aufgaben:**
1. `ProjectDetailScreen.kt` — `TabWithBadge` (Z. 804–824) um ein **Icon je Reiter** erweitern (Foto / Schaden / Video / Notiz), Reiterhöhe ≥ 72 dp, größerer Text.
2. Aktiven Reiter klar absetzen (Amber-Akzent, SA-Design).
3. TabRow-Layout prüfen (ggf. `ScrollableTabRow`), damit Icon + Label + Badge auf das Display passen.

**Abnahme:** Reiter deutlich größer/farbig, lesbar, mit Handschuh treffbar. Screenshot.

**Commit:** `feat(projectdetail): Reiter größer + farbige Icons (Foto/Schaden/Video/Notiz)`

🔒 **KRITIS-Check:** OK — rein kosmetische UI-Änderung.

---

## W5 — Hauptnavigation verschlanken (Finding 3) — ENTSCHEIDUNG

**Default (empfohlen):** `bottomNavItems` (`NavGraph.kt:76–81`) auf **drei Einträge** reduzieren: **Home, Inspektion, Einstellungen**. „Projekte" entfällt aus der Leiste/Rail (bleibt über die Home-Schnellzugriffskachel erreichbar). Inspektion bleibt direkt erreichbar, weil meistgenutzt.

**Alternative (Wunsch Louis):** Nur **Home + Einstellungen**. Inspektion und Projekte nur über Home.

**Aufgaben:**
1. `bottomNavItems` entsprechend kürzen (wirkt auf Bottom-Bar und Rail).
2. Sicherstellen, dass entfernte Ziele über `HomeScreen` erreichbar sind (Schnellzugriffskacheln prüfen/ergänzen).
3. Tote Navigationspfade vermeiden (Routen bleiben im NavGraph bestehen, nur die Leisteneinträge ändern sich).

**Abnahme:** Navigation schlüssig, alle Bereiche erreichbar, kein Sackgassen-Pfad. Screenshot.

**Commit:** `feat(nav): Hauptnavigation auf Home/Inspektion/Einstellungen verschlankt`

🔒 **KRITIS-Check:** OK — Navigations-/UI-Änderung, keine Compliance-Relevanz.

---

## W6 — Notizen mit Schaden zusammenlegen (Finding 4) — ENTSCHEIDUNG

**Default (empfohlen, kein Datenverlust):** Notiz aus der **Haupt-/Inspektionsbedienung** entfernen — der Schaden mit seinem Freitext-Beschreibungsfeld ist der primäre Erfassungsweg. Den **Notizen-Reiter und die Sprachnotiz (Audio) im Projekt-Detail behalten**, damit keine Funktion (insb. Audio, schadensunabhängige Notiz) verloren geht.
- Konkret: Notiz-Button im Inspektions-Panel (`InspectionScreen.kt:998–1011`) entfernen; `NoteDialog`/`NotesTab`/Daten unverändert lassen.

**Alternative (vollständige Zusammenlegung, mehr Aufwand):** Notizen ganz entfernen und dafür ein **Audio-Memo + Freitext direkt in den Schaden** (`DamageDialog`) integrieren; `NotesTab` (`ProjectDetailScreen.kt:482,504`) entfernen; bestehende Notizen migrieren/zuordnen. **Vor Umsetzung klären**, ob schadensunabhängige Notizen im Feld gebraucht werden.

**Aufgaben (Default):**
1. Notiz-Aktion aus dem Inspektions-Bedienpanel nehmen.
2. Notiz weiterhin im Projekt-Detail erfass-/abrufbar lassen.
3. Lokalisierung prüfen (keine neuen Strings nötig).

**Abnahme:** Schaden ist der klare Erfassungsweg in der Inspektion; Notiz inkl. Audio bleibt im Detail nutzbar. Screenshot.

**Commit:** `feat(inspection): Notiz aus Hauptbedienung entfernt, Schaden als primärer Weg`

🔒 **KRITIS-Check:** RELEVANT — bei der Alternative auf **Rückwärtskompatibilität** achten (bestehende Notizen dürfen bei Migration nicht verloren gehen). Default ist datensicher.

---

## W7 — Dokumentation & Hinweise (Findings 7, 9, 8)

**Ziel:** Bekannte Bedien-Stolpersteine in der Bedienungsanleitung dokumentieren; eine Bedien-Verbesserung optional einbauen.

**Aufgaben:**
1. **USB-Stick (Finding 7):** In der Bedienungsanleitung ergänzen, dass der USB-Stick einmalig eingerichtet/freigegeben werden muss, sonst werden keine Daten gespeichert (Quelle: `generate_manual.js` / `ONE_APP_Bedienungsanleitung`). **Optional Code:** im `UsbExportDialog` eine klare Meldung + Sprung zur Berechtigungsseite zeigen, wenn Stick/Berechtigung fehlt.
2. **Sprachwechsel-Neustart (Finding 9):** In der Anleitung vermerken, dass nach Sprachwechsel ein Neustart nötig ist. **Optional (Folge):** prüfen, ob der Neustart vermeidbar ist (Sprache live anwenden).
3. **Weitere Sprachen (Finding 8):** Kein Code — Hinweis: Sprachen für die BETA bewusst auf de+en begrenzt (CEO-Beschluss 2026-06-07); weitere Sprachen + Berichts-Review nach BETA über die Portal-Freischaltung.

**Abnahme:** Anleitung enthält beide Hinweise; falls optionaler USB-Dialog umgesetzt: am Gerät bestätigt.

**Commit:** `docs: USB-Einrichtung + Sprachwechsel-Neustart in Anleitung; USB-Dialog-Hinweis`

🔒 **KRITIS-Check:** RELEVANT (USB) — Datenexport auf Wechselmedium ist ein Datenabfluss-Pfad: Zielpfad validieren, keine Schreibzugriffe außerhalb des vorgesehenen Stick-Ordners, im Risikoregister vermerken (für Pilot akzeptiert). Keine neuen Dependencies.

---

## W8 — Abschluss

1. `.\gradlew assembleDebug test` grün; `installDebug` aufs Gerät.
2. **On-Device-Abnahme** je Welle (Tabelle unten) mit Screenshots nach `tools/_louis/`.
3. Gezielte Commits prüfen, Branch `feature/louis-feedback` **pushen**.
4. `RESULT_LOUIS_FEEDBACK.md` schreiben: je Welle geänderte Dateien (Datei:Zeile), Abnahme-Status, offene Punkte. Danach Review → Merge-Entscheidung → Tag.

### Abnahme-Tabelle

| # | Kriterium | Status | Beleg |
|---|-----------|--------|-------|
| 1 | Kein grauer Balken über Bedienicons (inkl. Dialog+Tastatur) | ☐ | |
| 2 | Icons bleiben sichtbar (Default); Auto-Hide nur per Option | ☐ | |
| 3 | Meterzähler läuft ruhig, kein Springen bei Aus-/Einziehen | ☐ | |
| 4 | Reiter größer + farbige Icons, mit Handschuh bedienbar | ☐ | |
| 5 | Navigation verschlankt, alle Bereiche erreichbar | ☐ | |
| 6 | Notiz/Schaden wie entschieden, kein Datenverlust | ☐ | |
| 7 | Anleitung: USB-Einrichtung + Sprach-Neustart dokumentiert | ☐ | |
| 8 | `gradlew test` grün | ☐ | |

---

## Security-Regeln (für alle Wellen)

- Keine Secrets im Code; keine neuen Netzwerk-Endpunkte ohne Not.
- Eingaben validieren (USB-Zielpfad, Textfelder); Fehler ohne Stack-Trace an den Nutzer.
- Keine neue Dependency ohne dokumentierten Grund (Lizenz/Wartung prüfen).
- Rückwärtskompatibel bleiben — kein Datenverlust bei App-Update (v. a. W6-Alternative).
- Neue Logik testabgedeckt; bestehende Tests grün halten.

## Liefergegenstände

- Branch `feature/louis-feedback` mit einem Commit je Welle, gepusht.
- `RESULT_LOUIS_FEEDBACK.md` (Fundstellen, Abnahme, offene Punkte).
- Vorher/Nachher-Screenshots in `tools/_louis/`.
