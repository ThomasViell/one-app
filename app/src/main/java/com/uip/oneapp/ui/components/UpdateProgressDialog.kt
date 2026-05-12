package com.uip.oneapp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uip.oneapp.ui.localization.S

@Composable
fun UpdateProgressDialog(
    stage: UpdateProgressStage,
    bytesDownloaded: Long,
    totalBytes: Long,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(S("update_install_now")) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                val stageText = when (stage) {
                    UpdateProgressStage.Downloading ->
                        S("update_progress_downloading")
                            .replace("{progress}", formatProgress(bytesDownloaded, totalBytes))
                    UpdateProgressStage.Verifying -> S("update_progress_verifying")
                    UpdateProgressStage.Installing -> S("update_progress_installing")
                }
                Text(
                    stageText,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (stage == UpdateProgressStage.Downloading && totalBytes > 0) {
                    LinearProgressIndicator(
                        progress = { (bytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(S("update_cancel"))
            }
        }
    )
}

enum class UpdateProgressStage { Downloading, Verifying, Installing }

private fun formatProgress(downloaded: Long, total: Long): String {
    val dl = if (downloaded >= 1024 * 1024) "%.1f MB".format(downloaded / (1024.0 * 1024.0))
    else "%.0f KB".format(downloaded / 1024.0)
    return if (total > 0) {
        val tot = if (total >= 1024 * 1024) "%.1f MB".format(total / (1024.0 * 1024.0))
        else "%.0f KB".format(total / 1024.0)
        "$dl / $tot"
    } else dl
}
