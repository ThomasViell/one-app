<#
.SYNOPSIS
    W-H4 Render-Skript: Synthetische Screenshots für alle Szenen je Sprache.

.DESCRIPTION
    Ruft Paparazzi (recordPaparazziDebug) für jede Sprache auf und kopiert die
    erzeugten PNGs nach docs/manual/screenshots_synth/<lang>/.

    Primärer Weg (bekannte App-Sprachen):
        render.ps1 -Langs de,en
        render.ps1 -Langs de,en,fr,it

    Partner-Weg (Sprache aus Portal-JSON):
        render.ps1 -Lang xx -TranslationJson "path\to\xx.json"
        Das JSON muss das Portal-Exportformat {key:value,...} haben.
        Fehlende Keys → Fallback DE + Warn-Ausgabe.

.PARAMETER Langs
    Komma-getrennte Liste der Sprach-Codes. Default: de,en.
    Bekannte App-Sprachen werden direkt gerendert (kein TranslationJson nötig).

.PARAMETER TranslationJson
    Pfad zu einer Portal-JSON-Übersetzungsdatei. Nur sinnvoll, wenn genau ein
    Sprachcode in -Langs angegeben wird und dieser Code NICHT im App-Bundle ist.

.EXAMPLE
    .\render.ps1
    .\render.ps1 -Langs de,en,fr
    .\render.ps1 -Langs xx -TranslationJson "C:\tmp\translations_xx.json"
#>
[CmdletBinding()]
param(
    [string]$Langs = "de,en",
    [string]$TranslationJson = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$Root = Resolve-Path (Join-Path $PSScriptRoot ".." "..")
$SnapshotDir = Join-Path $Root "app" "src" "test" "snapshots" "images"
$SynthDir    = Join-Path $Root "docs" "manual" "screenshots_synth"
$GradlewCmd  = Join-Path $Root "gradlew.bat"

if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = "C:\Android\jdk17"
    Write-Host "JAVA_HOME not set - using default: $($env:JAVA_HOME)"
}

$langList = $Langs.Split(",") | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne "" }

Write-Host ""
Write-Host "=== W-H4 render.ps1 ===" -ForegroundColor Cyan
Write-Host "Sprachen: $($langList -join ', ')"
Write-Host "Root:     $Root"
Write-Host "Synth:    $SynthDir"
if ($TranslationJson) {
    Write-Host "Portal-JSON: $TranslationJson"
    if ($langList.Count -ne 1) {
        Write-Error "-TranslationJson ist nur mit genau einer Sprache (-Langs xx) sinnvoll."
        exit 1
    }
    if (-not (Test-Path $TranslationJson)) {
        Write-Error "TranslationJson nicht gefunden: $TranslationJson"
        exit 1
    }
}

$totalFailed = 0
$report = @()

