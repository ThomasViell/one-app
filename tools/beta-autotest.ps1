# beta-autotest.ps1 — Automatisierter Geraetetest DrainQ.ONE (BETA-Welle 2)
# Fuehrt alle Tests aus, die KEINEN Benutzereingriff brauchen, und sammelt Beweise
# (Screenshots, Datenbank, Video, Logs) unter tools\_autotest\ zur Auswertung.
#
# Aufruf:  cd C:\Projekte\drainq.one; .\tools\beta-autotest.ps1
# Dauer:   ca. 3-4 Minuten. Geraet muss per USB verbunden und entsperrt sein.
# Wichtig: Waehrend des Laufs NICHT am Geraet tippen.

$ErrorActionPreference = "Continue"
$env:ANDROID_SERIAL = "233b4bd2865177ed"
$pkg = "com.uip.drainq.one"
$out = Join-Path $PSScriptRoot "_autotest"
$deviceTmp = "/sdcard/Download/_autotest"

New-Item -ItemType Directory -Force -Path $out | Out-Null
$log = Join-Path $out "autotest_protokoll.txt"
"DrainQ.ONE Autotest $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" | Set-Content $log

function Log($msg) {
    $line = "[{0}] {1}" -f (Get-Date -Format "HH:mm:ss"), $msg
    Write-Host $line
    Add-Content $log $line
}

function Shot($name) {
    adb shell "screencap -p $deviceTmp/$name.png" | Out-Null
    adb pull "$deviceTmp/$name.png" (Join-Path $out "$name.png") | Out-Null
    Log "Screenshot: $name.png"
}

# Tippt auf ein UI-Element anhand seines sichtbaren Textes (uiautomator-Dump).
# Liefert $true bei Treffer.
function TapText([string[]]$candidates) {
    adb shell "uiautomator dump $deviceTmp/ui.xml" | Out-Null
    $xml = adb exec-out cat "$deviceTmp/ui.xml" 2>$null
    if (-not $xml) { return $false }
    foreach ($t in $candidates) {
        if ($xml -match ('text="' + [regex]::Escape($t) + '"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')) {
            $x = ([int]$Matches[1] + [int]$Matches[3]) / 2
            $y = ([int]$Matches[2] + [int]$Matches[4]) / 2
            adb shell input tap $x $y | Out-Null
            Log "Tap auf '$t' ($x,$y)"
            return $true
        }
        # Compose rendert Texte oft als content-desc
        if ($xml -match ('content-desc="' + [regex]::Escape($t) + '"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')) {
            $x = ([int]$Matches[1] + [int]$Matches[3]) / 2
            $y = ([int]$Matches[2] + [int]$Matches[4]) / 2
            adb shell input tap $x $y | Out-Null
            Log "Tap auf desc '$t' ($x,$y)"
            return $true
        }
    }
    return $false
}

function UiContains([string[]]$candidates) {
    adb shell "uiautomator dump $deviceTmp/ui.xml" | Out-Null
    $xml = adb exec-out cat "$deviceTmp/ui.xml" 2>$null
    foreach ($t in $candidates) { if ($xml -and $xml.Contains($t)) { return $t } }
    return $null
}

# ============================== START ==============================

Log "=== 0) Vorbereitungen ==="
$dev = adb devices | Select-String $env:ANDROID_SERIAL
if (-not $dev) { Log "FEHLER: Geraet nicht verbunden."; exit 1 }
adb shell "mkdir -p $deviceTmp" | Out-Null
$ver = adb shell dumpsys package $pkg | Select-String "versionName"
Log "App-Version: $($ver -join ' ' | Out-String)".Trim()
adb logcat -c
Log "Logcat geleert."

Log "=== 1) App-Kaltstart ==="
adb shell am force-stop $pkg
Start-Sleep 2
adb shell monkey -p $pkg -c android.intent.category.LAUNCHER 1 | Out-Null
Start-Sleep 8
Shot "01_splash_oder_home"
# Splash wegtippen (Weiter-Button oder Tap in die Mitte)
if (-not (TapText @("Weiter","Los geht's","Continue","Start"))) {
    adb shell input tap 540 750 | Out-Null
}
Start-Sleep 3
# Screen-Pinning-Hinweis bestaetigen, falls er auftaucht
TapText @("GOT IT","OK","Verstanden") | Out-Null
Start-Sleep 2
Shot "02_home"

Log "=== 2) Migrations-Check: Datenbank ziehen (v9 + Datenbestand) ==="
adb shell "run-as $pkg sh -c 'cat databases/oneapp_database'      > $deviceTmp/db.sqlite" 2>$null
adb shell "run-as $pkg sh -c 'cat databases/oneapp_database-wal'  > $deviceTmp/db.sqlite-wal" 2>$null
adb shell "run-as $pkg sh -c 'cat databases/oneapp_database-shm'  > $deviceTmp/db.sqlite-shm" 2>$null
adb pull "$deviceTmp/db.sqlite"     (Join-Path $out "db.sqlite")     | Out-Null
adb pull "$deviceTmp/db.sqlite-wal" (Join-Path $out "db.sqlite-wal") | Out-Null
adb pull "$deviceTmp/db.sqlite-shm" (Join-Path $out "db.sqlite-shm") | Out-Null
Log "Datenbank gezogen: $((Get-Item (Join-Path $out 'db.sqlite')).Length) Bytes"

