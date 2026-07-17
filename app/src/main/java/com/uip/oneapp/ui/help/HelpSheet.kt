package com.uip.oneapp.ui.help

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.uip.oneapp.ui.theme.DqColors
import com.uip.oneapp.ui.theme.DrainQTheme
import com.uip.oneapp.ui.theme.InterFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpSheet(
    route: String,
    lang: String,
    onDismiss: () -> Unit,
    helpRepository: HelpRepository,
) {
    val screen = remember(route, lang) { helpRepository.getHelpForRoute(route, lang) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val c = DrainQTheme.colors

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.bgPanel,
        contentColor = c.textPrimary,
    ) {
        if (screen == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary,
                )
            }
            return@ModalBottomSheet
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            // Screenshot
            if (screen.screenshot.isNotEmpty()) {
                item {
                    HelpScreenshot(
                        screenshotId = screen.screenshot,
                        lang = lang,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                }
            }
            // Title
            item {
                Text(
                    text = screen.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = InterFontFamily,
                    color = c.textPrimary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            // Intro
            if (screen.intro.isNotEmpty()) {
                item {
                    Text(
                        text = screen.intro,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = InterFontFamily,
                        color = c.textSecondary,
                    )
                }
            }
            // Elements
            items(screen.elements) { elem ->
                HelpElementItem(elem = elem, colors = c)
            }
        }
    }
}

@Composable
private fun HelpScreenshot(
    screenshotId: String,
    lang: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bitmap = remember(screenshotId, lang) {
        try {
            context.assets.open("help/screenshots/$lang/$screenshotId.png")
                .use { BitmapFactory.decodeStream(it) }
        } catch (_: Exception) {
            try {
                context.assets.open("help/screenshots/de/$screenshotId.png")
                    .use { BitmapFactory.decodeStream(it) }
            } catch (_: Exception) {
                null
            }
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = screenshotId,
            contentScale = ContentScale.Fit,
            modifier = modifier,
        )
    } else {
        Box(modifier = modifier.background(DrainQTheme.colors.bgElevated))
    }
}

@Composable
private fun HelpElementItem(elem: HelpElement, colors: DqColors) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = elem.label,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = InterFontFamily,
            color = colors.amber,
        )
        Text(
            text = elem.text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = InterFontFamily,
            color = colors.textSecondary,
        )
    }
}
