# RUN REPORT: Hilfe-System ONE — W-H1 + W-H2
**Datum:** 2026-07-16 · **Branch:** feature/help-system · **Basis:** feature/dual-mode

---

## Phase 0 — Preflight

| Check | Ergebnis |
|-------|----------|
| ONE per ADB angeschlossen | ✅ 233b4bd2865177ed |
| Kamera-Device-Node `/dev/video0` | ✅ vorhanden |
| `DRAINQ_PUBLISH_APIKEY` | ✅ gesetzt |
| `JAVA_HOME` | ✅ `C:\Android\jdk17` (symlink → Microsoft JDK 17) |
| Branch erstellt | ✅ `feature/help-system` von `feature/dual-mode` |
| Uncommittete Dateien | ⚠️ Diverse Prompt-/Status-MDs untracked — unberührt gelassen |

**Phase-0-Ergebnis: PASS** — kein Abbruchkriterium verletzt.

---

## Phase 1 — W-H1 Harness bauen

| Artefakt | Status |
|----------|--------|
| `app/src/main/java/.../debugrig/ScreenshotRigBus.kt` | ✅ erstellt (SharedFlow-Bus, src/main) |
| `app/src/debug/java/.../debugrig/ScreenshotRigReceiver.kt` | ✅ erstellt (src/debug, BuildConfig.DEBUG-Guard) |
| `app/src/debug/java/.../debugrig/DemoDataSeeder.kt` | ✅ erstellt (src/debug, idempotent) |
| `app/src/debug/AndroidManifest.xml` | ✅ erstellt (4 Intent-Filter, nur assembleDebug) |
| `NavGraph.kt` — LaunchedEffect rig-Bus | ✅ ergänzt (DEBUG-gated) |
| `app/src/test/.../debugrig/ScreenshotRigTest.kt` | ✅ 7 Unit-Tests GRÜN |
| `tools/manual/scenes.json` | ✅ 21 Szenen (SCR-01–13 + Dialoge) |
| `tools/manual/capture.ps1` | ✅ vollständig (-Langs, -Only, -SkipSeed) |
| `docs/manual/README.md` | ✅ Kurzanleitung |
| `docs/manual/_legacy/` | ✅ Altdateien per git mv archiviert |

**Opus-Audit (RESULT_WH1_AUDIT.md):** 1 Iteration — Befund (src/main statt src/debug) behoben → **PASS**

**Phase-1-Ergebnis: PASS**

---

## Phase 2 — Opus Code-Audit

Siehe `RESULT_WH1_AUDIT.md`. **PASS nach 1 Fix-Iteration.**

---

## Phase 3 — Beta veröffentlichen + Gerät updaten

| Schritt | Ergebnis |
|---------|----------|
| APK gebaut (assembleDebug) | ✅ `app-debug.apk` |
| Publish zu `license.drainq.com` | ✅ 0.5.17/517, SHA256 verifiziert |
| Gerät auf 0.5.16 → 0.5.17 | ✅ Update-Dialog → Download (163 MB) → Android-Install-Dialog → UPDATE |
| versionCode-Verifikation | ✅ `versionCode=517 versionName=0.5.17` |

**Phase-3-Ergebnis: PASS**

---

## Phase 4 — Screenshot-Run (capture.ps1)

*In Arbeit…*

