# check-microphone.ps1 — Mikrofon-Check auf der ONE (CEO-Beschluss 2026-06-07, Punkt 12)
# Prüft, ob die ONE-Hardware (RK3588) ein internes Mikrofon hat. Ergebnis entscheidet:
#   Mikro vorhanden  -> Audio-Spur in Videoaufnahmen über internes Mikro
#   kein Mikro       -> Bluetooth-Mikrofon-Unterstützung (SCO) einbauen
#
# Aufruf (Gerät per USB verbunden):
#   cd C:\Projekte\drainq.one; .\tools\check-microphone.ps1

$env:ANDROID_SERIAL = "233b4bd2865177ed"
$adb = "adb"

Write-Host "=== 1) Feature-Flag: deklariert das System ein Mikrofon? ===" -ForegroundColor Cyan
& $adb shell pm list features | Select-String "microphone"
Write-Host "(android.hardware.microphone = Systemimage behauptet ein Mikro - kann bei OEM-Images gelogen sein)`n"

Write-Host "=== 2) Audio-Eingabegeraete laut AudioService ===" -ForegroundColor Cyan
& $adb shell dumpsys audio | Select-String -Pattern "input device|builtin_mic|BUILTIN_MIC|wired_headset|bluetooth" | Select-Object -First 20
Write-Host ""

Write-Host "=== 3) Audio-Policy: verfuegbare Input-Profile ===" -ForegroundColor Cyan
& $adb shell dumpsys media.audio_policy | Select-String -Pattern "Input|mic|MIC" | Select-Object -First 25
Write-Host ""

Write-Host "=== 4) ALSA-Capture-Geraete (Kernel-Ebene, ehrlichste Quelle) ===" -ForegroundColor Cyan
& $adb shell cat /proc/asound/cards
& $adb shell ls -la /dev/snd/ 2>$null | Select-String "pcmC.*c"
Write-Host "(pcmC*c-Eintraege = Capture-Devices; nur 'p' = nur Playback, kein Mikro)`n"

Write-Host "=== 5) Praxistest: 5 Sekunden aufnehmen und zurueckholen ===" -ForegroundColor Cyan
Write-Host "JETZT LAUT SPRECHEN (5 s)..." -ForegroundColor Yellow
& $adb shell "screenrecord --help" *> $null   # wake
& $adb shell "am start -a android.provider.MediaStore.RECORD_SOUND" 2>$null | Out-Null
# Direkter Low-Level-Test ohne App: tinycap (auf vielen RK-Images vorhanden)
& $adb shell "command -v tinycap && tinycap /sdcard/mic_test.wav -D 0 -d 0 -c 1 -r 16000 -b 16 -T 5 || echo 'tinycap fehlt - Test ueber App noetig'"
& $adb pull /sdcard/mic_test.wav "$PSScriptRoot\mic_test.wav" 2>$null
if (Test-Path "$PSScriptRoot\mic_test.wav") {
    $size = (Get-Item "$PSScriptRoot\mic_test.wav").Length
    Write-Host "mic_test.wav geholt ($size Bytes) -> anhoeren: $PSScriptRoot\mic_test.wav" -ForegroundColor Green
    Write-Host "Stille/Rauschen trotz Sprechens = kein nutzbares internes Mikro."
} else {
    Write-Host "Kein tinycap auf dem Image - Alternativtest: Audio-Notiz in DrainQ.ONE aufnehmen und anhoeren." -ForegroundColor Yellow
}

Write-Host "`n=== Bewertung ===" -ForegroundColor Cyan
Write-Host "Mikro real vorhanden -> Audio-Spur (internes Mikro) in beide Recorder einbauen."
Write-Host "Kein/stummes Mikro   -> BT-Mikrofon-Support (SCO-Routing) einbauen (Beschluss Punkt 12)."
