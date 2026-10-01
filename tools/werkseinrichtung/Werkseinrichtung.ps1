<#
.SYNOPSIS
    DrainQ.ONE — Werkseinrichtung. Gerät(e) per USB anschließen, diese Datei starten, fertig.

.DESCRIPTION
    Siehe docs/WERKSEINRICHTUNG.md für die vollständige Anleitung (auch als Anleitung.txt
    in diesem Ordner beigelegt).

    Kurzfassung: Prüft die mitgelieferte App-Datei (Signatur muss zum Plattformschlüssel
    passen), sucht alle angeschlossenen Geräte, richtet sie PARALLEL ein und prüft dabei
    jeden einzelnen Schritt nach. Geräte, die nicht fabrikneu sind, werden übersprungen und
    rot markiert — nichts wird ungefragt überschrieben oder gelöscht.

.PARAMETER Bestandsgeraet
    Zweiter Modus für Bestandsgeräte OHNE schützenswerte Daten (CEO-Entscheid 30.07.2026):
    entfernt eine bereits vorhandene DrainQ.ONE-App (andere Version/Signatur) und richtet
    danach normal ein — KEIN Werksreset. Muss ausdrücklich angefordert werden (dieser
    Schalter oder Start-Werkseinrichtung-Bestandsgeraet.cmd), ist NIE der Standard. Fragt vor
    jeder Änderung einmal für den ganzen Lauf eine ausdrückliche Bestätigung ab.

.PARAMETER Channel
    Überschreibt den Update-Kanal aus autoupdate.config.json (Vorgabe dort: "beta" — derselbe
    Kanal, aus dem sich auch die Geräte selbst aktualisieren). Nur für Sonderfälle/Tests.

.PARAMETER PortalUrl
    Überschreibt die Portal-Basis-URL aus autoupdate.config.json. Nur für Sonderfälle/Tests.

.PARAMETER KeineSelbstaktualisierung
    Überspringt die Selbstaktualisierung komplett und arbeitet direkt mit der mitgelieferten
    App-Datei — z. B. auf einem Rechner ohne Internet, der nie online ist.

.PARAMETER Sprache
    Fenstersprache de oder en. Ohne Angabe automatisch: Windows-Anzeigesprache Deutsch (de-*)
    -> deutsch, jede andere -> englisch. Die Protokolle in logs\ bleiben immer deutsch.
    Der Bestandsgeraet-Hinweisblock bleibt immer deutsch.
#>
param(
    [switch]$Bestandsgeraet,
    [string]$Channel,
    [string]$PortalUrl,
    [switch]$KeineSelbstaktualisierung,
    [ValidateSet('de', 'en')] [string]$Sprache
)

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}
$root = $PSScriptRoot
$adb = Join-Path $root 'adb\adb.exe'
$appDir = Join-Path $root 'app'
$logsDir = Join-Path $root 'logs'
$workerScript = Join-Path $root 'Invoke-DeviceSetup.ps1'
$fingerprintHelper = Join-Path $root 'Get-ApkSignatureFingerprint.ps1'
$updateHelper = Join-Path $root 'Update-WerkzeugApp.ps1'
$configFile = Join-Path $root 'autoupdate.config.json'
$textePfad = Join-Path $root 'Texte.ps1'
. $fingerprintHelper
. $updateHelper
. $textePfad
$Sprache = Get-WerkzeugSprache -Erzwungen $Sprache

# Fingerabdruck des Plattformschlüssels bominwellalias (ADR-0005). Ändert sich NUR, wenn der
# Hersteller-Keystore selbst gewechselt wird — nicht bei jeder neuen App-Version.
$ExpectedFingerprint = '2D:37:0C:21:F5:DF:D5:53:D2:A7:96:31:4B:70:92:5F:B3:8A:DE:EF:90:86:4C:92:0B:BB:BB:12:88:7D:35:22'
$ExpectedPackage = 'com.uip.drainq.one'
$ExpectedAdminComponent = "$ExpectedPackage/com.uip.oneapp.bootstrap.OneDeviceAdminReceiver"
$ExpectedHomeActivity = "$ExpectedPackage/com.uip.oneapp.MainActivity"
$KioskAction = "$ExpectedPackage.action.PROVISION_KIOSK_ON"
$KioskReceiverComponent = "$ExpectedPackage/com.uip.oneapp.bootstrap.ProvisioningReceiver"

