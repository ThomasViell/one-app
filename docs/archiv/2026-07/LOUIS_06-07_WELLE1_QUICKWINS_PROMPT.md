# Auftrag: Louis-Feldfeedback 06.07. — Welle 1 (Quick Wins)

**Branch:** `feature/dual-mode` (weiterarbeiten, KEIN Merge, kein Tag)
**Quelle:** Louis' Beta-Test 06.07. (Dokument „ONE DrainQ Beta test 06.07"), verifiziert gegen den Code am 07.07.
**Ziel:** Die fünf sofort im Code lösbaren Punkte umsetzen. Jeder Punkt: Build + Tests grün, dann kleiner Commit `fix(louis-w1): …`.

Die übrigen Louis-Punkte sind NICHT Teil dieser Welle (siehe „Bewusst außerhalb"). Nicht mitmachen.

---

## QW1 — Licht bis 100 % (Louis #4)

**Ist:** `ui/screens/inspection/InspectionScreen.kt`, `HwButton.LIGHT` (~Z. 480–483): Zyklus `intArrayOf(0, 30, 60, 90)` deckelt bei 90 %.
**Soll:** Zyklus bis 100 %: `intArrayOf(0, 30, 60, 100)`. Die Logik `cycle.firstOrNull { it > lightLevel } ?: 0` ergibt dann 0→30→60→100→0. Der Licht-Slider im Popup steht bereits auf `0f..100f` — nur den Tasten-Zyklus ändern.
**Prüfen:** OSD/Popup zeigt nach 3× Licht-Tipp `100%`.

## QW2 — Sonde: aktive Frequenz grün markieren (Louis #2)

**Ist:** `ui/screens/inspection/InspectionScreen.kt`, Sonde-Popup (~Z. 820–831): alle Optionen als `TextButton` mit weißem Text, keine Markierung der aktiven Frequenz.
**Soll:** Die aktuell aktive Option grün und fett hervorheben, die übrigen weiß lassen. Aktive Bestimmung über die vorhandene RX-Anzeige `crawler.sondeFrequency` (String-Label aus derselben Quelle wie `SondeFrequency.name(...)`):
- Für jede Option `(label, f)`: `val active = SondeFrequency.name(f) == (crawler.sondeFrequency ?: SondeFrequency.name(SondeFrequency.OFF))`
- Aktiv → Textfarbe `OsdColorGreen` (gut lesbar auf dunklem Popup), `FontWeight.Bold`; sonst `Color.White`, normal.
- Kein neuer State, keine Logikänderung am Senden — nur Darstellung.
**Prüfen:** Nach Auswahl einer Frequenz ist genau diese im wieder geöffneten Popup grün.

## QW3 — Einstellungen: „Datum & Uhrzeit stellen" (Louis #7)

**Ist:** Das Inspektionsdatum wird bereits mit `LocalDate.now()` vorbelegt (`ProjectFormViewModel.kt` Z. 40–42) — es zeigt nur deshalb 01.01.2021, weil die **Geräte-Uhr** falsch steht. In den App-Einstellungen gibt es keinen Weg, das zu korrigieren.
**Soll:** In `ui/screens/settings/SettingsScreen.kt` einen Eintrag „Datum & Uhrzeit stellen" ergänzen, der die Android-Systemeinstellung öffnet:
```kotlin
context.startActivity(
    android.content.Intent(android.provider.Settings.ACTION_DATE_SETTINGS)
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
)
```
- Passendes Icon (z. B. `Icons.Default.Schedule` / `CalendarToday`), Stil wie die übrigen Settings-Einträge.
- Die App-seitige Systemuhr NICHT programmatisch setzen (braucht privilegierte Rechte) — nur den Sprung in die OS-Einstellung anbieten.
- Neue L10n-Keys de + en (z. B. `settings_datetime_title`, `settings_datetime_desc`).
**Prüfen:** Knopf öffnet die Android-Datum/Uhrzeit-Seite; nach Korrektur der Uhr steht das Datum in neuen Projekten automatisch richtig.

## QW4 — Info-Panel zuverlässig schließen (Louis #3)

**Ist:** `ui/screens/inspection/InspectionScreen.kt`, rechtes Info-Panel (Absolut/Strecke/Letzte Schäden, `AnimatedVisibility(visible = showControls)`, ~Z. 971). Öffnen/Schließen läuft über einen Tipp auf die freie Videofläche; die Panel-Buttons schlucken den Tipp, sodass ein Tipp **auf** das Panel es nicht schließt — der Nutzer findet nur den Pfeil oben links (der den Screen verlässt).
**Soll:** Eine eindeutige Schließen-Affordanz direkt am Panel — ein kleiner X-/Chevron-Button in der Panel-Kopfzeile, der `showControls = false` setzt (unteres Band unverändert lassen). Bestehendes Tipp-Toggle bleibt zusätzlich erhalten.
- Touch-Target ≥ 40 dp, Farbe/Transparenz wie die übrigen Panel-Elemente.
**Prüfen:** Panel öffnet per Videotipp, schließt zuverlässig per X am Panel — ohne den Inspektions-Screen zu verlassen.

## QW5 — Schadensart-Dropdown reparieren (Louis #1 + #6)

**Ist:** `ui/screens/inspection/DamageDialog.kt` (~Z. 307–337): `ExposedDropdownMenuBox`/`ExposedDropdownMenu` **innerhalb** eines Compose-`Dialog`. Das Menü-Popup verankert am Activity- statt am Dialog-Fenster → die Auswahl greift auf der ONE nicht, „Risse" lässt sich nicht ändern. (Höchste Priorität — blockiert die Kernerfassung.)
**Soll:** Die `ExposedDropdownMenu` durch ein dialog-festes Muster ersetzen:
- Ein read-only Feld/`OutlinedTextField` (oder klickbare `Surface`) zeigt `selectedType` mit Dropdown-Pfeil und öffnet bei Klick einen kleinen Auswahl-Dialog (`AlertDialog` mit Liste/Radio der `damageTypes`).
- Tipp auf einen Eintrag setzt `selectedType` und schließt die Auswahl. Bestehende Speicherlogik unverändert.
- Innerhalb der Auswahl `HideSystemBarsInDialog()` beibehalten.
- **Nicht anfassen:** die gleich aussehende `ExposedDropdownMenu` in `ProjectFormScreen.kt` (Material/Wetter) — die steht auf einem Vollbild-Screen, nicht im Dialog, und funktioniert.
**Prüfen:** Im Dialog „Schaden erfassen" lässt sich die Schadensart zuverlässig auf jeden Katalogwert ändern und wird korrekt gespeichert.

---

## Leitplanken

- **Direkt-Modus/Hardware unberührt:** `OneInternalHardwareService`, Serial/V4L2, `OneRemoteServer`, `AccessPointController` — keine Änderungen. Reines UI.
- **L10n:** alle neuen Strings über `S()`-Keys in `LocalizationManager.kt`, de + en, chirurgisch. **Den Localization-Generator NICHT ausführen.** Keine Hardcodes.
- **KRITIS:** UI-only. Einziger neuer Systemzugriff ist der Intent auf `ACTION_DATE_SETTINGS` (harmloser OS-Einstellungs-Sprung, keine neue gefährliche Permission, kein Datenfluss/Logging berührt).
- **Tests:** bestehende Tests dürfen nicht brechen. Für QW1 (Licht-Zyklus) und QW2 (aktive-Frequenz-Bestimmung) je einen kleinen Unit-Test ergänzen, wenn sinnvoll extrahierbar.
- Nach jedem QW: `.\gradlew assembleDebug test` grün, dann Commit `fix(louis-w1): …`.
- **Abschlussbericht** `RESULT_LOUIS_W1_QUICKWINS.md` (Repo-Root): was geändert (Datei:Zeile), plus **Geräte-Checkliste** je QW zum Abhaken beim nächsten ONE-Test.
- Adversariale Selbst-Review vor dem letzten Commit (QW5: greift die Auswahl im Dialog wirklich? QW4: kein Leak/kein doppeltes Toggle?).

## Bewusst außerhalb dieser Welle (nicht mitmachen)

- **Louis #5a (Foto aus Video):** bereits gefixt (Commit `35fd3f5`) — nur am Gerät gegenchecken.
- **Louis #8 (Durchmesser/Länge):** Code intakt (Felder eingebbar + im Report). Aus dem Code nicht reproduzierbar → erst Geräte-Repro, dann eigene Aufgabe.
- **Louis #9a + #5b (Video nach Pause abgeschnitten / nur Endzeit):** echter Bug am Aufnahme-Pfad (`LocalBitmapRecorder`-Pause/Zeitstempel) → eigene Welle mit Sorgfalt + Gerätetest.
- **Louis #9b (Report auf USB):** `UsbExportService` existiert; Louis' Fehlschlag ist „Alle Dateien"-Zugriff bzw. Stick-Format (FAT32/exFAT) → Handbuch/Bedienung, kein Code.
