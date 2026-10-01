# Werkseinrichtung.Texte.Tests.ps1 - Pester-Tests fuer tools/werkseinrichtung
# (Welle werkzeug-partner-en, PLAN Abschnitt 4; Pester 3.4.0, Windows PowerShell 5.1 wie
# Start-Werkseinrichtung.cmd - deshalb KEIN #Requires -Version 7.0)
#
# Lauf:
#   powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "Import-Module Pester -RequiredVersion 3.4.0;
#     (Invoke-Pester -Path 'tools\werkseinrichtung\tests' -PassThru) | Select-Object TotalCount,PassedCount,FailedCount | Format-List"
#
# Kein echtes Geraet, kein Portal: adb ist ein Stub (fake-adb.ps1 im TestDrive), das Portal-Manifest
# und der Download sind gemockt.
#
# Testliste:
#   T-1  Get-WerkzeugSprache: de-DE/de-AT -> de, en-US/fr-FR -> en, -Erzwungen schlaegt die Kultur,
#        -Erzwungen '' = automatisch, -Erzwungen fr wirft (8 Faelle)
#   T-2  Katalog: Schluesselformat, de und en nicht leer, Anzahl >= 60, die 9 unveraenderten rot.*
#        byte-gleich zu den alten Literalen, die 2 neuen ROT-Texte im verbindlichen Wortlaut
#   T-3  AST ueber die drei Skripte UND Texte.ps1: jedes -Key/-Schluessel-Argument ist eine
#        Zeichenkettenkonstante im Katalog (Ausnahme: Weiterreichung des eigenen Parameters in den
#        benannten Weiterreich-Funktionen), benutzt >= 60, jeder Katalogschluessel >= 1x benutzt
#   T-4  keine fest verdrahteten Fenstertexte im Partnerweg: Inhaltspruefung aller Zeichenketten
#        (Umlaut oder zwei Woerter) UND Ausgabestellen-Pruefung (Write-Host/Read-Host/Write-Headline/
#        Fenstertitel, AddLog, SourceLabel/Detail/ProtokollLabel: kein Buchstabe als Literal);
#        Regionen Bestandsgeraet je Datei genau 1 (Hauptskript <= 20, Worker <= 45 Zeilen, Update 0);
#        im Worker ausserhalb der Region kein Write-Result -Grund
#   T-5  keine Werksreset-Empfehlung (Verneinungen ausgenommen) in Katalog und Skript-Zeichenketten;
#        kein Verweis auf eine Anleitung/einen Ruecklaeufer-Abschnitt (de und en)
#   T-6  Geraetejob in-process mit adb-Stub: S1 Konto vorhanden, S2 App installiert, S3 Konten
#        unlesbar, S4 fremder Eigentuemer je de/en, S5 = S1 im Bestandsgeraet-Modus
#   T-7  Protokollformat unveraendert (JSON-Felder, CSV-Kopf und -Zeile), Verdrahtung im Hauptskript
#        (Start-Job 15 Argumente, -Sprache an Invoke-WerkzeugSelfUpdate, Receive-Job nicht verworfen,
#        Fenstergrund aus Select-FensterGrund bis zur Anzeige verfolgt)
#   T-8  Parser 0 Fehler und UTF-8-BOM fuer die fuenf .ps1 im Scope
#   T-9  Invoke-WerkzeugSelfUpdate -Sprache en: Fenster englisch, ProtokollLabel deutsch und
#        byte-gleich zum alten Format (5 Faelle, Portal gemockt)
#   T-10 Get-WerkzeugText: Platzhalter, unbekannter Schluessel/Sprache wirft, ArrayList als -Werte
#   T-11 Select-FensterGrund und New-AbbruchErgebnis
#   T-12 Convert-BekannteMeldung: die feste deutsche Meldung aus Get-ApkSignatureFingerprint.ps1
#        wird im Fenster englisch, alles andere bleibt; beide Aufrufer benutzen die Funktion

$here       = Split-Path -Parent $MyInvocation.MyCommand.Path
$werkzeug   = Split-Path -Parent $here
$textePfad  = Join-Path $werkzeug 'Texte.ps1'
$hauptPfad  = Join-Path $werkzeug 'Werkseinrichtung.ps1'
$workerPfad = Join-Path $werkzeug 'Invoke-DeviceSetup.ps1'
$updatePfad = Join-Path $werkzeug 'Update-WerkzeugApp.ps1'
$fingerPfad = Join-Path $werkzeug 'Get-ApkSignatureFingerprint.ps1'
$testPfad   = Join-Path $here 'Werkseinrichtung.Texte.Tests.ps1'
. $textePfad
. $updatePfad
. $fingerPfad

# Benannte Positivliste exakter Zeichenketten, die T-4 trotz Treffer zulaesst (Soll 0, hoechstens 5).
$Positivliste = @()

# Weiterreich-Funktionen: nur hier darf ein -Key/-Schluessel-Argument der eigene Parameter sein.
$Weiterreicher = @{ 'Get-FensterText' = 'Key'; 'Write-Result' = 'Schluessel'; 'AddLog' = 'Key' }

$AdbStubText = @'
# adb-Stub fuer die Tests: ohne param-Block, bekommt "-s <serial> <befehl...>" in $args.
$alle = $args -join ' '
$szenario = @{}
foreach ($zeile in (Get-Content -LiteralPath (Join-Path $PSScriptRoot 'szenario.txt'))) {
    $teile = $zeile -split '=', 2
    $szenario[$teile[0]] = $teile[1]
}
if ($alle -match 'dumpsys account') { $szenario['konto'] }
elseif ($alle -match 'dpm list-owners') { $szenario['eigentuemer'] }
elseif ($alle -match 'pm list packages') { $szenario['pakete'] }
elseif ($alle -match 'dumpsys package') { $szenario['version'] }
else { '' }
'@

function Get-TestParse {
    param([string]$Pfad)
    $tokens = $null
    $fehler = $null
    $ast = [System.Management.Automation.Language.Parser]::ParseFile($Pfad, [ref]$tokens, [ref]$fehler)
    return [pscustomobject]@{ Ast = $ast; Fehler = @($fehler) }
}

function Get-Befehle {
    param($Ast, [string]$Name)
    return @($Ast.FindAll({ param($n) $n -is [System.Management.Automation.Language.CommandAst] }, $true) |
        Where-Object { $_.GetCommandName() -eq $Name })
}

function Get-Zeichenketten {
    param($Ast)
    return @($Ast.FindAll({ param($n)
        ($n -is [System.Management.Automation.Language.StringConstantExpressionAst]) -or
        ($n -is [System.Management.Automation.Language.ExpandableStringExpressionAst]) }, $true))
}

