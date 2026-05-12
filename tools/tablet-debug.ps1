# =============================================================================
# DrainQ ONE - Tablet Debug Setup
# Startet scrcpy (Mirror) + Logcat-Capture parallel
# Output: C:\Projekte\drainq.one\debug-logs\<timestamp>\
# =============================================================================

param(
    [string]$DeviceSerial = "",          # Optional: bestimmtes Geraet (sonst erstes)
    [int]$MaxLogSizeMB = 100,
    [switch]$NoScrcpy,                    # Nur Logcat, kein Mirror
    [switch]$Wireless,                    # WLAN-Modus (Tablet muss vorher per USB gepairt sein)
    [string]$WirelessIp = "",             # z.B. 192.168.1.50
    [switch]$Install                      # Pre-Check + Hinweise zur Installation
)

$ErrorActionPreference = "Stop"
$ProjectRoot = "C:\Projekte\drainq.one"
$LogRoot = Join-Path $ProjectRoot "debug-logs"
$Timestamp = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"
$SessionDir = Join-Path $LogRoot $Timestamp
$LatestLink = Join-Path $LogRoot "latest"

# -----------------------------------------------------------------------------
# Pre-Checks
# -----------------------------------------------------------------------------
function Test-Tool($Name) {
    $cmd = Get-Command $Name -ErrorAction SilentlyContinue
    return [bool]$cmd
}

Write-Host "==> DrainQ ONE Tablet Debug Setup" -ForegroundColor Cyan
Write-Host "    Session: $Timestamp"
Write-Host ""

$adbOk    = Test-Tool "adb"
$scrcpyOk = Test-Tool "scrcpy"

if ($Install -or -not $adbOk -or -not $scrcpyOk) {
    Write-Host "Tool-Status:" -ForegroundColor Yellow
    Write-Host "  adb    : $(if($adbOk){'OK'}else{'FEHLT'})"
    Write-Host "  scrcpy : $(if($scrcpyOk){'OK'}else{'FEHLT'})"
    Write-Host ""
    if (-not $adbOk -or -not $scrcpyOk) {
        Write-Host "Installation (eine der Optionen):" -ForegroundColor Yellow
        Write-Host "  Chocolatey : choco install adb scrcpy -y"
        Write-Host "  Scoop      : scoop install adb scrcpy"
        Write-Host "  Manuell    : https://github.com/Genymobile/scrcpy/releases"
        Write-Host ""
        if (-not $adbOk -or -not $scrcpyOk) { exit 1 }
    }
}

# -----------------------------------------------------------------------------
# Verzeichnis anlegen
# -----------------------------------------------------------------------------
New-Item -ItemType Directory -Force -Path $SessionDir | Out-Null
if (Test-Path $LatestLink) { Remove-Item $LatestLink -Force -Recurse -ErrorAction SilentlyContinue }
# Junction statt SymLink (kein Admin noetig)
cmd /c mklink /J "$LatestLink" "$SessionDir" | Out-Null

Write-Host "Output-Verzeichnis: $SessionDir" -ForegroundColor Green
Write-Host "Shortcut         : $LatestLink"
Write-Host ""

# -----------------------------------------------------------------------------
# WLAN-Verbindung (optional)
# -----------------------------------------------------------------------------
if ($Wireless -and $WirelessIp) {
    Write-Host "Verbinde via WLAN: $WirelessIp:5555" -ForegroundColor Cyan
    & adb connect "${WirelessIp}:5555" | Out-Host
    Start-Sleep -Seconds 2
}

# -----------------------------------------------------------------------------
# Geraete-Check
# -----------------------------------------------------------------------------
$devicesRaw = & adb devices
$devices = @()
foreach ($line in $devicesRaw) {
    $lineStr = [string]$line
    # Header und leere Zeilen ueberspringen
    if ($lineStr -match "^List of devices" -or [string]::IsNullOrWhiteSpace($lineStr)) { continue }
    # Erwartetes Format: "<serial>\t<state>"  - state muss exakt "device" sein (nicht "unauthorized", "offline" etc.)
    if ($lineStr -match "^(\S+)\s+device\s*$") {
        $devices += $matches[1]
    }
}

if ($devices.Count -eq 0) {
    Write-Host "FEHLER: Kein Android-Geraet erkannt." -ForegroundColor Red
    Write-Host "       - USB-Debugging am Tablet aktiviert?"
    Write-Host "       - USB-Kabel ein Datenkabel (nicht nur Strom)?"
    Write-Host "       - 'Diesen Computer immer erlauben' bestaetigt?"
    exit 1
}

if (-not $DeviceSerial) { $DeviceSerial = $devices[0] }
Write-Host "Geraet: $DeviceSerial" -ForegroundColor Green

# Geraete-Info dokumentieren
$infoFile = Join-Path $SessionDir "device-info.txt"
"=== Device Info ($Timestamp) ===" | Out-File $infoFile
& adb -s $DeviceSerial shell getprop ro.product.manufacturer | Out-File $infoFile -Append
& adb -s $DeviceSerial shell getprop ro.product.model        | Out-File $infoFile -Append
& adb -s $DeviceSerial shell getprop ro.build.version.release| Out-File $infoFile -Append
& adb -s $DeviceSerial shell getprop ro.build.version.sdk    | Out-File $infoFile -Append
& adb -s $DeviceSerial shell pm list packages com.uip        | Out-File $infoFile -Append

# -----------------------------------------------------------------------------
# Logcat-Filter: nur fuer DrainQ ONE relevante Tags
# -----------------------------------------------------------------------------
$logFile     = Join-Path $SessionDir "logcat.log"
$errFile     = Join-Path $SessionDir "errors.log"
$crashFile   = Join-Path $SessionDir "crashes.log"

