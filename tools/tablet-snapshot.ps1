# =============================================================================
# DrainQ ONE - Schneller Tablet-Snapshot
# Macht ein Screenshot + zieht die letzten 200 Logcat-Zeilen.
# Output landet in debug-logs\snapshots\<timestamp>\
# Ideal fuer "Claude, schau mal" - kein Mirror noetig.
# =============================================================================

param([string]$DeviceSerial = "")

$ProjectRoot = "C:\Projekte\drainq.one"
$SnapDir = Join-Path $ProjectRoot "debug-logs\snapshots\$(Get-Date -Format 'yyyy-MM-dd_HH-mm-ss')"
New-Item -ItemType Directory -Force -Path $SnapDir | Out-Null

$devicesRaw = & adb devices
$devices = @()
foreach ($line in $devicesRaw) {
    $lineStr = [string]$line
    if ($lineStr -match "^List of devices" -or [string]::IsNullOrWhiteSpace($lineStr)) { continue }
    if ($lineStr -match "^(\S+)\s+device\s*$") { $devices += $matches[1] }
}
if ($devices.Count -eq 0) { Write-Host "Kein Geraet." -ForegroundColor Red; exit 1 }
if (-not $DeviceSerial) { $DeviceSerial = $devices[0] }

Write-Host "Snapshot Geraet $DeviceSerial -> $SnapDir" -ForegroundColor Cyan

# Screenshot
& adb -s $DeviceSerial exec-out screencap -p > "$SnapDir\screen.png"

# Letzte 200 Zeilen Logcat (alle Tags, nur Errors getrennt)
& adb -s $DeviceSerial logcat -d -t 200 | Out-File "$SnapDir\logcat-tail.log"
& adb -s $DeviceSerial logcat -d -b crash | Out-File "$SnapDir\crash-buffer.log"

# Aktive Activity
& adb -s $DeviceSerial shell dumpsys activity activities | Select-String "mResumedActivity|topResumedActivity" | Out-File "$SnapDir\current-activity.txt"

Write-Host "Fertig. Pfad in Zwischenablage." -ForegroundColor Green
$SnapDir | Set-Clipboard
