# Ergebnis: Android-Systemleiste (launcher3-Taskbar) im Kiosk endgültig beseitigt

**Branch:** `feature/beta-wave-1` · **Gerät:** `233b4bd2865177ed` (RK3588_s, Android 12, userdebug)
**Datum:** 2026-06-07 · **App:** `com.uip.drainq.one`

---

## Kurzfassung

Die „Navigationsleiste", die im Kiosk auftauchte und blieb, ist auf der ONE die
**launcher3-System-Taskbar der Gesten-Navigation** (`ty=NAVIGATION_BAR_PANEL`, Insets-Typ
`ITYPE_EXTRA_NAVIGATION_BAR`, `com.android.launcher3`, `navigation_mode=2`). Sie wird von **jedem
separaten Fenster** „unstashed" — Compose-`Dialog`/`AlertDialog`/`Popup`, dem `ExposedDropdown`
**und der Soft-Tastatur (IME)** — und lässt sich danach vom App-Fenster **nicht** mehr einziehen.

**Eigentliche, vollständige Lösung:** Im Kiosk den System-Navigationsmodus auf **3-Button**
(`navigation_mode=0`) zwingen — in diesem Modus existiert die Gesten-Taskbar gar nicht; die
3-Button-Leiste selbst ist auf der ONE bereits über `qemu.hw.mainkeys=1` /
`persist.sys.navigationbar.enable=false` unterdrückt. Da `navigation_mode` **nicht reboot-persistent**
ist (OEM-Default = 2), setzt `MainActivity` es bei jedem Start/Kiosk-Wechsel und zieht es per
Fokus-/Backstop-Wächter nach (SystemUI setzt es im Boot kurz zurück). Benötigt `WRITE_SECURE_SETTINGS`
(per `pm grant` in der Provisionierung).

Am Gerät verifiziert: Aufnahme (Ohne/Mit Einblendung, Pause/Stop), Foto, **Schaden-Dialog mit
Tastatureingabe**, Projekt­ansicht — **kein Streifen mehr**, auch **ohne** Verlass auf `qemu.hw.mainkeys`.

---

## Ursachenanalyse (ursachengenau, mit Belegen)

1. **Es ist die launcher3-Gesten-Taskbar, nicht die klassische Nav-Bar.** `dumpsys window windows`:
   ```
   Window{... u0 Taskbar}: mAttrs={(0,0)(fillx87) gr=BOTTOM ... ty=NAVIGATION_BAR_PANEL ...}
     insetsTypes=ITYPE_EXTRA_NAVIGATION_BAR ...   package=com.android.launcher3
   ```
   `settings get secure navigation_mode` → `2` (Gesten-Navigation).
   Belege: `tools/_navbarfix/dumpsys_window_recording.txt`, `dumpsys_after_swipe.txt`.

2. **Die App kann sie per Insets NICHT steuern.** Auch im Bug-Zustand fordert das App-Fenster sie als
   unsichtbar an:
   ```
   MainActivity: Requested visibilities: ... ITYPE_EXTRA_NAVIGATION_BAR: invisible
   ```
   launcher3 zeigt sie trotzdem. ⇒ `WindowInsetsController.hide(systemBars())` ist ein **No-Op**;
   getestet wirkungslos: der 1-s-Backstop (Befund: `isVisible(systemBars())` meldet die Taskbar
   gar nicht → `visible=false`), `onWindowFocusChanged`, ein `show→hide`-Toggle und sogar die
   Legacy-`SYSTEM_UI_FLAG_IMMERSIVE_STICKY`-Flags.

3. **Auslöser ist jedes separate Fenster.** Isolationstests:
   - Aufnahme-`AlertDialog` nur öffnen + ohne Aufnahme verwerfen → Taskbar erscheint und bleibt
     (`iso_dialog_btm.png` → `iso_dismissed_btm.png`).
   - `DamageDialog` (Schaden) öffnen/schließen → Taskbar bleibt (`dumpsys_after_swipe.txt`).
   - **Soft-Tastatur (IME)** beim Tippen im Beschreibungsfeld → Taskbar bleibt (`v3_note_done.png`).
     Das ist entscheidend: Texteingabe ist unvermeidbar, daher genügt es nicht, nur Dialoge zu
     vermeiden.

4. **`qemu.hw.mainkeys=1` hilft hier NICHT.** Auch mit Prop=1 erscheint die Taskbar nach einem Dialog
   (`prop1_after_dialog_btm.png`). Die Prop unterdrückt nur die klassische 3-Button-Leiste, nicht die
   Gesten-Taskbar.

