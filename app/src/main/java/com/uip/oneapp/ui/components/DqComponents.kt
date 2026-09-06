package com.uip.oneapp.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.uip.oneapp.R
import com.uip.oneapp.ui.localization.S
import com.uip.oneapp.ui.theme.DrainQTheme
import com.uip.oneapp.ui.theme.Dimensions
import com.uip.oneapp.ui.theme.PillShape

// =====================================================================================
// DrainQ SA-Design — wiederverwendbare Composables (Vorgabe Abschnitt 4).
// Regeln: keine festen Farben — nur Tokens (MaterialTheme / DrainQTheme.colors).
// Müssen in Dark UND Light korrekt aussehen. Touch min 48 dp.
// =====================================================================================

// --- DqIcon -------------------------------------------------------------------------
// Tabler-Outline-Vektor-Drawable über Key (Richtlinie §4.3), Strich 2 px.
object DqIcons {
    // Key -> Vektor-Drawable. Die Drawables liegen als ic_dq_<key>.xml in res/drawable.
    val byKey: Map<String, Int> = mapOf(
        "home" to R.drawable.ic_dq_home,
        "inspection" to R.drawable.ic_dq_inspection,
        "projects" to R.drawable.ic_dq_projects,
        "settings" to R.drawable.ic_dq_settings,
        "search" to R.drawable.ic_dq_search,
        "check" to R.drawable.ic_dq_check,
        "chevron_down" to R.drawable.ic_dq_chevron_down,
        "chevron_right" to R.drawable.ic_dq_chevron_right,
        "expand_less" to R.drawable.ic_dq_expand_less,
        "refresh" to R.drawable.ic_dq_refresh,
        "dot" to R.drawable.ic_dq_dot,
        "camera" to R.drawable.ic_dq_camera,
        "photo" to R.drawable.ic_dq_photo,
        "alert" to R.drawable.ic_dq_alert,
        "probe" to R.drawable.ic_dq_probe,
        "light" to R.drawable.ic_dq_light,
        "minus" to R.drawable.ic_dq_minus,
        "plus" to R.drawable.ic_dq_plus,
        "meter" to R.drawable.ic_dq_meter,
        "battery" to R.drawable.ic_dq_battery,
        "battery_charging" to R.drawable.ic_dq_battery_charging,
        "back" to R.drawable.ic_dq_back,
        "keyboard_hide" to R.drawable.ic_dq_keyboard_hide,
        "language" to R.drawable.ic_dq_language,
        "company" to R.drawable.ic_dq_company,
        "weather" to R.drawable.ic_dq_weather,
        "osd" to R.drawable.ic_dq_osd,
        "fullscreen" to R.drawable.ic_dq_fullscreen,
        "info" to R.drawable.ic_dq_info,
        "map" to R.drawable.ic_dq_map,
        "delete" to R.drawable.ic_dq_delete,
        "edit" to R.drawable.ic_dq_edit,
        "close" to R.drawable.ic_dq_close,
        "download" to R.drawable.ic_dq_download,
        "pdf" to R.drawable.ic_dq_pdf,
        "archive" to R.drawable.ic_dq_archive,
        "moon" to R.drawable.ic_dq_moon,
        "sun" to R.drawable.ic_dq_sun,
        "save" to R.drawable.ic_dq_save,
        "new_project" to R.drawable.ic_dq_new_project,
        "wifi" to R.drawable.ic_dq_wifi,
        "lock" to R.drawable.ic_dq_lock,
        "cloud" to R.drawable.ic_dq_cloud,
        "access_point" to R.drawable.ic_dq_access_point,
        "video" to R.drawable.ic_dq_video,
        "note" to R.drawable.ic_dq_note,
        "clock" to R.drawable.ic_dq_clock,
    )

    @DrawableRes
    fun res(key: String): Int = byKey[key] ?: R.drawable.ic_dq_info
}

@Composable
fun DqIcon(
    key: String,
    modifier: Modifier = Modifier,
    size: Dp = Dimensions.DqIconStd,
    tint: Color = LocalContentColor.current,
    contentDescription: String? = null,
) {
    Icon(
        painter = painterResource(DqIcons.res(key)),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.size(size),
    )
}

// --- DqButton -----------------------------------------------------------------------
enum class DqButtonStyle { Primary, Secondary, Ghost, Danger }

