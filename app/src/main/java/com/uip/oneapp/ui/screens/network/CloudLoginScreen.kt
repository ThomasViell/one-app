package com.uip.oneapp.ui.screens.network

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.uip.oneapp.ui.components.DqButton
import com.uip.oneapp.ui.components.DqCard
import com.uip.oneapp.ui.components.DqIcon
import com.uip.oneapp.ui.components.DqStatusChip
import com.uip.oneapp.ui.components.KeyboardHideButton
import com.uip.oneapp.ui.help.HelpButton
import com.uip.oneapp.ui.localization.S
import com.uip.oneapp.ui.theme.DrainQTheme
import com.uip.oneapp.ui.theme.Dimensions

/**
 * Platzhalter-Screen für die spätere DrainQ-Cloud-Anmeldung. Das Login-Layout ist
 * bereits vorhanden, aber DEAKTIVIERT — es gibt noch keine echte Authentifizierung.
 * Anbindung (OAuth/Token gegen drainq.web) folgt; Token-Ablage siehe
 * [com.uip.oneapp.cloud.CloudAccountStore].
 */
@Composable
fun CloudLoginScreen(navController: NavController) {
    val c = DrainQTheme.colors

    Scaffold(
        containerColor = c.bgWindow,
        topBar = {
            Surface(color = c.bgPanel, modifier = Modifier.fillMaxWidth()) {
                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Dimensions.HeaderHeight)
                            .padding(horizontal = Dimensions.Space12),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            DqIcon("back", size = Dimensions.DqIconToolbar, tint = c.textPrimary)
                        }
                        Spacer(Modifier.width(Dimensions.Space8))
                        Text(
                            S("cloud_account"),
                            style = MaterialTheme.typography.headlineMedium,
                            color = c.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        HelpButton(route = "cloud_login")
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(c.borderSubtle)
                            .align(Alignment.BottomStart)
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(Dimensions.Space16),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimensions.Space16),
        ) {
            DqCard(modifier = Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    DqIcon("cloud", tint = c.amber, size = Dimensions.DqIconLarge)
                    Spacer(Modifier.height(Dimensions.Space12))
                    Text(
                        S("cloud_login_headline"),
                        style = MaterialTheme.typography.titleMedium,
                        color = c.textPrimary,
                    )
                    Spacer(Modifier.height(Dimensions.Space8))
                    DqStatusChip(text = S("cloud_coming_soon"), color = c.info, showDot = false)
                }
            }

            // Deaktiviertes Login-Layout (Vorschau auf die spätere Anmeldung).
            DqCard(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = "",
                    onValueChange = {},
                    enabled = false,
                    label = { Text(S("cloud_email")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Dimensions.InputHeight),
                    textStyle = TextStyle(fontSize = Dimensions.InputFontSize),
                )
                Spacer(Modifier.height(Dimensions.Space12))
                OutlinedTextField(
                    value = "",
                    onValueChange = {},
                    enabled = false,
                    label = { Text(S("cloud_password")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Dimensions.InputHeight),
                    textStyle = TextStyle(fontSize = Dimensions.InputFontSize),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(),
                    trailingIcon = { KeyboardHideButton() },
                )
                Spacer(Modifier.height(Dimensions.Space16))
                DqButton(
                    text = S("cloud_login_button"),
                    onClick = { /* deaktiviert — bald verfügbar */ },
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
