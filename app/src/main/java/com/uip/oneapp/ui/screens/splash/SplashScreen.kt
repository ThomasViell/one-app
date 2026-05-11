package com.uip.oneapp.ui.screens.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uip.oneapp.BuildConfig
import com.uip.oneapp.ui.localization.S
import com.uip.oneapp.ui.theme.*

@Composable
fun SplashScreen(onDismiss: () -> Unit) {
    val screenHeight = LocalConfiguration.current.screenHeightDp
    val compact = screenHeight < 500
    val spacerLarge = if (compact) 12.dp else 32.dp
    val spacerSmall = if (compact) 6.dp else 8.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(spacerLarge))

            // DrainQ Logo Text
            Text(
                text = "DrainQ",
                fontSize = if (compact) 48.sp else 64.sp,
                fontWeight = FontWeight.Black,
                fontFamily = BarlowFontFamily,
                color = DrainQTeal,
                letterSpacing = 2.sp
            )

            Text(
                text = "ONE",
                fontSize = if (compact) 20.sp else 28.sp,
                fontWeight = FontWeight.Light,
                fontFamily = BarlowFontFamily,
                color = DrainQTealLight,
                letterSpacing = 8.sp
            )

            Spacer(modifier = Modifier.height(spacerLarge))

            // Version
            Text(
                text = "${S("app_version")} ${BuildConfig.VERSION_NAME}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = BarlowFontFamily,
                color = DarkOnSurface
            )

            Spacer(modifier = Modifier.height(spacerSmall))

            // BETA Badge
            Surface(
                color = DrainQTeal,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "BETA",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = BarlowFontFamily,
                    color = Color.White,
                    letterSpacing = 2.sp
                )
            }

            Spacer(modifier = Modifier.height(spacerLarge))

            // Disclaimer
            Text(
                text = S("beta_disclaimer"),
                fontSize = 14.sp,
                fontFamily = BarlowFontFamily,
                color = DarkOnSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(spacerLarge))

            // OK Button
            Button(
                onClick = onDismiss,
                modifier = Modifier.width(200.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DrainQTeal,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = S("button_ok"),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = BarlowFontFamily
                )
            }

            Spacer(modifier = Modifier.height(spacerLarge))
        }
    }
}
