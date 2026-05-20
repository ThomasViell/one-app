# DrainQ ONE - OSD Live-Burn-In Autorun (Phasen 2-7)
# Startet Claude Code headless fuer jede Phase sequentiell.
# Jeder claude-Call hat eigenen Context (= implizites /clear).
# Bricht ab, wenn eine Phase fehlschlaegt oder kein RESULT_PHASE_N.md erzeugt.

$ErrorActionPreference = "Stop"
$repo = "C:\Projekte\drainq.one"
Set-Location $repo

Write-Host "=== DrainQ ONE - OSD Live-Burn-In Autorun ===" -ForegroundColor Cyan
Write-Host "Repo: $repo"
Write-Host "Start: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Write-Host ""

if (-not (Test-Path "OSD_LIVE_BURNIN_PHASENPLAN.md")) {
    Write-Host "FEHLER: OSD_LIVE_BURNIN_PHASENPLAN.md nicht gefunden." -ForegroundColor Red
    exit 1
}
if (-not (Test-Path "RESULT_PHASE_1.md")) {
    Write-Host "FEHLER: RESULT_PHASE_1.md fehlt. Phase 1 muss zuerst durch sein." -ForegroundColor Red
    exit 1
}

$phases = @(
    @{ Num = 2; Model = "sonnet"; Hint = "think harder"; Desc = "FFmpeg-Live-Decoder" },
    @{ Num = 3; Model = "sonnet"; Hint = "think";        Desc = "OSD-Renderer" },
    @{ Num = 4; Model = "sonnet"; Hint = "think harder"; Desc = "Integration Pipeline + OSD" },
    @{ Num = 5; Model = "sonnet"; Hint = "think harder"; Desc = "Recording mit Burn-In (R3-Pivot-faehig)" },
    @{ Num = 6; Model = "haiku";  Hint = "";             Desc = "Cleanup / Docs / v0.2.0" },
    @{ Num = 7; Model = "sonnet"; Hint = "";             Desc = "libVLC-Ausbau / v0.3.0" }
)

function Build-Prompt {
    param([int]$Num, [int]$Prev, [string]$Desc, [string]$Hint)

    $lines = New-Object System.Collections.Generic.List[string]
    [void]$lines.Add("Lies OSD_LIVE_BURNIN_PHASENPLAN.md und fuehre Phase $Num ($Desc) vollstaendig aus.")
    [void]$lines.Add("")
    [void]$lines.Add("Voraussetzung: Lies zuerst RESULT_PHASE_$Prev.md fuer den aktuellen Stand und uebernehme die dortigen Entscheidungen. Lies auch docs/adr/0001-osd-live-burnin-architecture.md.")
    [void]$lines.Add("")
    [void]$lines.Add("Pflicht:")
    [void]$lines.Add("- Branch feature/osd-phase-$Num aus master, am Ende committen.")
    [void]$lines.Add("- Nutze Subagenten wie im Phasenplan vorgesehen.")
    [void]$lines.Add("- KRITIS-Compliance-Skill konsultieren.")
    [void]$lines.Add("- Keine hardcodierten Strings/Farben.")
    [void]$lines.Add("- Schreibe am Ende RESULT_PHASE_$Num.md ins Repo-Root mit allen vom Plan geforderten Inhalten: Dateien, Diff-Summary, Performance-Werte, Screenshots/Pfade, Branch + Commit-Hashes, bekannte Issues.")

    if ($Num -eq 2) {
        [void]$lines.Add("")
        [void]$lines.Add("KRITISCHER ERSTER SCHRITT (R3-Test aus ADR):")
        [void]$lines.Add("- Bevor irgendwelcher Player-Code geschrieben wird, baue einen Minimal-Test:")
        [void]$lines.Add("  Kann das NSP3CT TWO Geraet oder ein simulierter RTSP-Server, der das Verhalten emuliert,")
        [void]$lines.Add("  ZWEI parallele RTSP-Sessions auf derselben URL bedienen?")
        [void]$lines.Add("- Dokumentiere das Ergebnis im RESULT_PHASE_2.md unter Abschnitt R3-Test-Ergebnis.")
        [void]$lines.Add("- Eine Zeile MUSS exakt so im Bericht stehen: R3-RESULT: TWO_RTSP_OK ODER R3-RESULT: TWO_RTSP_FAIL")
    }
    if ($Num -eq 5) {
        [void]$lines.Add("")
        [void]$lines.Add("R3-PIVOT-CHECK:")
        [void]$lines.Add("- Lies RESULT_PHASE_2.md und suche die Zeile R3-RESULT.")
        [void]$lines.Add("- Bei R3-RESULT: TWO_RTSP_OK: Standardarchitektur, zwei unabhaengige FFmpegKit-Sessions (Display + Recording) wie in ADR Variante A.")
        [void]$lines.Add("- Bei R3-RESULT: TWO_RTSP_FAIL: PIVOT auf geteilten Frame-Buffer. Eine FFmpegKit-Session liefert Frames in einen Ring-Buffer, Display und Recording lesen beide daraus. Die Recording-Encoder-Session erhaelt Rohframes ueber stdin-Pipe.")
        [void]$lines.Add("- Dokumentiere die gewaehlte Variante explizit am Anfang von RESULT_PHASE_5.md.")
    }

    if ($Hint -ne "") {
        [void]$lines.Add("")
        [void]$lines.Add($Hint)
    }

    return ($lines -join "`n")
}

foreach ($p in $phases) {
    $num = [int]$p.Num
    $prev = $num - 1
    $desc = [string]$p.Desc
    $model = [string]$p.Model
    $hint = [string]$p.Hint

    Write-Host ""
    Write-Host "================================================================" -ForegroundColor Cyan
    Write-Host "Phase $num - $desc  (Modell: $model, Effort: $hint)" -ForegroundColor Cyan
    Write-Host "Start: $(Get-Date -Format 'HH:mm:ss')"
    Write-Host "================================================================" -ForegroundColor Cyan

    $prompt = Build-Prompt -Num $num -Prev $prev -Desc $desc -Hint $hint

    & claude -p $prompt --model $model --dangerously-skip-permissions

    if ($LASTEXITCODE -ne 0) {
        Write-Host "ABBRUCH: Phase $num exit-code $LASTEXITCODE" -ForegroundColor Red
        exit 1
    }

    $result = "RESULT_PHASE_$num.md"
    if (-not (Test-Path $result)) {
        Write-Host "ABBRUCH: $result wurde nicht erzeugt." -ForegroundColor Red
        exit 1
    }

    Write-Host "Phase $num abgeschlossen. Ergebnis: $result" -ForegroundColor Green
    Write-Host "Ende: $(Get-Date -Format 'HH:mm:ss')"
}

Write-Host ""
Write-Host "================================================================" -ForegroundColor Green
Write-Host "ALLE PHASEN ABGESCHLOSSEN - $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" -ForegroundColor Green
Write-Host "================================================================" -ForegroundColor Green
Write-Host ""
Write-Host "Berichte zum Rueberkopieren:"
Get-ChildItem -Path . -Filter "RESULT_PHASE_*.md" | ForEach-Object { Write-Host ("  - " + $_.FullName) }
