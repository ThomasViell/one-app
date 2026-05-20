# DrainQ.ONE — Einmalige Provisionierung des ONE-Tablets (Variante A)
#
# Installiert /vendor/etc/init/init.drainq.rc damit beim Boot automatisch:
#   1) Android External-Camera-HAL (camera.provider@2.4-external) gestoppt wird
#   2) /dev/ttyS5 und /dev/video0 auf chmod 666 gesetzt werden
#
# Damit kann die DrainQ.ONE App nach jedem Reboot direkt auf die Hardware
# zugreifen — kein manueller adb-Eingriff mehr noetig.
#
# Voraussetzung: Tablet ist via USB verbunden, ADB-Debugging an, Build ist
# userdebug (adb root muss funktionieren).
#
# Nutzung:
#   .\provision_tablet.ps1                       # erstes ONE-Tablet
#   .\provision_tablet.ps1 -Device R52Y303GEZH   # explizites Geraet

param(
    [string]$Device = "233b4bd2865177ed"
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$InitRc = Join-Path $ScriptDir "init.drainq.rc"

if (-not (Test-Path $InitRc)) {
    Write-Error "init.drainq.rc nicht gefunden: $InitRc"
    exit 1
}

Write-Host "[1/7] adb root ..."
adb -s $Device root | Out-Host
Start-Sleep -Seconds 2

Write-Host "[2/7] Pruefe root-Status ..."
$who = adb -s $Device shell whoami
if ($who.Trim() -ne "root") {
    Write-Error "adb shell laeuft nicht als root (whoami='$who'). userdebug-Build erforderlich."
    exit 1
}

Write-Host "[3/7] /vendor und /system remount RW ..."
adb -s $Device remount | Out-Host

Write-Host "[4/7] Push init.drainq.rc nach /vendor/etc/init/ ..."
adb -s $Device push $InitRc /vendor/etc/init/init.drainq.rc | Out-Host

Write-Host "[5/7] Permissions + SELinux-Context setzen ..."
adb -s $Device shell chmod 644 /vendor/etc/init/init.drainq.rc
adb -s $Device shell chown root:root /vendor/etc/init/init.drainq.rc
adb -s $Device shell restorecon /vendor/etc/init/init.drainq.rc

Write-Host "[6/7] Verifikation Datei vorhanden ..."
adb -s $Device shell ls -lZ /vendor/etc/init/init.drainq.rc | Out-Host

Write-Host "[7/8] Akut-Aktivierung ohne Reboot ..."
adb -s $Device shell stop vendor.camera-provider-2-4-ext
adb -s $Device shell chmod 666 /dev/ttyS5 /dev/video0

Write-Host "[8/9] DrainQ.ONE als System-Launcher registrieren (Kiosk-Modus) ..."
# Macht die App zum HOME-Launcher. Beim Boot startet sie sofort,
# Home-Taste fuehrt zur App zurueck statt zum Android-Launcher.
adb -s $Device shell cmd package set-home-activity com.uip.drainq.one/com.uip.oneapp.MainActivity

Write-Host "[9/9] Bominwell MiniPush deaktivieren (sonst startet sie via BOOT_COMPLETED-Receiver vor uns) ..."
adb -s $Device shell pm disable-user --user 0 com.bominwell.minipush 2>&1 | Out-Host
adb -s $Device shell am force-stop com.bominwell.minipush

adb -s $Device shell am force-stop com.uip.drainq.one
adb -s $Device shell monkey -p com.uip.drainq.one -c android.intent.category.LAUNCHER 1 | Out-Null

Write-Host ""
Write-Host "Tablet $Device provisioniert (Kiosk-Modus aktiv)."
Write-Host "Beim naechsten Reboot startet DrainQ.ONE automatisch als Launcher."
Write-Host "Bei USB-Replug der Inspektionskamera ggf. reset_tablet.ps1 ausfuehren."
Write-Host ""
Write-Host "Rueckgaengig (z.B. fuer Service): "
Write-Host "  adb -s $Device shell cmd package set-home-activity com.android.launcher3/.uioverrides.QuickstepLauncher"
Write-Host "  adb -s $Device shell pm enable com.bominwell.minipush"
