# Autorun-Phasenplan — Touch-UI / Cinema-Mode für die Inspection-Ansicht

**Stand:** 2026-05-19
**Bezug:** docs/PLAN_INTERNAL_HARDWARE_INTEGRATION.md (Phase 2 abgeschlossen, Migration A im Master `13b1384`)
**Skill:** `autorun_phasenplan_pattern` (siehe memory/MEMORY.md)

---

## Ziel

Die `InspectionScreen.kt` wird von einem **Split-Layout (Video links, Steuerung rechts ~360 dp)** auf einen **Cinema-Mode (Video full-bleed, Steuerung auf Tap-Demand)** umgestellt. Buttons werden auf **handschuh-taugliche Touch-Größen** vergrößert und mit einer **Slide-In-/Slide-Out-Animation** kontextbezogen eingeblendet.

Plus: das **OSD (Distanz/Sonde/Licht/Spannung)** liegt persistent über dem Video, in Inspektionsmonitor-Qualität lesbar (96 sp Distanz, Schatten gegen helle Hintergründe).

Quelle der Touch-Größen-Empfehlungen: Material Design 3, Nielsen Norman Group, Android-Accessibility-Guidelines, plus Industrie-Tablet-Best-Practices für Glove-Bedienung (Web-Recherche vom 2026-05-19).

---

## Design-Tokens (zwingend zu verwenden)

In `app/src/main/java/com/uip/oneapp/ui/theme/Dimensions.kt` (neu) hinterlegen:

```kotlin
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Dimensions {
    // Touch-Targets
    val TouchLarge = 72.dp        // ≈ 15 mm — Primary (Sonde, Licht, Reset)
    val TouchMedium = 56.dp       // ≈ 12 mm — Sekundär
    val TouchSpacing = 12.dp      // Mindestabstand zwischen Touch-Targets

    // Cinema-Mode Panel
    val PanelWidth = 320.dp       // Schiebbares rechtes Panel
    val PanelEdgePadding = 16.dp
    val PanelSlideDuration = 250  // ms, FastOutSlowInEasing

    // Auto-Hide
    val ControlsAutoHideMs = 5000L

    // OSD
    val OsdDistanceFontSize = 96.sp
    val OsdSecondaryFontSize = 20.sp
    val OsdSmallFontSize = 14.sp
    val OsdShadowOffset = 3.dp

    // Buttons
    val ButtonLabelFontSize = 18.sp
    val ButtonCornerRadius = 12.dp
}
```

Alle Wellen verwenden diese Konstanten — **keine Magic-Numbers** in der UI.

---

## Wellen-Übersicht

| Welle | Beschreibung | Dauer (Schätzung) | Modell |
|---|---|---|---|
| W0 | Pre-Flight (im Skript, kein Agent) | 2 min | – |
| W1 | Cinema-Mode Layout + Tap-State + Slide-In | 20 min | sonnet (think) |
| W2 | Touch-optimierte Button-Größen + Dimensions.kt | 15 min | sonnet |
| W3 | OSD-Overlay auf Video (groß, persistent, Schatten) | 15 min | sonnet |
| W4 | Build + Deploy + Verify + Commit/Push | 10 min | haiku |
| **Summe** | | **~62 min** | |

Jede Welle hat:
- **Vorbedingung:** liest `RESULT_TOUCHUI_W{N-1}.md` (außer W1)
- **Output:** schreibt `RESULT_TOUCHUI_W{N}.md` mit den Pflicht-Inhalten unten
- **Compile-Pflicht:** `./gradlew compileDebugKotlin` muss `BUILD SUCCESSFUL` melden, sonst Welle FAILED

---

## W0 — Pre-Flight (vom Skript, kein Agent)

Das `autorun_touchui.ps1`-Skript prüft vor W1:

1. **Repo sauber genug:** Master-Branch, letzter Commit ist `13b1384` oder neuer (Migration A drin)
2. **Build:** `./gradlew compileDebugKotlin` → BUILD SUCCESSFUL
3. **NDK-Native-Build:** `./gradlew :app:externalNativeBuildDebug` → ohne Fehler
4. **Tablet erreichbar:** `adb -s 233b4bd2865177ed get-state` → `device`
5. **Plan-MD vorhanden:** `AUTORUN_TOUCHUI_PLAN.md` (diese Datei) existiert
6. **Feature-Branch:** wenn noch nicht da, `git checkout -b feature/touchui-cinema-mode`

Bei Fehlern in Schritt 1–5: Skript bricht mit klarer Fehlermeldung ab. Schritt 6 ist idempotent.

---

## W1 — Cinema-Mode Layout + Tap-State + Slide-In-Animation

