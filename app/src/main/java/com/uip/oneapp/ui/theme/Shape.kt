package com.uip.oneapp.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// DrainQ SA-Design — Radien (Vorgabe Abschnitt 3)
// small 8 · medium 12 (Inputs) · large 16 (Cards/Dialoge/Buttons) · Pill 999
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small      = RoundedCornerShape(8.dp),
    medium     = RoundedCornerShape(12.dp),
    large      = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

// Pille (vollständig gerundet) — für Chips/Status-Pills/Toggles.
val PillShape = RoundedCornerShape(999.dp)
