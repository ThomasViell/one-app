# ============================================================================
# ONE.APP Translation Pipeline
# ============================================================================
# Unified script: Extract from xlsx + merge missing_keys + generate Kotlin + validate
#
# Usage:
#   .\translate.ps1                  # Generate from existing translations_raw.txt
#   .\translate.ps1 -Extract         # Re-extract from xlsx first, then generate
#   .\translate.ps1 -ValidateOnly    # Only check S("key") usage vs translations
#   .\translate.ps1 -Extract -Build  # Extract, generate, and build APK
# ============================================================================

param(
    [switch]$Extract,
    [switch]$ValidateOnly,
    [switch]$Build
)

$projectRoot = "C:\projekte\one.app"
$rawFile = "$projectRoot\translations_raw.txt"
$missingFile = "$projectRoot\missing_keys.txt"
$outFile = "$projectRoot\app\src\main\java\com\uip\oneapp\ui\localization\LocalizationManager.kt"
$srcDir = "$projectRoot\app\src\main\java\com\uip\oneapp"

# Code remapping: xlsx header codes -> internal codes
$codeRemap = @{ "uk" = "en"; "cn" = "zh" }

# ============================================================================
# STEP 1: Extract from xlsx (optional)
# ============================================================================
if ($Extract) {
    $xlsxPath = "$projectRoot\ONE_APP_Translations.xlsx"
    if (-not (Test-Path $xlsxPath)) {
        Write-Host "ERROR: $xlsxPath not found!" -ForegroundColor Red
        exit 1
    }

    Write-Host "=== Extracting from xlsx ===" -ForegroundColor Cyan
    $excel = New-Object -ComObject Excel.Application
    $excel.Visible = $false
    $excel.DisplayAlerts = $false
    $workbook = $excel.Workbooks.Open($xlsxPath)
    $sheet = $workbook.Sheets.Item(1)
    $maxRow = $sheet.UsedRange.Rows.Count
    $maxCol = $sheet.UsedRange.Columns.Count

    # Build column-to-code mapping from headers
    $colCodes = @()
    for ($c = 5; $c -le $maxCol; $c++) {
        $header = [string]$sheet.Cells.Item(1, $c).Value2
        if ($header -match '\((\w+)\)') {
            $code = $Matches[1].ToLower()
            if ($codeRemap.ContainsKey($code)) { $code = $codeRemap[$code] }
            $colCodes += @{ Col = $c; Code = $code; Header = $header }
        }
    }

    Write-Host "Found $($colCodes.Count) languages in xlsx"

    $writer = New-Object System.IO.StreamWriter($rawFile, $false, (New-Object System.Text.UTF8Encoding($false)))
    foreach ($lc in $colCodes) {
        $col = $lc.Col
        $code = $lc.Code
        $writer.WriteLine("===LANG:$code===")
        for ($r = 2; $r -le $maxRow; $r++) {
            $key = $sheet.Cells.Item($r, 1).Value2
            if ([string]::IsNullOrEmpty($key)) { continue }
            $value = $sheet.Cells.Item($r, $col).Value2
            if ($null -eq $value) { $value = "" }
            $value = [string]$value
            $writer.WriteLine("$key=$value")
        }
        $writer.WriteLine("===END===")
    }
    $writer.Close()
    $writer.Dispose()

    $workbook.Close($false)
    $excel.Quit()
    [System.Runtime.Interopservices.Marshal]::ReleaseComObject($excel) | Out-Null

    Write-Host "Extracted to $rawFile" -ForegroundColor Green
}