**Ziel:** Video füllt komplett den Bildschirm. Steuer-Panel ist standardmäßig versteckt. Tap aufs Video toggelt das Panel. Nach 5 s ohne Interaktion verschwindet das Panel automatisch.

### Files zu ändern
- `app/src/main/java/com/uip/oneapp/ui/screens/inspection/InspectionScreen.kt` — Layout-Refactor
- `app/src/main/java/com/uip/oneapp/ui/theme/Dimensions.kt` — neu, mit den Tokens oben

### Vorgehen

1. **Dimensions.kt anlegen** mit den Konstanten oben.

2. **InspectionScreen.kt:**
   - Top-Level `Row { Column(weight=videoWeight) | Column(weight=controlsWeight) }` ersetzen durch `Box { … }`
   - Im Box:
     - **Layer 1:** `VideoView` / `FfmpegVideoPlayer` mit `Modifier.fillMaxSize()` — Video als Hintergrund
     - **Layer 2:** OSD-Overlay (kommt fest in W3, hier als Platzhalter mit aktueller Darstellung beibehalten)
     - **Layer 3:** Tap-Detector — `Modifier.pointerInput { detectTapGestures { … } }` über die ganze Fläche. Single-Tap → `showControls = !showControls`, Doppel-Tap bleibt Zoom-Logik (war schon da).
     - **Layer 4:** `AnimatedVisibility(visible = showControls, enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(Dimensions.PanelSlideDuration, easing = FastOutSlowInEasing)), exit = slideOutHorizontally(targetOffsetX = { it }, …))` mit dem Steuer-Panel. Panel-Modifier: `Modifier.align(Alignment.CenterEnd).width(Dimensions.PanelWidth).fillMaxHeight().padding(Dimensions.PanelEdgePadding)`

3. **Auto-Hide-Timer:**
   ```kotlin
   var showControls by remember { mutableStateOf(false) }
   var lastInteractionMs by remember { mutableLongStateOf(0L) }

   LaunchedEffect(showControls, lastInteractionMs) {
       if (showControls) {
           delay(Dimensions.ControlsAutoHideMs)
           if (System.currentTimeMillis() - lastInteractionMs >= Dimensions.ControlsAutoHideMs) {
               showControls = false
           }
       }
   }
   ```
   Jeder Klick auf einen Button im Panel setzt `lastInteractionMs = System.currentTimeMillis()` — verhindert dass das Panel mitten in der Bedienung verschwindet.

4. **Steuer-Panel-Inhalt:** Status, Sonde (4 Buttons), Licht (8 Buttons), Meterzähler-Reset, „Neu verbinden" — gleiche Komponenten wie bisher, nur in der neuen Container-Struktur.

### Akzeptanzkriterien W1
- [ ] Build kompiliert (`./gradlew compileDebugKotlin` → BUILD SUCCESSFUL)
- [ ] In `RESULT_TOUCHUI_W1.md`: Liste der geänderten Files, Diff-Summary, Compile-Status, ggf. Issues
- [ ] Commit auf `feature/touchui-cinema-mode` mit Message `feat(ui): Cinema-Mode-Layout für InspectionScreen (W1)`

---

## W2 — Touch-optimierte Button-Größen

**Ziel:** Alle Buttons im Cinema-Mode-Panel haben handschuh-taugliche Größen. Mindestens 72 dp Höhe für Primary, 56 dp für Sekundär, 12 dp Abstand.

### Files zu ändern
- `app/src/main/java/com/uip/oneapp/ui/screens/inspection/InspectionScreen.kt` — Button-Modifier ändern
- ggf. ein neues `app/src/main/java/com/uip/oneapp/ui/components/inspection/SondeFrequencyPicker.kt` und `LightStepPicker.kt` (Refactor in eigene Composables für Übersichtlichkeit, optional)

### Vorgehen

1. **Sonde-Buttons (AUS / 512 / 640 / 33k):**
   - 4 Buttons in einer Spalte, je Button `Modifier.fillMaxWidth().height(Dimensions.TouchLarge)`
   - Abstand: `verticalArrangement = Arrangement.spacedBy(Dimensions.TouchSpacing)`
   - Text: `fontSize = Dimensions.ButtonLabelFontSize`, `fontWeight = FontWeight.SemiBold`

2. **Licht-Stufen (0/25/50/75/100/125/150/200):**
   - 8 Buttons in 2 Zeilen à 4 (`Row` × 2), je Button `Modifier.weight(1f).height(Dimensions.TouchLarge)`
   - Spacing zwischen Buttons und zwischen Zeilen: `Dimensions.TouchSpacing`
   - Aktive Stufe (Toleranzfenster ±12 wie heute) klar hervorgehoben (Primary-Farbe)

