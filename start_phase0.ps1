# Phase 0 Starter - manuelle Phase vor dem Autorun
# Ruft Claude Code mit dem Phase-0-Auftrag auf.
# GodMode aktiv, Sonnet, think harder.
#
# Aufruf:
#   .\start_phase0.ps1

$ErrorActionPreference = "Stop"
$RepoOne = "C:\Projekte\drainq.one-localization"
Set-Location $RepoOne

Write-Host "================================================================" -ForegroundColor Cyan
Write-Host " Phase 0 - Inventur, Key-Extraktion DE+EN, ADR" -ForegroundColor Cyan
Write-Host " Modell: sonnet  Effort: think harder" -ForegroundColor Cyan
Write-Host " Start : $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host ""

$lines = New-Object System.Collections.Generic.List[string]
[void]$lines.Add("GodMode aktiv. Konsultiere ZUERST den Skill 'godmode' und den Skill 'drainq-kritis-compliance'.")
[void]$lines.Add("")
[void]$lines.Add("Lies C:\Projekte\drainq.one-localization\PHASENPLAN_L10N.md und fuehre Phase 0 (Inventur, Key-Extraktion DE+EN, ADR) komplett aus.")
[void]$lines.Add("")
[void]$lines.Add("Lies auch C:\Projekte\drainq.one-localization\docs\concepts\L10N_PORTAL_KONZEPT.md (Konzept v1.1).")
[void]$lines.Add("")
[void]$lines.Add("Aktives Repo: C:\Projekte\drainq.one-localization")
[void]$lines.Add("Branch: feature/l10n-phase-0-inventur")
[void]$lines.Add("")
[void]$lines.Add("Pflicht:")
[void]$lines.Add("- Branch feature/l10n-phase-0-inventur aus dem Default-Branch anlegen, alle Aenderungen darin committen (Conventional Commits).")
[void]$lines.Add("- Skill 'drainq-kritis-compliance' anwenden.")
[void]$lines.Add("- Keine hardcodierten Strings/Farben/Secrets.")
[void]$lines.Add("- Tempdatei-Pattern fuer Shell-Befehle > 600 Zeichen (siehe godmode-Skill).")
[void]$lines.Add("")
[void]$lines.Add("Konkrete Aufgaben:")
[void]$lines.Add("1. app/src/main/java/com/uip/oneapp/ui/localization/LocalizationManager.kt parsen.")
[void]$lines.Add("2. Nur DE- und EN-Map extrahieren als phase0/keys_de_en.json im Format { snake_key: { de, en } }.")
[void]$lines.Add("3. Mengengeruest dokumentieren.")
[void]$lines.Add("4. Hardcoded-String-Scan in app/src/main/java/com/uip/oneapp/ui/screens/** und /components/**. Liste mit Datei+Zeile als phase0/hardcoded_audit_preview.md.")
[void]$lines.Add("5. Abgleich mit C:\Projekte\Drainq\Drainq_Suite_repo\src\DrainQ.Core\Resources\Localization\de-DE.json. Heuristik: gleicher oder fast-gleicher DE-Wert -> Shared-Kandidat.")
[void]$lines.Add("6. ADR schreiben: docs/adr/0010-l10n-portal-as-sot.md mit den 4 Kern-Entscheidungen aus dem Konzept v1.1 (UPPER_SNAKE, Bundle DE+EN+Lazy, DeepL+Partner-Review, eigener Portal-Reiter).")
[void]$lines.Add("7. RESULT_PHASE_0.md schreiben mit Pflicht-Markern:")
[void]$lines.Add("   PHASE0-KEYS: <Anzahl>")
[void]$lines.Add("   PHASE0-SHARED-CANDIDATES: <Anzahl>")
[void]$lines.Add("   PHASE0-HARDCODED: <Anzahl>")
[void]$lines.Add("")
[void]$lines.Add("Definition of Done:")
[void]$lines.Add("- phase0/keys_de_en.json mit n >= 250 Eintraegen")
[void]$lines.Add("- phase0/hardcoded_audit_preview.md mit allen Fundstellen")
[void]$lines.Add("- docs/adr/0010-l10n-portal-as-sot.md mit Entscheidungstabelle")
[void]$lines.Add("- RESULT_PHASE_0.md mit Mengengeruest + Markern")
[void]$lines.Add("- Branch gepusht, Commit-Hashes im RESULT")
[void]$lines.Add("")
[void]$lines.Add("think harder")

$prompt = $lines -join "`n"

$tmp = Join-Path $env:TEMP "drainq-l10n-phase-0-$([guid]::NewGuid()).txt"
$prompt | Set-Content -Path $tmp -Encoding UTF8

try {
    Get-Content -Raw -Path $tmp | & claude -p --model sonnet --dangerously-skip-permissions
}
finally {
    Remove-Item -Force $tmp -ErrorAction SilentlyContinue
}

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "Phase 0 mit Fehler beendet (exit $LASTEXITCODE)." -ForegroundColor Red
    exit 1
}

$result = Join-Path $RepoOne "RESULT_PHASE_0.md"
if (-not (Test-Path $result)) {
    Write-Host ""
    Write-Host "WARNUNG: RESULT_PHASE_0.md wurde nicht erzeugt." -ForegroundColor Yellow
    exit 1
}

Write-Host ""
Write-Host "================================================================" -ForegroundColor Green
Write-Host " Phase 0 abgeschlossen - $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" -ForegroundColor Green
Write-Host "================================================================" -ForegroundColor Green
Write-Host ""
Write-Host "Naechster Schritt: RESULT_PHASE_0.md pruefen, dann autorun_l10n.ps1 starten." -ForegroundColor Cyan

