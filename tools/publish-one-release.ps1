# publish-one-release.ps1 - Baut (optional, RELEASE-Bautyp plattformsigniert), legt einen
# DrainQ.ONE-Release im Portal an und laedt die APK hoch: Release anlegen -> APK hochladen ->
# FERTIG. Freigeben und Veroeffentlichen geschehen danach im Portal (Admin -> Releases), nie
# im Skript (CEO-Entscheid 04.09.2026). Ein 4-Augen-Prinzip (Zweit-Admin) ist NICHT gebaut,
# siehe docs/UPDATE_PROCESS_CONCEPT.md; Freigeben ist der einzige rein menschliche Akt (kein
# API-Endpunkt), Veroeffentlichen geht auch per API-Schluessel. sha256 + size rechnet der
# Server selbst (UploadArtifact).
#
# Voraussetzung: Portal-Endpoints akzeptieren X-DrainQ-ApiKey (siehe
#   drainq.portal, ApiKeyOrAdminAuthAttribute.cs, Header X-DrainQ-ApiKey).
# PowerShell 7+ noetig (Invoke-RestMethod -Form fuer Multipart).
#
# PLATTFORMSCHLUESSEL PFLICHT (fail-closed, CEO-Entscheid 05.09.2026 / Welle portalweg):
#   $env:ONE_PLATFORM_KEYSTORE = 'C:\...\bominwellalias.keystore'
#   $env:ONE_PLATFORM_PASS     = '<Passwort>'   (von Hand, nirgends gespeichert)
# Ohne beide Variablen bricht das Skript vor Bau und Portal-Kontakt ab - ein Release-Bau
# ohne Plattformschluessel wird mit dem Debug-Schluessel signiert und ist auf dem Geraet
# (sharedUserId=android.uid.system, ADR-0005) nicht installierbar.
#
# API-Key EINMALIG hinterlegen (persistente Benutzer-Variable, danach neue pwsh oeffnen):
#   [Environment]::SetEnvironmentVariable("DRAINQ_PUBLISH_APIKEY","<KEY>","User")
# Danach reicht (ohne -ApiKey, wird aus der Variable gelesen):
#   cd C:\Projekte\drainq.one
#   .\tools\publish-one-release.ps1 -VersionName 0.9.2 -VersionCode 902 -Notes "..."
# -ApiKey "<KEY>" uebersteuert die Variable weiterhin, falls noetig.
# Optional:
#   -Channel beta|stable  (Default beta)
#   -SkipBuild            (vorhandene app-release.apk nehmen, nicht neu bauen;
#                          Zertifikat wird gegen den Plattform-Fingerabdruck geprueft)
#   -PortalUrl "https://license.drainq.com"

