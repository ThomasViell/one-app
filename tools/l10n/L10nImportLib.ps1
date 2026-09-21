# L10nImportLib.ps1 - Bibliothek fuer den Portal-Nachzug der deutschen ONE-Begriffe
# (Welle portal-nachzug, PLAN Abschnitt 3.2). Wird vom Aufrufskript
# tools/l10n-import-to-portal.ps1 und von L10nImportLib.Tests.ps1 punktiert.
#
# Bausteine:
#   Get-KotlinLangBlock      - schneidet einen Sprachblock (fun xxTranslations) aus
#   ConvertFrom-KotlinPairs  - liest "key" to "wert"-Paare, dekodiert Escapes
#                              (letzter Wert gewinnt, Warnung mit Dateizeile)
#   Normalisiere-Zeilenenden - CRLF/CR -> LF fuer den Vergleich
#   Compare-L10nKeys         - teilt in NEU / GLEICH / ABWEICHEND / NUR-PORTAL
#   Get-PortalDe             - holt das lebende Portal (2 GET, no-cache)
#   New-L10nImportBody       - baut das Paket aus NEU + Freigaben
#   Test-L10nImportBody      - Sperren e1..e6 vor jedem Senden

#Requires -Version 7.0

function Decode-KotlinString {
    param([string]$Text)
    # Kotlin-Escapes in einem Durchlauf: uXXXX, n, t, ", \, $
    $rx = [regex]'\\(n|t|"|\\|\$|u[0-9a-fA-F]{4})'
    return $rx.Replace($Text, {
        param($m)
        switch ($m.Groups[1].Value) {
            "n"     { "`n" }
            "t"     { "`t" }
            '"'     { '"' }
            '\'     { '\' }
            '$'     { '$' }
            default { [char][Convert]::ToInt32($m.Groups[1].Value.Substring(1), 16) }
        }
    })
}

function Get-KotlinLangBlock {
    [CmdletBinding()]
    param([string]$Source, [string]$Lang)
    $start = $Source.IndexOf("fun ${Lang}Translations(")
    if ($start -lt 0) { return $null }
    $next = [regex]::Match($Source.Substring($start + 10), "fun \w+Translations\(")
    if ($next.Success) { return $Source.Substring($start, $next.Index + 10) }
    return $Source.Substring($start)
}

function ConvertFrom-KotlinPairs {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)] [string]$Block,
        [int]$ZeilenVersatz = 0
    )
    $map = @{}
    $rx = [regex]'"((?:[^"\\]|\\.)*)"\s+to\s+"((?:[^"\\]|\\.)*)"'
    foreach ($m in $rx.Matches($Block)) {
        $key = Decode-KotlinString $m.Groups[1].Value
        $val = Decode-KotlinString $m.Groups[2].Value
        if ($map.ContainsKey($key)) {
            $zeile = ($Block.Substring(0, $m.Index).Split("`n")).Count + $ZeilenVersatz
            Write-Warning ("Doppelschluessel '{0}' in Zeile {1}: der spaetere Wert gewinnt (mapOf-Semantik)." -f $key, $zeile)
        }
        $map[$key] = $val
    }
    return [pscustomobject]@{ Map = $map; Count = $map.Count }
}

function Normalisiere-Zeilenenden {
    param([string]$Text)
    return $Text.Replace("`r`n", "`n").Replace("`r", "`n")
}

function Compare-L10nKeys {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)] [hashtable]$Map,
        [Parameter(Mandatory = $true)] [hashtable]$Portal,
        [Parameter(Mandatory = $true)] [string[]]$RohPortal
    )
    $neu = New-Object System.Collections.Generic.List[string]
    $gleich = New-Object System.Collections.Generic.List[string]
    $abweichend = New-Object System.Collections.Generic.List[object]
    $nurPortal = New-Object System.Collections.Generic.List[string]
    foreach ($k in $Map.Keys) {
        if (-not $Portal.ContainsKey($k)) { $neu.Add($k); continue }
        if ((Normalisiere-Zeilenenden $Map[$k]) -ceq (Normalisiere-Zeilenenden $Portal[$k])) {
            $gleich.Add($k)
        } else {
            $abweichend.Add([pscustomobject]@{ Key = $k; Repo = $Map[$k]; Portal = $Portal[$k] })
        }
    }
    # NUR-PORTAL gegen die rohen Portal-Schluessel (case-sensitive): das Portal fuehrt
    # Schreibweisen wie cancel/CANCEL als eigene Datensaetze; eine Abfrage gegen die
    # case-insensitive Repo-Map wuerde die Gross-Variante verschlucken (ContainsKey
    # "CANCEL" trifft "cancel"), deshalb hier ein Ordinal-HashSet.
    $mapKeys = New-Object System.Collections.Generic.HashSet[string]
    foreach ($k in $Map.Keys) { [void]$mapKeys.Add($k) }
    foreach ($k in $RohPortal) {
        if (-not $mapKeys.Contains($k)) { $nurPortal.Add($k) }
    }
    $neu.Sort(); $gleich.Sort(); $nurPortal.Sort()
    return [pscustomobject]@{
        Neu        = $neu
        Gleich     = $gleich
        Abweichend = @($abweichend | Sort-Object -Property Key)
        NurPortal  = $nurPortal
    }
}

function Get-Kopfzeile {
    param($Antwort, [string]$Name)
    $v = $Antwort.Headers[$Name]
    if ($null -eq $v) { return "" }
    return ($v -join "; ")
}

