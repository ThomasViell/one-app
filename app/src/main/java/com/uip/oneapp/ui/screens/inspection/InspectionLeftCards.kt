package com.uip.oneapp.ui.screens.inspection

import android.os.Environment
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uip.oneapp.R
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Linke Side-Cards fuer den Cinema-Mode (permanent sichtbar, 220dp breit).
 *
 *  - DateTimeCard:  aktuelles Datum + Uhrzeit (gross/bold, sonnenlichttauglich)
 *  - StorageCard:   Donut-Anzeige des freien internen Speichers (DrainQ-Teal)
 *  - BatteryCard:   Akku-Status des Cable-Controllers (C18) mit 4-Balken-Anzeige
 */
@Composable
fun InspectionLeftCards(
    batteryLevel: Int?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(220.dp)
            .fillMaxHeight()
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        DateTimeCard()
        StorageCard(modifier = Modifier.weight(1f))
        BatteryCard(batteryLevel = batteryLevel)
    }
}

@Composable
private fun DateTimeCard() {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val date = remember(now) { SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN).format(Date(now)) }
    val time = remember(now) { SimpleDateFormat("HH:mm", Locale.GERMAN).format(Date(now)) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1A1A1A))
            .padding(vertical = 12.dp, horizontal = 14.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
               modifier = Modifier.fillMaxWidth()) {
            Text(
                text = date,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFC8C8C8)
            )
            Text(
                text = time,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun StorageCard(modifier: Modifier = Modifier) {
    val (freeGB, percent) = remember {
        val dataDir = Environment.getDataDirectory()
        val total = dataDir.totalSpace.toDouble()
        val free = dataDir.freeSpace.toDouble()
        val pct = if (total > 0) (free / total * 100).toInt() else 0
        val freeGB = free / (1024.0 * 1024.0 * 1024.0)
        freeGB to pct
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0D7377))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "SPEICHER",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF14BDAC)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0A0A0F)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$percent%",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF14BDAC)
                    )
                }
            }
            Text(
                text = String.format(Locale.GERMAN, "%.1f GB", freeGB),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = "frei",
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun BatteryCard(batteryLevel: Int?) {
    val bars = when {
        batteryLevel == null -> 0
        batteryLevel >= 75 -> 4
        batteryLevel >= 50 -> 3
        batteryLevel >= 25 -> 2
        batteryLevel > 0 -> 1
        else -> 0
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1A1A1A))
            .padding(vertical = 12.dp, horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "C18",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "BATTERIE",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFC8C8C8)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                repeat(4) { i ->
                    Box(
                        modifier = Modifier
                            .size(width = 7.dp, height = 18.dp)
                            .background(if (i < bars) Color(0xFFFFCD00) else Color(0xFF333333))
                    )
                }
            }
        }
    }
}
