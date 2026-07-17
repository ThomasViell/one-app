<#
.SYNOPSIS
    W-H5 Wochenjob: Handbuch-Sync gegen Portal-Drift.

.DESCRIPTION
    1. Portal abfragen: aktive Sprachen + Übersetzungsstand (ETag/Timestamp je Sprache).
    2. Vergleich gegen docs/manual/manual-manifest.json (Quelle-ETag + PDF-Stand je Sprache).
    3. Drift → SELBST reparieren: render + PDF für betroffene Sprachen, Manifest aktualisieren,
       Log-Eintrag schreiben, Commit auf Arbeits-Branch.
    4. Nur MELDEN (nicht bauen) wenn Hilfe-Baustein oder Key fehlt (E6 — Texte = belegpflichtig).
    5. KEIN Push ohne -Push-Flag (Default: lokal lassen).

    Manuelle Ausführung: .\weekly-manual-sync.ps1
    Registrierung via schtasks (wöchentlich So 06:00) — NUR im README dokumentiert,
    NICHT automatisch registriert (CEO registriert per Copy-Paste):

        schtasks /Create /TN "DrainQ\ManualSync" /TR "pwsh -NonInteractive -File C:\Projekte\drainq.one\tools\manual\weekly-manual-sync.ps1 -Push" /SC WEEKLY /D SUN /ST 06:00 /RU SYSTEM /F

.PARAMETER PortalUrl
    Portal-Basis-URL. Default: https://license.drainq.com

.PARAMETER Push
    Wenn gesetzt: committed + gepusht zum aktuellen Branch. Default: nur lokaler Commit.

.PARAMETER DryRun
    Nur analysieren, nichts ändern/committen.

.EXAMPLE
    .\weekly-manual-sync.ps1
    .\weekly-manual-sync.ps1 -Push
    .\weekly-manual-sync.ps1 -DryRun