function Write-Headline([string]$Text) {
    Write-Host ''
    Write-Host "=== $Text ===" -ForegroundColor Cyan
}

# Fenstertext aus dem Katalog (Texte.ps1) in der Fenstersprache $Sprache.
function Get-FensterText {
    param([string]$Key, [object[]]$Werte = @())
    Get-WerkzeugText -Key $Key -Sprache $Sprache -Werte $Werte
}

Write-Host ''
Write-Host (Get-FensterText -Key 'haupt.titel') -ForegroundColor White
Write-Host '=============================='

#region Bestandsgeraet-nur-deutsch
if ($Bestandsgeraet) {
    Write-Host ''
    Write-Host '*** BESTANDSGERAET-MODUS ***' -ForegroundColor Yellow
    Write-Host 'Dieser Modus ist NICHT der Standard. Er ist nur für Bestandsgeräte ohne' -ForegroundColor Yellow
    Write-Host 'schützenswerte Daten gedacht (z.B. ein Testgerät mit alter Signatur, das' -ForegroundColor Yellow
    Write-Host 'nirgends registriert ist). Trifft das nicht zu: jetzt abbrechen (Strg+C oder' -ForegroundColor Yellow
    Write-Host 'unten NEIN eintippen) und stattdessen Start-Werkseinrichtung.cmd verwenden.' -ForegroundColor Yellow
    Write-Host ''
    Write-Host 'Für jedes angeschlossene Gerät, auf dem bereits eine DrainQ.ONE-App liegt, wird' -ForegroundColor Red
    Write-Host 'diese App JETZT entfernt (kein Werksreset). Alle Daten dieser App gehen' -ForegroundColor Red
    Write-Host 'verloren. Fortfahren?' -ForegroundColor Red
    $bestandConfirm = Read-Host 'Tippe JA zum Bestätigen'
    if ($bestandConfirm -ne 'JA') {
        Write-Host 'Abgebrochen, kein Gerät wurde angefasst.' -ForegroundColor Yellow
        exit 0
    }
    Write-Host ''
}
#endregion

if (-not (Test-Path $adb)) {
    Write-Host (Get-FensterText -Key 'haupt.adb_fehlt' -Werte @($adb)) -ForegroundColor Red
    Read-Host (Get-FensterText -Key 'haupt.taste_beenden')
    exit 1
}
if (-not (Test-Path $workerScript)) {
    Write-Host (Get-FensterText -Key 'haupt.worker_fehlt') -ForegroundColor Red
    Read-Host (Get-FensterText -Key 'haupt.taste_beenden')
    exit 1
}

