<#
.SYNOPSIS
  Screenshot-Harness für den DrainQ ONE Manual-Screenshot-Run (W-H1 / W-H2).

.DESCRIPTION
  Steuert die ONE per ADB und dem ScreenshotRigReceiver, um alle Szenen aus
  scenes.json als PNG nach docs/manual/screenshots/<lang>/<szene>.png zu schreiben.

  Ablauf je Sprache:
    1. DEMO_SEED - legt deterministisches Demo-Projekt an
    2. Für jede Szene: NAVIGATE/UI_STATE → Settle → screencap → PNG
    3. Abschlussbericht

.PARAMETER Langs
  Kommagetrennte Sprachliste. Default: de,en

.PARAMETER Only
  Einzelne Szene (name-Feld aus scenes.json) - nur diese aufnehmen.

.PARAMETER ScenesFile
  Pfad zu scenes.json. Default: $PSScriptRoot\scenes.json

.PARAMETER OutDir
  Ausgabeordner. Default: $PSScriptRoot\..\..\docs\manual\screenshots

.PARAMETER SkipSeed
  Überspringt DEMO_SEED (wenn Demo-Projekt bereits vorhanden).

.EXAMPLE
  .\capture.ps1
  .\capture.ps1 -Langs de
  .\capture.ps1 -Only scr02_home -Langs de
  .\capture.ps1 -SkipSeed
#>
[CmdletBinding()]
param(
    [string]$Langs = "de,en",
    [string]$Only = "",
    [string]$ScenesFile = "$PSScriptRoot\scenes.json",
    [string]$OutDir = "$PSScriptRoot\..\..\docs\manual\screenshots",
    [switch]$SkipSeed
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$PACKAGE = "com.uip.drainq.one"
$RIG_PREFIX = "com.uip.drainq.one.rig"
$SETTLE_EXTRA_MS = 500

function Write-Step([string]$msg) { Write-Host "  >> $msg" -ForegroundColor Cyan }
function Write-OK([string]$msg)   { Write-Host "  OK $msg" -ForegroundColor Green }
function Write-Warn([string]$msg) { Write-Host "  !! $msg" -ForegroundColor Yellow }
function Write-Fail([string]$msg) { Write-Host "  FAIL $msg" -ForegroundColor Red }

# --------- Phase 0: ADB-Check ---------------------------------------------------------------------------------------------------------------------------------------------------------------------
Write-Host "`n=== DrainQ ONE Screenshot-Harness (W-H1) ===" -ForegroundColor Magenta

$devices = @(adb devices 2>&1 | Select-String "device$").Count
if ($devices -eq 0) {
    Write-Fail "Kein ADB-Gerät angeschlossen. Abbruch."
    exit 1
}
if ($devices -gt 1) {
    Write-Warn "Mehrere Geräte - nehme das erste (standard adb)."
}
Write-OK "ADB-Gerät erkannt."

# --------- Phase 0b: Kamera-Precheck ---------------------------------------------------------------------------------------------------------------------------------------------
Write-Step "Prüfe Kamera-Node /dev/video0 …"
adb shell "test -e /dev/video0" | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Fail "Kein Kamerabild - /dev/video0 nicht vorhanden. Kamera anschließen und erneut starten. ABBRUCH."
    exit 1
}
Write-OK "Kamera-Node /dev/video0 vorhanden."

# --------- Szenen laden ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
$scenes = (Get-Content $ScenesFile -Raw | ConvertFrom-Json).scenes
if ($Only) {
    $onlyList = $Only -split ","
    $scenes = $scenes | Where-Object { $_.name -in $onlyList }
    if (-not $scenes) {
        Write-Fail "Szene(n) '$Only' nicht in scenes.json gefunden."
        exit 1
    }
}
Write-OK "$($scenes.Count) Szene(n) geladen."

# --------- Demo-Seed ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
$demoProjectId = $null

