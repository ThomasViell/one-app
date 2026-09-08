# =============================================================================
# DrainQ.ONE - L10n-Portal Autorun (Phasen 1-7 mit Telegram-Updates)
# =============================================================================
# Arbeitet ausschliesslich in der Kopie C:\Projekte\drainq.one-localization
# Original C:\Projekte\drainq.one bleibt unangetastet.
#
# Aufruf:
#   cd C:\Projekte\drainq.one-localization
#   .\autorun_l10n_portal.ps1 *>&1 | Tee-Object -FilePath autorun_l10n_portal.log
#
# Voraussetzungen (siehe preflight_l10n.ps1):
#   - Phase 0 abgeschlossen: RESULT_PHASE_0.md + phase0/*  + docs/adr/0010-l10n-portal-as-sot.md
#   - claude CLI im PATH, GodMode-Permissions in %USERPROFILE%\.claude\settings.json
#   - telegram-config.local.ps1 vorhanden (Token + ChatId)
#   - Portal-Repo C:\Projekte\drainq.web existiert
# =============================================================================

$ErrorActionPreference = "Stop"

$RepoOne    = "C:\Projekte\drainq.one-localization"
$RepoSuite  = "C:\Projekte\DrainQ\drainq_suite_repo"
$RepoPortal = "C:\Projekte\DrainQ\drainq.web"

Set-Location $RepoOne

# --- Telegram-Config laden ---------------------------------------------------
$tgConfig = Join-Path $RepoOne "telegram-config.local.ps1"
if (Test-Path $tgConfig) {
    . $tgConfig
} else {
    Write-Host "WARNUNG: telegram-config.local.ps1 nicht gefunden - laeuft ohne Telegram." -ForegroundColor Yellow
}

function Send-TelegramMessage {
    param([string]$Text)
    if ([string]::IsNullOrWhiteSpace($env:TELEGRAM_BOT_TOKEN)) { return }
    try {
        $body = @{
            chat_id    = $env:TELEGRAM_CHAT_ID
            text       = $Text
            parse_mode = "HTML"
            disable_web_page_preview = "true"
        }
        $url = "https://api.telegram.org/bot$($env:TELEGRAM_BOT_TOKEN)/sendMessage"
        Invoke-RestMethod -Uri $url -Method Post -Body $body -TimeoutSec 10 | Out-Null
    } catch {
        Write-Host "Telegram send failed: $($_.Exception.Message)" -ForegroundColor DarkYellow
    }
}

# --- Header + Vorbedingungen -------------------------------------------------
Write-Host ""
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host " DrainQ.ONE - L10n-Portal Autorun (Phasen 1-7)" -ForegroundColor Cyan
Write-Host " Start : $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Write-Host " App   : $RepoOne"
Write-Host " Suite : $RepoSuite"
Write-Host " Portal: $RepoPortal"
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host ""

$prereqs = @(
    "PHASENPLAN_L10N.md",
    "RESULT_PHASE_0.md",
    "phase0\keys_de_en.json",
    "phase0\hardcoded_audit_preview.md",
    "docs\adr\0010-l10n-portal-as-sot.md"
)
foreach ($f in $prereqs) {
    if (-not (Test-Path (Join-Path $RepoOne $f))) {
        $msg = "FEHLER: Vorbedingung fehlt - $f"
        Write-Host $msg -ForegroundColor Red
        Send-TelegramMessage "[L10n ABORT] $msg"
        exit 1
    }
}
if (-not (Test-Path $RepoSuite))  { Write-Host "FEHLER: Suite-Repo nicht gefunden: $RepoSuite"   -ForegroundColor Red; Send-TelegramMessage "[L10n ABORT] Suite-Repo fehlt";  exit 1 }
if (-not (Test-Path $RepoPortal)) { Write-Host "FEHLER: Portal-Repo nicht gefunden: $RepoPortal" -ForegroundColor Red; Send-TelegramMessage "[L10n ABORT] Portal-Repo fehlt"; exit 1 }

Write-Host "Vorbedingungen OK." -ForegroundColor Green
Write-Host ""

Send-TelegramMessage @"
<b>L10n-Portal Migration START</b>
Repo: drainq.one-localization
Phasen: 1..7 (sequentiell)
Start: $(Get-Date -Format 'HH:mm:ss')
"@

