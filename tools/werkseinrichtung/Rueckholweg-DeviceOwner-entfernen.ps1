<#
.SYNOPSIS
    Rückholweg: Geräteeigentümer + App-Installation zurücknehmen, OHNE Werksreset.

.DESCRIPTION
    Für den Sonderfall "ein Gerät wurde versehentlich eingerichtet" oder "die Einrichtung ist
    mittendrin abgebrochen und das Gerät soll wieder in einen sauberen Ausgangszustand".

    Android lässt einen einmal gesetzten Geräteeigentümer NICHT über den regulären Weg
    (`dpm remove-active-admin`) entfernen, wenn die App nicht `android:testOnly` ist (bei
    DrainQ.ONE bewusst so, das ist eine Auslieferungs-App). Der reguläre Befehl scheitert mit
    `SecurityException: Attempt to remove non-test admin`.

    Dieser Weg wurde am 30.07.2026 auf einem echten Gerät geprüft und funktioniert: die
    Geräteeigentümer-Policy liegt in zwei Dateien unter /data/system/, die als root gelöscht
    werden können. Nach einem Neustart ist der Geräteeigentümer weg.

    WICHTIG: Das ist NICHT dasselbe wie ein Werksreset. Die USB-Verbindung bleibt erhalten,
    kein Entwicklermodus-Freischalten nötig. Genau DESHALB ist dieser Weg für die Werkstatt
    brauchbar - ein Werksreset auf einem fabrikneuen Gerät schaltet laut Messung vom
    29.07.2026 die USB-Verbindung ab.

.PARAMETER Serial
    Seriennummer des Geräts (adb devices). Pflichtangabe - dieser Weg läuft absichtlich NICHT
    automatisch über alle angeschlossenen Geräte, das wäre bei einer Aufräum-Aktion zu riskant.

.PARAMETER AuchAppEntfernen
    Zusätzlich zur Geräteeigentümer-Rücknahme auch die App deinstallieren (löscht deren Daten
    auf dem Gerät unwiederbringlich). Ohne diesen Schalter bleibt die App installiert, nur der
    Geräteeigentümer-Status und die Startbildschirm-Zuordnung werden zurückgenommen.
#>
param(
    [Parameter(Mandatory)] [string]$Serial,
    [switch]$AuchAppEntfernen
)

try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}
$root = $PSScriptRoot
$adb = Join-Path $root 'adb\adb.exe'
if (-not (Test-Path $adb)) { $adb = 'adb' } # Fallback: System-adb, falls das Paket separat läuft

function Invoke-Adb([string[]]$Arguments) {
    & $adb -s $Serial @Arguments 2>&1
}

Write-Host ''
Write-Host "Rückholweg für Gerät $Serial" -ForegroundColor White
Write-Host '=============================================='
Write-Host ''
Write-Host 'Das hier ist KEIN Werksreset. Es nimmt nur die Werkseinrichtung (Geräteeigentümer,' -ForegroundColor Yellow
Write-Host 'ggf. die App) zurück. Die USB-Verbindung bleibt die ganze Zeit erhalten.' -ForegroundColor Yellow
if ($AuchAppEntfernen) {
    Write-Host ''
    Write-Host 'ACHTUNG: -AuchAppEntfernen ist gesetzt - die App wird deinstalliert, ihre Daten auf' -ForegroundColor Red
    Write-Host 'diesem Gerät (z.B. lokal gespeicherte Projekte) gehen dabei unwiederbringlich verloren.' -ForegroundColor Red
}
Write-Host ''
$confirm = Read-Host "Wirklich fortfahren fuer Geraet $Serial ? Tippe JA zum Bestaetigen"
if ($confirm -ne 'JA') {
    Write-Host 'Abgebrochen, nichts wurde verändert.' -ForegroundColor Yellow
    exit 0
}

Write-Host ''
Write-Host '1) adb root...' -ForegroundColor Cyan
Invoke-Adb @('root') | Out-Null
Start-Sleep -Seconds 1
& $adb wait-for-device
Start-Sleep -Seconds 1

Write-Host '2) Policy-Dateien sichern...' -ForegroundColor Cyan
$backupDir = "/data/local/tmp/werkseinrichtung_backup_$(Get-Date -Format 'yyyyMMdd_HHmmss')"
Invoke-Adb @('shell', "mkdir -p $backupDir")
Invoke-Adb @('shell', "cp /data/system/device_owner_2.xml $backupDir/device_owner_2.xml.bak 2>/dev/null")
Invoke-Adb @('shell', "cp /data/system/device_policies.xml $backupDir/device_policies.xml.bak 2>/dev/null")
Write-Host "   Sicherung liegt auf dem Gerät unter $backupDir (falls die Dateien existierten)."

Write-Host '3) Geräteeigentümer-Policy löschen...' -ForegroundColor Cyan
Invoke-Adb @('shell', 'rm -f /data/system/device_owner_2.xml /data/system/device_policies.xml')

Write-Host '4) Neustart...' -ForegroundColor Cyan
Invoke-Adb @('reboot')
& $adb -s $Serial wait-for-device
Write-Host '   warte auf vollständigen Boot...'
$booted = $false
for ($i = 0; $i -lt 40; $i++) {
    Start-Sleep -Seconds 2
    $prop = (Invoke-Adb @('shell', 'getprop', 'sys.boot_completed')) -join ''
    if ($prop.Trim() -eq '1') { $booted = $true; break }
}
if (-not $booted) {
    Write-Host 'WARNUNG: Boot-Abschluss nicht innerhalb von 80s bestätigt - Gerät von Hand prüfen.' -ForegroundColor Yellow
}

Write-Host '5) Prüfung...' -ForegroundColor Cyan
$ownerCheck = Invoke-Adb @('shell', 'dpm', 'list-owners')
Write-Host "   dpm list-owners: $ownerCheck"
if ($ownerCheck -notmatch 'no owners') {
    Write-Host 'FEHLGESCHLAGEN: Geräteeigentümer ist immer noch gesetzt. Nicht weitermachen, Rückfrage halten.' -ForegroundColor Red
    exit 1
}
Write-Host '   Geräteeigentümer entfernt, bestätigt.' -ForegroundColor Green

if ($AuchAppEntfernen) {
    Write-Host '6) App deinstallieren...' -ForegroundColor Cyan
    $uninstallResult = Invoke-Adb @('uninstall', 'com.uip.drainq.one')
    Write-Host "   $uninstallResult"
    $pkgCheck = Invoke-Adb @('shell', 'pm', 'list', 'packages', 'com.uip.drainq.one')
    if ($pkgCheck -match 'package:com\.uip\.drainq\.one') {
        Write-Host 'FEHLGESCHLAGEN: App ist immer noch installiert.' -ForegroundColor Red
        exit 1
    }
    Write-Host '   App entfernt, bestätigt.' -ForegroundColor Green
}

Write-Host ''
Write-Host "Gerät $Serial ist zurückgenommen. Kein Werksreset war nötig, USB-Verbindung blieb erhalten." -ForegroundColor Green
