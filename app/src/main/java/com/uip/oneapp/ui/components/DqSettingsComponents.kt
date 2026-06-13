package com.uip.oneapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.uip.oneapp.ui.localization.S
import com.uip.oneapp.ui.theme.DrainQTheme
import com.uip.oneapp.ui.theme.Dimensions
import com.uip.oneapp.ui.theme.ThemeMode
import com.uip.oneapp.ui.theme.isDark
import com.uip.oneapp.ui.theme.rememberThemeMode
import com.uip.oneapp.ui.theme.setThemeMode
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

// =====================================================================================
// SA-Komponenten für Einstellungs-Zeilen + Theme-Umschalter (Vorgabe Abschnitt 4).
// =====================================================================================

/** Settings-/Listen-Zeile: optionales Icon, Titel + Untertitel, rechts beliebiger Slot. */
@Composable
fun DqSettingRow(
    title: String,
    modifier: Modifier = Modifier,
    iconKey: String? = null,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = DrainQTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimensions.SettingRowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconKey != null) {
            DqIcon(iconKey, size = Dimensions.DqIconStd, tint = c.amber)
            Spacer(Modifier.width(Dimensions.Space12))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(Dimensions.Space12))
            trailing()
        }
    }
}

/** Dropdown-Auswahl-Zeile (Sprache, Meterquelle …). Zeilenhöhe 56. */
@Composable
fun DqDropdownRow(
    label: String,
    selectedText: String,
    options: List<Pair<String, String>>, // value -> Anzeigetext
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    iconKey: String? = null,
) {
    val c = DrainQTheme.colors
    var expanded by remember { mutableStateOf(false) }
    DqSettingRow(
        title = label,
        modifier = modifier,
        iconKey = iconKey,
        trailing = {
            Box {
                Row(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.medium) // Radius 12
                        .background(c.bgElevated)
                        .clickable { expanded = true }
                        .heightIn(min = 40.dp)
                        .padding(horizontal = Dimensions.Space12),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = selectedText,
                        style = MaterialTheme.typography.labelLarge,
                        color = c.textPrimary,
                    )
                    Spacer(Modifier.width(Dimensions.Space8))
                    DqIcon("chevron_down", size = Dimensions.DqIconInline, tint = c.textSecondary)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    HideSystemBarsInDialog()
                    options.forEach { (value, text) ->
                        DropdownMenuItem(
                            text = { Text(text) },
                            onClick = { onSelect(value); expanded = false },
                            trailingIcon = if (text == selectedText) {
                                { DqIcon("check", size = Dimensions.DqIconInline, tint = c.amber) }
                            } else null,
                        )
                    }
                }
            }
        },
    )
}

/**
 * Segment-Umschalter Dunkel/Hell (Mockup 04). Schreibt die `themeMode`-Pref.
 * Aktives Segment spiegelt den effektiven Dark/Light-Zustand (auch bei System-Modus).
 */
@Composable
fun DqThemeToggle(modifier: Modifier = Modifier) {
    val c = DrainQTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mode = rememberThemeMode(context)
    val darkActive = mode.isDark()

    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(c.bgElevated)
            .padding(Dimensions.Space4),
        horizontalArrangement = Arrangement.spacedBy(Dimensions.Space4),
    ) {
        ThemeSegment(
            label = S("appearance_dark"),
            selected = darkActive,
            onClick = { scope.launch { setThemeMode(context, ThemeMode.DARK) } },
        )
        ThemeSegment(
            label = S("appearance_light"),
            selected = !darkActive,
            onClick = { scope.launch { setThemeMode(context, ThemeMode.LIGHT) } },
        )
    }
}

@Composable
private fun ThemeSegment(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = DrainQTheme.colors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) c.amber else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .heightIn(min = 32.dp)
            .width(80.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) c.onAmber else c.textSecondary,
        )
    }
}
