package com.uip.oneapp.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import com.uip.oneapp.ui.theme.Dimensions

@Composable
fun InspectionOsd(
    distanceMeters: Float,
    sondeMode: String,
    lightLevel: Int,
    voltage: Float,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        ShadowedText(
            text = String.format(java.util.Locale.US, "%.2f m", displayDistance(distanceMeters)),
            fontSize = Dimensions.OsdDistanceFontSize,
            fontWeight = FontWeight.Bold
        )
        ShadowedText(
            text = "Sonde: $sondeMode  ·  Licht: $lightLevel",
            fontSize = Dimensions.OsdSecondaryFontSize
        )
        if (voltage > 0f) {
            ShadowedText(
                text = String.format(java.util.Locale.US, "%.1f V", voltage),
                fontSize = Dimensions.OsdSmallFontSize
            )
        }
    }
}

@Composable
private fun ShadowedText(
    text: String,
    fontSize: TextUnit,
    fontWeight: FontWeight = FontWeight.Normal
) {
    Box {
        Text(
            text = text,
            color = Color.Black,
            fontSize = fontSize,
            fontWeight = fontWeight,
            modifier = Modifier.offset(
                x = Dimensions.OsdShadowOffset,
                y = Dimensions.OsdShadowOffset
            )
        )
        Text(
            text = text,
            color = Color.White,
            fontSize = fontSize,
            fontWeight = fontWeight
        )
    }
}

private fun displayDistance(d: Float): Float = if (kotlin.math.abs(d) < 0.005f) 0.0f else d
