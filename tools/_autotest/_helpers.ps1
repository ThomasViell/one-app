$SERIAL = "233b4bd2865177ed"
$OUT = "C:\Projekte\drainq.one\tools\_autotest"

function Shot($name) {
    adb -s $SERIAL shell screencap -p /sdcard/Download/_s.png | Out-Null
    adb -s $SERIAL pull /sdcard/Download/_s.png "$OUT\$name" | Out-Null
    Write-Output "shot -> $name"
}
function Dump($name) {
    adb -s $SERIAL shell uiautomator dump /sdcard/Download/_ui.xml | Out-Null
    adb -s $SERIAL pull /sdcard/Download/_ui.xml "$OUT\$name" | Out-Null
    Write-Output "dump -> $name"
}
function Tap($x, $y) { adb -s $SERIAL shell input tap $x $y | Out-Null }
function Key($code) { adb -s $SERIAL shell input keyevent $code | Out-Null }
function LogC() { adb -s $SERIAL logcat -c | Out-Null }
function LogD($name) {
    adb -s $SERIAL logcat -d > "$OUT\$name"
    Write-Output "logcat -> $name"
}
function Adb { adb -s $SERIAL @args }
