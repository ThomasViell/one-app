#Requires -Version 5.1
<#
.SYNOPSIS
    Wechselt den DrainQ.ONE Update-Prozess von Variante B (Hetzner-Mirror)
    auf Variante A (direkter GitHub-Download aus public Repo).

.DESCRIPTION
    Voraussetzung: Repo ThomasViell/one-app wurde auf public umgeschaltet.
    Skript ruft Claude headless auf, der alle Code- und Doku-Aenderungen
    in einem Commit auf master macht.
#>

$ErrorActionPreference = "Stop"
Set-Location C:\Projekte\drainq.one

$prompt = @"
Du arbeitest am DrainQ.ONE Repo unter C:\Projekte\drainq.one auf Branch master.

Aufgabe: Update-Prozess von Variante B (Hetzner-Mirror updates.drainq.de/one/) auf
Variante A (direkter GitHub-Download aus public Repo ThomasViell/one-app) umstellen.

Hintergrund: Der Hetzner-Server hat einen Container-Stack, kein Bare-Metal-Nginx,
und der Suite-Mirror existiert dort nicht. Das Repo wurde stattdessen auf public
umgeschaltet. Damit ist der Mirror obsolet, GitHub Releases werden direkt
abgefragt.

Pflicht-Vorbereitung (in dieser Reihenfolge lesen):
  1. docs/adr/0001-update-process-android.md (zu aendern)
  2. docs/UPDATE_PROCESS_CONCEPT.md (zu aendern)
  3. docs/UPDATE_OPS_GUIDE.md (zu aendern)
  4. app/build.gradle.kts (BuildConfig-Felder UPDATE_*)
  5. app/src/main/java/com/uip/oneapp/update/HttpUpdateService.kt
  6. scripts/generate-release-manifest.py
  7. ops/hetzner-update-proxy/ (kompletter Ordner — wird geloescht)

Pflicht-Skills (Read auf SKILL.md):
  - drainq-kritis-compliance (Public-Repo-Konsequenzen pruefen)

CODE-AENDERUNGEN:

1. app/build.gradle.kts:
   - BuildConfig-Feld UPDATE_PROXY_URL aendern auf:
     'https://github.com/ThomasViell/one-app/releases/latest/download/'
   - BuildConfig-Feld UPDATE_MODE bleibt 'proxy' (Bedeutung umdefiniert: einfacher Direct-Download statt Eigen-Server)

2. scripts/generate-release-manifest.py:
   - APK-URL im Manifest aendern von:
     'https://updates.drainq.de/one/drainq-one-VER.apk'
     auf:
     'https://github.com/ThomasViell/one-app/releases/download/vVER/drainq-one-VER.apk'
   - SHA256-Datei-URL analog

3. app/src/main/java/com/uip/oneapp/update/HttpUpdateService.kt:
   - Pruefen ob OkHttp followRedirects aktiviert ist (Default: ja, GitHub redirected /latest/download/ auf konkrete Release-Asset-URL).
   - Pruefen ob Manifest-URL-Konstruktion `<UPDATE_PROXY_URL>releases.<channel>.json` weiterhin funktioniert. Falls noetig: anpassen.
   - Bei Bedarf: User-Agent-Header setzen ('DrainQ.ONE/VERSION') damit GitHub-API-Aufrufe nicht raten-gelimitet werden.

4. Loeschen (komplett):
   - ops/hetzner-update-proxy/mirror-releases-one.sh
   - ops/hetzner-update-proxy/drainq-one-mirror.service
   - ops/hetzner-update-proxy/drainq-one-mirror.timer
   - ops/hetzner-update-proxy/nginx-snippet-one.conf
   - ops/hetzner-update-proxy/DEPLOYMENT.md
   - Wenn ops/hetzner-update-proxy/ danach leer: Ordner ebenfalls loeschen.

5. ADR 0001 aktualisieren:
   - Status auf 'Superseded by ADR 0002' setzen ODER inhaltlich aktualisieren.
   - Neuer Abschnitt 'Aenderung 2026-05-12: Wechsel auf Variante A':
     * Begruendung: Hetzner-Container-Stack, kein Bare-Metal-Nginx, Suite-Mirror nicht existent, Aufwand fuer Mirror-Setup unverhaeltnismaessig.
     * Konsequenz: Repo public, kein PAT, kein Mirror, kein DNS-Aufwand.
     * KRITIS: APK-Signatur + SHA256-Pruefung bleiben Pflicht, sind ausreichend gegen Tampering.
   - MARKER_HOSTING umsetzen auf: GITHUB_PUBLIC (statt SUBPATH)

