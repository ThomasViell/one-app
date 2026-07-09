package com.uip.oneapp.network

/**
 * Zentrale Aufnahme-Parameter (Welle 5). Ersetzt die zuvor an drei Stellen hartkodierte `12`
 * (LocalBitmapRecorder-Default, zwei InspectionScreen-Call-Sites) durch **eine** Quelle.
 */
object RecorderConfig {
    /**
     * Ziel-Bildrate. Beim HW-Encoder-Weg ist dies nur noch ein **Rate-Hint** für die
     * Encoder-Ratensteuerung/GOP — die Zeitbasis ist eine echte Uhr (VFR-PTS), nicht diese Zahl.
     * Der tatsächlich erreichte Mittelwert wird am Gerät gemessen, nicht behauptet.
     */
    const val TARGET_FPS = 25
}
