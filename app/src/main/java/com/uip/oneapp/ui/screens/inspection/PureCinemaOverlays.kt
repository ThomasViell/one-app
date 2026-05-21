package com.uip.oneapp.ui.screens.inspection

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import com.uip.oneapp.ui.localization.S
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Pure Cinema Overlays — transparente UI-Elemente direkt auf dem Video.
 *
 *  - MeterCounterOverlay: gross oben links, klickbar fuer Reset-Menue,
 *                         long-press auf Reset-Buttons oeffnet Meta-Eingabe
 *  - StatusPills:         oben rechts, 3 Mini-Pills (Akku / REC / Licht)
 *  - LightSliderOverlay:  mittig, erscheint bei Hardware-Licht-Taste
 */

@Composable
fun MeterCounterOverlay(
    meterValue: Float,
    onResetAbsolute: () -> Unit,
    onResetDistance: () -> Unit,
    onSetMetaAbsolute: (Float) -> Unit,
    onSetMetaDistance: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var metaTarget by remember { mutableStateOf<MetaTarget?>(null) }

    Box(modifier = modifier) {
        // Meterzaehler — gross, klickbar
        Text(
            text = String.format(Locale.US, "%.2f m", displayMeter(meterValue)),
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 96.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .clickable { showMenu = true }
                .padding(4.dp)
        )
    }

    // Reset-Menue (mittig auf Video)
    if (showMenu) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable { showMenu = false },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF1A1A1A))
                    .padding(24.dp)
                    .clickable(enabled = false) {},
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Meterzähler zurücksetzen",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                ResetTile(
                    label = "GESAMT → 0",
                    sub = "Absolut-Zähler",
                    onTap = {
                        onResetAbsolute()
                        showMenu = false
                    },
                    onLongPress = {
                        metaTarget = MetaTarget.ABSOLUTE
                        showMenu = false
                    }
                )
                ResetTile(
                    label = "ABSTAND → 0",
                    sub = "Strecken-Zähler",
                    onTap = {
                        onResetDistance()
                        showMenu = false
                    },
                    onLongPress = {
                        metaTarget = MetaTarget.DISTANCE
                        showMenu = false
                    }
                )
                Text(
                    "Tipp: lang drücken für Meta-Wert",
                    color = Color(0xFFC8C8C8),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }

    // Meta-Wert-Eingabe-Dialog
    metaTarget?.let { target ->
        MetaValueDialog(
            title = if (target == MetaTarget.ABSOLUTE) "Gesamtmeter setzen" else "Abstandszähler setzen",
            onConfirm = { value ->
                if (target == MetaTarget.ABSOLUTE) onSetMetaAbsolute(value)
                else onSetMetaDistance(value)
                metaTarget = null
            },
            onDismiss = { metaTarget = null }
        )
    }
}

private enum class MetaTarget { ABSOLUTE, DISTANCE }

@Composable
private fun ResetTile(
    label: String,
    sub: String,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0D7377))
            .pointerInput(label) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { onLongPress() }
                )
            }
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        Column {
            Text(label, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun MetaValueDialog(
    title: String,
    onConfirm: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(S("meter_set_dialog_hint"), fontSize = 12.sp)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text(S("meter_unit")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val v = input.replace(',', '.').toFloatOrNull() ?: 0f
                onConfirm(v)
            }) { Text(S("btn_set")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(S("cancel")) }
        }
    )
}

private fun displayMeter(v: Float): Float = if (kotlin.math.abs(v) < 0.005f) 0f else v

// ─────────────────────────────────────────────────────────────────────────────
// Status Pills oben rechts
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun StatusPillsOverlay(
    batteryLevel: Int?,
    isRecording: Boolean,
    recordingElapsed: String,
    lightLevel: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BatteryPill(batteryLevel)
        RecPill(isRecording, recordingElapsed)
        LightPill(lightLevel)
    }
}

@Composable
private fun BatteryPill(level: Int?) {
    val color = when {
        level == null -> Color(0xFF555555)
        level >= 50 -> Color(0xFF4CAF50)
        level >= 20 -> Color(0xFFFFCD00)
        else -> Color(0xFFB91C1C)
    }
    Pill(bg = Color.Black.copy(alpha = 0.55f)) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = level?.let { "$it%" } ?: "—",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RecPill(isRecording: Boolean, elapsed: String) {
    val pulse = remember { mutableStateOf(true) }
    LaunchedEffect(isRecording) {
        while (isRecording) {
            pulse.value = !pulse.value
            delay(500)
        }
        pulse.value = true
    }
    val bg = if (isRecording) Color(0xFFB91C1C).copy(alpha = if (pulse.value) 0.9f else 0.55f)
             else Color.Black.copy(alpha = 0.55f)
    Pill(bg = bg) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (isRecording) Color.White else Color(0xFF888888))
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (isRecording) elapsed else "REC",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun LightPill(level: Int) {
    val active = level > 0
    val color = if (active) Color(0xFFFFCD00) else Color(0xFF888888)
    Pill(bg = Color.Black.copy(alpha = 0.55f)) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "L: $level",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun Pill(bg: Color, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Licht-Slider-Overlay (erscheint bei Hardware-Licht-Taste oder Tile-Tap)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LightSliderOverlay(
    visible: Boolean,
    currentLevel: Int,
    onLevelChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Auto-hide nach 3s ohne Interaktion
    LaunchedEffect(visible, currentLevel) {
        if (visible) {
            delay(3000)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(200)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(horizontal = 24.dp, vertical = 18.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(420.dp)
            ) {
                Text(
                    text = "LICHT",
                    color = Color(0xFFFFCD00),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "$currentLevel %",
                    color = Color.White,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Slider(
                    value = currentLevel.toFloat(),
                    onValueChange = { onLevelChange(it.toInt()) },
                    valueRange = 0f..100f,
                    steps = 9,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Hardware-Taste: +10% pro Druck   ·   Touch-Slider: stufenlos",
                    color = Color(0xFFC8C8C8),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
