# Louis W2 (Video): Beweist, dass der Remux beim Stopp das Container-Problem behebt.
#
# Die Recorder schreiben live ein fragmentiertes MP4 (+frag_keyframe+empty_moov+
# default_base_moof) — absturzsicher, aber der moov-Header trägt KEINE Gesamtdauer
# (mvhd.duration == 0). Player, die die Länge aus dem Header lesen (Androids
# MediaPlayer/ExoPlayer), zeigen dann nur eine Endzeit (#5b) und brechen nach einer
# Pausen-Lücke früh ab (#9a).
#
# Fix: beim gewollten Stopp einmal verlustfrei remuxen:  -c copy -movflags +faststart
# → normaler moov mit korrekter mvhd.duration, vorn im File (seekbar).
#
# Hinweis: ffprobe SCANNT die Fragmente und meldet für BEIDE Dateien die volle
# format=duration — der Header-Bug ist damit nicht sichtbar. Deshalb liest dieses
# Skript die mvhd.duration direkt aus dem moov-Atom (genau das, was schwache Player lesen).

$ErrorActionPreference = 'Stop'

$ffmpeg  = (Get-Command ffmpeg).Source
$ffprobe = (Get-Command ffprobe).Source
$outDir  = "C:\Projekte\drainq.one\_verify"

if (Test-Path $outDir) { Remove-Item -Recurse -Force $outDir }
New-Item -ItemType Directory -Path $outDir | Out-Null

$frag  = Join-Path $outDir "live_fragmented.mp4"
$final = Join-Path $outDir "stopped_remuxed.mp4"