Log "=== 3) Screens durchgehen (Screenshots) ==="
TapText @("Projekte","Projects") | Out-Null; Start-Sleep 2; Shot "03_projekte"
TapText @("Einstellungen","Settings") | Out-Null; Start-Sleep 2; Shot "04_einstellungen"
# In Settings nach unten scrollen fuer OSD/Helligkeit/Update-Bereiche
adb shell input swipe 540 800 540 300 400 | Out-Null; Start-Sleep 1; Shot "05_einstellungen_unten"
adb shell input swipe 540 800 540 300 400 | Out-Null; Start-Sleep 1; Shot "06_einstellungen_unten2"

Log "=== 4) Inspektion: Aufnahme + PAUSE + Foto (per Hardtasten-Codes) ==="
TapText @("Inspektion","Inspection") | Out-Null
Start-Sleep 5
Shot "07_inspektion_live"

# F3 (Code 133) = Aufnahme-Dialog oeffnen
adb shell input keyevent 133; Start-Sleep 2
Shot "08_aufnahme_dialog"
if (-not (TapText @("Mit Overlay","With overlay","MIT OVERLAY"))) {
    Log "WARNUNG: 'Mit Overlay'-Knopf nicht gefunden - Dialog evtl. nicht offen."
}
Start-Sleep 2; Shot "09_aufnahme_laeuft"
Log "Aufnahme laeuft 10 s..."
Start-Sleep 10

# F3 erneut = PAUSE (neues Feature)
adb shell input keyevent 133; Start-Sleep 2
Shot "10_aufnahme_pause"
Log "Pause 5 s..."
Start-Sleep 5

# F3 erneut = WEITER
adb shell input keyevent 133; Start-Sleep 2
Shot "11_aufnahme_weiter"
Start-Sleep 8

# F5 (Code 135) = Schnellfoto waehrend der Aufnahme
adb shell input keyevent 135; Start-Sleep 2
Shot "12_schnellfoto"

# F4 (Code 134) = Stop
adb shell input keyevent 134
Log "Aufnahme gestoppt. Warte auf Finalisierung..."
Start-Sleep 8
Shot "13_nach_stop"

Log "=== 5) Ergebnis-Dateien vom Geraet holen ==="
$recDirs = adb shell "ls -t /sdcard/Android/data/$pkg/files/recordings 2>/dev/null"
Log "Recording-Ordner: $recDirs"
$newestDir = ($recDirs -split "`n" | Select-Object -First 1).Trim()
if ($newestDir) {
    $files = adb shell "ls -lt /sdcard/Android/data/$pkg/files/recordings/$newestDir"
    Log "Inhalt: $files"
    $newestMp4 = (adb shell "ls -t /sdcard/Android/data/$pkg/files/recordings/$newestDir/*.mp4 2>/dev/null" -split "`n" | Select-Object -First 1).Trim()
    if ($newestMp4) {
        adb pull "$newestMp4" (Join-Path $out "testaufnahme.mp4") | Out-Null
        Log "Video geholt: testaufnahme.mp4 ($((Get-Item (Join-Path $out 'testaufnahme.mp4')).Length) Bytes)"
    } else { Log "WARNUNG: keine MP4 gefunden." }
}
$newestJpg = (adb shell "ls -t /sdcard/Android/data/$pkg/files/damages/*/*.jpg 2>/dev/null" -split "`n" | Select-Object -First 1).Trim()
if ($newestJpg) {
    adb pull "$newestJpg" (Join-Path $out "testfoto.jpg") | Out-Null
    Log "Foto geholt: testfoto.jpg"
}

Log "=== 6) Logbuch der App sichern ==="
adb logcat -d > (Join-Path $out "logcat_voll.txt")
Select-String -Path (Join-Path $out "logcat_voll.txt") `
    -Pattern "FATAL|AndroidRuntime|CRASH|OneInternalHW|LocalBitmapRecorder|InspectionScreen|Migration|ROOM" |
    ForEach-Object { $_.Line } | Set-Content (Join-Path $out "logcat_relevant.txt")
Log "Logs gesichert."

Log "=== 7) Galerie-Gegenprobe ==="
adb shell input keyevent 136; Start-Sleep 3   # F6 = Galerie
Shot "14_galerie_nach_aufnahme"
# Zurueck zur Inspektion fuer sauberen Endzustand
adb shell input keyevent 4 | Out-Null

adb shell "rm -rf $deviceTmp" | Out-Null
Log "=== FERTIG ==="
Log "Alle Ergebnisse liegen in: $out"
Write-Host ""
Write-Host "FERTIG. Ergebnisse in tools\_autotest\ - Claude wertet sie jetzt aus." -ForegroundColor Green
