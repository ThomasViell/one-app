#Requires -Version 5.1
<#
.SYNOPSIS
    DrainQ.ONE - Update-Prozess Autorun (Phasen 2-7).

.DESCRIPTION
    Pattern: autorun-phasenplan (siehe memory/autorun_phasenplan_pattern.md).
    Jede Phase ruft Claude headless mit eigenem Context auf.
    Uebergabe zwischen Phasen ausschliesslich via RESULT_PHASE_N.md.
    Bricht ab bei Exit-Code != 0 oder fehlendem RESULT_PHASE_N.md.

    Voraussetzung: RESULT_PHASE_1.md liegt im Repo-Root (ADR + Marker).

.PARAMETER StartPhase
    Phase, bei der gestartet werden soll (default 2).
    Nuetzlich bei Wiederaufnahme nach Abbruch.

.PARAMETER EndPhase
    Phase, bei der gestoppt werden soll (default 7).

.PARAMETER SkipPreflight
    Ueberspringt Pre-Flight-Checks (claude CLI, git sauber, master-Branch, gradle assembleDebug).
    NICHT empfohlen.

.PARAMETER Force
    Bricht NICHT ab wenn git dirty ist. Sollte nur in Notfall genutzt werden.

.EXAMPLE
    .\update_autorun.ps1 *>&1 | Tee-Object -FilePath update_autorun.log

.EXAMPLE
    .\update_autorun.ps1 -StartPhase 4    # Wiederaufnahme ab Phase 4
#>
[CmdletBinding()]
param(
    [ValidateRange(2,7)][int]$StartPhase = 2,
    [ValidateRange(2,7)][int]$EndPhase = 7,
    [switch]$SkipPreflight,
    [switch]$Force
)

$ErrorActionPreference = "Stop"
$repo = "C:\Projekte\drainq.one"
Set-Location $repo

function Write-Banner([string]$msg, [string]$color = "Cyan") {
    Write-Host ""
    Write-Host ("=" * 72) -ForegroundColor $color
    Write-Host $msg -ForegroundColor $color
    Write-Host ("=" * 72) -ForegroundColor $color
}

function Fail([string]$msg) {
    Write-Host ""
    Write-Host "ABBRUCH: $msg" -ForegroundColor Red
    exit 1
}

Write-Banner "DrainQ.ONE - Update-Prozess Autorun"
Write-Host "Repo:        $repo"
Write-Host "Phasen:      $StartPhase .. $EndPhase"
Write-Host "Start:       $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Write-Host ""

# === Pre-Flight-Checks ===================================================
if (-not $SkipPreflight) {
    Write-Banner "Pre-Flight-Checks" "Yellow"

    # 1) claude CLI vorhanden
    $claudeCmd = Get-Command claude -ErrorAction SilentlyContinue
    if (-not $claudeCmd) {
        Fail "claude CLI nicht gefunden. Install: npm install -g @anthropic-ai/claude-code"
    }
    Write-Host "  [OK] claude CLI gefunden: $($claudeCmd.Source)"

    # 2) Pflicht-Dateien
    $required = @(
        "docs/UPDATE_PROCESS_PHASENPLAN.md",
        "docs/UPDATE_PROCESS_CONCEPT.md",
        "docs/adr/0001-update-process-android.md",
        "RESULT_PHASE_1.md"
    )
    foreach ($f in $required) {
        if (-not (Test-Path $f)) { Fail "Pflicht-Datei fehlt: $f" }
        Write-Host "  [OK] $f"
    }

    # 3) git tracked-modified pruefen (Untracked sind OK)
    $gitDirty = git status --porcelain 2>&1 | Where-Object { $_ -notmatch '^\?\? ' }
    if ($gitDirty -and -not $Force) {
        Write-Host $gitDirty
        Fail "Tracked Files modifiziert (Untracked ist OK). Commit/stash oder -Force."
    }
    if ($gitDirty) {
        Write-Host "  [WARN] Tracked-modified Files vorhanden (Force aktiv)"
    } else {
        Write-Host "  [OK] Keine tracked-modified Files"
    }

    # 4) Branch-Anzeige (nur Info, kein Abbruch)
    $branch = (git branch --show-current).Trim()
    if ($branch -eq "master") {
        Write-Host "  [OK] Branch: $branch"
    } else {
        Write-Host "  [WARN] Branch: $branch (Phasen erstellen feature/update-phase-N aus master)"
    }

    # 5) JAVA_HOME
    if (-not $env:JAVA_HOME) {
        $env:JAVA_HOME = "C:\Android\jdk17"
    }
    if (-not (Test-Path $env:JAVA_HOME)) {
        Write-Host "  [WARN] JAVA_HOME=$env:JAVA_HOME nicht vorhanden - Phase-Builds koennten scheitern"
    } else {
        Write-Host "  [OK] JAVA_HOME: $env:JAVA_HOME"
    }

    # 6) Marker aus RESULT_PHASE_1.md anzeigen
    Write-Host ""
    Write-Host "  Marker aus RESULT_PHASE_1.md:"
    Get-Content RESULT_PHASE_1.md | Select-String "^MARKER_" | ForEach-Object {
        Write-Host "    $_"
    }
} else {
    Write-Host "  Pre-Flight-Checks uebersprungen (-SkipPreflight)" -ForegroundColor Yellow
}

