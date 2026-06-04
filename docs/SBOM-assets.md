# SBOM — Design-Assets (DrainQ SA-Design, Welle 0)

**Stand:** 2026-06-04 · **Branch:** `feature/sa-design-rollout`
**KRITIS-Bezug:** Vorgabe Abschnitt 7 (Supply Chain / NIS2 #4). Neue eingebettete
Assets werden hier nachgewiesen; keine neuen Netz-/Laufzeit-Abhängigkeiten.

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