# --- Phasen-Definition -------------------------------------------------------
# WICHTIG: Repo-Pfade sind aus diesem Autorun (Kopie), nicht aus dem Original.
# Phase 2-4 laufen aber im *Portal*-Repo (drainq.web), nicht im Suite-Repo,
# weil DrainQ.ONE-spezifische Endpoints + Scopes dort gepflegt werden.
$phases = @(
    @{ Num=1; Model="sonnet"; Hint="think";        Repo=$RepoOne;    Branch="feature/l10n-phase-1-mapping";      Desc="Key-Mapping snake -> UPPER_SNAKE + Shared-Detection" },
    @{ Num=2; Model="sonnet"; Hint="think harder"; Repo=$RepoPortal; Branch="feature/l10n-phase-2-backend";      Desc="Portal-Backend: Locale-Status, Scope=one, Partner-Entity, API" },
    @{ Num=3; Model="sonnet"; Hint="think";        Repo=$RepoPortal; Branch="feature/l10n-phase-3-frontend";     Desc="Portal-Frontend: Tab DrainQ.ONE + Partner-Verwaltung" },
    @{ Num=4; Model="sonnet"; Hint="think";        Repo=$RepoPortal; Branch="feature/l10n-phase-4-deepl";        Desc="DeepL-Service + Glossar + Review-Workflow" },
    @{ Num=5; Model="sonnet"; Hint="think harder"; Repo=$RepoOne;    Branch="feature/l10n-phase-5-app-refactor"; Desc="App: LocalizationManager neu (Bundle DE+EN + Lazy-Download)" },
    @{ Num=6; Model="sonnet"; Hint="think";        Repo=$RepoOne;    Branch="feature/l10n-phase-6-hardcoded";    Desc="App: Hardcoded-Audit + Composables auf t(KEY)" },
    @{ Num=7; Model="haiku";  Hint="";             Repo=$RepoOne;    Branch="feature/l10n-phase-7-cutover";      Desc="Cutover: Smoke-Tests, Doku, Release-Notes v0.4.0" }
)