function Get-JsonRohSchluessel {
    param([string]$RohJson)
    # Flache Portal-JSON {schluessel: "wert", ...}: liefert die Schluesselmenge aus dem
    # Rohtext (case-sensitive, eindeutige Schreibweisen). Notwendig, weil die Repo-Map
    # (hashtable @{}) case-insensitiv prueft: ContainsKey("CANCEL") trifft "cancel",
    # die Gross-Variante wuerde in der NUR-PORTAL-Sicht verschluckt. Gemessen auf
    # pwsh 7.6.6: -AsHashtable erhaelt die Schreibweisen; der Zusammenfall ist die
    # Repo-Map. Das Portal fuehrt cancel/CANCEL und save/SAVE als eigene Datensaetze.
    $menge = New-Object System.Collections.Generic.HashSet[string]
    $rx = [regex]'"((?:[^"\\]|\\.)*)"\s*:'
    foreach ($m in $rx.Matches($RohJson)) {
        [void]$menge.Add($m.Groups[1].Value)
    }
    return $menge
}

function Get-PortalDe {
    [CmdletBinding()]
    param([Parameter(Mandatory = $true)] [string]$PortalUrl)
    try {
        $uhr = [System.Diagnostics.Stopwatch]::StartNew()
        $zusammen = "$PortalUrl/api/translations/de.json?scope=one,shared"
        $antwort1 = Invoke-WebRequest -Uri $zusammen -Headers @{ "Cache-Control" = "no-cache" } -TimeoutSec 30
        if ($antwort1.StatusCode -ne 200) { return $null }
        $dauer1 = $uhr.ElapsedMilliseconds
        $uhr.Restart()
        $nurShared = "$PortalUrl/api/translations/de.json?scope=shared"
        $antwort2 = Invoke-WebRequest -Uri $nurShared -Headers @{ "Cache-Control" = "no-cache" } -TimeoutSec 30
        if ($antwort2.StatusCode -ne 200) { return $null }
        $uhr.Stop()
        $dauer2 = $uhr.ElapsedMilliseconds
        $rohKeys = Get-JsonRohSchluessel $antwort1.Content
        $rohShared = Get-JsonRohSchluessel $antwort2.Content
        $karte = $antwort1.Content | ConvertFrom-Json -AsHashtable
        $shared = $antwort2.Content | ConvertFrom-Json -AsHashtable
        return [pscustomobject]@{
            Map          = $karte
            Shared       = $shared
            RohKeys      = $rohKeys
            RohShared    = $rohShared
            ETag         = Get-Kopfzeile $antwort1 "ETag"
            LastModified = Get-Kopfzeile $antwort1 "Last-Modified"
            Datum        = Get-Kopfzeile $antwort1 "Date"
            DauerMs      = $dauer1 + $dauer2
            AnzahlGet    = 2
        }
    } catch {
        return $null
    }
}

function New-L10nImportBody {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)] [hashtable]$Neu,
        [object[]]$Freigegeben = @()
    )
    $keys = New-Object System.Collections.Generic.List[object]
    foreach ($k in ($Neu.Keys | Sort-Object)) {
        $keys.Add([pscustomobject]@{ newKey = $k; scope = "ONE"; sourceDe = $Neu[$k] })
    }
    foreach ($f in @($Freigegeben | Sort-Object -Property Key)) {
        $keys.Add([pscustomobject]@{ newKey = $f.Key; scope = "ONE"; sourceDe = $f.Repo })
    }
    $body = [pscustomobject]@{ keys = $keys.ToArray() }
    return [pscustomobject]@{
        Body  = $body
        Json  = ($body | ConvertTo-Json -Depth 4)
        Count = $keys.Count
    }
}

function Test-L10nImportBody {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)] [object]$Body,
        [hashtable]$Portal = @{},
        [string[]]$PortalRoh = @(),
        [string[]]$Unveraendert = @(),
        [string[]]$Abweichend = @(),
        [string[]]$Freigegeben = @()
    )
    $verstoesse = New-Object System.Collections.Generic.List[string]
    $enFeld = ('source' + 'En')
    foreach ($k in $Body.keys) {
        if ($null -ne $k.PSObject.Properties[$enFeld]) {
            $verstoesse.Add("e1: Schluessel '{0}' traegt ein {1}-Feld - im Paket verboten (Portal legt EN an)." -f $k.newKey, $enFeld)
        }
        if ($k.newKey -like "help.*") {
            $verstoesse.Add("e2: Schluessel '{0}' ist ein Hilfe-Text - bleibt aussen." -f $k.newKey)
        }
        if (($Portal.Count -gt 0 -and $Portal.ContainsKey($k.newKey)) -or ($PortalRoh -contains $k.newKey)) {
            $verstoesse.Add("e3: Schluessel '{0}' liegt bereits im SHARED-Scope des Portals - nicht anfassen." -f $k.newKey)
        }
        if ($Abweichend -contains $k.newKey -and -not ($Freigegeben -contains $k.newKey)) {
            $verstoesse.Add("e4: Schluessel '{0}' weicht im Portal ab und hat keine Freigabe - bleibt unberuehrt." -f $k.newKey)
        }
        if ($k.sourceDe -match '\\u[0-9a-fA-F]{4}') {
            $verstoesse.Add("e5: Schluessel '{0}' traegt ein woertliches \\uXXXX im Wert - Dekodierung pruefen." -f $k.newKey)
        }
        if ($Unveraendert -contains $k.newKey) {
            $verstoesse.Add("e6: Schluessel '{0}' ist im Portal unveraendert - wird nie gesendet." -f $k.newKey)
        }
    }
    return $verstoesse.ToArray()
}
