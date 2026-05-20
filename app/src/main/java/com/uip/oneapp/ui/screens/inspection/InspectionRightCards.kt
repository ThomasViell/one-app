package com.uip.oneapp.ui.screens.inspection

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
 * Rechte Side-Cards fuer den Cinema-Mode (permanent sichtbar, 220dp breit).
 *
 *  - PositionCard:   Meterzaehler-Anzeige (gross)
 *  - DistanceCard:   Abstand vom letzten Reset
 *  - RecordingCard:  Aufnahme-Timer (rot bei aktiv)
 *  - WlanBtCard:     Mini-Status WLAN + BT
 *  - NspBrandCard:   NSP3CT PRO Logo
 */
@Composable
fun InspectionRightCards(
    positionMeters: Float,
    distanceMeters: Float,
    isRecording: Boolean,
    recordingElapsed: String,
    wlanConnected: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(220.dp)
            .fillMaxHeight()
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        InfoCard(label = "POSITION", value = String.format("%.1f m", positionMeters))
        InfoCard(label = "ABSTAND", value = String.format("%.2f m", distanceMeters))
        RecordingCard(isRecording = isRecording, elapsed = recordingElapsed)
        WlanBtMiniRow(wlanConnected = wlanConnected)
        BrandCard(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun InfoCard(label: String, value: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1A1A1A))
            .padding(vertical = 10.dp, horizontal = 14.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
               modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFC8C8C8)
            )
            Text(
                text = value,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun RecordingCard(isRecording: Boolean, elapsed: String) {
    val bg = if (isRecording) Color(0xFFB91C1C) else Color(0xFF1A1A1A)
    val fg = if (isRecording) Color.White else Color(0xFFC8C8C8)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .padding(vertical = 10.dp, horizontal = 14.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
               modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isRecording) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Color.White)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = "AUFNAHME",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = fg
                )
            }
            Text(
                text = if (isRecording) elapsed else "00:00",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = if (isRecording) Color.White else Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun WlanBtMiniRow(wlanConnected: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MiniTile(
            label = "WLAN",
            active = wlanConnected,
            modifier = Modifier.weight(1f)
        )
        MiniTile(
            label = "BT",
            active = false,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MiniTile(label: String, active: Boolean, modifier: Modifier = Modifier) {
    val bg = if (active) Color(0xFF0D7377) else Color(0xFF1A1A1A)
    Box(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) Color.White else Color(0xFFC8C8C8)
        )
    }
}

@Composable
private fun BrandCard(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1A1A1A))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.brand_nsp3ct),
            contentDescription = "NSP3CT PRO",
            modifier = Modifier.fillMaxWidth(0.85f)
        )
    }
}
