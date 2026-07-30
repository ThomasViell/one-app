<#
.SYNOPSIS
    Werkseinrichtung DrainQ.ONE — Ablauf fuer EIN Geraet (wird von Werkseinrichtung.ps1 pro
    angeschlossenem Geraet als eigener Hintergrund-Job gestartet, damit mehrere Geraete
    gleichzeitig laufen).

.DESCRIPTION
    Jeder Schritt wird nach der Ausfuehrung erneut abgefragt (nicht nur "kein Fehler" =
    "hat gewirkt"). Bricht bei jedem Fehlschlag SOFORT fuer dieses eine Geraet ab und
    schreibt den Grund in Klartext in die Ergebnisdatei — andere Geraete laufen weiter.

    Schreibt am Ende IMMER eine JSON-Ergebnisdatei ($ResultFile) und eine Textdatei mit dem
    vollstaendigen Ablauf (gleicher Pfad, Endung .log), auch bei einem Abbruch mitten im Lauf.
#>
param(
    [Parameter(Mandatory)] [string]$Serial,
    [Parameter(Mandatory)] [string]$AdbPath,
    [Parameter(Mandatory)] [string]$ApkPath,
    [Parameter(Mandatory)] [string]$ExpectedPackage,
    [Parameter(Mandatory)] [string]$ExpectedVersionName,
    [Parameter(Mandatory)] [string]$ExpectedVersionCode,
    [Parameter(Mandatory)] [string]$ExpectedAdminComponent,
    [Parameter(Mandatory)] [string]$ExpectedHomeActivity,
    [Parameter(Mandatory)] [string]$KioskAction,
    [Parameter(Mandatory)] [string]$KioskReceiverComponent,
    [Parameter(Mandatory)] [string]$ResultFile,
    # Bestandsgeraet-Modus (CEO-Entscheid 30.07.2026): fuer Geraete mit bereits installierter,
    # nicht mehr passender App-Version, auf denen NICHTS schuetzenswert ist. Die Rueckfrage dazu
    # ("Alle Daten dieser App gehen verloren. Fortfahren?") ist bereits VOR dem Start dieses Jobs
    # in Werkseinrichtung.ps1 einmalig fuer den gesamten Lauf bestaetigt worden - hier nur noch
    # ausfuehren, keine zweite Rueckfrage (dieser Job laeuft ohne Konsole/Read-Host-faehig).
    # Bewusst [bool], NICHT [switch]: Start-Job -ArgumentList bindet einen rohen $true/$false
    # positional nicht an einen [switch]-Parameter ("A positional parameter cannot be found
    # that accepts argument 'False'", Befund 30.07.2026) - [bool] funktioniert dort zuverlaessig.
    [bool]$Bestandsgeraet,
    # Woher die verwendete App-Version stammt (Portal-aktualisiert / lokaler Stand / Fallback-
    # Grund) - von Werkseinrichtung.ps1 nach der Selbstaktualisierung ermittelt, hier nur noch
    # protokolliert (ZIEL Punkt 6: Version + Herkunft je Geraet ins Protokoll).
    [string]$VersionSourceNote = ''
)

$Modus = if ($Bestandsgeraet) { 'Bestandsgeraet' } else { 'Standard' }

$stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
$logLines = New-Object System.Collections.Generic.List[string]

function Log {
    param([string]$Message)
    $logLines.Add(("[{0:HH:mm:ss}] {1}" -f (Get-Date), $Message))
}

function Invoke-Adb {
    # Bewusst NICHT ueber ProcessStartInfo.ArgumentList: liefert auf manchen Windows-PowerShell-
    # 5.1-Stationen $null statt einer leeren Collection (getestet 30.07.2026) und wirft dann
    # "Methode fuer einen Ausdruck mit NULL-Wert" - der native Aufrufoperator ist robuster.
    param([string[]]$Arguments)
    $allArgs = @('-s', $Serial) + $Arguments
    $output = & $AdbPath @allArgs 2>&1
    $combined = ($output | Out-String)
    Log ("adb $($Arguments -join ' ') => " + ($combined.Trim() -replace "`r?`n", ' | '))
    return [pscustomobject]@{ Combined = $combined }
}