# === Phasen-Definition ===================================================
$allPhases = @(
    @{ Num = 2; Model = "sonnet"; Hint = "think harder"; Desc = "Android Update-Modul - UpdateService, Installer, BuildConfig, FileProvider, Unit-Tests" }
    @{ Num = 3; Model = "sonnet"; Hint = "think";        Desc = "Settings-UI plus Update-Dialog plus WorkManager Periodic-Check" }
    @{ Num = 4; Model = "sonnet"; Hint = "think";        Desc = "GitHub Actions Release-Workflow - signiertes APK plus Manifest-Generator" }
    @{ Num = 5; Model = "sonnet"; Hint = "think";        Desc = "Hetzner-Proxy-Erweiterung - Mirror-Skript plus Systemd plus Nginx-Snippet" }
    @{ Num = 6; Model = "sonnet"; Hint = "think harder"; Desc = "Sicherheits-Haertung plus Integrationstests plus KRITIS-Check-Doku" }
    @{ Num = 7; Model = "haiku";  Hint = "";             Desc = "Lokalisation 35 Sprachen plus User-Guide plus Ops-Guide plus HANDOVER plus CHANGELOG" }
)

function Build-Prompt {
    param([int]$Num, [int]$Prev, [string]$Desc, [string]$Hint)

    $lines = New-Object System.Collections.Generic.List[string]
    [void]$lines.Add("Du arbeitest am DrainQ.ONE Repo unter C:\Projekte\drainq.one.")
    [void]$lines.Add("")
    [void]$lines.Add("Phase ${Num}: $Desc")
    [void]$lines.Add("")
    [void]$lines.Add("Pflicht-Vorbereitung (in dieser Reihenfolge lesen):")
    [void]$lines.Add("  1. RESULT_PHASE_${Prev}.md - aktueller Stand, Marker und Entscheidungen vorheriger Phasen.")
    [void]$lines.Add("  2. docs/UPDATE_PROCESS_PHASENPLAN.md - genaue Liefer-Anforderungen Deiner Phase.")
    [void]$lines.Add("  3. docs/adr/0001-update-process-android.md - Architecture Decision Record.")
    [void]$lines.Add("  4. docs/UPDATE_PROCESS_CONCEPT.md - Gesamtkontext und Manifest-Schema.")
    [void]$lines.Add("  5. HANDOVER.md - Projekt-Kontext, Hardware-Setup, Code-Pfade.")
    [void]$lines.Add("")
    [void]$lines.Add("Pflicht-Skills (Read auf SKILL.md vor Code-Aenderung):")
    [void]$lines.Add("  - drainq-kritis-compliance (bei Netzwerk, Auth, Logging, Permissions, Manifest)")
    [void]$lines.Add("")
    [void]$lines.Add("Pflicht-Vorgehen:")
    [void]$lines.Add("  - Branch feature/update-phase-${Num} aus master erstellen.")
    [void]$lines.Add("  - Code schreiben, ./gradlew assembleDebug muss gruen sein.")
    [void]$lines.Add("  - Keine hardcodierten Strings - LocalizationManager-Keys mit DE-Defaults.")
    [void]$lines.Add("  - Keine hardcodierten Farben - DrainQ Design System verwenden.")
    [void]$lines.Add("  - KEINE Tokens oder Passwords in Logs, Commits, Tests, Kommentaren.")
    [void]$lines.Add("  - Commit-Message: 'feat(update-phase-${Num}): Kurzbeschreibung' plus Bullet-Liste.")
    [void]$lines.Add("  - Pre-Existing-Phase-8-OSD-Branch nicht beruehren.")
    [void]$lines.Add("")
    [void]$lines.Add("Pflicht-Output am Ende der Phase: RESULT_PHASE_${Num}.md im Repo-Root mit:")
    [void]$lines.Add("  - Liste aller neuen und geaenderten Dateien mit Pfad.")
    [void]$lines.Add("  - Diff-Summary pro Datei (1-2 Zeilen was geaendert wurde und warum).")
    [void]$lines.Add("  - Compile- und Test-Ergebnisse als Zitat von ./gradlew Output.")
    [void]$lines.Add("  - Branch-Name plus Commit-Hashes.")
    [void]$lines.Add("  - Bekannte Issues und TODOs fuer Folge-Phasen.")
    [void]$lines.Add("  - Fortgepflanzte Marker aus RESULT_PHASE_1.md falls relevant.")
    [void]$lines.Add("")

    # Phase-spezifische Anforderungen und Marker-Branching
    if ($Num -eq 2) {
        [void]$lines.Add("Phase-2 spezifische Lieferung:")
        [void]$lines.Add("  - app/src/main/java/com/uip/oneapp/update/UpdateService.kt (Interface).")
        [void]$lines.Add("  - app/src/main/java/com/uip/oneapp/update/HttpUpdateService.kt - OkHttp-Impl gegen Proxy aus RESULT_PHASE_1.")
        [void]$lines.Add("  - app/src/main/java/com/uip/oneapp/update/UpdateModels.kt - ReleaseManifest, ReleaseInfo, UpdateCheckResult Sealed Class.")
        [void]$lines.Add("  - app/src/main/java/com/uip/oneapp/update/UpdateInstaller.kt - PackageInstaller-Session plus FileProvider.")
        [void]$lines.Add("  - app/src/main/java/com/uip/oneapp/update/UpdateConfig.kt - BuildConfig-Read plus SharedPrefs-Override.")
        [void]$lines.Add("  - AndroidManifest.xml: Permission REQUEST_INSTALL_PACKAGES, FileProvider-Authority com.uip.drainq.one.fileprovider.")
        [void]$lines.Add("  - app/src/main/res/xml/file_paths.xml mit cache-path name=updates.")
        [void]$lines.Add("  - DI-Registrierung in di/AppModule.kt.")
        [void]$lines.Add("  - app/build.gradle.kts BuildConfig-Fields: UPDATE_MODE=proxy, UPDATE_PROXY_URL=https://updates.drainq.de/one/.")
        [void]$lines.Add("  - Unit-Tests: SHA256-Mismatch, Netzfehler, fehlendes Manifest, versionCode-Vergleich.")
    }
    elseif ($Num -eq 3) {
        [void]$lines.Add("MARKER-Auswertung aus RESULT_PHASE_1.md:")
        [void]$lines.Add("  - MARKER_CHANNEL_UI: HIDDEN -> Channel-Switch hinter 7-Tap-Easter-Egg auf Versionsnummer in Settings.")
        [void]$lines.Add("  - MARKER_MANDATORY: NO -> mandatory=true im Manifest zeigt Warning-Banner, KEIN App-Block, Spaeter-Button immer da.")
        [void]$lines.Add("  - MARKER_AUTOCHECK: DAILY_WIFI -> WorkManager PeriodicWorkRequest 24h, NetworkType.UNMETERED.")
        [void]$lines.Add("")
        [void]$lines.Add("Phase-3 spezifische Lieferung:")
        [void]$lines.Add("  - app/src/main/java/com/uip/oneapp/ui/screens/settings/UpdateSection.kt.")
        [void]$lines.Add("  - app/src/main/java/com/uip/oneapp/ui/components/UpdateDialog.kt.")
        [void]$lines.Add("  - app/src/main/java/com/uip/oneapp/ui/components/UpdateProgressDialog.kt.")
        [void]$lines.Add("  - app/src/main/java/com/uip/oneapp/update/UpdateWorker.kt - WorkManager.")
        [void]$lines.Add("  - Einbindung in SettingsScreen.kt.")
        [void]$lines.Add("  - Localization-Keys in app/src/main/assets/i18n/de.json - alle update_-Keys.")
    }
    elseif ($Num -eq 4) {
        [void]$lines.Add("MARKER-Auswertung aus RESULT_PHASE_1.md:")
        [void]$lines.Add("  - MARKER_VERSIONCODE: FROM_TAG -> Workflow extrahiert MAJOR.MINOR.PATCH aus Tag.")
        [void]$lines.Add("  - versionCode = MAJOR*10000 + MINOR*100 + PATCH.")
        [void]$lines.Add("")
        [void]$lines.Add("Phase-4 spezifische Lieferung:")
        [void]$lines.Add("  - .github/workflows/release-apk.yml mit Trigger push tags v.")
        [void]$lines.Add("  - Keystore-Decode aus DRAINQ_ONE_KEYSTORE_BASE64 in Laufzeitfile, signiertes assembleRelease.")
        [void]$lines.Add("  - scripts/generate-release-manifest.py - liest CHANGELOG.md-Block, berechnet sha256, schreibt releases.stable.json.")
        [void]$lines.Add("  - APK-Umbenennung zu drainq-one-VERSION.apk.")
        [void]$lines.Add("  - softprops/action-gh-release@v2 mit Assets: APK plus releases.stable.json plus .sha256.")
        [void]$lines.Add("  - app/build.gradle.kts: signingConfigs.release liest aus ENV (KEYSTORE_PATH, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD).")
        [void]$lines.Add("  - signingConfig in release-buildType auf signingConfigs.release wechseln, mit if-Fallback auf debug fuer lokale Dev-Builds.")
        [void]$lines.Add("  - KEINE Keystore-Datei und KEINE Passwords ins Repo - alles via GitHub Secrets und ENV.")
    }
    elseif ($Num -eq 5) {
        [void]$lines.Add("MARKER-Auswertung aus RESULT_PHASE_1.md:")
        [void]$lines.Add("  - MARKER_HOSTING: SUBPATH -> Nginx-Snippet als location /one/ in bestehenden Server-Block updates.drainq.de.")
        [void]$lines.Add("")
        [void]$lines.Add("Phase-5 spezifische Lieferung im Repo unter ops/hetzner-update-proxy/:")
        [void]$lines.Add("  - mirror-releases-one.sh - Variante des Suite-Skripts mit Repo=ThomasViell/one-app, Dest=/var/www/drainq-updates/one.")
        [void]$lines.Add("  - drainq-one-mirror.service - Systemd-Service-Datei.")
        [void]$lines.Add("  - drainq-one-mirror.timer - OnBootSec=4min, OnUnitActiveSec=5min - versetzt zu Suite.")
        [void]$lines.Add("  - nginx-snippet-one.conf - location /one/ Block zum Einfuegen in bestehende Konfig.")
        [void]$lines.Add("  - DEPLOYMENT.md - exakte SSH-Befehlsfolge fuer Erst-Deployment, inkl. Test-Smoke via curl.")
        [void]$lines.Add("  - KEINE PATs oder Tokens in Files. PAT-Datei /etc/drainq/github-pat-one wird vom Operator angelegt.")
    }
    elseif ($Num -eq 6) {
        [void]$lines.Add("Phase-6 spezifische Lieferung:")
        [void]$lines.Add("  - app/src/main/java/com/uip/oneapp/data/local/entity/UpdateEventEntity.kt - Audit-Log Entity.")
        [void]$lines.Add("  - DAO plus Repository-Erweiterung, Migration des Room-Schemas Version+1.")
        [void]$lines.Add("  - Integrationstest in app/src/androidTest/.../update/: lokaler MockWebServer, voller Flow Manifest -> APK-Download -> SHA256 -> Install-Intent.")
        [void]$lines.Add("  - Failure-Tests: SHA256-Mismatch (abort), 404 (skip), Verbindungsabbruch, niedrigerer versionCode (skip).")
        [void]$lines.Add("  - docs/kritis/update-process.md - KRITIS-Check (Audit-Log, Transport-Security, Permissions, DSGVO-Auflagen).")
        [void]$lines.Add("  - Konsultation drainq-kritis-compliance Skill ist Pflicht, KRITIS-Check-Block in RESULT_PHASE_6 zitieren.")
    }
    elseif ($Num -eq 7) {
        [void]$lines.Add("Phase-7 spezifische Lieferung:")
        [void]$lines.Add("  - Alle update_-Keys in 35 Sprachdateien unter app/src/main/assets/i18n/. EN, DE Native, andere Sprachen via Fallback oder maschinell, markiert als TODO.")
        [void]$lines.Add("  - docs/UPDATE_USER_GUIDE.md - Endkunden-Anleitung.")
        [void]$lines.Add("  - docs/UPDATE_OPS_GUIDE.md - Ops-Anleitung (Tag setzen, Workflow ueberwachen, Hetzner-Mirror pruefen, Rollback-Pfad).")
        [void]$lines.Add("  - HANDOVER.md - neuer Abschnitt Update-Prozess mit Erst-Release-Schritten und Backlog.")
        [void]$lines.Add("  - CHANGELOG.md - v0.4.0-Eintrag vorbereiten mit Phasen 2-6 als Bullet-Liste.")
        [void]$lines.Add("  - README-Snippet zum Update-Prozess.")
    }

    if ($Hint -ne "") {
        [void]$lines.Add("")
        [void]$lines.Add("Effort-Hint: $Hint")
    }

    return ($lines -join "`n")
}

