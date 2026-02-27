package com.uip.oneapp.ui.theme

import androidx.compose.ui.graphics.Color

// === NSP3CT BRAND COLORS ===
// Source: brand_colors.pdf / documentation_ui_layout_nsp3ct_pro_one.pdf

// NSP3CT Yellow – HEX #F9A800 / RGB 249,168,0 / Pantone 7548
val Nsp3ctYellow     = Color(0xFFF9A800)
// Active button gradient end – R=191 G=162 B=26 (from UI doc)
val Nsp3ctYellowDark = Color(0xFFBFA21A)

// DT3CT Blue – HEX #005387 / RGB 0,83,135 / Pantone 2945
val Dt3ctBlue        = Color(0xFF005387)

// === Dark Theme (Background: Black per user requirement) ===
val DarkBackground      = Color(0xFF000000)   // Pure black
val DarkSurface         = Color(0xFF0D0D0D)   // Near black
val DarkSurfaceVariant  = Color(0xFF1A1A1A)   // Dark grey
val DarkPrimary         = Nsp3ctYellow
val DarkPrimaryVariant  = Nsp3ctYellowDark
val DarkSecondary       = Dt3ctBlue
val DarkTertiary        = Nsp3ctYellowDark
val DarkOnBackground    = Color(0xFFE8E8E8)
val DarkOnSurface       = Color(0xFFBDBDBD)
val DarkOnPrimary       = Color(0xFF0D0D0D)   // Dark text on yellow
val DarkOnSecondary     = Color(0xFFFFFFFF)   // White text on DT3CT Blue
val DarkError           = Color(0xFFCF6679)
val DarkOnError         = Color(0xFF000000)

// === Status Colors (neutral, non-branded) ===
val StatusGreen  = Color(0xFF4CAF50)
val StatusYellow = Nsp3ctYellow
val StatusOrange = Color(0xFFFF9800)
val StatusRed    = Color(0xFFE63946)
val StatusBlue   = Dt3ctBlue
val MeterBlue    = Dt3ctBlue

// === Damage Class Colors (ZK 0–4) ===
val DamageClass0 = Color(0xFF4CAF50)    // Kein Schaden – Grün
val DamageClass1 = Color(0xFF8BC34A)    // Gering – Hellgrün
val DamageClass2 = Nsp3ctYellow         // Mittel – NSP3CT Yellow
val DamageClass3 = Color(0xFFFF9800)    // Hoch – Orange
val DamageClass4 = Color(0xFFE63946)    // Sehr Hoch – Rot

// === Connection Status ===
val Connected    = Color(0xFF4CAF50)
val Disconnected = Color(0xFFE63946)
val Connecting   = Nsp3ctYellow
