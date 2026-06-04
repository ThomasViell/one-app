package com.uip.oneapp.ui.screens.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.uip.oneapp.BuildConfig
import com.uip.oneapp.ui.components.DqButton
import com.uip.oneapp.ui.localization.S
import com.uip.oneapp.ui.theme.DrainQTheme
import com.uip.oneapp.ui.theme.Dimensions
import com.uip.oneapp.ui.theme.InterFontFamily

@Composable
fun SplashScreen(onDismiss: () -> Unit) {
    // SA-Design: tokenbasiert (Dark + Light). Wird von DrainQTheme umschlossen (MainActivity).
    val c = DrainQTheme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bgWindow),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(Dimensions.XLargeSpacing))

            // Logo-Slot (Amber-Marke) — Platzhalter für das ONE-Logo.
            Box(
                modifier = Modifier
                    .size(Dimensions.IconSizeHuge)
                    .clip(RoundedCornerShape(Dimensions.DialogCornerRadius))
                    .background(c.amber),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ONE",
                    fontSize = Dimensions.SplashSubtitleFontSize,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = InterFontFamily,
                    color = c.onAmber
                )
            }

            Spacer(modifier = Modifier.height(Dimensions.LargeSpacing))

            // DrainQ Logo Text
            Text(
                text = "DrainQ",
                fontSize = Dimensions.SplashTitleFontSize,
                fontWeight = FontWeight.SemiBold,
                fontFamily = InterFontFamily,
                color = c.amber,
                letterSpacing = Dimensions.LetterSpacingBrand
            )

            Text(
                text = "ONE",
                fontSize = Dimensions.SplashSubtitleFontSize,
                fontWeight = FontWeight.Normal,
                fontFamily = InterFontFamily,
                color = c.textSecondary,
                letterSpacing = Dimensions.LetterSpacingSubtitle
            )

            Spacer(modifier = Modifier.height(Dimensions.XLargeSpacing))

            // Version
            Text(
                text = "${S("app_version")} ${BuildConfig.VERSION_NAME}",
                fontSize = Dimensions.ButtonLabelFontSize,
                fontWeight = FontWeight.Medium,
                fontFamily = InterFontFamily,
                color = c.textSecondary
            )

            Spacer(modifier = Modifier.height(Dimensions.SectionSpacing))

            // BETA Badge
            Surface(
                color = c.amber,
                shape = RoundedCornerShape(Dimensions.ThumbnailCornerRadius)
            ) {
                Text(
                    text = "BETA",
                    modifier = Modifier.padding(
                        horizontal = Dimensions.PanelEdgePadding,
                        vertical = Dimensions.SmallSpacing
                    ),
                    fontSize = Dimensions.NavRailLabelFontSize,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = InterFontFamily,
                    color = c.onAmber,
                    letterSpacing = Dimensions.LetterSpacingBrand
                )
            }

            Spacer(modifier = Modifier.height(Dimensions.XLargeSpacing))

            // Disclaimer
            Text(
                text = S("beta_disclaimer"),
                fontSize = Dimensions.OsdSmallFontSize,
                fontFamily = InterFontFamily,
                color = c.textSecondary,
                textAlign = TextAlign.Center,
                lineHeight = Dimensions.LineHeightBody
            )

            Spacer(modifier = Modifier.height(Dimensions.XLargeSpacing))

            // OK Button
            DqButton(
                text = S("button_ok"),
                onClick = onDismiss,
                large = true,
                modifier = Modifier.width(Dimensions.SplashButtonWidth)
            )

            Spacer(modifier = Modifier.height(Dimensions.XLargeSpacing))
        }
    }
}
