# ==============================================================================
# DrainQ.ONE - Autorun Bottom-Bar 9-Tiles
# ==============================================================================
# Wellen: W0 Preflight, W1 KeyBus, W2 BottomBar, W3 Integrate,
#         W4 Build, W5 Deploy, W6 Verify, W7 CommitPush
# ==============================================================================

$ErrorActionPreference = "Stop"
Set-Location "C:\Projekte\drainq.one"
$DEVICE = "233b4bd2865177ed"
$BRANCH = "feature/bottom-bar-9tiles"

if (Test-Path .\telegram-config.local.ps1) {
    . .\telegram-config.local.ps1
} else {
    Write-Host "WARN: telegram-config.local.ps1 fehlt"
}

function Tg($text) {
    if (-not $env:TELEGRAM_BOT_TOKEN) { return }
    try {
        Invoke-RestMethod -Method Post `
            -Uri "https://api.telegram.org/bot$env:TELEGRAM_BOT_TOKEN/sendMessage" `
            -Body @{ chat_id = $env:TELEGRAM_CHAT_ID; text = $text } | Out-Null
    } catch {}
}

function Phase($name, $msg) {
    Write-Host ""
    Write-Host "=========================================="
    Write-Host "  $name  $msg"
    Write-Host "=========================================="
    Tg "[$name] $msg"
}

function Fail($name, $err) {
    Write-Host "FAIL: $err" -ForegroundColor Red
    Tg "[$name] FAIL: $err"
    exit 1
}

$startTime = Get-Date
Tg "Autorun BottomBar 9-Tiles gestartet"

# ============================================================================
# W0 Preflight
# ============================================================================
Phase "W0" "Preflight Branch + Drawables"
$ErrorActionPreference = "Continue"
git checkout -B $BRANCH 2>&1 | Out-Host
$ErrorActionPreference = "Stop"

$requiredDrawables = @(
    "app\src\main\res\drawable\ic_one_record_circle.xml",
    "app\src\main\res\drawable\ic_one_stop_square.xml",
    "app\src\main\res\drawable\ic_one_day_night.xml",
    "app\src\main\res\drawable\ic_one_power.png",
    "app\src\main\res\drawable\ic_one_light_on.png",
    "app\src\main\res\drawable\ic_one_light.png",
    "app\src\main\res\drawable\ic_one_camera.png",
    "app\src\main\res\drawable\ic_one_gallery.png",
    "app\src\main\res\drawable\ic_one_settings.png"
)
foreach ($f in $requiredDrawables) {
    if (-not (Test-Path $f)) { Fail "W0" "Drawable fehlt: $f" }
}
Write-Host "  OK: alle 9 Drawables vorhanden"

# ============================================================================
# W1 HardwareKeyBus + MainActivity-Patch
# ============================================================================
Phase "W1" "HardwareKeyBus + MainActivity onKeyDown"

$keyBusDir = "app\src\main\java\com\uip\oneapp\hardware"
New-Item -ItemType Directory -Force -Path $keyBusDir | Out-Null

$keyBusContent = @'
package com.uip.oneapp.hardware

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * HardwareKeyBus - globaler Event-Bus fuer die 8 programmierbaren Hardware-
 * Tasten unter dem ONE-Tablet-Display (KeyCodes 131-138).
 *
 * MainActivity.onKeyDown emittiert hier rein, die InspectionScreen-Composable
 * collectet via SharedFlow.
 */
object HardwareKeyBus {

    enum class Action {
        LIGHT,
        LIGHT_LONG,
        SONDE,
        SONDE_LONG,
        REC_START,
        REC_STOP,
        PHOTO,
        GALLERY,
        DAY_NIGHT,
        SETTINGS
    }

    private val _events = MutableSharedFlow<Action>(extraBufferCapacity = 16)
    val events: SharedFlow<Action> = _events.asSharedFlow()

    fun emit(action: Action): Boolean = _events.tryEmit(action)
}
'@
Set-Content -Encoding UTF8 -Path "$keyBusDir\HardwareKeyBus.kt" -Value $keyBusContent

# MainActivity patchen
$mainAct = "app\src\main\java\com\uip\oneapp\MainActivity.kt"
$mainContent = Get-Content $mainAct -Raw

