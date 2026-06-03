# Feldtest-Feedback Jakob (28.05.2026) — Analyse & Lösungen

**Stand:** 2026-06-02 · **Tool:** drainq.one (ONE-Schiebekamera) · **Quelle:** „Jakob Feedback 0206.pdf"

## Wichtiger Kontext zum Setup

Die App läuft **direkt auf der ONE-Hardware** (RK3588, Android), nicht mehr als Tablet-Slave. Steuerung über serielle Schnittstelle `/dev/ttyS5`, Video über V4L2 `/dev/video0` (`OneInternalHardwareService`). Das ist entscheidend für mehrere Punkte: Die physischen Tasten am Gerät und der Kiosk-Charakter (kein Verlassen zur Android-Oberfläche) gehören mit zur App-Verantwortung.

Zwei Befunde betreffen mehrere Punkte gleichzeitig und werden unten als **Querschnittsthemen** behandelt:
- **A — Hardware-Tasten werden ignoriert** (betrifft #1, #3)
- **B — Kein Kiosk-/Vollbild-Modus** (betrifft #4, #5)

**Festgelegte Entscheidungen (Thomas, 2026-06-02):**
- **#8:** Schnellaufnahme ohne Projekt wird als eigener Modus aufgenommen (Variante A).
- **Hardtasten:** Die On-Screen-Softbuttons **sind** die Hardtasten-Belegung — Hardtaste und der positionsgleiche Softbutton lösen dieselbe Aktion aus (Ziel: Handschuh-Bedienung). Eine gemeinsame Aktionsliste ist die einzige Quelle für beide.

---

## Übersicht

| Nr | Punkt | Ursache (Kurz) | Lösung (Kurz) | Aufwand | Prio |
|---|---|---|---|---|---|
| 5 | Wischen → Android-Homescreen, Anlage hängt | Kein Immersive-/Kiosk-Mode; normale Launcher-Activity | Immersive-Sticky + Screen-Pinning/LockTask + HOME-Launcher | M | **Showstopper** |
| 6/8 | Keine Aufnahme/Foto ohne Projekt | `if (projectId == null) return` an allen Capture-Pfaden | **Schnellaufnahme-Modus** (Default-Bucket), später zuordenbar | M | Hoch |
| 2 | Sonde an/aus + Frequenz keine Funktion | Befehl wird gesendet (`sendBase`) — Verdacht: Hardware nicht verbunden + Bedienelement versteckt im Slide-Panel | Konnektivität verifizieren + Sonde-Bedienung sichtbar/dediziert | S–M | Hoch |
| 4 | Tastatur fährt nicht ein | Kein IME-Dismiss, kein sichtbarer „Fertig", kein System-Back | imeAction=Done + clearFocus + Tap-außerhalb + Dialog-Schließen | S | Hoch |
| 1 | Touch-Buttons nicht auf Ebene der Hardtasten | Querschnitt A: keine feste Softbutton-Leiste, Aktionen nur im Slide-Panel | Feste Softbutton-Leiste am Tastenraster = Hardtasten-Belegung | M | Mittel |
| 3 | „Neues Projekt"-Button sehr klein + Hardtaste ohne Funktion | Standard-FAB (56 dp); Querschnitt A | FAB vergrößern/Extended-FAB + Hardtaste mappen | S | Mittel |
| 7 | Kein Zurück Inspektion→Gallery; „Gallery" = Projektverzeichnis | InspectionScreen ohne Zurück-Affordanz; ProjectDetail ist Verzeichnis mit Tabs | Zurück-Button in Inspektion + Benennung/Struktur klären | S–M | Mittel |

---

## Querschnittsthema A — Hardware-Tasten werden ignoriert

**Ursache (belegt):** Das serielle Protokoll überträgt die physischen Tasten als `btn1..btn6` — sowohl im Sende-Frame (`OneFrameCodec.baseCommand`, Bytes 5–10) als auch in der **RX-Statusmeldung** `GROUP_STATUS` (Payload `[power, light, freq, btn1..btn6]`). Aber `OneInternalHardwareService.foldFrames()` liest aus `GROUP_STATUS` nur `payload[1]` (Licht) und `payload[2]` (Frequenz) aus — **`btn1..btn6` (payload[3..8]) werden verworfen.** Es gibt repo-weit **kein** `onKeyDown`/`dispatchKeyEvent` und keine Auswertung der Tasten-Bytes. Folge: Jede physische Taste am Gerät ist in der App wirkungslos.

**Leitlinie (festgelegt):** Hardtaste und positionsgleicher Softbutton lösen **dieselbe** Aktion aus. Es gibt **eine** Aktionsliste (z. B. Foto, Schaden, Aufnahme, Sonde, Licht, Meter-0), die zugleich (a) als feste On-Screen-Leiste am unteren Rand gerendert und (b) vom Hardtasten-Handler angesprochen wird. Damit ist Bedienung mit Handschuh über Touch ODER Hardtaste möglich.

**Lösung:**
1. **Gemeinsame Aktionsliste** definieren (Reihenfolge = Tastenraster). Single Source of Truth für Leiste + Hardtasten-Dispatch.
2. In `foldFrames` (Group `GROUP_STATUS`) die Tasten-Bytes `btn1..btn6` (payload[3..8]) auslesen und als Flanken (gedrückt/losgelassen) in einen Event-Flow legen.
3. Zentraler `HardwareButtonHandler`: Tastenindex → Eintrag der gemeinsamen Aktionsliste → gleiche `onClick`-Logik wie der Softbutton.
4. Feste Softbutton-Leiste im `InspectionScreen` am unteren Rand, Positionen fluchten mit den physischen Tasten (aktuell liegen die Aktionen nur im rechten Slide-In-Panel — sie werden in die Leiste gezogen bzw. dort gespiegelt).
5. **Einziger noch zu ermittelnder Punkt:** Welcher `btn`-Byte-Index entspricht welcher physischen Tastenposition. Einmalig am Gerät per Logcat (`OneInternalHW`) beim Tastendruck abgleichen — die Aktionen selbst stehen über die Leiste fest.

**Betroffen:** `network/internal/OneInternalHardwareService.kt`, `network/internal/OneFrameCodec.kt`, neuer `HardwareButtonHandler`, `InspectionScreen.kt` (feste Leiste).

---

## Querschnittsthema B — Kein Kiosk-/Vollbild-Modus

**Ursache (belegt):** `MainActivity` ruft nur `enableEdgeToEdge()` — **kein** Immersive-Sticky, **kein** `startLockTask()`, keine System-Bar-Steuerung (`WindowInsetsController`). Die Activity ist eine normale `LAUNCHER`-Activity. Dadurch holen Wischgesten die System-Leisten hervor und führen zum Android-Homescreen; das Gerät wirkt „hängengeblieben", weil die App den Vordergrund verliert und Hardware-/Kamera-Ressourcen nicht sauber re-initialisiert werden.

**Lösung:**
1. **Immersive-Sticky:** System-Bars dauerhaft ausblenden (`WindowInsetsControllerCompat.hide(systemBars)` + `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`), in `onResume` erneut setzen.
2. **Screen-Pinning/LockTask:** App als Device-Owner (per ADB provisioniert) → `startLockTask()` macht Home/Recents wirkungslos. Ohne Device-Owner zumindest manuelles Screen-Pinning aktivieren.
3. **HOME-Launcher (optional):** Activity zusätzlich als `CATEGORY_HOME` registrieren, damit die ONE direkt in die App bootet.
4. **Lebenszyklus:** Hardware-Init/-Teardown an `onResume`/`onPause` koppeln, damit nach versehentlichem Verlassen kein Hänger bleibt.

**Betroffen:** `MainActivity.kt`, `AndroidManifest.xml`.

> KRITIS-Bezug: Kiosk/LockTask ist auch eine **Härtungsmaßnahme** (verhindert Zugriff auf System/andere Apps am Feldgerät) — positiv für die KRITIS-Readiness, sollte aber dokumentiert werden (Geräte-Provisionierung, Device-Owner-Prozess).

---

## Detailanalyse

### #5 — Wischen lässt zum Homescreen, Anlage hängt — *Showstopper*
Siehe **Querschnitt B**. Höchste Priorität, weil es den Feldeinsatz blockiert (Gerät unbedienbar nach Verlassen).

### #6 / #8 — Keine Aufnahme/Foto ohne Projekt + „muss immer ein Projekt angelegt werden?"
**Ursache (belegt):** In `InspectionScreen.kt` brechen **alle** Erfassungsaktionen bei fehlendem Projekt ab: Foto (`if (projectId == null) return@OutlinedButton`, Z. 436), Schaden (Z. 467), Notiz (Z. 516), Aufnahme-Button `enabled = projectId != null && rtspUrl.isNotEmpty()` (Z. 534). Der Bottom-Nav-Tab „Inspektion" öffnet `InspectionScreen(navController)` **ohne** `projectId` → Live-Bild läuft, aber nichts lässt sich speichern. Das ist Absicht (alle Artefakte hängen an einem Projekt), aber für den Bediener nicht erkennbar.

**Lösung (festgelegt — Variante A „Schnellaufnahme"):**
- Aufnahme/Foto ohne Projekt erlaubt. Ablage in ein automatisch angelegtes Default-/Tagesprojekt („Schnellaufnahme JJJJ-MM-TT"), später einem echten Projekt zuordenbar.
- Capture-Buttons sind ohne Projekt aktiv (keine stummen `return` mehr); beim ersten Auslösen wird der Default-Bucket angelegt.
- Datenintegrität: jede Aufnahme bekommt sofort einen Projektbezug (Default-Bucket), nichts liegt „lose" — wichtig für lückenlose Doku.
- UI: Schnellaufnahme als sichtbarer, benannter Modus (z. B. Badge „Schnellaufnahme") statt namenlosem „kein Projekt".

**Betroffen:** `InspectionScreen.kt` (Guards entfernen + Default-Bucket-Trigger), `ProjectRepository` (Default-/Tagesprojekt + Zuordnungsfunktion), `NavGraph.kt`.

### #2 — Sonde an/aus + Ortungsfrequenz keine Funktion
**Ursache:** Der Befehl wird korrekt erzeugt und gesendet — `sendFrequency()` setzt `curFreq` und `curPower` (0=aus, sonst an) und schreibt den vollständigen Base-Frame (`OneFrameCodec.baseCommand`, Sonde-Power=Byte 2, Frequenz=Byte 4). Wenn nichts passiert, kommen zwei Ursachen in Frage:
1. **Hardware nicht verbunden:** Bei fehlendem RW-Zugriff auf `/dev/ttyS5` (chmod 666 / Root) sendet `sendBase` ins Leere (`addLog("TX failed")`). Im Smoke-Test-Setup verifiziert, im Feldtest evtl. nicht gegeben.
2. **Bedienelement versteckt:** Die Sonde-Steuerung liegt **nur** im Slide-In-Panel (Video antippen → Panel → scrollen bis „Sonde"). Es ist ein einzelner Zyklus-Button (Off→512→640→33 kHz). Der Tester fragt explizit „Wechsel Ortungsfrequenz?" → er hat das Bedienelement nicht gefunden bzw. erwartet getrenntes An/Aus + Frequenzwahl.

**Lösung:**
1. Konnektivität am Gerät verifizieren (Logcat `OneInternalHW`: `probeEndpoints` serial=true?, `TX failed`?). Permission-Strategie Phase P7 prüfen.
2. Sonde-Bedienung sichtbarer machen: dedizierter An/Aus-Schalter + Frequenz-Auswahl (statt eines kombinierten Zyklus-Buttons), und als physische Hardtaste mappen (Querschnitt A).
3. Status zurückspiegeln: `GROUP_STATUS` liefert die reale Frequenz zurück (`freqName`) — bestätigt visuell, ob die Hardware den Befehl angenommen hat.

**Betroffen:** `OneInternalHardwareService.kt` (Diagnose), `InspectionScreen.kt` (Sonde-UI).

### #4 — Tastatur fährt nicht wieder ein
**Ursache:** `windowSoftInputMode=adjustResize` ist gesetzt, aber es gibt keinen expliziten IME-Dismiss: Textfelder in Dialogen (`DamageDialog`, `NoteDialog`, `ProjectFormScreen`) haben keinen erkennbaren „Fertig"/Schließen-Weg, kein Tap-außerhalb-schließt-Tastatur, und im Vollbild ohne sichtbaren System-Back (siehe #5) bleibt der Bediener mit offener Tastatur hängen.

**Lösung:**
1. Textfelder: `keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)` + `keyboardActions` mit `focusManager.clearFocus()` / `keyboardController?.hide()`.
2. Auf Dialog-/Screen-Ebene Tap-außerhalb → `clearFocus()`.
3. Beim Bestätigen/Abbrechen eines Dialogs IME aktiv schließen.
4. In Verbindung mit Immersive-Sticky (#5) verschwindet auch das „Wisch-zum-Schließen führt zum Homescreen".

**Betroffen:** `inspection/DamageDialog.kt`, `inspection/NoteDialog.kt`, `projects/ProjectFormScreen.kt`.

### #1 — Touch-Buttons nicht auf gleicher Ebene wie Hardtasten
**Ursache:** Querschnitt A. Konkret: Es gibt aktuell **keine** feste Softbutton-Leiste — die Bedienelemente sitzen im rechten Slide-In-Panel (`InspectionScreen`, Layer 4). Damit fluchtet nichts mit dem physischen Tastenraster unter dem Display. (Die Icon-Reihe auf Foto #1 ist die Legacy-Kamera-OSD-Leiste der Original-Firmware, ins Video eingebrannt — kein DrainQ-Element.)

**Lösung:** Feste On-Screen-Softbutton-Leiste am unteren Rand, Positionen fluchten mit den physischen Tasten; Touch und Hardtaste teilen sich die gemeinsame Aktionsliste (Querschnitt A). Touch = Hardtaste, 1:1.

**Betroffen:** `InspectionScreen.kt` (feste Leiste), `HardwareButtonHandler`.

### #3 — „Neues Projekt"-Button sehr klein + Hardtaste ohne Funktion
**Ursache:** `ProjectsScreen` nutzt einen Standard-`FloatingActionButton` (56 dp) — auf dem großen, ggf. mit Handschuh bedienten Feld-Display zu klein. Zusätzlich Querschnitt A (Hardtaste darunter ohne Funktion). Hinweis: auf dem Home-Screen ist „Neues Projekt" eine `QuickActionCard` (gut sichtbar) — die Beanstandung betrifft die Projektliste.

**Lösung:** `ExtendedFloatingActionButton` mit Label „Neues Projekt" oder vergrößerter FAB (≥ 72 dp) gemäß Design-Guidelines (Mindest-Touch). Optional Hardtaste „Neu" mappen.

**Betroffen:** `projects/ProjectsScreen.kt`.

### #7 — Kein Zurück bei Inspektion → Gallery; „Gallery" ist Projektverzeichnis
**Ursache:** `InspectionScreen` hat **keine** TopAppBar/Zurück-Affordanz — aus der Inspektion führt kein direkter Weg zurück zur Herkunfts-View. Das, was der Tester „Gallery" nennt, ist `ProjectDetailScreen`: ein Projektverzeichnis mit Tabs (Fotos/Schäden/Videos/Notizen), erreichbar nur über Projektliste → Projekt. `ProjectDetailScreen` hat zwar einen Zurück-Pfeil (Z. 345), die Inspektion selbst aber nicht.

**Lösung:**
1. In `InspectionScreen` eine dezente Zurück-Affordanz (Top-Left-Icon im Cinema-Mode), bei Aufruf via `inspection/{projectId}` zurück zur `ProjectDetailScreen`.
2. Benennung klären: Wenn der Bediener eine „Galerie" der aufgenommenen Medien erwartet, den Foto-/Video-Zugang prominent benennen (z. B. Tab „Galerie" innerhalb des Projekts) — bewusst vom „Projektverzeichnis" trennen.

**Betroffen:** `InspectionScreen.kt`, ggf. `NavGraph.kt`, Benennung in i18n.

---

## Empfohlene Reihenfolge

1. **#5 + #4** (Kiosk/Immersive + Tastatur) — gemeinsam, beheben den Feld-Showstopper.
2. **#8/#6** (Workflow-Entscheidung Schnellaufnahme) — Grundsatz, beeinflusst weitere UI.
3. **Querschnitt A** (Hardtasten) — schaltet #1 und #3 frei; vorab Tasten-Mapping am Gerät ermitteln.
4. **#2** (Sonde sichtbar + verifiziert), **#7** (Zurück/Benennung), **#3** (FAB) — Politur.

## Entscheidungen (festgelegt, 2026-06-02)
- **#8:** Schnellaufnahme ohne Projekt (Variante A) wird umgesetzt — eigener, benannter Modus mit Default-Bucket.
- **Hardtasten:** On-Screen-Softbuttons = Hardtasten-Belegung (positionsgleich, gemeinsame Aktionsliste). Einzig der `btn`-Byte-Index ↔ physische Position muss einmalig am Gerät per Logcat verifiziert werden.

## Nächster Schritt
Umsetzung in Wellen gemäß „Empfohlene Reihenfolge". Git-Schreibvorgänge laufen lokal (Cowork-Mount blockiert sie); Code-Änderungen kann ich hier vorbereiten. Auf Wunsch lege ich die Punkte als priorisierte Tickets/Wellen-Specs an.
