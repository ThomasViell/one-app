<#
.SYNOPSIS
    Selbstaktualisierung des Werkseinrichtungs-Werkzeugs: holt bei Bedarf den aktuellen,
    freigegebenen App-Stand aus demselben Lizenzportal-Manifest, aus dem sich auch die
    Geraete selbst aktualisieren (siehe app/src/main/java/com/uip/oneapp/update/*.kt).

.DESCRIPTION
    Kein zweiter, eigener Vertriebsweg: dieselbe Manifest-URL-Form wie
    UpdateConfig.manifestUrl im App-Code ("<portalUrl>/api/software/<product>/releases.<channel>.json"),
    ohne Zugangsdaten (die Geraete brauchen ebenfalls keine — siehe HttpUpdateService.kt).

    Ablauf (siehe AUTOUPDATE_WERKZEUG_PROMPT.md):
      1. Manifest holen, Version mit der lokal vorliegenden App vergleichen.
      2. Portal neuer -> herunterladen, Pruefsumme (sha256) UND Signatur-Fingerabdruck pruefen.
         Erst wenn beides stimmt, wird die lokale Datei ersetzt (alte Datei zur Seite gelegt,
         nicht ueberschrieben).
      3. Stimmt eine der beiden Pruefungen nicht -> heruntergeladene Datei verwerfen, mit der
         bisherigen weiterarbeiten, Status 'Rejected' deutlich sichtbar melden.
      4. Portal nicht erreichbar (oder Kanal nicht veroeffentlicht) -> mit der vorhandenen Datei
         weiterarbeiten, Status 'PortalUnreachable', Version+Datum der lokalen Datei im Ergebnis.
      5. Lokal neuer als das Portal -> Status 'LocalNewer', ebenfalls deutlich gemeldet.

    Reine Funktionsbibliothek (kein Seiteneffekt beim Dot-Source). Aufrufer: Werkseinrichtung.ps1.
    Texte kommen aus Texte.ps1 (vom Aufrufer vorher dot-gesourcet): LogLines, SourceLabel und
    Detail in der Fenstersprache -Sprache, ProtokollLabel immer deutsch (Geraete-.log).
#>

$script:LocalApkNamePattern = '^DrainQ-ONE_(?<name>[\d.]+)_(?<code>\d+)_platform\.apk$'

function Get-LocalPlatformApks {
    <# Alle Dateien im App-Ordner, die dem Namensmuster entsprechen (0, 1 oder mehrere). #>
    param([Parameter(Mandatory)] [string]$AppDir)
    $files = @(Get-ChildItem -Path $AppDir -Filter 'DrainQ-ONE_*_platform.apk' -File -ErrorAction SilentlyContinue)
    $parsed = @()
    foreach ($f in $files) {
        if ($f.Name -match $script:LocalApkNamePattern) {
            $parsed += [pscustomobject]@{
                Path        = $f.FullName
                Name        = $f.Name
                VersionName = $Matches['name']
                VersionCode = [int]$Matches['code']
                LastWrite   = $f.LastWriteTime
            }
        }
    }
    return $parsed
}

function Get-PortalManifest {
    param(
        [Parameter(Mandatory)] [string]$Url,
        [int]$TimeoutSec = 10
    )
    try {
        [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
    } catch {}
    try {
        $resp = Invoke-WebRequest -Uri $Url -TimeoutSec $TimeoutSec -UseBasicParsing -ErrorAction Stop
        $manifest = $resp.Content | ConvertFrom-Json
        return [pscustomobject]@{ Ok = $true; StatusCode = [int]$resp.StatusCode; Manifest = $manifest; Error = $null }
    } catch {
        $statusCode = $null
        if ($_.Exception.Response) {
            try { $statusCode = [int]$_.Exception.Response.StatusCode } catch {}
        }
        if ($statusCode -eq 404) {
            return [pscustomobject]@{ Ok = $true; StatusCode = 404; Manifest = $null; Error = $null }
        }
        return [pscustomobject]@{ Ok = $false; StatusCode = $statusCode; Manifest = $null; Error = $_.Exception.Message }
    }
}

function Invoke-WerkzeugSelfUpdate {
    <#
    .PARAMETER AppDir
        Ordner mit der App-Datei (tools\werkseinrichtung\app).
    .PARAMETER ExpectedFingerprint
        SHA-256-Fingerabdruck des Plattformschluessels (dieselbe Konstante wie in
        Werkseinrichtung.ps1, wird als Parameter uebergeben statt doppelt gepflegt).
    .PARAMETER Sprache
        Fenstersprache de|en fuer LogLines, SourceLabel und Detail. ProtokollLabel ist immer deutsch.
    .OUTPUTS
        pscustomobject mit: Status, ApkPath, VersionName, VersionCode, SourceLabel, Detail, LogLines, IsWarning,
        ProtokollLabel
    #>
    param(
        [Parameter(Mandatory)] [string]$AppDir,
        [Parameter(Mandatory)] [string]$ExpectedFingerprint,
        [string]$PortalUrl = 'https://license.drainq.com',
        [string]$Product = 'one',
        [string]$Channel = 'beta',
        [int]$TimeoutSec = 15,
        [string]$Sprache = 'de'
    )

    $log = New-Object System.Collections.Generic.List[string]
    function AddLog {
        param([string]$Key, [object[]]$Werte = @())
        $log.Add((Get-WerkzeugText -Key $Key -Sprache $Sprache -Werte $Werte))
    }

    # @(...) ist Pflicht: liefert Get-LocalPlatformApks GENAU EIN Element, wickelt Windows
    # PowerShell 5.1 (die Laufzeit von Start-Werkseinrichtung.cmd, powershell.exe) das Array beim
    # Return sonst zu einem einzelnen Objekt OHNE .Count-Eigenschaft ab - $localCandidates.Count
    # waere dann $null, und der haeufigste Fall (genau eine App-Datei vorhanden) wuerde faelschlich
    # als "kein lokaler Stand" behandelt (gemessen 30.07.2026, siehe RESULT_WERKZEUG_AUTOUPDATE_*.md
    # - derselbe Effekt ist in Werkseinrichtung.ps1 schon einmal aufgefallen, siehe dortiger
    # Kommentar bei @($jobs | Where-Object ...)). PowerShell 7 (pwsh) zeigt den Fehler NICHT, weil
    # es scalare Objekte mit einer intrinsischen Count-Eigenschaft = 1 versieht - deshalb faellt
    # das nur unter der echten Produktionslaufzeit auf, nicht bei einem pwsh-Test.
    $localCandidates = @(Get-LocalPlatformApks -AppDir $AppDir)
    $localAmbiguous = $localCandidates.Count -gt 1
    $local = $null
    if ($localCandidates.Count -eq 1) { $local = $localCandidates[0] }

    if ($localAmbiguous) {
        AddLog -Key 'update.log_mehrere' -Werte @($localCandidates.Count)
        return [pscustomobject]@{
            Status = 'Skipped-Ambiguous'; ApkPath = $null; VersionName = $null; VersionCode = $null
            SourceLabel = (Get-WerkzeugText -Key 'update.label_mehrere' -Sprache $Sprache)
            Detail = (Get-WerkzeugText -Key 'update.detail_mehrere' -Sprache $Sprache)
            LogLines = $log; IsWarning = $true
            ProtokollLabel = (Get-WerkzeugText -Key 'update.label_mehrere' -Sprache 'de')
        }
    }

    $manifestUrl = "$($PortalUrl.TrimEnd('/'))/api/software/$Product/releases.$Channel.json"
    AddLog -Key 'update.log_frage_manifest' -Werte @($manifestUrl, $Channel)
    $portal = Get-PortalManifest -Url $manifestUrl -TimeoutSec $TimeoutSec

    if (-not $portal.Ok) {
        AddLog -Key 'update.log_portal_nicht_erreichbar' -Werte @($portal.Error)
        if (-not $local) {
            return [pscustomobject]@{
                Status = 'NoLocalNoPortal'; ApkPath = $null; VersionName = $null; VersionCode = $null
                SourceLabel = (Get-WerkzeugText -Key 'update.label_kein_lokal_kein_portal' -Sprache $Sprache)
                Detail = (Get-WerkzeugText -Key 'update.detail_kein_lokal_kein_portal' -Sprache $Sprache -Werte @($portal.Error))
                LogLines = $log; IsWarning = $true
                ProtokollLabel = (Get-WerkzeugText -Key 'update.label_kein_lokal_kein_portal' -Sprache 'de')
            }
        }
        $werte = @($local.VersionName, $local.VersionCode, $local.LastWrite.ToString('yyyy-MM-dd HH:mm'))
        return [pscustomobject]@{
            Status = 'PortalUnreachable'; ApkPath = $local.Path; VersionName = $local.VersionName; VersionCode = $local.VersionCode
            SourceLabel = (Get-WerkzeugText -Key 'update.label_portal_unerreichbar' -Sprache $Sprache -Werte $werte)
            Detail = (Get-WerkzeugText -Key 'update.detail_portal_fehler' -Sprache $Sprache -Werte @($portal.Error))
            LogLines = $log; IsWarning = $true
            ProtokollLabel = (Get-WerkzeugText -Key 'update.label_portal_unerreichbar' -Sprache 'de' -Werte $werte)
        }
    }

    if ($null -eq $portal.Manifest) {
        AddLog -Key 'update.log_kanal_404' -Werte @($Channel)
        if (-not $local) {
            return [pscustomobject]@{
                Status = 'NoLocalNoPortal'; ApkPath = $null; VersionName = $null; VersionCode = $null
                SourceLabel = (Get-WerkzeugText -Key 'update.label_kanal_404_kein_lokal' -Sprache $Sprache -Werte @($Channel))
                Detail = (Get-WerkzeugText -Key 'update.detail_404' -Sprache $Sprache -Werte @($manifestUrl))
                LogLines = $log; IsWarning = $true
                ProtokollLabel = (Get-WerkzeugText -Key 'update.label_kanal_404_kein_lokal' -Sprache 'de' -Werte @($Channel))
            }
        }
        $werte = @($Channel, $local.VersionName, $local.VersionCode, $local.LastWrite.ToString('yyyy-MM-dd HH:mm'))
        return [pscustomobject]@{
            Status = 'PortalUnreachable'; ApkPath = $local.Path; VersionName = $local.VersionName; VersionCode = $local.VersionCode
            SourceLabel = (Get-WerkzeugText -Key 'update.label_kanal_404' -Sprache $Sprache -Werte $werte)
            Detail = (Get-WerkzeugText -Key 'update.detail_404' -Sprache $Sprache -Werte @($manifestUrl))
            LogLines = $log; IsWarning = $true
            ProtokollLabel = (Get-WerkzeugText -Key 'update.label_kanal_404' -Sprache 'de' -Werte $werte)
        }
    }

    $release = $portal.Manifest.latest
    AddLog -Key 'update.log_portal_meldet' -Werte @($release.version, $release.versionCode, $release.releasedAt)

    if (-not $local) {
        AddLog -Key 'update.log_bootstrap'
        $localVersionCode = -1
    } else {
        AddLog -Key 'update.log_lokaler_stand' -Werte @($local.VersionName, $local.VersionCode)
        $localVersionCode = $local.VersionCode
    }

    if ($release.versionCode -le $localVersionCode) {
        if ($release.versionCode -lt $localVersionCode) {
            AddLog -Key 'update.log_lokal_neuer'
            $werte = @($local.VersionName, $local.VersionCode, $release.version, $release.versionCode)
            return [pscustomobject]@{
                Status = 'LocalNewer'; ApkPath = $local.Path; VersionName = $local.VersionName; VersionCode = $local.VersionCode
                SourceLabel = (Get-WerkzeugText -Key 'update.label_lokal_neuer' -Sprache $Sprache -Werte $werte)
                Detail = (Get-WerkzeugText -Key 'update.detail_lokal_neuer' -Sprache $Sprache -Werte @($release.version, $release.versionCode, $release.releasedAt, $local.VersionName, $local.VersionCode))
                LogLines = $log; IsWarning = $true
                ProtokollLabel = (Get-WerkzeugText -Key 'update.label_lokal_neuer' -Sprache 'de' -Werte $werte)
            }
        }
        AddLog -Key 'update.log_aktuell'
        $werte = @($local.VersionName, $local.VersionCode, $Channel)
        return [pscustomobject]@{
            Status = 'UpToDate'; ApkPath = $local.Path; VersionName = $local.VersionName; VersionCode = $local.VersionCode
            SourceLabel = (Get-WerkzeugText -Key 'update.label_aktuell' -Sprache $Sprache -Werte $werte)
            Detail = (Get-WerkzeugText -Key 'update.detail_aktuell' -Sprache $Sprache -Werte @($release.version, $release.versionCode))
            LogLines = $log; IsWarning = $false
            ProtokollLabel = (Get-WerkzeugText -Key 'update.label_aktuell' -Sprache 'de' -Werte $werte)
        }
    }

    # --- Portal ist neuer: herunterladen, pruefen, ersetzen ---
    AddLog -Key 'update.log_lade_herunter' -Werte @($release.versionCode, $localVersionCode, $release.url)
    $stagingDir = Join-Path $AppDir '_update_staging'
    New-Item -ItemType Directory -Path $stagingDir -Force -ErrorAction SilentlyContinue | Out-Null
    $downloadFile = Join-Path $stagingDir ("download_" + [Guid]::NewGuid().ToString('N') + '.apk')

    # Vorheriger Stand fuer die Ergebniszeile: Fenster in $Sprache, Protokoll deutsch.
    $previousLabel = if ($local) { "$($local.VersionName)/$($local.VersionCode)" } else { Get-WerkzeugText -Key 'update.kein_lokaler_stand' -Sprache $Sprache }
    $previousLabelDe = if ($local) { "$($local.VersionName)/$($local.VersionCode)" } else { Get-WerkzeugText -Key 'update.kein_lokaler_stand' -Sprache 'de' }
    $fallbackApkPath = $null; $fallbackVersionName = $null; $fallbackVersionCode = $null
    if ($local) {
        $fallbackApkPath = $local.Path; $fallbackVersionName = $local.VersionName; $fallbackVersionCode = $local.VersionCode
    }

    try {
        try {
            [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
        } catch {}
        # $ProgressPreference lokal abschalten: der Fortschrittsbalken von Invoke-WebRequest
        # bremst grosse Downloads in Windows PowerShell 5.1 um GROESSENORDNUNGEN aus (bekannter
        # Effekt, gemessen: eine 175-MB-APK brauchte damit mehrere Minuten statt Sekunden).
        $prevProgressPreference = $ProgressPreference
        $ProgressPreference = 'SilentlyContinue'
        try {
            Invoke-WebRequest -Uri $release.url -OutFile $downloadFile -TimeoutSec ([Math]::Max($TimeoutSec, 60)) -UseBasicParsing -ErrorAction Stop
        } finally {
            $ProgressPreference = $prevProgressPreference
        }
    } catch {
        AddLog -Key 'update.log_download_fehler' -Werte @($_.Exception.Message)
        Remove-Item -LiteralPath $downloadFile -Force -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $stagingDir -Force -Recurse -ErrorAction SilentlyContinue
        $fallback = if ($local) { $local } else { $null }
        return [pscustomobject]@{
            Status = if ($fallback) { 'PortalUnreachable' } else { 'NoLocalNoPortal' }
            ApkPath = if ($fallback) { $fallback.Path } else { $null }
            VersionName = if ($fallback) { $fallback.VersionName } else { $null }
            VersionCode = if ($fallback) { $fallback.VersionCode } else { $null }
            SourceLabel = (Get-WerkzeugText -Key 'update.label_download_fehler' -Sprache $Sprache -Werte @($release.version, $release.versionCode, $previousLabel))
            Detail = (Get-WerkzeugText -Key 'update.detail_download_fehler' -Sprache $Sprache -Werte @($_.Exception.Message))
            LogLines = $log; IsWarning = $true
            ProtokollLabel = (Get-WerkzeugText -Key 'update.label_download_fehler' -Sprache 'de' -Werte @($release.version, $release.versionCode, $previousLabelDe))
        }
    }

    # 1) Pruefsumme aus dem Manifest verifizieren
    $actualHash = (Get-FileHash -Path $downloadFile -Algorithm SHA256).Hash
    AddLog -Key 'update.log_heruntergeladen' -Werte @($release.sha256, $actualHash)
    if ($actualHash -ine $release.sha256) {
        AddLog -Key 'update.log_pruefsumme_falsch'
        Remove-Item -LiteralPath $downloadFile -Force -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $stagingDir -Force -Recurse -ErrorAction SilentlyContinue
        return [pscustomobject]@{
            Status = 'Rejected'; ApkPath = $fallbackApkPath; VersionName = $fallbackVersionName; VersionCode = $fallbackVersionCode
            SourceLabel = (Get-WerkzeugText -Key 'update.label_pruefsumme_abgelehnt' -Sprache $Sprache -Werte @($release.version, $release.versionCode, $previousLabel))
            Detail = (Get-WerkzeugText -Key 'update.detail_pruefsumme' -Sprache $Sprache -Werte @($release.sha256, $actualHash))
            LogLines = $log; IsWarning = $true
            ProtokollLabel = (Get-WerkzeugText -Key 'update.label_pruefsumme_abgelehnt' -Sprache 'de' -Werte @($release.version, $release.versionCode, $previousLabelDe))
        }
    }
    AddLog -Key 'update.log_pruefsumme_ok'

    # 2) Signatur-Fingerabdruck zusaetzlich pruefen
    try {
        $fingerprint = Get-ApkSignatureFingerprint -ApkPath $downloadFile
    } catch {
        # Feste deutsche Meldung des Helfers wird fuers Fenster uebersetzt (Convert-BekannteMeldung, Texte.ps1).
        $meldung = Convert-BekannteMeldung -Meldung $_.Exception.Message -Sprache $Sprache
        AddLog -Key 'update.log_signatur_unlesbar' -Werte @($meldung)
        Remove-Item -LiteralPath $downloadFile -Force -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $stagingDir -Force -Recurse -ErrorAction SilentlyContinue
        return [pscustomobject]@{
            Status = 'Rejected'; ApkPath = $fallbackApkPath; VersionName = $fallbackVersionName; VersionCode = $fallbackVersionCode
            SourceLabel = (Get-WerkzeugText -Key 'update.label_signatur_unlesbar' -Sprache $Sprache -Werte @($release.version, $release.versionCode, $previousLabel))
            Detail = (Get-WerkzeugText -Key 'update.detail_signatur_unlesbar' -Sprache $Sprache -Werte @($meldung))
            LogLines = $log; IsWarning = $true
            ProtokollLabel = (Get-WerkzeugText -Key 'update.label_signatur_unlesbar' -Sprache 'de' -Werte @($release.version, $release.versionCode, $previousLabelDe))
        }
    }
    AddLog -Key 'update.log_fingerabdruck' -Werte @($fingerprint)
    if ($fingerprint -ne $ExpectedFingerprint) {
        AddLog -Key 'update.log_signatur_falsch'
        Remove-Item -LiteralPath $downloadFile -Force -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $stagingDir -Force -Recurse -ErrorAction SilentlyContinue
        return [pscustomobject]@{
            Status = 'Rejected'; ApkPath = $fallbackApkPath; VersionName = $fallbackVersionName; VersionCode = $fallbackVersionCode
            SourceLabel = (Get-WerkzeugText -Key 'update.label_signatur_falsch' -Sprache $Sprache -Werte @($release.version, $release.versionCode, $previousLabel))
            Detail = (Get-WerkzeugText -Key 'update.detail_signatur_falsch' -Sprache $Sprache -Werte @($ExpectedFingerprint, $fingerprint))
            LogLines = $log; IsWarning = $true
            ProtokollLabel = (Get-WerkzeugText -Key 'update.label_signatur_falsch' -Sprache 'de' -Werte @($release.version, $release.versionCode, $previousLabelDe))
        }
    }
    AddLog -Key 'update.log_signatur_ok'

    # --- Beide Pruefungen bestanden: alte Datei zur Seite legen, neue einsetzen ---
    $targetName = "DrainQ-ONE_$($release.version)_$($release.versionCode)_platform.apk"
    $targetPath = Join-Path $AppDir $targetName

    if ($local) {
        $previousDir = Join-Path $AppDir '_previous'
        New-Item -ItemType Directory -Path $previousDir -Force -ErrorAction SilentlyContinue | Out-Null
        $stamp = Get-Date -Format 'yyyy-MM-dd_HHmmss'
        $asideName = "$([IO.Path]::GetFileNameWithoutExtension($local.Name))_ersetzt_$stamp.apk"
        $asidePath = Join-Path $previousDir $asideName
        Move-Item -LiteralPath $local.Path -Destination $asidePath -Force
        AddLog -Key 'update.log_alte_datei' -Werte @($asidePath)
    }
    Move-Item -LiteralPath $downloadFile -Destination $targetPath -Force
    AddLog -Key 'update.log_neue_datei' -Werte @($targetPath)
    Remove-Item -LiteralPath $stagingDir -Force -Recurse -ErrorAction SilentlyContinue

    return [pscustomobject]@{
        Status = 'Updated'; ApkPath = $targetPath; VersionName = $release.version; VersionCode = $release.versionCode
        SourceLabel = (Get-WerkzeugText -Key 'update.label_aktualisiert' -Sprache $Sprache -Werte @($release.version, $release.versionCode, $previousLabel))
        Detail = (Get-WerkzeugText -Key 'update.detail_aktualisiert' -Sprache $Sprache -Werte @($Channel, $release.releasedAt))
        LogLines = $log; IsWarning = $false
        ProtokollLabel = (Get-WerkzeugText -Key 'update.label_aktualisiert' -Sprache 'de' -Werte @($release.version, $release.versionCode, $previousLabelDe))
    }
}
