# SZENARIEN — Kette kiosk-pflicht (03.09.2026)

Fuenf Erfolgskriterien aus `AUFTRAG.md` („Woran wir Erfolg messen"), je Szenario:
Vorbedingung, Handlung, Messbefehl, Erwartung. Geraet: `e27915a669970b5f`.
Messanker „im Kiosk": `mLockTaskModeState=LOCKED` UND `topResumedActivity` = unser Paket
(Definition aus `tools/werkseinrichtung/Invoke-DeviceSetup.ps1`, vom Plan uebernommen).
Ohne Geraeteeigentuemer heisst „im Kiosk" (CEO-Entscheid R2, 03.09.2026): Vollbild
(5894-Flags an unseren Fenstern) + Balken-Behandlung + HOME-Rolle, ohne LockTask.

## Szenario 1 (A) — Kiosk ist Vorgabe und nicht abschaltbar

**1a Bestandsgeraet mit Altwert AUS**
- Vorbedingung: `kiosk_mode` im DataStore steht auf AUS (Nachweis in M0); Testbau 901/0.9.1 plattformsigniert per `adb install -r` installiert.
- Handlung: App starten; Einstellungen oeffnen.
- Messbefehl: `dumpsys activity activities | grep -E "mLockTaskModeState|topResumedActivity"`; `uiautomator dump` + `grep -ci kiosk` auf dem Dump.
- Erwartung: innerhalb 10 s `LOCKED` + unser Paket oben; Einstellungen enthalten keinen Kiosk-Schalter (`grep -ci kiosk` = 0).

**1b Geraet ohne Werkseinrichtung**
- Vorbedingung: Geraet ohne Eigentuemer (R4: nach Ruecksetzen per `Rueckholweg-DeviceOwner-entfernen.ps1`), Testbau installiert.
- Handlung: App starten, 10 s warten.
- Messbefehl: `dumpsys window windows` (unsere Fenster, Flags), `dumpsys activity activities | grep topResumedActivity`.
- Erwartung: Vollbild (5894-Flags), Balken-Behandlung aktiv, kein Anpinn-Dialog. `LOCKED` ist ohne Eigentuemer nicht erwartbar und wird nicht gefordert.

## Szenario 2 (B) — „App verlassen" als einmalige Handlung

**2a Verlassen ueber Einstellungen**
- Vorbedingung: App im Kiosk (`LOCKED`).
- Handlung: Einstellungen → „App verlassen" → Bestaetigung „Verlassen".
- Messbefehl: nach 3 s `dumpsys activity activities | grep topResumedActivity`; Screenshot.
- Erwartung: `topResumedActivity` = `com.android.launcher3`, nicht unser Paket.

**2b Verlassen ueber Power-Langdruck**
- Vorbedingung: App im Kiosk, Inspektionsbild.
- Handlung: Power-Knopf lang druecken → „Beenden".
- Messbefehl: wie 2a.
- Erwartung: wie 2a — Systemoberflaeche, nicht unsere App.

**2c Rueckweg ueber Startsymbol**
- Vorbedingung: Zustand nach 2a/2b (Systemoberflaeche).
- Handlung: Startsymbol der App antippen.
- Messbefehl: `dumpsys activity activities | grep -E "mLockTaskModeState|topResumedActivity"`.
- Erwartung: `LOCKED` + unser Paket oben, Vollbild.

**2d Neustart des Geraets**
- Vorbedingung: beliebiger Zustand, App als HOME bevorzugt.
- Handlung: `reboot`, 90 s warten.
- Messbefehl: wie 2c.
- Erwartung: `LOCKED` + unser Paket oben.

**2e Versehensschutz**
- Vorbedingung: App im Kiosk, Einstellungen offen.
- Handlung: Zeile „App verlassen" einmal antippen, 10 s warten.
- Messbefehl: `dumpsys activity activities | grep mLockTaskModeState`; Screenshot.
- Erwartung: App bleibt, `LOCKED` bleibt, Bestaetigungszeile ist zugeklappt.

## Szenario 3 (C) — Balken-Behandlung ohne Kiosk-Zustand

**3a ausserhalb des Kiosks**
- Vorbedingung: Geraet ohne Eigentuemer (R4-Zustand; App dauerhaft nicht `LOCKED`), Testbau laeuft.
- Handlung: Power-Knopf → „Abbrechen", 1 s warten.
- Messbefehl: `dumpsys window windows | grep -E "Window #|Taskbar|mFrame|mViewVisibility"` vorher/nachher; Screenshot.
- Erwartung nachher: Taskbar nicht sichtbar (eingezogen). Vorher/Nachher-Paar mit Fensterliste, nicht Bildschirmeindruck allein.

**3b im Kiosk (Regression)**
- Vorbedingung: `LOCKED`.
- Handlung: Power-Knopf → „Abbrechen"; Tastatur auf/zu.
- Messbefehl: Fensterliste wie 3a.
- Erwartung: Taskbar nach 1 s eingezogen, Verhalten wie 0.9.1.

## Szenario 4 (D) — Wirkungsloser Schreibzugriff und Sonderrecht raus

**4a Recht**
- Vorbedingung: Testbau installiert.
- Messbefehl: `dumpsys package com.uip.drainq.one | grep -c WRITE_SECURE_SETTINGS`.
- Erwartung: 0.

**4b Protokoll**
- Vorbedingung: Testbau installiert.
- Handlung: `logcat -c`; App-Start, drei Fokuswechsel, 5 min laufen lassen; `logcat -d | grep -c navigation_mode`.
- Erwartung: 0.

**4c Werkseinrichtung ohne Grant**
- Vorbedingung: geaendertes `Invoke-DeviceSetup.ps1` (Grant-Schritt entfernt); Geraet ohne Eigentuemer (R4-Zustand); Testbau-APK zeitweise als `tools/werkseinrichtung/app/DrainQ-ONE_0.9.1_901_platform.apk` (nicht committet).
- Handlung: `Start-Werkseinrichtung.cmd` (Standardweg, nicht Bestandsgeraete-Modus — Auflage A-5).
- Messbefehl: Protokoll in `tools/werkseinrichtung/logs/`.
- Erwartung: Lauf endet GRUEN, kein Grant-Schritt im Log. Zugleich Nachweis, dass der normale Fertigungsweg auf einem Geraet ohne unsere Vorbelegung durchlaeuft (eigene Berichtszeile, Auflage A-5).

## Szenario 5 — Testsuite und Golden-Tor

- Vorbedingung: Bau abgeschlossen.
- Handlung: `./gradlew testDebugUnitTest`; `.\tools\manual\verify.ps1`.
- Messbefehl: JUnit-XML auszaehlen (nicht nur Exit-Code); Exit-Code von verify.ps1.
- Erwartung: Tests N/N gruen, verify.ps1 Exit 0.
