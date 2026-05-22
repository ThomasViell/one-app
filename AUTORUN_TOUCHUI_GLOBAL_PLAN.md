# Autorun-Phasenplan — Touch-UI global über alle Views

**Stand:** 2026-05-19, Folge zu `AUTORUN_TOUCHUI_PLAN.md` (W0–W4 abgeschlossen)
**Branch:** `feature/touchui-global` (frisch aus master)
**Skill:** `autorun_phasenplan_pattern`

---

## Ziel

W1–W4 hat nur den `InspectionScreen` auf Cinema-Mode + Touch umgestellt. Jetzt:
1. **Alle anderen Views** auf gleiche Touch-Standards bringen (Buttons 72 dp / 56 dp, Spacing 12 dp, Labels 18 sp+)
2. **Navigation Rail** (Home / Inspektion / Projekte / Einstellungen) groß und gut bedienbar machen
3. **InspectionScreen-Verfeinerung:** Licht → Slider, Sonde → durchtoggelnder Button (statt 4 Einzelbuttons)

Bezug auf das `Dimensions.kt`-Theme aus W2 — keine neuen Magic-Numbers, alles zentral.

---

## Design-Tokens — Erweiterungen

`app/src/main/java/com/uip/oneapp/ui/theme/Dimensions.kt` bekommt zusätzlich:

```kotlin
// Navigation Rail
val NavRailItemHeight = 80.dp
val NavRailIconSize = 40.dp
val NavRailLabelFontSize = 16.sp
val NavRailIndicatorWidth = 56.dp

// List & Cards
val CardMinHeight = 72.dp          // typische Touch-Höhe für ListItems
val CardElevation = 2.dp
val SectionTitleFontSize = 22.sp   // Headlines in Screens
val BodyFontSize = 16.sp           // Body-Text minimum

// Inputs
val InputHeight = 56.dp
val InputFontSize = 18.sp

// Dialoge
val DialogCloseIconSize = 40.dp
val DialogButtonHeight = 56.dp

// Slider (Licht im InspectionScreen)
val SliderThumbSize = 32.dp        // großer Thumb für Drag
val SliderTrackHeight = 12.dp
```

Wenn ein Token doppelt definiert wird (z. B. weil schon ähnlich vorhanden) → konsolidieren, nicht duplizieren.

---

## Wellen-Übersicht

| Welle | Beschreibung | Dauer-Schätzung | Modell |
|---|---|---|---|
| W0 | Pre-Flight (im Skript) | 2 min | – |
| W5 | Navigation Rail | 10 min | sonnet |
| W6 | Home + Projects + ProjectForm + ProjectDetail | 25 min | sonnet (think) |
| W7 | Settings + Connection + OfflineMaps + Splash | 20 min | sonnet (think) |
| W8 | Dialoge (Damage, VideoPlayback, PdfPreview, MapPicker) | 15 min | sonnet |
| W9 | InspectionScreen Refinement (Slider + Toggle) | 15 min | sonnet |
| W10 | Build, Deploy, Verify, Merge, Push | 10 min | haiku |
| **Summe** | | **~97 min** | |

Jede Welle:
- Liest `RESULT_TOUCHUI_GLOBAL_W{N-1}.md` (außer W5)
- Schreibt `RESULT_TOUCHUI_GLOBAL_W{N}.md` nach dem unten beschriebenen Schema
- Compile-Pflicht: `./gradlew compileDebugKotlin` → BUILD SUCCESSFUL
- Commit auf `feature/touchui-global` mit Commit-Message nach Welle

---

## W0 — Pre-Flight

Das Skript prüft:
1. `AUTORUN_TOUCHUI_GLOBAL_PLAN.md` (diese Datei) existiert
2. `gradlew.bat` da
3. `claude` CLI im PATH
4. Master enthält Merge `7272b15` von Welle W4 (Touch-UI Cinema-Mode)
5. `compileDebugKotlin` läuft sauber durch
6. Tablet `233b4bd2865177ed` erreichbar
7. Branch `feature/touchui-global` aus master ausgecheckt (oder vorhanden)

---

## W5 — Navigation Rail Touch-optimieren

**Ziel:** Die linke `NavigationRail` mit Home/Inspektion/Projekte/Einstellungen ist mit Handschuhen sicher bedienbar.

**Files:**
- `app/src/main/java/com/uip/oneapp/ui/navigation/NavGraph.kt`
- `app/src/main/java/com/uip/oneapp/ui/theme/Dimensions.kt` (Tokens erweitern)

