package com.uip.oneapp.ui.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext

@Composable
fun rememberDeviceBattery(): Int? {
    val context = LocalContext.current
    val level by produceState<Int?>(initialValue = null, context) {
        val mgr = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        fun readLevel(): Int? =
            mgr?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it >= 0 }
        value = readLevel()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) { value = readLevel() }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        awaitDispose { context.unregisterReceiver(receiver) }
    }
    return level
}
