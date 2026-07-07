# Ergebnis: Louis-Feldfeedback 06.07. — Welle 1 (Quick Wins)

**Branch:** `feature/dual-mode` (kein Merge, kein Tag)
**Stand:** 2026-07-07
**Quelle:** Louis' Beta-Test 06.07., verifiziert gegen den Code am 07.07.

Alle fünf sofort im Code lösbaren Punkte umgesetzt. Jeder QW einzeln committet, nach
jedem QW `.\gradlew assembleDebug test` grün. Reines UI — Direkt-Modus/Hardware
(`OneInternalHardwareService`, Serial/V4L2, `OneRemoteServer`, `AccessPointController`)
unberührt. Neue L10n-Strings ausschließlich über `S()`-Keys (de + en), Generator nicht
ausgeführt.

| QW | Louis | Commit | Kern |
|----|-------|--------|------|
| QW1 | #4 | `d31a462` | Licht-Taste bis 100 % |
| QW2 | #2 | `287f561` | Aktive Sonde-Frequenz grün markiert |
| QW3 | #7 | `df37dfe` | Einstellung „Datum & Uhrzeit stellen" |
| QW4 | #3 | `067fb5f` | Info-Panel per X schließbar |
| QW5 | #1 + #6 | `44910ed` | Schadensart-Auswahl dialog-fest |

---

## QW1 — Licht bis 100 % (Louis #4)

**Geändert:**
- `ui/screens/inspection/InspectionControls.kt` (neu): `LightCycle = intArrayOf(0, 30, 60, 100)` + `nextLightLevel()` (Z. 11–14).
- `ui/screens/inspection/InspectionScreen.kt:481`: `HwButton.LIGHT` nutzt `nextLightLevel(lightLevel)` statt Inline-Zyklus `0,30,60,90`.
- `ui/screens/inspection/InspectionControlsTest.kt` (neu): Zyklus 0→30→60→100→0, Zwischenwerte, „90→100"-Regression, 3-Tipp-Kriterium.

**Warum extrahiert:** testbare reine Funktion (Auftrag: Unit-Test für QW1). Der Slider im Popup stand bereits auf `0f..100f` — nur der Tasten-Zyklus deckelte bei 90 %.

**Geräte-Checkliste:**
- [ ] Licht-Taste (F1) 3× tippen → Popup/OSD zeigt `100%`.
- [ ] 4. Tipp → zurück auf `0%`.
- [ ] Slider im Licht-Popup erreicht weiterhin 100 %.

## QW2 — Aktive Sonde-Frequenz grün markieren (Louis #2)

**Geändert:**
- `ui/screens/inspection/InspectionControls.kt:26`: `isSondeFrequencyActive(optionCode, rxLabel)` — testbare Bestimmung der aktiven Frequenz.
- `ui/screens/inspection/InspectionScreen.kt:825`: Sonde-Popup markiert die aktive Option grün (`OsdColorGreen`) + `FontWeight.Bold`, sonst weiß/normal.
- `InspectionControlsTest.kt`: beide Label-Formate + „genau eine aktive Option".

**Robustheits-Hinweis (über den Prompt hinaus):** Der Prompt nahm ein einziges Label-Format an. Tatsächlich liefert die Dual-Mode-Branch **zwei**: DIRECT (`OneInternalHardwareService`) über `SondeFrequency.name()` → `"33 kHz"` (mit Leerzeichen); WIFI/Remote (`OneHardwareService`) über `OneRemoteProtocol.freqLabel()` → `"33kHz"` (ohne, `null` = Off). Der Vergleich normalisiert daher (ohne Leerzeichen, case-insensitiv) und trifft beide Modi. Kein neuer State, kein geändertes Senden — reine Darstellung.

**Geräte-Checkliste:**
- [ ] DIRECT (App auf ONE): Frequenz wählen → im wieder geöffneten Popup genau diese grün+fett.
- [ ] WIFI (Tablet→ONE): dito (Label ohne Leerzeichen darf die Markierung nicht verfehlen).
- [ ] „Off/Aus" gewählt → Off-Zeile grün; keine zweite Zeile grün.

## QW3 — Einstellung „Datum & Uhrzeit stellen" (Louis #7)

**Geändert:**
- `ui/screens/settings/SettingsScreen.kt:225–246`: neue klickbare `DqCard` → Intent `Settings.ACTION_DATE_SETTINGS` (+`FLAG_ACTIVITY_NEW_TASK`), defensiv gegen `ActivityNotFoundException` (gesperrtes Gerät).
- `res/drawable/ic_dq_clock.xml` (neu, Tabler-Stil) + `ui/components/DqComponents.kt:104`: Icon-Key `"clock"`.
- `ui/localization/LocalizationManager.kt:117/982`: `settings_datetime_title` + `settings_datetime_desc` (de + en).

**Warum:** Das Inspektionsdatum wird bereits mit `LocalDate.now()` vorbelegt — es zeigt nur 01.01.2021, weil die **Geräte-Uhr** falsch steht (ONE fällt offline auf 2021 zurück). Die App setzt die Systemuhr **nicht** programmatisch (privilegiert) — nur der Sprung in die OS-Einstellung.