3. **Meterzähler „Auf 0 setzen":**
   - Einzelner Button, `Modifier.fillMaxWidth().height(80.dp)` (extra fett, da häufig benutzt)
   - Icon + Text, `fontSize = Dimensions.ButtonLabelFontSize`

4. **„Neu verbinden":**
   - Sekundärbutton, `Modifier.fillMaxWidth().height(Dimensions.TouchMedium)`

5. **Status-Bereich (Verbunden/Firmware/TX-RX):**
   - Keine Buttons, nur Text. `fontSize = 14.sp` für Labels, `18.sp` für Status-Wert.

### Akzeptanzkriterien W2
- [ ] Build kompiliert
- [ ] In `RESULT_TOUCHUI_W2.md`: vorher/nachher Button-Größen, Files
- [ ] Keine `dp`/`sp`-Magic-Numbers in `InspectionScreen.kt` — alles über `Dimensions.*`
- [ ] Commit `feat(ui): touch-optimierte Button-Größen (W2)`

---

## W3 — OSD-Overlay auf Video (groß, persistent, lesbar)

**Ziel:** Distanz/Sonde/Licht/Spannung sind **immer** über dem Live-Bild zu sehen, auch wenn das Steuer-Panel aus ist. Hauptdistanz prominent (96 sp), Sekundärzeile dezent (20 sp), Schatten oder Outline für Lesbarkeit gegen helle Inspektionsbilder.

### Files zu ändern
- `app/src/main/java/com/uip/oneapp/ui/components/InspectionOsd.kt` — neu
- `app/src/main/java/com/uip/oneapp/ui/screens/inspection/InspectionScreen.kt` — integriert die neue Komponente
- ggf. `app/src/main/java/com/uip/oneapp/ui/components/LocalBitmapVideoPlayer.kt` — bekommt einen OSD-Slot via Lambda

### Vorgehen

1. **`InspectionOsd.kt` anlegen** als eigenes Composable:
   ```kotlin
   @Composable
   fun InspectionOsd(
       distanceMeters: Float,
       sondeMode: String,
       lightLevel: Int,
       voltage: Float,
       modifier: Modifier = Modifier
   ) {
       Column(modifier = modifier) {
           ShadowedText(
               text = "%.2f m".format(displayDistance(distanceMeters)),
               fontSize = Dimensions.OsdDistanceFontSize,
               fontWeight = FontWeight.Bold
           )
           ShadowedText(
               text = "Sonde: $sondeMode  ·  Licht: $lightLevel",
               fontSize = Dimensions.OsdSecondaryFontSize
           )
           if (voltage > 0f) {
               ShadowedText(
                   text = "%.1f V".format(voltage),
                   fontSize = Dimensions.OsdSmallFontSize
               )
           }
       }
   }

   @Composable
   private fun ShadowedText(text: String, fontSize: TextUnit, fontWeight: FontWeight = FontWeight.Normal) {
       // Trick: Text zweimal rendern — schwarz mit Offset (Schatten) + weiß oben drauf
       Box {
           Text(text, color = Color.Black, fontSize = fontSize, fontWeight = fontWeight,
                modifier = Modifier.offset(Dimensions.OsdShadowOffset, Dimensions.OsdShadowOffset))
           Text(text, color = Color.White, fontSize = fontSize, fontWeight = fontWeight)
       }
   }
   ```

2. **In `InspectionScreen.kt`** im Box-Layout den OSD-Layer hinzufügen:
   ```kotlin
   InspectionOsd(
       distanceMeters = meterValue,
       sondeMode = crawler.sondeFrequency ?: "—",
       lightLevel = crawler.frontLightPower ?: 0,
       voltage = cable.batteryLevel?.let { it / 100f * 12.6f } ?: 0f,
       modifier = Modifier
           .align(Alignment.BottomStart)
           .padding(24.dp)
   )
   ```
   **OSD ist nicht in der `AnimatedVisibility`** — bleibt immer sichtbar.

3. **Distanz-Glättung** (aus Smoke-Test übernommen): Werte mit `|d| < 0.005 f` snappen auf `0.0f`, damit kein `-0.00 m`-Flackern.

### Akzeptanzkriterien W3
- [ ] Build kompiliert
- [ ] OSD ist sichtbar wenn `showControls = false` (Cinema-Mode pur)
- [ ] In `RESULT_TOUCHUI_W3.md`: Screenshot-Plan oder Beschreibung wo das OSD positioniert ist
- [ ] Commit `feat(ui): persistentes OSD-Overlay mit Schatten (W3)`

---

## W4 — Build, Deploy, Verify, Commit & Push

**Ziel:** APK bauen, auf der ONE-Hardware installieren, mit Logcat-Sanity-Check verifizieren, dann den Feature-Branch nach Master mergen und pushen.

### Vorgehen

