# =============================================================================
# DrainQ.ONE — L10n-Portal-Migration Preflight
# =============================================================================
# Prueft alle Voraussetzungen vor Phase 0 und dem Autorun.
# Exit-Code 0 = alle Checks gruen. Exit-Code 1 = mindestens ein Check rot.
#
# Aufruf:
#   cd C:\Projekte\drainq.one-localization
#   .\preflight_l10n.ps1
# =============================================================================

$ErrorActionPreference = "Continue"
$failures = @()

function Write-Check {
    param([string]$name, [bool]$ok, [string]$detail = "")
    $mark = if ($ok) { "[OK]  " } else { "[FAIL]" }
    $color = if ($ok) { "Green" } else { "Red" }
    Write-Host "  $mark $name" -ForegroundColor $color
    if ($detail) { Write-Host "        $detail" -ForegroundColor DarkGray }
    if (-not $ok) { $script:failures += $name }
}

Write-Host ""
Write-Host "=== DrainQ.ONE L10n-Portal Preflight ===" -ForegroundColor Cyan
Write-Host "Datum: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Write-Host ""

# -----------------------------------------------------------------------------
# 1) Arbeitskopie sauber
# -----------------------------------------------------------------------------
$workdir = "C:\Projekte\drainq.one-localization"
if (Test-Path $workdir) {
    Push-Location $workdir
    try {
        $branch = (git rev-parse --abbrev-ref HEAD 2>$null).Trim()
        $status = git status --porcelain 2>$null
        Write-Check "Arbeitskopie existiert" $true $workdir
        Write-Check "Branch = feature/l10n-portal" ($branch -eq "feature/l10n-portal") "Aktueller Branch: $branch"
        Write-Check "Arbeitskopie sauber (keine uncommitted changes)" ([string]::IsNullOrWhiteSpace($status)) ($status | Out-String).Trim()
    } finally { Pop-Location }
} else {
    Write-Check "Arbeitskopie existiert" $false "Pfad nicht gefunden: $workdir. Erst setup_l10n_workdir.ps1 laufen lassen."
}

# -----------------------------------------------------------------------------
# 2) Original-Repo unangetastet
# -----------------------------------------------------------------------------
$origdir = "C:\Projekte\drainq.one"
if (Test-Path $origdir) {
    Push-Location $origdir
    try {
        $origBranch = (git rev-parse --abbrev-ref HEAD 2>$null).Trim()
        $origStatus = git status --porcelain 2>$null | Where-Object { $_ -notmatch "_l10n_bundle/|setup_l10n_workdir.ps1" }
        Write-Check "Original-Repo existiert" $true $origdir
        Write-Check "Original sauber (ausser _l10n_bundle/ + setup-Skript)" ([string]::IsNullOrWhiteSpace(($origStatus | Out-String))) ($origStatus | Out-String).Trim()
    } finally { Pop-Location }
} else {
    Write-Check "Original-Repo existiert" $false "Pfad nicht gefunden: $origdir"
}

# -----------------------------------------------------------------------------
# 3) Suite-Repo
# -----------------------------------------------------------------------------
$suitedir = "C:\Projekte\DrainQ\drainq_suite_repo"
if (Test-Path $suitedir) {
    Push-Location $suitedir
    try {
        $suiteStatus = git status --porcelain 2>$null
        Write-Check "Suite-Repo existiert" $true $suitedir
        Write-Check "Suite-Repo sauber" ([string]::IsNullOrWhiteSpace($suiteStatus)) ($suiteStatus | Out-String).Trim()
    } finally { Pop-Location }
} else {
    Write-Check "Suite-Repo existiert" $false "Pfad nicht gefunden: $suitedir"
}

# -----------------------------------------------------------------------------
# 4) Portal-Repo (drainq.web)
# -----------------------------------------------------------------------------
$portaldir = "C:\Projekte\DrainQ\drainq.web"
if (Test-Path $portaldir) {
    Push-Location $portaldir
    try {
        $portalStatus = git status --porcelain 2>$null
        Write-Check "Portal-Repo existiert (drainq.web)" $true $portaldir
        Write-Check "Portal-Repo sauber" ([string]::IsNullOrWhiteSpace($portalStatus)) ($portalStatus | Out-String).Trim()
        # Pruefe ob Translation-Infrastruktur da ist (Sanity)
        $hasController = Test-Path (Join-Path $portaldir "src\DrainQ.Web\Controllers\TranslationController.cs")
        Write-Check "Portal-Repo enthaelt TranslationController" $hasController
    } finally { Pop-Location }
} else {
    Write-Check "Portal-Repo existiert (drainq.web)" $false "Pfad nicht gefunden: $portaldir. Phase 2-4 koennen so nicht laufen."
}

