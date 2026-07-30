# publish-one-release.ps1 - Baut (optional) und veroeffentlicht einen DrainQ.ONE-Release
# im Portal mit EINEM Befehl: Release anlegen -> APK hochladen -> veroeffentlichen -> verifizieren.
# sha256 + size rechnet der Server selbst (UploadArtifact).
#
# Voraussetzung: Portal-Endpoints akzeptieren X-DrainQ-ApiKey (siehe
#   drainq.web/PROMPT_SOFTWARE_PUBLISH_APIKEY.md). Bis das deployt ist -> 401.
# PowerShell 7+ noetig (Invoke-RestMethod -Form fuer Multipart).
#
# API-Key EINMALIG hinterlegen (persistente Benutzer-Variable, danach neue pwsh oeffnen):
#   [Environment]::SetEnvironmentVariable("DRAINQ_PUBLISH_APIKEY","<KEY>","User")
# Danach reicht (ohne -ApiKey, wird aus der Variable gelesen):
#   cd C:\Projekte\drainq.one
#   .\tools\publish-one-release.ps1 -VersionName 0.5.3 -VersionCode 503 -Notes "..."
# -ApiKey "<KEY>" uebersteuert die Variable weiterhin, falls noetig.
# Optional:
#   -Channel beta|stable  (Default beta)
#   -SkipBuild            (vorhandene app-debug.apk nehmen, nicht neu bauen)
#   -SkipPublish          (Release anlegen + APK hochladen, aber NICHT veroeffentlichen -
#                          fuer den Fall, dass das Freischalten im Admin bewusst separat
#                          und von jemand anderem gemacht wird)
#   -PortalUrl "https://license.drainq.com"

param(
    [string]$ApiKey = $env:DRAINQ_PUBLISH_APIKEY,
    [Parameter(Mandatory = $true)] [string]$VersionName,
    [Parameter(Mandatory = $true)] [int]$VersionCode,
    [string]$Channel = "beta",
    [string]$Notes = "",
    [switch]$SkipBuild,
    [switch]$SkipDocs,   # W-H5: Notausstieg für Docs-Gate (dokumentieren, nicht für Routine-Releases)
    [switch]$SkipPublish, # Release anlegen + hochladen, aber Freischalten bewusst dem Admin überlassen
    [string]$PortalUrl = "https://license.drainq.com"
)

$ErrorActionPreference = "Stop"
# Multipart-Upload braucht PS7. Laeuft das Script unter Windows PowerShell 5.1,
# startet es sich selbst unter pwsh neu (statt den Nutzer zu zwingen, die Shell zu wechseln).
if ($PSVersionTable.PSVersion.Major -lt 7) {
    $pwshExe = (Get-Command pwsh -ErrorAction SilentlyContinue).Source
    if (-not $pwshExe) {
        $fallback = "C:\Program Files\PowerShell\7\pwsh.exe"
        if (Test-Path $fallback) { $pwshExe = $fallback }
    }
    if (-not $pwshExe) { throw "PowerShell 7 nicht gefunden. Installieren: winget install --id Microsoft.PowerShell" }

    Write-Host "Wechsle nach PowerShell 7 ($pwshExe) ..." -ForegroundColor DarkGray
    $argv = @()
    foreach ($kv in $PSBoundParameters.GetEnumerator()) {
        if ($kv.Value -is [System.Management.Automation.SwitchParameter]) {
            if ($kv.Value.IsPresent) { $argv += "-$($kv.Key)" }
        } else {
            $argv += "-$($kv.Key)"; $argv += "$($kv.Value)"
        }
    }
    & $pwshExe -NoLogo -File $PSCommandPath @argv
    exit $LASTEXITCODE
}
if ($Channel -notin @("beta", "stable")) { throw "Channel muss beta oder stable sein." }
if ([string]::IsNullOrWhiteSpace($ApiKey)) {
    throw "Kein API-Key. Einmalig setzen: [Environment]::SetEnvironmentVariable('DRAINQ_PUBLISH_APIKEY','<KEY>','User') -- danach neue pwsh oeffnen. Oder -ApiKey mitgeben."
}

$root = Split-Path $PSScriptRoot -Parent
$apk  = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk"
$product  = "one"
$platform = "android-apk"

