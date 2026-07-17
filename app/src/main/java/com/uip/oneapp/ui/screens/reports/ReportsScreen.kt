package com.uip.oneapp.ui.screens.reports

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.uip.oneapp.ui.components.DqButton
import com.uip.oneapp.ui.components.DqCard
import com.uip.oneapp.ui.components.DqHeader
import com.uip.oneapp.ui.components.DqIcon
import com.uip.oneapp.ui.help.HelpButton
import com.uip.oneapp.ui.localization.S
import com.uip.oneapp.ui.theme.DrainQTheme
import com.uip.oneapp.ui.theme.Dimensions
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * M11: Echte Berichtsübersicht. Sammelt alle erzeugten PDF-Berichte geräteweit
 * (exports/ und reports je Projekt), listet sie und teilt sie via FileProvider (ACTION_SEND).
 * Erreichbar über die Einstellungen. Die zentrale CTA führt zur Projektauswahl, um einen
 * neuen Bericht zu erstellen (PDF-Erzeugung liegt pro Projekt im ProjectDetail).
 */
@Composable
fun ReportsScreen(navController: NavController) {
    val c = DrainQTheme.colors
    val context = LocalContext.current

    var pdfs by remember { mutableStateOf<List<File>>(emptyList()) }
    LaunchedEffect(Unit) {
        val found = mutableListOf<File>()
        context.getExternalFilesDir("exports")?.listFiles()
            ?.filter { it.isFile && it.extension.equals("pdf", true) && it.length() > 0 }
            ?.let { found += it }
        context.getExternalFilesDir("reports")?.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
            dir.listFiles()
                ?.filter { it.isFile && it.extension.equals("pdf", true) && it.length() > 0 }
                ?.let { found += it }
        }
        pdfs = found.sortedByDescending { it.lastModified() }
    }

    val df = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    fun share(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(Intent.createChooser(intent, file.name)) }
    }

    Column(modifier = Modifier.fillMaxSize().background(c.bgWindow)) {
        DqHeader(title = S("reports_title"), actions = { HelpButton(route = "reports") })

        Column(
            modifier = Modifier.fillMaxSize().padding(Dimensions.Space16),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Space16),
        ) {
            if (pdfs.isEmpty()) {
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
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Dimensions.Space8),
                ) {
                    items(pdfs) { file ->
                        DqCard(modifier = Modifier.fillMaxWidth().clickable { share(file) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                DqIcon("save", tint = c.amber)
                                Spacer(Modifier.width(Dimensions.Space12))
                                Column(Modifier.weight(1f)) {
                                    Text(file.name, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
                                    Text(
                                        "${df.format(Date(file.lastModified()))} · ${file.length() / 1024} KB",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = c.textSecondary,
                                    )
                                }
                                DqIcon("chevron_right", tint = c.textSecondary)
                            }
                        }
                    }
                }
            }

            // Zentrale Large-CTA: führt zur Projektauswahl (Bericht je Projekt im Detail erzeugen).
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