param(
    [string]$ApiKey = $env:DRAINQ_PUBLISH_APIKEY,
    [Parameter(Mandatory = $true)] [string]$VersionName,
    [Parameter(Mandatory = $true)] [int]$VersionCode,
    [string]$Channel = "beta",
    [string]$Notes = "",
    [switch]$SkipBuild,
    [switch]$SkipDocs,   # W-H5: Notausstieg für Docs-Gate (dokumentieren, nicht für Routine-Releases)
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
$apk  = Join-Path $root "app\build\outputs\apk\release\app-release.apk"
$product  = "one"
$platform = "android-apk"

# ---- 0) Plattformschluessel PFLICHT (fail-closed, vor Bau UND vor Portal-Kontakt) --------
# Ein Release-Bau ohne ONE_PLATFORM_* wird mit dem Debug-Schluessel signiert (Gradle-
# Rueckfall, app/build.gradle.kts) und ist auf dem Geraet nicht installierbar - lieber hier
# abbrechen als einen falschen Bau hochzuladen.
if ([string]::IsNullOrWhiteSpace($env:ONE_PLATFORM_KEYSTORE) -or [string]::IsNullOrWhiteSpace($env:ONE_PLATFORM_PASS)) {
    throw "ONE_PLATFORM_KEYSTORE/ONE_PLATFORM_PASS fehlen. Release-Bau ohne Plattformschluessel wuerde mit dem Debug-Schluessel signiert und ist auf dem Geraet nicht installierbar (sharedUserId=android.uid.system, ADR-0005). Beide Variablen setzen, Passwort von Hand."
}
if (-not (Test-Path $env:ONE_PLATFORM_KEYSTORE)) {
    throw "ONE_PLATFORM_KEYSTORE zeigt auf keine Datei: $env:ONE_PLATFORM_KEYSTORE"
}

# ---- 1) Bauen (RELEASE, plattformsigniert; Version ueber Env) -----------------------------
# Env-Variablen VOR dem if setzen: das Docs-Gate ruft weiter unten ebenfalls gradlew.bat auf
# (auch bei -SkipBuild), und der Gradle-Guard (app/build.gradle.kts) bricht hart ab, wenn
# ONE_PLATFORM_KEYSTORE/PASS gesetzt sind, APP_VERSION_CODE/NAME aber fehlen.
$env:APP_VERSION_CODE = "$VersionCode"; $env:APP_VERSION_NAME = "$VersionName"
if (-not $SkipBuild) {
    Write-Host "Baue app-release.apk ($VersionName / $VersionCode), plattformsigniert ..." -ForegroundColor Cyan
    & (Join-Path $root "gradlew.bat") assembleRelease --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "Build fehlgeschlagen." }
}
if (-not (Test-Path $apk)) { throw "APK nicht gefunden: $apk (ohne -SkipBuild bauen)." }
if ($SkipBuild) {
    Write-Host "ACHTUNG -SkipBuild: die vorhandene APK MUSS bereits $VersionName/$VersionCode enthalten." -ForegroundColor Yellow
    Write-Host "  Bei NEUER Versionsnummer NIE -SkipBuild verwenden - sonst meldet das Portal eine Version," -ForegroundColor Yellow
    Write-Host "  die die APK nicht hat, und das Geraet bekommt endlos 'Update verfuegbar'." -ForegroundColor Yellow
    # Zertifikatsprobe: die vorhandene APK muss mit dem Plattformschluessel signiert sein.
    . (Join-Path $root "tools\werkseinrichtung\Get-ApkSignatureFingerprint.ps1")
    $expectedFingerprint = '2D:37:0C:21:F5:DF:D5:53:D2:A7:96:31:4B:70:92:5F:B3:8A:DE:EF:90:86:4C:92:0B:BB:BB:12:88:7D:35:22'  # Sollwert = tools/werkseinrichtung/Werkseinrichtung.ps1:54
    $actualFingerprint = Get-ApkSignatureFingerprint -ApkPath $apk
    if ($actualFingerprint -ne $expectedFingerprint) {
        throw "SkipBuild-Zertifikatsprobe FEHLGESCHLAGEN: $apk ist nicht mit dem Plattformschluessel signiert (gefunden $actualFingerprint, erwartet $expectedFingerprint). Kein Upload."
    }
    Write-Host "  Zertifikatsprobe OK: Plattform-Fingerabdruck stimmt." -ForegroundColor Green
}
$niceName = "DrainQ-ONE_${VersionName}-${Channel}_${VersionCode}.apk"
Copy-Item $apk (Join-Path $root $niceName) -Force
Write-Host "APK: $niceName ($([math]::Round((Get-Item $apk).Length/1MB)) MB)"

# ------ W-H5 Docs-Gate (vor Upload) ---------------------------------------------------------------------------------------------------------------------------------------
if (-not $SkipDocs) {
    Write-Host ""
    Write-Host "=== Docs-Gate - VOR Upload ===" -ForegroundColor Cyan
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
        Write-Host "  [3/4] Portal offline - nur de,en (WARN: Portal-Drift nicht geprüft)" -ForegroundColor Yellow
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
    Write-Host "  [4/4] generate.js - PDF je Sprache ..." -ForegroundColor DarkCyan
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
        Write-Host "  [4/4] generate.js nicht gefunden - übersprungen (WARN)" -ForegroundColor Yellow
    }
    Write-Host "  [4/4] PASS ($([int]([DateTime]::UtcNow - $t4).TotalSeconds)s)" -ForegroundColor Green

    $docsTotal = [int]([DateTime]::UtcNow - $docsT0).TotalSeconds
    Write-Host ""
    Write-Host "  Docs-Gate gesamt: ${docsTotal}s (Ziel: <600s)" -ForegroundColor Cyan
    if ($docsTotal -gt 600) {
        Write-Host "  WARN: Docs-Gate > 10 min - Ziel verfehlt, bitte optimieren." -ForegroundColor Yellow
    }
} else {
    Write-Host "WARN: -SkipDocs aktiv - Docs-Gate übersprungen. Nur für Notfälle!" -ForegroundColor Yellow
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

    # ---- Ende des Skriptanteils: Freigabe + Veroeffentlichung sind ein menschlicher Akt im Portal (CEO-Entscheid 04.09.2026) ----
    Write-Host ""
    Write-Host "FERTIG: $product/$Channel $VersionName ($VersionCode) liegt im Portal als Entwurf, Artefakt $niceName haengt daran (Release-Id $id)." -ForegroundColor Green
    Write-Host "Jetzt im Portal unter 'Releases' freigeben und veroeffentlichen: $PortalUrl/admin/releases" -ForegroundColor Green
} catch {
    Write-Host "FEHLER: $($_.Exception.Message)" -ForegroundColor Red
    if ($_.ErrorDetails.Message) { Write-Host $_.ErrorDetails.Message }
    Write-Host "Hinweise: 401 = ApiKey falsch (siehe drainq.portal: ApiKeyOrAdminAuthAttribute.cs, Schluessel DrainQCloud:ApiKey). 409 = versionCode existiert schon." -ForegroundColor Yellow
    exit 1
}

