# =============================================================================
# DrainQ ONE - Einmaliger Screenshot der ONE-Hardware (Rockchip-Android)
# Schreibt nach C:\Projekte\drainq.one\debug-logs\claude-view\one-screen.jpg
# =============================================================================
param([string]$DeviceSerial = "233b4bd2865177ed")

$ProjectRoot = "C:\Projekte\drainq.one"
$ViewDir     = Join-Path $ProjectRoot "debug-logs\claude-view"
New-Item -ItemType Directory -Force -Path $ViewDir | Out-Null

$rawPath = Join-Path $ViewDir "one-screen-raw.png"
$jpgPath = Join-Path $ViewDir "one-screen.jpg"

Write-Host "==> ONE Snapshot ($DeviceSerial)" -ForegroundColor Cyan

# Binaere Ausgabe via cmd (PowerShell '>' zerstoert PNG durch UTF-16)
cmd /c "adb -s $DeviceSerial exec-out screencap -p > `"$rawPath`""

if (-not (Test-Path $rawPath) -or (Get-Item $rawPath).Length -lt 1024) {
    Write-Host "FEHLER: Screencap leer/zu klein. Geraet erreichbar?" -ForegroundColor Red
    exit 1
}

# Resize + JPEG re-encode fuer API-Kompatibilitaet
Add-Type -AssemblyName System.Drawing
$src = [System.Drawing.Image]::FromFile($rawPath)
$maxW = 1600
if ($src.Width -gt $maxW) {
    $ratio = $maxW / $src.Width
    $nw = $maxW
    $nh = [int]($src.Height * $ratio)
} else { $nw = $src.Width; $nh = $src.Height }
$bmp = New-Object System.Drawing.Bitmap $nw, $nh
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.DrawImage($src, 0, 0, $nw, $nh)
$g.Dispose(); $src.Dispose()

$encoder = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() |
           Where-Object { $_.MimeType -eq "image/jpeg" }
$params = New-Object System.Drawing.Imaging.EncoderParameters 1
$params.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter(
    [System.Drawing.Imaging.Encoder]::Quality, [long]85)
$bmp.Save($jpgPath, $encoder, $params)
$bmp.Dispose()

$sizeKB = [math]::Round((Get-Item $jpgPath).Length / 1KB, 1)
Write-Host "OK -> $jpgPath  (${nw}x${nh}, ${sizeKB} KB)" -ForegroundColor Green