# -----------------------------------------------------------------------------
# 5) claude CLI
# -----------------------------------------------------------------------------
$claudeOk = $false
try {
    $v = & claude --version 2>$null
    $claudeOk = $LASTEXITCODE -eq 0
    Write-Check "claude CLI im PATH" $claudeOk $v
} catch {
    Write-Check "claude CLI im PATH" $false "claude --version fehlgeschlagen"
}

# -----------------------------------------------------------------------------
# 6) GodMode-Permissions
# -----------------------------------------------------------------------------
$settings = "$env:USERPROFILE\.claude\settings.json"
if (Test-Path $settings) {
    $cfg = Get-Content $settings -Raw
    $hasAllow = $cfg -match '"allow"\s*:' -and $cfg -match 'Bash'
    Write-Check "GodMode-Permissions (.claude/settings.json)" $hasAllow "settings.json hat allow-Liste mit Bash"
} else {
    Write-Check "GodMode-Permissions (.claude/settings.json)" $false "Datei nicht gefunden: $settings"
}

# -----------------------------------------------------------------------------
# 7) Telegram-Config
# -----------------------------------------------------------------------------
if (Test-Path $workdir) {
    $tg = Join-Path $workdir "telegram-config.local.ps1"
    if (Test-Path $tg) {
        . $tg
        $hasToken = -not [string]::IsNullOrWhiteSpace($env:TELEGRAM_BOT_TOKEN)
        $hasChat  = -not [string]::IsNullOrWhiteSpace($env:TELEGRAM_CHAT_ID)
        Write-Check "Telegram-Config geladen" ($hasToken -and $hasChat) "Token=$($hasToken)  ChatID=$($hasChat)"

        # Ping
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

# -----------------------------------------------------------------------------
# 8) Gradle Wrapper
# -----------------------------------------------------------------------------
if (Test-Path $workdir) {
    $gradle = Join-Path $workdir "gradlew.bat"
    Write-Check "Gradle-Wrapper vorhanden" (Test-Path $gradle) $gradle
}

# -----------------------------------------------------------------------------
# 9) Skill godmode
# -----------------------------------------------------------------------------
if (Test-Path $workdir) {
    $skill = Join-Path $workdir ".claude\skills\godmode\SKILL.md"
    Write-Check "Skill godmode vorhanden" (Test-Path $skill) $skill
}

# -----------------------------------------------------------------------------
# 10) Disk Space (mind. 5 GB auf C:)
# -----------------------------------------------------------------------------
$drive = Get-PSDrive C
$freeGB = [math]::Round($drive.Free / 1GB, 1)
Write-Check "Disk Space >= 5 GB frei auf C:" ($drive.Free -gt 5GB) "$freeGB GB frei"

# -----------------------------------------------------------------------------
# 11) Konzept + Phasenplan in der Arbeitskopie
# -----------------------------------------------------------------------------
if (Test-Path $workdir) {
    Write-Check "PHASENPLAN_L10N.md vorhanden"     (Test-Path (Join-Path $workdir "PHASENPLAN_L10N.md"))
    Write-Check "L10N_PORTAL_KONZEPT.md vorhanden" (Test-Path (Join-Path $workdir "docs\concepts\L10N_PORTAL_KONZEPT.md"))
}

# -----------------------------------------------------------------------------
# Zusammenfassung
# -----------------------------------------------------------------------------
Write-Host ""
if ($failures.Count -eq 0) {
    Write-Host "=== Alle Checks GRUEN ===" -ForegroundColor Green
    Write-Host ""
    Write-Host "Naechste Schritte:" -ForegroundColor Cyan
    Write-Host "  1) Phase 0 manuell:  .\start_phase0.ps1 *>&1 | Tee-Object -FilePath phase0.log"
    Write-Host "  2) Pruefe RESULT_PHASE_0.md"
    Write-Host "  3) Autorun:          .\autorun_l10n_portal.ps1 *>&1 | Tee-Object -FilePath autorun_l10n_portal.log"
    Write-Host ""
    exit 0
} else {
    Write-Host "=== $($failures.Count) Check(s) ROT ===" -ForegroundColor Red
    foreach ($f in $failures) { Write-Host "  - $f" -ForegroundColor Red }
    Write-Host ""
    exit 1
}
