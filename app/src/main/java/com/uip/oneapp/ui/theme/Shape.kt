package com.uip.oneapp.ui.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// NSP3CT PRO ONE Design Language: chamfered/slanted 45° corners
// Source: documentation_ui_layout_nsp3ct_pro_one.pdf – "Corner radius: 5px, slanted 45°"
val AppShapes = Shapes(
    extraSmall = CutCornerShape(4.dp),
    small      = CutCornerShape(6.dp),
    medium     = CutCornerShape(8.dp),
    large      = CutCornerShape(10.dp),
    extraLarge = CutCornerShape(12.dp)
)
