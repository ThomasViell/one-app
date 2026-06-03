# Welle 1 — Kiosk/Immersive (#5) + Tastatur-Dismiss (#4)

**Stand:** 2026-06-02 · **Bezug:** `FEEDBACK_Jakob_2026-06-02_Analyse.md` (Querschnitt B, #4, #5)
**Ziel:** Feld-Showstopper beheben — Gerät verlässt nicht mehr versehentlich die App, Tastatur lässt sich zuverlässig schließen.

## Scope

- **#5 Kiosk/Vollbild als Schalter (Standard AUS):** System-Bars (Status + Navigation) werden nur ausgeblendet, wenn der **Kiosk-Modus** in den Einstellungen aktiv ist. Wischen blendet sie dann nur transient ein (kein Absturz auf den Android-Homescreen). **Standard AUS**, damit Entwicklung/Service immer auf die Android-Ebene kommt — verhindert das Aussperren beim Feldtest.
- **#4 Tastatur-Dismiss:** Tipp auf freie Fläche schließt das IME in Formular und Dialogen.

**Wichtig:** Immersive blendet nur die **Android**-Leisten aus — die **App-eigene Navigation** (Bottom-Bar/Rail) bleibt sichtbar. Der Kiosk-Schalter ist daher auch bei aktivem Kiosk über Einstellungen wieder erreichbar.

**Nicht in Welle 1:** Echtes LockTask/Screen-Pinning (braucht Device-Owner-Provisionierung der ONE per ADB — separater Ops-Schritt). Hardtasten (Welle 2), Schnellaufnahme (Welle 3).

## Geänderte Dateien

| Datei | Änderung |
|---|---|
| `MainActivity.kt` | `applySystemBars()` reaktiv: liest `kiosk_mode`-Pref (DataStore); AN = System-Bars verstecken (`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`), AUS = Bars anzeigen. Re-Apply in `onWindowFocusChanged`. |
| `ui/screens/settings/SettingsViewModel.kt` | `kioskMode`-State (Default false) + Key `kiosk_mode` + `updateKioskMode()` + Persistenz. |
| `ui/screens/settings/SettingsScreen.kt` | Kiosk-Schalter (Card mit Switch). |
| `ui/localization/LocalizationManager.kt` | i18n-Keys `kiosk_mode` / `kiosk_mode_desc` in `deTranslations()` (Fallback für alle Sprachen) + `enTranslations()`. |
| `ui/screens/projects/ProjectFormScreen.kt` | `LocalFocusManager` + Tap-außerhalb → `clearFocus()`. |
| `ui/screens/inspection/DamageDialog.kt` | dito (Content-Column). |
| `ui/screens/inspection/NoteDialog.kt` | dito + fehlende Imports. |

## Lokaler Build & Test (auf Thomas' Maschine)

```powershell
cd C:\Projekte\drainq.one
$env:JAVA_HOME = "C:\Android\jdk17"
adb devices                              # Serial ablesen
$env:ANDROID_SERIAL = "233b4bd2865177ed" # ONE — oder Tablet-Serial
.\gradlew installDebug
```

## Abnahmekriterien

1. **Standard:** App startet **mit** Android-Leisten (Kiosk AUS) — kein Aussperren.
2. Einstellungen → „Kiosk-Modus (Vollbild)" einschalten → System-Bars verschwinden; Rand-Wisch blendet sie nur transient ein, **kein** Wechsel zum Android-Homescreen.
3. Kiosk AN: App-Navigation bleibt sichtbar → man kommt zurück in die Einstellungen und kann Kiosk wieder ausschalten.
4. In „Neues Projekt", Schaden- und Notiz-Dialog: Tippen auf freie Fläche schließt die Tastatur.

## Commit (lokal)

```
git add app/src/main/java/com/uip/oneapp/MainActivity.kt \
        app/src/main/java/com/uip/oneapp/ui/screens/settings/SettingsViewModel.kt \
        app/src/main/java/com/uip/oneapp/ui/screens/settings/SettingsScreen.kt \
        app/src/main/java/com/uip/oneapp/ui/localization/LocalizationManager.kt \
        app/src/main/java/com/uip/oneapp/ui/screens/projects/ProjectFormScreen.kt \
        app/src/main/java/com/uip/oneapp/ui/screens/inspection/DamageDialog.kt \
        app/src/main/java/com/uip/oneapp/ui/screens/inspection/NoteDialog.kt \
        docs/waves/WELLE-1-Kiosk-Tastatur.md
git commit -m "feat(ux): Welle 1 — Kiosk-Schalter/Immersive (#5, default aus) + Tastatur-Tap-Dismiss (#4)"
```

## Folge-Schritt (Ops, optional, für echten Kiosk)
ONE als Device-Owner provisionieren (ADB, Werksreset-Gerät) → `startLockTask()` koppeln, sobald Kiosk-Pref aktiv. Sperrt Home/Recents vollständig. Härtungsmaßnahme (KRITIS-positiv), separat dokumentieren.
