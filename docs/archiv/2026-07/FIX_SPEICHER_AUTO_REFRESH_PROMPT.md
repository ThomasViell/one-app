/clean
/goal: Speicheranzeige aktualisiert bei USB-Stecken/-Ziehen automatisch (Media-Broadcast) + manuelles Refresh-Icon
/model: sonnet
/effort: mittel

# Reparaturauftrag — Speicheranzeige: Auto-Refresh bei USB-Wechsel + Refresh-Button

Quelle: Gerätetest 0.5.12. Speicher-Karte funktioniert, aktualisiert sich aber NICHT, wenn der USB-Stick bei offenem Home gesteckt/gezogen wird (Refresh nur bei Entry + ON_RESUME). Nur HomeScreen.kt anfassen.
Branch: `feature/dual-mode`. Kein Merge, kein Tag.

## Ist-Stand (Anker) — `ui/screens/home/HomeScreen.kt`
- `var storageRefreshTick by remember { mutableIntStateOf(0) }`
- `LaunchedEffect(storageRefreshTick) { withContext(IO) { … Intern + USB neu erheben … } }`
- `DisposableEffect(lifecycleOwner) { … ON_RESUME → storageRefreshTick++ … }`
- `StorageCard(internal = storageInternal, usb = storageUsb)` mit Titelzeile (Icon „save" + `S("storage_title")`).

## Fix

### 1) Auto-Refresh bei USB-Mount/Unmount (BroadcastReceiver)
Neben dem bestehenden ON_RESUME-`DisposableEffect` einen zweiten `DisposableEffect(context)` ergänzen, der einen `BroadcastReceiver` auf die Storage-Events registriert und bei jedem Event `storageRefreshTick++` setzt:
```kotlin
DisposableEffect(context) {
    val receiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(ctx: android.content.Context?, intent: android.content.Intent?) {
            storageRefreshTick++
        }
    }
    val filter = android.content.IntentFilter().apply {
        addAction(android.content.Intent.ACTION_MEDIA_MOUNTED)
        addAction(android.content.Intent.ACTION_MEDIA_UNMOUNTED)
        addAction(android.content.Intent.ACTION_MEDIA_EJECT)
        addAction(android.content.Intent.ACTION_MEDIA_REMOVED)
        addAction(android.content.Intent.ACTION_MEDIA_BAD_REMOVAL)
        addDataScheme("file")   // Media-Broadcasts tragen eine file-URI → Pflicht, sonst kommt nichts an
    }
    androidx.core.content.ContextCompat.registerReceiver(
        context, receiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
    )
    onDispose { runCatching { context.unregisterReceiver(receiver) } }
}
```
- `RECEIVER_NOT_EXPORTED` ist für System-Broadcasts korrekt (targetSdk 34) und liefert sie trotzdem.
- Der Mount-Broadcast kommt teils minimal vor dem Verfügbar-Werden des Volumes; die vorhandene `runCatching`-Erhebung fängt das ab, und ein Unmount setzt USB ohnehin auf null.

### 2) Manuelles Refresh-Icon in der Speicher-Karte
`StorageCard` um einen `onRefresh: () -> Unit`-Parameter erweitern; im Aufruf `onRefresh = { storageRefreshTick++ }` übergeben. Die Titelzeile zu einer `Row(fillMaxWidth, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = CenterVertically)` machen: links weiterhin `DqIcon("save")` + `S("storage_title")`, rechts ein `IconButton(onClick = onRefresh) { DqIcon("refresh", tint = c.textSecondary) }`. (`DqIcon("refresh")` existiert bereits, z. B. im Settings-PresetEditor.)

## Verifikation (Pflicht am Gerät)
- USB-Stick bei offenem Home stecken → USB-Balken erscheint binnen ~1 s automatisch; ziehen → wechselt automatisch auf „Kein USB-Stick".
- Refresh-Icon antippen → Werte werden sofort neu erhoben (Intern + USB).
- Kein Crash beim Ein-/Ausstecken; Verlassen/Wiederkehren unverändert ok.

## Nicht anfassen
Die Erhebungslogik selbst (`StatFs`/`findUsbVolumes`), `StorageInfo.kt`, KPI-Row, restlicher Home-Screen.
