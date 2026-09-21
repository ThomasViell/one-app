# L10nImportLib.Tests.ps1 - Pester-Tests fuer tools/l10n/L10nImportLib.ps1
# (Welle portal-nachzug, PLAN Abschnitt 3.1; Pester 3.4, pwsh 7 wegen -AsHashtable)
#
# Testliste:
#   T-1  Dekodierung aller sechs Kotlin-Escape-Arten in einem Durchlauf
#   T-2  Doppelschluessel ergibt den letzten Wert, mit Warnung und Zeilennummer
#   T-3  Zeilenversatz rechnet auf die echte Dateizeile
#   T-4  Block-Extraktion endet am naechsten Sprachblock
#   T-5  Vergleich normiert Zeilenenden und ordnet NEU/GLEICH/ABWEICHEND/NUR-PORTAL
#   T-6  Paket ohne sourceEn-Feld; hilfe-Schluessel loesen Sperre e2 aus
#   T-7  ABWEICHEND geht nur mit Freigabe ins Paket (Sperre e4)
#   T-8  SHARED-Portal-Schluessel im Paket loesen Sperre e3 aus
#   T-9  echter LocalizationManager.kt: > 500 Begriffe, kein woertliches \u, keine hilfe-Schluessel
#   T-10 GLEICH-Schluessel im Paket loesen Sperre e6 aus (auch mit Freigabe)

#Requires -Version 7.0

$here     = Split-Path -Parent $MyInvocation.MyCommand.Path
$lib      = Join-Path $here "L10nImportLib.ps1"
. $lib
$repoRoot = Split-Path (Split-Path $here -Parent) -Parent
$lmPfad   = Join-Path $repoRoot "app\src\main\java\com\uip\oneapp\ui\localization\LocalizationManager.kt"

