package com.uip.oneapp.ui.screens.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.uip.oneapp.BuildConfig
import com.uip.oneapp.R
import com.uip.oneapp.ui.components.DqButton
import com.uip.oneapp.ui.localization.S
import com.uip.oneapp.ui.theme.DrainQTheme
import com.uip.oneapp.ui.theme.Dimensions
import com.uip.oneapp.ui.theme.InterFontFamily

@Composable
fun SplashScreen(onDismiss: () -> Unit) {
    // SA-Design: tokenbasiert (Dark + Light). Wird von DrainQTheme umschlossen (MainActivity).
    val c = DrainQTheme.colors
    val context = LocalContext.current
    // ImageLoader mit SVG-Decoder für die Marken-Logos (R.raw.*.svg).
    val svgLoader = remember(context) {
        ImageLoader.Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .build()
    }
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

            // Echtes DrainQ-Logo, theme-abhängig (gleiche Quelle wie der Rest der App).
            // Breite ~42 % der Bildschirmbreite, Seitenverhältnis 340:99, zentriert.
            val logoRes = if (c.isDark) R.raw.logo_drainq_on_dark else R.raw.logo_drainq_on_light
            val logoWidth = LocalConfiguration.current.screenWidthDp.dp * 0.42f
            AsyncImage(
                model = ImageRequest.Builder(context).data(logoRes).build(),
                imageLoader = svgLoader,
                contentDescription = "DrainQ ONE",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .width(logoWidth)
                    .aspectRatio(340f / 99f)
            )

            Spacer(modifier = Modifier.height(Dimensions.LargeSpacing))

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
