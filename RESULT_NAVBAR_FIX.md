# Ergebnis: Android-Systemleiste bei Aufnahmestart im Kiosk beseitigt

**Branch:** `feature/beta-wave-1` · **Gerät:** `233b4bd2865177ed` (RK3588_s, Android 12, userdebug)
**Datum:** 2026-06-07 · **App:** `com.uip.drainq.one` (v0.4.1 / 401)

---

## Kurzfassung

Die „Navigationsleiste", die nach dem Aufnahmestart erschien und blieb, ist auf der ONE die
**launcher3-System-Taskbar** (`ty=NAVIGATION_BAR_PANEL`, Insets-Typ `ITYPE_EXTRA_NAVIGATION_BAR`,
`com.android.launcher3`). Auslöser ist **jedes separate Fenster** (Compose-`AlertDialog`/`Popup`):
der Fenster-Übergang „unstashed" die Taskbar, und danach lässt sie sich vom App-Fenster **nicht**
mehr einziehen. Fix: Der Aufnahme-Modus-Dialog ist jetzt ein **In-Window-Overlay** (kein eigenes
Fenster) → kein Fensterwechsel → Taskbar bleibt eingezogen. Alle Abnahmekriterien am Gerät grün,
**auch ohne** `qemu.hw.mainkeys=1`.

---

## Ursache (ursachengenau, mit Beleg)

1. **Es ist nicht die klassische Nav-Bar.** `dumpsys window windows` zeigt als unteres Element die
   **Taskbar** von `com.android.launcher3`:
   ```
   Window{... u0 Taskbar}: mAttrs={(0,0)(fillx87) gr=BOTTOM ... ty=NAVIGATION_BAR_PANEL ...}
     insetsTypes=ITYPE_EXTRA_NAVIGATION_BAR ...
   ```
   Beleg: `tools/_navbarfix/dumpsys_window_recording.txt`, `dumpsys_after_swipe.txt`.

2. **Die App fordert die Leiste korrekt als unsichtbar an** — auch im Bug-Zustand:
   ```
   MainActivity: Requested visibilities: ... ITYPE_EXTRA_NAVIGATION_BAR: invisible
   ```
   Trotzdem rendert launcher3 die Taskbar. ⇒ `WindowInsetsControllerCompat.hide(systemBars())`
   ist ein **No-Op** (bereits invisible angefordert). Darum half weder der Insets-Listener noch der
   1-s-Backstop-Wächter (beide prüfen `isVisible(systemBars())`, das die Taskbar gar nicht erfasst).

3. **Trigger ist das separate Dialog-Fenster, nicht die Aufnahme selbst.** Isolationstest:
   Aufnahme-Dialog nur **geöffnet und ohne Aufnahme wieder verworfen** (Tap außerhalb) →
   Taskbar erscheint und bleibt. Beleg: `tools/_navbarfix/iso_dialog_btm.png` (Dialog offen, keine
   Taskbar) → `iso_dismissed_btm.png` (nach Verwerfen: graue Taskbar). Der Aufnahme-`AlertDialog`
   und der Recorder (`LocalBitmapRecorder` bzw. `FfmpegRtspRecorder`) erzeugen sonst kein Fenster/
   keinen Foreground-Service.

**Datei:Zeile (vorher):** `app/src/main/java/com/uip/oneapp/ui/screens/inspection/InspectionScreen.kt`,
`AlertDialog`-Block des Aufnahme-Modus-Dialogs (ehem. ~Z. 1468–1571).

> Hinweis Hardware-Pfad: Auf diesem Gerät liefert der HardwareService `VideoSource.LocalBitmap`
> (V4L2), `rtspUrl` ist leer → Aufnahme läuft über `LocalBitmapRecorder`. Der Defekt ist aber
> Recorder-unabhängig, weil er rein vom Dialog-Fenster kommt.

---

## Fix (minimal, Ursache adressiert)