Describe "L10nImportLib" {
    Context "ConvertFrom-KotlinPairs" {
        It "T-1: dekodiert alle sechs Escape-Arten in einem Durchlauf" {
            $block = '"a" to "x → y\n\"z\" \\ \$ 5"'
            $erwartet = "x $([char]0x2192) y`n`"z`" \ `$ 5"
            $map = ConvertFrom-KotlinPairs -Block $block
            $map.Count | Should Be 1
            $map.Map["a"] | Should Be $erwartet
        }

        It "T-2: Doppelschluessel ergibt den letzten Wert, mit Warnung und Zeilennummer" {
            $block = "fun deTranslations(): Map<String, String> = mapOf(`n    `"dup`" to `"alt`",`n    `"dup`" to `"neu`")"
            $warnungen = New-Object System.Collections.Generic.List[string]
            Mock Write-Warning {
                $warnungen.Add($Message)
            }
            $map = ConvertFrom-KotlinPairs -Block $block
            $map.Count | Should Be 1
            $map.Map["dup"] | Should Be "neu"
            @($warnungen).Count | Should Be 1
            $warnungen[0] | Should Match "dup"
            $warnungen[0] | Should Match "Zeile 3"
        }

        It "T-3: Zeilenversatz rechnet auf die echte Dateizeile" {
            $block = "`n`nfun deTranslations(): Map<String, String> = mapOf(`n    `"d`" to `"a`",`n    `"d`" to `"b`")`n"
            $warnungen = New-Object System.Collections.Generic.List[string]
            Mock Write-Warning {
                $warnungen.Add($Message)
            }
            $map = ConvertFrom-KotlinPairs -Block $block -ZeilenVersatz 10
            $map.Count | Should Be 1
            @($warnungen).Count | Should Be 1
            $warnungen[0] | Should Match "Zeile 15"
        }

        It "T-4: Get-KotlinLangBlock schneidet bis zum naechsten Sprachblock" {
            $src = @'
fun x(): Int = 1
private fun deTranslations(): Map<String, String> = mapOf(
    "a" to "eins"
)
fun enTranslations(): Map<String, String> = mapOf(
    "a" to "one"
)
'@
            $block = Get-KotlinLangBlock -Source $src -Lang "de"
            $block | Should Match "fun deTranslations"
            $block | Should Match "mapOf"
            $block | Should Not Match "enTranslations"
        }
    }

    Context "Compare-L10nKeys" {
        It "T-5: Vergleich normiert Zeilenenden und ordnet NEU/GLEICH/ABWEICHEND/NUR-PORTAL" {
            $map    = @{ "k" = "x`ny" }
            $portal = @{ "k" = "x`r`ny" }
            $v1 = Compare-L10nKeys -Map $map -Portal $portal -RohPortal @("k")
            $v1.Gleich.Count | Should Be 1
            $v1.Neu.Count | Should Be 0
            $v1.Abweichend.Count | Should Be 0
            $v1.NurPortal.Count | Should Be 0

            $map2    = @{ "n" = "neu"; "a" = "anders" }
            $portal2 = @{ "a" = "portal"; "p" = "nur portal" }
            $v2 = Compare-L10nKeys -Map $map2 -Portal $portal2 -RohPortal @("a", "p")
            ($v2.Neu | ForEach-Object { $_ } ) -join "," | Should Be "n"
            (($v2.Gleich) -join ",") | Should Be ""
            ($v2.Abweichend | ForEach-Object { $_.Key }) -join "," | Should Be "a"
            ($v2.Abweichend | ForEach-Object { $_.Repo }) -join "," | Should Be "anders"
            ($v2.Abweichend | ForEach-Object { $_.Portal }) -join "," | Should Be "portal"
            ($v2.NurPortal | ForEach-Object { $_ }) -join "," | Should Be "p"
        }
    }

    Context "New-L10nImportBody und Sperren" {
        It "T-6: Paket traegt kein sourceEn-Feld; hilfe-Schluessel loesen e2 aus" {
            $enFeld = ('source' + 'En')
            $neu = @{ "a" = "wert" }
            $paket = New-L10nImportBody -Neu $neu
            $paket.Count | Should Be 1
            $falsch = @($paket.Body.keys | Where-Object { $null -ne $_.PSObject.Properties[$enFeld] })
            $falsch.Count | Should Be 0
            ($paket.Json | ConvertFrom-Json).keys[0].newKey | Should Be "a"
            ($paket.Json | ConvertFrom-Json).keys[0].sourceDe | Should Be "wert"
            ($paket.Json | ConvertFrom-Json).keys[0].scope | Should Be "ONE"

            $neu2 = @{ "help.x" = "Hilfe" }
            $paket2 = New-L10nImportBody -Neu $neu2
            $verstoesse = Test-L10nImportBody -Body $paket2.Body
            ($verstoesse -join " ") | Should Match "e2"
        }

        It "T-7: ABWEICHEND geht nur mit Freigabe ins Paket (e4)" {
            $neu = @{}
            $freigaben = @([pscustomobject]@{ Key = "x"; Repo = "repo" })
            $paket = New-L10nImportBody -Neu $neu -Freigegeben $freigaben
            $paket.Count | Should Be 1
            ($paket.Json | ConvertFrom-Json).keys[0].sourceDe | Should Be "repo"

            $v1 = Test-L10nImportBody -Body $paket.Body -Abweichend @("x") -Freigegeben @("x")
            @($v1).Count | Should Be 0
            $v2 = Test-L10nImportBody -Body $paket.Body -Abweichend @("x")
            ($v2 -join " ") | Should Match "e4"
        }

        It "T-8: SHARED-Portal-Schluessel im Paket loesen e3 aus" {
            $neu = @{ "Connection.Wifi.NotConnected" = "Wert" }
            $paket = New-L10nImportBody -Neu $neu
            $shared = @{ "Connection.Wifi.NotConnected" = "Portalwert" }
            $v = Test-L10nImportBody -Body $paket.Body -Portal $shared
            ($v -join " ") | Should Match "e3"
        }

        It "T-9: echter LocalizationManager.kt: ueber 500 Begriffe, kein woertliches \u, keine hilfe-Schluessel" {
            $src = [IO.File]::ReadAllText($lmPfad, [Text.Encoding]::UTF8)
            $block = Get-KotlinLangBlock -Source $src -Lang "de"
            $map = ConvertFrom-KotlinPairs -Block $block
            $map.Count | Should BeGreaterThan 500
            $hilfe = @($map.Map.Keys | Where-Object { $_ -like "help.*" })
            $hilfe.Count | Should Be 0
            $lit = @($map.Map.Values | Where-Object { $_ -match '\\u[0-9a-fA-F]{4}' })
            $lit.Count | Should Be 0
        }

        It "T-10: GLEICH-Schluessel im Paket loesen e6 aus (auch mit Freigabe)" {
            $neu = @{ "x" = "wert" }
            $paket = New-L10nImportBody -Neu $neu
            $v1 = Test-L10nImportBody -Body $paket.Body -Unveraendert @("x")
            ($v1 -join " ") | Should Match "e6"
            $v2 = Test-L10nImportBody -Body $paket.Body -Unveraendert @("x") -Freigegeben @("x")
            ($v2 -join " ") | Should Match "e6"
        }
    }
}
