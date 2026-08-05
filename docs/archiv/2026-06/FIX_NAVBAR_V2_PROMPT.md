# Auftrag: Untere Leiste endgültig weg — Technik der Original-App übernehmen (On-Device, autonom)

**Rolle:** Autonomer On-Device-Entwickler für DrainQ.ONE. Übernimm die in der Original-App
(`com.bominwell.minipush`) bewährte Technik, setze sie in DrainQ.ONE um, verifiziere am Gerät,
iteriere bis ALLE Dialoge + Tastatur auf ALLEN Screens streifenfrei sind.

## Kontext (heute hart erarbeitet)
Die „Leiste" ist die **launcher3-Gesten-Taskbar** (`ITYPE_EXTRA_NAVIGATION_BAR`, `navigation_mode=2`).
Der bisherige Fix (`navigation_mode=0` + moderne `WindowInsetsControllerCompat.hide()`) ist nur eine
**Teillösung** — irgendein Dialog aktiviert die Leiste weiterhin. Die **moderne** Insets-API ist
gegen diese Taskbar wirkungslos (per dumpsys belegt: App fordert „invisible", launcher3 ignoriert es).

## GOLDENE QUELLE — so macht es die Original-App (dort tritt der Balken NIE auf)
Decompiliert unter `C:\Projekte\one-revers\decoded\jadx-v130\sources\com\bominwell\robot\`:

**`base/BaseActivity.java`**
- `hideBottomUIMenu()` (Z. 166–168): `getWindow().getDecorView().setSystemUiVisibility(5894)`
  — **LEGACY**-Immersive-Flags, NICHT die moderne API.
- Aufruf in `onResume()` (Z. 126) **und** `onPause()` (Z. 137–139, wenn `isNeedHideBottomUiMenu()`).

**`base/BaseDialogFragment.java`** — der entscheidende Teil (Dialoge sind der Auslöser):
- `setWindowStyle(dialog)` (Z. 118–134): vor dem Anzeigen `window.setFlags(8, 8)`
  → **FLAG_NOT_FOCUSABLE** (Wert 8). Ein nicht-fokussierbares Fenster „unstasht" die Taskbar NICHT.
- `onResume()` (Z. 145–155): `hideBottomUIMenu()` + danach `getDialog().getWindow().clearFlags(8)`
  (Fokus/Eingabe wieder zulassen, nachdem Immersive steht).
- `hideBottomUIMenu()` (Z. 157–165): auf der **Dialog-decorView** `setSystemUiVisibility(2)` **plus**
  ein `OnSystemUiVisibilityChangeListener`, der bei JEDER Sichtbarkeitsänderung erneut
  `setSystemUiVisibility(5894)` setzt. → Das Dialog-Fenster hält sich selbst immersiv.

**Kernrezept:** JEDES Fenster (Activity **und jeder Dialog**) trägt die Legacy-Flags `5894`
(= IMMERSIVE_STICKY|FULLSCREEN|HIDE_NAVIGATION|LAYOUT_STABLE|LAYOUT_FULLSCREEN|LAYOUT_HIDE_NAVIGATION)
auf seiner eigenen decorView, **mit** Re-Hide-Listener; Dialoge zusätzlich beim Show kurz
FLAG_NOT_FOCUSABLE.

## Umsetzung in DrainQ.ONE (Compose)
1. **Activity:** In `MainActivity` zusätzlich zur bestehenden Logik die Legacy-`5894`-Flags +
   `OnSystemUiVisibilityChangeListener` (Re-Hide) anwenden, wenn Kiosk an (Original-Verhalten).
   `navigation_mode`-Logik darf bleiben (schadet nicht), ist aber nicht mehr der Träger.
2. **Jeder Compose-Dialog/Popup:** Ein wiederverwendbarer Helfer, der das Dialog-Fenster holt
   (`(LocalView.current.parent as? DialogWindowProvider)?.window`) und darauf anwendet:
   - `decorView.setSystemUiVisibility(5894)` + `OnSystemUiVisibilityChangeListener` (Re-Hide auf 5894),
   - optional `FLAG_NOT_FOCUSABLE`-Trick analog Original (falls für saubere Übernahme nötig).
   Diesen Helfer in **ALLE** Dialoge/Popups einsetzen. Bekannte Fenster-Quellen in DrainQ.ONE:
   `DamageDialog`, `NoteDialog`, `ImagePickerDialog` (neu), Export-/Archiv-/USB-Dialoge,
   Lösch-Bestätigung, `ExposedDropdownMenu`/Dropdowns, alle `AlertDialog`/`Dialog`/`Popup`.
   (Der Aufnahme-Dialog ist bereits In-Window-Overlay — als Muster ok.)
3. **Soft-Tastatur (IME):** Eingabe ist unvermeidbar und triggert die Taskbar ebenfalls. Sicherstellen,
   dass der Activity-Re-Hide-Listener (5894) nach IME-Show/Hide greift; ggf. nach IME-Dismiss erneut anwenden.
4. **Dropdowns/Spinner:** Falls `ExposedDropdown` als eigenes Fenster die Taskbar holt, ebenfalls den
   Helfer anwenden oder auf In-Window-Lösung umstellen.

## Randbedingungen
- Gerät `233b4bd2865177ed` (RK3588, Android 12, userdebug, `adb root`). Kiosk ist AN.
- Build/Install: `$env:JAVA_HOME="C:\Android\jdk17"; $env:ANDROID_SERIAL="233b4bd2865177ed"; .\gradlew installDebug`
- Branch `feature/beta-wave-1`. **Gezielt committen, nie `git add -A`.**
- **Keine bash/python-Schreibzugriffe über den Cowork-Mount** (CRLF/Korruption) — nur Editor-Tools.
- `WRITE_SECURE_SETTINGS` ist auf dem Gerät erteilt; falls verloren: `pm grant com.uip.drainq.one android.permission.WRITE_SECURE_SETTINGS`.
- Bestehende Tests grün halten (`.\gradlew test`).

## Abnahme (am Gerät, jeder Punkt streifenfrei — Vorher/Nachher-Screenshots nach `tools/_navbarfix/`)
1. Aufnahme Ohne/Mit Einblendung + Pause/Stop
2. Foto-Dialog
3. **Schaden-Dialog inkl. Texteingabe (Soft-Tastatur)** ← der zuletzt brechende Fall
4. Notiz-Dialog (inkl. Audio/Text)
5. **Logo-Auswahl (ImagePickerDialog)**
6. Export-/Archiv-/USB-Dialog, Lösch-Bestätigung
7. Dropdowns in Einstellungen + Projekt-Formular
8. Projektansicht nach Aufnahme/Foto (exakter User-Fall)
9. Nach jedem Dialog zurück → kein Reststreifen
10. `.\gradlew test` grün

## Falls die Original-Technik den Fall NICHT vollständig löst
Dann lautet der Auftrag: **ALLE** Dialoge, Popups, Dropdowns und Tastatur-Eingaben in DrainQ.ONE
systematisch am Gerät durchspielen (jeder Screen, jeder Dialog) und so lange nachbessern
(In-Window-Overlays statt separater Fenster, wo nötig), bis an KEINER Stelle mehr ein Streifen
erscheint. Quelle der Wahrheit ist der Screenshot am Gerät, nicht die Theorie.

## Liefergegenstand
- `RESULT_NAVBAR_FIX_V2.md`: übernommene Original-Technik (mit Verweis auf die Fundstellen oben),
  geänderte Dateien (Datei:Zeile), Abnahme-Tabelle mit Belegen.
- Gezielte Commits auf `feature/beta-wave-1`, gepusht.