# ============================================================================
# STEP 2: Parse translations_raw.txt + missing_keys.txt
# ============================================================================
if (-not $ValidateOnly) {
    if (-not (Test-Path $rawFile)) {
        Write-Host "ERROR: $rawFile not found! Run with -Extract first." -ForegroundColor Red
        exit 1
    }

    Write-Host "`n=== Parsing translation data ===" -ForegroundColor Cyan

    $langOrder = [System.Collections.ArrayList]::new()
    $langData = @{}

    # Parse raw translations
    $rawLines = [System.IO.File]::ReadAllLines($rawFile, [System.Text.Encoding]::UTF8)
    $currentLang = $null
    foreach ($line in $rawLines) {
        if ($line -match "^===LANG:(\w+)===$") {
            $rawCode = $Matches[1]
            $currentLang = if ($codeRemap.ContainsKey($rawCode)) { $codeRemap[$rawCode] } else { $rawCode }
            if (-not $langData.ContainsKey($currentLang)) {
                $langData[$currentLang] = [ordered]@{}
                [void]$langOrder.Add($currentLang)
            }
        }
        elseif ($line -eq "===END===") { $currentLang = $null }
        elseif ($null -ne $currentLang -and $line -match "^([^=]+)=(.*)$") {
            $langData[$currentLang][$Matches[1]] = $Matches[2]
        }
    }

    Write-Host "Parsed $($langOrder.Count) languages from translations_raw.txt"

    # Parse missing_keys.txt - supports partial languages (DE fallback for missing)
    if (Test-Path $missingFile) {
        $missingLines = [System.IO.File]::ReadAllLines($missingFile, [System.Text.Encoding]::UTF8)
        $missingKeys = @{}  # key -> { lang -> value }
        $currentLang = $null

        foreach ($line in $missingLines) {
            if ($line -match "^===LANG:(\w+)===$") {
                $rawCode = $Matches[1]
                $currentLang = if ($codeRemap.ContainsKey($rawCode)) { $codeRemap[$rawCode] } else { $rawCode }
            }
            elseif ($line -eq "===END===") { $currentLang = $null }
            elseif ($null -ne $currentLang -and $line -match "^([^=]+)=(.*)$") {
                $key = $Matches[1]
                $val = $Matches[2]
                if (-not $missingKeys.ContainsKey($key)) { $missingKeys[$key] = @{} }
                $missingKeys[$key][$currentLang] = $val
            }
        }

        # Merge missing keys into all languages, using DE fallback
        $addedCount = 0
        foreach ($kvp in $missingKeys.GetEnumerator()) {
            $key = $kvp.Key
            $translations = $kvp.Value
            $deFallback = if ($translations.ContainsKey("de")) { $translations["de"] } else { $key }

            foreach ($code in $langOrder) {
                if (-not $langData[$code].Contains($key)) {
                    if ($translations.ContainsKey($code)) {
                        $langData[$code][$key] = $translations[$code]
                    } else {
                        # Use DE fallback for languages not in missing_keys.txt
                        $langData[$code][$key] = $deFallback
                    }
                    $addedCount++
                }
            }
        }
        Write-Host "Merged $($missingKeys.Count) keys from missing_keys.txt ($addedCount insertions, DE-fallback for missing languages)" -ForegroundColor Green
    }

    Write-Host "Total: $($langOrder.Count) languages, $($langData['de'].Count) keys each"

    # ============================================================================
    # STEP 3: Generate LocalizationManager.kt
    # ============================================================================
    Write-Host "`n=== Generating LocalizationManager.kt ===" -ForegroundColor Cyan

    # Language display names (using char escapes for non-ASCII)
    $langInfo = @{
        "de" = @{ Name = "Deutsch";          Flag = "\uD83C\uDDE9\uD83C\uDDEA" }
        "no" = @{ Name = "Norsk";            Flag = "\uD83C\uDDF3\uD83C\uDDF4" }
        "en" = @{ Name = "English";          Flag = "\uD83C\uDDEC\uD83C\uDDE7" }
        "it" = @{ Name = "Italiano";         Flag = "\uD83C\uDDEE\uD83C\uDDF9" }
        "nl" = @{ Name = "Nederlands";       Flag = "\uD83C\uDDF3\uD83C\uDDF1" }
        "fr" = @{ Name = ("Fran" + [char]0xE7 + "ais");  Flag = "\uD83C\uDDEB\uD83C\uDDF7" }
        "es" = @{ Name = ("Espa" + [char]0xF1 + "ol");   Flag = "\uD83C\uDDEA\uD83C\uDDF8" }
        "pt" = @{ Name = ("Portugu" + [char]0xEA + "s");  Flag = "\uD83C\uDDF5\uD83C\uDDF9" }
        "pl" = @{ Name = "Polski";           Flag = "\uD83C\uDDF5\uD83C\uDDF1" }
        "cs" = @{ Name = ([string]([char]0x10C) + "e" + [char]0x161 + "tina");  Flag = "\uD83C\uDDE8\uD83C\uDDFF" }
        "sk" = @{ Name = ("Sloven" + [char]0x10D + "ina");  Flag = "\uD83C\uDDF8\uD83C\uDDF0" }
        "sl" = @{ Name = ("Sloven" + [char]0x161 + [char]0x10D + "ina");  Flag = "\uD83C\uDDF8\uD83C\uDDEE" }
        "hr" = @{ Name = "Hrvatski";         Flag = "\uD83C\uDDED\uD83C\uDDF7" }
        "hu" = @{ Name = "Magyar";           Flag = "\uD83C\uDDED\uD83C\uDDFA" }
        "ro" = @{ Name = ("Rom" + [char]0xE2 + "n" + [char]0x103);  Flag = "\uD83C\uDDF7\uD83C\uDDF4" }
        "bg" = @{ Name = ([string]([char]0x411) + [char]0x44A + [char]0x43B + [char]0x433 + [char]0x430 + [char]0x440 + [char]0x441 + [char]0x43A + [char]0x438);  Flag = "\uD83C\uDDE7\uD83C\uDDEC" }
        "el" = @{ Name = ([string]([char]0x395) + [char]0x3BB + [char]0x3BB + [char]0x3B7 + [char]0x3BD + [char]0x3B9 + [char]0x3BA + [char]0x3AC);  Flag = "\uD83C\uDDEC\uD83C\uDDF7" }
        "da" = @{ Name = "Dansk";            Flag = "\uD83C\uDDE9\uD83C\uDDF0" }
        "sv" = @{ Name = "Svenska";          Flag = "\uD83C\uDDF8\uD83C\uDDEA" }
        "fi" = @{ Name = "Suomi";            Flag = "\uD83C\uDDEB\uD83C\uDDEE" }
        "et" = @{ Name = "Eesti";            Flag = "\uD83C\uDDEA\uD83C\uDDEA" }
        "lv" = @{ Name = ("Latvie" + [char]0x161 + "u");  Flag = "\uD83C\uDDF1\uD83C\uDDFB" }
        "lt" = @{ Name = ("Lietuvi" + [char]0x173);  Flag = "\uD83C\uDDF1\uD83C\uDDF9" }
        "ga" = @{ Name = "Gaeilge";          Flag = "\uD83C\uDDEE\uD83C\uDDEA" }
        "mt" = @{ Name = "Malti";            Flag = "\uD83C\uDDF2\uD83C\uDDF9" }
        "ar" = @{ Name = ([string]([char]0x627) + [char]0x644 + [char]0x639 + [char]0x631 + [char]0x628 + [char]0x64A + [char]0x629);  Flag = "\uD83C\uDDF8\uD83C\uDDE6" }
        "ru" = @{ Name = ([string]([char]0x420) + [char]0x443 + [char]0x441 + [char]0x441 + [char]0x43A + [char]0x438 + [char]0x439);  Flag = "\uD83C\uDDF7\uD83C\uDDFA" }
        "tr" = @{ Name = ("T" + [char]0xFC + "rk" + [char]0xE7 + "e");  Flag = "\uD83C\uDDF9\uD83C\uDDF7" }
        "sr" = @{ Name = ([string]([char]0x421) + [char]0x440 + [char]0x43F + [char]0x441 + [char]0x43A + [char]0x438);  Flag = "\uD83C\uDDF7\uD83C\uDDF8" }
        "sq" = @{ Name = "Shqip";            Flag = "\uD83C\uDDE6\uD83C\uDDF1" }
        "zh" = @{ Name = ([string]([char]0x4E2D) + [char]0x6587);  Flag = "\uD83C\uDDE8\uD83C\uDDF3" }
        "ja" = @{ Name = ([string]([char]0x65E5) + [char]0x672C + [char]0x8A9E);  Flag = "\uD83C\uDDEF\uD83C\uDDF5" }
        "ko" = @{ Name = ([string]([char]0xD55C) + [char]0xAD6D + [char]0xC5B4);  Flag = "\uD83C\uDDF0\uD83C\uDDF7" }
        "id" = @{ Name = "Bahasa Indonesia"; Flag = "\uD83C\uDDEE\uD83C\uDDE9" }
        "th" = @{ Name = ([string]([char]0xE44) + [char]0xE17 + [char]0xE22);  Flag = "\uD83C\uDDF9\uD83C\uDDED" }
    }

    $w = New-Object System.IO.StreamWriter($outFile, $false, (New-Object System.Text.UTF8Encoding($false)))

    # Package + imports
    $w.WriteLine("package com.uip.oneapp.ui.localization")
    $w.WriteLine("")
    $w.WriteLine("import android.content.Context")
    $w.WriteLine("import androidx.compose.runtime.Composable")
    $w.WriteLine("import androidx.compose.runtime.collectAsState")
    $w.WriteLine("import androidx.compose.runtime.getValue")
    $w.WriteLine("import androidx.datastore.preferences.core.edit")
    $w.WriteLine("import androidx.datastore.preferences.core.stringPreferencesKey")
    $w.WriteLine("import androidx.datastore.preferences.preferencesDataStore")
    $w.WriteLine("import kotlinx.coroutines.CoroutineScope")
    $w.WriteLine("import kotlinx.coroutines.Dispatchers")
    $w.WriteLine("import kotlinx.coroutines.flow.MutableStateFlow")
    $w.WriteLine("import kotlinx.coroutines.flow.StateFlow")
    $w.WriteLine("import kotlinx.coroutines.flow.asStateFlow")
    $w.WriteLine("import kotlinx.coroutines.flow.first")
    $w.WriteLine("import kotlinx.coroutines.launch")
    $w.WriteLine("")
    $w.WriteLine("private val Context.langStore by preferencesDataStore(name = `"language_prefs`")")
    $w.WriteLine("")
    $w.WriteLine("data class AppLanguage(")
    $w.WriteLine("    val code: String,")
    $w.WriteLine("    val name: String,")
    $w.WriteLine("    val flag: String")
    $w.WriteLine(")")
    $w.WriteLine("")
    $w.WriteLine("object LocalizationManager {")
    $w.WriteLine("")
    $w.WriteLine("    private val KEY_LANGUAGE = stringPreferencesKey(`"app_language`")")
    $w.WriteLine("")
    $w.WriteLine("    private val _currentLanguage = MutableStateFlow(`"de`")")
    $w.WriteLine("    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()")
    $w.WriteLine("")

    # availableLanguages
    $w.WriteLine("    val availableLanguages = listOf(")
    foreach ($code in $langOrder) {
        $info = $langInfo[$code]
        if ($null -eq $info) {
            Write-Host "WARNING: No display info for language '$code' - skipping from availableLanguages" -ForegroundColor Yellow
            continue
        }
        $name = $info.Name
        $flag = $info.Flag
        $w.WriteLine("        AppLanguage(`"$code`", `"$name`", `"$flag`"),")
    }
    $w.WriteLine("    )")
    $w.WriteLine("")

    # Per-language translation functions
    foreach ($code in $langOrder) {
        $pairs = $langData[$code]
        $w.WriteLine("    private fun ${code}Translations(): Map<String, String> = mapOf(")
        foreach ($p in $pairs.GetEnumerator()) {
            $key = $p.Key
            $val = $p.Value.Replace('\', '\\').Replace('"', '\"').Replace('$', '\$')
            $w.WriteLine("        `"$key`" to `"$val`",")
        }
        $w.WriteLine("    )")
        $w.WriteLine("")
    }

    # Lazy translations map
    $w.WriteLine("    private val translations: Map<String, Map<String, String>> by lazy {")
    $w.WriteLine("        mapOf(")
    foreach ($code in $langOrder) {
        $w.WriteLine("            `"$code`" to ${code}Translations(),")
    }
    $w.WriteLine("        )")
    $w.WriteLine("    }")
    $w.WriteLine("")

    # init, setLanguage, getString
    $w.WriteLine("    fun init(context: Context) {")
    $w.WriteLine("        CoroutineScope(Dispatchers.IO).launch {")
    $w.WriteLine("            val prefs = context.langStore.data.first()")
    $w.WriteLine("            _currentLanguage.value = prefs[KEY_LANGUAGE] ?: `"de`"")
    $w.WriteLine("        }")
    $w.WriteLine("    }")
    $w.WriteLine("")
    $w.WriteLine("    fun setLanguage(context: Context, langCode: String) {")
    $w.WriteLine("        _currentLanguage.value = langCode")
    $w.WriteLine("        CoroutineScope(Dispatchers.IO).launch {")
    $w.WriteLine("            context.langStore.edit { it[KEY_LANGUAGE] = langCode }")
    $w.WriteLine("        }")
    $w.WriteLine("    }")
    $w.WriteLine("")
    $w.WriteLine("    fun getString(key: String): String {")
    $w.WriteLine("        val lang = _currentLanguage.value")
    $w.WriteLine("        return translations[lang]?.get(key)")
    $w.WriteLine("            ?: translations[`"de`"]?.get(key)")
    $w.WriteLine("            ?: key")
    $w.WriteLine("    }")
    $w.WriteLine("}")
    $w.WriteLine("")

    # S() composable
    $w.WriteLine("/**")
    $w.WriteLine(" * Composable-friendly string lookup. Recomposes when language changes.")
    $w.WriteLine(" */")
    $w.WriteLine("@Suppress(`"unused`")")
    $w.WriteLine("@Composable")
    $w.WriteLine("fun S(key: String): String {")
    $w.WriteLine("    // Collecting state triggers recomposition when language changes")
    $w.WriteLine("    val lang by LocalizationManager.currentLanguage.collectAsState()")
    $w.WriteLine("    return LocalizationManager.getString(key)")
    $w.WriteLine("}")
    $w.WriteLine("")

    $w.Close()
    $w.Dispose()

    Write-Host "Generated $outFile" -ForegroundColor Green
    Write-Host "  $($langOrder.Count) languages, $($langData['de'].Count) keys each"
}

# ============================================================================
# STEP 4: Validate S("key") usage in code
# ============================================================================
Write-Host "`n=== Validating S(`"key`") usage ===" -ForegroundColor Cyan

# Collect all keys from DE translations (reference language)
if ($null -eq $langData -or $langData.Count -eq 0) {
    # If we only run validation, parse the raw file first
    $langData = @{}
    $rawLines = [System.IO.File]::ReadAllLines($rawFile, [System.Text.Encoding]::UTF8)
    $currentLang = $null
    foreach ($line in $rawLines) {
        if ($line -match "^===LANG:(\w+)===$") {
            $rawCode = $Matches[1]
            $currentLang = if ($codeRemap.ContainsKey($rawCode)) { $codeRemap[$rawCode] } else { $rawCode }
            if (-not $langData.ContainsKey($currentLang)) { $langData[$currentLang] = [ordered]@{} }
        }
        elseif ($line -eq "===END===") { $currentLang = $null }
        elseif ($null -ne $currentLang -and $line -match "^([^=]+)=(.*)$") {
            $langData[$currentLang][$Matches[1]] = $Matches[2]
        }
    }
    # Also parse missing_keys
    if (Test-Path $missingFile) {
        $missingLines = [System.IO.File]::ReadAllLines($missingFile, [System.Text.Encoding]::UTF8)
        $currentLang = $null
        foreach ($line in $missingLines) {
            if ($line -match "^===LANG:(\w+)===$") {
                $rawCode = $Matches[1]
                $currentLang = if ($codeRemap.ContainsKey($rawCode)) { $codeRemap[$rawCode] } else { $rawCode }
            }
            elseif ($line -eq "===END===") { $currentLang = $null }
            elseif ($null -ne $currentLang -and $line -match "^([^=]+)=(.*)$") {
                if ($langData.ContainsKey($currentLang)) {
                    $langData[$currentLang][$Matches[1]] = $Matches[2]
                }
            }
        }
    }
}

$availableKeys = [System.Collections.Generic.HashSet[string]]::new()
if ($langData.ContainsKey("de")) {
    foreach ($k in $langData["de"].Keys) { [void]$availableKeys.Add($k) }
}

# Scan all .kt files for S("...") and getString("...") calls
$usedKeys = [System.Collections.Generic.HashSet[string]]::new()
$ktFiles = Get-ChildItem -Path $srcDir -Recurse -Filter "*.kt" | Where-Object { $_.FullName -notlike "*LocalizationManager*" }

foreach ($file in $ktFiles) {
    $content = [System.IO.File]::ReadAllText($file.FullName, [System.Text.Encoding]::UTF8)
    # Match S("key") and getString("key")
    $matches = [regex]::Matches($content, '(?:S|getString)\("([^"]+)"\)')
    foreach ($m in $matches) {
        [void]$usedKeys.Add($m.Groups[1].Value)
    }
}

# Find missing and unused keys
$missingInTranslations = @()
$unusedInCode = @()

foreach ($key in $usedKeys) {
    if (-not $availableKeys.Contains($key)) {
        $missingInTranslations += $key
    }
}

foreach ($key in $availableKeys) {
    if (-not $usedKeys.Contains($key)) {
        $unusedInCode += $key
    }
}

# Report
Write-Host ""
Write-Host "Keys in code (S/getString): $($usedKeys.Count)" -ForegroundColor White
Write-Host "Keys in translations (DE):  $($availableKeys.Count)" -ForegroundColor White

if ($missingInTranslations.Count -eq 0) {
    Write-Host "`nAll keys used in code have translations." -ForegroundColor Green
} else {
    Write-Host "`nMISSING translations for $($missingInTranslations.Count) keys used in code:" -ForegroundColor Red
    foreach ($k in ($missingInTranslations | Sort-Object)) {
        Write-Host "  - $k" -ForegroundColor Red
    }
}

if ($unusedInCode.Count -gt 0) {
    Write-Host "`n$($unusedInCode.Count) translation keys not used in code (may be used dynamically):" -ForegroundColor Yellow
    foreach ($k in ($unusedInCode | Sort-Object)) {
        Write-Host "  - $k" -ForegroundColor DarkYellow
    }
}

Write-Host ""

# ============================================================================
# STEP 5: Build (optional)
# ============================================================================
if ($Build) {
    Write-Host "=== Building APK ===" -ForegroundColor Cyan
    $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
    & "$projectRoot\gradlew.bat" assembleDebug
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Build successful!" -ForegroundColor Green
    } else {
        Write-Host "Build FAILED!" -ForegroundColor Red
        exit 1
    }
}

Write-Host "=== Done ===" -ForegroundColor Green
