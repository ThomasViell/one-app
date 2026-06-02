# =============================================================================
# Phase-0-Recovery: Outputs aus Original in Kopie verschieben
# =============================================================================
# Phase 0 hat versehentlich im Original C:\Projekte\drainq.one gearbeitet,
# weil start_phase0.ps1 hardcodierte Pfade hatte. Diese Recovery:
#   1) Loescht Lock im Kopie-Repo (falls vorhanden)
#   2) Kopiert Phase-0-Files vom Original in die Kopie
#   3) Legt Branch feature/l10n-phase-0-inventur in der Kopie an + committet
#   4) Setzt das Original zurueck (Branch + Files weg)
#   5) Fixt start_phase0.ps1 in der Kopie (Pfade auf -localization)
#
# WICHTIG: Vor dem Lauf alle anderen PowerShell-Fenster schliessen,
# die in einem der beiden Repos offen sind.
#
# Aufruf (in FRISCHER PowerShell):
#   cd C:\Projekte\drainq.one
#   .\recovery_phase0_to_workdir.ps1
# =============================================================================

$ErrorActionPreference = "Continue"
$orig = "C:\Projekte\drainq.one"
$copy = "C:\Projekte\drainq.one-localization"

Write-Host ""
Write-Host "=== Phase-0-Recovery ===" -ForegroundColor Cyan
Write-Host ""

# --- 1) Lock im Kopie-Repo entfernen ----------------------------------------
$lock = Join-Path $copy ".git\index.lock"
if (Test-Path $lock) {
    Write-Host "[1] index.lock im Kopie-Repo gefunden - loesche ..." -ForegroundColor Yellow
    Remove-Item -Force $lock -ErrorAction SilentlyContinue
    if (Test-Path $lock) {
        Write-Host "    FEHLER: konnte Lock nicht loeschen. Sind alle anderen PS-Fenster zu?" -ForegroundColor Red
        Write-Host "    Pfad: $lock"
        exit 1
    }
    Write-Host "    OK" -ForegroundColor Green
} else {
    Write-Host "[1] Kein Lock vorhanden - OK" -ForegroundColor Green
}

# --- 2) Phase-0-Files vom Original in die Kopie spiegeln ---------------------
Write-Host ""
Write-Host "[2] Spiegele Phase-0-Files vom Original in die Kopie ..." -ForegroundColor Yellow

$files = @(
    "RESULT_PHASE_0.md",
    "phase0\keys_de_en.json",
    "phase0\shared_candidates.json",
    "phase0\hardcoded_audit_preview.md",
    "docs\adr\0010-l10n-portal-as-sot.md"
)

foreach ($f in $files) {
    $src = Join-Path $orig $f
    $dst = Join-Path $copy $f
    if (Test-Path $src) {
        $dir = Split-Path $dst -Parent
        if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
        Copy-Item -Path $src -Destination $dst -Force
        Write-Host "    + $f" -ForegroundColor Green
    } else {
        Write-Host "    - $f (im Original nicht gefunden)" -ForegroundColor DarkYellow
    }
}

# --- 3) In der Kopie: Branch anlegen + committen -----------------------------
Write-Host ""
Write-Host "[3] In der Kopie: Branch feature/l10n-phase-0-inventur + Commit ..." -ForegroundColor Yellow

Push-Location $copy
try {
    # Aktuellen Branch merken
    $currentBranch = (& git rev-parse --abbrev-ref HEAD 2>$null).Trim()
    Write-Host "    Kopie aktiver Branch (vorher): $currentBranch"

    # Pruefe ob l10n-portal Branch existiert
    & git rev-parse --verify feature/l10n-portal *>$null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "    FEHLER: Branch feature/l10n-portal existiert nicht in der Kopie" -ForegroundColor Red
        exit 1
    }

    # Phase-0-Branch aus l10n-portal abzweigen (oder umschalten falls schon da)
    & git rev-parse --verify feature/l10n-phase-0-inventur *>$null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "    Branch existiert bereits - checke aus"
        & git checkout feature/l10n-phase-0-inventur *>$null
    } else {
        Write-Host "    Lege Branch feature/l10n-phase-0-inventur an (aus feature/l10n-portal)"
        & git checkout -b feature/l10n-phase-0-inventur feature/l10n-portal *>$null
    }

    # Files stagen + committen
    & git add RESULT_PHASE_0.md phase0/ docs/adr/0010-l10n-portal-as-sot.md *>$null
    $stagedStatus = & git status --porcelain
    if ([string]::IsNullOrWhiteSpace($stagedStatus)) {
        Write-Host "    Nichts zu committen - Files schon im Branch?" -ForegroundColor DarkYellow
    } else {
        & git commit -m "L10n Phase 0: Inventur DE+EN, 355 Keys, 19 Hardcoded, 80 Shared-Kandidaten, ADR 0010" *>$null
        $sha = (& git rev-parse --short HEAD).Trim()
        Write-Host "    Commit angelegt: $sha" -ForegroundColor Green
    }
} finally {
    Pop-Location
}

