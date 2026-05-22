# DrainQ ONE — Touch-UI / Cinema-Mode Autorun (Wellen W0-W4)
# Startet Claude Code headless fuer jede Welle sequentiell.
# Jeder claude-Call hat eigenen Context (= implizites /clear).
# Bricht ab, wenn eine Welle fehlschlaegt oder kein RESULT_TOUCHUI_WN.md erzeugt.
#
# Aufruf:
#   cd C:\Projekte\drainq.one
#   .\autorun_touchui.ps1 *>&1 | Tee-Object -FilePath autorun_touchui.log

$ErrorActionPreference = "Stop"
$repo = "C:\Projekte\drainq.one"
$SER = "233b4bd2865177ed"
$branch = "feature/touchui-cinema-mode"

Set-Location $repo

if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = "C:\Android\jdk17"
    Write-Host "JAVA_HOME defaulted to $env:JAVA_HOME" -ForegroundColor Yellow
}

Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "  DrainQ ONE  Touch-UI / Cinema-Mode  Autorun" -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "Repo:    $repo"
Write-Host "Tablet:  $SER"
Write-Host "Branch:  $branch"
Write-Host "Start:   $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Write-Host ""

# =================================================================
#  WELLE W0 — Pre-Flight (im Skript, kein Agent)
# =================================================================
Write-Host "=== Welle W0: Pre-Flight ===" -ForegroundColor Yellow

# 1. Plan-MD muss da sein
if (-not (Test-Path "AUTORUN_TOUCHUI_PLAN.md")) {
    Write-Host "FEHLER: AUTORUN_TOUCHUI_PLAN.md nicht gefunden." -ForegroundColor Red
    exit 1
}
Write-Host "  [OK] Plan-MD vorhanden"

# 2. gradlew vorhanden
if (-not (Test-Path "gradlew.bat")) {
    Write-Host "FEHLER: gradlew.bat nicht im Repo-Root." -ForegroundColor Red
    exit 1
}
Write-Host "  [OK] gradlew.bat vorhanden"

# 3. claude CLI muss installiert sein
$claudeExists = Get-Command claude -ErrorAction SilentlyContinue
if (-not $claudeExists) {
    Write-Host "FEHLER: 'claude' CLI nicht im PATH. Installiere Claude Code." -ForegroundColor Red
    exit 1
}
Write-Host "  [OK] claude CLI vorhanden"

# 4. Master-Branch und Migration-A muss schon drin sein (Commit 13b1384 oder neuer)
$mig = git log --oneline --all 2>$null | Select-String "Migration A" | Select-Object -First 1
if (-not $mig) {
    Write-Host "FEHLER: Migration A (Commit 13b1384) nicht in git log gefunden." -ForegroundColor Red
    exit 1
}
Write-Host "  [OK] Migration A im git log: $mig"

# 5. Build muss durchlaufen (Kotlin + Native)
Write-Host "  Pruefe Build (kann 1-2 min dauern)..."
& .\gradlew.bat compileDebugKotlin --console=plain 2>&1 | Out-File preflight_compile.log -Encoding utf8
$lastLine = Get-Content preflight_compile.log -Tail 5 | Out-String
if (-not ($lastLine -match "BUILD SUCCESSFUL")) {
    Write-Host "FEHLER: compileDebugKotlin nicht erfolgreich. Tail:" -ForegroundColor Red
    Write-Host $lastLine -ForegroundColor Red
    exit 1
}
Remove-Item preflight_compile.log -ErrorAction SilentlyContinue
Write-Host "  [OK] compileDebugKotlin: BUILD SUCCESSFUL"

# 6. Tablet erreichbar
$tabletState = adb -s $SER get-state 2>&1
if ($tabletState -ne "device") {
    Write-Host "WARN: Tablet $SER nicht erreichbar (state=$tabletState). W4 wird Deploy ueberspringen koennen." -ForegroundColor Yellow
} else {
    Write-Host "  [OK] Tablet $SER erreichbar"
}

# 7. Feature-Branch anlegen oder wechseln
$currentBranch = git branch --show-current 2>$null
if ($currentBranch -ne $branch) {
    $branchExists = git branch --list $branch 2>$null
    if ($branchExists) {
        git checkout $branch 2>&1 | Out-Null
        Write-Host "  [OK] Branch $branch ausgecheckt (existierte schon)"
    } else {
        git checkout -b $branch 2>&1 | Out-Null
        Write-Host "  [OK] Branch $branch frisch angelegt aus $currentBranch"
    }
} else {
    Write-Host "  [OK] Branch $branch bereits ausgecheckt"
}

Write-Host ""
Write-Host "Pre-Flight komplett. Starte Code-Wellen W1-W4." -ForegroundColor Green
Write-Host ""

# =================================================================
#  WELLEN W1-W4 — Code-Aenderungen + Build + Deploy + Merge
# =================================================================
$waves = @(
    @{ Num = 1; Model = "sonnet"; Hint = "think";         Desc = "Cinema-Mode-Layout + Tap-State + Slide-In" },
    @{ Num = 2; Model = "sonnet"; Hint = "";              Desc = "Touch-optimierte Button-Groessen + Dimensions.kt" },
    @{ Num = 3; Model = "sonnet"; Hint = "";              Desc = "OSD-Overlay auf Video (96sp, persistent, Schatten)" },
    @{ Num = 4; Model = "haiku";  Hint = "";              Desc = "Build, Deploy, Verify, Merge, Push" }
)