# Vorherige Logs leeren, damit Capture sauber startet
& adb -s $DeviceSerial logcat -c

Write-Host ""
Write-Host "==> Starte Logcat-Capture" -ForegroundColor Cyan
Write-Host "    Voll-Log   : $logFile"
Write-Host "    Nur Fehler : $errFile"
Write-Host "    Crashes    : $crashFile"

# Vollstaendiges Log (gefiltert auf App + relevante System-Tags)
$logcatArgs = @(
    "-s", $DeviceSerial, "logcat",
    "-v", "time",
    "OneApp:*", "DrainQ:*",
    "AndroidRuntime:E",
    "ExoPlayer:*", "MediaCodec:*",
    "FFmpeg:*", "FFmpegRtspRecorder:*",
    "MQTT:*", "PahoMqtt:*",
    "RTSP:*", "VLC:*",
    "*:E"   # alle Errors fangen
)

$logcatProc = Start-Process -FilePath "adb" -ArgumentList $logcatArgs `
    -RedirectStandardOutput $logFile -NoNewWindow -PassThru

# Parallel: nur Errors mitschreiben (zweiter Stream fuer schnelle Sicht)
$errArgs = @("-s", $DeviceSerial, "logcat", "-v", "time", "*:E")
$errProc = Start-Process -FilePath "adb" -ArgumentList $errArgs `
    -RedirectStandardOutput $errFile -NoNewWindow -PassThru

# Crash-Buffer
$crashArgs = @("-s", $DeviceSerial, "logcat", "-b", "crash", "-v", "time")
$crashProc = Start-Process -FilePath "adb" -ArgumentList $crashArgs `
    -RedirectStandardOutput $crashFile -NoNewWindow -PassThru

Start-Sleep -Seconds 1

# -----------------------------------------------------------------------------
# scrcpy starten
# -----------------------------------------------------------------------------
$scrcpyProc = $null
if (-not $NoScrcpy -and $scrcpyOk) {
    Write-Host ""
    Write-Host "==> Starte scrcpy Mirror" -ForegroundColor Cyan
    $scrcpyArgs = @(
        "--serial=$DeviceSerial",
        "--window-title=DrainQ ONE Tablet ($DeviceSerial)",
        "--max-size=1600",
        "--video-bit-rate=8M",
        "--max-fps=30",
        "--stay-awake",
        "--turn-screen-off",   # PC-Mirror an, Tablet-Display aus (spart Akku)
        "--record=$SessionDir\screen-record.mp4"
    )
    $scrcpyProc = Start-Process -FilePath "scrcpy" -ArgumentList $scrcpyArgs -PassThru
}

# -----------------------------------------------------------------------------
# Marker-Datei fuer Claude
# -----------------------------------------------------------------------------
$readme = @"
DrainQ ONE - Debug Session $Timestamp
=======================================

Geraet     : $DeviceSerial
Start      : $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")

Dateien:
  device-info.txt    Geraete-Eigenschaften + installierte uip-Pakete
  logcat.log         Voll-Log (App-Tags + alle Errors)
  errors.log         Nur Error-Level Eintraege
  crashes.log        Crash-Buffer (Stacktraces)
  screen-record.mp4  scrcpy Bildschirmaufnahme (falls aktiv)

Fuer Claude (in C:\Projekte\drainq.one):
  Lies tail -50 von debug-logs\latest\errors.log
  oder Get-Content debug-logs\latest\logcat.log -Tail 100 -Wait
"@
$readme | Out-File (Join-Path $SessionDir "README.txt")

# -----------------------------------------------------------------------------
# Hauptschleife: warten bis Abbruch
# -----------------------------------------------------------------------------
Write-Host ""
Write-Host "==> Capture laeuft." -ForegroundColor Green
Write-Host "    Mit STRG+C beenden. Alle Streams werden sauber geschlossen."
Write-Host ""
Write-Host "Live-Tail Fehler (in anderem Fenster):" -ForegroundColor Yellow
Write-Host "    Get-Content '$errFile' -Tail 20 -Wait"
Write-Host ""

try {
    while ($true) {
        Start-Sleep -Seconds 5
        # Log-Groesse pruefen
        if (Test-Path $logFile) {
            $sizeMB = [math]::Round((Get-Item $logFile).Length / 1MB, 1)
            if ($sizeMB -gt $MaxLogSizeMB) {
                Write-Host "[WARN] Logcat ueber $MaxLogSizeMB MB ($sizeMB MB) - rotiere." -ForegroundColor Yellow
                Stop-Process -Id $logcatProc.Id -Force -ErrorAction SilentlyContinue
                Move-Item $logFile "$logFile.$(Get-Date -Format 'HHmmss').bak"
                $logcatProc = Start-Process -FilePath "adb" -ArgumentList $logcatArgs `
                    -RedirectStandardOutput $logFile -NoNewWindow -PassThru
            }
        }
        # scrcpy beendet?
        if ($scrcpyProc -and $scrcpyProc.HasExited) {
            Write-Host "[INFO] scrcpy beendet." -ForegroundColor Yellow
            $scrcpyProc = $null
        }
    }
} finally {
    Write-Host ""
    Write-Host "==> Beende Capture..." -ForegroundColor Cyan
    foreach ($p in @($logcatProc, $errProc, $crashProc, $scrcpyProc)) {
        if ($p -and -not $p.HasExited) {
            Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
        }
    }
    Write-Host "    Logs gespeichert in: $SessionDir" -ForegroundColor Green
    Write-Host "    Shortcut           : $LatestLink"
}
