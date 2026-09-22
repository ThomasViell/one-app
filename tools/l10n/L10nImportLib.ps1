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
#   Get-PortalBereiche         - Fremd-Bereichs-GETs de+en plus SA-Sicht sa/{lang}.json,
#                                baut die FREMD-Menge (N-1); Positivliste, Mindestumfang
#                                je Sprache und Pflicht-Schluessel (M-3)
#   Test-L10nPlausibilitaet    - Grenzen 450 (Portal) / 200 (NEU), benannt (N-3.2, M-3.4)
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
    # ist "{}" nur fuer WEB legal ($script:LeerErlaubteBereiche, M-3.1) - die
    # Bereichs-Funktion prueft das selbst, sa/{lang}.json liefert nie leer
    # (514/42, belege/r3_messung.txt).
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

# M-3 (C-2, C-4, A-3): Mindestumfang je Fremd-Bereich und je Sprache, benannt und an
# der Messung vom 21.09.2026 belegt (belege/r3_messung.txt im Kettenordner):
#   de hmx 749 / en hmx 804 (en: Pruefer-Bezug belege/pruefB2_unabhaengig.txt) -> 700
#   de app 510 / en app 38                                              -> 450 / 30
#   de catalog 1266 / en catalog 1266 (Pruefer-Bezug, wie oben)         -> 1200
#   de manhole 68 / en manhole 68                                       -> 60
#   sa/de.json 514 / sa/en.json 42                                      -> 500 / 30
# Unterschreitung oder {} bei einem nicht als leer erlaubten Bereich -> $null ->
# Exit 4 im Aufrufskript, kein Paket. Die Messung ist eine Momentaufnahme; die
# Grenzen liegen bewusst unter den gemessenen Staenden (L-213, im Beleg benannt).
$script:Mindestumfang = @{
    'DE_HMX' = 700; 'EN_HMX' = 700
    'DE_APP' = 450; 'EN_APP' = 30
    'DE_CATALOG' = 1200; 'EN_CATALOG' = 1200
    'DE_MANHOLE' = 60; 'EN_MANHOLE' = 60
}
$script:MindestumfangSaDe = 500
$script:MindestumfangSaEn = 30

# M-3.1 (C-2): Positivliste der Bereiche, am drainq.web-Code belegt
# (ScopeClassifier.cs:12-18, Kopf 5d8922d, nur gelesen). Ausdruecklich leer erlaubt
# ist nur, was heute gemessen leer ist (belege/r3_messung.txt) UND am Code als leer
# erklaerbar ist: WEB entsteht ausschliesslich im Start-Seed aus den eingebetteten
# Ressourcen (TranslationSeedService.cs:212-220; DrainQ.Web.csproj:31-33); der Seed
# ueberspringt still, wenn die Einbettung leer oder unladbar ist
# (TranslationSeedService.cs:82-86) und laeuft im try/catch (Program.cs:552-558).
# Die Leere ist damit MOEGLICH, aber NICHT GARANTIERT: nach einem Seed-Lauf kann
# web > 0 liefern - dann greift der normale FREMD-Abzug (L-213, benannt).
# 'sa' ist kein Bereich (SA-Schluessel tragen Scope="APP", SaSeedService.cs:59-67):
# ersetzt durch den Abruf sa/{lang}.json, Schluessel daraus gehen in FREMD.
$script:BereichsPositivliste = @('APP', 'WEB', 'CATALOG', 'ONE', 'HMX', 'SHARED', 'MANHOLE')
$script:LeerErlaubteBereiche = @('WEB')

# M-3.2: Pflicht-Schluessel je Abfrage (Schluessel "sprache_bereich"). 'ok' ist der
# einzige heute gemessene FREMD-Treffer (HMX); fehlt er, ist FREMD mit hoher
# Wahrscheinlichkeit unvollstaendig - genau das zeigte der Pruefer-Mock
# "hmx_ohne_ok" (Exit 0 am Stand 4dce12c). Gemessen: de_hmx ok=True; en_hmx war per
# WebFetch nicht auszaehlbar (Antwortlimit, im Messbeleg benannt), deshalb ist 'ok'
# nur in de Pflicht.
$script:PflichtSchluessel = @{ 'DE_HMX' = 'ok' }

