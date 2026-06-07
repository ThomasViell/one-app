<#
  check-camera-head.ps1 — liest die USB-/UVC-Kennung des aktuell angesteckten
  Kamerakopfes von der ONE aus, damit wir prüfen können, ob sich C10 und C18
  automatisch unterscheiden lassen.

  Nutzung (Köpfe sind hot-plug):
    1) C10 anstecken:   .\tools\check-camera-head.ps1 -Label C10
    2) auf C18 umstecken (kurz warten, bis neu erkannt)
    3) C18 ausgelesen:  .\tools\check-camera-head.ps1 -Label C18
    4) beide Ausgaben / die zwei .txt-Dateien vergleichen (oder hier reinpasten)

  Unterscheiden sich Produktname / VID:PID / bcdDevice / Auflösungen -> Auto-Detect möglich.
  Sind sie identisch -> dieselbe UVC-Kamera, nur mechanisch verschieden -> bleibt manuell.
#>
param(
  [Parameter(Mandatory=$true)][string]$Label,     # z.B. C10 oder C18
  [string]$Serial = "233b4bd2865177ed"
)

$ts      = Get-Date -Format "yyyyMMdd_HHmmss"
$outFile = Join-Path $PSScriptRoot "camera-head_${Label}_${ts}.txt"

# Geräte-Snippet (LF!) — alle USB-Geräte + video4linux-Zuordnung + v4l2-Formate
$sh = @'
echo "=== USB-Geraete (sysfs) ==="
for d in /sys/bus/usb/devices/*; do
  [ -f "$d/idVendor" ] || continue
  printf '%s  %s:%s  bcd=%s  man=[%s]  prod=[%s]  serial=[%s]\n' \
    "$(basename $d)" "$(cat $d/idVendor 2>/dev/null)" "$(cat $d/idProduct 2>/dev/null)" \
    "$(cat $d/bcdDevice 2>/dev/null)" "$(cat $d/manufacturer 2>/dev/null)" \
    "$(cat $d/product 2>/dev/null)" "$(cat $d/serial 2>/dev/null)"
done
echo
echo "=== video4linux (welches Geraet ist die Kamera) ==="
for v in /sys/class/video4linux/video*; do
  [ -e "$v" ] || continue
  link=$(readlink -f "$v/device" 2>/dev/null)
  usb=$(dirname "$link" 2>/dev/null)
  printf '%s: name=[%s]  usb=%s:%s prod=[%s]\n' \
    "$(basename $v)" "$(cat $v/name 2>/dev/null)" \
    "$(cat $usb/idVendor 2>/dev/null)" "$(cat $usb/idProduct 2>/dev/null)" "$(cat $usb/product 2>/dev/null)"
done
echo
echo "=== v4l2-ctl Formate/Aufloesungen (falls vorhanden) ==="
if command -v v4l2-ctl >/dev/null 2>&1; then
  v4l2-ctl -d /dev/video0 --all 2>&1 | head -40
  echo "--- Formate ---"
  v4l2-ctl -d /dev/video0 --list-formats-ext 2>&1
else
  echo "v4l2-ctl nicht vorhanden — USB-Kennung oben reicht zum Vergleich"
fi
'@

$tmp = Join-Path $env:TEMP "check_cam.sh"
[IO.File]::WriteAllText($tmp, ($sh -replace "`r`n","`n"))

Write-Host "Lese Kamerakopf '$Label' von Geraet $Serial ..." -ForegroundColor Cyan
adb -s $Serial push $tmp /data/local/tmp/check_cam.sh | Out-Null
$res = adb -s $Serial shell sh /data/local/tmp/check_cam.sh 2>&1

$header = "### Kamerakopf: $Label   ($ts)   Geraet $Serial"
$header | Tee-Object -FilePath $outFile
$res    | Tee-Object -FilePath $outFile -Append

Write-Host ""
Write-Host ">> Gespeichert: $outFile" -ForegroundColor Green
Write-Host ">> Jetzt Kopf wechseln und erneut mit dem anderen -Label laufen lassen, dann beide vergleichen." -ForegroundColor Green
