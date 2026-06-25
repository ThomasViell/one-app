package com.uip.oneapp.bootstrap

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log

/**
 * Android-/DevicePolicyManager-Implementierung von [DevicePolicyGateway] (Dual-Modus, Welle 3a) —
 * die einzige Schicht des Standort-Auto-Grants mit echtem Geräte-Bezug (Geräte-Test).
 *
 * Setzt den [OneDeviceAdminReceiver] als Admin-Komponente voraus (Manifest). Alle Aufrufe wirken
 * **nur**, wenn die App Geräteeigentümer ist; andernfalls wirft die Plattform `SecurityException` —
 * jeder Aufruf ist deshalb defensiv gekapselt und liefert dann `false`, statt zu crashen.
 */
class AndroidDevicePolicyGateway(context: Context) : DevicePolicyGateway {

    private val appContext = context.applicationContext
    private val dpm = appContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
    private val admin = ComponentName(appContext, OneDeviceAdminReceiver::class.java)

    override fun isDeviceOwner(): Boolean =
        try { dpm?.isDeviceOwnerApp(appContext.packageName) == true } catch (_: Throwable) { false }

    override fun grantFineLocation(): Boolean = try {
        val ok = dpm?.setPermissionGrantState(
            admin,
            appContext.packageName,
            Manifest.permission.ACCESS_FINE_LOCATION,
            DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED,
        ) == true
        if (!ok) Log.w(TAG, "setPermissionGrantState(ACCESS_FINE_LOCATION) abgelehnt")
        ok
    } catch (t: Throwable) {
        Log.w(TAG, "setPermissionGrantState warf ${t.javaClass.simpleName}: ${t.message}")
        false
    }

    override fun enableLocation(): Boolean = try {
        // setLocationEnabled existiert erst ab Android 11 (API 30).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && dpm != null) {
            dpm.setLocationEnabled(admin, true)
            true
        } else {
            false
        }
    } catch (t: Throwable) {
        Log.w(TAG, "setLocationEnabled warf ${t.javaClass.simpleName}: ${t.message}")
        false
    }

    private companion object { const val TAG = "DevicePolicyGateway" }
}
