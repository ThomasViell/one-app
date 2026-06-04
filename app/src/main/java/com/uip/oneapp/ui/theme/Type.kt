package com.uip.oneapp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.uip.oneapp.R

// =====================================================================================
// DrainQ SA-Design — Typografie (Vorgabe Abschnitt 2)
// Schrift: Inter (OFL) ersetzt Barlow. NUR Gewichte 400/500/600 — kein Bold/Black.
// Touch-vergrößerte Material3-Styles; Mindestgröße 16 sp im Fließtext (Outdoor/Handschuh).
// =====================================================================================
val InterFontFamily = FontFamily(
    Font(R.font.inter_regular,  FontWeight.Normal),   // 400
    Font(R.font.inter_medium,   FontWeight.Medium),   // 500
    Font(R.font.inter_semibold, FontWeight.SemiBold), // 600
)

private fun inter(
    weight: FontWeight,
    size: Int,
    line: Int = (size * 1.3).toInt(),
    spacing: Double = 0.0,
) = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = spacing.sp,
)

// Material3-Skala — die in der Vorgabe genannten Rollen sind exakt gesetzt, der Rest
// wird harmonisch ergänzt (alle Inter, max. Gewicht 600).
val Typography = Typography(
    displayLarge   = inter(FontWeight.SemiBold, 48, 56),
    displayMedium  = inter(FontWeight.SemiBold, 44, 52),
    displaySmall   = inter(FontWeight.SemiBold, 40, 48),  // Hero-KPI (Home-Statwerte)
    headlineLarge  = inter(FontWeight.SemiBold, 32, 40),
    headlineMedium = inter(FontWeight.SemiBold, 27, 34),  // Screen-Titel im Header
    headlineSmall  = inter(FontWeight.SemiBold, 24, 30),
    titleLarge     = inter(FontWeight.SemiBold, 22, 28),  // Card-Überschrift, Detail-Titel
    titleMedium    = inter(FontWeight.SemiBold, 20, 26),  // Button-Label, Zeilen-Titel, Softbuttons
    titleSmall     = inter(FontWeight.Medium,   16, 22),
    bodyLarge      = inter(FontWeight.Normal,   18, 26),  // Standard-Text, Listentitel
    bodyMedium     = inter(FontWeight.Normal,   16, 22),  // Sekundär-Text, Meta
    bodySmall      = inter(FontWeight.Normal,   14, 20),
    labelLarge     = inter(FontWeight.Medium,   15, 20),  // Nav-Label, Pills, Chips
    labelMedium    = inter(FontWeight.Medium,   13, 16),
    labelSmall     = inter(FontWeight.Medium,   12, 16),
)