**`InspectionScreen.kt`** — Aufnahme-Modus-Auswahl von `AlertDialog` (separates Fenster) auf ein
**In-Window-Overlay** umgestellt: ein `Box`(Scrim) + `Card` innerhalb des bestehenden
cinema-mode-Root-`Box` derselben Activity. Die Aufnahme-Logik („Ohne/Mit Einblendung", RTSP- vs.
Lokal-Pfad, OSD-Burn-in) ist **unverändert** übernommen. Tap auf den Scrim schließt, Tap auf die
Karte wird verschluckt.

**`MainActivity.kt`** — Der vorhandene Lebenszyklus-Backstop bleibt als günstige Absicherung für
*fenster­kontrollierbare* Fälle erhalten; sein Kommentar dokumentiert jetzt ehrlich die
launcher3-Taskbar-Grenze (er kann die Taskbar nicht einziehen — der Overlay-Fix verhindert das
Problem an der Wurzel).

---

## Abnahme (am Gerät verifiziert, `qemu.hw.mainkeys=0`, Nav-Bar systemweit AN)

| # | Kriterium | Status | Beleg (`tools/_navbarfix/`) |
|---|-----------|--------|------------------------------|
| 1 | Aufnahme **Ohne Einblendung** → Leiste bleibt weg | ✅ | `fix_c_t0_btm.png`, `fix_c_t3.png` |
| 2 | **Mit Einblendung** + Pause/Weiter + Stop → bleibt weg | ✅ | `fix_mit_run_btm.png`, `fix_pause.png`, `fix_resume.png` |
| 3 | Nach Aufnahme-Stop → bleibt weg | ✅ | `fix_afterstop_clean.png` |
| 4 | Hochwischen zeigt Leiste nur transient, zieht sich ein | ✅ | `fix_d_swipe_t1_btm.png`, `fix_d_swipe_t5_btm.png` |
| 5 | Funktioniert **ohne** `qemu.hw.mainkeys=1` | ✅ | gesamte Reihe `fix_*` lief mit Prop=0 |
| 6 | `./gradlew test` grün | ✅ | BUILD SUCCESSFUL (testDebug/Release) |

**Vorher (Bug, Prop=0):** `noprop_c_t0/t1/t3.png` + `timeline_btm.png` (Zeilen 3–5 graue Taskbar
direkt nach „Ohne Einblendung", ohne jedes Wischen). **Nachher:** `fix_*` (Video bis zur Unterkante).

Zusätzlich Final-Check mit wiederhergestelltem `qemu.hw.mainkeys=1`: `final_prop1_rec.png`
(Overlay + Aufnahme funktionieren, keine Leiste).

---

## Empfehlung zur Golden-Image-Prop `qemu.hw.mainkeys=1`

**Empfehlung: im Golden-Image BELASSEN** (Wert auf dem Gerät nach dem Test wieder auf `=1` gesetzt).

Begründung:
- Der App-Fix macht den **Aufnahme-Flow** auch ohne die Prop sauber (Kriterium 5 erfüllt) — die
  Prop ist für diesen konkreten Bug **nicht mehr nötig**.
- Aber **dieselbe Ursache gilt für alle weiteren separaten Fenster** der App (`DamageDialog`,
  `NoteDialog`, Power/Beenden-`AlertDialog`, `ImageAnnotationDialog`, Licht-/Sonde-`Popup`). Bis die
  ebenfalls auf In-Window-Overlays umgestellt sind, holt jeder dieser Dialoge die launcher3-Taskbar
  hoch. `qemu.hw.mainkeys=1` entfernt die Taskbar **systemweit zuverlässig** und ist damit die
  robuste Defense-in-Depth fürs Feldgerät.
- Folge-Empfehlung (separates Ticket): die übrigen Dialoge analog auf In-Window-Overlays umstellen;
  danach kann die Prop optional fallen.

---

## Geänderte Dateien
- `app/src/main/java/com/uip/oneapp/ui/screens/inspection/InspectionScreen.kt` (Overlay statt AlertDialog)
- `app/src/main/java/com/uip/oneapp/MainActivity.kt` (Backstop-Kommentar präzisiert)

Diagnose-/Beleg-Artefakte: `tools/_navbarfix/`.
