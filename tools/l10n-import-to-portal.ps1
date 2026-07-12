# l10n-import-to-portal.ps1 - Spielt die ONE-Woerterliste ins DrainQ-Portal ein
# (CEO-Beschluss 2026-06-07: Partner uebersetzen kuenftig im Portal).
#
# Quelle:  LocalizationManager.kt (deTranslations + enTranslations) - die EINZIG
#          vollstaendige Quelle (~430 Keys inkl. der neuen von 2026-06-07).
#          translations_raw.txt ist veraltet (nur 198 Keys, kein Englisch).
# Ziel:    POST https://license.drainq.com/api/admin/l10n/translations/import
#
# Aufruf:  cd C:\Projekte\drainq.one
#          .\tools\l10n-import-to-portal.ps1 -ApiKey "<DrainQCloud:ApiKey>"
# Optional: -PortalUrl "https://license.drainq.com"   (Default)
#           -DryRun   (nur JSON erzeugen, kein Upload)

param(
    [Parameter(Mandatory = $true)] [string]$ApiKey,
    [string]$PortalUrl = "https://license.drainq.com",
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$lm = Join-Path $root "app\src\main\java\com\uip\oneapp\ui\localization\LocalizationManager.kt"
if (-not (Test-Path $lm)) { Write-Host "LocalizationManager.kt nicht gefunden: $lm" -ForegroundColor Red; exit 1 }

# ---- 1) Kotlin-Sprachbloecke parsen ------------------------------------------
# Die Datei besteht aus Funktionen wie: private fun deTranslations(): Map<...> = mapOf("key" to "wert", ...)
# Wir schneiden den de- und en-Block heraus und lesen alle "key" to "wert"-Paare.
$src = [IO.File]::ReadAllText($lm, [Text.Encoding]::UTF8)

function Get-LangBlock([string]$source, [string]$lang) {
    $start = $source.IndexOf("fun ${lang}Translations(")
    if ($start -lt 0) { return $null }
    $next = [regex]::Match($source.Substring($start + 10), "fun \w+Translations\(")
    if ($next.Success) { return $source.Substring($start, $next.Index + 10) }
    return $source.Substring($start)
}

function Parse-Pairs([string]$block) {
    $map = [ordered]@{}
    if (-not $block) { return $map }
    $rx = [regex]'"((?:[^"\\]|\\.)*)"\s+to\s+"((?:[^"\\]|\\.)*)"'
    foreach ($m in $rx.Matches($block)) {
        $key = $m.Groups[1].Value
        $val = $m.Groups[2].Value
        # Kotlin-Escapes aufloesen
        $val = $val -replace '\\n', "`n" -replace '\\t', "`t"
        $val = $val.Replace('\"', '"').Replace('\\', '\')
        $key = $key.Replace('\"', '"')
        if (-not $map.Contains($key)) { $map[$key] = $val }
    }
    return $map
}

$de = Parse-Pairs (Get-LangBlock $src "de")
$en = Parse-Pairs (Get-LangBlock $src "en")
Write-Host "Gelesen aus LocalizationManager.kt: $($de.Count) deutsche, $($en.Count) englische Begriffe."
if ($de.Count -lt 300) { Write-Host "WARNUNG: de-Block unerwartet klein - bitte melden." -ForegroundColor Yellow }

# ---- 2) Import-JSON bauen (Schema: L10nImportRequest) ------------------------
$keys = @()
foreach ($k in $de.Keys) {
    $keys += [pscustomobject]@{
        newKey   = $k
        scope    = "ONE"
        sourceDe = $de[$k]
        sourceEn = if ($en.Contains($k)) { $en[$k] } else { $null }
    }
}
foreach ($k in $en.Keys) {
    if (-not $de.Contains($k)) {
        $keys += [pscustomobject]@{ newKey = $k; scope = "ONE"; sourceDe = $null; sourceEn = $en[$k] }
    }
}
$body = @{ keys = $keys } | ConvertTo-Json -Depth 4 -Compress
Write-Host "Import-Paket: $($keys.Count) Begriffe, $([math]::Round($body.Length/1KB)) KB."

if ($DryRun) {
    $outDir = Join-Path $PSScriptRoot "_autotest"
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null
    $out = Join-Path $outDir "l10n_import.json"
    [IO.File]::WriteAllText($out, $body, [Text.Encoding]::UTF8)
    Write-Host "DryRun: JSON liegt unter $out - kein Upload." -ForegroundColor Yellow
    exit 0
}

# ---- 3) Upload ----------------------------------------------------------------
$uri = "$PortalUrl/api/admin/l10n/translations/import"
Write-Host "Sende an $uri ..."
try {
    $resp = Invoke-RestMethod -Method Post -Uri $uri `
        -Headers @{ "X-DrainQ-ApiKey" = $ApiKey } `
        -ContentType "application/json; charset=utf-8" `
        -Body ([Text.Encoding]::UTF8.GetBytes($body))
    Write-Host "ERFOLG: $($resp.created) neu angelegt, $($resp.updated) aktualisiert (von $($resp.total))." -ForegroundColor Green
    Write-Host "Paketgroessen: de=$($resp.deByteSize) Bytes, en=$($resp.enByteSize) Bytes."
    Write-Host ""
    Write-Host "Naechste Schritte im Portal (https://license.drainq.com):" -ForegroundColor Cyan
    Write-Host " 1. Admin / Uebersetzungen / ONE: Begriffe sind da."
    Write-Host " 2. Tab 'Sprachen und Partner': Partner anlegen, Sprache zuordnen."
    Write-Host "    DeepL uebersetzt zugeordnete Sprachen automatisch vor (alle 15 Min)."
    Write-Host " 3. Partner prueft unter /partner/translations; ab 95 Prozent wird die Sprache aktivierbar."
} catch {
    Write-Host "FEHLER: $($_.Exception.Message)" -ForegroundColor Red
    if ($_.ErrorDetails.Message) { Write-Host $_.ErrorDetails.Message }
    Write-Host "Hinweise: 401 = ApiKey falsch (DrainQCloud:ApiKey). 404 = Portal-Stand ohne L10n-API, erst deployen."
    exit 1
}
