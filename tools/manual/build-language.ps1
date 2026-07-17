<#
.SYNOPSIS
    W-H4 Phase 8: Einzel-Sprach-Builder für Partner-Übersetzungen.

.DESCRIPTION
    Holt eine Sprache vom Portal (oder liest eine lokale JSON-Datei), rendert synthetische
    Screenshots und baut das PDF-Handbuch.

    Partner-Ablauf (Vollautomatik):
        build-language.ps1 -Lang fr -PortalUrl "https://portal.drainq.com/api/translations/fr.json?scope=one"

    Lokale Übersetzungsdatei (Offline-Test):
        build-language.ps1 -Lang fr -TranslationJson "C:\tmp\fr_translations.json"

    Bekannte App-Sprache (kein Download, keine Injektion):
        build-language.ps1 -Lang de

.PARAMETER Lang
    Zweistelliger Sprach-Code (z.B. de, en, fr, nl).

.PARAMETER PortalUrl
    URL zum Portal-API für die Übersetzung ({code}.json?scope=one-Format).
    Wenn angegeben, wird die Datei heruntergeladen und als TranslationJson gespeichert.

.PARAMETER TranslationJson
    Pfad zu einer lokalen Portal-Export-JSON-Datei.
    Überschreibt PortalUrl wenn beide gesetzt sind.

.EXAMPLE
    .\build-language.ps1 -Lang de
    .\build-language.ps1 -Lang fr -PortalUrl "https://portal.drainq.com/api/translations/fr.json?scope=one"
    .\build-language.ps1 -Lang nl -TranslationJson "C:\tmp\nl.json"
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)]
    [string]$Lang,
    [string]$PortalUrl  = "",
    [string]$TranslationJson = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$Root = Resolve-Path (Join-Path $PSScriptRoot ".." "..")
$RenderScript = Join-Path $PSScriptRoot "render.ps1"
$TranslationsDir = Join-Path $PSScriptRoot "translations"
$ManualDir = Join-Path $Root "docs" "manual"

if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = "C:\Android\jdk17"
}

Write-Host ""
Write-Host "=== W-H4 build-language.ps1 ===" -ForegroundColor Cyan
Write-Host "Sprache : $Lang"

# ── Schritt 1: Übersetzung besorgen ─────────────────────────────────────────
$jsonPath = ""

if ($TranslationJson) {
    # Lokale Datei vorgegeben
    if (-not (Test-Path $TranslationJson)) {
        Write-Error "TranslationJson nicht gefunden: $TranslationJson"
        exit 1
    }
    $jsonPath = $TranslationJson
    Write-Host "Übersetzung: lokal ($jsonPath)"
}
elseif ($PortalUrl) {
    # Download vom Portal
    New-Item -ItemType Directory -Force $TranslationsDir | Out-Null
    $jsonPath = Join-Path $TranslationsDir "${Lang}.json"
    Write-Host "Download: $PortalUrl → $jsonPath"
    Invoke-WebRequest -Uri $PortalUrl -OutFile $jsonPath -UseBasicParsing
    Write-Host "  OK ($((Get-Item $jsonPath).Length) Bytes)"
}
else {
    Write-Host "Übersetzung: App-Bundle (kein Portal-JSON)"
}

# ── Schritt 2: Synthetische Screenshots rendern ──────────────────────────────
Write-Host ""
Write-Host "--- Render ---" -ForegroundColor Yellow
if ($jsonPath) {
    & $RenderScript -Langs $Lang -TranslationJson $jsonPath
} else {
    & $RenderScript -Langs $Lang
}
if ($LASTEXITCODE -ne 0) {
    Write-Error "render.ps1 fehlgeschlagen (Exit $LASTEXITCODE)"
    exit 1
}

# ── Schritt 3: PDF bauen ─────────────────────────────────────────────────────
Write-Host ""
Write-Host "--- PDF ---" -ForegroundColor Yellow
$nodeArgs = @("generate.js", "--lang", $Lang)
& node @nodeArgs 2>&1 | ForEach-Object { Write-Host "  $_" }
if ($LASTEXITCODE -ne 0) {
    Write-Error "generate.js fehlgeschlagen (Exit $LASTEXITCODE)"
    exit 1
}

# ── Fertig ───────────────────────────────────────────────────────────────────
$pdfName = "DrainQ-ONE_Bedienungsanleitung_${Lang}_*.pdf"
$pdf = Get-ChildItem $ManualDir -Filter $pdfName | Sort-Object LastWriteTime -Descending | Select-Object -First 1
Write-Host ""
if ($pdf) {
    Write-Host "Fertig: $($pdf.FullName)" -ForegroundColor Green
} else {
    Write-Warning "PDF nicht gefunden unter $ManualDir"
}
