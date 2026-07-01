package com.uip.oneapp.ui.theme

import androidx.compose.ui.graphics.Color

// =====================================================================================
// DrainQ SA-Design — Farb-Tokens (Vorgabe Abschnitt 1)
// Quelle: docs/design/drainq-one_SA-Design_Umsetzung_2026-06-04.md
// Amber/Dark-First. Dark UND Light gleichwertig. KEINE Hardcode-Farben in Screens —
// immer über das MaterialTheme-Schema oder DrainQTheme.colors (DqColors) gehen.
// =====================================================================================

// --- Marke / Primär (in Dark + Light gleich, außer Hover) ---
val Amber          = Color(0xFFFF9900)
val OnAmber        = Color(0xFF1D1D1B) // dunkler Text/Icon auf Amber!
val AmberHoverDark = Color(0xFFFFB340)
val AmberHoverLight= Color(0xFFE68A00)

// --- Dark-Rollen ---
val BgWindowDark    = Color(0xFF1C1C1E)
val BgPanelDark     = Color(0xFF2C2C2E)
val BgSidebarDark   = Color(0xFF242426)
val BgElevatedDark  = Color(0xFF3A3A3C)
val BorderSubtleDark= Color(0xFF3A3A3C)
val TextPrimaryDark = Color(0xFFF2F2F7)
val TextSecondaryDark = Color(0xFF98989D)
val TextTertiaryDark  = Color(0xFF636366)
val SuccessDark     = Color(0xFF30D158)
val WarningDark     = Color(0xFFFFD60A)
val ErrorDark       = Color(0xFFFF453A)
val InfoDark        = Color(0xFF0A84FF)

// --- Light-Rollen ---
val BgWindowLight    = Color(0xFFFAFAFA)
val BgPanelLight     = Color(0xFFFFFFFF)
val BgSidebarLight   = Color(0xFFF4F4F5)
val BgElevatedLight  = Color(0xFFE4E4E7)
val BorderSubtleLight= Color(0xFFE4E4E7)
val TextPrimaryLight = Color(0xFF18181B)
val TextSecondaryLight = Color(0xFF71717A)
val TextTertiaryLight  = Color(0xFFA1A1AA)
val SuccessLight     = Color(0xFF34C759)
val WarningLight     = Color(0xFFFFD60A)
val ErrorLight       = Color(0xFFFF3B30)
val InfoLight        = Color(0xFF0A84FF)

// --- Video / OSD (BG schwarz in beiden Themes) ---
val VideoBgColor     = Color(0xFF000000)
val OsdBgDark        = Color(0xB3000000) // #000 @ 70 %
val OsdBgLight       = Color(0x8C000000) // #000 @ 55 %

// --- Code-Familienfarben (themenunabhängig, gleich in Dark/Light) ---
val CodeLeitung   = Color(0xFF0A84FF)
val CodeSchacht   = Color(0xFF5E5CE6)
val CodeAnschluss = Color(0xFFBF5AF2)
val CodeStrecke   = Color(0xFF64D2FF)
val CodeBetrieb   = Color(0xFF30D158)

// =====================================================================================
// Damage-Class-Farben (ZK 0–4) — Semantik beibehalten (Vorgabe Abschnitt 1)
// =====================================================================================
val DamageClass0 = Color(0xFF4CAF50)    // Kein Schaden – Grün
val DamageClass1 = Color(0xFF8BC34A)    // Gering – Hellgrün
val DamageClass2 = Color(0xFFFF9800)    // Mittel – Orange
val DamageClass3 = Color(0xFFFF5722)    // Hoch – Dunkelorange
val DamageClass4 = Color(0xFFF44336)    // Sehr Hoch – Rot

// =====================================================================================
// LEGACY (DrainQ-Teal-Theme) — wird in den Screen-Wellen 1–4 auf SA-Tokens migriert.
// Bis dahin referenzieren noch nicht umgestellte Screens diese Namen direkt; daher
// bleiben sie erhalten, damit der Build grün bleibt. NICHT für neue Screens verwenden.
// =====================================================================================

val DrainQTeal        = Color(0xFF0D7377)
val DrainQTealDark    = Color(0xFF095457)
val DrainQTealLight   = Color(0xFF14BDAC)
val DrainQDeepBlue    = Color(0xFF0F3460)
val DrainQDeepBlueDark = Color(0xFF0A2340)

val DarkBackground      = BgWindowDark
val DarkSurface         = Color(0xFF111118)
val DarkSurfaceVariant  = BgPanelDark
val DarkPrimary         = Amber
val DarkPrimaryVariant  = AmberHoverDark
val DarkSecondary       = InfoDark
val DarkTertiary        = DrainQTealLight
val DarkOnBackground    = TextPrimaryDark
val DarkOnSurface       = TextSecondaryDark
val DarkOnPrimary       = OnAmber
val DarkOnSecondary     = Color(0xFFFFFFFF)
val DarkError           = ErrorDark
val DarkOnError         = Color(0xFFFFFFFF)

// Status (neutral) — Legacy-Aliasse auf SA-Status, wo sinnvoll
val StatusGreen  = SuccessDark
val StatusYellow = WarningDark
val StatusOrange = Color(0xFFFF9800)
val StatusRed    = ErrorDark
val StatusBlue   = InfoDark
val MeterBlue    = InfoDark

val Connected    = SuccessDark
val Disconnected = ErrorDark
val Connecting   = WarningDark

// Foto-Annotation: kräftiges Grün für die Stift-Palette (heller als StatusGreen,
// damit es auf dem Kamerabild sichtbar bleibt).
val AnnotationGreen = Color(0xFF00CC00)

// OSD Burn-In Pixel-Farben (Welle 1: Burn-in-Pfad wird dort neu gestaltet)
val OsdColorGreen  = Color(0xFF64FF64)
val OsdColorWhite  = Color(0xFFDCDCDC)
val OsdColorYellow = Color(0xFFFAE164)
val OsdColorGray   = Color(0xFFA0A0A0)
val OsdBarBackground = Color(0xCC000000)