if (-not $SkipSeed) {
    Write-Step "DEMO_SEED - lege deterministisches Demo-Projekt an …"
    $seedResult = adb shell "am broadcast -a ${RIG_PREFIX}.DEMO_SEED -p $PACKAGE --receiver-foreground" 2>&1
    Start-Sleep -Milliseconds 2000
    # Out-String converts array to scalar so -match sets $Matches
    $seedResultStr = ($seedResult | Out-String)
    if ($seedResultStr -match "OK:projectId=(\d+)") {
        $demoProjectId = $Matches[1]
        Write-OK "Demo-Seed OK: projectId=$demoProjectId"
    } else {
        Write-Warn "Demo-Seed ohne projectId-Rückgabe. Suche ID in DB …"
        $idLine = (adb shell "sqlite3 /data/data/$PACKAGE/databases/oneapp_database 'SELECT id FROM projects WHERE projectNumber=""DEMO_160726_0900_01"" LIMIT 1'" 2>&1 | Out-String).Trim()
        if ($idLine -match "^\d+$") {
            $demoProjectId = $idLine
            Write-OK "Demo-ID aus DB: $demoProjectId"
        } else {
            Write-Fail "Demo-Seed fehlgeschlagen und ID nicht ermittelbar. Abbruch."
            Write-Host $seedResult
            exit 1
        }
    }
} else {
    Write-Step "SkipSeed - suche bestehende Demo-ID …"
    $idLine = (adb shell "sqlite3 /data/data/$PACKAGE/databases/oneapp_database 'SELECT id FROM projects WHERE projectNumber=""DEMO_160726_0900_01"" LIMIT 1'" 2>&1 | Out-String).Trim()
    if ($idLine -match "^\d+$") {
        $demoProjectId = $idLine
        Write-OK "Demo-ID: $demoProjectId"
    } else {
        Write-Fail "Kein Demo-Projekt gefunden. Führe ohne -SkipSeed aus."
        exit 1
    }
}

# --------- Screenshot-Lauf je Sprache ------------------------------------------------------------------------------------------------------------------------------------------
$langList = $Langs -split ","
$summary = @{ ok = 0; fail = 0; failed = [System.Collections.Generic.List[string]]::new() }

foreach ($lang in $langList) {
    Write-Host "`n--- Sprache: $lang ---" -ForegroundColor Magenta

    # SET_LOCALE
    Write-Step "SET_LOCALE $lang …"
    adb shell "am broadcast -a ${RIG_PREFIX}.SET_LOCALE --es lang $lang -p $PACKAGE --receiver-foreground" | Out-Null
    Start-Sleep -Milliseconds 800

    $langDir = Join-Path $OutDir $lang
    New-Item -ItemType Directory -Force $langDir | Out-Null

    foreach ($scene in $scenes) {
        $sceneName = $scene.name
        $route = $scene.route -replace "\{demoId\}", $demoProjectId
        $uiState = $scene.uiState
        $settleMs = [int]$scene.settleMs + $SETTLE_EXTRA_MS
        $outFile = Join-Path $langDir "$sceneName.png"

        Write-Step "[$lang] $sceneName …"

        # Navigate
        $navResult = adb shell "am broadcast -a ${RIG_PREFIX}.NAVIGATE --es route '$route' -p $PACKAGE --receiver-foreground" 2>&1
        if ($navResult -match "FAIL") {
            Write-Fail "  NAVIGATE fehlgeschlagen: $navResult"
            $summary.fail++
            $summary.failed.Add("$lang/$sceneName")
            continue
        }
        Start-Sleep -Milliseconds ([math]::Max(800, $settleMs / 2))

        # UI_STATE (wenn definiert)
        if ($uiState -and $uiState -ne "null" -and $uiState.ToString() -ne "") {
            $stateResult = adb shell "am broadcast -a ${RIG_PREFIX}.UI_STATE --es state '$uiState' -p $PACKAGE --receiver-foreground" 2>&1
            if ($stateResult -match "FAIL") {
                Write-Warn "  UI_STATE '$uiState' fehlgeschlagen - Screenshot trotzdem machen."
            }
            Start-Sleep -Milliseconds ([math]::Max(500, $settleMs / 2))
        } else {
            Start-Sleep -Milliseconds $settleMs
        }

        # Screenshot
        try {
            adb exec-out screencap -p > $outFile
            $fileSize = (Get-Item $outFile).Length
            if ($fileSize -lt 10000) {
                Write-Warn "  Screenshot verdächtig klein ($fileSize Bytes) - möglicherweise Schwarzbild!"
                $summary.fail++
                $summary.failed.Add("$lang/$sceneName (klein)")
            } else {
                Write-OK "  $sceneName.png ($([math]::Round($fileSize/1024)) KB)"
                $summary.ok++
            }
        } catch {
            Write-Fail "  screencap fehlgeschlagen: $_"
            $summary.fail++
            $summary.failed.Add("$lang/$sceneName")
        }
    }
}

# --------- Abschlussbericht ------------------------------------------------------------------------------------------------------------------------------------------------------------------------
Write-Host "`n=== Abschlussbericht ===" -ForegroundColor Magenta
Write-Host "  OK:   $($summary.ok)" -ForegroundColor Green
if ($summary.fail -gt 0) {
    Write-Host "  FAIL: $($summary.fail)" -ForegroundColor Red
    Write-Host "  Fehlgeschlagene Szenen:" -ForegroundColor Red
    $summary.failed | ForEach-Object { Write-Host "    - $_" -ForegroundColor Red }
    exit 1
} else {
    Write-Host "  Alle Szenen erfolgreich." -ForegroundColor Green
    exit 0
}