# ---- 1) Bauen (OHNE Keystore, Version ueber Env) ------------------------------
if (-not $SkipBuild) {
    Write-Host "Baue app-debug.apk ($VersionName / $VersionCode) ..." -ForegroundColor Cyan
    $env:APP_VERSION_CODE = "$VersionCode"; $env:APP_VERSION_NAME = "$VersionName"
    & (Join-Path $root "gradlew.bat") assembleDebug
    if ($LASTEXITCODE -ne 0) { throw "Build fehlgeschlagen." }
}
if (-not (Test-Path $apk)) { throw "APK nicht gefunden: $apk (ohne -SkipBuild bauen)." }
if ($SkipBuild) {
    Write-Host "ACHTUNG -SkipBuild: die vorhandene APK MUSS bereits $VersionName/$VersionCode enthalten." -ForegroundColor Yellow
    Write-Host "  Bei NEUER Versionsnummer NIE -SkipBuild verwenden - sonst meldet das Portal eine Version," -ForegroundColor Yellow
    Write-Host "  die die APK nicht hat, und das Geraet bekommt endlos 'Update verfuegbar'." -ForegroundColor Yellow
}
$niceName = "DrainQ-ONE_${VersionName}-${Channel}_${VersionCode}.apk"
Copy-Item $apk (Join-Path $root $niceName) -Force
Write-Host "APK: $niceName ($([math]::Round((Get-Item $apk).Length/1MB)) MB)"