**Vorgehen:**
1. Tokens ergänzen (siehe „Design-Tokens" oben)
2. `NavigationRailItem`-Aufrufe: `Modifier.height(Dimensions.NavRailItemHeight)`, Icon mit `Modifier.size(Dimensions.NavRailIconSize)`, Label mit `fontSize = Dimensions.NavRailLabelFontSize, fontWeight = FontWeight.SemiBold`
3. `NavigationRail`-Container hat `Modifier.width(96.dp)` (oder Token), damit der größere Inhalt reinpasst
4. Indicator-Pille (selektierter Item): über `colors`-Parameter oder Custom-Composable verbreitern, falls Material-3-Default zu schmal

**Akzeptanz:**
- [ ] Compile OK
- [ ] Tap-Bereich pro Nav-Item ≥ 80 dp hoch
- [ ] Icons gut erkennbar
- [ ] Labels lesbar
- [ ] Commit `feat(nav): Navigation Rail touch-optimiert (W5)`

---

## W6 — Home + Projects + ProjectForm + ProjectDetail

**Ziel:** Die vier zentralen Bedien-Screens haben großzügige Buttons, Karten mit guter Tap-Höhe, lesbare Headlines.

**Files:**
- `ui/screens/home/HomeScreen.kt`
- `ui/screens/projects/ProjectsScreen.kt` (falls vorhanden — sonst ProjectFormScreen)
- `ui/screens/projects/ProjectFormScreen.kt`
- `ui/screens/projects/ProjectFormViewModel.kt` (nur falls nötig)
- `ui/screens/projectdetail/ProjectDetailScreen.kt`

**Vorgehen pro Screen:**
1. Headlines/Section-Titel auf `Dimensions.SectionTitleFontSize` (22 sp)
2. ListItems / Cards: `Modifier.heightIn(min = Dimensions.CardMinHeight)`, Innen-Padding `16.dp`
3. Buttons (Primary): `Modifier.height(Dimensions.TouchLarge)`, `fontSize = Dimensions.ButtonLabelFontSize`
4. Secondary-Buttons: `TouchMedium`
5. Input-Felder: `Modifier.height(Dimensions.InputHeight)`, `textStyle = TextStyle(fontSize = Dimensions.InputFontSize)`
6. Card-Spacing zwischen Listenelementen: `Dimensions.TouchSpacing` (12 dp)
7. Magic-Numbers entfernen — alle Dimensionen über `Dimensions.*`

**Akzeptanz:**
- [ ] Compile OK
- [ ] `grep -E '[0-9]+\.(dp|sp)' app/src/main/java/com/uip/oneapp/ui/screens/{home,projects,projectdetail}/*.kt` → 0 Treffer (außer in Kommentaren)
- [ ] Commit `feat(ui): Home + Projects + ProjectDetail touch-optimiert (W6)`

---

## W7 — Settings + Connection + OfflineMaps + Splash

**Files:**
- `ui/screens/settings/SettingsScreen.kt` (+ UpdateSection.kt)
- `ui/screens/connection/ConnectionScreen.kt`
- `ui/screens/offlinemaps/OfflineMapsScreen.kt`
- `ui/screens/splash/SplashScreen.kt`

**Vorgehen:**
1. Gleiche Touch-Standards wie W6
2. **`Switch`** (für `useHardwareOsd`, Theme-Toggles etc.): wrap in einem Touch-Target von `Dimensions.TouchMedium` Höhe (Switch selbst bleibt klein, aber die ganze Row ist tap-bar)
3. **Slider** (falls vorhanden, z. B. für Update-Channels o. ä.): Thumb-Size auf `Dimensions.SliderThumbSize`
4. **ConnectionScreen Migration-A-Aufräumen (optional):** Da `OneInternalHardwareService` keine WLAN-Discovery braucht, kann der „Scan"-Button und die manuelle RTSP-URL-Eingabe für `DeviceType.ONE` ausgeblendet werden. Für `DeviceType.TWO` bleibt's. Pragmatisch: Hide-Statt-Löschen, mit `if (deviceType != DeviceType.ONE)`.
5. **SplashScreen:** Größe der Logo-Anzeige + ggf. Text vergrößern

**Akzeptanz:**
- [ ] Compile OK
- [ ] Settings-Switches haben ≥ 56 dp Touch-Höhe (gesamte Row)
- [ ] Commit `feat(ui): Settings/Connection/OfflineMaps/Splash touch-optimiert (W7)`

---

## W8 — Dialoge

**Files:**
- `ui/screens/inspection/DamageDialog.kt`
- `ui/screens/projectdetail/VideoPlaybackDialog.kt`
- `ui/screens/projectdetail/PdfPreviewDialog.kt`
- `ui/screens/projects/MapPickerDialog.kt`
- `ui/components/UpdateDialog.kt` + `UpdateProgressDialog.kt`

**Vorgehen:**
1. AlertDialog/Modal-Buttons: `Modifier.height(Dimensions.DialogButtonHeight)`
2. Close-Icons (X oben rechts): `Modifier.size(Dimensions.DialogCloseIconSize)` (40 dp)
3. Input-Felder im Dialog: `Dimensions.InputHeight`
4. Body-Text: ≥ `Dimensions.BodyFontSize` (16 sp)
5. DamageDialog hat vermutlich Schadens-Picker-Listen — die Listen-Items ebenfalls min `Dimensions.CardMinHeight`

**Akzeptanz:**
- [ ] Compile OK
- [ ] Commit `feat(ui): Dialoge touch-optimiert (W8)`

---

## W9 — InspectionScreen Refinement: Licht-Slider + Sonde-Toggle

**Ziel:**
- **Licht:** 8 Buttons → 1 Material3-`Slider` (0..200) — fein einstellbar, Drag stabil dank 33 ms-State-Throttle aus dem Smoke-Test-Lessons
- **Sonde-Frequenz:** 4 Einzel-Buttons → 1 großer Toggle-Button der durchcyclet:
  AUS → 512 Hz → 640 Hz → 33 kHz → AUS
  Label des Buttons zeigt den **aktuellen** Modus an

**Files:**
- `ui/screens/inspection/InspectionScreen.kt` — das Steuer-Panel (die `AnimatedVisibility`-Sektion)

**Vorgehen für Slider:**
```kotlin
var sliderUi by remember { mutableFloatStateOf(state.lightLevel.coerceAtLeast(0).toFloat()) }
LaunchedEffect(state.lightLevel) {
    if (state.lightLevel >= 0) sliderUi = state.lightLevel.toFloat()
}
Column {
    Text("Licht: ${sliderUi.toInt()}", fontSize = Dimensions.BodyFontSize)
    Slider(
        value = sliderUi,
        onValueChange = { sliderUi = it; lastInteractionMs = System.currentTimeMillis() },
        onValueChangeFinished = { onLightChange(sliderUi.toInt()) },
        valueRange = 0f..200f,
        steps = 0,
        modifier = Modifier.heightIn(min = Dimensions.TouchLarge)
    )
}
```
**Wichtig:** Nur in `onValueChangeFinished` an die Hardware schicken — sonst flutet der User die serielle Schnittstelle beim Ziehen.

**Vorgehen für Sonde-Toggle:**
```kotlin
val nextMode = nextSondeMode(state.sondeMode)  // AUS→512→640→33k→AUS
Button(
    onClick = { onSondeModeChange(nextMode) },
    modifier = Modifier.fillMaxWidth().height(Dimensions.TouchLarge)
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Sonde: ${state.sondeMode.display}", fontSize = Dimensions.ButtonLabelFontSize, fontWeight = FontWeight.SemiBold)
        Text("Antippen für: ${nextMode.display}", fontSize = 12.sp, color = LocalContentColor.current.copy(alpha = 0.6f))
    }
}
```

Wenn `SondeMode` in der bestehenden DrainQ-Codebase noch nicht so heißt: passend zur lokalen API (`crawler.sondeFrequency` als String) implementieren — Hauptsache: ein Button, der durchschaltet und seinen aktuellen Zustand im Label zeigt.

**Akzeptanz:**
- [ ] Compile OK
- [ ] Slider beim Drag bewegt sich flüssig (im Auto-Test reicht: kompiliert; Hardware-Test in W10)
- [ ] Sonde-Toggle-Label zeigt aktuelle Frequenz UND nächste Frequenz (Discoverability)
- [ ] Commit `feat(ui): InspectionScreen Slider + Toggle (W9)`

---

## W10 — Build, Deploy, Verify, Merge, Push

1. `./gradlew assembleDebug` → BUILD SUCCESSFUL
2. `adb -s 233b4bd2865177ed install -r app/build/outputs/apk/debug/app-debug.apk`
3. `adb shell am force-stop com.uip.drainq.one && am start -n com.uip.drainq.one/com.uip.oneapp.MainActivity`
4. 15 s warten, Logcat → keine `FATAL EXCEPTION`
5. `git checkout master && git merge --no-ff feature/touchui-global -m "merge: feature/touchui-global (Touch-Optimierung über alle Views)"`
6. `git push origin master`
7. `RESULT_TOUCHUI_GLOBAL_W10.md` mit Commit-Hashes der W5–W9, Logcat-Ausschnitt, APK-Größe

---

## Result-File-Schema

`RESULT_TOUCHUI_GLOBAL_W{N}.md`:

```markdown
# Welle W{N} — {Titel}

**Stand:** YYYY-MM-DD HH:MM
**Branch:** feature/touchui-global
**Commit:** {sha}

## Geänderte Files
- {pfad} (+lines/-lines)

## Diff-Summary
{kurz, 3–5 Punkte}

## Compile-Status
- `compileDebugKotlin`: BUILD SUCCESSFUL / FAILED

## Akzeptanzkriterien
- [x] {Kriterium}

## Bekannte Issues
- {falls vorhanden}
```

---

## Bekannte Risiken

| Risiko | Mitigation |
|---|---|
| `NavigationRail` zu breit, Video-Bereich wird kleiner | Token `NavRailWidth = 96.dp` als Standard, kann später feinjustiert werden |
| `Slider`-Drag bricht wegen Recompose-Sturm wie im Smoke-Test | Achtung: drainq.one hat bereits einen 33ms-State-Publish-Throttle im OneInternalHardwareService — Drag wird funktionieren |
| `MapPickerDialog` mit MapsForge-View — Touch-Konflikte | Map-Touches haben Vorrang, nur Buttons-/Close-Bereich optimieren |
| Sub-Agent erzeugt versehentlich neuen Branch `feature` (Bug aus W1) | Skript prüft expliziten Branch-Namen vorher |
