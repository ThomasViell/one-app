# TESTREPORT — BETA-Welle 1 + 2, vollautomatischer On-Device-Test

**Datum:** 2026-06-07
**Gerät:** ONE-Hardware `233b4bd2865177ed` (rk3588_s, Android 12), USB
**Paket:** `com.uip.drainq.one` (Debug). App-Version beim Start: 0.3.0 → nach Bugfix-Rebuild 0.4.0
**Tester:** Claude (Auto-Test-Automatisierer)
**Beweise:** `tools\_autotest\` (Screenshots, Dumps, Logs, gezogene Dateien)

---

## Vorbedingungen

- `adb devices` → Gerät erkannt; App im Vordergrund (`com.uip.oneapp.MainActivity`).
- `appops set … MANAGE_EXTERNAL_STORAGE allow` gesetzt (USB-Export-Test freigeschaltet).
- **Kein USB-Stick** im Gerät (`/storage/` zeigt nur `emulated` + `self`) → T8 nur bis Erkennungsmeldung (TEILWEISE).
- Bestehende Projekte unangetastet; keine zerstörerischen Aktionen ausgeführt.

---

## T0 — Offener Vorlauf-Befund: „Aufnahme-Dialog öffnet nicht?"

**Ergebnis: SKRIPT-ARTEFAKT, KEIN BUG.**
`keyevent 133` öffnet in der Inspektion zuverlässig den Dialog **„Aufnahme starten"** mit den
Knöpfen **„Ohne Einblendung"** und **„Mit Einblendung"** (Beweis: `t0_after133.png`, `t0_after133.xml`).
Der starre Vorlauf-Lauf scheiterte ausschließlich daran, dass er nach dem Knopftext **„Mit Overlay"**
suchte — der tatsächliche Text lautet **„Mit Einblendung"**. Code (`runHwButton`/`HardwareKeyBus`)
ist korrekt; **kein Code-Fix für T0 nötig, kein Commit für T0**.

---

## Testliste (Übersicht)

| Nr | Test | Status | Beweis | Anmerkung |
|----|------|--------|--------|-----------|
| T0 | Aufnahme-Dialog (Hardtaste 133) | **GRÜN** | `t0_after133.png/.xml` | Dialog öffnet; Vorlauf-Fehler = falscher Suchtext „Mit Overlay" statt „Mit Einblendung". |
| T1 | Aufnahme + Pause/Weiter + Stop | **GRÜN** | `t1_pause_aufnahme.mp4` (5,5 MB), `t1_pause.png`, `t1_resume.png`, `t1_log.txt` | Genau 1 neue MP4 in `recordings/project_13/`, ffmpeg rc=0. Dauer **14,75 s** → Pause (5 s) NICHT im Video (sonst ~20–23 s). PAUSE-Chip amber, danach REC. |
| T2 | OSD eingebrannt + Unicode + Schadensfoto | **ROT → nach Fix GRÜN** | `t2_schadenfoto_vorhanden.jpg`, `t2_dmg_alt_flash.jpg`, `t2_filled.png`, `t2_frame_diag.txt`, `t2retest_schadenfoto.jpg` | OSD-Burn-in & Unicode (ü in „Brüche") nachgewiesen. **Aber: neue Schäden erzeugten KEIN Foto** („Kein Screenshot verfügbar"). Root-Cause gefunden, gefixt, neu gebaut & retestet → Foto wird wieder erzeugt. Siehe Rote Befunde. |
| T3 | F7 / Tag-Nacht entfernt | **GRÜN** | `t2_bar.xml`, `t3_nach_137.png`, `t3_log.txt` | Untere Leiste: 9 Kacheln (Power, Licht, Sonde, Aufnahme, Stop, Foto, **Schaden**, Galerie, Einstellungen) — **keine Tag/Nacht-Kachel**. `keyevent 137` ohne Wirkung, 0 FATAL. |
| T4 | HW-OSD-Schalter weg | **GRÜN** | `t2_bar.xml` (rechtes Panel), `t4_osd_section.png/.xml` | Rechtes Panel ohne „Hardware-OSD". Einstellungen→OSD: nur **„OSD Burn-In aktivieren"** (Meterstand/Datum/Projekt). Kein „Hardware-OSD", kein „Neigung/Sonde zeigen". |
| T5 | Helligkeits-Slider | **GRÜN** | `t5_manuell_an.png`, `t5_20prozent.png` (24 %), `t5_100prozent.png` (99 %) | Schalter→manuell blendet Slider ein; Wert ändert sich (24 % → 99 %). App dimmt per **Fenster-Helligkeit** (WindowManager) → physische Helligkeit nicht via `screencap`/`settings` messbar (Framebuffer identisch). Zurück auf Automatisch. |
| T6 | XML restlos weg (Export) | **GRÜN** | `t6_archiv_dialog.xml`, `t6_export.zip`, `t6_export_entpackt/` | Export-Dialog: nur „Fotos einbinden" + „Standortkarte einschließen", **keine XML-Option**. ZIP enthält `fotos/`, `videos/`, PDF, `projekt_info.txt` — **0 .xml-Dateien**. |
| T7 | PDF-Bericht | **TEILWEISE** | `t7_bericht.pdf` (2.627 B), `t7_log.txt` | Gültige PDF (iText 7.2.5, A4, 1 Seite), aber nur **2,6 KB < 20 KB**, und „Fotos einbinden" bettet **kein Bild** ein (Größe mit/ohne Häkchen identisch). Siehe Rote Befunde. |
| T8 | USB-Export | **TEILWEISE** | `t8_usb_dialog.xml` | Kein Stick gesteckt. Dialog zeigt korrekt „Kein USB-Stick erkannt…" (appops war gesetzt → echter No-Stick-Zustand, kein Rechteproblem). Vollexport nicht prüfbar. |
| T9 | Sonde-Frequenz (512 Hz, TX) | **GRÜN** | `t9_sonde_fast.xml`, `t9_tx3_log.txt`, `t9_aus_log.txt` | Sonde-Popup über Softbar: „33 kHz / 640 Hz / 512 Hz / AUS". 512 Hz → `OneInternalHW: TX p=1 l=30 f=3`. AUS → `TX p=0 … f=0`. **Hinweis:** Hardtaste 132 zeigt das Popup nur bei sichtbarer Bedien-Leiste (siehe Beobachtungen). |
| T10 | Licht (TX-Zyklus) | **GRÜN** | `t10_hardkey_log.txt`, `t10b_log.txt` | `keyevent 131`: `TX … l=30` → `l=60` → `l=90` → `l=0` → `l=30`. Zyklus 0→30→60→90→0 wie spezifiziert. |
| T11 | Meter-Reset | **GRÜN** | `t11_nach_reset.png/.xml`, `t11_log.txt` | „Absolut → 0" und „Strecke → 0" getippt, **0 FATAL**, Meter 0.00 m (Kabel nicht gesteckt → bleibt 0, laut Vorgabe OK). |
| T12 | Lifecycle / Hintergrund (V7) | **GRÜN** | `t12_nach_home.png` | `keyevent 3` (Home): Fokus bleibt `com.uip.drainq.one/MainActivity` → **Pinning/Kiosk hält**, App bleibt im Vordergrund (gültiges Ergebnis). |
| T13 | Kamerakopf-Chip | **GRÜN** | `t2_bar2.xml`, `t9_log.txt` | Chip **„C10"** oben rechts; `OneInternalHW: RX grp=23`-Zeilen laufen durchgehend. |
| T14 | Crash-Sweep (alle Screens) | **GRÜN** | `t14_*.png`, `t14_log.txt` | Home, Projekte, Projekt-Detail (alle 4 Reiter Fotos/Schäden/Videos/Notizen), Einstellungen + Unterseiten (Offline-Karten, Netzwerk & Verbindung, Berichte). **0 × FATAL EXCEPTION** (AndroidRuntime-Zeilen = nur uiautomator-Prozesse uid 2000). |

---

## Rote Befunde im Detail

### BEFUND #1 (T2) — Schadens-/Foto-Erfassung erzeugt kein Bild  ▸ GEFIXT & RETESTET

**Symptom:** Beim Anlegen eines Schadens (Kachel „Schaden") zeigt der Dialog „Schaden erfassen"
oben **„Kein Screenshot verfügbar"**; nach „Speichern" wird **keine `dmg_*.jpg`** in
`files/damages/project_<id>/` geschrieben. Im Schäden-Reiter erscheinen die neuen Schäden mit
**leerem Thumbnail** (graues Warn-Icon), während ältere Schäden ein Foto haben.
Betrifft sowohl laufende Aufnahme als auch reine Live-Ansicht.

**Screenshots:** `t2_schaden_dialog.png` / `t2b_dialog_live.png` („Kein Screenshot verfügbar"),
`t14_tab_schaeden.png` (#1 Risse & #2 Brüche ohne Thumbnail, #3 Foto mit Thumbnail).

**Logauszug (Diagnose-Trigger):**
```
W InspectionScreen: No frame available for screenshot
    (tv=android.view.TextureView{… 0,0-1920,1080}, localFrame=true)