# ── W-H5 Docs-Gate (vor Upload) ─────────────────────────────────────────────
if (-not $SkipDocs) {
    Write-Host ""
    Write-Host "=== Docs-Gate — VOR Publish ===" -ForegroundColor Cyan
    $docsT0 = [DateTime]::UtcNow

    # Gate 1: HelpCoverageTest
    Write-Host "  [1/4] HelpCoverageTest ..." -ForegroundColor DarkCyan
    $t1 = [DateTime]::UtcNow
    & (Join-Path $root "gradlew.bat") "--project-dir" $root ":app:testDebugUnitTest" `
        "--tests=com.uip.oneapp.help.HelpCoverageTest" "--rerun-tasks"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "DOCS-GATE 1 FAIL: HelpCoverageTest rot. Release abgebrochen." -ForegroundColor Red
        Write-Host "Hilfe: neuer Screen/Dialog braucht Eintrag in scenes.json + help_*.json + Keys." -ForegroundColor Yellow
        exit 1
    }
    Write-Host "  [1/4] PASS ($([int]([DateTime]::UtcNow - $t1).TotalSeconds)s)" -ForegroundColor Green

    # Gate 2: Golden-Diff
    Write-Host "  [2/4] verify.ps1 (Golden-Diff DE+EN) ..." -ForegroundColor DarkCyan
    $t2 = [DateTime]::UtcNow
    & (Join-Path $root "tools\manual\verify.ps1")
    if ($LASTEXITCODE -ne 0) {
        Write-Host "DOCS-GATE 2 FAIL: Screenshot-Diff zeigt Abweichungen. Release abgebrochen." -ForegroundColor Red
        Write-Host "Hilfe: .\tools\manual\verify.ps1 -Update ausführen und Goldens committen." -ForegroundColor Yellow
        exit 1
    }
    Write-Host "  [2/4] PASS ($([int]([DateTime]::UtcNow - $t2).TotalSeconds)s)" -ForegroundColor Green

    # Gate 3: render.ps1 (Portal-Sprachen oder de,en-Fallback)
    Write-Host "  [3/4] render.ps1 (alle Portal-Sprachen) ..." -ForegroundColor DarkCyan
    $t3 = [DateTime]::UtcNow
    $portalLangs = @("de", "en")
    try {
        $locales = Invoke-RestMethod -Method Get -Uri "$PortalUrl/api/software/one/locales.json" -TimeoutSec 8
        if ($locales.languages -and $locales.languages.Count -gt 0) {
            $portalLangs = $locales.languages | Where-Object { $_ } | Sort-Object -Unique
        }
    } catch {
        Write-Host "  [3/4] Portal offline — nur de,en (WARN: Portal-Drift nicht geprüft)" -ForegroundColor Yellow
    }
    $langStr = $portalLangs -join ","
    Write-Host "         Sprachen: $langStr" -ForegroundColor DarkGray
    & (Join-Path $root "tools\manual\render.ps1") -Langs $langStr
    if ($LASTEXITCODE -ne 0) {
        Write-Host "DOCS-GATE 3 FAIL: render.ps1 fehlgeschlagen. Release abgebrochen." -ForegroundColor Red
        exit 1
    }
    Write-Host "  [3/4] PASS ($([int]([DateTime]::UtcNow - $t3).TotalSeconds)s)" -ForegroundColor Green

    # Gate 4: generate.js (PDF je Sprache mit Versionsnummer)
    Write-Host "  [4/4] generate.js — PDF je Sprache ..." -ForegroundColor DarkCyan
    $t4 = [DateTime]::UtcNow
    $generateScript = Join-Path $root "tools\manual\generate.js"
    if (Test-Path $generateScript) {
        foreach ($lang in $portalLangs) {
            node $generateScript --lang $lang --version $VersionName 2>&1 | ForEach-Object { Write-Host "    $_" }
            if ($LASTEXITCODE -ne 0) {
                Write-Host "DOCS-GATE 4 FAIL: generate.js für '$lang' fehlgeschlagen." -ForegroundColor Red
                exit 1
            }
        }
    } else {
        Write-Host "  [4/4] generate.js nicht gefunden — übersprungen (WARN)" -ForegroundColor Yellow
    }
    Write-Host "  [4/4] PASS ($([int]([DateTime]::UtcNow - $t4).TotalSeconds)s)" -ForegroundColor Green

    $docsTotal = [int]([DateTime]::UtcNow - $docsT0).TotalSeconds
    Write-Host ""
    Write-Host "  Docs-Gate gesamt: ${docsTotal}s (Ziel: <600s)" -ForegroundColor Cyan
    if ($docsTotal -gt 600) {
        Write-Host "  WARN: Docs-Gate > 10 min — Ziel verfehlt, bitte optimieren." -ForegroundColor Yellow
    }
} else {
    Write-Host "WARN: -SkipDocs aktiv — Docs-Gate übersprungen. Nur für Notfälle!" -ForegroundColor Yellow
}

$headers = @{ "X-DrainQ-ApiKey" = $ApiKey }
try {
    # ---- 2) Release anlegen ---------------------------------------------------
    $createBody = @{ product = $product; channel = $Channel; version = $VersionName
                     versionCode = $VersionCode; releaseNotes = $Notes } | ConvertTo-Json -Compress
    Write-Host "Lege Release an ..." -ForegroundColor Cyan
    $rel = Invoke-RestMethod -Method Post -Uri "$PortalUrl/api/software/releases" `
        -Headers $headers -ContentType "application/json; charset=utf-8" `
        -Body ([Text.Encoding]::UTF8.GetBytes($createBody))
    $id = $rel.Id; if (-not $id) { $id = $rel.id }
    Write-Host "  Release-Id: $id"

    # ---- 3) APK hochladen (Multipart; Server rechnet sha256+size) -------------
    Write-Host "Lade APK hoch (kann je nach Upstream einige Minuten dauern) ..." -ForegroundColor Cyan
    $art = Invoke-RestMethod -Method Post -Uri "$PortalUrl/api/software/releases/$id/artifacts" `
        -Headers $headers -Form @{ platform = $platform; file = Get-Item $apk } -TimeoutSec 0
    Write-Host "  sha256=$($art.Sha256)  size=$($art.SizeBytes)"

    if ($SkipPublish) {
        Write-Host ""
        Write-Host "-SkipPublish aktiv: Release angelegt und APK hochgeladen, aber NICHT veroeffentlicht." -ForegroundColor Yellow
        Write-Host "  Release-Id $id ($product/$Channel, $VersionName/$VersionCode) liegt bereit -" -ForegroundColor Yellow
        Write-Host "  das Freischalten im Admin ist ein separater, bewusster Schritt." -ForegroundColor Yellow
    } else {
        # ---- 4) Veroeffentlichen --------------------------------------------------
        Write-Host "Veroeffentliche ..." -ForegroundColor Cyan
        Invoke-RestMethod -Method Post -Uri "$PortalUrl/api/software/releases/$id/publish" -Headers $headers | Out-Null

        # ---- 5) Verifizieren (oeffentliches Client-Manifest) ----------------------
        $manifest = Invoke-RestMethod -Method Get -Uri "$PortalUrl/api/software/$product/releases.$Channel.json"
        if ("$($manifest.latest.versionCode)" -eq "$VersionCode") {
            Write-Host "ERFOLG: $product/$Channel jetzt $($manifest.latest.version) / $($manifest.latest.versionCode) live." -ForegroundColor Green
        } else {
            Write-Host "WARNUNG: Manifest zeigt versionCode $($manifest.latest.versionCode), erwartet $VersionCode." -ForegroundColor Yellow
        }
    }
} catch {
    Write-Host "FEHLER: $($_.Exception.Message)" -ForegroundColor Red
    if ($_.ErrorDetails.Message) { Write-Host $_.ErrorDetails.Message }
    Write-Host "Hinweise: 401 = ApiKey falsch ODER Endpoints noch nicht auf ApiKey deployt (siehe drainq.web/PROMPT_SOFTWARE_PUBLISH_APIKEY.md). 409 = versionCode existiert schon." -ForegroundColor Yellow
    exit 1
}
