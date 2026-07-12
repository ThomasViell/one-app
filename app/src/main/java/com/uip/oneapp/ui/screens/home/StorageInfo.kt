package com.uip.oneapp.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.uip.oneapp.ui.theme.DrainQTheme

data class VolumeUsage(val freeBytes: Long, val totalBytes: Long) {
    val usedFraction: Float
        get() = if (totalBytes <= 0) 0f
                else ((totalBytes - freeBytes).toFloat() / totalBytes).coerceIn(0f, 1f)
}

fun formatGb(bytes: Long): String =
    String.format(java.util.Locale.US, "%.1f GB", bytes / 1_000_000_000.0)

enum class StorageFillLevel { OK, WARN, CRITICAL }

fun storageFillLevel(fraction: Float): StorageFillLevel = when {
    fraction > 0.95f -> StorageFillLevel.CRITICAL
    fraction > 0.80f -> StorageFillLevel.WARN
    else             -> StorageFillLevel.OK
}

@Composable
fun storageFillColor(fraction: Float): Color {
    val c = DrainQTheme.colors
    return when (storageFillLevel(fraction)) {
        StorageFillLevel.CRITICAL -> c.error
        StorageFillLevel.WARN     -> c.amber
        StorageFillLevel.OK       -> c.success
    }
}
