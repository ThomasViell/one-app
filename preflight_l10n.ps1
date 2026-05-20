# =============================================================================
# DrainQ.ONE - L10n-Portal-Migration Preflight (v2 - tolerant gegen untracked)
# =============================================================================
# Exit-Code 0 = alle Pflicht-Checks gruen. Exit-Code 1 = mindestens FAIL.
# Untracked Files (??) in den Repos werden als WARN behandelt, nicht FAIL.
#
# Aufruf:
#   cd C:\Projekte\drainq.one-localization
#   .\preflight_l10n.ps1
# =============================================================================

$ErrorActionPreference = "Continue"
$failures = @()
$warnings = @()

function Write-Check {
    param([string]$name, [bool]$ok, [string]$detail = "")
    $mark = if ($ok) { "[OK]  " } else { "[FAIL]" }
    $color = if ($ok) { "Green" } else { "Red" }
    Write-Host "  $mark $name" -ForegroundColor $color
    if ($detail) { Write-Host "        $detail" -ForegroundColor DarkGray }
    if (-not $ok) { $script:failures += $name }
}

function Write-Warn {
    param([string]$name, [string]$detail = "")
    Write-Host "  [WARN] $name" -ForegroundColor DarkYellow
    if ($detail) { Write-Host "        $detail" -ForegroundColor DarkGray }
    $script:warnings += $name
}

function Get-RealChanges {
    param([string]$RepoPath)
    Push-Location $RepoPath
    try {
        $all = & git status --porcelain 2>$null
        $relevant = $all | Where-Object { $_ -notmatch '^\?\?' }
        return ($relevant -join "`n").Trim()
    } finally { Pop-Location }
}

function Get-Untracked {
    param([string]$RepoPath)
    Push-Location $RepoPath
    try {
        $all = & git status --porcelain 2>$null
        $untracked = $all | Where-Object { $_ -match '^\?\?' }
        return ($untracked -join "`n").Trim()
    } finally { Pop-Location }
}

Write-Host ""
Write-Host "=== DrainQ.ONE L10n-Portal Preflight ===" -ForegroundColor Cyan
Write-Host "Datum: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Write-Host ""

# 1) Arbeitskopie
$workdir = "C:\Projekte\drainq.one-localization"
if (Test-Path $workdir) {
    Push-Location $workdir
    try {
        $branch = (& git rev-parse --abbrev-ref HEAD 2>$null).Trim()
        Write-Check "Arbeitskopie existiert" $true $workdir
        Write-Check "Branch = feature/l10n-portal" ($branch -eq "feature/l10n-portal") "Aktueller Branch: $branch"
    } finally { Pop-Location }
    $realChg = Get-RealChanges $workdir
    Write-Check "Arbeitskopie ohne echte Aenderungen (kein M/D/A)" ([string]::IsNullOrWhiteSpace($realChg)) $realChg
    $untracked = Get-Untracked $workdir
    if (-not [string]::IsNullOrWhiteSpace($untracked)) {
        Write-Warn "Arbeitskopie hat untracked Files" $untracked
    }
} else {
    Write-Check "Arbeitskopie existiert" $false "Pfad nicht gefunden: $workdir"
}

# 2) Original-Repo
$origdir = "C:\Projekte\drainq.one"
if (Test-Path $origdir) {
    Write-Check "Original-Repo existiert" $true $origdir
    $origReal = Get-RealChanges $origdir
    Write-Check "Original ohne echte Aenderungen" ([string]::IsNullOrWhiteSpace($origReal)) $origReal
    $origUntracked = Get-Untracked $origdir
    if (-not [string]::IsNullOrWhiteSpace($origUntracked)) {
        Write-Warn "Original hat untracked Helper-Files (OK fuer Migration)"
    }
} else {
    Write-Check "Original-Repo existiert" $false "Pfad nicht gefunden: $origdir"
}

# 3) Suite-Repo
$suitedir = "C:\Projekte\DrainQ\drainq_suite_repo"
if (Test-Path $suitedir) {
    Write-Check "Suite-Repo existiert" $true $suitedir
    $suiteReal = Get-RealChanges $suitedir
    Write-Check "Suite-Repo ohne echte Aenderungen" ([string]::IsNullOrWhiteSpace($suiteReal)) $suiteReal
    $suiteUntracked = Get-Untracked $suitedir
    if (-not [string]::IsNullOrWhiteSpace($suiteUntracked)) {
        Write-Warn "Suite-Repo hat untracked Files (lokale Notes - OK)"
    }
} else {
    Write-Check "Suite-Repo existiert" $false "Pfad nicht gefunden: $suitedir"
}