**Geräte-Checkliste:**
- [ ] Einstellungen → „Datum & Uhrzeit stellen" öffnet die Android-Datum/Uhrzeit-Seite.
- [ ] Nach Uhr-Korrektur: neues Projekt hat automatisch das richtige Datum.
- [ ] Auf der gesperrten ONE: kein Crash, falls die Seite fehlt (dann nur Log-Warnung).

## QW4 — Info-Panel zuverlässig schließen (Louis #3)

**Geändert:**
- `ui/screens/inspection/InspectionScreen.kt:1021` (Kopfzeile des rechten Panels): X-Button (`Icons.Default.Close`, Touch-Target 48 dp = `TouchMin`, Glyph 20 dp, `onSurfaceVariant`) setzt `showControls = false`.

**Warum:** Das Panel schluckte Taps auf seine Buttons — ein Tipp auf das Panel schloss es nicht; nur der Zurück-Pfeil (verlässt den Screen) blieb. Das X schließt zuverlässig, **ohne** die Inspektion zu verlassen. Video-Tipp-Toggle und unteres Bedienband bleiben unverändert (nur `showControls`, nicht `showBottomBar`).

**Geräte-Checkliste:**
- [ ] Panel per Videotipp öffnen → X in der Panel-Kopfzeile schließt es.
- [ ] Screen wird dabei **nicht** verlassen (Inspektion bleibt).
- [ ] Unteres Bedienband bleibt sichtbar/unverändert.

## QW5 — Schadensart-Auswahl dialog-fest (Louis #1 + #6) — höchste Priorität

**Geändert:**
- `ui/screens/inspection/DamageDialog.kt:309–365`: `ExposedDropdownMenu` ersetzt durch read-only `OutlinedTextField` (Dropdown-Pfeil) mit transparenter Klickfläche darüber; Klick öffnet einen `AlertDialog` mit Radio-Liste der `damageTypes`. Tap auf eine Zeile setzt `selectedType` und schließt. `HideSystemBarsInDialog()` in der Auswahl beibehalten. State `dropdownExpanded` → `showTypePicker`.

**Warum:** Das `ExposedDropdownMenu` verankerte sein Popup am **Activity**-, nicht am **Dialog**-Fenster → auf der ONE griff die Auswahl nicht, „Risse" ließ sich nicht ändern (blockierte die Kernerfassung). Der `AlertDialog` ist ein eigenes Top-Level-Fenster ohne diese Verankerungs-Falle; die transparente Klickfläche macht den Tap unabhängig vom Gesten-Handling des read-only-Felds. Speicherlogik unverändert. **Nicht angefasst:** das gleich aussehende `ExposedDropdownMenu` in `ProjectFormScreen.kt` (Vollbild-Screen, funktioniert).

**Geräte-Checkliste (Kern):**
- [ ] Dialog „Schaden erfassen" → Schadensart-Feld tippen → Auswahl-Dialog öffnet.
- [ ] Jeden Katalogwert wählbar; Auswahl steht danach im Feld.
- [ ] „Risse" → anderer Wert → **wird gespeichert** (im Schaden + Bericht).
- [ ] Zurück/Außentipp schließt die Auswahl ohne Änderung.

---

## Adversariale Selbst-Review (vor letztem Commit)

- **QW5 — greift die Auswahl?** Ja. `selectedType` ist derselbe State wie Feldanzeige *und* Speichern (`damageType = selectedType`). Der Tap trifft die Overlay-Fläche (unabhängig vom Feld-Gesten-Handling — genau die Bug-Ursache). `AlertDialog` = echtes Fenster, keine Activity-vs-Dialog-Verankerung. `RadioButton onClick = null` + Row-`clickable` → kein Doppel-Handling.
- **QW4 — Leak / Doppel-Toggle?** Nein. Der X-Button setzt **nur** `showControls = false` (kein Toggle), keine neue Ressource/Coroutine/Listener. `showBottomBar` bleibt unberührt.
- **QW2 — Off-Sonderfall:** Off-Option nutzt Code 0 → interner Vergleich über `SondeFrequency.name(0)`, unabhängig vom (lokalisierten) Anzeige-Label „Aus". Test „genau eine aktive Option" deckt alle rx-Formate ab.

## Bewusst außerhalb dieser Welle (nicht bearbeitet)

- **Louis #5a (Foto aus Video):** bereits gefixt (`35fd3f5`) — nur am Gerät gegenchecken.
- **Louis #8 (Durchmesser/Länge):** Code intakt → erst Geräte-Repro, dann eigene Aufgabe.
- **Louis #9a + #5b (Video nach Pause / nur Endzeit):** echter Aufnahme-Pfad-Bug → eigene Welle mit Gerätetest.
- **Louis #9b (Report auf USB):** Bedienung/Stick-Format (FAT32/exFAT) → Handbuch, kein Code.