5. **`navigation_mode=0` (3-Button) entfernt die Gesten-Taskbar vollständig** — live getestet
   verschwindet die hängende Leiste sofort, und nach Dialog+IME-Flow bleibt der Rand sauber
   (`combo_after.png`, `rev_after_ime.png`).

---

## Fix (minimal, Ursache adressiert)

**`MainActivity.kt`** – `applyNavigationMode()`:
- Kiosk AN → `Settings.Secure.putInt("navigation_mode", 0)` (3-Button → keine Gesten-Taskbar).
- Kiosk AUS → `2` (Gesten, Normalzustand für Service/Entwicklung).
- Aufgerufen aus dem Settings-Collector, `onWindowFocusChanged` und dem 1-s-Backstop (schreibt nur
  bei Abweichung). Mehrfach nötig, weil SystemUI `navigation_mode` im Boot kurz auf 2 zurücksetzt,
  nachdem die App es früh auf 0 gesetzt hat.

**`AndroidManifest.xml`** – `WRITE_SECURE_SETTINGS` (Schutzlevel u. a. „development", daher per
`pm grant` erteilbar, kein System-Signing nötig).

**Recording-Dialog (bereits committet, `d2fe8fc`)** – als In-Window-Overlay belassen (sauber, kein
Flackern). Weitere Dialoge mussten NICHT umgebaut werden, weil `navigation_mode=0` die Taskbar
quellenunabhängig beseitigt.

### Provisionierung (Golden Image) — EINMALIG nötig
```
adb shell pm grant com.uip.drainq.one android.permission.WRITE_SECURE_SETTINGS
```
Ohne diese Berechtigung loggt die App `navigation_mode nicht setzbar (WRITE_SECURE_SETTINGS fehlt?)`
und der Fix greift nicht. Die Berechtigung überlebt Reboots; die App setzt `navigation_mode`
danach bei jedem Start selbst.

---

## Abnahme (am Gerät, `qemu.hw.mainkeys` für den Fix NICHT erforderlich)

| # | Kriterium | Status | Beleg (`tools/_navbarfix/`) |
|---|-----------|--------|------------------------------|
| 1 | Aufnahme Ohne Einblendung → Leiste weg | ✅ | `fix_c_t0_btm.png`, `fix_c_t3.png` |
| 2 | Mit Einblendung + Pause/Weiter + Stop → weg | ✅ | `fix_mit_run_btm.png`, `fix_pause.png` |
| 3 | Nach Stop → weg | ✅ | `fix_afterstop_clean.png` |
| 4 | Hochwischen nur transient, zieht sich ein | ✅ | `fix_d_swipe_*_btm.png` |
| 5 | **Foto + Schaden-Dialog + Tastatureingabe → weg** | ✅ | `rev_after_ime.png` |
| 6 | **Projektansicht nach Aufnahme/Foto → weg** (exakter User-Fall) | ✅ | `final_projectview.png` |
| 7 | `navigation_mode` überlebt Reboot via App-Autostart | ✅ | nach Reboot = `0` |
| 8 | `./gradlew test` grün | ✅ | BUILD SUCCESSFUL |

**Vorher:** `timeline_btm.png` (graue Taskbar nach Aufnahmestart), `v3_note_done.png` (Taskbar nach
IME). **Nachher:** `rev_after_ime.png`, `final_projectview.png` (Rand sauber).

---

## Empfehlung zur Golden-Image-Prop `qemu.hw.mainkeys=1`

**Belassen.** Sie ist für DIESEN Fix nicht mehr nötig (gelöst über `navigation_mode=0`), unterdrückt
aber zusätzlich die klassische 3-Button-Leiste und ist damit eine günstige, systemweite Absicherung
fürs Feldgerät. Auf dem Gerät steht sie nach dem Test wieder auf `1`.

---

## Geänderte Dateien
- `app/src/main/java/com/uip/oneapp/MainActivity.kt` (applyNavigationMode + Aufrufe)
- `app/src/main/AndroidManifest.xml` (WRITE_SECURE_SETTINGS)
- `app/src/main/java/com/uip/oneapp/ui/screens/inspection/InspectionScreen.kt` (Recording-Overlay, bereits in `d2fe8fc`)

Diagnose-/Beleg-Artefakte: `tools/_navbarfix/` (lokal, nicht committet).