function Get-ParameterArgument {
    param($Befehl, [string]$Parameter)
    $el = $Befehl.CommandElements
    for ($i = 1; $i -lt $el.Count; $i++) {
        if ($el[$i] -is [System.Management.Automation.Language.CommandParameterAst] -and $el[$i].ParameterName -eq $Parameter) {
            if ($null -ne $el[$i].Argument) { return $el[$i].Argument }
            if ($i + 1 -lt $el.Count) { return $el[$i + 1] }
        }
    }
    return $null
}

function Get-UmgebendeFunktion {
    param($Knoten)
    $p = $Knoten.Parent
    while ($null -ne $p) {
        if ($p -is [System.Management.Automation.Language.FunctionDefinitionAst]) { return $p.Name }
        $p = $p.Parent
    }
    return ''
}

function Test-HatBefehlVorfahr {
    # true, wenn zwischen $Knoten (exklusiv) und $Bis (exklusiv, $null = Wurzel) ein CommandAst liegt,
    # dessen Name in $Befehlsnamen steht (leere Liste = jeder Befehl).
    param($Knoten, $Bis, [string[]]$Befehlsnamen = @())
    $p = $Knoten.Parent
    while ($null -ne $p -and -not [object]::ReferenceEquals($p, $Bis)) {
        if ($p -is [System.Management.Automation.Language.CommandAst]) {
            if ($Befehlsnamen.Count -eq 0 -or $Befehlsnamen -contains $p.GetCommandName()) { return $true }
        }
        $p = $p.Parent
    }
    return $false
}

function Get-Resttext {
    # Quelltext der Zeichenkette ohne die eingebetteten Ausdruecke ($x, $(...)).
    param($Knoten)
    $text = $Knoten.Extent.Text
    if ($Knoten -is [System.Management.Automation.Language.ExpandableStringExpressionAst]) {
        foreach ($n in $Knoten.NestedExpressions) { $text = $text.Replace($n.Extent.Text, '') }
    }
    return $text
}

function Test-IstRegexOperand {
    param($Knoten)
    $kind = $Knoten
    $p = $Knoten.Parent
    if ($p -is [System.Management.Automation.Language.ArrayLiteralAst]) { $kind = $p; $p = $p.Parent }
    if ($p -is [System.Management.Automation.Language.BinaryExpressionAst] -and [object]::ReferenceEquals($p.Right, $kind)) {
        return (@('Imatch', 'Inotmatch', 'Ireplace', 'Cmatch', 'Cnotmatch', 'Creplace') -contains $p.Operator.ToString())
    }
    return $false
}

function Get-Regionen {
    param([string]$Pfad)
    $zeilen = [System.IO.File]::ReadAllLines($Pfad)
    $liste = @()
    $von = 0
    for ($i = 0; $i -lt $zeilen.Count; $i++) {
        if ($zeilen[$i] -match '^\s*#region Bestandsgeraet-nur-deutsch\s*$') { $von = $i + 1 }
        elseif ($von -gt 0 -and $zeilen[$i] -match '^\s*#endregion') {
            $liste += [pscustomobject]@{ Von = $von; Bis = $i + 1 }
            $von = 0
        }
    }
    return $liste
}

function Test-InRegion {
    param([int]$Zeile, $Regionen)
    foreach ($r in @($Regionen)) {
        if ($Zeile -ge $r.Von -and $Zeile -le $r.Bis) { return $true }
    }
    return $false
}

function Get-SenkenTreffer {
    # Ausgabe-Argument: jede Zeichenkette darin, die nicht unter einem Befehl (z. B. Get-FensterText)
    # steht, darf keinen Buchstaben tragen (faengt auch "Datei:" / "Protokoll:").
    param($Element, [string]$Datei)
    $treffer = @()
    foreach ($s in (Get-Zeichenketten -Ast $Element)) {
        if (-not [object]::ReferenceEquals($s, $Element) -and (Test-HatBefehlVorfahr -Knoten $s -Bis $Element)) { continue }
        # Eigenschaftsnamen ($x.SourceLabel, $x.Detail) sind im AST auch Zeichenkettenkonstanten - kein Text.
        if ($s.Parent -is [System.Management.Automation.Language.MemberExpressionAst] -and [object]::ReferenceEquals($s.Parent.Member, $s)) { continue }
        $rest = Get-Resttext -Knoten $s
        if ($Positivliste -contains $rest) { continue }
        if ($rest -match '\p{L}{2,}') { $treffer += "$($Datei):$($s.Extent.StartLineNumber):$rest" }
    }
    return $treffer
}

function Get-FesteFenstertexte {
    # Liefert @{ Treffer; Zeichenketten; Senken } fuer eine der drei Skriptdateien.
    param([string]$Pfad, [string]$Art)
    $datei = Split-Path -Leaf $Pfad
    $ast = (Get-TestParse -Pfad $Pfad).Ast
    $regionen = @(Get-Regionen -Pfad $Pfad)
    $treffer = @()
    $geprueft = 0
    foreach ($k in (Get-Zeichenketten -Ast $ast)) {
        $zeile = $k.Extent.StartLineNumber
        if (Test-InRegion -Zeile $zeile -Regionen $regionen) { continue }
        if ($Art -eq 'worker' -and (Test-HatBefehlVorfahr -Knoten $k -Bis $null -Befehlsnamen @('Log'))) { continue }
        if (Test-IstRegexOperand -Knoten $k) { continue }
        $rest = Get-Resttext -Knoten $k
        if ($Positivliste -contains $rest) { continue }
        $geprueft++
        if ($rest -match '[äöüÄÖÜß]' -or $rest -match '\p{L}{3,}.*\s.*\p{L}{3,}') { $treffer += "$($datei):$($zeile):$rest" }
    }

    $senkenBefehle = @()
    if ($Art -eq 'haupt') { $senkenBefehle = @('Write-Host', 'Read-Host', 'Write-Headline') }
    if ($Art -eq 'update') { $senkenBefehle = @('AddLog') }
    $wertParameter = @('ForegroundColor', 'BackgroundColor', 'Separator', 'Key', 'Werte')
    $senken = 0
    $befehle = @($ast.FindAll({ param($n) $n -is [System.Management.Automation.Language.CommandAst] }, $true))
    foreach ($b in $befehle) {
        if ($senkenBefehle -notcontains $b.GetCommandName()) { continue }
        if (Test-InRegion -Zeile $b.Extent.StartLineNumber -Regionen $regionen) { continue }
        $el = $b.CommandElements
        for ($i = 1; $i -lt $el.Count; $i++) {
            $e = $el[$i]
            if ($e -is [System.Management.Automation.Language.CommandParameterAst]) {
                if ($null -eq $e.Argument -and $wertParameter -contains $e.ParameterName) { $i++ }
                continue
            }
            $senken++
            $treffer += @(Get-SenkenTreffer -Element $e -Datei $datei)
        }
    }
    if ($Art -eq 'update') {
        foreach ($h in @($ast.FindAll({ param($n) $n -is [System.Management.Automation.Language.HashtableAst] }, $true))) {
            foreach ($paar in $h.KeyValuePairs) {
                if (@('SourceLabel', 'Detail', 'ProtokollLabel') -contains $paar.Item1.Extent.Text) {
                    $senken++
                    $treffer += @(Get-SenkenTreffer -Element $paar.Item2 -Datei $datei)
                }
            }
        }
    }
    if ($Art -eq 'haupt') {
        foreach ($z in @($ast.FindAll({ param($n) $n -is [System.Management.Automation.Language.AssignmentStatementAst] }, $true))) {
            if ($z.Left.Extent.Text -like '*WindowTitle') {
                $senken++
                $treffer += @(Get-SenkenTreffer -Element $z.Right -Datei $datei)
            }
        }
    }
    return [pscustomobject]@{ Treffer = @($treffer); Zeichenketten = $geprueft; Senken = $senken }
}

