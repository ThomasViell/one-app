package com.uip.oneapp.ui.screens.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.uip.oneapp.ui.components.DqButton
import com.uip.oneapp.ui.components.DqCard
import com.uip.oneapp.ui.components.DqHeader
import com.uip.oneapp.ui.components.DqIcon
import com.uip.oneapp.ui.localization.S
import com.uip.oneapp.ui.theme.DrainQTheme
import com.uip.oneapp.ui.theme.Dimensions

@Composable
fun ReportsScreen(navController: NavController) {
    val c = DrainQTheme.colors
    Column(modifier = Modifier.fillMaxSize().background(c.bgWindow)) {
        DqHeader(title = S("reports_title"))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Dimensions.Space16),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Space16),
        ) {
            DqCard(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    DqIcon("save", size = Dimensions.IconSizeHuge, tint = c.textSecondary)
                    Spacer(modifier = Modifier.height(Dimensions.Space16))
                    Text(S("no_reports"), style = MaterialTheme.typography.bodyLarge, color = c.textSecondary)
                    Text(S("create_inspection_first"), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                }
            }

            // Zentrale Large-CTA: führt zur Projektauswahl (Bericht je Projekt im Detail).
            DqButton(
                text = S("generate_report"),
                iconKey = "save",
                large = true,
                onClick = { navController.navigate("projects") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
