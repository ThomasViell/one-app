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
    val findingFlashPosition: OsdFlashPosition = OsdFlashPosition.Center
)
