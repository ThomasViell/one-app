# DrainQ ONE — L10N Portal Autorun (Phasen 1-7)
# Startet Claude Code headless fuer jede Phase sequentiell.
# Cross-Repo: arbeitet abwechselnd in drainq.one und Drainq_Suite_repo.
# Jeder claude-Call hat eigenen Context (= implizites /clear).
# Bricht ab, wenn eine Phase fehlschlaegt oder kein RESULT_PHASE_N.md erzeugt.
#
# Aufruf:
#   .\autorun_l10n.ps1 *>&1 | Tee-Object -FilePath autorun_l10n.log
#
# Voraussetzung:
#   - Phase 0 ist abgeschlossen, RESULT_PHASE_0.md, phase0/keys_de_en.json,
#     phase0/hardcoded_audit_preview.md und docs/adr/0010-l10n-portal-as-sot.md liegen vor.
#   - %USERPROFILE%\.claude\settings.json hat Permissions allow: Bash(*), Edit(*), Write(*), Read(*), Glob(*), Grep(*)
#   - claude CLI im PATH

$ErrorActionPreference = "Stop"
$RepoOne   = "C:\Projekte\drainq.one"
$RepoSuite = "C:\Projekte\Drainq\Drainq_Suite_repo"
Set-Location $RepoOne

Write-Host "================================================================" -ForegroundColor Cyan
Write-Host " DrainQ ONE - L10N Portal Autorun" -ForegroundColor Cyan
Write-Host " Start: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Write-Host " Repo (App)  : $RepoOne"
Write-Host " Repo (Web)  : $RepoSuite"
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host ""

# --- Vorbedingungen ---
$prereqs = @(
    "PHASENPLAN_L10N.md",
    "RESULT_PHASE_0.md",
    "phase0\keys_de_en.json",
    "phase0\hardcoded_audit_preview.md",
    "docs\adr\0010-l10n-portal-as-sot.md"
)
foreach ($f in $prereqs) {
    if (-not (Test-Path (Join-Path $RepoOne $f))) {
        Write-Host "FEHLER: Vorbedingung fehlt - $f" -ForegroundColor Red
        exit 1
    }
}
if (-not (Test-Path $RepoSuite)) {
    Write-Host "FEHLER: Suite-Repo nicht gefunden: $RepoSuite" -ForegroundColor Red
    exit 1
}
Write-Host "Vorbedingungen OK." -ForegroundColor Green
Write-Host ""

# --- Phasen-Definition ---
$phases = @(
    @{ Num=1; Model="sonnet"; Hint="think";        Repo=$RepoOne;   Branch="feature/l10n-phase-1-mapping";        Desc="Key-Mapping snake -> UPPER_SNAKE + Shared-Detection" },
    @{ Num=2; Model="sonnet"; Hint="think harder"; Repo=$RepoSuite; Branch="feature/l10n-phase-2-backend";        Desc="Portal-Backend: Locale-Status, Scope, Partner-Entity, API" },
    @{ Num=3; Model="sonnet"; Hint="think";        Repo=$RepoSuite; Branch="feature/l10n-phase-3-frontend";       Desc="Portal-Frontend: Tab DrainQ.ONE + Sprachen/Partner-UI" },
    @{ Num=4; Model="sonnet"; Hint="think";        Repo=$RepoSuite; Branch="feature/l10n-phase-4-deepl";          Desc="DeepL-Service + Glossar + Review-Workflow" },
    @{ Num=5; Model="sonnet"; Hint="think harder"; Repo=$RepoOne;   Branch="feature/l10n-phase-5-app-refactor";   Desc="ONE-App: LocalizationManager neu (Bundle DE+EN + Lazy)" },
    @{ Num=6; Model="sonnet"; Hint="think";        Repo=$RepoOne;   Branch="feature/l10n-phase-6-hardcoded";      Desc="ONE-App: Hardcoded-Audit + Composables auf t(KEY)" },
    @{ Num=7; Model="haiku";  Hint="";             Repo=$RepoOne;   Branch="feature/l10n-phase-7-cutover";        Desc="Cutover: Smoke-Tests, Doku, Release-Notes v0.4.0" }
)