# ---- 4) Auslieferungspaket: kompletter werkseinrichtung-Ordner als Zip (fuer Rechner, die mal ----
# ---- offline sind - siehe AUTOUPDATE_WERKZEUG_PROMPT.md). Fehler hier stoppen NICHT den Release, ----
# ---- der ist zu diesem Zeitpunkt bereits hochgeladen; Freigabe und Veroeffentlichung geschehen im Portal. ----
Write-Host ""
Write-Host "Baue Auslieferungspaket (werkseinrichtung-Ordner, ohne logs) ..." -ForegroundColor Cyan
try {
    $weOrdner = Join-Path $root "tools\werkseinrichtung"
    if (-not (Test-Path $weOrdner)) {
        Write-Host "  WARN: '$weOrdner' nicht gefunden - Auslieferungspaket uebersprungen." -ForegroundColor Yellow
    } else {
        $distDir = Join-Path $weOrdner "dist"
        New-Item -ItemType Directory -Path $distDir -Force -ErrorAction SilentlyContinue | Out-Null

        $bundledApk = @(Get-ChildItem -Path (Join-Path $weOrdner "app") -Filter "DrainQ-ONE_*_platform.apk" -File -ErrorAction SilentlyContinue) | Select-Object -First 1
        $pkgVersionName = $VersionName
        $pkgVersionCode = $VersionCode
        if ($bundledApk -and $bundledApk.Name -match '^DrainQ-ONE_(?<name>[\d.]+)_(?<code>\d+)_platform\.apk$') {
            $pkgVersionName = $Matches['name']; $pkgVersionCode = $Matches['code']
            if ($pkgVersionName -ne $VersionName -or "$pkgVersionCode" -ne "$VersionCode") {
                Write-Host "  WARN: werkseinrichtung/app enthaelt $pkgVersionName/$pkgVersionCode, hochgeladen wird gerade $VersionName/$VersionCode - Paket spiegelt den App-Ordner-Stand, nicht zwingend diesen Release." -ForegroundColor Yellow
            }
        } else {
            Write-Host "  WARN: keine App-Datei in werkseinrichtung/app gefunden - Paket wird ohne App erstellt." -ForegroundColor Yellow
        }

        $stagingRoot = Join-Path $env:TEMP "werkseinrichtung-paket-$([Guid]::NewGuid().ToString('N'))"
        New-Item -ItemType Directory -Path $stagingRoot -Force | Out-Null
        try {
            Copy-Item -Path $weOrdner -Destination $stagingRoot -Recurse -Force
            $stagedRoot = Join-Path $stagingRoot "werkseinrichtung"
            foreach ($skip in @("logs", "dist", "app\_update_staging", "app\_previous")) {
                $p = Join-Path $stagedRoot $skip
                if (Test-Path $p) { Remove-Item $p -Recurse -Force }
            }

            $zipName = "Werkseinrichtung_${pkgVersionName}_${pkgVersionCode}.zip"
            $zipPath = Join-Path $distDir $zipName
            if (Test-Path $zipPath) { Remove-Item $zipPath -Force }
            Compress-Archive -Path $stagedRoot -DestinationPath $zipPath -CompressionLevel Optimal
            Write-Host "  Auslieferungspaket: $zipPath ($([math]::Round((Get-Item $zipPath).Length/1MB)) MB)" -ForegroundColor Green
        } finally {
            Remove-Item $stagingRoot -Recurse -Force -ErrorAction SilentlyContinue
        }
    }
} catch {
    Write-Host "  WARN: Auslieferungspaket konnte nicht erstellt werden: $($_.Exception.Message)" -ForegroundColor Yellow
}

# Erfolg: Release liegt als Entwurf im Portal, APK ist hochgeladen. Vertrag: Rueckgabewert 0.
exit 0