@Composable
fun DqButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: DqButtonStyle = DqButtonStyle.Primary,
    iconKey: String? = null,
    enabled: Boolean = true,
    large: Boolean = false,
) {
    val c = DrainQTheme.colors
    val container: Color
    val content: Color
    val border: BorderStroke?
    when (style) {
        DqButtonStyle.Primary   -> { container = c.amber; content = c.onAmber; border = null }
        DqButtonStyle.Secondary -> { container = Color.Transparent; content = c.amber; border = BorderStroke(1.dp, c.amber) }
        DqButtonStyle.Ghost     -> { container = Color.Transparent; content = MaterialTheme.colorScheme.onSurface; border = null }
        DqButtonStyle.Danger    -> { container = c.error; content = Color.White; border = null }
    }
    val alpha = if (enabled) 1f else 0.4f
    val height = if (large) Dimensions.ButtonHeightLarge else Dimensions.ButtonHeight

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large, // Radius 16
        color = container.copy(alpha = if (style == DqButtonStyle.Primary || style == DqButtonStyle.Danger) alpha else 1f),
        contentColor = content.copy(alpha = alpha),
        border = border,
        modifier = modifier.heightIn(min = height),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = height)
                .padding(horizontal = Dimensions.Space20),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (iconKey != null) {
                DqIcon(iconKey, size = Dimensions.DqIconInline, tint = content.copy(alpha = alpha))
                Spacer(Modifier.width(Dimensions.Space8))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium, // 20/600
                color = content.copy(alpha = alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// --- DqCard -------------------------------------------------------------------------
@Composable
fun DqCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = Dimensions.CardPadding,
    content: @Composable () -> Unit,
) {
    val c = DrainQTheme.colors
    Surface(
        shape = MaterialTheme.shapes.large, // Radius 16
        color = c.bgPanel,
        border = BorderStroke(1.dp, c.borderSubtle),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(contentPadding)) { content() }
    }
}

// --- DqPill / DqStatusChip ----------------------------------------------------------
// Höhe 32, Radius 999, BG=Statusfarbe@16 %, Text=Statusfarbe, optional Dot.
@Composable
fun DqStatusChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    showDot: Boolean = true,
    iconKey: String? = null,
    // Auftrag bedienbild Z-2 (F-2): eigenes Leading-Element statt des 8-dp-Punkts, z. B. ein
    // groesserer, blinkender Aufnahme-Punkt. Optional, aendert keine bestehende Aufrufstelle.
    leading: (@Composable () -> Unit)? = null,
) {
    Surface(
        shape = PillShape,
        color = color.copy(alpha = 0.16f),
        modifier = modifier.heightIn(min = Dimensions.PillHeight),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimensions.Space12),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(Dimensions.Space8))
            }
            if (iconKey != null) {
                DqIcon(iconKey, size = Dimensions.DqIconInline, tint = color)
                Spacer(Modifier.width(Dimensions.Space8))
            }
            if (showDot) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(PillShape)
                        .background(color)
                )
                Spacer(Modifier.width(Dimensions.Space8))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge, // 15/500
                color = color,
                maxLines = 1,
            )
        }
    }
}

/** Generische Pille (z. B. Tab-Indikator/Kategorie) — BG/Text frei wählbar. */
@Composable
fun DqPill(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = DrainQTheme.colors.amber.copy(alpha = 0.16f),
    contentColor: Color = DrainQTheme.colors.amber,
) {
    Surface(
        shape = PillShape,
        color = background,
        contentColor = contentColor,
        modifier = modifier.heightIn(min = Dimensions.PillHeight),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = Dimensions.Space12),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

// --- DqToggle -----------------------------------------------------------------------
// 64×36, Track Amber(an)/BgElevated(aus), Knob 28.
@Composable
fun DqToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = DrainQTheme.colors
    val track = if (checked) c.amber else c.bgElevated
    val knob = if (checked) c.onAmber else c.textSecondary
    val pad = (Dimensions.ToggleHeight - Dimensions.ToggleKnob) / 2
    val knobOffset by animateDpAsState(
        targetValue = if (checked) Dimensions.ToggleWidth - Dimensions.ToggleKnob - pad else pad,
        label = "dqToggleKnob",
    )
    Box(
        modifier = modifier
            .size(Dimensions.ToggleWidth, Dimensions.ToggleHeight)
            .clip(PillShape)
            .background(track.copy(alpha = if (enabled) 1f else 0.4f))
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(start = knobOffset)
                .size(Dimensions.ToggleKnob)
                .clip(PillShape)
                .background(knob)
        )
    }
}

// --- DqNavRail ----------------------------------------------------------------------
// Breite 120, BG BgSidebar, aktiv = Amber-Pille + Amber Icon/Label.
data class DqNavItem(val iconKey: String, val label: String, val route: String)