function Build-Prompt {
    param([int]$Num, [int]$Prev, [string]$Desc, [string]$Hint, [string]$Repo, [string]$Branch)

    $lines = New-Object System.Collections.Generic.List[string]
    [void]$lines.Add("GodMode aktiv. Konsultiere ZUERST den Skill 'godmode' und den Skill 'drainq-kritis-compliance'.")
    [void]$lines.Add("")
    [void]$lines.Add("Lies C:\Projekte\drainq.one\PHASENPLAN_L10N.md vollstaendig und fuehre Phase $Num ($Desc) komplett aus.")
    [void]$lines.Add("")
    [void]$lines.Add("Aktives Arbeits-Repo fuer diese Phase: $Repo")
    [void]$lines.Add("Branch fuer diese Phase: $Branch")
    [void]$lines.Add("")
    [void]$lines.Add("Voraussetzung: Lies zuerst C:\Projekte\drainq.one\RESULT_PHASE_$Prev.md fuer den aktuellen Stand und uebernimm die dortigen Entscheidungen.")
    [void]$lines.Add("Lies auch C:\Projekte\drainq.one\docs\concepts\L10N_PORTAL_KONZEPT.md (Konzept v1.1) und C:\Projekte\drainq.one\docs\adr\0010-l10n-portal-as-sot.md.")
    [void]$lines.Add("")
    [void]$lines.Add("Pflicht:")
    [void]$lines.Add("- Branch $Branch aus dem Default-Branch des Repos anlegen und alle Aenderungen darin committen (Conventional Commits).")
    [void]$lines.Add("- Skill 'drainq-kritis-compliance' MUSS angewendet werden.")
    [void]$lines.Add("- Keine hardcodierten Strings/Farben/Secrets.")
    [void]$lines.Add("- Build muss am Ende gruen sein.")
    [void]$lines.Add("- Tests muessen am Ende gruen sein.")
    [void]$lines.Add("- Tempdatei-Pattern fuer alle Shell-Befehle > 600 Zeichen (siehe godmode-Skill).")
    [void]$lines.Add("- Wenn ein Befehl zu Quoting-Problemen fuehrt, weiche auf File-Tools (Read/Write/Edit/Glob/Grep) aus.")
    [void]$lines.Add("")
    [void]$lines.Add("Am Ende schreibe RESULT_PHASE_$Num.md immer ins Repo-Root von C:\Projekte\drainq.one (nicht ins Suite-Repo), unabhaengig davon, in welchem Repo die Aenderungen erfolgt sind.")
    [void]$lines.Add("")
    [void]$lines.Add("RESULT_PHASE_$Num.md muss enthalten:")
    [void]$lines.Add("- Was wurde geliefert (Datei-Liste, Kommentar)")
    [void]$lines.Add("- Diff-Summary je Repo (Datei-Anzahl, +Zeilen/-Zeilen)")
    [void]$lines.Add("- Branch + Commit-Hashes pro Repo (drainq.one und/oder Drainq_Suite_repo)")
    [void]$lines.Add("- Pragmatische Entscheidungen (Abweichungen vom Plan)")
    [void]$lines.Add("- Build-Status (App: gradle, Web: dotnet)")
    [void]$lines.Add("- Test-Status (Zahlen, gruen/rot)")
    [void]$lines.Add("- KRITIS-Check-Status")
    [void]$lines.Add("- Bekannte Issues / Offene Punkte")

    # Phasen-spezifische Marker / Hinweise
    if ($Num -eq 1) {
        [void]$lines.Add("")
        [void]$lines.Add("PFLICHT-MARKER im RESULT_PHASE_1.md (einzeilig, exakt):")
        [void]$lines.Add("PHASE1-MAPPED: <Anzahl Keys gemappt>")
        [void]$lines.Add("PHASE1-SHARED: <Anzahl shared-Keys>")
        [void]$lines.Add("PHASE1-CONFLICTS: <Anzahl Konflikte aus conflicts.md>")
    }
    if ($Num -eq 2) {
        [void]$lines.Add("")
        [void]$lines.Add("PFLICHT-MARKER im RESULT_PHASE_2.md:")
        [void]$lines.Add("PHASE2-MIGRATION-OK: yes|no")
        [void]$lines.Add("PHASE2-ENDPOINTS-COUNT: <Anzahl neuer/erweiterter Endpoints>")
    }
    if ($Num -eq 5) {
        [void]$lines.Add("")
        [void]$lines.Add("PFLICHT-MARKER im RESULT_PHASE_5.md:")
        [void]$lines.Add("PHASE5-APK-SIZE-BEFORE-MB: <Wert>")
        [void]$lines.Add("PHASE5-APK-SIZE-AFTER-MB: <Wert>")
        [void]$lines.Add("PHASE5-LM-LINES-BEFORE: <Wert>")
        [void]$lines.Add("PHASE5-LM-LINES-AFTER: <Wert>")
    }
    if ($Num -eq 6) {
        [void]$lines.Add("")
        [void]$lines.Add("PFLICHT-MARKER im RESULT_PHASE_6.md:")
        [void]$lines.Add("PHASE6-HARDCODED-FOUND: <Anzahl>")
        [void]$lines.Add("PHASE6-HARDCODED-FIXED: <Anzahl>")
    }
    if ($Num -eq 7) {
        [void]$lines.Add("")
        [void]$lines.Add("PFLICHT-MARKER im RESULT_PHASE_7.md:")
        [void]$lines.Add("PHASE7-SMOKE-PASSED: <Anzahl>")
        [void]$lines.Add("PHASE7-SMOKE-FAILED: <Anzahl>")
        [void]$lines.Add("PHASE7-RELEASE-NOTES-PATH: <relativer Pfad>")
    }

    if ($Hint -ne "") {
        [void]$lines.Add("")
        [void]$lines.Add($Hint)
    }

    return ($lines -join "`n")
}

