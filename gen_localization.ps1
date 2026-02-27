# Read translations from raw file + missing keys, then generate split LocalizationManager.kt
$rawLines = [System.IO.File]::ReadAllLines("C:\projekte\one.app\translations_raw.txt", [System.Text.Encoding]::UTF8)
$missingLines = [System.IO.File]::ReadAllLines("C:\projekte\one.app\missing_keys.txt", [System.Text.Encoding]::UTF8)

# Code remapping: xlsx uses "uk" for English, we use "en" internally
$codeRemap = @{ "uk" = "en" }

# Parse both files into ordered data structure
# Use ArrayList of pairs: (code, ordered dict of key->value)
$langOrder = [System.Collections.ArrayList]::new()
$langData = @{}

foreach ($source in @($rawLines, $missingLines)) {
    $currentLang = $null
    foreach ($line in $source) {
        if ($line -match "^===LANG:(\w+)===$") {
            $rawCode = $Matches[1]
            $currentLang = if ($codeRemap.ContainsKey($rawCode)) { $codeRemap[$rawCode] } else { $rawCode }
            if (-not $langData.ContainsKey($currentLang)) {
                $langData[$currentLang] = [ordered]@{}
                [void]$langOrder.Add($currentLang)
            }
        }
        elseif ($line -eq "===END===") {
            $currentLang = $null
        }
        elseif ($null -ne $currentLang -and $line -match "^([^=]+)=(.*)$") {
            $langData[$currentLang][$Matches[1]] = $Matches[2]
        }
    }
}

Write-Host "Parsed $($langOrder.Count) languages"
Write-Host "Codes: $($langOrder -join ', ')"
Write-Host "Keys per language (de): $($langData['de'].Count)"

# Language display info - read from missing_keys or define here
# We'll write these as data to a temp file and read back to avoid encoding issues
$langInfoPath = "C:\projekte\one.app\lang_info.txt"
$liWriter = New-Object System.IO.StreamWriter($langInfoPath, $false, (New-Object System.Text.UTF8Encoding($false)))

# code|name|flag_unicode_escape
$liWriter.WriteLine("de|Deutsch|\uD83C\uDDE9\uD83C\uDDEA")
$liWriter.WriteLine("no|Norsk|\uD83C\uDDF3\uD83C\uDDF4")
$liWriter.WriteLine("en|English|\uD83C\uDDEC\uD83C\uDDE7")
$liWriter.WriteLine("it|Italiano|\uD83C\uDDEE\uD83C\uDDF9")
$liWriter.WriteLine("nl|Nederlands|\uD83C\uDDF3\uD83C\uDDF1")

# Build names from native names - write directly using chars
$names = @{
    "fr" = "Fran" + [char]0x00E7 + "ais"
    "es" = "Espa" + [char]0x00F1 + "ol"
    "pt" = "Portugu" + [char]0x00EA + "s"
    "pl" = "Polski"
    "cs" = [string]([char]0x010C) + "e" + [char]0x0161 + "tina"
    "sk" = "Sloven" + [char]0x010D + "ina"
    "sl" = "Sloven" + [char]0x0161 + [char]0x010D + "ina"
    "hr" = "Hrvatski"
    "hu" = "Magyar"
    "ro" = "Rom" + [char]0x00E2 + "n" + [char]0x0103
    "bg" = [string]([char]0x0411) + [char]0x044A + [char]0x043B + [char]0x0433 + [char]0x0430 + [char]0x0440 + [char]0x0441 + [char]0x043A + [char]0x0438
    "el" = [string]([char]0x0395) + [char]0x03BB + [char]0x03BB + [char]0x03B7 + [char]0x03BD + [char]0x03B9 + [char]0x03BA + [char]0x03AC
    "da" = "Dansk"
    "sv" = "Svenska"
    "fi" = "Suomi"
    "et" = "Eesti"
    "lv" = "Latvie" + [char]0x0161 + "u"
    "lt" = "Lietuvi" + [char]0x0173
    "ga" = "Gaeilge"
    "mt" = "Malti"
    "ar" = [string]([char]0x0627) + [char]0x0644 + [char]0x0639 + [char]0x0631 + [char]0x0628 + [char]0x064A + [char]0x0629
    "ru" = [string]([char]0x0420) + [char]0x0443 + [char]0x0441 + [char]0x0441 + [char]0x043A + [char]0x0438 + [char]0x0439
    "tr" = "T" + [char]0x00FC + "rk" + [char]0x00E7 + "e"
    "sr" = [string]([char]0x0421) + [char]0x0440 + [char]0x043F + [char]0x0441 + [char]0x043A + [char]0x0438
    "sq" = "Shqip"
    "zh" = [string]([char]0x4E2D) + [char]0x6587
    "ja" = [string]([char]0x65E5) + [char]0x672C + [char]0x8A9E
    "ko" = [string]([char]0xD55C) + [char]0xAD6D + [char]0xC5B4
    "id" = "Bahasa Indonesia"
    "th" = [string]([char]0x0E44) + [char]0x0E17 + [char]0x0E22
}