# ── 1) Live-Aufnahme simulieren: exakt die Recorder-Muxer-Flags ─────────────────
Write-Host ">>> 1) Live fragmentiert aufnehmen (Recorder-Flags)" -ForegroundColor Cyan
& $ffmpeg -v error -f lavfi -i "testsrc=duration=8:size=320x240:rate=12" `
    -c:v libx264 -preset ultrafast -pix_fmt yuv420p `
    -movflags +frag_keyframe+empty_moov+default_base_moof -frag_duration 1000000 `
    -y $frag
if ($LASTEXITCODE -ne 0) { throw "fragmentierte Aufnahme fehlgeschlagen" }

# ── 2) Gewollter Stopp: EXAKT das Produktions-Remux-Kommando ────────────────────
Write-Host ">>> 2) Stopp-Remux (-c copy -movflags +faststart)" -ForegroundColor Cyan
& $ffmpeg -v error -i $frag -c copy -movflags +faststart -y $final
if ($LASTEXITCODE -ne 0) { throw "Remux fehlgeschlagen" }

# ── Hilfsfunktionen: Top-Level-Box-Reihenfolge + mvhd.duration ──────────────────
function Get-TopLevelBoxes([string]$file) {
    $bytes = [System.IO.File]::ReadAllBytes($file)
    $o = 0; $order = @()
    while ($o + 8 -le $bytes.Length) {
        $size = ([uint32]$bytes[$o] -shl 24) -bor ([uint32]$bytes[$o+1] -shl 16) -bor ([uint32]$bytes[$o+2] -shl 8) -bor [uint32]$bytes[$o+3]
        $type = [System.Text.Encoding]::ASCII.GetString($bytes, $o+4, 4)
        $order += $type
        if ($size -eq 1) {
            # 64-bit largesize
            $size = 0; for ($i=0; $i -lt 8; $i++) { $size = ($size -shl 8) -bor [uint64]$bytes[$o+8+$i] }
        } elseif ($size -eq 0) { break }
        if ($size -lt 8) { break }
        $o += $size
    }
    return $order
}

function Get-MvhdDurationSec([string]$file) {
    $bytes = [System.IO.File]::ReadAllBytes($file)
    # 'mvhd' im moov finden (genau eins in diesen Testdateien).
    $pat = [byte[]]@(0x6D,0x76,0x68,0x64) # 'mvhd'
    $idx = -1
    for ($i = 0; $i -le $bytes.Length - 4; $i++) {
        if ($bytes[$i] -eq $pat[0] -and $bytes[$i+1] -eq $pat[1] -and $bytes[$i+2] -eq $pat[2] -and $bytes[$i+3] -eq $pat[3]) { $idx = $i; break }
    }
    if ($idx -lt 0) { return [pscustomobject]@{ Found=$false; Seconds=$null } }
    $p = $idx + 4                       # nach dem Typ
    $version = $bytes[$p]; $p += 4       # version(1) + flags(3)
    if ($version -eq 1) {
        $p += 16                         # creation(8) + modification(8)
        $timescale = ([uint32]$bytes[$p] -shl 24) -bor ([uint32]$bytes[$p+1] -shl 16) -bor ([uint32]$bytes[$p+2] -shl 8) -bor [uint32]$bytes[$p+3]; $p += 4
        $dur = 0; for ($i=0; $i -lt 8; $i++) { $dur = ($dur -shl 8) -bor [uint64]$bytes[$p+$i] }
    } else {
        $p += 8                          # creation(4) + modification(4)
        $timescale = ([uint32]$bytes[$p] -shl 24) -bor ([uint32]$bytes[$p+1] -shl 16) -bor ([uint32]$bytes[$p+2] -shl 8) -bor [uint32]$bytes[$p+3]; $p += 4
        $dur = ([uint32]$bytes[$p] -shl 24) -bor ([uint32]$bytes[$p+1] -shl 16) -bor ([uint32]$bytes[$p+2] -shl 8) -bor [uint32]$bytes[$p+3]
    }
    $sec = if ($timescale -gt 0) { [double]$dur / [double]$timescale } else { 0 }
    return [pscustomobject]@{ Found=$true; Timescale=$timescale; DurationUnits=$dur; Seconds=$sec }
}

$fragBoxes  = Get-TopLevelBoxes $frag
$finalBoxes = Get-TopLevelBoxes $final
$fragMvhd   = Get-MvhdDurationSec $frag
$finalMvhd  = Get-MvhdDurationSec $final
$fragProbe  = & $ffprobe -v error -show_entries format=duration -of default=nw=1:nk=1 $frag
$finalProbe = & $ffprobe -v error -show_entries format=duration -of default=nw=1:nk=1 $final

Write-Host ""
Write-Host "LIVE (fragmentiert, empty_moov):" -ForegroundColor Magenta
Write-Host ("  Top-Level-Boxen : " + ($fragBoxes -join ' '))
Write-Host ("  mvhd.duration   : {0:N3} s   (Header-Dauer, die schwache Player lesen)" -f $fragMvhd.Seconds)
Write-Host ("  ffprobe scan    : {0} s   (voller Datei-Scan — versteckt den Header-Bug)" -f $fragProbe)
Write-Host ""
Write-Host "GESTOPPT (remuxt, +faststart):" -ForegroundColor Green
Write-Host ("  Top-Level-Boxen : " + ($finalBoxes -join ' '))
Write-Host ("  mvhd.duration   : {0:N3} s" -f $finalMvhd.Seconds)
Write-Host ("  ffprobe scan    : {0} s" -f $finalProbe)
Write-Host ""

# ── Verdikt ─────────────────────────────────────────────────────────────────────
$moovIdx = [array]::IndexOf($finalBoxes, 'moov')
$mdatIdx = [array]::IndexOf($finalBoxes, 'mdat')
$faststart = ($moovIdx -ge 0 -and $mdatIdx -ge 0 -and $moovIdx -lt $mdatIdx)

Write-Host "================================================================"
if ($fragMvhd.Seconds -lt 0.5 -and $finalMvhd.Seconds -gt 7 -and $faststart) {
    Write-Host "VERDICT: PASS" -ForegroundColor Green
    Write-Host " - fragmentiert: mvhd.duration ~0 (kein Header-Dauer) -> reproduziert #5b/#9a"
    Write-Host " - remuxt:      mvhd.duration voll + moov vor mdat (faststart) -> Fix belegt"
    exit 0
} else {
    Write-Host "VERDICT: UNERWARTET — Werte prüfen (evtl. andere ffmpeg-Version)." -ForegroundColor Yellow
    exit 1
}