# --- Ausfuehrung ---
$startAll = Get-Date

foreach ($p in $phases) {
    $num   = [int]$p.Num
    $prev  = $num - 1
    $desc  = [string]$p.Desc
    $model = [string]$p.Model
    $hint  = [string]$p.Hint
    $repo  = [string]$p.Repo
    $branch = [string]$p.Branch

    Write-Host ""
    Write-Host "================================================================" -ForegroundColor Cyan
    Write-Host " Phase $num - $desc" -ForegroundColor Cyan
    Write-Host " Modell: $model  Effort: $hint" -ForegroundColor Cyan
    Write-Host " Repo  : $repo" -ForegroundColor Cyan
    Write-Host " Branch: $branch" -ForegroundColor Cyan
    Write-Host " Start : $(Get-Date -Format 'HH:mm:ss')"
    Write-Host "================================================================" -ForegroundColor Cyan

    # Working Directory pro Phase setzen
    Set-Location $repo

    $prompt = Build-Prompt -Num $num -Prev $prev -Desc $desc -Hint $hint -Repo $repo -Branch $branch

    # Prompt in Tempdatei (Pflicht laut godmode-Skill ueber 600 Zeichen)
    $tmpPrompt = Join-Path $env:TEMP "drainq-l10n-phase-$num-$([guid]::NewGuid()).txt"
    $prompt | Set-Content -Path $tmpPrompt -Encoding UTF8

    try {
        # Headless Claude Call mit GodMode (Permissions via Settings)
        Get-Content -Raw -Path $tmpPrompt | & claude -p --model $model --dangerously-skip-permissions
    }
    finally {
        Remove-Item -Force $tmpPrompt -ErrorAction SilentlyContinue
    }

    if ($LASTEXITCODE -ne 0) {
        Write-Host ""
        Write-Host "ABBRUCH: Phase $num exit-code $LASTEXITCODE" -ForegroundColor Red
        exit 1
    }

    # RESULT-File muss in drainq.one liegen (auch wenn die Phase im Suite-Repo gearbeitet hat)
    $resultPath = Join-Path $RepoOne "RESULT_PHASE_$num.md"
    if (-not (Test-Path $resultPath)) {
        Write-Host ""
        Write-Host "ABBRUCH: $resultPath wurde nicht erzeugt." -ForegroundColor Red
        exit 1
    }

    Write-Host ""
    Write-Host "Phase $num abgeschlossen. Ergebnis: $resultPath" -ForegroundColor Green
    Write-Host "Ende  : $(Get-Date -Format 'HH:mm:ss')"
}

$endAll = Get-Date
$dur = $endAll - $startAll

Write-Host ""
Write-Host "================================================================" -ForegroundColor Green
Write-Host " ALLE PHASEN ABGESCHLOSSEN" -ForegroundColor Green
Write-Host " Start: $($startAll.ToString('yyyy-MM-dd HH:mm:ss'))" -ForegroundColor Green
Write-Host " Ende : $($endAll.ToString('yyyy-MM-dd HH:mm:ss'))" -ForegroundColor Green
Write-Host " Dauer: $($dur.ToString())" -ForegroundColor Green
Write-Host "================================================================" -ForegroundColor Green
Write-Host ""
Write-Host "Berichte:" -ForegroundColor Green
Get-ChildItem -Path $RepoOne -Filter "RESULT_PHASE_*.md" | Sort-Object Name | ForEach-Object {
    Write-Host ("  - " + $_.FullName)
}
