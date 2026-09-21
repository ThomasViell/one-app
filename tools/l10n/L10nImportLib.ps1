# L10nImportLib.ps1 - Bibliothek fuer den Portal-Nachzug der deutschen ONE-Begriffe
# (Welle portal-nachzug, PLAN Abschnitt 3.2, Runde 2: NACHBESSERUNG N-1..N-6).
# Wird vom Aufrufskript tools/l10n-import-to-portal.ps1 und von
# L10nImportLib.Tests.ps1 punktiert.
#
# Bausteine:
#   Get-KotlinLangBlock        - schneidet einen Sprachblock (fun xxTranslations) aus
#   ConvertFrom-KotlinPairs    - liest "key" to "wert"-Paare, dekodiert Escapes
#                                (letzter Wert gewinnt, Warnung mit Dateizeile)
#   Normalisiere-Zeilenenden   - CRLF/CR -> LF fuer den Vergleich
#   Compare-L10nKeys           - teilt in NEU / GLEICH / ABWEICHEND / NUR-PORTAL;
#                                fremde und zurueckgehaltene Schluessel fallen aus NEU
#   Konvertiere-PortalKoerper  - leer/null/{}/nicht parsebares JSON -> $null (N-3.1)
#   Get-PortalDe               - Haupt-View one,shared + shared (2 GET, no-cache);
#                                $null bei unverwertbarem Koerper (N-3.1)
#   Get-PortalBereiche         - Fremd-Bereichs-GETs, baut die FREMD-Menge (N-1)
#   Test-L10nPlausibilitaet    - Grenzen 400 (Portal) / 200 (NEU), benannt (N-3.2)
#   New-L10nImportBody         - baut das Paket aus NEU + Freigaben
#   Test-L10nImportBody        - Sperren e1..e8 vor jedem Senden

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
        [Parameter(Mandatory = $true)] [AllowEmptyCollection()] [string[]]$RohPortal,
        [hashtable]$Fremd = @{},
        [hashtable]$Zurueckgehalten = @{}
    )
    $neu = New-Object System.Collections.Generic.List[string]
    $gleich = New-Object System.Collections.Generic.List[string]
    $abweichend = New-Object System.Collections.Generic.List[object]
    $nurPortal = New-Object System.Collections.Generic.List[string]
    $fremdTreffer = New-Object System.Collections.Generic.List[object]
    $zurueckTreffer = New-Object System.Collections.Generic.List[object]
    foreach ($k in $Map.Keys) {
        if (-not $Portal.ContainsKey($k)) {
            # N-1 (B-1): liegt der Schluessel in einem fremden Portal-Bereich (SHARED
            # eingeschlossen), faellt er aus NEU heraus und wird nicht gesendet
            # (CEO-Entscheid 21.09.2026). FREMD ist case-insensitiv, weil der
            # Import-Endpunkt scope-uebergreifend per SQL "=" sucht
            # (L10nApiController.cs:160-161) - die Pruefung hier muss mindestens so
            # scharf sein.
            if ($Fremd.ContainsKey($k)) {
                $fremdTreffer.Add([pscustomobject]@{ Key = $k; Bereiche = @($Fremd[$k] | Sort-Object) })
                continue
            }
            # N-2 (B-6): zurueckgehaltene Schluessel (benannte Liste im Aufrufskript)
            # fallen ebenfalls aus NEU heraus.
            if ($Zurueckgehalten.ContainsKey($k)) {
                $zurueckTreffer.Add([pscustomobject]@{ Key = $k; Grund = [string]$Zurueckgehalten[$k] })
                continue
            }
            $neu.Add($k)
            continue
        }
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
        Fremd      = @($fremdTreffer | Sort-Object -Property Key)
        Zurueckgehalten = @($zurueckTreffer | Sort-Object -Property Key)
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

function Konvertiere-PortalKoerper {
    param([string]$RohJson)
    # N-3.1 (B-9): leerer Koerper, "null", "{}" und nicht parsebares JSON ergeben $null.
    # Der Aufrufer entscheidet, ob $null einen Abbruch bedeutet: fuer den Haupt-View
    # one,shared und fuer shared ja (Exit 4 im Aufrufskript); fuer die Fremd-Bereiche
    # ist "{}" legal (web und sa liefern es dauerhaft, belege/r2_n1_bereiche.txt).
    if ([string]::IsNullOrWhiteSpace($RohJson)) { return $null }
    $trim = $RohJson.Trim()
    if ($trim -eq "{}") { return $null }
    if ($trim -eq "null") { return $null }
    try {
        $karte = $RohJson | ConvertFrom-Json -AsHashtable
    } catch {
        return $null
    }
    if ($null -eq $karte -or $karte.Count -eq 0) { return $null }
    return $karte
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
        # Koerper-Pruefung vor der Kopfzeilen-Auswertung: leer/null/{}/kaputt bricht
        # kontrolliert ab (N-3.1), statt ein Objekt mit $null-Feldern durchzureichen -
        # genau das war der Exit-1-Bindungsfehler aus PRUEFBERICHT_B P-9.
        $karte = Konvertiere-PortalKoerper $antwort1.Content
        if ($null -eq $karte) { return $null }
        $nurShared = "$PortalUrl/api/translations/de.json?scope=shared"
        $antwort2 = Invoke-WebRequest -Uri $nurShared -Headers @{ "Cache-Control" = "no-cache" } -TimeoutSec 30
        if ($antwort2.StatusCode -ne 200) { return $null }
        # SHARED muss geprueft sein (Sperre e3 braucht die Menge): auch hier $null bei
        # unverwertbarem Koerper - ein still leerer SHARED-Scope waere ein Schutzverlust.
        $shared = Konvertiere-PortalKoerper $antwort2.Content
        if ($null -eq $shared) { return $null }
        $uhr.Stop()
        $dauer2 = $uhr.ElapsedMilliseconds
        $rohKeys = Get-JsonRohSchluessel $antwort1.Content
        $rohShared = Get-JsonRohSchluessel $antwort2.Content
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

function Get-PortalBereiche {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)] [string]$PortalUrl,
        [Parameter(Mandatory = $true)] [AllowEmptyCollection()] [string[]]$Bereiche,
        [AllowEmptyCollection()] [string[]]$Shared = @()
    )
    # N-1 (B-1): holt jeden weiteren Portal-Bereich (oeffentlicher GET, kein Schluessel)
    # und baut FREMD = Schluessel, die in einem Bereich ausser ONE existieren (SHARED
    # eingeschlossen). Bereichsliste mit Code-Beleg: belege/r2_n1_bereiche.txt,
    # Gegenprobe am lebenden Portal: belege/r2_n1_gets.txt; die Grenze der Erhebung
    # (L-213) ist im Beleg benannt. Die Tabelle ist case-insensitiv: der Import sucht
    # scope-uebergreifend per SQL "=" (L10nApiController.cs:160-161), die Pruefung hier
    # muss mindestens so scharf sein. Gueltiges leeres "{}" zaehlt als 0 Schluessel
    # (web/sa liefern es dauerhaft); leerer Koerper, "null" oder nicht parsebares JSON
    # gilt als Scheitern -> $null -> Exit 4 im Aufrufskript.
    $fremd = @{}
    $zaehler = @{}
    $uhr = [System.Diagnostics.Stopwatch]::StartNew()
    $anzahl = 0
    try {
        foreach ($b in $Bereiche) {
            $uri = "$PortalUrl/api/translations/de.json?scope=$b"
            $antwort = Invoke-WebRequest -Uri $uri -Headers @{ "Cache-Control" = "no-cache" } -TimeoutSec 30
            $anzahl++
            if ($antwort.StatusCode -ne 200) { return $null }
            $roh = [string]$antwort.Content
            if ([string]::IsNullOrWhiteSpace($roh) -or $roh.Trim() -eq "null") { return $null }
            $menge = @()
            if ($roh.Trim() -ne "{}") {
                try {
                    $karte = $roh | ConvertFrom-Json -AsHashtable
                } catch {
                    return $null
                }
                if ($null -eq $karte) { return $null }
                $menge = @($karte.Keys)
            }
            $bereichName = $b.ToUpper()
            $zaehler[$bereichName] = $menge.Count
            foreach ($k in $menge) {
                if ($fremd.ContainsKey($k)) {
                    if ($bereichName -notin @($fremd[$k])) { $fremd[$k] += $bereichName }
                } else {
                    $fremd[$k] = @($bereichName)
                }
            }
        }
        foreach ($k in $Shared) {
            if ($fremd.ContainsKey($k)) {
                if ("SHARED" -notin @($fremd[$k])) { $fremd[$k] += "SHARED" }
            } else {
                $fremd[$k] = @("SHARED")
            }
        }
    } catch {
        return $null
    }
    $uhr.Stop()
    return [pscustomobject]@{
        Fremd = $fremd
        Zaehler = $zaehler
        AnzahlGet = $anzahl
        DauerMs = $uhr.ElapsedMilliseconds
    }
}