function Build-Prompt {
    param([int]$Num, [int]$Prev, [string]$Desc, [string]$Hint)

    $lines = New-Object System.Collections.Generic.List[string]
    [void]$lines.Add("Lies AUTORUN_TOUCHUI_PLAN.md und fuehre Welle W$Num ($Desc) vollstaendig aus.")
    [void]$lines.Add("")
    if ($Num -gt 1) {
        [void]$lines.Add("Voraussetzung: Lies zuerst RESULT_TOUCHUI_W$Prev.md fuer den Stand der vorherigen Welle.")
        [void]$lines.Add("")
    }
    [void]$lines.Add("Pflicht:")
    [void]$lines.Add("- Branch ist bereits 'feature/touchui-cinema-mode' (vom Skript ausgecheckt).")
    [void]$lines.Add("- Halte dich exakt an die Design-Tokens in AUTORUN_TOUCHUI_PLAN.md Abschnitt 'Design-Tokens'.")
    [void]$lines.Add("- Konsultiere den drainq-kritis-compliance-Skill (steht im CLAUDE.md des Repos).")
    [void]$lines.Add("- Keine Magic-Numbers in der UI - alles ueber Dimensions.*-Konstanten.")
    [void]$lines.Add("- Pruefe nach allen Aenderungen mit ./gradlew compileDebugKotlin dass der Build clean ist.")
    [void]$lines.Add("- Committe deine Aenderungen mit der im Plan vorgegebenen Commit-Message.")
    [void]$lines.Add("- Schreibe am Ende RESULT_TOUCHUI_W$Num.md ins Repo-Root nach dem Result-File-Schema in der Plan-MD.")
    [void]$lines.Add("- Falls etwas nicht klar ist: lieber konservativ entscheiden und im Result-File dokumentieren, NICHT zurueckfragen.")

    if ($Num -eq 4) {
        [void]$lines.Add("")
        [void]$lines.Add("Welle W4 ist die Deploy-/Merge-Welle:")
        [void]$lines.Add("1. ./gradlew assembleDebug")
        [void]$lines.Add("2. adb -s $SER install -r app\build\outputs\apk\debug\app-debug.apk")
        [void]$lines.Add("3. App starten, 15s warten, Logcat auf FATAL EXCEPTION pruefen.")
        [void]$lines.Add("4. Bei Erfolg: git checkout master, git merge --no-ff feature/touchui-cinema-mode, git push origin master")
        [void]$lines.Add("5. Logcat-Ausschnitt und Commit-Hashes ins RESULT_TOUCHUI_W4.md")
    }

    if ($Hint) {
        [void]$lines.Add("")
        [void]$lines.Add($Hint)
    }

    return $lines -join "`n"
}

foreach ($w in $waves) {
    $num = $w.Num
    $prev = $num - 1
    $resultFile = "RESULT_TOUCHUI_W$num.md"

    # Idempotenz: wenn RESULT-File schon existiert, Welle als erledigt betrachten
    if (Test-Path $resultFile) {
        Write-Host "[SKIP] Welle W$num bereits abgeschlossen ($resultFile vorhanden, $((Get-Item $resultFile).Length) Bytes)" -ForegroundColor Yellow
        Write-Host ""
        continue
    }

    Write-Host "================================================================" -ForegroundColor Cyan
    Write-Host "  Welle W$num : $($w.Desc)" -ForegroundColor Cyan
    Write-Host "  Modell: $($w.Model)  Hint: $($w.Hint)  Start: $(Get-Date -Format 'HH:mm:ss')" -ForegroundColor Cyan
    Write-Host "================================================================" -ForegroundColor Cyan

    $prompt = Build-Prompt -Num $num -Prev $prev -Desc $w.Desc -Hint $w.Hint

    # WICHTIG: -p macht claude headless (print mode). Ohne -p oeffnet sich ein interaktiver REPL!
    & claude -p $prompt --model $($w.Model) --dangerously-skip-permissions
    $exitCode = $LASTEXITCODE

    if ($exitCode -ne 0) {
        Write-Host "FEHLER: claude beendete Welle W$num mit Exit-Code $exitCode" -ForegroundColor Red
        exit 1
    }

    if (-not (Test-Path $resultFile)) {
        Write-Host "FEHLER: $resultFile wurde nicht erzeugt. Welle W$num gilt als fehlgeschlagen." -ForegroundColor Red
        exit 1
    }

    Write-Host "  [OK] Welle W$num fertig, $resultFile erzeugt ($((Get-Item $resultFile).Length) Bytes)" -ForegroundColor Green
    Write-Host ""
}

# =================================================================
#  Abschluss
# =================================================================
Write-Host "================================================================" -ForegroundColor Green
Write-Host "  Alle Wellen erfolgreich!" -ForegroundColor Green
Write-Host "  Ende: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" -ForegroundColor Green
Write-Host "================================================================" -ForegroundColor Green
Write-Host ""
Write-Host "Result-Files:"
Get-ChildItem RESULT_TOUCHUI_W*.md | ForEach-Object {
    Write-Host "  $($_.Name)  ($($_.Length) Bytes)"
}
Write-Host ""
Write-Host "Log:        autorun_touchui.log"
Write-Host "Branch:     $branch (gemerged in master via W4)"
Write-Host ""
Write-Host "Naechste Schritte:"
Write-Host "  - RESULT_TOUCHUI_W*.md durchschauen"
Write-Host "  - Falls noetig: am Tablet Smoke-Test der neuen UI durchgehen"
