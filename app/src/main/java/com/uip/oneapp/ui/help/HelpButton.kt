package com.uip.oneapp.ui.help

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.uip.oneapp.ui.localization.LocalizationManager
import com.uip.oneapp.ui.theme.DrainQTheme

@Composable
fun HelpButton(
    route: String,
    modifier: Modifier = Modifier,
) {
    val lang by LocalizationManager.currentLanguage.collectAsState()
    val context = LocalContext.current
    val helpRepository = remember(context) { HelpRepository(context) }
    var showHelp by remember { mutableStateOf(false) }

    IconButton(
        onClick = { showHelp = true },
        modifier = modifier.size(40.dp),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
            contentDescription = if (lang == "en") "Help" else "Hilfe",
            tint = DrainQTheme.colors.textSecondary,
        )
    }

    if (showHelp) {
        HelpSheet(
            route = route,
            lang = lang,
            onDismiss = { showHelp = false },
            helpRepository = helpRepository,
        )
    }
}