6. docs/UPDATE_PROCESS_CONCEPT.md:
   - Variante-A-Abschnitt als gewaehlt markieren, Variante B mit Hinweis 'verworfen am 2026-05-12, siehe ADR'.

7. docs/UPDATE_OPS_GUIDE.md:
   - Abschnitt 'Hetzner-Mirror' komplett raus.
   - Neuer Abschnitt 'GitHub Release Direct': Tag-Push -> GitHub Actions -> Release public sichtbar -> Tablets ziehen direkt.
   - Rollback: alte Releases bleiben auf GitHub erhalten, App kann ueber Settings auf vorherige Version downgraden (falls vorgesehen) oder via ADB manuell sideloaden.

8. docs/UPDATE_USER_GUIDE.md:
   - keine Aenderung am User-Flow (Update-Check im Settings, Banner, Install), nur Quelle ist transparent.

9. HANDOVER.md:
   - Update-Prozess-Sektion: Variante A statt B, Mirror-Abschnitt raus.

10. CHANGELOG.md:
    - Neuer Eintrag unter v0.4.0-Section:
      * 'refactor(update): Wechsel von Variante B (Hetzner-Mirror) auf Variante A (direkter GitHub-Download); Repo public'

11. README.md:
    - Update-Prozess-Snippet auf Variante A anpassen.

12. docs/kritis/update-process.md:
    - Neuer Abschnitt 'Public-Repo-Konsequenzen': Code oeffentlich sichtbar; Auswirkungen auf BWELL-Protokoll-Wissen pruefen (das kann ggf. spaeter privatisiert werden falls noetig). APK-Signatur und SHA256 bleiben aktiv.
    - DSGVO: GitHub-Download-Logs werden von Microsoft/GitHub gefuehrt, in AVV ergaenzen.
    - Threat-Model T1-T4 ueberpruefen, ggf. ergaenzen.

13. docs/UPDATE_PROCESS_PHASENPLAN.md:
    - Phase 5 (Hetzner-Mirror) als 'obsolet, ersetzt durch Variante A' markieren.

PFLICHT-VORGEHEN:
  - Auf Branch master arbeiten (kein neuer Branch — Erst-Release ist noch nicht draussen).
  - Alle Aenderungen in EINEM Commit zusammenfassen.
  - Commit-Message: 'refactor(update): Variante B (Hetzner-Mirror) auf Variante A (public GitHub) umstellen' + Bullet-Liste.
  - ./gradlew assembleDebug muss gruen sein.
  - ./gradlew testDebugUnitTest muss gruen sein. UpdateServiceTest und UpdateE2ETest sind agnostic gegen Base-URL und sollten unveraendert laufen.
  - KEINE Tokens, KEINE Passwords in Logs, Commits, Tests.

PFLICHT-OUTPUT am Ende: SWITCH_TO_VARIANT_A.md im Repo-Root mit:
  - Liste aller geaenderten und geloeschten Dateien.
  - Diff-Summary pro Datei (1-2 Zeilen).
  - Compile- und Test-Ergebnisse als Zitat von ./gradlew Output.
  - Commit-Hash auf master.
  - Naechste Schritte: 'Tag v0.4.0 pushen, dann ist Erst-Release live.'
  - Hinweis dass GitHub-Secret DRAINQ_RELEASE_PAT nicht mehr noetig ist (kann manuell geloescht werden, blockiert aber nicht).

WICHTIG: KEIN git push automatisch — User pusht manuell nach Review.
"@

Write-Host "=== Switch zu Variante A (public GitHub) ===" -ForegroundColor Cyan
Write-Host "Start: $(Get-Date -Format 'HH:mm:ss')"
Write-Host ""

& claude -p $prompt --model sonnet --dangerously-skip-permissions

if ($LASTEXITCODE -ne 0) {
    Write-Host "ABBRUCH: Exit-Code $LASTEXITCODE" -ForegroundColor Red
    exit 1
}

if (-not (Test-Path "SWITCH_TO_VARIANT_A.md")) {
    Write-Host "WARN: SWITCH_TO_VARIANT_A.md wurde nicht erzeugt." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Fertig: $(Get-Date -Format 'HH:mm:ss')" -ForegroundColor Green
Write-Host ""
Write-Host "Naechste Schritte:" -ForegroundColor Yellow
Write-Host "  1. SWITCH_TO_VARIANT_A.md lesen"
Write-Host "  2. git log --oneline -5 (letzten Commit pruefen)"
Write-Host "  3. git push origin master"
Write-Host "  4. git tag v0.4.0; git push origin v0.4.0"
Write-Host "  5. GitHub Actions beobachten"