1. **Build:** `./gradlew assembleDebug` → BUILD SUCCESSFUL.

2. **Deploy:**
   ```powershell
   $SER = "233b4bd2865177ed"
   adb -s $SER shell am force-stop com.uip.drainq.one
   adb -s $SER shell am force-stop com.bominwell.minipush
   adb -s $SER install -r app\build\outputs\apk\debug\app-debug.apk
   adb -s $SER shell am start -n com.uip.drainq.one/com.uip.oneapp.MainActivity
   ```

3. **Verify per Logcat (15 s warten):**
   ```powershell
   adb -s $SER logcat -c
   Start-Sleep 15
   adb -s $SER logcat -d DeviceFilePermission:V OneInternalHW:V V4L2Bridge:V AndroidRuntime:E *:S | Select-Object -First 60
   ```
   Erwartet:
   - `DeviceFilePermission: Device files already RW-accessible` ODER `chmod via su OK`
   - `OneInternalHW: startPolling: opening …` (wenn Inspection-Screen geöffnet)
   - `V4L2Bridge: Stream started with 4 buffers`
   - **Keine** `AndroidRuntime: FATAL EXCEPTION`

4. **Merge in Master:**
   ```powershell
   git checkout master
   git merge --no-ff feature/touchui-cinema-mode -m "merge: feature/touchui-cinema-mode (Cinema-Mode + Touch-Optimierung)"
   git push origin master
   ```

5. **`RESULT_TOUCHUI_W4.md`** schreiben:
   - APK-Pfad und -Größe
   - Logcat-Ausschnitt
   - Commit-Hashes der vier Wellen
   - Bekannte Issues, falls vorhanden
   - Bilanz: wie viele Files insgesamt geändert, Insertions/Deletions

### Akzeptanzkriterien W4
- [ ] APK installiert und gestartet ohne `FATAL EXCEPTION`
- [ ] Logcat zeigt erwartete Hardware-Initialisierung
- [ ] Master-Branch hat den Merge-Commit
- [ ] Push auf `origin master` erfolgreich

---

## Pre-Flight-Pflichten (im Skript)

Jede Welle wird durch `autorun_touchui.ps1` gestartet. Das Skript:

1. Prüft `JAVA_HOME` ist gesetzt (sonst auf `C:\Android\jdk17` defaulten)
2. Prüft `gradlew` Datei existiert
3. Prüft Plan-MD (`AUTORUN_TOUCHUI_PLAN.md`) existiert
4. Prüft Tablet-State über ADB (für W0 und W4)
5. Prüft nach jeder Welle das `RESULT_TOUCHUI_W{N}.md`-File — fehlt es → Abbruch
6. Schreibt alles in `autorun_touchui.log`

---

## Result-File-Schema (jeder Welle)

`RESULT_TOUCHUI_W{N}.md` enthält:

```markdown
# Welle W{N} — {Titel}

**Stand:** YYYY-MM-DD HH:MM
**Branch:** feature/touchui-cinema-mode
**Commit:** {sha}

## Geänderte Files
- {pfad} ({+lines}/-{lines})

## Diff-Summary
{kurz, max 5 Punkte}

## Compile-Status
- `compileDebugKotlin`: BUILD SUCCESSFUL / FAILED
- ggf. Fehler-Tail

## Akzeptanzkriterien
- [x] / [ ] {Kriterium}

## Bekannte Issues
- {falls vorhanden}

## Übergabe an nächste Welle
- {was die nächste Welle wissen muss}
```

---

## Skill-Reference

- **`autorun_phasenplan_pattern`** (memory/MEMORY.md) — Generelles Pattern, Modell-Auswahl, Pre-Flight
- **`drainq-kritis-compliance`** — bei jeder Code-Änderung konsultieren (steht in CLAUDE.md des Repos)
- **`design:design-critique`** — optional in W1/W2/W3 für UX-Review-Pass (eigentlich für Phase nach W4)

---

## Bekannte Risiken

| Risiko | Mitigation |
|---|---|
| Phase-8-libVLC-Branch hat parallele Änderungen in `InspectionScreen.kt` | Master-Stand prüfen; ggf. nach W3 rebasen statt merge |
| ExoPlayer-Player-Container reagiert beim Layout-Refactor unerwartet (TextureView-Lifecycle) | W1-Compile-Test, ggf. Logcat-Check direkt nach W1 |
| OSD-Distanz 96sp ist zu groß auf kleinerem Bildschirm | In `Dimensions.kt` bei Bedarf reduzieren, ist ein zentraler Hebel |
| Auto-Hide stört in Edge-Cases (z. B. lange Sonde-Frequenz-Auswahl) | Akzeptanz: jeder Button-Click resettet den Timer auf 5 s |
