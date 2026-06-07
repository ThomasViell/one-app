package com.uip.oneapp.bootstrap

import android.app.admin.DeviceAdminReceiver

/**
 * Minimaler Device-Admin-Receiver.
 *
 * Zweck: DrainQ.ONE kann damit als **Device-Owner** provisioniert werden (per ADB,
 * siehe docs/PROVISIONING_GOLDEN_IMAGE.md). Als Device-Owner darf die App sich selbst
 * über `DevicePolicyManager.setLockTaskPackages(admin, ...)` für den echten Kiosk
 * freischalten — `startLockTask()` sperrt dann Home/Recents vollständig (Feedback #5).
 *
 * Ohne Device-Owner fällt der Kiosk auf normales Screen-Pinning zurück. Der Receiver
 * selbst enthält keine eigene Policy-Logik; allein seine Existenz + die Manifest-
 * Deklaration sind die Voraussetzung für die Owner-Provisionierung.
 */
class OneDeviceAdminReceiver : DeviceAdminReceiver()
