package com.uip.oneapp.ui.theme

import androidx.compose.ui.graphics.Color

// === DRAINQ BRAND COLORS ===
// Source: DrainQ Windows Colors.xaml

// DrainQ Teal – Primary brand color
val DrainQTeal        = Color(0xFF0D7377)
val DrainQTealDark    = Color(0xFF095457)
val DrainQTealLight   = Color(0xFF14BDAC)

// DrainQ Deep Blue – Accent color
val DrainQDeepBlue    = Color(0xFF0F3460)
val DrainQDeepBlueDark = Color(0xFF0A2340)

// === Dark Theme (matching DrainQ Windows) ===
val DarkBackground      = Color(0xFF0A0A0F)   // Near black
val DarkSurface         = Color(0xFF111118)   // Dark surface
val DarkSurfaceVariant  = Color(0xFF1C1C28)   // Card color
val DarkPrimary         = DrainQTeal
val DarkPrimaryVariant  = DrainQTealDark
val DarkSecondary       = DrainQDeepBlue
val DarkTertiary        = DrainQTealLight
val DarkOnBackground    = Color(0xFFE8E8E8)
val DarkOnSurface       = Color(0xFFBDBDBD)
val DarkOnPrimary       = Color(0xFFFFFFFF)   // White text on teal
val DarkOnSecondary     = Color(0xFFFFFFFF)   // White text on deep blue
val DarkError           = Color(0xFFF44336)
val DarkOnError         = Color(0xFFFFFFFF)

// === Status Colors (neutral, non-branded) ===
val StatusGreen  = Color(0xFF4CAF50)
val StatusYellow = Color(0xFFFF9800)
val StatusOrange = Color(0xFFFF9800)
val StatusRed    = Color(0xFFF44336)
val StatusBlue   = Color(0xFF2196F3)
val MeterBlue    = Color(0xFF2196F3)

// === Damage Class Colors (ZK 0–4) ===
val DamageClass0 = Color(0xFF4CAF50)    // Kein Schaden – Grün
val DamageClass1 = Color(0xFF8BC34A)    // Gering – Hellgrün
val DamageClass2 = Color(0xFFFF9800)    // Mittel – Orange
val DamageClass3 = Color(0xFFFF5722)    // Hoch – Dunkelorange
val DamageClass4 = Color(0xFFF44336)    // Sehr Hoch – Rot

// === Connection Status ===
val Connected    = Color(0xFF4CAF50)
val Disconnected = Color(0xFFF44336)
val Connecting   = Color(0xFFFF9800)

// === OSD Burn-In Pixel Colors (matched to DrainQ.WPF OsdRenderer.cs BGR→RGB) ===
val OsdColorGreen  = Color(0xFF64FF64)   // bright green text (primary)
val OsdColorWhite  = Color(0xFFDCDCDC)   // near-white text
val OsdColorYellow = Color(0xFFFAE164)   // amber/yellow for flash & bottom bar
val OsdColorGray   = Color(0xFFA0A0A0)   // dimmed telemetry text
val OsdBarBackground = Color(0xCC000000) // semi-transparent black bar (80% opacity)
