package com.uip.oneapp.export

enum class OsdFontSize { Small, Medium, Large, Maxi }

enum class OsdColor { Green, White, Yellow }

enum class OsdBackground { Transparent, SemiTransparent, Solid }

enum class OsdFlashPosition { Center, BelowLine1 }

data class OsdSettings(
    val enableOsdBurnIn: Boolean = false,
    val showMeterValue: Boolean = true,
    val showDate: Boolean = true,
    val showInclination: Boolean = false,
    val fontSize: OsdFontSize = OsdFontSize.Medium,
    val fontColor: OsdColor = OsdColor.Green,
    val background: OsdBackground = OsdBackground.SemiTransparent,
    val findingFlashPosition: OsdFlashPosition = OsdFlashPosition.Center,
    // Damage findings are an app-only concept the camera can't know about,
    // so this layer is independent from enableOsdBurnIn. With hardware OSD
    // (camera renders date/meter/time itself) the static OSD bars are off
    // but findings still need to flash on the burned video.
    val enableFindingBurnIn: Boolean = true
)
