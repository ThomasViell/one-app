# =============================================================================
# DrainQ.ONE — L10n-Arbeitskopie anlegen
# =============================================================================
# Erzeugt eine vollstaendige Kopie von C:\Projekte\drainq.one nach
# C:\Projekte\drainq.one-localization, inkl. .git, ohne Build-Artefakte.
# Der bisherige master-Branch wird umbenannt zu feature/l10n-portal.
#
# Aufruf:
#   cd C:\Projekte\drainq.one
#   .\setup_l10n_workdir.ps1
#
# Idempotent: laeuft auch ohne Schaden, wenn Ziel schon existiert (fragt nach).
# =============================================================================

$ErrorActionPreference = "Stop"

$source = "C:\Projekte\drainq.one"
$target = "C:\Projekte\drainq.one-localization"

Write-Host "DrainQ.ONE -> L10n-Arbeitskopie" -ForegroundColor Cyan
Write-Host "  Quelle: $source"
Write-Host "  Ziel  : $target"
Write-Host ""

# --- Sanity ------------------------------------------------------------------
if (-not (Test-Path $source)) {
    throw "Quelle existiert nicht: $source"
}

if (Test-Path $target) {
    $answer = Read-Host "Zielordner existiert bereits. Loeschen und neu kopieren? [y/N]"
    if ($answer -ne "y") {
        Write-Host "Abbruch." -ForegroundColor Yellow
        exit 1
    }
    Write-Host "  -> Loesche $target ..." -ForegroundColor Yellow
    Remove-Item -Recurse -Force $target
}

# --- Kopie mit robocopy ------------------------------------------------------
Write-Host "  -> Robocopy laeuft (kann 1-2 Minuten dauern) ..." -ForegroundColor Yellow

# Exclude: Build-Output, IDE-Caches, Native-Build-Output, Velopack-Output.
# Inkludiert: .git (wichtig - History bleibt!), .claude (Skills!),
#             app/, gradle/, alle Skripte, alle MD-Dokumente, *.xlsx.
$excludeDirs = @(
    "build",
    ".gradle",
    ".idea",
    ".kotlin",
    "dist",
    "artifacts",
    "app\build",
    "app\.cxx",
    "app\.gradle"
)
$excludeFiles = @(
    "*.apk",
    "*.aab",
    "local.properties",
    "*.log"
)

$args = @($source, $target, "/E", "/COPY:DAT", "/DCOPY:DAT", "/R:1", "/W:1", "/NFL", "/NDL", "/NJH")
foreach ($d in $excludeDirs) { $args += @("/XD", (Join-Path $source $d)) }
foreach ($f in $excludeFiles) { $args += @("/XF", $f) }

& robocopy @args | Out-Null
# Robocopy returncodes: 0-7 = OK, >=8 = Fehler
if ($LASTEXITCODE -ge 8) {
    throw "robocopy fehlgeschlagen mit Exitcode $LASTEXITCODE"
}

Write-Host "  -> Kopie fertig." -ForegroundColor Green

# --- Branch im Ziel ----------------------------------------------------------
# WICHTIG: git schreibt seinen normalen Output auf stderr - PowerShell mit
# ErrorActionPreference=Stop wertet das als Error. Daher lokal aufweichen.
Write-Host "  -> Branch feature/l10n-portal anlegen ..." -ForegroundColor Yellow
Push-Location $target
$savedEAP = $ErrorActionPreference
$ErrorActionPreference = "Continue"
try {
    & git status --short *>$null
    & git checkout -b feature/l10n-portal *>$null
    if ($LASTEXITCODE -ne 0) {
        # Branch existiert evtl. schon
        & git checkout feature/l10n-portal *>$null
    }
    $currentBranch = (& git rev-parse --abbrev-ref HEAD).Trim()
    Write-Host "     Aktiver Branch in Kopie: $currentBranch" -ForegroundColor Green
} finally {
    $ErrorActionPreference = $savedEAP
    Pop-Location
}

# --- Bundle (Plan + Preflight + Autorun) ans Ziel ausrollen ------------------
$bundleSrc = Join-Path $source "_l10n_bundle"
if (Test-Path $bundleSrc) {
    Write-Host "  -> L10n-Bundle ans Ziel ausrollen ..." -ForegroundColor Yellow
    Copy-Item -Path (Join-Path $bundleSrc "*") -Destination $target -Recurse -Force
    Write-Host "     OK: AUTORUN_L10N_PORTAL.md, preflight_l10n.ps1, autorun_l10n_portal.ps1" -ForegroundColor Green
} else {
    Write-Host "  WARN: _l10n_bundle/ nicht gefunden im Original - bitte separat reinkopieren" -ForegroundColor Yellow
}

# --- Marker fuer Identifikation ---------------------------------------------
Set-Content -Path (Join-Path $target ".l10n-workdir") -Value @"
Diese Kopie wurde am $(Get-Date -Format "yyyy-MM-dd HH:mm") via setup_l10n_workdir.ps1 angelegt.
Zweck: L10n-Migration ueber DrainQ.Web Portal.
Branch: feature/l10n-portal
Original: $source
"@

Write-Host ""
Write-Host "Fertig." -ForegroundColor Green
Write-Host "Naechste Schritte:" -ForegroundColor Cyan
Write-Host "  cd $target"
Write-Host "  .\preflight_l10n.ps1            # Pruefe Voraussetzungen"
Write-Host "  # Phase 0 MANUELL (Inventur+ADR):"
Write-Host "  .\start_phase0.ps1 *>&1 | Tee-Object -FilePath phase0.log"
Write-Host "  # Dann Autorun Phasen 1-7 (mit Telegram-Updates):"
Write-Host "  .\autorun_l10n_portal.ps1 *>&1 | Tee-Object -FilePath autorun_l10n_portal.log"
Write-Host ""