# === Phasen-Loop =========================================================
$phasesToRun = $allPhases | Where-Object { $_.Num -ge $StartPhase -and $_.Num -le $EndPhase }
if ($phasesToRun.Count -eq 0) {
    Fail "Keine Phasen im Bereich $StartPhase..$EndPhase."
}

foreach ($p in $phasesToRun) {
    $num = [int]$p.Num
    $prev = $num - 1
    $desc = [string]$p.Desc
    $model = [string]$p.Model
    $hint = [string]$p.Hint

    Write-Banner "Phase $num - $desc"
    Write-Host "Modell:      $model"
    Write-Host "Effort:      $hint"
    Write-Host "Start:       $(Get-Date -Format 'HH:mm:ss')"

    $prompt = Build-Prompt -Num $num -Prev $prev -Desc $desc -Hint $hint

    # Headless Claude mit godmode (--dangerously-skip-permissions).
    & claude -p $prompt --model $model --dangerously-skip-permissions

    if ($LASTEXITCODE -ne 0) {
        Fail "Phase $num exit-code $LASTEXITCODE. Wiederaufnahme mit: .\update_autorun.ps1 -StartPhase $num"
    }

    $resultFile = "RESULT_PHASE_$num.md"
    if (-not (Test-Path $resultFile)) {
        Fail "$resultFile wurde von Phase $num nicht erzeugt."
    }

    Write-Host ""
    Write-Host "Phase $num abgeschlossen. Ergebnis: $resultFile" -ForegroundColor Green
    Write-Host "Ende:        $(Get-Date -Format 'HH:mm:ss')"
}