# --- 4) Original aufraeumen --------------------------------------------------
Write-Host ""
Write-Host "[4] Original-Repo zuruecksetzen ..." -ForegroundColor Yellow

Push-Location $orig
try {
    # Wenn Phase-0-Branch im Original ausgechecked ist, erst auf master wechseln
    $origBranch = (& git rev-parse --abbrev-ref HEAD 2>$null).Trim()
    if ($origBranch -eq "feature/l10n-phase-0-inventur") {
        Write-Host "    Original ist auf l10n-phase-0-inventur - wechsle auf master"
        & git checkout master *>$null
    }

    # Phase-0-Branch loeschen (force, weil die Commits ja in der Kopie sind, nicht hier gepusht)
    & git rev-parse --verify feature/l10n-phase-0-inventur *>$null
    if ($LASTEXITCODE -eq 0) {
        & git branch -D feature/l10n-phase-0-inventur *>$null
        Write-Host "    Branch feature/l10n-phase-0-inventur geloescht" -ForegroundColor Green
    }

    # Phase-0-Files entfernen
    foreach ($f in $files) {
        $p = Join-Path $orig $f
        if (Test-Path $p) {
            Remove-Item -Force $p -ErrorAction SilentlyContinue
            Write-Host "    - geloescht: $f"
        }
    }
    if (Test-Path (Join-Path $orig "phase0")) {
        Remove-Item -Recurse -Force (Join-Path $orig "phase0") -ErrorAction SilentlyContinue
        Write-Host "    - phase0/ Ordner geloescht"
    }

    # Letzter Check: ist git clean?
    $st = & git status --porcelain
    if ([string]::IsNullOrWhiteSpace($st)) {
        Write-Host "    Original ist sauber" -ForegroundColor Green
    } else {
        Write-Host "    WARNUNG: Original noch nicht ganz sauber:" -ForegroundColor DarkYellow
        $st | ForEach-Object { Write-Host "      $_" }
    }
} finally {
    Pop-Location
}

# --- 5) start_phase0.ps1 in der Kopie auf richtige Pfade umstellen ----------
Write-Host ""
Write-Host "[5] start_phase0.ps1 in der Kopie auf -localization umstellen ..." -ForegroundColor Yellow

$startPath = Join-Path $copy "start_phase0.ps1"
if (Test-Path $startPath) {
    $content = Get-Content $startPath -Raw
    $content = $content -replace 'C:\\Projekte\\drainq\.one\b(?!-localization)', 'C:\Projekte\drainq.one-localization'
    Set-Content -Path $startPath -Value $content -Encoding UTF8
    Write-Host "    Pfade gefixt in start_phase0.ps1" -ForegroundColor Green
} else {
    Write-Host "    start_phase0.ps1 nicht gefunden in der Kopie - skip" -ForegroundColor DarkYellow
}

# --- Zusammenfassung ---------------------------------------------------------
Write-Host ""
Write-Host "=== Recovery FERTIG ===" -ForegroundColor Green
Write-Host ""
Write-Host "Naechste Schritte:" -ForegroundColor Cyan
Write-Host "  cd $copy"
Write-Host "  git log --oneline -3                      # checke die zwei letzten Commits"
Write-Host "  git checkout feature/l10n-portal          # zurueck auf Hauptbranch der Kopie"
Write-Host "  git merge feature/l10n-phase-0-inventur   # Phase-0 in den Hauptbranch mergen (optional)"
Write-Host "  .\preflight_l10n.ps1                      # Voraussetzungen pruefen"
Write-Host "  .\autorun_l10n_portal.ps1 *>&1 | Tee-Object -FilePath autorun_l10n_portal.log"
Write-Host ""