# 4) Portal-Repo (drainq.web)
$portaldir = "C:\Projekte\DrainQ\drainq.web"
if (Test-Path $portaldir) {
    Write-Check "Portal-Repo existiert (drainq.web)" $true $portaldir
    $portalReal = Get-RealChanges $portaldir
    Write-Check "Portal-Repo ohne echte Aenderungen" ([string]::IsNullOrWhiteSpace($portalReal)) $portalReal
    $hasController = Test-Path (Join-Path $portaldir "src\DrainQ.Web\Controllers\TranslationController.cs")
    Write-Check "Portal-Repo enthaelt TranslationController" $hasController
    $portalUntracked = Get-Untracked $portaldir
    if (-not [string]::IsNullOrWhiteSpace($portalUntracked)) {
        Write-Warn "Portal-Repo hat untracked Files (lokale Notes - OK)"
    }
} else {
    Write-Check "Portal-Repo existiert (drainq.web)" $false "Pfad nicht gefunden: $portaldir"
}

# 5) claude CLI
$claudeOk = $false
try {
    $v = & claude --version 2>$null
    $claudeOk = $LASTEXITCODE -eq 0
    Write-Check "claude CLI im PATH" $claudeOk $v
} catch {
    Write-Check "claude CLI im PATH" $false "claude --version fehlgeschlagen"
}

# 6) GodMode-Permissions
$settings = "$env:USERPROFILE\.claude\settings.json"
if (Test-Path $settings) {
    $cfg = Get-Content $settings -Raw
    $hasAllow = $cfg -match '"allow"\s*:' -and $cfg -match 'Bash'
    Write-Check "GodMode-Permissions (.claude/settings.json)" $hasAllow "settings.json hat allow-Liste mit Bash"
} else {
    Write-Check "GodMode-Permissions (.claude/settings.json)" $false "Datei nicht gefunden: $settings"
}

# 7) Telegram-Config
if (Test-Path $workdir) {
    $tg = Join-Path $workdir "telegram-config.local.ps1"
    if (Test-Path $tg) {
        . $tg
        $hasToken = -not [string]::IsNullOrWhiteSpace($env:TELEGRAM_BOT_TOKEN)
        $hasChat  = -not [string]::IsNullOrWhiteSpace($env:TELEGRAM_CHAT_ID)
        Write-Check "Telegram-Config geladen" ($hasToken -and $hasChat) "Token=$($hasToken)  ChatID=$($hasChat)"
        if ($hasToken -and $hasChat) {
            try {
                $url = "https://api.telegram.org/bot$($env:TELEGRAM_BOT_TOKEN)/getMe"
                $r = Invoke-RestMethod -Uri $url -TimeoutSec 5
                Write-Check "Telegram-Bot erreichbar (getMe)" $r.ok "Bot: $($r.result.username)"
            } catch {
                Write-Check "Telegram-Bot erreichbar (getMe)" $false $_.Exception.Message
            }
        }
    } else {
        Write-Check "Telegram-Config vorhanden" $false "telegram-config.local.ps1 fehlt in $workdir"
    }
}

# 8) Gradle Wrapper
if (Test-Path $workdir) {
    $gradle = Join-Path $workdir "gradlew.bat"
    Write-Check "Gradle-Wrapper vorhanden" (Test-Path $gradle) $gradle
}

# 9) Skill godmode
if (Test-Path $workdir) {
    $skill = Join-Path $workdir ".claude\skills\godmode\SKILL.md"
    Write-Check "Skill godmode vorhanden" (Test-Path $skill) $skill
}

# 10) Disk Space
$drive = Get-PSDrive C
$freeGB = [math]::Round($drive.Free / 1GB, 1)
Write-Check "Disk Space >= 5 GB frei auf C:" ($drive.Free -gt 5GB) "$freeGB GB frei"

# 11) Konzept + Phasenplan + Phase-0-Outputs
if (Test-Path $workdir) {
    Write-Check "PHASENPLAN_L10N.md vorhanden"     (Test-Path (Join-Path $workdir "PHASENPLAN_L10N.md"))
    Write-Check "L10N_PORTAL_KONZEPT.md vorhanden" (Test-Path (Join-Path $workdir "docs\concepts\L10N_PORTAL_KONZEPT.md"))
    Write-Check "RESULT_PHASE_0.md vorhanden"      (Test-Path (Join-Path $workdir "RESULT_PHASE_0.md"))
    Write-Check "phase0/keys_de_en.json vorhanden" (Test-Path (Join-Path $workdir "phase0\keys_de_en.json"))
    Write-Check "ADR 0010 vorhanden"               (Test-Path (Join-Path $workdir "docs\adr\0010-l10n-portal-as-sot.md"))
}

# Zusammenfassung
Write-Host ""
if ($failures.Count -eq 0) {
    Write-Host "=== Pflicht-Checks alle GRUEN ===" -ForegroundColor Green
    if ($warnings.Count -gt 0) {
        Write-Host "    ($($warnings.Count) Warnung(en) - nicht blockend)" -ForegroundColor DarkYellow
    }
    Write-Host ""
    Write-Host "Naechste Schritte:" -ForegroundColor Cyan
    Write-Host "  .\autorun_l10n_portal.ps1 *>&1 | Tee-Object -FilePath autorun_l10n_portal.log"
    Write-Host ""
    exit 0
} else {
    Write-Host "=== $($failures.Count) Check(s) ROT ===" -ForegroundColor Red
    foreach ($f in $failures) { Write-Host "  - $f" -ForegroundColor Red }
    Write-Host ""
    exit 1
}