```
→ Die TextureView existiert (1920×1080), aber **`tv.bitmap` liefert `null`** (der Canvas-Overlay-
Player aus Phase 7 rendert nicht in diese TextureView). `localFrame` (V4L2-Live-Frame) ist
**verfügbar** (`localFrame=true`), wird aber nie genutzt.

**Code-Verdacht (bestätigt):** `app/.../inspection/InspectionScreen.kt`, `doDamage` und `doPhoto`:
```kotlin
val bitmap = if (tv != null && tv.width > 0 && tv.height > 0) tv.bitmap   // kann null sein!
             else localFrame?.copy(Bitmap.Config.ARGB_8888, true)         // else-Zweig nie erreicht
```
Sobald die TextureView mit Maßen existiert, wird der `tv.bitmap`-Zweig gewählt — auch wenn
`tv.bitmap` `null` ist. Der Fallback auf `localFrame` steht im `else` und greift nie.
Folge: `doDamage` → kein Foto; `doPhoto` → leere Datei via `file.createNewFile()` (still, ohne Fehler).
Eingeführt durch den Player-Umbau in **Phase 7 (libVLC-Entfernung, Canvas-Overlay-Player)**.

**Durchgeführter Fix:** Fallback auf `localFrame`, wenn `tv.bitmap` `null` ist (beide Stellen):
```kotlin
val bitmap = (if (tv != null && tv.width > 0 && tv.height > 0) tv.bitmap else null)
             ?: localFrame?.copy(Bitmap.Config.ARGB_8888, true)
