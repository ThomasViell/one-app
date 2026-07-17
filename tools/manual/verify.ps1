<#
.SYNOPSIS
    W-H5 Golden-Diff Gate: vergleicht aktuelle Paparazzi-Renders mit committeten Goldens.

.DESCRIPTION
    Ein-Befehl-Wrapper für den Screenshot-Diff. Ohne Parameter: prüft DE+EN gegen die
    committeten Goldens (app/src/test/snapshots/images/).

    Mit -Update: führt record (render.ps1 DE+EN) + hartes Sprachdifferenz-Gate aus,
    damit die Goldens aktualisiert werden.

    Alle Prüfungen sind Exit-Code-basiert (NIEMALS nur Sichtprüfung).

.PARAMETER Update
    Statt Prüfung: Goldens neu rendern (render.ps1 DE+EN, inkl. Sprachdiff-Gate).

.PARAMETER Langs
    Komma-getrennte Sprachliste. Default: de,en.

.EXAMPLE
    .\verify.ps1                # prüfen
    .\verify.ps1 -Update        # Goldens neu rendern
    .\verify.ps1 -Langs de,en,fr
#>
[CmdletBinding()]
param(
    [switch]$Update,
    [string]$Langs = "de,en"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$Root        = Resolve-Path (Join-Path $PSScriptRoot ".." "..")
$GradlewCmd  = Join-Path $Root "gradlew.bat"
$SnapshotDir = Join-Path $Root "app" "src" "test" "snapshots" "images"
$DiffDir     = Join-Path $Root "app" "build" "paparazzi" "failures"
$RenderScript = Join-Path $PSScriptRoot "render.ps1"

Write-Host ""
Write-Host "=== W-H5 verify.ps1 ===" -ForegroundColor Cyan
Write-Host "Root:    $Root"
Write-Host "Goldens: $SnapshotDir"

if ($Update) {
    Write-Host ""
    Write-Host "--- Modus: -Update (record + Sprachdiff-Gate) ---" -ForegroundColor Yellow
    & $RenderScript -Langs $Langs
    if ($LASTEXITCODE -ne 0) {
        Write-Host "FAIL: render.ps1 fehlgeschlagen." -ForegroundColor Red
        exit 1
    }
    Write-Host ""
    Write-Host "Update abgeschlossen. Goldens liegen in $SnapshotDir" -ForegroundColor Green
    Write-Host "Nicht vergessen: geänderte Goldens committen!" -ForegroundColor Yellow
    exit 0
}

# ── Prüfmodus: verifyPaparazziDebug (DE+EN) ──────────────────────────────────

$langList = $Langs.Split(",") | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne "" }
$totalFailed = 0
$failedScenes = @()

foreach ($lang in $langList) {
    Write-Host ""
    Write-Host "--- Sprache: $lang (verifyPaparazziDebug) ---" -ForegroundColor Yellow

    $gradleArgs = @(
        "--project-dir", $Root,
        ":app:verifyPaparazziDebug",
        "--tests=com.uip.oneapp.screenshot.ManualScreenshotTest",
        "-Pscreenshot.lang=$lang"
    )

    & $GradlewCmd @gradleArgs 2>&1 | Tee-Object -Variable gradleOut | ForEach-Object {
        if ($_ -match "FAILED|FAILURE|AssertionError|differ") {
            Write-Host "  $_" -ForegroundColor Red
        } else {
            Write-Host "  $_"
        }
    }

    if ($LASTEXITCODE -ne 0) {
        $totalFailed++

        # Diff-Bilder finden und melden
        if (Test-Path $DiffDir) {
            $diffs = Get-ChildItem $DiffDir -Filter "*_${lang}_*.png" -Recurse -ErrorAction SilentlyContinue
            if ($diffs) {
                Write-Host ""
                Write-Host "  Diff-Bilder ($lang):" -ForegroundColor Red
                foreach ($d in $diffs) {
                    $sceneName = if ($d.Name -match "_${lang}_(.+)\.png$") { $Matches[1] } else { $d.BaseName }
                    Write-Host "    Szene: $sceneName" -ForegroundColor Red
                    Write-Host "    Diff:  $($d.FullName)" -ForegroundColor DarkRed
                    $failedScenes += "${lang}/$sceneName"
                }
            } else {
                Write-Host "  Keine Diff-Bilder in $DiffDir gefunden (Ausgabe oben lesen)." -ForegroundColor Yellow
            }
        }
    } else {
        Write-Host "  PASS ($lang — alle Szenen identisch mit Goldens)" -ForegroundColor Green
    }
}

Write-Host ""
Write-Host "=== Ergebnis ===" -ForegroundColor Cyan
if ($totalFailed -gt 0) {
    Write-Host "GATE FAIL: $totalFailed Sprache(n) weichen von den Goldens ab." -ForegroundColor Red
    if ($failedScenes) {
        Write-Host "Abweichende Szenen:" -ForegroundColor Red
        $failedScenes | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
    }
    Write-Host ""
    Write-Host "Wenn die Abweichung gewollt ist (UI-Änderung): .\verify.ps1 -Update" -ForegroundColor Yellow
    exit 1
} else {
    Write-Host "PASS — alle Szenen in $Langs stimmen mit den committeten Goldens überein." -ForegroundColor Green
    exit 0
}
