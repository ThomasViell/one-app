# Local verification that the recorder's drawtext finding layer renders text
# into the encoded video. Uses forward-slash paths (like Android cacheDir).

$ErrorActionPreference = 'Stop'

$ffmpeg  = "C:\Users\t.viell\AppData\Local\Microsoft\WinGet\Packages\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe\ffmpeg-8.1-full_build\bin\ffmpeg.exe"
$ffprobe = "C:\Users\t.viell\AppData\Local\Microsoft\WinGet\Packages\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe\ffmpeg-8.1-full_build\bin\ffprobe.exe"

$dir = "C:/Projekte/drainq.one/_verify"
if (Test-Path $dir) { Remove-Item -Recurse -Force $dir }
New-Item -ItemType Directory -Path $dir | Out-Null

$find = "$dir/osd_rec_finding.txt"
Set-Content -Path $find -Value 'Risse'

# Mirror what the Kotlin builder produces for hardware-OSD mode
# (finding layer only). Colon escape applied.
$findEsc = ($find -replace ':','\:')
$vf = "drawtext=textfile='$findEsc':reload=1:x=(w-text_w)/2:y=80:fontsize=30:fontcolor=0xFFFFFFFF:box=1:boxcolor=0xCC0000E0:boxborderw=8"

$out   = "$dir/finding_burnin_test.mp4"
$frame = "$dir/finding_frame.png"

& $ffmpeg -hide_banner -loglevel error `
  -f lavfi -i "color=c=gray:s=1920x1080:d=4:r=25" `
  -vf $vf `
  -c:v libx264 -preset ultrafast -crf 30 -an `
  -f mp4 -movflags +frag_keyframe+empty_moov+default_base_moof -frag_duration 1000000 `
  -y $out

if (-not (Test-Path $out)) { throw "encode failed" }
Write-Host (">>> encoded {0:N1} KB" -f ((Get-Item $out).Length / 1KB)) -ForegroundColor Cyan

& $ffmpeg -hide_banner -loglevel error -i $out -ss 1 -frames:v 1 $frame -y
if (-not (Test-Path $frame)) { throw "frame extract failed" }

Write-Host "Frame: $frame" -ForegroundColor Green
& $ffprobe -v error -show_entries format=duration,nb_streams -of default=nw=1 $out
