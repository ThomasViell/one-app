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
import com.uip.oneapp.ui.theme.Amber
import com.uip.oneapp.ui.theme.Dimensions
import com.uip.oneapp.ui.theme.InterFontFamily

/**
 * Persistentes Live-OSD über dem Inspektions-Video.
 *
 * Minimal: nur Distanz (72sp Bold, 70% Opacity) und optional Spannung als Diagnose-Zeile.
 * Sonde/Licht sind absichtlich NICHT mehr im OSD — sie stehen im Steuer-Panel (Tap-Demand),
 * damit das HUD das Live-Bild möglichst wenig verdeckt.
 *
 * Backwards-Compatible API: sondeMode/lightLevel werden weiter angenommen aber nicht gerendert
 * (vermeidet Refactor in InspectionScreen.kt für diesen Mikro-Patch).
 *
 * @param distanceMeters Distanz in Metern (Werte mit |d|<0.005 werden auf 0 geclamped — vermeidet
 *   '-0.00 m'-Flackern bei Float-Vorzeichen-Drift).
 * @param voltage optional. Wird nur gerendert wenn > 0 (kleine 14sp-Zeile).
 */
@Composable
fun InspectionOsd(
    distanceMeters: Float,
    sondeMode: String,
    lightLevel: Int,
    voltage: Float,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // SA-Design: Station/Meter in Amber (Inter), schwarzer Schatten für Lesbarkeit.
        ShadowedText(
            text = String.format(java.util.Locale.US, "%.2f m", displayDistance(distanceMeters)),
            fontSize = Dimensions.OsdDistanceFontSize,
            fontWeight = FontWeight.SemiBold,
            alpha = Dimensions.OsdDistanceAlpha,
            color = Amber
        )
        if (voltage > 0f) {
            ShadowedText(
                text = String.format(java.util.Locale.US, "%.1f V", voltage),
                fontSize = Dimensions.OsdSmallFontSize,
                alpha = Dimensions.OsdDistanceAlpha,
                color = Color.White
            )
        }
    }
}

@Composable
private fun ShadowedText(
    text: String,
    fontSize: TextUnit,
    fontWeight: FontWeight = FontWeight.Normal,
    alpha: Float = 1f,
    color: Color = Color.White
) {
    Box {
        Text(
            text = text,
            color = Color.Black.copy(alpha = alpha),
            fontSize = fontSize,
            fontWeight = fontWeight,
            fontFamily = InterFontFamily,
            modifier = Modifier.offset(
                x = Dimensions.OsdShadowOffset,
                y = Dimensions.OsdShadowOffset
            )
        )
        Text(
            text = text,
            color = color.copy(alpha = alpha),
            fontSize = fontSize,
            fontWeight = fontWeight,
            fontFamily = InterFontFamily
        )
    }
}

private fun displayDistance(d: Float): Float = if (kotlin.math.abs(d) < 0.005f) 0.0f else d