$flags = @{
    "fr" = "\uD83C\uDDEB\uD83C\uDDF7"; "es" = "\uD83C\uDDEA\uD83C\uDDF8"
    "pt" = "\uD83C\uDDF5\uD83C\uDDF9"; "pl" = "\uD83C\uDDF5\uD83C\uDDF1"
    "cs" = "\uD83C\uDDE8\uD83C\uDDFF"; "sk" = "\uD83C\uDDF8\uD83C\uDDF0"
    "sl" = "\uD83C\uDDF8\uD83C\uDDEE"; "hr" = "\uD83C\uDDED\uD83C\uDDF7"
    "hu" = "\uD83C\uDDED\uD83C\uDDFA"; "ro" = "\uD83C\uDDF7\uD83C\uDDF4"
    "bg" = "\uD83C\uDDE7\uD83C\uDDEC"; "el" = "\uD83C\uDDEC\uD83C\uDDF7"
    "da" = "\uD83C\uDDE9\uD83C\uDDF0"; "sv" = "\uD83C\uDDF8\uD83C\uDDEA"
    "fi" = "\uD83C\uDDEB\uD83C\uDDEE"; "et" = "\uD83C\uDDEA\uD83C\uDDEA"
    "lv" = "\uD83C\uDDF1\uD83C\uDDFB"; "lt" = "\uD83C\uDDF1\uD83C\uDDF9"
    "ga" = "\uD83C\uDDEE\uD83C\uDDEA"; "mt" = "\uD83C\uDDF2\uD83C\uDDF9"
    "ar" = "\uD83C\uDDF8\uD83C\uDDE6"; "ru" = "\uD83C\uDDF7\uD83C\uDDFA"
    "tr" = "\uD83C\uDDF9\uD83C\uDDF7"; "sr" = "\uD83C\uDDF7\uD83C\uDDF8"
    "sq" = "\uD83C\uDDE6\uD83C\uDDF1"; "zh" = "\uD83C\uDDE8\uD83C\uDDF3"
    "ja" = "\uD83C\uDDEF\uD83C\uDDF5"; "ko" = "\uD83C\uDDF0\uD83C\uDDF7"
    "id" = "\uD83C\uDDEE\uD83C\uDDE9"; "th" = "\uD83C\uDDF9\uD83C\uDDED"
}

$liWriter.Close()
$liWriter.Dispose()

# Generate the Kotlin file
$outPath = "C:\projekte\one.app\app\src\main\java\com\uip\oneapp\ui\localization\LocalizationManager.kt"
$w = New-Object System.IO.StreamWriter($outPath, $false, (New-Object System.Text.UTF8Encoding($false)))

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
$w.WriteLine("        AppLanguage(`"de`", `"Deutsch`", `"\uD83C\uDDE9\uD83C\uDDEA`"),")
$w.WriteLine("        AppLanguage(`"no`", `"Norsk`", `"\uD83C\uDDF3\uD83C\uDDF4`"),")
$w.WriteLine("        AppLanguage(`"en`", `"English`", `"\uD83C\uDDEC\uD83C\uDDE7`"),")
$w.WriteLine("        AppLanguage(`"it`", `"Italiano`", `"\uD83C\uDDEE\uD83C\uDDF9`"),")
$w.WriteLine("        AppLanguage(`"nl`", `"Nederlands`", `"\uD83C\uDDF3\uD83C\uDDF1`"),")
foreach ($code in @("fr","es","pt","pl","cs","sk","sl","hr","hu","ro","bg","el","da","sv","fi","et","lv","lt","ga","mt","ar","ru","tr","sr","sq","zh","ja","ko","id","th")) {
    $name = $names[$code]
    $flag = $flags[$code]
    $w.WriteLine("        AppLanguage(`"$code`", `"$name`", `"$flag`"),")
}
$w.WriteLine("    )")
$w.WriteLine("")

# Write per-language functions
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

# translations lazy map
$w.WriteLine("    private val translations: Map<String, Map<String, String>> by lazy {")
$w.WriteLine("        mapOf(")
foreach ($code in $langOrder) {
    $w.WriteLine("            `"$code`" to ${code}Translations(),")
}
$w.WriteLine("        )")
$w.WriteLine("    }")
$w.WriteLine("")
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

Write-Host "Generated LocalizationManager.kt: $($langOrder.Count) languages, $($langData['de'].Count) keys each"

# Cleanup
Remove-Item $langInfoPath -ErrorAction SilentlyContinue
