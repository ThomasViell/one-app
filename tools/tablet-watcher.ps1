# =============================================================================
# DrainQ ONE - Tablet Snapshot Watcher
# Laeuft im Hintergrund. Wenn Claude eine Trigger-Datei schreibt, wird ein
# Snapshot ausgefuehrt und das Ergebnis nach <ProjectRoot>\debug-logs\claude-view\
# gespeichert. Claude liest dann das PNG direkt.
#
# Start  : .\tablet-watcher.ps1
# Beenden: STRG+C
# =============================================================================

param([string]$DeviceSerial = "")

$ProjectRoot = "C:\Projekte\drainq.one"
$TriggerFile = Join-Path $ProjectRoot ".claude-snapshot-trigger"
$ViewDir     = Join-Path $ProjectRoot "debug-logs\claude-view"
$StatusFile  = Join-Path $ViewDir "status.txt"

New-Item -ItemType Directory -Force -Path $ViewDir | Out-Null

# Geraet bestimmen
$devicesRaw = & adb devices
$devices = @()
foreach ($line in $devicesRaw) {
    $lineStr = [string]$line
    if ($lineStr -match "^List of devices" -or [string]::IsNullOrWhiteSpace($lineStr)) { continue }
    if ($lineStr -match "^(\S+)\s+device\s*$") { $devices += $matches[1] }
}
if ($devices.Count -eq 0) { Write-Host "Kein Geraet erkannt." -ForegroundColor Red; exit 1 }
if (-not $DeviceSerial) { $DeviceSerial = $devices[0] }

Write-Host "==> Tablet Snapshot Watcher" -ForegroundColor Cyan
Write-Host "    Geraet         : $DeviceSerial"
Write-Host "    Trigger-Datei  : $TriggerFile"
Write-Host "    Output         : $ViewDir"
Write-Host "    -> Claude erzeugt Trigger, ich liefere den Snapshot."
Write-Host "    Beenden: STRG+C"
Write-Host ""

# Initiales "ready"
"ready  $(Get-Date -Format 'HH:mm:ss')  device=$DeviceSerial" | Out-File $StatusFile

$counter = 0
while ($true) {
    if (Test-Path $TriggerFile) {
        $counter++
        $ts = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"
        Write-Host "[$ts] Trigger #$counter erkannt - erstelle Snapshot..." -ForegroundColor Yellow

        # Aktueller Snapshot (wird ueberschrieben - Claude liest immer screen.png)
        $screen = Join-Path $ViewDir "screen.png"
        $log    = Join-Path $ViewDir "logcat-tail.log"
        $crash  = Join-Path $ViewDir "crash-buffer.log"
        $act    = Join-Path $ViewDir "current-activity.txt"

        # Historie behalten
        $histDir = Join-Path $ViewDir "history\$ts"
        New-Item -ItemType Directory -Force -Path $histDir | Out-Null

        try {
            # WICHTIG: binaere Ausgabe NICHT via PowerShell '>' umleiten -
            # das macht UTF-16 daraus und zerstoert das PNG. cmd /c verwenden.
            $screenRaw = Join-Path $ViewDir "screen-raw.png"
            cmd /c "adb -s $DeviceSerial exec-out screencap -p > `"$screenRaw`""

            # Sicherheits-Reencode: max 1600px Breite, als JPEG (API-freundlich).
            Add-Type -AssemblyName System.Drawing
            $src = [System.Drawing.Image]::FromFile($screenRaw)
            $maxW = 1600
            if ($src.Width -gt $maxW) {
                $ratio = $maxW / $src.Width
                $nw = $maxW
                $nh = [int]($src.Height * $ratio)
            } else {
                $nw = $src.Width; $nh = $src.Height
            }
            $bmp = New-Object System.Drawing.Bitmap $nw, $nh
            $g = [System.Drawing.Graphics]::FromImage($bmp)
            $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
            $g.DrawImage($src, 0, 0, $nw, $nh)
            $g.Dispose(); $src.Dispose()

            # Als JPEG mit Q85 speichern (statt PNG) - API-kompatibel und klein.
            $screenJpg = [System.IO.Path]::ChangeExtension($screen, ".jpg")
            $encoder = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() |
                       Where-Object { $_.MimeType -eq "image/jpeg" }
            $params = New-Object System.Drawing.Imaging.EncoderParameters 1
            $params.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter(
                [System.Drawing.Imaging.Encoder]::Quality, [long]85)
            $bmp.Save($screenJpg, $encoder, $params)
            $bmp.Dispose()

            # screen.png als JPEG-Kopie fuer Abwaertskompatibilitaet entfernen
            if (Test-Path $screen) { Remove-Item $screen -Force }

            # 2000 Zeilen statt 300 – erfasst auch kurz zurueckliegende Events
            # wie Hardware-Toggles (sendVideoOverlay etc.)
            & adb -s $DeviceSerial logcat -d -t 2000 | Out-File $log -Encoding UTF8
            & adb -s $DeviceSerial logcat -d -b crash -t 200 | Out-File $crash -Encoding UTF8
            # Spezifischer DrainQ-Filter zusaetzlich
            $appLog = Join-Path $ViewDir "logcat-app.log"
            & adb -s $DeviceSerial logcat -d -t 2000 OneHardwareService:* InspectionScreen:* VideoPlayer:* FfmpegVideoPlayer:* AndroidRuntime:E *:S | Out-File $appLog -Encoding UTF8
            & adb -s $DeviceSerial shell dumpsys activity activities | Select-String "mResumedActivity|topResumedActivity" | Out-File $act -Encoding UTF8

            # In Historie kopieren
            Copy-Item $screenJpg, $log, $crash, $act $histDir -ErrorAction SilentlyContinue

            $jpgSize = [math]::Round((Get-Item $screenJpg).Length / 1KB, 1)
            "ok     $ts  #$counter  ${nw}x${nh}  ${jpgSize}KB" | Out-File $StatusFile
            Write-Host "       OK -> $screenJpg  (${nw}x${nh}, ${jpgSize} KB)" -ForegroundColor Green
        } catch {
            "error  $ts  $($_.Exception.Message)" | Out-File $StatusFile
            Write-Host "       FEHLER: $_" -ForegroundColor Red
        }

        Remove-Item $TriggerFile -Force -ErrorAction SilentlyContinue
    }
    Start-Sleep -Milliseconds 800
}
