# DrainQ.ONE — Notfall-Reset wenn kein Video (Variante B)
#
# Faelle in denen das hilft:
#   - Tablet wurde rebootet, aber init.drainq.rc nicht installiert
#   - USB-Inspektionskamera wurde gezogen/neu eingesteckt — Android-HAL hat
#     /dev/video0 wieder geschnappt
#   - Andere App hat die Kamera geoeffnet
#
# Schnellster Weg: dieses Script ausfuehren, Tablet ist sofort wieder bereit.
#
# Nutzung:
#   .\reset_tablet.ps1
#   .\reset_tablet.ps1 -Device <serial>

param(
    [string]$Device = "233b4bd2865177ed"
)

Write-Host "Reset DrainQ.ONE auf $Device ..."

adb -s $Device root | Out-Host
Start-Sleep -Seconds 2

adb -s $Device shell stop vendor.camera-provider-2-4-ext
adb -s $Device shell chmod 666 /dev/ttyS5 /dev/video0
adb -s $Device shell ls -l /dev/ttyS5 /dev/video0 | Out-Host
adb -s $Device shell am force-stop com.uip.drainq.one
adb -s $Device shell monkey -p com.uip.drainq.one -c android.intent.category.LAUNCHER 1 | Out-Null

Write-Host ""
Write-Host "Fertig — App ist neu gestartet, Video sollte da sein."
