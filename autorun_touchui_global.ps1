# DrainQ ONE - Touch-UI Global Autorun (Wellen W5-W10)
# Idempotent: bereits abgeschlossene Wellen werden uebersprungen.
# Telegram-Notifications via telegram-config.local.ps1 (optional).
#
# Aufruf:
#   cd C:\Projekte\drainq.one
#   .\autorun_touchui_global.ps1 *>&1 | Tee-Object -FilePath autorun_touchui_global.log

$ErrorActionPreference = "Stop"
$repo = "C:\Projekte\drainq.one"
$SER = "233b4bd2865177ed"
$branch = "feature/touchui-global"
$baseCommit = "7272b15"

Set-Location $repo

# --- Telegram-Config laden (falls vorhanden) ---
if (Test-Path ".\telegram-config.local.ps1") {
    . .\telegram-config.local.ps1
}

# --- Telegram-Notifications (silent no-op wenn Env-Vars leer) ---
function Send-TelegramMessage {
    param([Parameter(Mandatory)][string]$Text)
    if (-not $env:TELEGRAM_BOT_TOKEN -or -not $env:TELEGRAM_CHAT_ID) { return }
    try {
        $maxLen = 3500
        $msg = if ($Text.Length -gt $maxLen) { $Text.Substring(0, $maxLen) + "`n...(gekuerzt)" } else { $Text }
        $body = @{
            chat_id = $env:TELEGRAM_CHAT_ID
            text = $msg
            disable_web_page_preview = $true
        }
        Invoke-RestMethod -Uri "https://api.telegram.org/bot$($env:TELEGRAM_BOT_TOKEN)/sendMessage" `
                          -Method Post -Body $body -TimeoutSec 10 | Out-Null
    } catch {
        Write-Host "Telegram-Fehler: $($_.Exception.Message)" -ForegroundColor Yellow
    }
}

if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = "C:\Android\jdk17"
    Write-Host "JAVA_HOME defaulted to $env:JAVA_HOME" -ForegroundColor Yellow
}

Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "  DrainQ ONE  Touch-UI GLOBAL  Autorun" -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "Repo:    $repo"
Write-Host "Tablet:  $SER"
Write-Host "Branch:  $branch"
Write-Host "Base:    $baseCommit (W4 Merge)"
Write-Host "Start:   $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Write-Host ""

# =================================================================
#  W0 - Pre-Flight
# =================================================================
Write-Host "=== Welle W0: Pre-Flight ===" -ForegroundColor Yellow

if (-not (Test-Path "AUTORUN_TOUCHUI_GLOBAL_PLAN.md")) {
    Write-Host "FEHLER: AUTORUN_TOUCHUI_GLOBAL_PLAN.md nicht gefunden." -ForegroundColor Red
    exit 1
}
Write-Host "  [OK] Plan-MD vorhanden"

if (-not (Test-Path "gradlew.bat")) {
    Write-Host "FEHLER: gradlew.bat nicht im Repo-Root." -ForegroundColor Red
    exit 1
}
Write-Host "  [OK] gradlew.bat vorhanden"

$claudeExists = Get-Command claude -ErrorAction SilentlyContinue
if (-not $claudeExists) {
    Write-Host "FEHLER: claude CLI nicht im PATH." -ForegroundColor Red
    exit 1
}
Write-Host "  [OK] claude CLI vorhanden"

$baseFound = git log --all --oneline 2>$null | Select-String $baseCommit | Select-Object -First 1
if (-not $baseFound) {
    Write-Host "FEHLER: Base-Commit $baseCommit (W4-Merge) nicht in git log gefunden." -ForegroundColor Red
    exit 1
}
Write-Host "  [OK] W4-Merge im git log: $baseFound"

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

$tabletState = adb -s $SER get-state 2>&1
if ($tabletState -ne "device") {
    Write-Host "WARN: Tablet $SER nicht erreichbar (state=$tabletState). W10-Deploy kann fehlschlagen." -ForegroundColor Yellow
} else {
    Write-Host "  [OK] Tablet $SER erreichbar"
}

# Git schreibt Info-Texte ('Already on master', 'Switched to branch') in stderr.
# PowerShell mit ErrorActionPreference=Stop wirft daraufhin NativeCommandError.
# Workaround: temporaer auf Continue, danach zurueck.
$savedEAP = $ErrorActionPreference
$ErrorActionPreference = "Continue"

$currentBranch = git branch --show-current 2>$null
if ($currentBranch -ne $branch) {
    $branchExists = git branch --list $branch 2>$null
    if ($branchExists) {
        git checkout $branch 2>&1 | Out-Null
        Write-Host "  [OK] Branch $branch ausgecheckt"
    } else {
        git checkout master 2>&1 | Out-Null
        git checkout -b $branch 2>&1 | Out-Null
        Write-Host "  [OK] Branch $branch frisch aus master angelegt"
    }
} else {
    Write-Host "  [OK] Branch $branch bereits ausgecheckt"
}

git branch -D feature 2>&1 | Out-Null

$ErrorActionPreference = $savedEAP

Write-Host ""
Write-Host "Pre-Flight komplett. Starte Code-Wellen W5-W10." -ForegroundColor Green
Write-Host ""

Send-TelegramMessage "DrainQ Touch-UI Global Autorun - START`nBranch: $branch`nWellen: W5-W10 (Nav-Rail, Home, Settings, Dialoge, Inspection-Slider, Deploy)"
$autorunStartMs = [int][double]::Parse((Get-Date -UFormat %s))

# =================================================================
#  Wellen W5-W10
# =================================================================
$waves = @(
    @{ Num = 5;  Model = "sonnet"; Hint = "";       Desc = "Navigation Rail Touch-optimieren" },
    @{ Num = 6;  Model = "sonnet"; Hint = "think";  Desc = "Home + Projects + ProjectForm + ProjectDetail" },
    @{ Num = 7;  Model = "sonnet"; Hint = "think";  Desc = "Settings + Connection + OfflineMaps + Splash" },
    @{ Num = 8;  Model = "sonnet"; Hint = "";       Desc = "Dialoge (Damage / VideoPlayback / PdfPreview / MapPicker)" },
    @{ Num = 9;  Model = "sonnet"; Hint = "";       Desc = "InspectionScreen Refinement: Slider + Toggle" },
    @{ Num = 10; Model = "haiku";  Hint = "";       Desc = "Build, Deploy, Verify, Merge, Push" }
)

function Build-Prompt {
    param([int]$Num, [int]$Prev, [string]$Desc, [string]$Hint)

    $lines = New-Object System.Collections.Generic.List[string]
    [void]$lines.Add("Lies AUTORUN_TOUCHUI_GLOBAL_PLAN.md und fuehre Welle W$Num ($Desc) vollstaendig aus.")
    [void]$lines.Add("")
    if ($Num -gt 5) {
        [void]$lines.Add("Voraussetzung: Lies RESULT_TOUCHUI_GLOBAL_W$Prev.md fuer den Stand der vorigen Welle.")
        [void]$lines.Add("")
    }
    [void]$lines.Add("Pflicht:")
    [void]$lines.Add("- Branch ist bereits 'feature/touchui-global' (vom Skript ausgecheckt). KEINE neuen Branches anlegen.")
    [void]$lines.Add("- Halte dich exakt an Abschnitt 'Design-Tokens' der Plan-MD und an die bestehenden Dimensions.*-Konstanten.")
    [void]$lines.Add("- Konsultiere drainq-kritis-compliance falls registriert.")
    [void]$lines.Add("- Keine Magic-Numbers in der UI - alles ueber Dimensions.*-Konstanten.")
    [void]$lines.Add("- Nach allen Aenderungen: ./gradlew compileDebugKotlin muss BUILD SUCCESSFUL melden.")
    [void]$lines.Add("- Committe mit der im Plan vorgegebenen Commit-Message.")
    [void]$lines.Add("- Schreibe RESULT_TOUCHUI_GLOBAL_W$Num.md ins Repo-Root nach dem Result-File-Schema in der Plan-MD.")
    [void]$lines.Add("- Bei Unklarheiten: konservativ entscheiden + im Result dokumentieren, NICHT zurueckfragen.")

    if ($Num -eq 10) {
        [void]$lines.Add("")
        [void]$lines.Add("Welle W10 - Deploy + Merge:")
        [void]$lines.Add("1. ./gradlew assembleDebug")
        [void]$lines.Add("2. adb -s $SER install -r app\build\outputs\apk\debug\app-debug.apk")
        [void]$lines.Add("3. Logcat 15s monitoren auf FATAL EXCEPTION (keine = OK)")
        [void]$lines.Add("4. git checkout master ; git merge --no-ff feature/touchui-global -m 'merge: feature/touchui-global (Touch-Optimierung global)'")
        [void]$lines.Add("5. git push origin master")
        [void]$lines.Add("6. Commit-Hashes W5-W9 + Merge-Commit + Logcat-Ausschnitt ins Result-File")
    }

    if ($Hint) {
        [void]$lines.Add("")
        [void]$lines.Add($Hint)
    }

    return ($lines -join "`n")
}

foreach ($w in $waves) {
    $num = $w.Num
    $prev = $num - 1
    $resultFile = "RESULT_TOUCHUI_GLOBAL_W$num.md"

    if (Test-Path $resultFile) {
        Write-Host ("[SKIP] Welle W{0} bereits abgeschlossen ({1} vorhanden)" -f $num, $resultFile) -ForegroundColor Yellow
        Send-TelegramMessage ("[W{0} SKIP] {1} (Result-File bereits da)" -f $num, $w.Desc)
        Write-Host ""
        continue
    }

    Write-Host "================================================================" -ForegroundColor Cyan
    Write-Host "  Welle W$num : $($w.Desc)" -ForegroundColor Cyan
    Write-Host "  Modell: $($w.Model)  Hint: $($w.Hint)  Start: $(Get-Date -Format 'HH:mm:ss')" -ForegroundColor Cyan
    Write-Host "================================================================" -ForegroundColor Cyan

    $startMsg = "[W$num START] $($w.Desc)`nModell: $($w.Model)"
    if ($w.Hint) { $startMsg += " ($($w.Hint))" }
    Send-TelegramMessage $startMsg

    $waveStartMs = [int][double]::Parse((Get-Date -UFormat %s))

    $prompt = Build-Prompt -Num $num -Prev $prev -Desc $w.Desc -Hint $w.Hint

    & claude -p $prompt --model $($w.Model) --dangerously-skip-permissions
    $exitCode = $LASTEXITCODE

    $waveEndMs = [int][double]::Parse((Get-Date -UFormat %s))
    $waveDuration = $waveEndMs - $waveStartMs
    $waveMin = [math]::Floor($waveDuration / 60)
    $waveSec = $waveDuration % 60

    if ($exitCode -ne 0) {
        Write-Host "FEHLER: claude beendete Welle W$num mit Exit-Code $exitCode" -ForegroundColor Red
        Send-TelegramMessage ("[W{0} FAIL] {1} - claude exit={2} nach {3}m {4}s" -f $num, $w.Desc, $exitCode, $waveMin, $waveSec)
        exit 1
    }

    if (-not (Test-Path $resultFile)) {
        Write-Host "FEHLER: $resultFile wurde nicht erzeugt." -ForegroundColor Red
        Send-TelegramMessage ("[W{0} FAIL] {1} - kein Result-File nach {2}m {3}s" -f $num, $w.Desc, $waveMin, $waveSec)
        exit 1
    }

    $bytes = (Get-Item $resultFile).Length
    Write-Host ("  [OK] Welle W{0} fertig, {1} erzeugt ({2} Bytes)" -f $num, $resultFile, $bytes) -ForegroundColor Green
    Write-Host ""

    $resultExcerpt = (Get-Content $resultFile -Raw -ErrorAction SilentlyContinue)
    if ($resultExcerpt -and $resultExcerpt.Length -gt 1500) {
        $resultExcerpt = $resultExcerpt.Substring(0, 1500) + "`n...(gekuerzt)"
    }
    Send-TelegramMessage ("[W{0} OK] {1} - {2}m {3}s`n`n{4}" -f $num, $w.Desc, $waveMin, $waveSec, $resultExcerpt)
}

# =================================================================
#  Abschluss
# =================================================================
$autorunEndMs = [int][double]::Parse((Get-Date -UFormat %s))
$totalDuration = $autorunEndMs - $autorunStartMs
$totalMin = [math]::Floor($totalDuration / 60)
$totalSec = $totalDuration % 60

Write-Host "================================================================" -ForegroundColor Green
Write-Host "  Alle Wellen erfolgreich!" -ForegroundColor Green
Write-Host "  Ende: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') (Dauer ${totalMin}m ${totalSec}s)" -ForegroundColor Green
Write-Host "================================================================" -ForegroundColor Green
Write-Host ""
Write-Host "Result-Files:"
Get-ChildItem RESULT_TOUCHUI_GLOBAL_W*.md | ForEach-Object {
    Write-Host "  $($_.Name)  ($($_.Length) Bytes)"
}
Write-Host ""
Write-Host "Log:        autorun_touchui_global.log"
Write-Host "Branch:     $branch (gemerged in master via W10)"

Send-TelegramMessage ("DrainQ Touch-UI Global Autorun - KOMPLETT`nDauer: {0}m {1}s`nAlle 6 Wellen durch, Branch gemerged + gepusht." -f $totalMin, $totalSec)