# Plausibilitaetsgrenzen (N-3.2, B-9), benannt und am Portalstand gemessen
# (belege/r2_n1_bereiche.txt, 2026-09-21):
#   one,shared = 469 Schluessel -> Untergrenze 400 faengt ein halb befuelltes oder
#                                  falsches Portal ab (der Haupt-View ist nie leer).
#   NEU = 135 (plus 4 Freigaben) -> Obergrenze 200 faengt "fast die ganze Map neu" ab
#                                  (falsche Instanz oder falscher Scope).
$script:MindestPortalSchluessel = 400
$script:HoechstNeueSchluessel = 200

function Test-L10nPlausibilitaet {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)] [int]$PortalSchluessel,
        [Parameter(Mandatory = $true)] [int]$NeuSchluessel
    )
    $verstoesse = New-Object System.Collections.Generic.List[string]
    if ($PortalSchluessel -lt $script:MindestPortalSchluessel) {
        $verstoesse.Add(("Plausibilitaet: Portal liefert {0} Schluessel in scope=one,shared (Grenze: {1}) - verdaechtig wenig, kein Paket." -f $PortalSchluessel, $script:MindestPortalSchluessel))
    }
    if ($NeuSchluessel -gt $script:HoechstNeueSchluessel) {
        $verstoesse.Add(("Plausibilitaet: {0} NEU-Schluessel (Grenze: {1}) - fast die ganze Map wuerde neu gesendet, kein Paket." -f $NeuSchluessel, $script:HoechstNeueSchluessel))
    }
    return $verstoesse.ToArray()
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
        [string[]]$Freigegeben = @(),
        [hashtable]$Fremd = @{},
        [hashtable]$Zurueckgehalten = @{}
    )
    $verstoesse = New-Object System.Collections.Generic.List[string]
    $enFeld = 'sourceEn'
    foreach ($k in $Body.keys) {
        if ($null -ne $k.PSObject.Properties[$enFeld]) {
            $verstoesse.Add(("e1: Schluessel '{0}' traegt ein {1}-Feld - im Paket verboten (Portal legt EN an)." -f $k.newKey, $enFeld))
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
        if ($Fremd.Count -gt 0 -and $Fremd.ContainsKey($k.newKey)) {
            # e7 greift auch fuer Freigaben: kein stilles Weglassen, Abbruch im
            # Aufrufskript (N-1.4).
            $verstoesse.Add(("e7: Schluessel '{0}' liegt in einem fremden Portal-Bereich ({1}) - nicht anfassen." -f $k.newKey, (@($Fremd[$k.newKey]) -join ", ")))
        }
        if ($Zurueckgehalten.Count -gt 0 -and $Zurueckgehalten.ContainsKey($k.newKey)) {
            $verstoesse.Add(("e8: Schluessel '{0}' ist zurueckgehalten ({1}) - nicht senden." -f $k.newKey, [string]$Zurueckgehalten[$k.newKey]))
        }
    }
    return $verstoesse.ToArray()
}
