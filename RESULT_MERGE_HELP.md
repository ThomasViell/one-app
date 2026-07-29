# RESULT: Merge feature/help-system → feature/dual-mode

**Datum:** 2026-07-17  
**Durchgeführt von:** Claude GodMode (MERGE_HELP_RUN_PROMPT.md)

---

## Phase 1 — Lage-Befund

**Warum scheiterte der CEO-Checkout?**  
Auf `feature/help-system` (HEAD c4caf20) lagen 12 tracked Modified Files ungestaged
(WifiController.kt, ConnectivityMonitor.kt u.a.). Git weigerte sich, auf
`feature/dual-mode` zu wechseln, weil diese Dateien sich zwischen den Branches
unterschieden → "Your local changes would be overwritten by checkout".

**Ahead/Behind-Stand (vor diesem Lauf):**
- Lokal `feature/dual-mode` (73deeea) war **12 Commits ahead** von `origin/feature/dual-mode` (a6d5bc1)
- `origin/feature/dual-mode` hatte **keine** zusätzlichen Commits gegenüber lokal
- Echter Divergenz-Konflikt: keiner

**Uncommitted Changes auf help-system (Ursache des fehlgeschlagenen Checkouts):**  
Alle 10 Dateien waren **W-H4-Paparazzi-Null-Safety-Fixes** — Voraussetzungen für
ManualScreenshotTest, die nie commitet wurden (build.gradle.kts Paparazzi-Plugin,
Theme.kt `as? Activity`-Safe-Cast, WifiController/ConnectivityMonitor/NetworkDiscoveryService/
AccessPointController/AndroidLohsStarter/UsbExportService BridgeContext-Guards,
OfflineMapsScreen/OfflineMapsViewModel).

---

## Phase 2 — dual-mode synchronisiert

`git push origin feature/dual-mode` ohne Checkout (von help-system aus):  
→ a6d5bc1..73deeea gepusht (12 Commits, Video-Permission-Persist + Hardbutton-Fixes + Speicheranzeige etc.)

---

## Phase 3 — Test-Root-Cause UpdateE2ETest

**Root-Cause (nicht OkHttp — echte Ursache):**  
`ManualScreenshotTest.scr05_project_form_new` und `scr05b_project_form_edit` rendern
`ProjectFormScreen` via Paparazzi. Diese Composable hat:
```kotlin
LaunchedEffect(Unit) { viewModel.setFilesDir(context.filesDir) }
```
Paparazzi's `BridgeContext.getFilesDir()` liefert null. Kotlin's Non-Null-Assertion
wirft `NullPointerException: getFilesDir(...) must not be null` auf dem
IO/Fake-Handler-Dispatcher. Diese NPE gelangt zum globalen Thread-UncaughtExceptionHandler.
`kotlinx.coroutines.test` speichert sie und wirft `UncaughtExceptionsBeforeTest` beim
Start des **8. UpdateE2ETest** (`downloadAndInstall - disconnect during body logs DOWNLOAD_FAIL`).

**Fix (reine Test-Datei `ManualScreenshotTest.kt`):**  
Im `screenshot()`-Helper einen `LocalContext`-Wrapper eingeführt, der `getFilesDir()`
und `getCacheDir()` auf `java.io.tmpdir` mappt. Alle anderen Context-Aufrufe delegieren
weiterhin an `paparazzi.context` (BridgeContext).

**Teststände (3× verifiziert):**  
- Lauf 1: 448/448 grün — BUILD SUCCESSFUL  
- Lauf 2: 448/448 grün — BUILD SUCCESSFUL  
- Lauf 3: 448/448 grün — BUILD SUCCESSFUL

**Commits auf feature/help-system:**  
- `43e7899` fix(paparazzi): BridgeContext-Null-Safety fuer Paparazzi-JVM-Tests (W-H4)  
- `cdc90c1` fix(test): UpdateE2ETest UncaughtExceptionsBeforeTest beheben  
- `a0fcb10` docs: PROJECT_STATUS + Louis-Antwort-Entwurf aktualisieren  
→ gepusht: c4caf20..a0fcb10

---

## Phase 4 — Merge + Verifikation

```
git checkout feature/dual-mode   ✓ (help-system jetzt clean)
git merge --no-ff feature/help-system  ✓ (kein Konflikt)
```

**Merge-Commit:** `8f04301 Merge branch 'feature/help-system' into feature/dual-mode`

**Post-Merge Tests:** 448/448 grün (--rerun-tasks, BUILD SUCCESSFUL 2m 28s)  
**assembleDebug:** BUILD SUCCESSFUL  
**Push:** 73deeea..8f04301 gepusht

---

## Was gepusht wurde

| Branch | Von | Nach |
|--------|-----|------|
| `feature/dual-mode` (Phase 2) | a6d5bc1 | 73deeea |
| `feature/help-system` (Phase 3) | c4caf20 | a0fcb10 |
| `feature/dual-mode` (Phase 4 Merge) | 73deeea | 8f04301 |

---

## STOPP — kein Release-Publish

Release-Publish wird vom CEO per Ein-Befehl ausgeführt (Gates laufen automatisch mit).