@Composable
fun DqNavRail(
    items: List<DqNavItem>,
    selectedRoute: String,
    onSelect: (DqNavItem) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
) {
    val c = DrainQTheme.colors
    Column(
        modifier = modifier
            .width(Dimensions.NavRailWidth)
            .background(c.bgSidebar),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (header != null) {
            Spacer(Modifier.height(Dimensions.Space24))
            header()
            Spacer(Modifier.height(Dimensions.Space24))
        } else {
            Spacer(Modifier.height(Dimensions.Space16))
        }
        items.forEach { item ->
            val active = item.route == selectedRoute
            val tint = if (active) c.amber else c.textSecondary
            Box(
                modifier = Modifier
                    .padding(horizontal = Dimensions.Space12, vertical = Dimensions.Space4)
                    .fillMaxWidth()
                    .heightIn(min = Dimensions.NavItemHeight)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (active) c.amber.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable { onSelect(item) },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    DqIcon(item.iconKey, size = Dimensions.DqIconLarge, tint = tint)
                    Spacer(Modifier.height(Dimensions.Space4))
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = tint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// --- DqHeader -----------------------------------------------------------------------
// Höhe 64, Titel headlineMedium, rechts Verbindungs-Chip / Actions.
@Composable
fun DqHeader(
    title: String,
    modifier: Modifier = Modifier,
    actions: (@Composable () -> Unit)? = null,
) {
    val c = DrainQTheme.colors
    Surface(color = c.bgPanel, modifier = modifier.fillMaxWidth()) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimensions.HeaderHeight)
                    .padding(horizontal = Dimensions.Space24),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium, // 27/600
                    color = c.textPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (actions != null) actions()
            }
            // 1 dp Trennlinie unten
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

// --- DqPager ------------------------------------------------------------------------
// Wiederverwendbare Seiten-Navigation:  Zähler "Seite X / N"  ‹  1 2 3 … N  ›
// Aktuelle Seite Amber hervorgehoben; Pfeile blättern (an den Enden deaktiviert).
// Bei > 7 Seiten werden die Zahlen gefenstert (1 … 4 5 6 … N). Touch-Targets ≥ 48 dp.
// currentPage 0-basiert; onPageSelected liefert die 0-basierte Zielseite.
@Composable
fun DqPager(
    currentPage: Int,
    pageCount: Int,
    onPageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = DrainQTheme.colors
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimensions.Space4),
    ) {
        Text(
            text = "${S("page")} ${currentPage + 1} / $pageCount",
            style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary,
        )
        Spacer(Modifier.width(Dimensions.Space8))
        DqPagerArrow(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            description = S("back"),
            enabled = currentPage > 0,
            onClick = { onPageSelected(currentPage - 1) },
        )
        dqPageWindow(currentPage, pageCount).forEach { token ->
            if (token == DQ_PAGER_ELLIPSIS) {
                Box(
                    modifier = Modifier.size(Dimensions.TouchMin),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("…", style = MaterialTheme.typography.bodyLarge, color = c.textTertiary)
                }
            } else {
                val selected = token - 1 == currentPage
                Box(
                    modifier = Modifier
                        .size(Dimensions.TouchMin)
                        .clip(RoundedCornerShape(Dimensions.OverlayCornerRadius))
                        .background(if (selected) c.amber else Color.Transparent)
                        .clickable(enabled = !selected) { onPageSelected(token - 1) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "$token",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (selected) c.onAmber else c.textPrimary,
                    )
                }
            }
        }
        DqPagerArrow(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            description = S("show_all"),
            enabled = currentPage < pageCount - 1,
            onClick = { onPageSelected(currentPage + 1) },
        )
    }
}

@Composable
private fun DqPagerArrow(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val c = DrainQTheme.colors
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(Dimensions.TouchMin),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) c.textPrimary else c.textTertiary,
            modifier = Modifier.size(Dimensions.DqIconStd),
        )
    }
}

private const val DQ_PAGER_ELLIPSIS = 0

/**
 * Liefert die anzuzeigenden Seitenzahlen (1-basiert). [DQ_PAGER_ELLIPSIS] = "…".
 * Bis 7 Seiten alle; darüber gefenstert: 1, aktuelle ±1, N (mit Lücken-Ellipsen).
 */
private fun dqPageWindow(currentPage: Int, pageCount: Int): List<Int> {
    if (pageCount <= 7) return (1..pageCount).toList()
    val cur = currentPage + 1
    val keep = sortedSetOf(1, pageCount, cur - 1, cur, cur + 1).filter { it in 1..pageCount }
    val result = mutableListOf<Int>()
    var prev = 0
    for (p in keep) {
        if (prev != 0 && p - prev > 1) result.add(DQ_PAGER_ELLIPSIS)
        result.add(p)
        prev = p
    }
    return result
}
