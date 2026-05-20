package com.uip.oneapp.ui.screens.inspection

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uip.oneapp.R

/**
 * BottomBar9Tiles - Outdoor-Bottom-Bar mit 9 grossen Touch-Tiles.
 * Layout 1:1 zur ONE.APP V1.3.0. Tiles 2-9 sind via HardwareKeyBus
 * mit den Hardware-Tasten 131-138 gekoppelt.
 */
/**
 * Pure-Cinema-Mapping (9 Tiles):
 *  1 POWER (HW), 2 LICHT (131), 3 SONDE (132), 4 REC (133 toggle),
 *  5 FOTO (134), 6 GALERIE (135), 7 TAG/NACHT (136), 8 SCHADEN (137), 9 MENU (138)
 */
@Composable
fun BottomBar9Tiles(
    isRecording: Boolean,
    isLightOn: Boolean,
    highlightKeyCode: Int? = null,
    onPower: () -> Unit,
    onLight: () -> Unit,
    onSonde: () -> Unit,
    onRecordToggle: () -> Unit,
    onPhoto: () -> Unit,
    onGallery: () -> Unit,
    onDayNight: () -> Unit,
    onDamage: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0A0A0A))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Tile(R.drawable.ic_one_power, "POWER", false, false, onClick = onPower, modifier = Modifier.weight(1f))
        Tile(R.drawable.ic_one_light_on, "LICHT", isLightOn, highlightKeyCode == 131, onClick = onLight, modifier = Modifier.weight(1f))
        Tile(R.drawable.ic_one_light, "SONDE", false, highlightKeyCode == 132, onClick = onSonde, modifier = Modifier.weight(1f))
        // REC-Tile als Toggle: ic_one_stop_square wenn aktiv, sonst ic_one_record_circle
        Tile(
            iconRes = if (isRecording) R.drawable.ic_one_stop_square else R.drawable.ic_one_record_circle,
            label = if (isRecording) "STOP" else "REC",
            active = isRecording,
            highlight = highlightKeyCode == 133,
            activeColor = Color(0xFFB91C1C),
            onClick = onRecordToggle,
            modifier = Modifier.weight(1f)
        )
        Tile(R.drawable.ic_one_camera, "FOTO", false, highlightKeyCode == 134, onClick = onPhoto, modifier = Modifier.weight(1f))
        Tile(R.drawable.ic_one_gallery, "GALERIE", false, highlightKeyCode == 135, onClick = onGallery, modifier = Modifier.weight(1f))
        Tile(R.drawable.ic_one_day_night, "TAG/NACHT", false, highlightKeyCode == 136, onClick = onDayNight, modifier = Modifier.weight(1f))
        Tile(R.drawable.ic_one_damage, "SCHADEN", false, highlightKeyCode == 137, activeColor = Color(0xFFB91C1C), onClick = onDamage, modifier = Modifier.weight(1f))
        Tile(R.drawable.ic_one_settings, "MENU", false, highlightKeyCode == 138, onClick = onSettings, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Tile(
    iconRes: Int,
    label: String,
    active: Boolean,
    highlight: Boolean,
    activeColor: Color = Color(0xFFFFCD00),
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val target = when {
        highlight -> Color(0xFFFFCD00)
        active -> activeColor
        else -> Color(0xFF1A1A1A)
    }
    val bg by animateColorAsState(target, tween(160), label = "tile-bg")

    Column(
        modifier = modifier
            .heightIn(min = 84.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = label,
            modifier = Modifier.size(36.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (active || highlight) Color.Black else Color(0xFFE0E0E0)
        )
    }
}