$script:resultWritten = $false
function Write-Result {
    param([string]$Ergebnis, [string]$Grund = '', [string]$Version = '')
    $stopwatch.Stop()
    $obj = [pscustomobject]@{
        Seriennummer  = $Serial
        Ergebnis      = $Ergebnis
        Grund         = $Grund
        Version       = $Version
        Modus         = $Modus
        DauerSekunden = [math]::Round($stopwatch.Elapsed.TotalSeconds, 1)
    }
    $obj | ConvertTo-Json | Out-File -FilePath $ResultFile -Encoding utf8
    $logFile = [System.IO.Path]::ChangeExtension($ResultFile, '.log')
    $logLines | Out-File -FilePath $logFile -Encoding utf8
    $script:resultWritten = $true
}

try {
    Log "Start Werkseinrichtung fuer $Serial"
    if ($VersionSourceNote) { Log "Verwendete App-Version/Herkunft: $VersionSourceNote" }

    # --- 1. Vorpruefung: NUR auf einem fabrikneuen (oder von uns selbst angebrochenen) Geraet weitermachen ---
    $accResult = Invoke-Adb @('shell', 'dumpsys', 'account')
    if ($accResult.Combined -notmatch 'Accounts:\s*(\d+)') {
        Write-Result -Ergebnis 'ROT' -Grund 'Konnte den Konten-Status nicht auslesen (dumpsys account lieferte kein auswertbares Ergebnis).'
        return
    }
    $accountCount = [int]$Matches[1]
    Log "Benutzerkonten: $accountCount"
    if ($accountCount -ne 0) {
        Write-Result -Ergebnis 'ROT' -Grund "Geraet hat $accountCount Benutzerkonto(en) - das ist KEIN fabrikneues Geraet. Erst Projekte per USB sichern, dann Werksreset, dann Entwicklermodus freischalten (siehe Anleitung, Abschnitt Ruecklaeufer)."
        return
    }

    $ownerResult = Invoke-Adb @('shell', 'dpm', 'list-owners')
    $ownerText = $ownerResult.Combined
    $deviceOwnerIsOurs = $false
    if ($ownerText -match 'no owners') {
        Log 'Kein Geraeteeigentuemer gesetzt (erwartet fuer ein fabrikneues Geraet).'
    } elseif (($ownerText -match [regex]::Escape($ExpectedAdminComponent)) -and ($ownerText -match 'DeviceOwner')) {
        $deviceOwnerIsOurs = $true
        Log 'Geraeteeigentuemer ist bereits unsere eigene App - wird als angebrochene/vorige Einrichtung fortgesetzt, nichts wird neu ueberschrieben.'
    } else {
        Write-Result -Ergebnis 'ROT' -Grund 'Geraet hat bereits einen ANDEREN Geraeteeigentuemer gesetzt. Nicht automatisch anfassen - bitte Rueckfrage vor jedem weiteren Schritt.'
        return
    }

    $pkgResult = Invoke-Adb @('shell', 'pm', 'list', 'packages', $ExpectedPackage)
    $appInstalled = $pkgResult.Combined -match [regex]::Escape("package:$ExpectedPackage")
    Log "App bereits installiert: $appInstalled"

    if ($appInstalled) {
        $verResult = Invoke-Adb @('shell', 'dumpsys', 'package', $ExpectedPackage)
        $installedVersionName = if ($verResult.Combined -match 'versionName=(\S+)') { $Matches[1] } else { '?' }
        $installedVersionCode = if ($verResult.Combined -match 'versionCode=(\d+)') { $Matches[1] } else { '?' }
        Log "Installierte Version: $installedVersionName ($installedVersionCode)"
        $versionMatches = ($installedVersionName -eq $ExpectedVersionName) -and ($installedVersionCode -eq $ExpectedVersionCode)

        if (-not ($deviceOwnerIsOurs -and $versionMatches)) {
            if (-not $Bestandsgeraet) {
                Write-Result -Ergebnis 'ROT' -Grund "App ist bereits installiert (Version $installedVersionName/$installedVersionCode) - das ist KEIN fabrikneues Geraet. Erst Projekte per USB sichern, dann Werksreset, dann Entwicklermodus freischalten (siehe Anleitung, Abschnitt Ruecklaeufer)."
                return
            }

            # --- Bestandsgeraet-Modus (CEO-Entscheid 30.07.2026): vorhandene App entfernen, ---
            # --- KEIN Werksreset (schaltet die USB-Wartungsverbindung ab, siehe WERKSEINRICHTUNG.md) ---
            Log "Bestandsgeraet-Modus: entferne vorhandene App (Version $installedVersionName/$installedVersionCode) - bereits vor dem Start des Laufs bestaetigt."

            if ($deviceOwnerIsOurs) {
                # Android verweigert 'pm uninstall' fuer eine Device-Owner-App direkt
                # (DELETE_FAILED_DEVICE_POLICY_MANAGER) und 'dpm remove-active-admin' scheitert bei
                # einer nicht-testOnly-App mit SecurityException (siehe Rueckholweg-Skript, gleiche
                # Ursache). Deshalb derselbe getestete Weg: Policy-Dateien als root loeschen + neu starten.
                Log 'Bestandsgeraet-Modus: Geraet ist eigener Geraeteeigentuemer - nehme das ohne Werksreset zurueck (root, Policy-Dateien loeschen, Neustart).'
                Invoke-Adb @('root') | Out-Null
                Invoke-Adb @('wait-for-device') | Out-Null
                Start-Sleep -Seconds 1
                Invoke-Adb @('shell', 'rm', '-f', '/data/system/device_owner_2.xml', '/data/system/device_policies.xml') | Out-Null
                Invoke-Adb @('reboot') | Out-Null
                Invoke-Adb @('wait-for-device') | Out-Null
                $rebooted = $false
                for ($i = 0; $i -lt 40; $i++) {
                    Start-Sleep -Seconds 2
                    $bootProp = Invoke-Adb @('shell', 'getprop', 'sys.boot_completed')
                    if ($bootProp.Combined.Trim() -eq '1') { $rebooted = $true; break }
                }
                if (-not $rebooted) {
                    Write-Result -Ergebnis 'ROT' -Grund 'Bestandsgeraet-Modus: Neustart nach Geraeteeigentuemer-Ruecknahme wurde nicht innerhalb von 80s bestaetigt.'
                    return
                }
                $ownerRecheck = Invoke-Adb @('shell', 'dpm', 'list-owners')
                if ($ownerRecheck.Combined -notmatch 'no owners') {
                    Write-Result -Ergebnis 'ROT' -Grund "Bestandsgeraet-Modus: Geraeteeigentuemer liess sich nicht zuruecknehmen. Ausgabe: $($ownerRecheck.Combined.Trim())"
                    return
                }
                Log 'Bestandsgeraet-Modus: Geraeteeigentuemer entfernt, bestaetigt.'
            }

            $uninstallResult = Invoke-Adb @('uninstall', $ExpectedPackage)
            $pkgRecheck = Invoke-Adb @('shell', 'pm', 'list', 'packages', $ExpectedPackage)
            if ($pkgRecheck.Combined -match [regex]::Escape("package:$ExpectedPackage")) {
                Write-Result -Ergebnis 'ROT' -Grund "Bestandsgeraet-Modus: vorhandene App liess sich nicht entfernen. Ausgabe: $($uninstallResult.Combined.Trim())"
                return
            }
            Log 'Bestandsgeraet-Modus: vorhandene App entfernt, bestaetigt. Fahre fort wie bei einem fabrikneuen Geraet.'
            $appInstalled = $false
            $deviceOwnerIsOurs = $false
        } else {
            Log 'Installierte Version entspricht dem mitgelieferten Paket, Geraeteeigentuemer ist unsere App - sichere Fortsetzung einer frueheren Einrichtung.'
        }
    }

    # --- 2. App installieren ---
    Log 'Installiere App...'
    $installResult = Invoke-Adb @('install', '-r', $ApkPath)
    if ($installResult.Combined -notmatch 'Success') {
        Write-Result -Ergebnis 'ROT' -Grund "Installation fehlgeschlagen: $($installResult.Combined.Trim())"
        return
    }
    $verResult2 = Invoke-Adb @('shell', 'dumpsys', 'package', $ExpectedPackage)
    $gotVersionName = if ($verResult2.Combined -match 'versionName=(\S+)') { $Matches[1] } else { '' }
    $gotVersionCode = if ($verResult2.Combined -match 'versionCode=(\d+)') { $Matches[1] } else { '' }
    if ($gotVersionName -ne $ExpectedVersionName -or $gotVersionCode -ne $ExpectedVersionCode) {
        Write-Result -Ergebnis 'ROT' -Grund "Nach der Installation stimmt die Version nicht: gefunden $gotVersionName/$gotVersionCode, erwartet $ExpectedVersionName/$ExpectedVersionCode."
        return
    }
    Log "Installation bestaetigt: $gotVersionName/$gotVersionCode"

    # --- 3. Werks-App com.bominwell.minipush entfernen, falls vorhanden ---
    # Startet sich beim Booten selbst (eigener BOOT_COMPLETED-Empfaenger) und ueberschreibt
    # damit unseren Autostart, unabhaengig von der HOME-Zuordnung (Befund 30.07.2026).
    $miniResult = Invoke-Adb @('shell', 'pm', 'list', 'packages', 'com.bominwell.minipush')
    if ($miniResult.Combined -match 'package:com\.bominwell\.minipush') {
        Log 'com.bominwell.minipush gefunden (startet sich selbst beim Booten) - wird entfernt.'
        $uninstallResult = Invoke-Adb @('shell', 'pm', 'uninstall', '--user', '0', 'com.bominwell.minipush')
        Start-Sleep -Milliseconds 500
        $checkAgain = Invoke-Adb @('shell', 'pm', 'list', 'packages', 'com.bominwell.minipush')
        if ($checkAgain.Combined -match 'package:com\.bominwell\.minipush') {
            Write-Result -Ergebnis 'ROT' -Grund "com.bominwell.minipush (Werks-App) laesst sich nicht entfernen und ueberschreibt beim Neustart unseren Autostart. Ausgabe: $($uninstallResult.Combined.Trim())"
            return
        }
        Log 'com.bominwell.minipush entfernt.'
    } else {
        Log 'com.bominwell.minipush nicht vorhanden - nichts zu tun.'
    }

    # --- 4. Startbildschirm setzen ---
    Log 'Setze Startbildschirm...'
    Invoke-Adb @('shell', 'cmd', 'package', 'set-home-activity', $ExpectedHomeActivity) | Out-Null
    $homeCheck = Invoke-Adb @('shell', 'cmd', 'package', 'resolve-activity', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME')
    if ($homeCheck.Combined -notmatch [regex]::Escape("packageName=$ExpectedPackage")) {
        Write-Result -Ergebnis 'ROT' -Grund "Startbildschirm konnte nicht auf DrainQ.ONE gesetzt werden. Ausgabe: $($homeCheck.Combined.Trim())"
        return
    }
    Log 'Startbildschirm bestaetigt: DrainQ.ONE'

    # --- 5. Berechtigung fuer die Navigationsleiste im Kiosk ---
    Log 'Gewaehre Berechtigung fuer die Navigationsleiste...'
    Invoke-Adb @('shell', 'pm', 'grant', $ExpectedPackage, 'android.permission.WRITE_SECURE_SETTINGS') | Out-Null
    $permCheck = Invoke-Adb @('shell', 'dumpsys', 'package', $ExpectedPackage)
    if ($permCheck.Combined -notmatch 'android\.permission\.WRITE_SECURE_SETTINGS:\s*granted=true') {
        Write-Result -Ergebnis 'ROT' -Grund 'Berechtigung WRITE_SECURE_SETTINGS wurde nicht erteilt.'
        return
    }
    Log 'Berechtigung bestaetigt.'

    # --- 6. Geraeteeigentuemer setzen (Voraussetzung fuer echten Kiosk-Betrieb) ---
    if (-not $deviceOwnerIsOurs) {
        Log 'Setze Geraeteeigentuemer...'
        Invoke-Adb @('shell', 'dpm', 'set-device-owner', $ExpectedAdminComponent) | Out-Null
    }
    $ownerCheck = Invoke-Adb @('shell', 'dpm', 'list-owners')
    if (-not (($ownerCheck.Combined -match [regex]::Escape($ExpectedAdminComponent)) -and ($ownerCheck.Combined -match 'DeviceOwner'))) {
        Write-Result -Ergebnis 'ROT' -Grund "Geraeteeigentuemer konnte nicht gesetzt werden. Ausgabe: $($ownerCheck.Combined.Trim())"
        return
    }
    Log 'Geraeteeigentuemer bestaetigt.'

    # --- 7. Kiosk-Betrieb aktivieren ---
    # Der Kiosk-Schalter der App laesst sich nicht per UI-Koordinaten-Tap zuverlaessig automatisieren
    # (Displaygroesse/Sprache). Stattdessen ein eigener, nicht exportierter BroadcastReceiver
    # (ProvisioningReceiver) - adb erreicht ihn ueber den vollen Klassennamen, aber NUR als root
    # (exported=false blockiert normalen Shell-Zugriff, das ist beabsichtigt und getestet).
    Log 'Aktiviere Kiosk-Betrieb...'
    Invoke-Adb @('root') | Out-Null
    Invoke-Adb @('wait-for-device') | Out-Null
    Start-Sleep -Seconds 1
    Invoke-Adb @('shell', 'am', 'broadcast', '-a', $KioskAction, '-n', $KioskReceiverComponent) | Out-Null

    $kioskConfirmed = $false
    for ($i = 0; $i -lt 10; $i++) {
        Start-Sleep -Seconds 2
        $actCheck = Invoke-Adb @('shell', 'dumpsys', 'activity', 'activities')
        $lockedOk = $actCheck.Combined -match 'mLockTaskModeState=LOCKED'
        $topLine = ($actCheck.Combined -split "`r?`n") | Where-Object { $_ -match 'topResumedActivity=' } | Select-Object -First 1
        $topOk = $topLine -and ($topLine -match [regex]::Escape($ExpectedPackage))
        if ($lockedOk -and $topOk) { $kioskConfirmed = $true; break }
    }
    if (-not $kioskConfirmed) {
        Write-Result -Ergebnis 'ROT' -Grund 'Kiosk-Betrieb konnte nicht bestaetigt werden (LockTask nicht aktiv oder DrainQ.ONE nicht im Vordergrund).'
        return
    }
    Log 'Kiosk-Betrieb bestaetigt (Sperre aktiv, DrainQ.ONE im Vordergrund).'

    # --- Kamera: rein informativer Hinweis, kein Abbruchkriterium (ersetzt keine Sichtpruefung) ---
    $camCheck = Invoke-Adb @('logcat', '-d', '-s', 'CameraServiceSelfStart:*')
    if ($camCheck.Combined -match 'AUDIT camera_self_start_failed') {
        Log 'WARNUNG: Kamera-Selbststart meldet einen Fehler im Log - bitte Kamerabild von Hand pruefen.'
    } else {
        Log 'Kamera-Selbststart zeigt keinen Fehler im Log (ersetzt keine Sichtpruefung am Geraet).'
    }

    Write-Result -Ergebnis 'GRUEN' -Version "$gotVersionName/$gotVersionCode"
} catch {
    Log "UNERWARTETER FEHLER: $($_.Exception.Message)"
    if (-not $script:resultWritten) {
        Write-Result -Ergebnis 'ROT' -Grund "Unerwarteter Fehler im Ablauf: $($_.Exception.Message)"
    }
}
