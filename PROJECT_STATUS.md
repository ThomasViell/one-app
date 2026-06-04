# drainq.one — Status

**Stand:** 2026-06-04 (Abend) · **Rolle:** ONE-Schiebekamera — läuft direkt auf der ONE-Hardware (RK3588, Android), Steuerung seriell `/dev/ttyS5`, Video V4L2 `/dev/video0`
**Stack:** Kotlin / Jetpack Compose (Room, Koin, ExoPlayer/Media3, iText7, Coil-SVG) · NDK (`app/src/main/cpp/v4l2bridge.c`) · **Pfad:** `C:\Projekte\drainq.one` (GitHub: ThomasViell/one-app)
**Aktiver Branch:** `feature/network-settings` — Superset des Tagewerks (SA-Design-Rollout + Device-Fixes + Netzwerk-Feature + Pager). **⚠ NICHT gepusht** (kein origin-Tracking) → bis zum Push ist alles nur lokal = Verlustrisiko.
**Gerät:** Serial `233b4bd2865177ed`. **Build:** `$env:JAVA_HOME="C:\Android\jdk17"; $env:ANDROID_SERIAL="233b4bd2865177ed"; .\gradlew installDebug`

## Tagewerk 2026-06-04 (alles committet, am Gerät verifiziert)
**SA-Design app-weit (Welle 0–4):** Fundament Amber/Dark+Light, Inter (Barlow raus), Tabler-Icons, Dq-Komponenten (`c2aee91`); Inspektion Cinema + OSD live/Burn-in (`030bdf4`); Navigation/Home/Projekte/Form/MapPicker (`dce66d1`); Detail/Galerie/Media-Dialoge/Reports (`e674c59`); Update/Connection/OfflineMaps/Splash (`dc260ed`). Vorgabe + Mockups in `docs/design/`.

**Device-Fixes (am Gerät iteriert):** Inspektion-Bedienpanel verschlankt + unteres Band on-demand/transparent (`b5313d2`,`6232477`,`1c283d3`); Status-Chips → ein Batterie-Chip, Quelle = **Android-System-Akku** (wie OEM-App, RE-verifiziert; serielle GROUP_CAMERA-Spannung war Fehlannahme) (`a72eec5`,`af595df`); Splash mit echtem DrainQ-Logo theme-abhängig (`da3dc6e`); Navi-Leiste DrainQ-Bildmarke statt „ONE" + „Verbunden"-Chip auf Home raus (`e1c6110`); Inspektion-Back-Button größer/transparenter (`a2b6857`); ProjectDetail-Header-Aktionen als Icon+Label, größer/weiter auseinander, Back-Pfeil 72-dp-Fläche (`d6a80d4`,`adf3ec3`). Logo-Assets (CORE entfernt) in `res/raw/logo_drainq_*`.

**Pager:** wiederverwendbare `DqPager`-Komponente, 6/Seite, „Seite X/N" + Zahlen + Pfeile, Fensterung > 7 Seiten; auf Home, Projekte und ProjectDetail-Tabs (`f6d6963`,`714804c`).

**Netzwerk-Feature (eigener Schwerpunkt):** neuer „Netzwerk & Verbindung"-Screen — Online-Status, In-App-WLAN (adaptiv nach Device-Owner: privilegiert WifiManager direkt, sonst WifiNetworkSpecifier+requestNetwork), Tethering-Sprung, DrainQ-Cloud-Login als Stub (später) (`e91aacc`,`8c73de1`,`1c869c1`). Diagnose am Gerät: Test-Gerät kein Device-Owner → REQUEST-Pfad; voller geräteweiter Connect erst auf der provisionierten ONE.

**Früher am Tag:** #14 Keyboard-folgt-Sprache + Compose-1.7 (`eb390ba`), Golden-Image-Doc + Aufräumen (`3f758cb`), Status/Handover nachgezogen (`f1ed28b`).

## Offen / nächste Schritte
1. **Push** `feature/network-settings` nach origin (Upstream setzen) — sichert das gesamte Tagewerk. Aktuell ungesichert.
2. **Geräte-Gate aus Welle 1** (OSD-Einbrennung/Recording + PDF-Overlay) noch verifizieren — vor master-Merge.
3. **Branch-Konsolidierung:** `feature/network-settings` (Superset) ist die Live-Linie; Draft-PR #2 (sa-design-rollout) entsprechend nachziehen/ersetzen, dann → master, danach Tag `v0.4.0`.
4. Pager auf weitere Listen bei Bedarf (Reports/Offline-Karten). Autostart (geparkt). Reorg app-one→drainq-android.
5. **DrainQ-Cloud-Login** echt ausbauen (OAuth/Token gegen drainq.web, Keystore/Encrypted, Audit) — „später".

## Hinweise
- **Git nur lokal** im Terminal; CRLF-Mount-Churn (~199 Dateien) ist kein echter Stand. Gezielt committen, nie `git add -A`.
- **Akku-Quelle** = Android-System-Akku (`ACTION_BATTERY_CHANGED`), nicht seriell. Device-Owner-Pfad für WLAN: Golden-Image (`docs/PROVISIONING_GOLDEN_IMAGE.md`).
- Kein `su`; HW-Serial nativ. bash-Reads über den Mount lügen — host-seitig/lokal prüfen.

## Letzte Änderungen
- [2026-06-04] SA-Design-Rollout (Welle 0–4) + Device-Fixes + Pager + Netzwerk-Feature, am Gerät iteriert. Branch `feature/network-settings` (ungepusht).
- [2026-06-03] Hardtasten/Softbutton-Leiste, Licht −/+, V4L2-Recording, Cinema, UI-Politur.
- [2026-06-02] Feldtest-Findings + HW-Serial-Fix nativ, Kiosk-Schalter, Schnellaufnahme.
