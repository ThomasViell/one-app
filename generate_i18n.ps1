#!/usr/bin/env pwsh
<#
.SYNOPSIS
Generiert 35 i18n JSON-Dateien mit update_*-Keys für Phase 7
#>

# Ensure i18n directory exists
$i18nDir = "app/src/main/assets/i18n"
New-Item -ItemType Directory -Force -Path $i18nDir | Out-Null

# Basis-Translations für DE und EN
$translations = @{
    "update_check_now" = @{
        "de" = "Nach Updates suchen"
        "en" = "Check for Updates"
    }
    "update_no_update" = @{
        "de" = "Du hast die aktuelle Version"
        "en" = "You have the latest version"
    }
    "update_available" = @{
        "de" = "Update verfügbar: {version}"
        "en" = "Update available: {version}"
    }
    "update_install_now" = @{
        "de" = "Jetzt installieren"
        "en" = "Install Now"
    }
    "update_later" = @{
        "de" = "Später"
        "en" = "Later"
    }
    "update_progress_downloading" = @{
        "de" = "Wird heruntergeladen..."
        "en" = "Downloading..."
    }
    "update_progress_verifying" = @{
        "de" = "Wird überprüft..."
        "en" = "Verifying..."
    }
    "update_progress_installing" = @{
        "de" = "Wird installiert..."
        "en" = "Installing..."
    }
    "update_error_network" = @{
        "de" = "Netzwerkfehler. Bitte später erneut versuchen."
        "en" = "Network error. Please try again later."
    }
    "update_error_hash_mismatch" = @{
        "de" = "Update-Datei beschädigt. Bitte später erneut versuchen."
        "en" = "Update file corrupted. Please try again later."
    }
    "update_error_install_failed" = @{
        "de" = "Installation fehlgeschlagen. Bitte später erneut versuchen."
        "en" = "Installation failed. Please try again later."
    }
    "update_channel_stable" = @{
        "de" = "Stabil"
        "en" = "Stable"
    }
    "update_channel_beta" = @{
        "de" = "Beta"
        "en" = "Beta"
    }
    "update_last_check" = @{
        "de" = "Letzter Check: {time}"
        "en" = "Last check: {time}"
    }
    "update_notes_label" = @{
        "de" = "Änderungen:"
        "en" = "Changes:"
    }
    "update_size_label" = @{
        "de" = "Größe: {size}"
        "en" = "Size: {size}"
    }
}

# Alle 35 Sprachen
$languages = @(
    @{ code = "de"; name = "Deutsch"; hasNative = $true }
    @{ code = "en"; name = "English"; hasNative = $true }
    @{ code = "no"; name = "Norsk"; hasNative = $false }
    @{ code = "it"; name = "Italiano"; hasNative = $false }
    @{ code = "nl"; name = "Nederlands"; hasNative = $false }
    @{ code = "fr"; name = "Français"; hasNative = $false }
    @{ code = "es"; name = "Español"; hasNative = $false }
    @{ code = "pt"; name = "Português"; hasNative = $false }
    @{ code = "pl"; name = "Polski"; hasNative = $false }
    @{ code = "cs"; name = "Čeština"; hasNative = $false }
    @{ code = "sk"; name = "Slovenčina"; hasNative = $false }
    @{ code = "sl"; name = "Slovenščina"; hasNative = $false }
    @{ code = "hr"; name = "Hrvatski"; hasNative = $false }
    @{ code = "hu"; name = "Magyar"; hasNative = $false }
    @{ code = "ro"; name = "Română"; hasNative = $false }
    @{ code = "bg"; name = "Български"; hasNative = $false }
    @{ code = "el"; name = "Ελληνικά"; hasNative = $false }
    @{ code = "da"; name = "Dansk"; hasNative = $false }
    @{ code = "sv"; name = "Svenska"; hasNative = $false }
    @{ code = "fi"; name = "Suomi"; hasNative = $false }
    @{ code = "et"; name = "Eesti"; hasNative = $false }
    @{ code = "lv"; name = "Latviešu"; hasNative = $false }
    @{ code = "lt"; name = "Lietuvių"; hasNative = $false }
    @{ code = "ga"; name = "Gaeilge"; hasNative = $false }
    @{ code = "mt"; name = "Malti"; hasNative = $false }
    @{ code = "ar"; name = "العربية"; hasNative = $false }
    @{ code = "ru"; name = "Русский"; hasNative = $false }
    @{ code = "tr"; name = "Türkçe"; hasNative = $false }
    @{ code = "sr"; name = "Српски"; hasNative = $false }
    @{ code = "sq"; name = "Shqip"; hasNative = $false }
    @{ code = "zh"; name = "中文"; hasNative = $false }
    @{ code = "ja"; name = "日本語"; hasNative = $false }
    @{ code = "ko"; name = "한국어"; hasNative = $false }
    @{ code = "id"; name = "Bahasa Indonesia"; hasNative = $false }
    @{ code = "th"; name = "ไทย"; hasNative = $false }
)

foreach ($lang in $languages) {
    $langCode = $lang.code
    $jsonObj = @{}

    foreach ($key in $translations.Keys) {
        if ($lang.hasNative) {
            # DE und EN haben native Übersetzungen
            $jsonObj[$key] = $translations[$key][$langCode]
        } else {
            # Alle anderen Sprachen bekommen TODO-Markierung mit DE-Fallback
            $jsonObj[$key] = "[TODO: $($lang.name)] " + $translations[$key]["de"]
        }
    }

    # JSON mit 4-Space-Indentation schreiben
    $json = $jsonObj | ConvertTo-Json -Depth 100 | % { $_ -replace '    ', '    ' }

    $filePath = Join-Path $i18nDir "$langCode.json"
    Set-Content -Path $filePath -Value $json -Encoding UTF8
    Write-Host "✓ Created $filePath"
}

Write-Host "`nDone! Generated $($languages.Count) language files in $i18nDir"
