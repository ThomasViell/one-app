# SMOKING-GUN-TEST für den grauen Balken
# Hypothese: Bominwell-RK3588-Custom-ROM unterdrueckt LAYOUT_INSET_DECOR nur fuer
# Apps, deren Package-Name in `persist.sys.top_app` steht. MiniPush setzt sich
# selbst, DrainQ.ONE bisher nicht — deshalb der Balken.

$serial = "233b4bd2865177ed"

Write-Host ""
Write-Host "=== Schritt 1: aktuellen Wert lesen ===" -ForegroundColor Cyan
adb -s $serial shell getprop persist.sys.top_app

Write-Host ""
Write-Host "=== Schritt 2: auf DrainQ.ONE setzen ===" -ForegroundColor Cyan
adb -s $serial shell "setprop persist.sys.top_app com.uip.drainq.one"
adb -s $serial shell getprop persist.sys.top_app

Write-Host ""
Write-Host "=== Schritt 3: DrainQ.ONE neu starten ===" -ForegroundColor Cyan
adb -s $serial shell am force-stop com.uip.drainq.one
adb -s $serial shell monkey -p com.uip.drainq.one -c android.intent.category.LAUNCHER 1 | Out-Null
Start-Sleep -Seconds 3

Write-Host ""
Write-Host "=== Schritt 4: Window-Flags pruefen ===" -ForegroundColor Cyan
adb -s $serial shell "dumpsys window windows | grep -A 5 'com.uip.drainq.one'" | Select-String -Pattern "fl=|mFullConfiguration" | Select-Object -First 4

Write-Host ""
Write-Host "JETZT auf dem Tablet schauen: ist der graue Balken weg?" -ForegroundColor Yellow
Write-Host "Falls JA: Hypothese bestaetigt. Wir bauen den setprop-Aufruf"
Write-Host "         in die App-onCreate und in init.drainq.rc ein."
Write-Host "Falls NEIN: ROM ignoriert das setprop fuer normale Apps —"
Write-Host "         dann brauchen wir Plattform-Cert (Task #36) oder Root-setprop."
Write-Host ""