Describe 'Werkseinrichtung - Textkatalog und Fenstersprache' {

    Context 'T-1 Get-WerkzeugSprache' {
        It 'T-1a: de-DE -> de' {
            Mock Get-UICulture { [pscustomobject]@{ Name = 'de-DE' } }
            Get-WerkzeugSprache | Should BeExactly 'de'
        }
        It 'T-1b: de-AT -> de' {
            Mock Get-UICulture { [pscustomobject]@{ Name = 'de-AT' } }
            Get-WerkzeugSprache | Should BeExactly 'de'
        }
        It 'T-1c: en-US -> en' {
            Mock Get-UICulture { [pscustomobject]@{ Name = 'en-US' } }
            Get-WerkzeugSprache | Should BeExactly 'en'
        }
        It 'T-1d: fr-FR -> en' {
            Mock Get-UICulture { [pscustomobject]@{ Name = 'fr-FR' } }
            Get-WerkzeugSprache | Should BeExactly 'en'
        }
        It 'T-1e: -Erzwungen en auf de-DE -> en' {
            Mock Get-UICulture { [pscustomobject]@{ Name = 'de-DE' } }
            Get-WerkzeugSprache -Erzwungen 'en' | Should BeExactly 'en'
        }
        It 'T-1f: -Erzwungen de auf en-US -> de' {
            Mock Get-UICulture { [pscustomobject]@{ Name = 'en-US' } }
            Get-WerkzeugSprache -Erzwungen 'de' | Should BeExactly 'de'
        }
        It 'T-1g: -Erzwungen leer auf en-US -> automatisch en' {
            Mock Get-UICulture { [pscustomobject]@{ Name = 'en-US' } }
            Get-WerkzeugSprache -Erzwungen '' | Should BeExactly 'en'
        }
        It 'T-1h: -Erzwungen fr wirft' {
            Mock Get-UICulture { [pscustomobject]@{ Name = 'de-DE' } }
            { Get-WerkzeugSprache -Erzwungen 'fr' } | Should Throw
        }
    }

    Context 'T-2 Katalog' {
        It 'T-2a: jeder Schluessel hat Format, nicht-leeres de und en; Anzahl >= 60' {
            $katalog = Get-WerkzeugTexte
            $fehler = @()
            foreach ($e in $katalog.GetEnumerator()) {
                if ($e.Key -cnotmatch '^(allg|haupt|update|rot)\.[a-z0-9_]+$') { $fehler += "Format: $($e.Key)" }
                foreach ($sp in @('de', 'en')) {
                    $wert = $e.Value[$sp]
                    if (-not ($wert -is [string]) -or [string]::IsNullOrEmpty($wert)) { $fehler += "$($e.Key).$sp leer/kein string" }
                }
                if ($e.Value.Count -ne 2) { $fehler += "$($e.Key): $($e.Value.Count) Sprachen statt 2" }
            }
            Write-Host "    T-2a geprueft: $($katalog.Count) Schluessel, $($katalog.Count * 2) Werte"
            ($fehler -join "`n") | Should BeNullOrEmpty
            ($katalog.Count -ge 60) | Should Be $true
        }
        It 'T-2b: die 9 unveraenderten rot.* (de) sind byte-gleich zu den alten Literalen' {
            $soll = @{
                'rot.konto_unlesbar'      = 'Konnte den Konten-Status nicht auslesen (dumpsys account lieferte kein auswertbares Ergebnis).'
                'rot.fremder_eigentuemer' = 'Geraet hat bereits einen ANDEREN Geraeteeigentuemer gesetzt. Nicht automatisch anfassen - bitte Rueckfrage vor jedem weiteren Schritt.'
                'rot.installation'        = 'Installation fehlgeschlagen: {0}'
                'rot.version_falsch'      = 'Nach der Installation stimmt die Version nicht: gefunden {0}/{1}, erwartet {2}/{3}.'
                'rot.werksapp'            = '{0} (Werks-App) laesst sich nicht entfernen und ueberschreibt beim Neustart unseren Autostart. Ausgabe: {1}'
                'rot.startbildschirm'     = 'Startbildschirm konnte nicht auf DrainQ.ONE gesetzt werden. Ausgabe: {0}'
                'rot.eigentuemer'         = 'Geraeteeigentuemer konnte nicht gesetzt werden. Ausgabe: {0}'
                'rot.kiosk'               = 'Kiosk-Betrieb konnte nicht bestaetigt werden (LockTask nicht aktiv oder DrainQ.ONE nicht im Vordergrund).'
                'rot.unerwartet'          = 'Unerwarteter Fehler im Ablauf: {0}'
            }
            $katalog = Get-WerkzeugTexte
            foreach ($e in $soll.GetEnumerator()) {
                $katalog[$e.Key].de | Should BeExactly $e.Value
            }
            $soll.Count | Should Be 9
        }
        It 'T-2c: die zwei neuen ROT-Texte im verbindlichen Wortlaut' {
            $katalog = Get-WerkzeugTexte
            $katalog['rot.konto_vorhanden'].de | Should BeExactly 'Kein fabrikneues Gerät ({0} Benutzerkonto(en) vorhanden). Anlage NICHT zurücksetzen.'
            $katalog['rot.konto_vorhanden'].en | Should BeExactly 'Not a factory-fresh device ({0} user account(s) present). Do NOT reset the device.'
            $katalog['rot.app_vorhanden'].de | Should BeExactly 'Kein fabrikneues Gerät (App bereits installiert, Version {0}/{1}). Anlage NICHT zurücksetzen.'
            $katalog['rot.app_vorhanden'].en | Should BeExactly 'Not a factory-fresh device (app already installed, version {0}/{1}). Do NOT reset the device.'
        }
    }

    Context 'T-3 Schluesselverwendung (AST, drei Skripte + Texte.ps1)' {
        It 'T-3: jedes -Key/-Schluessel-Argument ist eine Konstante im Katalog; jeder Katalogschluessel benutzt' {
            $katalog = Get-WerkzeugTexte
            $benutzt = @{}
            $fehler = @()
            $anzahl = 0
            foreach ($pfad in @($hauptPfad, $workerPfad, $updatePfad, $textePfad)) {
                $datei = Split-Path -Leaf $pfad
                $ast = (Get-TestParse -Pfad $pfad).Ast
                foreach ($b in @($ast.FindAll({ param($n) $n -is [System.Management.Automation.Language.CommandAst] }, $true))) {
                    foreach ($par in @('Key', 'Schluessel')) {
                        $arg = Get-ParameterArgument -Befehl $b -Parameter $par
                        if ($null -eq $arg) { continue }
                        if ($arg -is [System.Management.Automation.Language.StringConstantExpressionAst] -and
                            $arg.StringConstantType -eq [System.Management.Automation.Language.StringConstantType]::SingleQuoted) {
                            $anzahl++
                            $benutzt[$arg.Value] = $true
                            if (-not $katalog.ContainsKey($arg.Value)) { $fehler += "$($datei):$($arg.Extent.StartLineNumber): unbekannter Schluessel '$($arg.Value)'" }
                            continue
                        }
                        $funktion = Get-UmgebendeFunktion -Knoten $b
                        $erlaubt = $Weiterreicher.ContainsKey($funktion) -and
                            ($arg -is [System.Management.Automation.Language.VariableExpressionAst]) -and
                            ($arg.VariablePath.UserPath -eq $Weiterreicher[$funktion])
                        if (-not $erlaubt) { $fehler += "$($datei):$($arg.Extent.StartLineNumber): -$par $($arg.Extent.Text) ist keine Konstante (Funktion '$funktion')" }
                    }
                }
            }
            foreach ($k in $katalog.Keys) {
                if (-not $benutzt.ContainsKey($k)) { $fehler += "Katalogschluessel nie benutzt: $k" }
            }
            Write-Host "    T-3 geprueft: $anzahl Schluessel-Argumente, $($benutzt.Count) verschiedene von $($katalog.Count) Katalogschluesseln"
            ($fehler -join "`n") | Should BeNullOrEmpty
            ($anzahl -ge 60) | Should Be $true
        }
    }

    Context 'T-4 keine fest verdrahteten Fenstertexte im Partnerweg' {
        It 'T-4a: Hauptskript, Worker, Update - 0 Treffer (Liste Datei:Zeile:Wert)' {
            $alle = @()
            $summeZ = 0
            $summeS = 0
            foreach ($paar in @(@($hauptPfad, 'haupt'), @($workerPfad, 'worker'), @($updatePfad, 'update'))) {
                $erg = Get-FesteFenstertexte -Pfad $paar[0] -Art $paar[1]
                $alle += $erg.Treffer
                $summeZ += $erg.Zeichenketten
                $summeS += $erg.Senken
            }
            Write-Host "    T-4a geprueft: $summeZ Zeichenketten, $summeS Ausgabe-Argumente, Treffer $($alle.Count), Positivliste $($Positivliste.Count)"
            ($alle -join "`n") | Should BeNullOrEmpty
            ($Positivliste.Count -le 5) | Should Be $true
        }
        It 'T-4b: je eine Region Bestandsgeraet in Hauptskript (<= 20 Zeilen) und Worker (<= 45), keine im Update' {
            $rh = @(Get-Regionen -Pfad $hauptPfad)
            $rw = @(Get-Regionen -Pfad $workerPfad)
            $ru = @(Get-Regionen -Pfad $updatePfad)
            $rh.Count | Should Be 1
            $rw.Count | Should Be 1
            $ru.Count | Should Be 0
            (($rh[0].Bis - $rh[0].Von + 1) -le 20) | Should Be $true
            (($rw[0].Bis - $rw[0].Von + 1) -le 45) | Should Be $true
        }
        It 'T-4c: im Worker ausserhalb der Region kein Write-Result mit -Grund' {
            $ast = (Get-TestParse -Pfad $workerPfad).Ast
            $regionen = @(Get-Regionen -Pfad $workerPfad)
            $aussen = @(Get-Befehle -Ast $ast -Name 'Write-Result' | Where-Object {
                -not (Test-InRegion -Zeile $_.Extent.StartLineNumber -Regionen $regionen) -and
                $null -ne (Get-ParameterArgument -Befehl $_ -Parameter 'Grund') })
            $alleWr = @(Get-Befehle -Ast $ast -Name 'Write-Result')
            Write-Host "    T-4c geprueft: $($alleWr.Count) Write-Result-Aufrufe"
            (($aussen | ForEach-Object { "$($_.Extent.StartLineNumber): $($_.Extent.Text)" }) -join "`n") | Should BeNullOrEmpty
        }
    }

    Context 'T-5 keine Werksreset-Empfehlung, kein Anleitungsverweis' {
        It 'T-5: Katalogwerte und Skript-Zeichenketten' {
            $verneinung = '(?i)kein Werksreset|ohne Werksreset|NICHT zur(ü|ue)cksetzen|do NOT reset|no factory reset'
            $verboten = '(?i)werksreset|factory reset|werkseinstellung|zur(ü|ue)cksetz|reset the device|\breset\b'
            $anleitung = '(?i)anleitung|r(ü|ue)ckl(ä|ae)ufer|handbuch|abschnitt|\bmanual\b|\binstructions?\b|\bguide\b|\bsection\b'
            $fehler = @()
            $anzahl = 0
            foreach ($e in (Get-WerkzeugTexte).GetEnumerator()) {
                foreach ($sp in @('de', 'en')) {
                    $anzahl++
                    $wert = $e.Value[$sp]
                    if (($wert -replace $verneinung, '') -match $verboten) { $fehler += "Katalog $($e.Key).$($sp): $wert" }
                    if ($wert -match $anleitung) { $fehler += "Katalog $($e.Key).$($sp) (Anleitung): $wert" }
                }
            }
            foreach ($pfad in @($hauptPfad, $workerPfad, $updatePfad)) {
                $datei = Split-Path -Leaf $pfad
                $regionen = @(Get-Regionen -Pfad $pfad)
                foreach ($k in (Get-Zeichenketten -Ast (Get-TestParse -Pfad $pfad).Ast)) {
                    $anzahl++
                    $wert = $k.Extent.Text
                    if (($wert -replace $verneinung, '') -match $verboten) { $fehler += "$($datei):$($k.Extent.StartLineNumber): $wert" }
                    if (-not (Test-InRegion -Zeile $k.Extent.StartLineNumber -Regionen $regionen) -and $wert -match $anleitung) {
                        $fehler += "$($datei):$($k.Extent.StartLineNumber) (Anleitung): $wert"
                    }
                }
            }
            Write-Host "    T-5 geprueft: $anzahl Werte (Katalog de+en und Zeichenketten der drei Skripte)"
            ($fehler -join "`n") | Should BeNullOrEmpty
        }
    }

    Context 'T-6 Geraetejob mit gemocktem adb (in-process)' {
        $td = (Get-PSDrive TestDrive).Root
        $stubDir = Join-Path $td 'adbstub'
        New-Item -ItemType Directory -Path $stubDir -Force | Out-Null
        $stub = Join-Path $stubDir 'fake-adb.ps1'
        Set-Content -LiteralPath $stub -Value $AdbStubText -Encoding UTF8

        function Invoke-Szenario {
            param([string]$Name, [string[]]$Antworten, [string]$Sprache, [bool]$Bestand)
            Set-Content -LiteralPath (Join-Path $stubDir 'szenario.txt') -Value $Antworten -Encoding UTF8
            $ergebnisDatei = Join-Path $td "$Name.json"
            $ausgabe = @(& $workerPfad -Serial 'TEST01' -AdbPath $stub -ApkPath (Join-Path $td 'nicht-vorhanden.apk') `
                -ExpectedPackage 'com.uip.drainq.one' -ExpectedVersionName '9.9.9' -ExpectedVersionCode '999' `
                -ExpectedAdminComponent 'com.uip.drainq.one/com.uip.oneapp.bootstrap.OneDeviceAdminReceiver' `
                -ExpectedHomeActivity 'com.uip.drainq.one/com.uip.oneapp.MainActivity' `
                -KioskAction 'com.uip.drainq.one.action.PROVISION_KIOSK_ON' `
                -KioskReceiverComponent 'com.uip.drainq.one/com.uip.oneapp.bootstrap.ProvisioningReceiver' `
                -ResultFile $ergebnisDatei -Bestandsgeraet $Bestand -VersionSourceNote 'Testlauf' `
                -Sprache $Sprache -TextePfad $textePfad)
            return [pscustomobject]@{
                Ausgabe = $ausgabe
                Json    = (Get-Content -LiteralPath $ergebnisDatei -Raw | ConvertFrom-Json)
                Log     = @(Get-Content -LiteralPath ([System.IO.Path]::ChangeExtension($ergebnisDatei, '.log')))
            }
        }

        $szenarien = @(
            @{ Name = 'S1'; Antworten = @('konto=Accounts: 1')
               De = 'Kein fabrikneues Gerät (1 Benutzerkonto(en) vorhanden). Anlage NICHT zurücksetzen. Ordner logs an service@uip.team senden.'
               En = 'Not a factory-fresh device (1 user account(s) present). Do NOT reset the device. Send the logs folder to service@uip.team.' },
            @{ Name = 'S2'; Antworten = @('konto=Accounts: 0', 'eigentuemer=no owners', 'pakete=package:com.uip.drainq.one', 'version=versionName=0.1.0 versionCode=1')
               De = 'Kein fabrikneues Gerät (App bereits installiert, Version 0.1.0/1). Anlage NICHT zurücksetzen. Ordner logs an service@uip.team senden.'
               En = 'Not a factory-fresh device (app already installed, version 0.1.0/1). Do NOT reset the device. Send the logs folder to service@uip.team.' },
            @{ Name = 'S3'; Antworten = @('konto=kaputt')
               De = 'Konnte den Konten-Status nicht auslesen (dumpsys account lieferte kein auswertbares Ergebnis).' + ' ' + 'Ordner logs an service@uip.team senden.'
               En = 'Could not read the account status (dumpsys account returned no usable result). Send the logs folder to service@uip.team.' },
            @{ Name = 'S4'; Antworten = @('konto=Accounts: 0', 'eigentuemer=admin: fremd/x DeviceOwner')
               De = 'Geraet hat bereits einen ANDEREN Geraeteeigentuemer gesetzt. Nicht automatisch anfassen - bitte Rueckfrage vor jedem weiteren Schritt.' + ' ' + 'Ordner logs an service@uip.team senden.'
               En = 'The device already has a DIFFERENT device owner set. Do not touch it automatically - please check back before any further step. Send the logs folder to service@uip.team.' }
        )

        foreach ($s in $szenarien) {
            foreach ($sprache in @('de', 'en')) {
                It "T-6: $($s.Name) Fenstersprache $sprache -> ROT, Protokoll deutsch mit Hinweis, Fenster $sprache" {
                    $r = Invoke-Szenario -Name "$($s.Name)_$sprache" -Antworten $s.Antworten -Sprache $sprache -Bestand $false
                    $soll = $s.En
                    if ($sprache -eq 'de') { $soll = $s.De }
                    $r.Json.Ergebnis | Should BeExactly 'ROT'
                    $r.Json.Modus | Should BeExactly 'Standard'
                    $r.Json.Grund | Should BeExactly $s.De
                    $r.Ausgabe.Count | Should Be 1
                    $r.Ausgabe[0].GrundFenster | Should BeExactly $soll
                    ([regex]::Matches($r.Json.Grund, 'service@uip\.team')).Count | Should Be 1
                    ([regex]::Matches($r.Ausgabe[0].GrundFenster, 'service@uip\.team')).Count | Should Be 1
                    $r.Log[0] | Should Match '^\[\d{2}:\d{2}:\d{2}\] Start Werkseinrichtung fuer TEST01$'
                }
            }
        }

        It 'T-6: S5 = S1 im Bestandsgeraet-Modus (de) -> ROT ohne Hinweis' {
            $r = Invoke-Szenario -Name 'S5_de' -Antworten @('konto=Accounts: 1') -Sprache 'de' -Bestand $true
            $r.Json.Ergebnis | Should BeExactly 'ROT'
            $r.Json.Modus | Should BeExactly 'Bestandsgeraet'
            $r.Json.Grund | Should BeExactly 'Kein fabrikneues Gerät (1 Benutzerkonto(en) vorhanden). Anlage NICHT zurücksetzen.'
            $r.Ausgabe.Count | Should Be 1
            $r.Ausgabe[0].GrundFenster | Should BeExactly $r.Json.Grund
            ([regex]::Matches($r.Json.Grund, 'service@uip\.team')).Count | Should Be 0
            $r.Log[0] | Should Match '^\[\d{2}:\d{2}:\d{2}\] Start Werkseinrichtung fuer TEST01$'
        }

        It 'T-7a: JSON-Feldnamen in Reihenfolge unveraendert (Fenstersprache en)' {
            $r = Invoke-Szenario -Name 'T7_en' -Antworten @('konto=Accounts: 1') -Sprache 'en' -Bestand $false
            (@($r.Json.PSObject.Properties | ForEach-Object { $_.Name }) -join ',') | Should BeExactly 'Seriennummer,Ergebnis,Grund,Version,Modus,DauerSekunden'
        }
    }

    Context 'T-7 Protokollformat und Verdrahtung im Hauptskript (AST)' {
        $ast = (Get-TestParse -Pfad $hauptPfad).Ast

        It 'T-7b: CSV-Kopf, CSV-Zeile und Einzeilig-Umformung byte-gleich, je genau 1x' {
            $kopf = @(Get-Zeichenketten -Ast $ast | Where-Object { $_.Extent.Text -ceq "'Zeitstempel;Seriennummer;Version;Ergebnis;Modus;Dauer_Sekunden;Grund'" })
            $kopf.Count | Should Be 1
            $zeile = @(Get-Zeichenketten -Ast $ast | Where-Object { $_.Extent.Text -ceq '"$(Get-Date -Format o);$($r.Seriennummer);$($r.Version);$($r.Ergebnis);$($r.Modus);$($r.DauerSekunden);$grundEinzeilig"' })
            $zeile.Count | Should Be 1
            $einzeilig = @($ast.FindAll({ param($n) $n -is [System.Management.Automation.Language.AssignmentStatementAst] }, $true) |
                Where-Object { $_.Extent.Text -ceq '$grundEinzeilig = ($r.Grund -replace '';'', '','') -replace "`r?`n", '' ''' })
            $einzeilig.Count | Should Be 1
        }
        It 'T-7c: Invoke-WerkzeugSelfUpdate bekommt -Sprache $Sprache' {
            $aufrufe = @(Get-Befehle -Ast $ast -Name 'Invoke-WerkzeugSelfUpdate')
            $aufrufe.Count | Should Be 1
            (Get-ParameterArgument -Befehl $aufrufe[0] -Parameter 'Sprache').Extent.Text | Should BeExactly '$Sprache'
        }
        It 'T-7d: Start-Job mit 15 Argumenten, 13. $versionSourceNote, 14. $Sprache, 15. Texte-Pfad' {
            $sj = @(Get-Befehle -Ast $ast -Name 'Start-Job')
            $sj.Count | Should Be 1
            $arg = Get-ParameterArgument -Befehl $sj[0] -Parameter 'ArgumentList'
            $liste = $arg.Find({ param($n) $n -is [System.Management.Automation.Language.ArrayLiteralAst] }, $true)
            $liste.Elements.Count | Should Be 15
            $liste.Elements[12].Extent.Text | Should BeExactly '$versionSourceNote'
            $liste.Elements[13].Extent.Text | Should BeExactly '$Sprache'
            $liste.Elements[14].Extent.Text | Should BeExactly '$textePfad'
        }
        It 'T-7e: Receive-Job wird nicht verworfen; Select-FensterGrund/New-AbbruchErgebnis je 1x; Anzeige zeigt den Fenstergrund' {
            $rj = @(Get-Befehle -Ast $ast -Name 'Receive-Job')
            $rj.Count | Should Be 1
            $outNull = @($rj[0].Parent.PipelineElements | Where-Object {
                $_ -is [System.Management.Automation.Language.CommandAst] -and $_.GetCommandName() -eq 'Out-Null' })
            $outNull.Count | Should Be 0
            @(Get-Befehle -Ast $ast -Name 'Select-FensterGrund').Count | Should Be 1
            @(Get-Befehle -Ast $ast -Name 'New-AbbruchErgebnis').Count | Should Be 1

            $zuweisungen = @($ast.FindAll({ param($n) $n -is [System.Management.Automation.Language.AssignmentStatementAst] }, $true))
            $zSfg = @($zuweisungen | Where-Object { @(Get-Befehle -Ast $_.Right -Name 'Select-FensterGrund').Count -gt 0 })
            $zSfg.Count | Should Be 1
            $fensterVar = $zSfg[0].Left.VariablePath.UserPath
            $zRj = @($zuweisungen | Where-Object { @(Get-Befehle -Ast $_.Right -Name 'Receive-Job').Count -gt 0 })
            $zRj.Count | Should Be 1
            $jobVar = $zRj[0].Left.VariablePath.UserPath

            # Select-FensterGrund bekommt Job-Ausgabe UND das Ergebnisobjekt $r (Rueckfall traegt GrundFenster).
            $sfg = (Get-Befehle -Ast $ast -Name 'Select-FensterGrund')[0]
            $jaArg = Get-ParameterArgument -Befehl $sfg -Parameter 'JobAusgabe'
            $jaVars = @($jaArg.FindAll({ param($n) $n -is [System.Management.Automation.Language.VariableExpressionAst] }, $true) | ForEach-Object { $_.VariablePath.UserPath })
            ($jaVars -contains $jobVar) | Should Be $true
            ($jaVars -contains 'r') | Should Be $true

            # Die Grund-Anzeige benutzt genau diese Variable ...
            $grundAnzeige = @(Get-Befehle -Ast $ast -Name 'Get-FensterText' | Where-Object {
                (Get-ParameterArgument -Befehl $_ -Parameter 'Key').Extent.Text -ceq "'haupt.grund'" })
            $grundAnzeige.Count | Should Be 1
            $werteArg = Get-ParameterArgument -Befehl $grundAnzeige[0] -Parameter 'Werte'
            $werteVars = @($werteArg.FindAll({ param($n) $n -is [System.Management.Automation.Language.VariableExpressionAst] }, $true) | ForEach-Object { $_.VariablePath.UserPath })
            ($werteVars -contains $fensterVar) | Should Be $true

            # ... und keine Anzeige liest .Grund direkt.
            $direkt = @($ast.FindAll({ param($n) $n -is [System.Management.Automation.Language.MemberExpressionAst] }, $true) | Where-Object {
                $_.Member.Extent.Text -eq 'Grund' -and (Test-HatBefehlVorfahr -Knoten $_ -Bis $null -Befehlsnamen @('Write-Host', 'Get-FensterText', 'Read-Host', 'Write-Headline')) })
            (($direkt | ForEach-Object { "$($_.Extent.StartLineNumber): $($_.Extent.Text)" }) -join "`n") | Should BeNullOrEmpty
        }
    }

    Context 'T-8 Parser und Kodierung' {
        foreach ($pfad in @($textePfad, $hauptPfad, $workerPfad, $updatePfad, $testPfad)) {
            It "T-8: $(Split-Path -Leaf $pfad) parst fehlerfrei und beginnt mit UTF-8-BOM" {
                $p = Get-TestParse -Pfad $pfad
                (($p.Fehler | ForEach-Object { "$($_.Extent.StartLineNumber): $($_.Message)" }) -join "`n") | Should BeNullOrEmpty
                $bytes = [System.IO.File]::ReadAllBytes($pfad)
                (($bytes[0..2] | ForEach-Object { $_.ToString('X2') }) -join ' ') | Should BeExactly 'EF BB BF'
            }
        }
    }

    Context 'T-9 Selbstaktualisierung englisch, Protokoll deutsch (Portal gemockt)' {
        $td = (Get-PSDrive TestDrive).Root
        $fp = '2D:37:0C:21:F5:DF:D5:53:D2:A7:96:31:4B:70:92:5F:B3:8A:DE:EF:90:86:4C:92:0B:BB:BB:12:88:7D:35:22'

        function New-AppOrdner {
            param([string]$Name, [bool]$MitDatei)
            $ordner = Join-Path $td "app_$Name"
            New-Item -ItemType Directory -Path $ordner -Force | Out-Null
            if ($MitDatei) {
                $datei = Join-Path $ordner 'DrainQ-ONE_0.1.0_1_platform.apk'
                Set-Content -LiteralPath $datei -Value 'kein echtes apk' -Encoding ASCII
                (Get-Item -LiteralPath $datei).LastWriteTime = New-Object DateTime 2026, 9, 1, 10, 20, 0
            }
            return $ordner
        }

        It 'T-9a: Portal nicht erreichbar -> PortalUnreachable, Fenster en, ProtokollLabel altes deutsches Format' {
            Mock Get-PortalManifest { [pscustomobject]@{ Ok = $false; StatusCode = $null; Manifest = $null; Error = 'Testfehler' } }
            $ordner = New-AppOrdner -Name 'a' -MitDatei $true
            $e = Invoke-WerkzeugSelfUpdate -AppDir $ordner -ExpectedFingerprint $fp -Sprache 'en'
            $e.Status | Should BeExactly 'PortalUnreachable'
            $e.SourceLabel | Should Match 'not reachable'
            $e.ProtokollLabel | Should BeExactly 'Portal nicht erreichbar - lokaler Stand 0.1.0/1 vom 2026-09-01 10:20'
            $e.LogLines[0] | Should Match '^Querying the portal manifest: '
        }
        It 'T-9b: Kanal nicht veroeffentlicht (404) -> "not published", ProtokollLabel deutsch' {
            Mock Get-PortalManifest { [pscustomobject]@{ Ok = $true; StatusCode = 404; Manifest = $null; Error = $null } }
            $ordner = New-AppOrdner -Name 'b' -MitDatei $true
            $e = Invoke-WerkzeugSelfUpdate -AppDir $ordner -ExpectedFingerprint $fp -Sprache 'en'
            $e.Status | Should BeExactly 'PortalUnreachable'
            $e.SourceLabel | Should Match 'not published'
            $e.ProtokollLabel | Should BeExactly "Kanal 'beta' im Portal nicht veroeffentlicht - lokaler Stand 0.1.0/1 vom 2026-09-01 10:20"
        }
        It 'T-9c: Manifest = lokal -> UpToDate, ProtokollLabel byte-gleich' {
            Mock Get-PortalManifest { [pscustomobject]@{ Ok = $true; StatusCode = 200; Error = $null
                Manifest = [pscustomobject]@{ latest = [pscustomobject]@{
                    version = '0.1.0'; versionCode = 1; releasedAt = '2026-09-15'; url = 'https://portal.invalid/x.apk'; sha256 = '00' } } } }
            $ordner = New-AppOrdner -Name 'c' -MitDatei $true
            $e = Invoke-WerkzeugSelfUpdate -AppDir $ordner -ExpectedFingerprint $fp -Sprache 'en'
            $e.Status | Should BeExactly 'UpToDate'
            $e.SourceLabel | Should BeExactly "Up to date: 0.1.0/1 (channel 'beta', matched with the portal)"
            $e.ProtokollLabel | Should BeExactly "Aktuell: 0.1.0/1 (Kanal 'beta', mit Portal abgeglichen)"
        }
        It 'T-9d: Manifest neuer, sha256 falsch -> Rejected, en REJECTED, de ABGELEHNT' {
            Mock Get-PortalManifest { [pscustomobject]@{ Ok = $true; StatusCode = 200; Error = $null
                Manifest = [pscustomobject]@{ latest = [pscustomobject]@{
                    version = '0.2.0'; versionCode = 2; releasedAt = '2026-09-15'; url = 'https://portal.invalid/x.apk'; sha256 = '00' } } } }
            Mock Invoke-WebRequest {
                $ziel = $OutFile
                if (-not $ziel) { $ziel = $PSBoundParameters['OutFile'] }
                Set-Content -LiteralPath $ziel -Value 'kein echtes apk' -Encoding ASCII
            }
            $ordner = New-AppOrdner -Name 'd' -MitDatei $true
            $e = Invoke-WerkzeugSelfUpdate -AppDir $ordner -ExpectedFingerprint $fp -Sprache 'en'
            $e.Status | Should BeExactly 'Rejected'
            $e.SourceLabel | Should BeExactly 'REJECTED: checksum of the portal file 0.2.0/2 does not match - staying with 0.1.0/1'
            $e.ProtokollLabel | Should BeExactly 'ABGELEHNT: Pruefsumme der Portal-Datei 0.2.0/2 stimmt nicht - bleibe bei 0.1.0/1'
        }
        It 'T-9e: wie d ohne lokale Datei -> "(no local file)" / "(kein lokaler Stand)"' {
            Mock Get-PortalManifest { [pscustomobject]@{ Ok = $true; StatusCode = 200; Error = $null
                Manifest = [pscustomobject]@{ latest = [pscustomobject]@{
                    version = '0.2.0'; versionCode = 2; releasedAt = '2026-09-15'; url = 'https://portal.invalid/x.apk'; sha256 = '00' } } } }
            Mock Invoke-WebRequest {
                $ziel = $OutFile
                if (-not $ziel) { $ziel = $PSBoundParameters['OutFile'] }
                Set-Content -LiteralPath $ziel -Value 'kein echtes apk' -Encoding ASCII
            }
            $ordner = New-AppOrdner -Name 'e' -MitDatei $false
            $e = Invoke-WerkzeugSelfUpdate -AppDir $ordner -ExpectedFingerprint $fp -Sprache 'en'
            $e.Status | Should BeExactly 'Rejected'
            $e.SourceLabel | Should BeExactly 'REJECTED: checksum of the portal file 0.2.0/2 does not match - staying with (no local file)'
            $e.ProtokollLabel | Should BeExactly 'ABGELEHNT: Pruefsumme der Portal-Datei 0.2.0/2 stimmt nicht - bleibe bei (kein lokaler Stand)'
        }
    }

    Context 'T-10 Get-WerkzeugText' {
        It 'T-10a: Platzhalter werden gefuellt (de und en)' {
            Get-WerkzeugText -Key 'haupt.gefunden' -Sprache 'de' -Werte @(2, 'A, B') | Should BeExactly 'Gefunden: 2 Gerät(e) - A, B'
            Get-WerkzeugText -Key 'haupt.gefunden' -Sprache 'en' -Werte @(2, 'A, B') | Should BeExactly 'Found: 2 device(s) - A, B'
        }
        It 'T-10b: unbekannter Schluessel wirft' {
            { Get-WerkzeugText -Key 'haupt.gibt_es_nicht' -Sprache 'de' } | Should Throw
        }
        It 'T-10c: unbekannte Sprache wirft' {
            { Get-WerkzeugText -Key 'haupt.titel' -Sprache 'fr' } | Should Throw
        }
        It 'T-10d: -Werte als ArrayList' {
            $liste = New-Object System.Collections.ArrayList
            [void]$liste.Add('1.2.3')
            [void]$liste.Add('42')
            Get-WerkzeugText -Key 'haupt.version' -Sprache 'en' -Werte $liste | Should BeExactly 'Version: 1.2.3 (code 42)'
        }
    }

    Context 'T-11 Select-FensterGrund und New-AbbruchErgebnis' {
        It 'T-11a: Job-Ausgabe mit GrundFenster -> dieser Text' {
            $aus = @('irgendwas', [pscustomobject]@{ Seriennummer = 'X'; GrundFenster = 'Fenstertext' })
            Select-FensterGrund -JobAusgabe $aus -Grund 'Protokolltext' -Sprache 'en' | Should BeExactly 'Fenstertext'
        }
        It 'T-11b: leere Ausgabe, en -> Vorsatz mit deutschem Grund' {
            Select-FensterGrund -JobAusgabe @() -Grund 'Protokolltext' -Sprache 'en' | Should BeExactly 'Reason only available in German (see logs folder): Protokolltext'
        }
        It 'T-11c: leere Ausgabe, de -> Grund' {
            Select-FensterGrund -JobAusgabe @() -Grund 'Protokolltext' -Sprache 'de' | Should BeExactly 'Protokolltext'
        }
        It 'T-11d: New-AbbruchErgebnis Standard/en -> Grund de + Hinweis, GrundFenster en + Hinweis, je 1x service@' {
            $o = New-AbbruchErgebnis -Seriennummer 'X1' -Modus 'Standard' -Sprache 'en'
            $o.Ergebnis | Should BeExactly 'ROT'
            $o.Grund | Should BeExactly 'Der Einrichtungs-Vorgang wurde unerwartet abgebrochen (kein Ergebnis geschrieben) - Protokolldatei prüfen. Ordner logs an service@uip.team senden.'
            $o.GrundFenster | Should BeExactly 'The setup process was aborted unexpectedly (no result written) - check the log file. Send the logs folder to service@uip.team.'
            ([regex]::Matches($o.Grund, 'service@uip\.team')).Count | Should Be 1
            ([regex]::Matches($o.GrundFenster, 'service@uip\.team')).Count | Should Be 1
        }
        It 'T-11e: New-AbbruchErgebnis Bestandsgeraet -> ohne Hinweis' {
            $o = New-AbbruchErgebnis -Seriennummer 'X1' -Modus 'Bestandsgeraet' -Sprache 'de'
            $o.Grund | Should BeExactly 'Der Einrichtungs-Vorgang wurde unerwartet abgebrochen (kein Ergebnis geschrieben) - Protokolldatei prüfen.'
            ([regex]::Matches($o.Grund, 'service@uip\.team')).Count | Should Be 0
        }
        It 'T-11f: Feldnamen = die sechs JSON-Felder in Reihenfolge, dazu GrundFenster als siebtes nur im Objekt' {
            $o = New-AbbruchErgebnis -Seriennummer 'X1' -Modus 'Standard' -Sprache 'de'
            (@($o.PSObject.Properties | ForEach-Object { $_.Name }) -join ',') | Should BeExactly 'Seriennummer,Ergebnis,Grund,Version,Modus,DauerSekunden,GrundFenster'
        }
        It 'T-11g: Rueckfall-Objekt am Ende der Liste liefert seinen GrundFenster an die Anzeige' {
            $o = New-AbbruchErgebnis -Seriennummer 'X1' -Modus 'Standard' -Sprache 'en'
            Select-FensterGrund -JobAusgabe (@() + @($o)) -Grund $o.Grund -Sprache 'en' | Should BeExactly $o.GrundFenster
        }
    }

    Context 'T-12 bekannte deutsche Signaturmeldung' {
        It 'T-12a: Katalog de ist byte-gleich zur Meldung in Get-ApkSignatureFingerprint.ps1' {
            $ast = (Get-TestParse -Pfad $fingerPfad).Ast
            $throws = @($ast.FindAll({ param($n) $n -is [System.Management.Automation.Language.ThrowStatementAst] }, $true))
            $throws.Count | Should Be 1
            $text = $throws[0].Pipeline.Find({ param($n) $n -is [System.Management.Automation.Language.StringConstantExpressionAst] }, $true).Value
            $text | Should BeExactly (Get-WerkzeugText -Key 'allg.signatur_keine_datei' -Sprache 'de')
        }
        It 'T-12b: Convert-BekannteMeldung uebersetzt nur diese Meldung' {
            $de = Get-WerkzeugText -Key 'allg.signatur_keine_datei' -Sprache 'de'
            Convert-BekannteMeldung -Meldung $de -Sprache 'en' | Should BeExactly 'No signature file (META-INF/*.RSA) found in the app file.'
            Convert-BekannteMeldung -Meldung $de -Sprache 'de' | Should BeExactly $de
            Convert-BekannteMeldung -Meldung 'Access denied' -Sprache 'en' | Should BeExactly 'Access denied'
        }
        It 'T-12c: Hauptskript und Update-Skript benutzen Convert-BekannteMeldung' {
            @(Get-Befehle -Ast (Get-TestParse -Pfad $hauptPfad).Ast -Name 'Convert-BekannteMeldung').Count | Should Be 1
            (@(Get-Befehle -Ast (Get-TestParse -Pfad $updatePfad).Ast -Name 'Convert-BekannteMeldung').Count -ge 1) | Should Be $true
        }
    }
}
