# SBOM — Design-Assets (DrainQ SA-Design, Welle 0)

**Stand:** 2026-06-04 · **Branch:** `feature/sa-design-rollout`
**Herkunfts-Nachweis:** Vorgabe Abschnitt 7 (Supply Chain). Neue eingebettete
Assets werden hier nachgewiesen; keine neuen Netz-/Laufzeit-Abhängigkeiten.
(KRITIS/NIS2/ISO 27001 sind für die ONE nicht einschlägig — CEO-Entscheid 14.07./07.09.2026,
siehe `docs/engineering/01-analysis_one.md:113` — der Herkunfts-Nachweis selbst bleibt sinnvolle
Praxis unabhängig davon.)

## Neu hinzugefügt

| Asset | Version/Quelle | Lizenz | Ablage | Verwendung |
|---|---|---|---|---|
| **Inter** (Regular/Medium/SemiBold) | rsms/inter v4.1 (static TTF) | SIL Open Font License 1.1 | `app/src/main/res/font/inter_*.ttf` | App-Schrift (ersetzt Barlow); später auch OSD-Burn-in-Renderer |
| **Tabler Icons** (Outline) | tabler/tabler-icons (main) | MIT | `app/src/main/res/drawable/ic_dq_*.xml` | `DqIcon`-Vektor-Drawables |
| **coil-svg** | io.coil-kt:coil-svg:2.5.0 | Apache-2.0 | Gradle (`app/build.gradle.kts`) | SVG-Decoder für das Splash-Logo; SVG-Geschwister von bereits vorhandenem `coil-compose:2.5.0` (gleicher Maintainer/Version) |
| **DrainQ-Logo** (on-dark / on-light) | Eigenes Marken-Asset (UIP) | proprietär (intern) | `app/src/main/res/raw/logo_drainq_on_{dark,light}.svg` | Splash-Screen-Logo, theme-abhängig |
| **DrainQ-Bildmarke** (icon, on-dark / on-light) | Eigenes Marken-Asset (UIP) | proprietär (intern) | `app/src/main/res/raw/logo_drainq_icon_on_{dark,light}.svg` | Navi-Leisten-Marke (DqNavRail-Header), theme-abhängig |

Lizenztexte: `docs/licenses/Inter-OFL.txt`, `docs/licenses/Tabler-MIT.txt`.
Tabler-SVG→VectorDrawable-Konvertierung: `tools/fetch_tabler_icons.js` (reproduzierbar).

## Entfernt

| Asset | Grund |
|---|---|
| **Barlow** (Light/Regular/Medium/SemiBold/Bold/Black TTF) | Durch Inter ersetzt (Vorgabe Abschnitt 0/6). Dateien aus `res/font/` gelöscht, alle Code-Referenzen migriert. |

## Netzwerk & Verbindung (feature/network-settings)

| Posten | Wert |
|---|---|
| **Neue Gradle-/Netz-Dependency** | **keine** — nur Plattform-APIs (`ConnectivityManager`, `WifiManager`, `WifiNetworkSuggestion`, Settings-Intents) |
| **Neue Permission** | `android.permission.NEARBY_WIFI_DEVICES` (`usesPermissionFlags="neverForLocation"`) für den In-App-WLAN-Scan ab Android 13; `android.permission.CHANGE_NETWORK_STATE` (normal) für die aktive Verbindung via `WifiNetworkSpecifier` + `ConnectivityManager.requestNetwork`. WLAN-/Standort-/Netzwerk-Permissions (`ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`, `ACCESS_NETWORK_STATE`, `ACCESS_FINE_LOCATION`) waren bereits vorhanden. |
| **Neue Icon-Keys (DqIcon)** | `wifi`, `lock`, `cloud`, `access_point` (Tabler Outline, gleiche MIT-Quelle/Pipeline wie oben) |
| **Secrets** | keine im Code. WLAN-Passwörter werden nicht persistiert/geloggt. DrainQ-Cloud-Login ist ein Stub ohne Endpunkt; Token-Ablage (Keystore/EncryptedSharedPreferences) ist vorbereitet, aber leer. |

## Hinweise

- **coil-svg:2.5.0** ist die einzige neue Gradle-Dependency: reiner SVG-Decoder,
  Geschwister-Artefakt des bereits genutzten `coil-compose:2.5.0` (kein neues
  Maintainer-/Versions-Risiko, keine neue Netz-Permission). Die Logo-SVGs sind
  statisch eingebettet. Fonts/Icons bleiben statisch eingebettet.
- Eingebettete Icon-Keys (DqIcon): home, inspection, projects, settings, check,
  chevron_down/up, chevron_right, refresh, dot, camera, photo, alert, probe, light,
  minus, plus, meter, back, keyboard_hide, language, company, weather, osd,
  fullscreen, info, map, delete, edit, close, download, moon, sun, save, new_project.
- **Offen für Welle 1:** OSD-Burn-in-Pfad nutzt Inter (FFmpeg/Renderer) — dort
  Font-Datei + Lesbarkeit am Gerät prüfen (Vorgabe Abschnitt 7, Rückwärtskompat.).