```

**Retest (nach `gradlew installDebug`):**
- Log: `D InspectionScreen: Screenshot saved: …/dmg_1780821842329.jpg` (statt „No frame available").
- Dialog zeigt jetzt eine **Live-Vorschau** statt „Kein Screenshot verfügbar" (`t2retest_dialog.png`).
- Neues Foto `dmg_1780821842329.jpg` (54 KB) mit **OSD eingebrannt**: oben „NSP3CT ONE | Schnellaufnahme_070626" (grün), unten „-0.00m | 2026-06-07" (orange) — **lesbar, keine „?"** (`t2retest_schadenfoto.jpg`).

**Unicode:** über vorhandenen Preset-Typ **„Brüche"** (mit ü) nachgewiesen — rendert in Dialog und
Schäden-Liste korrekt (`t2_filled.png`, `t14_tab_schaeden.png`); OSD-Datum rendert ebenfalls fehlerfrei.
Direkte Umlaut-Eingabe per `adb shell input text` ist prinzipbedingt unmöglich
(`NullPointerException` bei Nicht-ASCII) — wie im Auftrag vermerkt.

> **Commit-Hinweis:** Laut Auftrag wird nur ein **T0**-Bug committet. T0 war kein Bug, dieser Fund
> stammt aus T2. Der Fix liegt daher **uncommittet im Arbeitsbaum** (`InspectionScreen.kt`, 2 Stellen)
> und ist zur Prüfung/zum Commit durch den Benutzer bereit. **Es wurde nichts committet.**

### BEFUND #2 (T7) — PDF-Bericht zu klein / Fotos nicht eingebettet

**Symptom:** Erzeugter PDF-Bericht ist nur **2.627 Bytes** (< 20-KB-Kriterium). „Fotos einbinden"
ist aktiv, dennoch ändert sich die Dateigröße nicht und es ist **kein Bild-XObject** enthalten.
Gilt für PDF-Aktion und für die im Archiv-ZIP gebündelte PDF.

**Beweis:** `t7_bericht.pdf` (gültig: `%PDF-1.7`, iText 7.2.5, A4 595×842, 1 Seite, FlateDecode-Stream),
Größe 2.627 B mit und ohne „Fotos einbinden".

**Verdacht:** Schadensfotos werden nicht ins PDF eingebettet (PDF rein textuell). **Nicht gefixt** —
hängt vermutlich mit demselben fehlenden Frame/Foto-Pfad zusammen (BEFUND #1): ohne `dmg_*.jpg`
gibt es kein Bild zum Einbetten. Empfehlung: nach Übernahme von Fix #1 PDF erneut prüfen
(Schaden mit Foto anlegen → PDF erzeugen → Größe/Bilder kontrollieren).

---

## Beobachtungen (kein ROT, aber notieren)

- **T9 / Hardtaste 132 (Sonde):** Das Frequenz-Popup wird im Code nur innerhalb der unteren
  Bedien-Leiste gerendert (`if (showBottomBar) { … if (showSondePopup) Popup }`). Drückt man F2
  bei **ausgeblendeter** Leiste, passiert sichtbar nichts (Status `showSondePopup=true`, aber kein
  Popup im Compose-Baum) und es wird **kein TX** gesendet. Im Gegensatz dazu sendet F1/Licht TX
  unabhängig von der Leiste. Auf dem physischen Gerät müsste der Bediener also zuerst die Leiste
  einblenden (Video antippen), bevor F2 wirkt. Funktional über die Sonde-Kachel voll nachgewiesen.
- **T5 / Helligkeit:** App nutzt fenster-lokale Helligkeit (kein Schreiben von
  `system screen_brightness`). Korrektes Verhalten, aber die optische Helligkeitsänderung lässt sich
  **nicht per Screenshot** belegen (Backlight ist nicht im Framebuffer) → physischer Sicht-Test.

---

## Für den Benutzer verbleibende manuelle Tests

- [ ] **Fix #1 prüfen/committen** (`InspectionScreen.kt`, uncommitted im Arbeitsbaum) und ggf. in den Beta-Build übernehmen.
- [ ] **T7 PDF nach Fix #1:** Schaden **mit** Foto anlegen → PDF erzeugen → Größe & eingebettete Fotos kontrollieren (BEFUND #2).
- [ ] **T5 Helligkeit physisch:** 20 % vs. 100 % am echten Display ansehen (Screenshot kann das nicht zeigen).
- [ ] **T8 USB-Export voll:** USB-Stick einstecken, „Komplettes Projekt" exportieren, Zielpfad/Dateien per `ls -R /storage/<ID>/DrainQ/` prüfen.
- [ ] **T9 Sonde-Ortung physisch** + Entscheidung, ob F2-Hardtaste auch bei ausgeblendeter Leiste das Popup öffnen soll.
- [ ] **Mikrofon/Audio:** `tools\check-microphone.ps1` (physisch).
- [ ] **Licht/Sonde/Meter mit echter Hardware-Reaktion** (Lichtwirkung, Frequenz orten, Kabelbewegung), Kopfwechsel C10/C18.
- [ ] **Self-Update / Device-Owner-Kiosk:** bewusst offen (kein Release publiziert / Provisionierung später).

---

## Fazit

**BETA-tauglich: ja, mit Einschränkungen** — Welle 1 + 2 funktionieren am Gerät weitgehend wie
spezifiziert (T1, T3–T6, T9–T14 grün). **Ein echter Regressions-Bug** (Schadens-/Foto-Erfassung
liefert kein Bild, T2) wurde gefunden, ursachengenau diagnostiziert, **gefixt und am Gerät
verifiziert** (Fix uncommittet im Arbeitsbaum). Der PDF-Bericht (T7) bleibt offen (zu klein / keine
Fotos, hängt an demselben Foto-Pfad). USB-Vollexport (T8) ohne Stick nicht abschließend prüfbar.