foreach ($lang in $langList) {
    Write-Host ""
    Write-Host "--- Sprache: $lang ---" -ForegroundColor Yellow

    # Gradle-Argumente (-P übergibt Werte an Gradle-Projekt-Properties, die der Build-Script
    # via project.findProperty() liest und als systemProperty an den Test-JVM weiterleitet.)
    $gradleArgs = @(
        "--project-dir", $Root,
        ":app:recordPaparazziDebug",
        "--rerun-tasks",
        "--tests=com.uip.oneapp.screenshot.ManualScreenshotTest",
        "-Pscreenshot.lang=$lang"
    )
    if ($TranslationJson) {
        $gradleArgs += "-Pscreenshot.translationJson=$TranslationJson"
    }

    Write-Host "Gradlew: $gradleArgs"
    & $GradlewCmd @gradleArgs
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "recordPaparazziDebug für '$lang' FEHLGESCHLAGEN (Exit $LASTEXITCODE)"
        $totalFailed++
        $report += "FAIL  $lang  (Gradle-Fehler)"
        continue
    }

    # Zielordner anlegen
    $destDir = Join-Path $SynthDir $lang
    New-Item -ItemType Directory -Force -Path $destDir | Out-Null

    # PNGs kopieren und umbenennen
    # Paparazzi-Dateiname: com.uip.oneapp.screenshot_ManualScreenshotTest_<method>_<lang>_<scene>.png
    # Ziel-Dateiname:      <scene>.png
    $pattern = "*_${lang}_*.png"
    $files = Get-ChildItem -Path $SnapshotDir -Filter $pattern -ErrorAction SilentlyContinue
    if (-not $files) {
        Write-Warning "Keine PNGs für Sprache '$lang' in $SnapshotDir"
        $totalFailed++
        $report += "FAIL  $lang  (0 PNGs)"
        continue
    }

    $copied = 0
    foreach ($f in $files) {
        # Extrahiere Szenen-Name: alles nach "_${lang}_" bis ".png"
        if ($f.Name -match "_${lang}_(.+)\.png$") {
            $sceneName = $Matches[1]
            $dest = Join-Path $destDir "$sceneName.png"
            Copy-Item -Path $f.FullName -Destination $dest -Force
            $copied++
        }
    }

    Write-Host "  $copied PNGs → $destDir" -ForegroundColor Green
    $report += "PASS  $lang  ($copied PNGs)"
}

Write-Host ""
Write-Host "=== Ergebnis ===" -ForegroundColor Cyan
$report | ForEach-Object { Write-Host "  $_" }
Write-Host ""

if ($totalFailed -gt 0) {
    Write-Host "$totalFailed Sprachen fehlgeschlagen." -ForegroundColor Red
    exit 1
} else {
    Write-Host "Alle $($langList.Count) Sprachen erfolgreich gerendert." -ForegroundColor Green
}

# ------ W-H4b: Hartes Sprachdifferenz-Gate ------------------------------------------------------------------------------------------------------------------
# Bei Mehrsprachläufen: jede Szene muss sich zwischen DE und jeder anderen Sprache
# sichtbar unterscheiden. Identische Hashes = Sprachumschaltung wirkungslos → ROT.
# Ausnahme: Nur eine Sprache gerendert → Gate überspringen.
if ($langList.Count -gt 1 -and $totalFailed -eq 0) {
    Write-Host ""
    Write-Host "=== Sprachdifferenz-Gate ===" -ForegroundColor Cyan

    $refLang = $langList[0]  # erste Sprache als Referenz (i.d.R. de)
    $refDir  = Join-Path $SynthDir $refLang
    $gateFailed = 0
    $gateReport = @()

    foreach ($cmpLang in $langList[1..($langList.Count-1)]) {
        $cmpDir = Join-Path $SynthDir $cmpLang
        $refFiles = Get-ChildItem $refDir -Filter "*.png" -ErrorAction SilentlyContinue | Sort-Object Name
        foreach ($rf in $refFiles) {
            $cf = Join-Path $cmpDir $rf.Name
            if (-not (Test-Path $cf)) { continue }
            $rh = (Get-FileHash $rf.FullName -Algorithm MD5).Hash
            $ch = (Get-FileHash $cf          -Algorithm MD5).Hash
            if ($rh -eq $ch) {
                Write-Host "  IDENTICAL ${refLang} vs ${cmpLang}: $($rf.Name)  [$rh]" -ForegroundColor Red
                $gateFailed++
                $gateReport += "IDENTICAL ${refLang}/${cmpLang} $($rf.Name)"
            }
        }
    }

    if ($gateFailed -gt 0) {
        Write-Host ""
        Write-Host "GATE FAIL: $gateFailed Szene(n) sind sprachunabhängig identisch." -ForegroundColor Red
        Write-Host "Sprachumschaltung ist wirkungslos. Build abgebrochen." -ForegroundColor Red
        exit 1
    } else {
        $totalScenes = ($langList.Count - 1) * ($refFiles.Count)
        Write-Host "  PASS - alle Szenenpaare sprachlich unterschiedlich ($totalScenes Vergleiche)." -ForegroundColor Green
    }
}