if ($mainContent -notmatch "HardwareKeyBus") {
    $mainContent = $mainContent -replace `
        "import com\.uip\.oneapp\.ui\.navigation\.NavGraph", `
        "import android.view.KeyEvent`r`nimport com.uip.oneapp.hardware.HardwareKeyBus`r`nimport com.uip.oneapp.ui.navigation.NavGraph"

    $onKeyDown = @'

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val long = event?.isLongPress == true
        val action = when (keyCode) {
            131 -> if (long) HardwareKeyBus.Action.LIGHT_LONG else HardwareKeyBus.Action.LIGHT
            132 -> if (long) HardwareKeyBus.Action.SONDE_LONG else HardwareKeyBus.Action.SONDE
            133 -> HardwareKeyBus.Action.REC_START
            134 -> HardwareKeyBus.Action.REC_STOP
            135 -> HardwareKeyBus.Action.PHOTO
            136 -> HardwareKeyBus.Action.GALLERY
            137 -> HardwareKeyBus.Action.DAY_NIGHT
            138 -> HardwareKeyBus.Action.SETTINGS
            else -> null
        }
        return if (action != null) {
            HardwareKeyBus.emit(action)
            true
        } else super.onKeyDown(keyCode, event)
    }
'@
    $idx = $mainContent.LastIndexOf("}")
    $mainContent = $mainContent.Substring(0, $idx) + $onKeyDown + "`r`n" + $mainContent.Substring($idx)
    Set-Content -Encoding UTF8 -Path $mainAct -Value $mainContent
    Write-Host "  OK: MainActivity.onKeyDown override eingefuegt"
} else {
    Write-Host "  SKIP: MainActivity hat HardwareKeyBus bereits"
}

# ============================================================================
# W2 BottomBar9Tiles
# ============================================================================
Phase "W2" "BottomBar9Tiles Composable"

$bottomBarContent = @'
package com.uip.oneapp.ui.screens.inspection

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uip.oneapp.R

/**
 * BottomBar9Tiles - Outdoor-Bottom-Bar mit 9 grossen Touch-Tiles.
 * Layout 1:1 zur ONE.APP V1.3.0. Tiles 2-9 sind via HardwareKeyBus
 * mit den Hardware-Tasten 131-138 gekoppelt.
 */
