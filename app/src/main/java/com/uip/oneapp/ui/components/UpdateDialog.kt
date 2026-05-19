package com.uip.oneapp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uip.oneapp.ui.localization.S
import com.uip.oneapp.ui.theme.Dimensions
import com.uip.oneapp.update.ReleaseInfo

@Composable
fun UpdateDialog(
    release: ReleaseInfo,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                S("update_available").replace("{version}", release.version),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (release.mandatory) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = Dimensions.TouchSpacing),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(Dimensions.IconSizeLarge)
                        )
                        Spacer(modifier = Modifier.width(Dimensions.SectionSpacing))
                        Text(
                            S("update_mandatory_hint"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Text(
                    S("update_size_label").replace("{size}", formatBytes(release.size)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (release.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(Dimensions.SectionSpacing))
                    Text(
                        S("update_notes_label"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(Dimensions.SmallSpacing))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = Dimensions.DialogContentMinHeight)
                    ) {
                        Text(
                            release.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onInstall,
                modifier = Modifier.height(Dimensions.DialogButtonHeight)
            ) {
                Text(S("update_install_now"))
            }
        },
        dismissButton = {
            // MARKER_MANDATORY: NO — Später button always shown even if mandatory=true
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.height(Dimensions.DialogButtonHeight)
            ) {
                Text(S("update_later"))
            }
        }
    )
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}