# === Zusammenfassung =====================================================
Write-Banner "ALLE PHASEN ABGESCHLOSSEN" "Green"
Write-Host "Ende:        $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Write-Host ""
Write-Host "Erzeugte Phasen-Berichte:"
Get-ChildItem -Path . -Filter "RESULT_PHASE_*.md" | Sort-Object Name | ForEach-Object {
    Write-Host "  - $($_.FullName)"
}

Write-Host ""
Write-Host "Manuelle Folge-Schritte:" -ForegroundColor Yellow
Write-Host "  1. Phasen-Branches reviewen und in master mergen (Reihenfolge in RESULT_PHASE_7.md)."
Write-Host "  2. GitHub Secrets im Repo ThomasViell/one-app setzen:"
Write-Host "       DRAINQ_ONE_KEYSTORE_BASE64, _PASSWORD, _KEY_ALIAS, _KEY_PASSWORD, DRAINQ_RELEASE_PAT"
Write-Host "  3. Hetzner-Deployment: Befehle aus ops/hetzner-update-proxy/DEPLOYMENT.md (entstanden in Phase 5)."
Write-Host "  4. Tag setzen: git tag v0.4.0 && git push --tags"
Write-Host "  5. Smoke-Test: SM-X610 deinstallieren, frisch installieren, Update-Check ausloesen."