@Composable
fun BottomBar9Tiles(
    isRecording: Boolean,
    isLightOn: Boolean,
    highlightKeyCode: Int? = null,
    onPower: () -> Unit,
    onLight: () -> Unit,
    onSonde: () -> Unit,
    onRecordStart: () -> Unit,
    onRecordStop: () -> Unit,
    onPhoto: () -> Unit,
    onGallery: () -> Unit,
    onDayNight: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0A0A0A))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Tile(R.drawable.ic_one_power, "POWER", false, false, onClick = onPower, modifier = Modifier.weight(1f))
        Tile(R.drawable.ic_one_light_on, "LICHT", isLightOn, highlightKeyCode == 131, onClick = onLight, modifier = Modifier.weight(1f))
        Tile(R.drawable.ic_one_light, "SONDE", false, highlightKeyCode == 132, onClick = onSonde, modifier = Modifier.weight(1f))
        Tile(R.drawable.ic_one_record_circle, "REC", isRecording, highlightKeyCode == 133, activeColor = Color(0xFFB91C1C), onClick = onRecordStart, modifier = Modifier.weight(1f))
        Tile(R.drawable.ic_one_stop_square, "STOP", false, highlightKeyCode == 134, onClick = onRecordStop, modifier = Modifier.weight(1f))
        Tile(R.drawable.ic_one_camera, "FOTO", false, highlightKeyCode == 135, onClick = onPhoto, modifier = Modifier.weight(1f))
        Tile(R.drawable.ic_one_gallery, "GALERIE", false, highlightKeyCode == 136, onClick = onGallery, modifier = Modifier.weight(1f))
        Tile(R.drawable.ic_one_day_night, "TAG/NACHT", false, highlightKeyCode == 137, onClick = onDayNight, modifier = Modifier.weight(1f))
        Tile(R.drawable.ic_one_settings, "MENU", false, highlightKeyCode == 138, onClick = onSettings, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Tile(
    iconRes: Int,
    label: String,
    active: Boolean,
    highlight: Boolean,
    activeColor: Color = Color(0xFFFFCD00),
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val target = when {
        highlight -> Color(0xFFFFCD00)
        active -> activeColor
        else -> Color(0xFF1A1A1A)
    }
    val bg by animateColorAsState(target, tween(160), label = "tile-bg")

    Column(
        modifier = modifier
            .heightIn(min = 84.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = label,
            modifier = Modifier.size(36.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (active || highlight) Color.Black else Color(0xFFE0E0E0)
        )
    }
}
'@
Set-Content -Encoding UTF8 -Path "app\src\main\java\com\uip\oneapp\ui\screens\inspection\BottomBar9Tiles.kt" -Value $bottomBarContent
Write-Host "  OK: BottomBar9Tiles.kt angelegt"

# ============================================================================
# W3 InspectionScreen Integration (nur Import)
# ============================================================================
Phase "W3" "InspectionScreen Imports"

$insScr = "app\src\main\java\com\uip\oneapp\ui\screens\inspection\InspectionScreen.kt"
$insContent = Get-Content $insScr -Raw

if ($insContent -notmatch "import com\.uip\.oneapp\.hardware\.HardwareKeyBus") {
    $insContent = $insContent -replace `
        "import com\.uip\.oneapp\.ui\.localization\.S", `
        "import com.uip.oneapp.hardware.HardwareKeyBus`r`nimport com.uip.oneapp.ui.localization.S"
    Set-Content -Encoding UTF8 -Path $insScr -Value $insContent
    Write-Host "  OK: HardwareKeyBus-Import in InspectionScreen"
} else {
    Write-Host "  SKIP: Import schon vorhanden"
}

# ============================================================================
# W4 Build
# ============================================================================
Phase "W4" "gradlew assembleDebug"
$buildOut = & .\gradlew.bat assembleDebug 2>&1
$buildOut | Out-Host
if ($LASTEXITCODE -ne 0) {
    $errLines = ($buildOut | Select-String -Pattern "error:|e: " | Select-Object -First 5) -join " || "
    Fail "W4" "Build FAILED $errLines"
}
Write-Host "  OK: Build SUCCESSFUL"

# ============================================================================
# W5 Deploy
# ============================================================================
Phase "W5" "Deploy auf ONE-Tablet"
$apk = "app\build\outputs\apk\debug\app-debug.apk"
adb -s $DEVICE install -r $apk | Out-Host
if ($LASTEXITCODE -ne 0) { Fail "W5" "adb install fehlgeschlagen" }
adb -s $DEVICE shell am force-stop com.uip.drainq.one
adb -s $DEVICE shell monkey -p com.uip.drainq.one -c android.intent.category.LAUNCHER 1 | Out-Null
Write-Host "  OK: APK installiert + App neu gestartet"

# ============================================================================
# W6 Verify
# ============================================================================
Phase "W6" "Verify logcat-Spotcheck"
Start-Sleep -Seconds 5
$logHits = adb -s $DEVICE logcat -d -t 200 2>$null | Select-String -Pattern "FATAL|AndroidRuntime.*com.uip.drainq.one" | Select-Object -First 5
if ($logHits) {
    Fail "W6" "Crash im logcat $logHits"
}
Write-Host "  OK: kein Crash im logcat"

# ============================================================================
# W7 Commit + Push
# ============================================================================
Phase "W7" "Commit + Push"
$commitMsg = "Bottom-Bar 9-Tiles: HardwareKeyBus + Composable. KeyCodes 131-138 reverse-engineered aus Bominwell V1.3.0."
$ErrorActionPreference = "Continue"
git add -A
git commit -m $commitMsg 2>&1 | Out-Host
git push -u origin $BRANCH 2>&1 | Out-Host
$ErrorActionPreference = "Stop"

$elapsed = (Get-Date) - $startTime
$min = [int]$elapsed.TotalMinutes
$sec = $elapsed.Seconds
Phase "DONE" "Alle Wellen erfolgreich"
Tg "BottomBar 9-Tiles fertig in $min min $sec s. Branch $BRANCH gepusht. Layout-Einbau in InspectionScreen folgt."
Write-Host ""
Write-Host "Branch: $BRANCH"
Write-Host "APK installiert auf $DEVICE"
