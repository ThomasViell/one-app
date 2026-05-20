# Phase 1 — Validate Key Mapping
# Prueft: alle alten Keys haben genau ein Mapping, keine Duplikate im Output, CSV vollstaendig.
# Aufruf: pwsh -NoProfile -File phase1\validate_mapping.ps1
# Exit-Code: 0 = OK, 1 = Fehler

Set-Location (Split-Path $PSScriptRoot -Parent)

$errors = 0
$warnings = 0

Write-Host "=== Phase 1 Mapping Validation ===" -ForegroundColor Cyan

# 1. Lade Quelldaten
$keysJson  = Get-Content "phase0\keys_de_en.json"  -Encoding UTF8 -Raw | ConvertFrom-Json
$csvData   = Import-Csv -Path "phase1\key_mapping.csv" -Encoding UTF8

$sourceKeys = $keysJson.PSObject.Properties.Name | Sort-Object
$csvKeys    = $csvData | ForEach-Object { $_.old_key }

# 2. Vollstaendigkeit: alle Source-Keys muessen im CSV sein
$missing = $sourceKeys | Where-Object { $_ -notin $csvKeys }
if ($missing.Count -gt 0) {
    Write-Host "FAIL: $($missing.Count) Keys fehlen im CSV:" -ForegroundColor Red
    $missing | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
    $errors++
} else {
    Write-Host "OK: Alle $($sourceKeys.Count) Source-Keys im CSV vorhanden." -ForegroundColor Green
}

# 3. Eindeutigkeit: jeder old_key darf nur einmal vorkommen
$duplicates = $csvData | Group-Object old_key | Where-Object { $_.Count -gt 1 }
if ($duplicates.Count -gt 0) {
    Write-Host "FAIL: $($duplicates.Count) doppelte old_keys im CSV:" -ForegroundColor Red
    $duplicates | ForEach-Object { Write-Host "  - $($_.Name) ($($_.Count)x)" -ForegroundColor Red }
    $errors++
} else {
    Write-Host "OK: Keine doppelten old_keys." -ForegroundColor Green
}

# 4. Scope-Werte muss 'shared' oder 'one' sein
$invalidScope = $csvData | Where-Object { $_.scope -notin @("shared", "one") }
if ($invalidScope.Count -gt 0) {
    Write-Host "FAIL: $($invalidScope.Count) Zeilen mit ungueltigem scope:" -ForegroundColor Red
    $invalidScope | ForEach-Object { Write-Host "  - $($_.old_key): '$($_.scope)'" -ForegroundColor Red }
    $errors++
} else {
    Write-Host "OK: Alle scope-Werte gueltig (shared/one)." -ForegroundColor Green
}

# 5. Shared-Keys muessen windows_key_match_or_empty gefuellt haben
$sharedNoWin = $csvData | Where-Object { $_.scope -eq "shared" -and [string]::IsNullOrWhiteSpace($_.windows_key_match_or_empty) }
if ($sharedNoWin.Count -gt 0) {
    Write-Host "FAIL: $($sharedNoWin.Count) shared-Keys ohne Windows-Key:" -ForegroundColor Red
    $sharedNoWin | ForEach-Object { Write-Host "  - $($_.old_key)" -ForegroundColor Red }
    $errors++
} else {
    Write-Host "OK: Alle shared-Keys haben Windows-Key-Referenz." -ForegroundColor Green
}

# 6. one-Keys: new_key muss UPPER_SNAKE sein (nur A-Z, 0-9, Underscore, Punkt fuer Windows-Keys)
$oneKeys = $csvData | Where-Object { $_.scope -eq "one" }
$badUpperSnake = $oneKeys | Where-Object { $_.new_key -cne $_.new_key.ToUpper() -or $_.new_key -match '[^A-Z0-9_]' }
if ($badUpperSnake.Count -gt 0) {
    Write-Host "WARN: $($badUpperSnake.Count) one-Keys nicht in reinem UPPER_SNAKE_CASE:" -ForegroundColor Yellow
    $badUpperSnake | ForEach-Object { Write-Host "  - $($_.old_key) -> $($_.new_key)" -ForegroundColor Yellow }
    $warnings++
} else {
    Write-Host "OK: Alle one-Keys in UPPER_SNAKE_CASE." -ForegroundColor Green
}

# 7. Shared-Keys: new_key muss mit windows_key_match uebereinstimmen
$sharedMismatch = $csvData | Where-Object { $_.scope -eq "shared" -and $_.new_key -ne $_.windows_key_match_or_empty }
if ($sharedMismatch.Count -gt 0) {
    Write-Host "FAIL: $($sharedMismatch.Count) shared-Keys: new_key stimmt nicht mit windows_key ueberein:" -ForegroundColor Red
    $sharedMismatch | ForEach-Object { Write-Host "  - $($_.old_key): new='$($_.new_key)' win='$($_.windows_key_match_or_empty)'" -ForegroundColor Red }
    $errors++
} else {
    Write-Host "OK: Alle shared-Keys: new_key = windows_key." -ForegroundColor Green
}

# 8. Statistik
$sharedCount = ($csvData | Where-Object { $_.scope -eq "shared" }).Count
$oneCount    = ($csvData | Where-Object { $_.scope -eq "one" }).Count
$totalCount  = $csvData.Count

Write-Host ""
Write-Host "=== Statistik ===" -ForegroundColor Cyan
Write-Host "  Total Keys:  $totalCount"
Write-Host "  Shared:      $sharedCount"
Write-Host "  ONE-spezif:  $oneCount"

# Unique Windows-Keys (mehrere ONE-Keys koennen auf gleichen Win-Key zeigen)
$uniqueWinKeys = ($csvData | Where-Object { $_.scope -eq "shared" } | Select-Object -ExpandProperty windows_key_match_or_empty -Unique).Count
Write-Host "  Unique Win-Keys referenziert: $uniqueWinKeys"

# 9. Ergebnis
Write-Host ""
if ($errors -gt 0) {
    Write-Host "VALIDATION FAILED: $errors Fehler, $warnings Warnungen." -ForegroundColor Red
    exit 1
} elseif ($warnings -gt 0) {
    Write-Host "VALIDATION PASSED WITH WARNINGS: $warnings Warnungen." -ForegroundColor Yellow
    exit 0
} else {
    Write-Host "VALIDATION PASSED: Kein Fehler, kein Warning." -ForegroundColor Green
    exit 0
}
