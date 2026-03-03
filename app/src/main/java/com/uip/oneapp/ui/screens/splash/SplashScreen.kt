package com.uip.oneapp.ui.screens.splash

import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uip.oneapp.BuildConfig
import com.uip.oneapp.R
import com.uip.oneapp.ui.localization.S

@Composable
fun SplashScreen(onDismiss: () -> Unit) {
    val screenHeight = LocalConfiguration.current.screenHeightDp
    val compact = screenHeight < 500
    val spacerLarge = if (compact) 12.dp else 32.dp
    val spacerSmall = if (compact) 6.dp else 8.dp
    val deviceImageHeight = if (compact) (screenHeight * 0.28).dp else 280.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(spacerLarge))

            // PipeAnalyzer Logo (SVG as vector drawable)
            Image(
                painter = painterResource(id = R.drawable.logo_pipeanalyzer),
                contentDescription = "PipeAnalyzer Logo",
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(if (compact) 40.dp else 60.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(spacerLarge))

            // ONE Device Image
            Image(
                painter = painterResource(id = R.drawable.one_device),
                contentDescription = "ONE Device",
                modifier = Modifier.height(deviceImageHeight),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(spacerLarge))

            // Version
            Text(
                text = "${S("app_version")} ${BuildConfig.VERSION_NAME}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1D1D1B)
            )

            Spacer(modifier = Modifier.height(spacerSmall))

            // BETA Badge
            Surface(
                color = Color(0xFFFFCD00),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "BETA",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1D1D1B),
                    letterSpacing = 2.sp
                )
            }

            Spacer(modifier = Modifier.height(spacerLarge))

            // Disclaimer
            Text(
                text = S("beta_disclaimer"),
                fontSize = 14.sp,
                color = Color(0xFF666666),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(spacerLarge))

            // OK Button
            Button(
                onClick = onDismiss,
                modifier = Modifier.width(200.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFCD00),
                    contentColor = Color(0xFF1D1D1B)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = S("button_ok"),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(spacerLarge))
        }
    }
}