# --- Selbstaktualisierung: dasselbe Portal-Manifest, aus dem sich auch die Geräte ---
# --- selbst aktualisieren (kein zweiter, eigener Weg). Siehe Update-WerkzeugApp.ps1. ---
# Herkunft der Version: $versionSourceNote deutsch (Geräte-.log), $versionSourceFenster in $Sprache.
$versionSourceNote = Get-WerkzeugText -Key 'haupt.update_uebersprungen_protokoll' -Sprache 'de'
$versionSourceFenster = Get-FensterText -Key 'haupt.update_uebersprungen_protokoll'
if (-not $KeineSelbstaktualisierung) {
    Write-Headline (Get-FensterText -Key 'haupt.pruefe_portal')
    $cfgChannel = 'beta'
    $cfgPortalUrl = 'https://license.drainq.com'
    $cfgProduct = 'one'
    if (Test-Path $configFile) {
        try {
            $cfg = Get-Content $configFile -Raw | ConvertFrom-Json
            if ($cfg.channel) { $cfgChannel = $cfg.channel }
            if ($cfg.portalUrl) { $cfgPortalUrl = $cfg.portalUrl }
            if ($cfg.product) { $cfgProduct = $cfg.product }
        } catch {
            Write-Host (Get-FensterText -Key 'haupt.config_warnung' -Werte @($_.Exception.Message)) -ForegroundColor Yellow
        }
    }
    if ($Channel) { $cfgChannel = $Channel }
    if ($PortalUrl) { $cfgPortalUrl = $PortalUrl }

    $updateResult = Invoke-WerkzeugSelfUpdate -AppDir $appDir -ExpectedFingerprint $ExpectedFingerprint `
        -PortalUrl $cfgPortalUrl -Product $cfgProduct -Channel $cfgChannel -Sprache $Sprache

    foreach ($line in $updateResult.LogLines) { Write-Host "  $line" -ForegroundColor DarkGray }

    switch ($updateResult.Status) {
        'Updated' { Write-Host $updateResult.SourceLabel -ForegroundColor Green }
        'UpToDate' { Write-Host $updateResult.SourceLabel -ForegroundColor Green }
        default { Write-Host $updateResult.SourceLabel -ForegroundColor Yellow }
    }
    if ($updateResult.Detail) { Write-Host "  $($updateResult.Detail)" -ForegroundColor DarkGray }

    if ($updateResult.Status -eq 'NoLocalNoPortal') {
        Write-Host ''
        Write-Host (Get-FensterText -Key 'haupt.nichts_zum_einrichten') -ForegroundColor Red
        Read-Host (Get-FensterText -Key 'haupt.taste_beenden')
        exit 1
    }
    $versionSourceNote = $updateResult.ProtokollLabel
    $versionSourceFenster = $updateResult.SourceLabel
    Write-Host ''
} else {
    Write-Headline (Get-FensterText -Key 'haupt.update_uebersprungen')
}

# --- App-Datei finden und Namen auswerten ---
Write-Headline (Get-FensterText -Key 'haupt.pruefe_appdatei')
$apkFiles = @(Get-ChildItem -Path $appDir -Filter 'DrainQ-ONE_*_platform.apk' -File -ErrorAction SilentlyContinue)
if ($apkFiles.Count -ne 1) {
    Write-Host (Get-FensterText -Key 'haupt.appdatei_anzahl' -Werte @($appDir, $apkFiles.Count)) -ForegroundColor Red
    Read-Host (Get-FensterText -Key 'haupt.taste_beenden')
    exit 1
}
$apkPath = $apkFiles[0].FullName
if ($apkFiles[0].Name -notmatch '^DrainQ-ONE_(?<name>[\d.]+)_(?<code>\d+)_platform\.apk$') {
    Write-Host (Get-FensterText -Key 'haupt.appdatei_name' -Werte @($apkFiles[0].Name)) -ForegroundColor Red
    Read-Host (Get-FensterText -Key 'haupt.taste_beenden')
    exit 1
}
$expectedVersionName = $Matches['name']
$expectedVersionCode = $Matches['code']
Write-Host (Get-FensterText -Key 'haupt.datei' -Werte @($apkFiles[0].Name))
Write-Host (Get-FensterText -Key 'haupt.version' -Werte @($expectedVersionName, $expectedVersionCode))

# --- Signatur rein per .NET prüfen (kein keytool/JDK nötig) ---
try {
    $fingerprint = Get-ApkSignatureFingerprint -ApkPath $apkPath
} catch {
    Write-Host (Get-FensterText -Key 'haupt.signatur_unlesbar' -Werte @(Convert-BekannteMeldung -Meldung $_.Exception.Message -Sprache $Sprache)) -ForegroundColor Red
    Read-Host (Get-FensterText -Key 'haupt.taste_beenden')
    exit 1
}

if ($fingerprint -ne $ExpectedFingerprint) {
    Write-Host ''
    Write-Host (Get-FensterText -Key 'haupt.signatur_falsch') -ForegroundColor Red
    Write-Host (Get-FensterText -Key 'haupt.signatur_gefunden' -Werte @($fingerprint)) -ForegroundColor Red
    Write-Host (Get-FensterText -Key 'haupt.signatur_erwartet' -Werte @($ExpectedFingerprint)) -ForegroundColor Red
    Write-Host ''
    Write-Host (Get-FensterText -Key 'haupt.kein_geraet_angefasst_1') -ForegroundColor Red
    Write-Host (Get-FensterText -Key 'haupt.kein_geraet_angefasst_2') -ForegroundColor Red
    Read-Host (Get-FensterText -Key 'haupt.taste_beenden')
    exit 1
}
Write-Host (Get-FensterText -Key 'haupt.signatur_ok') -ForegroundColor Green

# --- Verwendete Version gut sichtbar oben im Fenster anzeigen (ZIEL Punkt 6) ---
try { $Host.UI.RawUI.WindowTitle = (Get-FensterText -Key 'haupt.fenstertitel' -Werte @($expectedVersionName, $expectedVersionCode)) } catch {}
Write-Host ''
Write-Host (Get-FensterText -Key 'haupt.verwendete_version' -Werte @($expectedVersionName, $expectedVersionCode)) -ForegroundColor White
Write-Host "*** $versionSourceFenster ***" -ForegroundColor White

# --- Geräte suchen ---
Write-Headline (Get-FensterText -Key 'haupt.suche_geraete')
& $adb start-server | Out-Null
Start-Sleep -Seconds 1
$rawDevices = & $adb devices
$deviceLines = $rawDevices | Select-Object -Skip 1 | Where-Object { $_.Trim() -ne '' }

$authorized = @()
$notReady = @()
foreach ($line in $deviceLines) {
    if ($line -match '^(?<serial>\S+)\s+(?<state>\S+)') {
        if ($Matches['state'] -eq 'device') { $authorized += $Matches['serial'] }
        else { $notReady += "$($Matches['serial']) ($($Matches['state']))" }
    }
}

if ($notReady.Count -gt 0) {
    Write-Host (Get-FensterText -Key 'haupt.nicht_bereit' -Werte @($notReady -join ', ')) -ForegroundColor Yellow
    Write-Host (Get-FensterText -Key 'haupt.usb_debugging') -ForegroundColor Yellow
}
if ($authorized.Count -eq 0) {
    Write-Host ''
    Write-Host (Get-FensterText -Key 'haupt.kein_geraet') -ForegroundColor Red
    Read-Host (Get-FensterText -Key 'haupt.taste_beenden')
    exit 1
}
Write-Host (Get-FensterText -Key 'haupt.gefunden' -Werte @($authorized.Count, ($authorized -join ', '))) -ForegroundColor Green

# --- Protokoll vorbereiten ---
if (-not (Test-Path $logsDir)) { New-Item -ItemType Directory -Path $logsDir | Out-Null }
$runStamp = Get-Date -Format 'yyyy-MM-dd_HHmmss'
$logFile = Join-Path $logsDir "Werkseinrichtung_$runStamp.csv"
'Zeitstempel;Seriennummer;Version;Ergebnis;Modus;Dauer_Sekunden;Grund' | Out-File -FilePath $logFile -Encoding utf8

# --- Pro Gerät einen eigenen Hintergrund-Job starten (echte Parallelverarbeitung) ---
Write-Headline "$(Get-FensterText -Key 'haupt.einrichtung_laeuft' -Werte @($authorized.Count))$(if ($Bestandsgeraet) { Get-FensterText -Key 'haupt.bestandsgeraet_zusatz' })"
$jobs = @()
foreach ($serial in $authorized) {
    $resultFile = Join-Path $logsDir "$serial`_$runStamp.json"
    $job = Start-Job -FilePath $workerScript -ArgumentList @(
        $serial, $adb, $apkPath, $ExpectedPackage, $expectedVersionName, $expectedVersionCode,
        $ExpectedAdminComponent, $ExpectedHomeActivity, $KioskAction, $KioskReceiverComponent, $resultFile,
        $Bestandsgeraet.IsPresent, $versionSourceNote, $Sprache, $textePfad
    )
    $jobs += [pscustomobject]@{ Serial = $serial; Job = $job; ResultFile = $resultFile }
}

