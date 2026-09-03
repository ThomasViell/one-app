# Messung 03.09.2026 — navigation_mode beseitigt die Taskbar NICHT

**Geraet:** `e27915a669970b5f` (RK3588, Android 12, Firmware 30.06.2026, `eng.yutong...userdebug`)
**App:** 0.9.1/901, per Werkseinrichtung eingerichtet (Geraeteverwalter gesetzt), **Kiosk AUS**
**Anlass:** CEO-Beobachtung 03.09. — grauer Balken bei ausgeschaltetem Kiosk.

## Was gemessen wurde

1. Fensterliste bei Kiosk AUS: `Window{... Taskbar} mFrame=[0,1113][1920,1200]`.
   **Bis aufs Pixel derselbe Rahmen wie am 10.08.2026.** Eine `NavigationBar` existiert nicht.
2. `settings put secure navigation_mode 0` allein: Taskbar bleibt.
3. Danach `killall com.android.systemui` (Neuaufbau der Systemoberflaeche), Wert waehrend des
   Hochlaufs 20x im 800-ms-Takt auf 0 gehalten, damit die App ihn nicht zurueckschreibt.
   Ergebnis: `navigation_mode=0`, **Taskbar weiterhin vorhanden und sichtbar**
   (`mViewVisibility=0x0 mHaveFrame=true mObscured=false`). Vom CEO am Bildschirm bestaetigt.

## Zwei Zwischenlaeufe, die nichts belegen (offen benannt)

- Erster Versuch: Wert stand beim Messen wieder auf 2 — `MainActivity.applyNavigationMode()`
  setzt bei Kiosk AUS auf 2 zurueck (`MainActivity.kt:327`).
- Zweiter Versuch mit `am force-stop`: ebenfalls 2. Grund: Die App ist der Startbildschirm
  (`category.HOME`), Android startet sie sofort neu, und sie schreibt beim Start.

## Befund

`AndroidManifest.xml` (Zeilen 34-41) begruendet das Sonderrecht `WRITE_SECURE_SETTINGS` damit,
dass `navigation_mode=0` die Gesten-Taskbar abschaltet. **Diese Begruendung ist gemessen falsch.**
Auf dieser Hardware und diesem launcher3-Stand existiert und zeichnet die Taskbar auch im
3-Button-Modus.

Folgen:
- Das Sonderrecht wird bei jeder Werkseinrichtung erteilt (`pm grant ... WRITE_SECURE_SETTINGS`)
  fuer eine Wirkung, die nicht eintritt.
- `applyNavigationMode()` schreibt dauerhaft an einer Systemeinstellung (offener Punkt
  „Sekundenwaechter schreibt Systemeinstellung", 10.08.) — ohne belegten Nutzen.
- Die einzige gemessen wirksame Behandlung bleibt der Stash-Impuls aus der Welle
  `taskbar-balken` (11.08.), und der ist an den Kiosk-Zustand gekoppelt.

## Was NICHT gemessen ist

- Ob der Zielstand die Ursache ist. Vergleich am Schreibtisch: die Lieferanten-App
  `com.bominwell.minipush` hat den Balken nicht und ist auf **targetSdk 29** gebaut
  (unsere App: 34), meldet sich **nicht** als Startbildschirm an (nur `category.LAUNCHER`)
  und setzt `fitsSystemWindows="false"`. Drei Unterschiede, keiner davon gemessen.
- Ob ein Neustart des Geraets mit von Anfang an gesetztem `navigation_mode=0` etwas aendert.
  Nicht geprueft, weil die App den Wert beim Start selbst setzt.
