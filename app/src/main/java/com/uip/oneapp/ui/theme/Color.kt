package com.uip.oneapp.ui.theme

import androidx.compose.ui.graphics.Color

// NSP3CT Corporate Design Colors
val Nsp3ctYellow = Color(0xFFFFCE00)            // NSP3CT Yellow Regular (Pantone 116 C)
val Nsp3ctYellowDark = Color(0xFFCB9700)        // NSP3CT Yellow Dark (Pantone 118 C)

// Dark Theme Colors (NSP3CT Style)
val DarkBackground = Color(0xFF1A1A2E)
val DarkSurface = Color(0xFF16213E)
val DarkSurfaceVariant = Color(0xFF1F2B47)
val DarkPrimary = Nsp3ctYellow                   // NSP3CT Yellow als Primary
val DarkPrimaryVariant = Nsp3ctYellowDark        // NSP3CT Yellow Dark als Variant
val DarkSecondary = Color(0xFF4CC9F0)            // Akzent Blau
val DarkTertiary = Nsp3ctYellowDark              // Yellow Dark als Tertiary
val DarkOnBackground = Color(0xFFE8E8E8)
val DarkOnSurface = Color(0xFFBDBDBD)
val DarkOnPrimary = Color(0xFF1A1A2E)            // Dunkler Text auf Yellow
val DarkOnSecondary = Color(0xFF000000)
val DarkError = Color(0xFFCF6679)
val DarkOnError = Color(0xFF000000)

// Status Colors
val StatusGreen = Color(0xFF4CAF50)
val StatusYellow = Color(0xFFFFEB3B)
val StatusOrange = Color(0xFFFF9800)
val StatusRed = Color(0xFFE63946)
val StatusBlue = Color(0xFF2196F3)
val MeterBlue = Color(0xFF2196F3)

// Damage Class Colors (ZK 0-4)
val DamageClass0 = Color(0xFF4CAF50)    // Kein Schaden - Gruen
val DamageClass1 = Color(0xFF8BC34A)    // Gering - Hellgruen
val DamageClass2 = Color(0xFFFFEB3B)    // Mittel - Gelb
val DamageClass3 = Color(0xFFFF9800)    // Hoch - Orange
val DamageClass4 = Color(0xFFE63946)    // Sehr Hoch - Rot

// Connection Status
val Connected = Color(0xFF4CAF50)
val Disconnected = Color(0xFFE63946)
val Connecting = Nsp3ctYellow