#>
[CmdletBinding()]
param(
    [string]$PortalUrl = "https://license.drainq.com",
    [switch]$Push,
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$Root         = Resolve-Path (Join-Path $PSScriptRoot ".." "..")
$ManualDir    = Join-Path $Root "docs" "manual"
$ManifestPath = Join-Path $ManualDir "manual-manifest.json"
$LogDir       = Join-Path $ManualDir "sync-log"
$RenderScript = Join-Path $PSScriptRoot "render.ps1"
$GenerateJs   = Join-Path $PSScriptRoot "generate.js"
$DateTag      = (Get-Date -Format "yyyy-MM-dd")
$LogPath      = Join-Path $LogDir "${DateTag}.md"

$exitCode = 0  # 0 = OK, 1 = Fehler, 2 = Texte-Pipeline-Lücke (nur melden)

Write-Host ""
Write-Host "=== W-H5 weekly-manual-sync.ps1 ($DateTag) ===" -ForegroundColor Cyan
if ($DryRun) { Write-Host "  [DryRun] Keine Änderungen werden geschrieben." -ForegroundColor Yellow }

# Sicherstellen dass Log-Ordner existiert
if (-not $DryRun) { New-Item -ItemType Directory -Force $LogDir | Out-Null }

$logLines = @()
$logLines += "# Manual-Sync Log — $DateTag"
$logLines += ""
$logLines += "**Modus:** $(if ($DryRun) { 'DryRun' } elseif ($Push) { 'Commit + Push' } else { 'Commit only' })"
$logLines += "**Portal:** $PortalUrl"
$logLines += ""

# ── 1. Portal: aktive Sprachen + Übersetzungsstand ────────────────────────────

Write-Host ""
Write-Host "--- Schritt 1: Portal abfragen ---" -ForegroundColor Yellow

$portalLangs = @{}  # lang -> { etag, timestamp, coverage }

try {
    $locales = Invoke-RestMethod -Method Get -Uri "$PortalUrl/api/software/one/locales.json" -TimeoutSec 10
    foreach ($entry in $locales.languages) {
        $lang = $entry.code ?? $entry
        # Versuche Übersetzungs-Metadaten abzurufen
        try {
            $meta = Invoke-RestMethod -Method Get -Uri "$PortalUrl/api/translations/$lang.json?scope=one&meta=1" -TimeoutSec 5
            $portalLangs[$lang] = @{
                etag      = $meta.etag ?? $meta.lastModified ?? $meta.timestamp ?? ""
                timestamp = $meta.lastModified ?? $meta.timestamp ?? ""
                coverage  = $meta.coverage ?? 0
            }
        } catch {
            $portalLangs[$lang] = @{ etag = ""; timestamp = ""; coverage = 0 }
        }
    }
    Write-Host "  Portal: $($portalLangs.Count) Sprachen gefunden: $($portalLangs.Keys -join ', ')"
    $logLines += "## Portal-Sprachen ($($portalLangs.Count))"
    $logLines += $portalLangs.Keys | ForEach-Object { "- $_ (ETag: $($portalLangs[$_].etag), Coverage: $($portalLangs[$_].coverage)%)" }
    $logLines += ""
} catch {
    Write-Host "  WARN: Portal nicht erreichbar ($($_.Exception.Message))" -ForegroundColor Yellow
    Write-Host "  Offline-Fallback: de,en" -ForegroundColor Yellow
    $portalLangs = @{ "de" = @{ etag = ""; timestamp = ""; coverage = 100 }; "en" = @{ etag = ""; timestamp = ""; coverage = 100 } }
    $logLines += "## Portal-Sprachen (Offline-Fallback)"
    $logLines += "- Portal nicht erreichbar, nur de+en geprüft."
    $logLines += ""
}

# ── 2. Manifest lesen (oder neu anlegen) ─────────────────────────────────────

Write-Host ""
Write-Host "--- Schritt 2: Manifest prüfen ---" -ForegroundColor Yellow

$manifest = @{ schema = 1; languages = @{} }
if (Test-Path $ManifestPath) {
    try {
        $raw = [IO.File]::ReadAllText($ManifestPath, [Text.Encoding]::UTF8) | ConvertFrom-Json
        foreach ($k in $raw.languages.PSObject.Properties.Name) {
            $manifest.languages[$k] = @{
                etag      = $raw.languages.$k.etag ?? ""
                pdfStand  = $raw.languages.$k.pdfStand ?? ""
                pdfFile   = $raw.languages.$k.pdfFile ?? ""
            }
        }
    } catch {
        Write-Host "  WARN: Manifest unlesbar, wird neu erstellt." -ForegroundColor Yellow
    }
}

# ── 3. Drift erkennen und reparieren ─────────────────────────────────────────

Write-Host ""
Write-Host "--- Schritt 3: Drift-Erkennung ---" -ForegroundColor Yellow

$driftLangs = @()
$missingHelpLangs = @()
$manifestChanged = $false

foreach ($lang in $portalLangs.Keys) {
    $portalEtag = $portalLangs[$lang].etag
    $savedEtag  = $manifest.languages[$lang]?.etag ?? ""

    if ($portalEtag -ne "" -and $portalEtag -eq $savedEtag) {
        Write-Host "  $lang : KEIN Drift (ETag identisch)" -ForegroundColor Green
        continue
    }

    # ETag-Vergleich nicht möglich (offline/kein ETag) → immer prüfen
    if ($portalEtag -eq "" -and $savedEtag -eq "") {
        Write-Host "  $lang : Kein ETag verfügbar — PDF-Stand prüfen" -ForegroundColor Yellow
    } else {
        Write-Host "  $lang : DRIFT erkannt (Portal=$portalEtag, Lokal=$savedEtag)" -ForegroundColor Yellow
    }

    $driftLangs += $lang
}

$logLines += "## Drift-Analyse"
if ($driftLangs) {
    $logLines += "Sprachen mit Drift: $($driftLangs -join ', ')"
} else {
    $logLines += "Kein Drift erkannt."
}
$logLines += ""

if ($driftLangs.Count -eq 0) {
    Write-Host "  Kein Drift — nichts zu tun." -ForegroundColor Green
    $logLines += "**Ergebnis:** Kein Drift, kein Rebuild nötig."
    $exitCode = 0
} else {
    # Selbst reparieren: render + PDF für betroffene Sprachen
    Write-Host ""
    Write-Host "--- Schritt 3b: Selbst reparieren ($(${driftLangs} -join ',')) ---" -ForegroundColor Yellow
    $logLines += "## Rebuild"

    foreach ($lang in $driftLangs) {
        Write-Host "  Rebuild $lang ..." -ForegroundColor Yellow
        if (-not $DryRun) {
            # Download Übersetzung vom Portal wenn ETag gesetzt
            $translationArg = ""
            if ($portalLangs[$lang].etag -ne "") {
                try {
                    $translDir = Join-Path $PSScriptRoot "translations"
                    New-Item -ItemType Directory -Force $translDir | Out-Null
                    $translFile = Join-Path $translDir "${lang}.json"
                    Invoke-WebRequest -Uri "$PortalUrl/api/translations/${lang}.json?scope=one" `
                        -OutFile $translFile -UseBasicParsing -TimeoutSec 15
                    $translationArg = $translFile
                } catch {
                    Write-Host "  WARN: Translation-Download für $lang fehlgeschlagen — App-Bundle nutzen" -ForegroundColor Yellow
                }
            }

            try {
                if ($translationArg) {
                    & $RenderScript -Langs $lang -TranslationJson $translationArg
                } else {
                    & $RenderScript -Langs $lang
                }
                if ($LASTEXITCODE -ne 0) { throw "render.ps1 fehlgeschlagen" }

                if (Test-Path $GenerateJs) {
                    node $GenerateJs --lang $lang 2>&1 | ForEach-Object { Write-Host "    $_" }
                    if ($LASTEXITCODE -ne 0) { throw "generate.js fehlgeschlagen" }
                }

                # Manifest aktualisieren
                $pdfPattern = "DrainQ-ONE_Bedienungsanleitung_${lang}_*.pdf"
                $latestPdf = Get-ChildItem $ManualDir -Filter $pdfPattern | Sort-Object LastWriteTime -Desc | Select-Object -First 1
                $manifest.languages[$lang] = @{
                    etag     = $portalLangs[$lang].etag
                    pdfStand = $DateTag
                    pdfFile  = if ($latestPdf) { $latestPdf.Name } else { "" }
                }
                $manifestChanged = $true
                $logLines += "- $lang REBUILT: $($manifest.languages[$lang].pdfFile)"
                Write-Host "  $lang : OK — $($manifest.languages[$lang].pdfFile)" -ForegroundColor Green
            } catch {
                Write-Host "  $lang : FEHLER — $($_.Exception.Message)" -ForegroundColor Red
                $logLines += "- $lang FEHLER: $($_.Exception.Message)"
                $exitCode = 1
            }
        } else {
            $logLines += "- $lang : DryRun (kein Rebuild)"
            Write-Host "  $lang : DryRun — würde Rebuild durchführen" -ForegroundColor DarkGray
        }
    }
    $logLines += ""
}

# ── 4. Texte-Pipeline-Lücken melden (EXIT 2, nicht bauen) ────────────────────

Write-Host ""
Write-Host "--- Schritt 4: Texte-Pipeline-Check ---" -ForegroundColor Yellow

$helpDeFile = Join-Path $Root "app" "src" "main" "assets" "help" "help_de.json"
$scenesFile = Join-Path $Root "tools" "manual" "scenes.json"
$textGaps = @()

if ((Test-Path $helpDeFile) -and (Test-Path $scenesFile)) {
    $scenes = ([IO.File]::ReadAllText($scenesFile) | ConvertFrom-Json).scenes
    $helpDe = ([IO.File]::ReadAllText($helpDeFile) | ConvertFrom-Json).screens
    $helpDeIds = $helpDe | ForEach-Object { $_.id }
    $exempt = @("scr01_splash")
    foreach ($sc in $scenes) {
        if ($sc.name -in $exempt) { continue }
        if ($sc.name -notin $helpDeIds) {
            $textGaps += $sc.name
        }
    }
}

if ($textGaps) {
    Write-Host "  EXIT 2: Fehlende Hilfe-Bausteine (Texte = belegpflichtige Pipeline, NICHT automatisch bauen):" -ForegroundColor Red
    $textGaps | ForEach-Object { Write-Host "    - $_" -ForegroundColor Red }
    $logLines += "## Texte-Pipeline-Lücken (EXIT 2 — NUR MELDEN)"
    $logLines += $textGaps | ForEach-Object { "- $_" }
    $logLines += ""
    $exitCode = 2
} else {
    Write-Host "  Alle Szenen haben Hilfe-Bausteine." -ForegroundColor Green
}

# ── 5. Log schreiben ──────────────────────────────────────────────────────────

$logLines += "## Exit-Code"
$logLines += "- Code $exitCode — $(switch ($exitCode) { 0 {'Alles OK'} 1 {'Rebuild-Fehler'} 2 {'Texte-Lücke (nur Meldung)'} default {'Unbekannt'} })"
$logLines += ""
$logLines += "_Log generiert: $DateTag_"

if (-not $DryRun) {
    [IO.File]::WriteAllText($LogPath, ($logLines -join "`n"), [Text.Encoding]::UTF8)
    Write-Host ""
    Write-Host "Log: $LogPath" -ForegroundColor DarkGray
}

# ── 6. Manifest persistieren + Commit ────────────────────────────────────────

if ($manifestChanged -and -not $DryRun) {
    # Manifest als JSON schreiben
    $manifestJson = @{
        schema = 1
        lastSync = $DateTag
        languages = @{}
    }
    foreach ($k in $manifest.languages.Keys) {
        $manifestJson.languages[$k] = $manifest.languages[$k]
    }
    $manifestJson | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 $ManifestPath
    Write-Host "Manifest aktualisiert: $ManifestPath" -ForegroundColor Green

    # Git-Commit
    Push-Location $Root
    try {
        git add (Join-Path $ManualDir "*.pdf")
        git add $ManifestPath
        git add $LogPath
        git add (Join-Path $ManualDir "screenshots_synth")
        $commitMsg = "chore(manual): weekly-sync $DateTag — $(($driftLangs) -join ',')"
        git commit -m $commitMsg
        Write-Host "Commit: $commitMsg" -ForegroundColor Green

        if ($Push) {
            git push
            Write-Host "Gepusht." -ForegroundColor Green
        } else {
            Write-Host "Noch nicht gepusht (kein -Push). Was gepusht werden würde: $(git log --oneline -1)" -ForegroundColor Yellow
        }
    } catch {
        Write-Host "Git-Commit fehlgeschlagen: $($_.Exception.Message)" -ForegroundColor Red
        $exitCode = 1
    } finally {
        Pop-Location
    }
}

Write-Host ""
Write-Host "=== Fertig (Exit $exitCode) ===" -ForegroundColor Cyan
exit $exitCode