function Build-Prompt {
    param([int]$Num, [int]$Prev, [string]$Desc, [string]$Hint, [string]$Repo, [string]$Branch)
    $lines = New-Object System.Collections.Generic.List[string]
    [void]$lines.Add("GodMode aktiv. Konsultiere ZUERST den Skill 'godmode'.")
    [void]$lines.Add("")
    [void]$lines.Add("Lies $RepoOne\PHASENPLAN_L10N.md vollstaendig und fuehre Phase $Num ($Desc) komplett aus.")
    [void]$lines.Add("")
    [void]$lines.Add("Aktives Arbeits-Repo fuer diese Phase: $Repo")
    [void]$lines.Add("Branch fuer diese Phase: $Branch")
    [void]$lines.Add("")
    [void]$lines.Add("WICHTIG: Original-Repo C:\Projekte\drainq.one darf NICHT veraendert werden. Alle App-Aenderungen passieren in $RepoOne (Arbeitskopie).")
    [void]$lines.Add("")
    [void]$lines.Add("Voraussetzung: Lies zuerst $RepoOne\RESULT_PHASE_$Prev.md fuer den aktuellen Stand und uebernimm die dortigen Entscheidungen.")
    [void]$lines.Add("Lies auch $RepoOne\docs\concepts\L10N_PORTAL_KONZEPT.md (Konzept v1.1) und $RepoOne\docs\adr\0010-l10n-portal-as-sot.md.")
    [void]$lines.Add("")
    [void]$lines.Add("Pflicht:")
    [void]$lines.Add("- Branch $Branch aus dem Default-Branch des Repos anlegen und alle Aenderungen darin committen (Conventional Commits).")
    [void]$lines.Add("- Keine hardcodierten Strings/Farben/Secrets.")
    [void]$lines.Add("- Build muss am Ende gruen sein.")
    [void]$lines.Add("- Tests muessen am Ende gruen sein.")
    [void]$lines.Add("- Tempdatei-Pattern fuer alle Shell-Befehle > 600 Zeichen (siehe godmode-Skill).")
    [void]$lines.Add("- Wenn ein Befehl zu Quoting-Problemen fuehrt, weiche auf File-Tools (Read/Write/Edit/Glob/Grep) aus.")
    [void]$lines.Add("")
    [void]$lines.Add("Am Ende schreibe RESULT_PHASE_$Num.md immer ins Repo-Root von $RepoOne (auch wenn die Aenderungen im Portal- oder Suite-Repo erfolgt sind).")
    [void]$lines.Add("")
    [void]$lines.Add("RESULT_PHASE_$Num.md muss enthalten:")
    [void]$lines.Add("- Was wurde geliefert (Datei-Liste, Kommentar)")
    [void]$lines.Add("- Diff-Summary je Repo (Datei-Anzahl, +Zeilen/-Zeilen)")
    [void]$lines.Add("- Branch + Commit-Hashes pro Repo")
    [void]$lines.Add("- Pragmatische Entscheidungen (Abweichungen vom Plan)")
    [void]$lines.Add("- Build-Status (App: gradle, Portal: dotnet)")
    [void]$lines.Add("- Test-Status (Zahlen, gruen/rot)")
    [void]$lines.Add("- KRITIS-Check-Status")
    [void]$lines.Add("- Bekannte Issues / Offene Punkte")

    if ($Num -eq 1) {
        [void]$lines.Add("")
        [void]$lines.Add("PFLICHT-MARKER (einzeilig, exakt):")
        [void]$lines.Add("PHASE1-MAPPED: <Anzahl Keys gemappt>")
        [void]$lines.Add("PHASE1-SHARED: <Anzahl shared-Keys>")
        [void]$lines.Add("PHASE1-CONFLICTS: <Anzahl Konflikte>")
    }
    if ($Num -eq 2) {
        [void]$lines.Add("")
        [void]$lines.Add("PFLICHT-MARKER:")
        [void]$lines.Add("PHASE2-MIGRATION-OK: yes|no")
        [void]$lines.Add("PHASE2-ENDPOINTS-COUNT: <Anzahl neuer/erweiterter Endpoints>")
    }
    if ($Num -eq 5) {
        [void]$lines.Add("")
        [void]$lines.Add("PFLICHT-MARKER:")
        [void]$lines.Add("PHASE5-APK-SIZE-BEFORE-MB: <Wert>")
        [void]$lines.Add("PHASE5-APK-SIZE-AFTER-MB: <Wert>")
        [void]$lines.Add("PHASE5-LM-LINES-BEFORE: <Wert>")
        [void]$lines.Add("PHASE5-LM-LINES-AFTER: <Wert>")
    }
    if ($Num -eq 6) {
        [void]$lines.Add("")
        [void]$lines.Add("PFLICHT-MARKER:")
        [void]$lines.Add("PHASE6-HARDCODED-FOUND: <Anzahl>")
        [void]$lines.Add("PHASE6-HARDCODED-FIXED: <Anzahl>")
    }
    if ($Num -eq 7) {
        [void]$lines.Add("")
        [void]$lines.Add("PFLICHT-MARKER:")
        [void]$lines.Add("PHASE7-SMOKE-PASSED: <Anzahl>")
        [void]$lines.Add("PHASE7-SMOKE-FAILED: <Anzahl>")
        [void]$lines.Add("PHASE7-RELEASE-NOTES-PATH: <relativer Pfad>")
    }

    if ($Hint -ne "") { [void]$lines.Add(""); [void]$lines.Add($Hint) }
    return ($lines -join "`n")
}

function Get-ResultExcerpt {
    param([string]$Path)
    if (-not (Test-Path $Path)) { return "" }
    $lines = Get-Content $Path -ErrorAction SilentlyContinue
    $markers = $lines | Where-Object { $_ -match "^PHASE\d+-" } | Select-Object -First 6
    return ($markers -join "`n")
}

# --- Ausfuehrung -------------------------------------------------------------
$startAll = Get-Date