# Ausdrücklich NICHT auf State -eq 'Running' prüfen: ein frisch gestarteter Job kann kurz
# 'NotStarted' sein, dann wäre die Bedingung sofort falsch und die Schleife würde gar nicht
# warten (Befund 30.07.2026). Stattdessen auf einen ENDZUSTAND warten.
# WICHTIG: @(...) erzwingt ein Array. Ohne das liefert Where-Object bei GENAU einem Treffer
# (z.B. genau 1 Gerät angeschlossen) in Windows PowerShell 5.1 ein einzelnes Objekt ohne
# .Count-Eigenschaft zurück -> .Count ist dann $null, "$null -gt 0" ist $false, und die
# Schleife wartet ueberhaupt nicht (Befund 30.07.2026, mit genau einem Testgeraet aufgefallen).
$terminalStates = @('Completed', 'Failed', 'Stopped')
while (@($jobs | Where-Object { $_.Job.State -notin $terminalStates }).Count -gt 0) {
    Start-Sleep -Seconds 3
    $running = @($jobs | Where-Object { $_.Job.State -notin $terminalStates }).Count
    Write-Host (Get-FensterText -Key 'haupt.in_arbeit' -Werte @($running, $jobs.Count)) -ForegroundColor DarkGray
}

# --- Ergebnisse einsammeln ---
Write-Headline (Get-FensterText -Key 'haupt.ergebnis_je_geraet')
$results = @()
foreach ($j in $jobs) {
    # Einzige Job-Ausgabe: GrundFenster (Ursache in Fenstersprache), siehe Invoke-DeviceSetup.ps1 Write-Result.
    $ausgabe = @(Receive-Job -Job $j.Job -ErrorAction SilentlyContinue)
    Remove-Job -Job $j.Job -Force -ErrorAction SilentlyContinue

    if (Test-Path $j.ResultFile) {
        $r = Get-Content $j.ResultFile -Raw | ConvertFrom-Json
    } else {
        $r = New-AbbruchErgebnis -Seriennummer $j.Serial -Modus $(if ($Bestandsgeraet) { 'Bestandsgeraet' } else { 'Standard' }) -Sprache $Sprache
    }
    $results += $r
    # Protokoll ($r.Grund) bleibt deutsch; das Fenster zeigt die Ursache in $Sprache.
    $grundFenster = Select-FensterGrund -JobAusgabe (@($ausgabe) + @($r)) -Grund $r.Grund -Sprache $Sprache

    Write-Host ''
    if ($r.Ergebnis -eq 'GRUEN') {
        Write-Host (Get-FensterText -Key 'haupt.geraet_gruen' -Werte @($r.Seriennummer)) -ForegroundColor Green
        # Dauer als "$(...)": Punkt als Dezimaltrenner wie bisher (-f wuerde nach Systemkultur formatieren).
        Write-Host (Get-FensterText -Key 'haupt.gruen_details' -Werte @($r.Version, "$($r.DauerSekunden)", $r.Modus)) -ForegroundColor Green
    } else {
        Write-Host (Get-FensterText -Key 'haupt.geraet_rot' -Werte @($r.Seriennummer)) -ForegroundColor Red
        Write-Host (Get-FensterText -Key 'haupt.grund' -Werte @($grundFenster)) -ForegroundColor Red
        Write-Host (Get-FensterText -Key 'haupt.modus' -Werte @($r.Modus)) -ForegroundColor Red
    }

    $grundEinzeilig = ($r.Grund -replace ';', ',') -replace "`r?`n", ' '
    "$(Get-Date -Format o);$($r.Seriennummer);$($r.Version);$($r.Ergebnis);$($r.Modus);$($r.DauerSekunden);$grundEinzeilig" |
        Out-File -FilePath $logFile -Append -Encoding utf8
}

Write-Headline (Get-FensterText -Key 'haupt.zusammenfassung')
$okCount = @($results | Where-Object { $_.Ergebnis -eq 'GRUEN' }).Count
Write-Host (Get-FensterText -Key 'haupt.erfolgreich' -Werte @($okCount, $results.Count)) -ForegroundColor White
Write-Host (Get-FensterText -Key 'haupt.protokoll' -Werte @($logFile))
Write-Host ''
Read-Host (Get-FensterText -Key 'haupt.taste_beenden')
