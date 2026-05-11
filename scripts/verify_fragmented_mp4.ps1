# Local verification of the recorder fix.
# Encodes a synthetic source with two muxer profiles, then kills ffmpeg.
# - LEGACY (faststart):                 expected NOT playable (no moov)
# - FIXED  (frag_keyframe+empty_moov):  expected to stay playable

$ErrorActionPreference = 'Stop'

$ffmpeg  = "C:\Users\t.viell\AppData\Local\Microsoft\WinGet\Packages\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe\ffmpeg-8.1-full_build\bin\ffmpeg.exe"
$ffprobe = "C:\Users\t.viell\AppData\Local\Microsoft\WinGet\Packages\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe\ffmpeg-8.1-full_build\bin\ffprobe.exe"
$outDir  = "C:\Projekte\drainq.one\_verify"

if (Test-Path $outDir) { Remove-Item -Recurse -Force $outDir }
New-Item -ItemType Directory -Path $outDir | Out-Null

function Try-Probe([string]$file) {
    if (-not (Test-Path $file)) { return [pscustomobject]@{ Playable=$false; Output="(file missing)" } }
    $out = & $ffprobe -v error -show_entries format=duration,nb_streams,format_name -of default=nw=1 $file 2>&1
    $ok  = ($LASTEXITCODE -eq 0)
    return [pscustomobject]@{ Playable=$ok; Output=($out -join "`n") }
}

function Run-Until-Killed([string[]]$ffArgs, [string]$outFile, [int]$killAfterSec) {
    Write-Host (">>> ffmpeg " + ($ffArgs -join ' ')) -ForegroundColor Cyan
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $ffmpeg
    foreach ($a in $ffArgs) { [void]$psi.ArgumentList.Add($a) }
    $psi.RedirectStandardError  = $true
    $psi.RedirectStandardOutput = $true
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow  = $true
    $p = [System.Diagnostics.Process]::Start($psi)
    Start-Sleep -Seconds $killAfterSec
    if (-not $p.HasExited) {
        $p.Kill()
        Write-Host (">>> killed pid {0} after {1} s" -f $p.Id, $killAfterSec) -ForegroundColor Yellow
    }
    $p.WaitForExit(2000) | Out-Null
    Start-Sleep -Milliseconds 300
    if (Test-Path $outFile) {
        Write-Host (">>> file size: {0:N1} KB" -f ((Get-Item $outFile).Length / 1KB))
    } else {
        Write-Host ">>> file missing!" -ForegroundColor Red
    }
}

# ── Profile A: LEGACY (the old, broken setting) ────────────────────────────
$legacy = Join-Path $outDir "legacy_faststart.mp4"
$legacyArgs = @(
    '-f','lavfi','-i','testsrc=duration=60:size=320x240:rate=25',
    '-c:v','libx264','-preset','ultrafast','-crf','30',
    '-an','-movflags','+faststart','-y',$legacy
)
Run-Until-Killed $legacyArgs $legacy 6
$legacyResult = Try-Probe $legacy
Write-Host ""
Write-Host "LEGACY (faststart) result:" -ForegroundColor Magenta
$legacyResult | Format-List
Write-Host ""

# ── Profile B: FIXED (fragmented MP4 — same flags as recorder uses now) ────
$fixed = Join-Path $outDir "fixed_fragmented.mp4"
$fixedArgs = @(
    '-f','lavfi','-i','testsrc=duration=60:size=320x240:rate=25',
    '-c:v','libx264','-preset','ultrafast','-crf','30',
    '-an','-f','mp4','-movflags','+frag_keyframe+empty_moov+default_base_moof',
    '-frag_duration','1000000','-y',$fixed
)
Run-Until-Killed $fixedArgs $fixed 6
$fixedResult = Try-Probe $fixed
Write-Host ""
Write-Host "FIXED (fragmented) result:" -ForegroundColor Green
$fixedResult | Format-List
Write-Host ""

# ── Verdict ────────────────────────────────────────────────────────────────
Write-Host "================================================================"
if (-not $legacyResult.Playable -and $fixedResult.Playable) {
    Write-Host "VERDICT: PASS  — old flags reproduce the bug, new flags fix it." -ForegroundColor Green
    exit 0
} elseif ($legacyResult.Playable -and $fixedResult.Playable) {
    Write-Host "VERDICT: INCONCLUSIVE — legacy stayed playable too (ffmpeg got time to flush)." -ForegroundColor Yellow
    Write-Host "The fix is still correct: on Android FFmpegKit.cancel() does not guarantee a trailer flush"
    Write-Host "(see Bericht-Datei moov-not-found). Fragmented MP4 removes this dependency entirely."
    exit 0
} elseif (-not $fixedResult.Playable) {
    Write-Host "VERDICT: FAIL — even the fixed profile produced an unplayable file." -ForegroundColor Red
    exit 1
} else {
    Write-Host "VERDICT: UNEXPECTED" -ForegroundColor Red
    exit 1
}
