package com.uip.oneapp.network.internal

/**
 * Linearisierung des Roh-Meterzählers aus Group 22 in real-world Millimeter.
 *
 * Aktuell Identitäts-Stub: Roh-Wert in mm wird 1:1 durchgereicht. Für den Pilot
 * ausreichend. Für Produktion: Stützstellen-Tabelle aus
 *   one-revers/decoded/jadx/sources/com/bominwell/robot/control/SerialControl/LinearDataPoints.java
 * portieren (29 KB, charakteristische Kabel-Dehnung der ONE-Schiebekamera).
 */
class LinearMeterCalculator {
    fun calculateWithDiscount(rawValueMm: Int): Double {
        return rawValueMm.toDouble()
    }
}
