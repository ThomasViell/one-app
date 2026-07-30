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
    .OUTPUTS
        pscustomobject mit: Status, ApkPath, VersionName, VersionCode, SourceLabel, Detail, LogLines, IsWarning
    #>
    param(
        [Parameter(Mandatory)] [string]$AppDir,
        [Parameter(Mandatory)] [string]$ExpectedFingerprint,
        [string]$PortalUrl = 'https://license.drainq.com',
        [string]$Product = 'one',
        [string]$Channel = 'beta',
        [int]$TimeoutSec = 15
    )

    $log = New-Object System.Collections.Generic.List[string]
    function AddLog([string]$m) { $log.Add($m) }

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
        AddLog "Mehr als eine App-Datei im Ordner ($($localCandidates.Count)) - Selbstaktualisierung uebersprungen, die anschliessende Pflichtpruefung entscheidet."
        return [pscustomobject]@{
            Status = 'Skipped-Ambiguous'; ApkPath = $null; VersionName = $null; VersionCode = $null
            SourceLabel = 'Mehrere App-Dateien vorhanden - Selbstaktualisierung uebersprungen'
            Detail = 'Mehr als eine Datei nach dem Muster DrainQ-ONE_<Version>_<Code>_platform.apk gefunden.'
            LogLines = $log; IsWarning = $true
        }
    }

    $manifestUrl = "$($PortalUrl.TrimEnd('/'))/api/software/$Product/releases.$Channel.json"
    AddLog "Frage Portal-Manifest ab: $manifestUrl (Kanal '$Channel')"
    $portal = Get-PortalManifest -Url $manifestUrl -TimeoutSec $TimeoutSec

    if (-not $portal.Ok) {
        AddLog "Portal NICHT erreichbar: $($portal.Error)"
        if (-not $local) {
            return [pscustomobject]@{
                Status = 'NoLocalNoPortal'; ApkPath = $null; VersionName = $null; VersionCode = $null
                SourceLabel = 'Kein lokaler Stand und Portal nicht erreichbar'
                Detail = "Weder eine lokale App-Datei noch eine Portal-Verbindung vorhanden. Fehler: $($portal.Error)"
                LogLines = $log; IsWarning = $true
            }
        }
        return [pscustomobject]@{
            Status = 'PortalUnreachable'; ApkPath = $local.Path; VersionName = $local.VersionName; VersionCode = $local.VersionCode
            SourceLabel = "Portal nicht erreichbar - lokaler Stand $($local.VersionName)/$($local.VersionCode) vom $($local.LastWrite.ToString('yyyy-MM-dd HH:mm'))"
            Detail = "Portal-Fehler: $($portal.Error)"
            LogLines = $log; IsWarning = $true
        }
    }

    if ($null -eq $portal.Manifest) {
        AddLog "Portal antwortet, aber Kanal '$Channel' ist dort nicht veroeffentlicht (HTTP 404)."
        if (-not $local) {
            return [pscustomobject]@{
                Status = 'NoLocalNoPortal'; ApkPath = $null; VersionName = $null; VersionCode = $null
                SourceLabel = "Kanal '$Channel' im Portal nicht veroeffentlicht und keine lokale App-Datei vorhanden"
                Detail = "Manifest-URL $manifestUrl liefert HTTP 404."
                LogLines = $log; IsWarning = $true
            }
        }
        return [pscustomobject]@{
            Status = 'PortalUnreachable'; ApkPath = $local.Path; VersionName = $local.VersionName; VersionCode = $local.VersionCode
            SourceLabel = "Kanal '$Channel' im Portal nicht veroeffentlicht - lokaler Stand $($local.VersionName)/$($local.VersionCode) vom $($local.LastWrite.ToString('yyyy-MM-dd HH:mm'))"
            Detail = "Manifest-URL $manifestUrl liefert HTTP 404."
            LogLines = $log; IsWarning = $true
        }
    }

    $release = $portal.Manifest.latest
    AddLog "Portal meldet: $($release.version) (Code $($release.versionCode)), veroeffentlicht $($release.releasedAt)"

    if (-not $local) {
        AddLog 'Kein lokaler Stand vorhanden - bootstrap: hole den Portal-Stand direkt.'
        $localVersionCode = -1
    } else {
        AddLog "Lokaler Stand: $($local.VersionName)/$($local.VersionCode)"
        $localVersionCode = $local.VersionCode
    }

    if ($release.versionCode -le $localVersionCode) {
        if ($release.versionCode -lt $localVersionCode) {
            AddLog 'Lokaler Stand ist NEUER als das Portal.'
            return [pscustomobject]@{
                Status = 'LocalNewer'; ApkPath = $local.Path; VersionName = $local.VersionName; VersionCode = $local.VersionCode
                SourceLabel = "ACHTUNG: lokaler Stand $($local.VersionName)/$($local.VersionCode) ist NEUER als das Portal ($($release.version)/$($release.versionCode)) - unveroeffentlichter Stand im Ordner"
                Detail = "Portal: $($release.version)/$($release.versionCode) ($($release.releasedAt)). Lokal: $($local.VersionName)/$($local.VersionCode)."
                LogLines = $log; IsWarning = $true
            }
        }
        AddLog 'Bereits aktuell - kein Update noetig.'
        return [pscustomobject]@{
            Status = 'UpToDate'; ApkPath = $local.Path; VersionName = $local.VersionName; VersionCode = $local.VersionCode
            SourceLabel = "Aktuell: $($local.VersionName)/$($local.VersionCode) (Kanal '$Channel', mit Portal abgeglichen)"
            Detail = "Portal und lokaler Stand stimmen ueberein: $($release.version)/$($release.versionCode)."
            LogLines = $log; IsWarning = $false
        }
    }

    # --- Portal ist neuer: herunterladen, pruefen, ersetzen ---
    AddLog "Portal ist neuer ($($release.versionCode) > $localVersionCode) - lade herunter: $($release.url)"
    $stagingDir = Join-Path $AppDir '_update_staging'
    New-Item -ItemType Directory -Path $stagingDir -Force -ErrorAction SilentlyContinue | Out-Null
    $downloadFile = Join-Path $stagingDir ("download_" + [Guid]::NewGuid().ToString('N') + '.apk')

    $previousLabel = if ($local) { "$($local.VersionName)/$($local.VersionCode)" } else { '(kein lokaler Stand)' }
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
        AddLog "Download fehlgeschlagen: $($_.Exception.Message)"
        Remove-Item -LiteralPath $downloadFile -Force -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $stagingDir -Force -Recurse -ErrorAction SilentlyContinue
        $fallback = if ($local) { $local } else { $null }
        return [pscustomobject]@{
            Status = if ($fallback) { 'PortalUnreachable' } else { 'NoLocalNoPortal' }
            ApkPath = if ($fallback) { $fallback.Path } else { $null }
            VersionName = if ($fallback) { $fallback.VersionName } else { $null }
            VersionCode = if ($fallback) { $fallback.VersionCode } else { $null }
            SourceLabel = "Download von Portal-Version $($release.version)/$($release.versionCode) fehlgeschlagen - bleibe bei $previousLabel"
            Detail = "Fehler beim Download: $($_.Exception.Message)"
            LogLines = $log; IsWarning = $true
        }
    }

    # 1) Pruefsumme aus dem Manifest verifizieren
    $actualHash = (Get-FileHash -Path $downloadFile -Algorithm SHA256).Hash
    AddLog "Heruntergeladen. sha256 erwartet=$($release.sha256) tatsaechlich=$actualHash"
    if ($actualHash -ine $release.sha256) {
        AddLog 'PRUEFSUMME STIMMT NICHT - heruntergeladene Datei wird verworfen, bisheriger Stand bleibt aktiv.'
        Remove-Item -LiteralPath $downloadFile -Force -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $stagingDir -Force -Recurse -ErrorAction SilentlyContinue
        return [pscustomobject]@{
            Status = 'Rejected'; ApkPath = $fallbackApkPath; VersionName = $fallbackVersionName; VersionCode = $fallbackVersionCode
            SourceLabel = "ABGELEHNT: Pruefsumme der Portal-Datei $($release.version)/$($release.versionCode) stimmt nicht - bleibe bei $previousLabel"
            Detail = "Erwartet sha256=$($release.sha256), tatsaechlich=$actualHash. Datei verworfen, NICHT verwendet."
            LogLines = $log; IsWarning = $true
        }
    }
    AddLog 'Pruefsumme OK.'

    # 2) Signatur-Fingerabdruck zusaetzlich pruefen
    try {
        $fingerprint = Get-ApkSignatureFingerprint -ApkPath $downloadFile
    } catch {
        AddLog "Signatur konnte nicht gelesen werden: $($_.Exception.Message)"
        Remove-Item -LiteralPath $downloadFile -Force -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $stagingDir -Force -Recurse -ErrorAction SilentlyContinue
        return [pscustomobject]@{
            Status = 'Rejected'; ApkPath = $fallbackApkPath; VersionName = $fallbackVersionName; VersionCode = $fallbackVersionCode
            SourceLabel = "ABGELEHNT: Signatur der Portal-Datei $($release.version)/$($release.versionCode) nicht lesbar - bleibe bei $previousLabel"
            Detail = "Fehler: $($_.Exception.Message). Datei verworfen, NICHT verwendet."
            LogLines = $log; IsWarning = $true
        }
    }
    AddLog "Signatur-Fingerabdruck: $fingerprint"
    if ($fingerprint -ne $ExpectedFingerprint) {
        AddLog 'SIGNATUR-FINGERABDRUCK STIMMT NICHT - heruntergeladene Datei wird verworfen, bisheriger Stand bleibt aktiv.'
        Remove-Item -LiteralPath $downloadFile -Force -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $stagingDir -Force -Recurse -ErrorAction SilentlyContinue
        return [pscustomobject]@{
            Status = 'Rejected'; ApkPath = $fallbackApkPath; VersionName = $fallbackVersionName; VersionCode = $fallbackVersionCode
            SourceLabel = "ABGELEHNT: Signatur der Portal-Datei $($release.version)/$($release.versionCode) stimmt nicht mit dem Plattformschluessel ueberein - bleibe bei $previousLabel"
            Detail = "Erwartet=$ExpectedFingerprint, tatsaechlich=$fingerprint. Datei verworfen, NICHT verwendet."
            LogLines = $log; IsWarning = $true
        }
    }
    AddLog 'Signatur-Fingerabdruck OK (Plattformschluessel bestaetigt).'

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
        AddLog "Alte Datei zur Seite gelegt: $asidePath"
    }
    Move-Item -LiteralPath $downloadFile -Destination $targetPath -Force
    AddLog "Neue Datei eingesetzt: $targetPath"
    Remove-Item -LiteralPath $stagingDir -Force -Recurse -ErrorAction SilentlyContinue

    return [pscustomobject]@{
        Status = 'Updated'; ApkPath = $targetPath; VersionName = $release.version; VersionCode = $release.versionCode
        SourceLabel = "AKTUALISIERT: Portal-Stand $($release.version)/$($release.versionCode) uebernommen (vorher $previousLabel)"
        Detail = "Kanal '$Channel', veroeffentlicht $($release.releasedAt). Pruefsumme und Signatur-Fingerabdruck bestaetigt."
        LogLines = $log; IsWarning = $false
    }
}
