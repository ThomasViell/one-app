<#
.SYNOPSIS
    Z-1 (PLAN.md 2e): echte Portalmessung gegen license.drainq.com, wiederholbar.

.BESCHREIBUNG
    Ruft dieselben vier Aufrufe wie PLAN.md Abschnitt 1.1 auf: die Sprachliste und
    de/en je Scope one, one,shared, shared, dazu die ETag-/If-Modified-Since-Gegenprobe.
    Zaehlt die Schluessel je Antwort und schreibt Kopfzeilen und Zahlen in eine Ablage
    unter belege/. Kein Ersatz fuer den echten Live-Test (L10nPortalLiveTest, E-11) —
    dies ist die menschenlesbare Gegenprobe dazu, mit Bezugszeile (Regel M-B).

.PARAMETER OutDir
    Zielverzeichnis fuer die Ablage. Default: belege/portalmessung_<datum>.

.BEISPIEL
    .\tools\l10n\portal-messung.ps1
#>

[CmdletBinding()]
param(
    [string]$OutDir = "belege\portalmessung_$(Get-Date -Format 'yyyy-MM-dd')"
)

$ErrorActionPreference = "Stop"
$baseUrl = "https://license.drainq.com"

$branch = (git rev-parse --abbrev-ref HEAD).Trim()
$commit = (git rev-parse --short HEAD).Trim()
$bezug = "Bezug: $branch $commit"
Write-Output $bezug

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$bezug | Out-File -FilePath (Join-Path $OutDir "BEZUG.txt") -Encoding utf8

function Get-KeyCount([string]$json) {
    try {
        $obj = $json | ConvertFrom-Json -AsHashtable
        if ($obj -is [hashtable]) { return $obj.Count }
        return -1
    } catch {
        return -1
    }
}

$summary = New-Object System.Collections.Generic.List[string]
$summary.Add($bezug)

# 1) Sprachliste
try {
    $locResp = Invoke-WebRequest -Uri "$baseUrl/api/locales?app=one" -UseBasicParsing
    $locResp.Content | Out-File -FilePath (Join-Path $OutDir "loc.json") -Encoding utf8
    $locales = $locResp.Content | ConvertFrom-Json
    $summary.Add("GET /api/locales?app=one -> HTTP $($locResp.StatusCode), $($locales.Count) Eintraege: $($locales.code -join ', ')")
} catch {
    $summary.Add("GET /api/locales?app=one -> FEHLER: $($_.Exception.Message)")
}

# 2) de/en je Scope
$langs = @("de", "en")
$scopes = @("one", "one,shared", "shared")
foreach ($lang in $langs) {
    foreach ($scope in $scopes) {
        $tag = "$lang" + "_" + ($scope -replace ",", "_")
        try {
            $resp = Invoke-WebRequest -Uri "$baseUrl/api/translations/$lang.json?scope=$scope" -UseBasicParsing
            $resp.Content | Out-File -FilePath (Join-Path $OutDir "t_$tag.json") -Encoding utf8
            $resp.Headers | Out-String | Out-File -FilePath (Join-Path $OutDir "h_$tag.txt") -Encoding utf8
            $n = Get-KeyCount $resp.Content
            $summary.Add("$lang.json scope=$scope -> HTTP $($resp.StatusCode), $n Schluessel")
        } catch {
            $status = $_.Exception.Response.StatusCode.value__
            $summary.Add("$lang.json scope=$scope -> HTTP $status (Fehler: $($_.Exception.Message))")
        }
    }
}

# 3) pl.json (erwartet 404 — Portal fuehrt fuer die ONE nur de/en)
try {
    $resp = Invoke-WebRequest -Uri "$baseUrl/api/translations/pl.json?scope=one,shared" -UseBasicParsing
    $summary.Add("pl.json scope=one,shared -> HTTP $($resp.StatusCode) (unerwartet: 404 war Sollwert)")
} catch {
    $status = $_.Exception.Response.StatusCode.value__
    $summary.Add("pl.json scope=one,shared -> HTTP $status")
}

# 4) ETag / If-Modified-Since Gegenprobe
try {
    $first = Invoke-WebRequest -Uri "$baseUrl/api/translations/de.json?scope=one" -UseBasicParsing
    $etag = $first.Headers["ETag"]
    $lastMod = $first.Headers["Last-Modified"]
    $summary.Add("de.json scope=one Kopfzeilen: ETag=$etag Last-Modified=$lastMod")

    try {
        $r304 = Invoke-WebRequest -Uri "$baseUrl/api/translations/de.json?scope=one" -Headers @{ "If-None-Match" = $etag } -UseBasicParsing -SkipHttpErrorCheck
        $summary.Add("If-None-Match: $etag -> HTTP $($r304.StatusCode) (Sollwert 304)")
    } catch {
        $status = $_.Exception.Response.StatusCode.value__
        $summary.Add("If-None-Match: $etag -> HTTP $status (Sollwert 304)")
    }

    try {
        $r200 = Invoke-WebRequest -Uri "$baseUrl/api/translations/de.json?scope=one" -Headers @{ "If-Modified-Since" = $lastMod } -UseBasicParsing -SkipHttpErrorCheck
        $summary.Add("If-Modified-Since: $lastMod -> HTTP $($r200.StatusCode) (gemessener Sollwert 200 — Portal-Vergleich benutzt Bruchteilsekunden, H-1)")
    } catch {
        $status = $_.Exception.Response.StatusCode.value__
        $summary.Add("If-Modified-Since: $lastMod -> HTTP $status")
    }
} catch {
    $summary.Add("ETag-Gegenprobe -> FEHLER: $($_.Exception.Message)")
}

$summaryPath = Join-Path $OutDir "ZUSAMMENFASSUNG.txt"
$summary | Out-File -FilePath $summaryPath -Encoding utf8
$summary | ForEach-Object { Write-Output $_ }
Write-Output "Ablage: $OutDir"
