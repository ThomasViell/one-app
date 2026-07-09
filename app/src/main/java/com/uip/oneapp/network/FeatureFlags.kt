package com.uip.oneapp.network

/**
 * Laufzeit-Feature-Flags (Welle 5). Einzige Wahrheitsquelle für den Aufnahmeweg-Schalter.
 *
 * [useHardwareRecorder] wird in `OneApp.onCreate` **eager** aus dem DataStore restauriert (kurz
 * blockierend), damit schon die erste Aufnahme den richtigen Recorder wählt (kein Default-Fenster).
 * Geschrieben wird der Wert ausschließlich über den Settings-Schalter (der ihn zusätzlich
 * persistiert). Der Schalter ist während einer laufenden Aufnahme deaktiviert → ein Flip kann keine
 * aktive Aufnahme verwaisen.
 */
object FeatureFlags {
    /**
     * true → neuer HW-Encoder-Aufnahmeweg (`HardwareBitmapRecorder`); false → alter
     * `LocalBitmapRecorder` (Rückfallebene). Default **an** für den Testbuild.
     */
    @Volatile
    var useHardwareRecorder: Boolean = true
}
