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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.uip.oneapp.BuildConfig
import com.uip.oneapp.ui.localization.S
import com.uip.oneapp.ui.theme.*

@Composable
fun SplashScreen(onDismiss: () -> Unit) {
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
            Spacer(modifier = Modifier.height(Dimensions.XLargeSpacing))

            // DrainQ Logo Text
            Text(
                text = "DrainQ",
                fontSize = Dimensions.SplashTitleFontSize,
                fontWeight = FontWeight.Black,
                fontFamily = InterFontFamily,
                color = DrainQTeal,
                letterSpacing = Dimensions.LetterSpacingBrand
            )

            Text(
                text = "ONE",
                fontSize = Dimensions.SplashSubtitleFontSize,
                fontWeight = FontWeight.Light,
                fontFamily = InterFontFamily,
                color = DrainQTealLight,
                letterSpacing = Dimensions.LetterSpacingSubtitle
            )

            Spacer(modifier = Modifier.height(Dimensions.XLargeSpacing))

            // Version
            Text(
                text = "${S("app_version")} ${BuildConfig.VERSION_NAME}",
                fontSize = Dimensions.ButtonLabelFontSize,
                fontWeight = FontWeight.Medium,
                fontFamily = InterFontFamily,
                color = DarkOnSurface
            )

            Spacer(modifier = Modifier.height(Dimensions.SectionSpacing))

            // BETA Badge
            Surface(
                color = DrainQTeal,
                shape = RoundedCornerShape(Dimensions.ThumbnailCornerRadius)
            ) {
                Text(
                    text = "BETA",
                    modifier = Modifier.padding(
                        horizontal = Dimensions.PanelEdgePadding,
                        vertical = Dimensions.SmallSpacing
                    ),
                    fontSize = Dimensions.NavRailLabelFontSize,
                    fontWeight = FontWeight.Bold,
                    fontFamily = InterFontFamily,
                    color = Color.White,
                    letterSpacing = Dimensions.LetterSpacingBrand
                )
            }

            Spacer(modifier = Modifier.height(Dimensions.XLargeSpacing))

            // Disclaimer
            Text(
                text = S("beta_disclaimer"),
                fontSize = Dimensions.OsdSmallFontSize,
                fontFamily = InterFontFamily,
                color = DarkOnSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                lineHeight = Dimensions.LineHeightBody
            )

            Spacer(modifier = Modifier.height(Dimensions.XLargeSpacing))

            // OK Button
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .width(Dimensions.SplashButtonWidth)
                    .height(Dimensions.TouchLarge),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DrainQTeal,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(Dimensions.ButtonCornerRadius)
            ) {
                Text(
                    text = S("button_ok"),
                    fontSize = Dimensions.ButtonLabelFontSize,
                    fontWeight = FontWeight.Bold,
                    fontFamily = InterFontFamily
                )
            }

            Spacer(modifier = Modifier.height(Dimensions.XLargeSpacing))
        }
    }
}