function Get-PortalBereiche {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)] [string]$PortalUrl,
        [Parameter(Mandatory = $true)] [AllowEmptyCollection()] [string[]]$Bereiche,
        [AllowEmptyCollection()] [string[]]$Shared = @()
    )
    # N-1 (B-1) und M-3 (C-2/C-4/A-3): holt jeden Fremd-Bereich in de UND en
    # (oeffentlicher GET, kein Schluessel) plus die SA-Sicht sa/{lang}.json und baut
    # FREMD = Schluessel, die in einem Bereich ausser ONE existieren (SHARED
    # eingeschlossen). Bereichsliste mit Code-Beleg: belege/r2_n1_bereiche.txt,
    # Gegenprobe am lebenden Portal: belege/r2_n1_gets.txt; die Grenze der Erhebung
    # (L-213) ist im Beleg benannt.
    #   - Positivliste $script:BereichsPositivliste (M-3.1): ein Bereich ausserhalb
    #     der Liste ist ein Skriptfehler -> $null -> Exit 4.
    #   - {} ist nur fuer $script:LeerErlaubteBereiche zulaessig; jeder andere
    #     Bereich mit {} oder unter seinem Mindestumfang -> $null -> Exit 4 (M-3.2).
    #   - FREMD wird aus de UND en gebildet (55 HMX-Referenzen stehen nur in en,
    #     M-3.3). Pflicht-Schluessel je Abfrage (DE_HMX: 'ok').
    #   - Die Tabelle ist case-insensitiv: der Import sucht scope-uebergreifend per
    #     SQL "=" (L10nApiController.cs:160-161), die Pruefung hier muss mindestens
    #     so scharf sein. Leerer Koerper, "null" oder nicht parsebares JSON gilt als
    #     Scheitern -> $null -> Exit 4 im Aufrufskript.
    $fremd = [System.Collections.Hashtable]::new([System.StringComparer]::OrdinalIgnoreCase)
    $zaehler = @{}
    $uhr = [System.Diagnostics.Stopwatch]::StartNew()
    $anzahl = 0
    try {
        foreach ($b in $Bereiche) {
            $bereichName = $b.ToUpper()
            if ($bereichName -notin $script:BereichsPositivliste) {
                return $null
            }
            foreach ($sprache in @("de", "en")) {
                $uri = "$PortalUrl/api/translations/$sprache.json?scope=$b"
                $antwort = Invoke-WebRequest -Uri $uri -Headers @{ "Cache-Control" = "no-cache" } -TimeoutSec 30
                $anzahl++
                if ($antwort.StatusCode -ne 200) { return $null }
                $roh = [string]$antwort.Content
                if ([string]::IsNullOrWhiteSpace($roh) -or $roh.Trim() -eq "null") { return $null }
                $karte = $null
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
                $abfrage = ($sprache + "_" + $bereichName).ToUpper()
                if ($menge.Count -eq 0 -and $bereichName -notin $script:LeerErlaubteBereiche) {
                    # M-3.1: leer ist nur erlaubt, was gemessen leer und am Code
                    # erklaerbar ist. Alles andere: kein Paket (Exit 4).
                    return $null
                }
                $mindest = 0
                if ($script:Mindestumfang.ContainsKey($abfrage)) { $mindest = $script:Mindestumfang[$abfrage] }
                if ($menge.Count -lt $mindest) {
                    # M-3.2: Unterschreitung des Mindestumfangs: kein Paket (Exit 4).
                    return $null
                }
                if ($script:PflichtSchluessel.ContainsKey($abfrage)) {
                    $pflicht = $script:PflichtSchluessel[$abfrage]
                    # Die Tabelle ist case-insensitiv (Zeile 285-287): $karte kommt aus
                    # ConvertFrom-Json -AsHashtable und ist case-sensitiv, ContainsKey()
                    # darauf verfehlt daher z. B. den Schluessel "OK". -in mit -Keys
                    # vergleicht case-insensitiv (PowerShell-Standard fuer -in/-eq).
                    if ($null -eq $karte -or $pflicht -notin @($karte.Keys)) { return $null }
                }
                $zaehler[$abfrage] = $menge.Count
                foreach ($k in $menge) {
                    if ($fremd.ContainsKey($k)) {
                        if ($bereichName -notin @($fremd[$k])) { $fremd[$k] += $bereichName }
                    } else {
                        $fremd[$k] = @($bereichName)
                    }
                }
            }
        }
        # M-3.1: SA-Sicht (sa/{lang}.json) statt des bisherigen Schein-Bereichs 'sa'.
        # Schluessel daraus gehen in FREMD wie die der anderen Bereiche; leer oder
        # unter Mindestumfang ist nie erlaubt (gemessen 514/42).
        foreach ($sprache in @("de", "en")) {
            $uri = "$PortalUrl/api/translations/sa/$sprache.json"
            $antwort = Invoke-WebRequest -Uri $uri -Headers @{ "Cache-Control" = "no-cache" } -TimeoutSec 30
            $anzahl++
            if ($antwort.StatusCode -ne 200) { return $null }
            $roh = [string]$antwort.Content
            if ([string]::IsNullOrWhiteSpace($roh) -or $roh.Trim() -eq "null") { return $null }
            if ($roh.Trim() -eq "{}") { return $null }
            try {
                $karte = $roh | ConvertFrom-Json -AsHashtable
            } catch {
                return $null
            }
            if ($null -eq $karte) { return $null }
            $menge = @($karte.Keys)
            $abfrage = ("SA_" + $sprache).ToUpper()
            $mindest = $script:MindestumfangSaDe
            if ($sprache -eq "en") { $mindest = $script:MindestumfangSaEn }
            if ($menge.Count -lt $mindest) { return $null }
            $zaehler[$abfrage] = $menge.Count
            foreach ($k in $menge) {
                if ($fremd.ContainsKey($k)) {
                    if ("SA" -notin @($fremd[$k])) { $fremd[$k] += "SA" }
                } else {
                    $fremd[$k] = @("SA")
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

# Plausibilitaetsgrenzen (N-3.2, B-9; M-3.4, C-4), benannt und am Portalstand
# gemessen (belege/r2_n1_bereiche.txt und belege/r3_messung.txt, 2026-09-21):
#   one,shared = 469 Schluessel -> Untergrenze 450. M-3.4: die alte Grenze 400 liess
#   den um 68 gekuerzten Abruf des Pruefers (469 - 68 = 401) durchgehen; 450 laesst
#   hoechstens 19 Schluessel Schrumpfung zu und faengt damit auch dessen naechste
#   Stufe ab. Der Haupt-View ist nie leer; 450 liegt knapp unter dem gemessenen
#   Stand und deutlich ueber jedem Teilabruf.
#   NEU = 135 (plus 4 Freigaben) -> Obergrenze 200 faengt "fast die ganze Map neu" ab
#                                  (falsche Instanz oder falscher Scope).
$script:MindestPortalSchluessel = 450
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