foreach ($p in $phases) {
    $num    = [int]$p.Num
    $prev   = $num - 1
    $desc   = [string]$p.Desc
    $model  = [string]$p.Model
    $hint   = [string]$p.Hint
    $repo   = [string]$p.Repo
    $branch = [string]$p.Branch

    # Idempotent: Phase ueberspringen, wenn RESULT-File schon da ist
    $resultPath = Join-Path $RepoOne "RESULT_PHASE_$num.md"
    if (Test-Path $resultPath) {
        Write-Host "Phase $num bereits abgeschlossen (RESULT-File vorhanden). Skip." -ForegroundColor DarkGray
        Send-TelegramMessage "[P$num SKIP] $desc (Result-File bereits vorhanden)"
        continue
    }

    $phaseStart = Get-Date

    Write-Host ""
    Write-Host "================================================================" -ForegroundColor Cyan
    Write-Host " Phase $num - $desc" -ForegroundColor Cyan
    Write-Host " Modell: $model  Effort: $hint" -ForegroundColor Cyan
    Write-Host " Repo  : $repo" -ForegroundColor Cyan
    Write-Host " Branch: $branch" -ForegroundColor Cyan
    Write-Host " Start : $(Get-Date -Format 'HH:mm:ss')"
    Write-Host "================================================================" -ForegroundColor Cyan

    Send-TelegramMessage @"
<b>[P$num START]</b> $desc
Modell: $model ($hint)
Repo: $(Split-Path $repo -Leaf)
Branch: $branch
"@

    Set-Location $repo

    $prompt    = Build-Prompt -Num $num -Prev $prev -Desc $desc -Hint $hint -Repo $repo -Branch $branch
    $tmpPrompt = Join-Path $env:TEMP "drainq-l10n-portal-phase-$num-$([guid]::NewGuid()).txt"
    $prompt | Set-Content -Path $tmpPrompt -Encoding UTF8

    $exitCode = 0
    try {
        Get-Content -Raw -Path $tmpPrompt | & claude -p --model $model --dangerously-skip-permissions
        $exitCode = $LASTEXITCODE
    } finally {
        Remove-Item -Force $tmpPrompt -ErrorAction SilentlyContinue
    }

    $dur     = (Get-Date) - $phaseStart
    $durStr  = "{0}m {1}s" -f [int]$dur.TotalMinutes, $dur.Seconds

    if ($exitCode -ne 0) {
        $msg = "[P$num FAIL] $desc - claude exit=$exitCode nach $durStr"
        Write-Host ""
        Write-Host "ABBRUCH: $msg" -ForegroundColor Red
        Send-TelegramMessage "<b>$msg</b>"
        exit 1
    }

    if (-not (Test-Path $resultPath)) {
        $msg = "[P$num FAIL] $desc - kein RESULT_PHASE_$num.md erzeugt nach $durStr"
        Write-Host ""
        Write-Host "ABBRUCH: $msg" -ForegroundColor Red
        Send-TelegramMessage "<b>$msg</b>"
        exit 1
    }

    $excerpt = Get-ResultExcerpt $resultPath
    $tgMsg = "<b>[P$num OK]</b> $desc`nDauer: $durStr`n"
    if ($excerpt) { $tgMsg += "`n<pre>$excerpt</pre>" }
    Send-TelegramMessage $tgMsg

    Write-Host ""
    Write-Host "Phase $num abgeschlossen ($durStr). Result: $resultPath" -ForegroundColor Green
}

# --- Abschluss ---------------------------------------------------------------
$endAll  = Get-Date
$durAll  = $endAll - $startAll
$durStr  = "{0}h {1}m" -f [int]$durAll.TotalHours, $durAll.Minutes

Write-Host ""
Write-Host "================================================================" -ForegroundColor Green
Write-Host " ALLE PHASEN ABGESCHLOSSEN ($durStr)" -ForegroundColor Green
Write-Host "================================================================" -ForegroundColor Green

Send-TelegramMessage @"
<b>L10n-Portal Migration FERTIG</b>
Dauer gesamt: $durStr
Phasen: 7/7 OK

Naechste Schritte:
1) APK aus drainq.one-localization bauen
2) Smoke-Test auf Tablet (DE + EN)
3) Entscheidung Merge zurueck nach drainq.one
"@

Write-Host ""
Write-Host "Berichte:" -ForegroundColor Green
Get-ChildItem -Path $RepoOne -Filter "RESULT_PHASE_*.md" | Sort-Object Name | ForEach-Object {
    Write-Host ("  - " + $_.FullName)
}
